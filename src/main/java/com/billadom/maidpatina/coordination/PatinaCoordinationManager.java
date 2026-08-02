package com.billadom.maidpatina.coordination;

import com.billadom.maidpatina.operation.BlockOperation;
import com.billadom.maidpatina.task.RustRemovalTask;
import com.billadom.maidpatina.task.WaxingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PatinaCoordinationManager {
    private static final double HELPER_SEARCH_RADIUS = 16.0D;
    private static final double INTERACTION_DISTANCE = 3.0D;
    private static final long REQUEST_TIMEOUT_TICKS = 20L * 20L;
    private static final long SCRAPE_INTERVAL_TICKS = 10L;
    private static final Map<ServerLevel, LevelRequests> REQUESTS = new WeakHashMap<>();

    private PatinaCoordinationManager() {
    }

    public static boolean requestRustRemoval(EntityMaid waxer, BlockPos target) {
        if (!(waxer.level() instanceof ServerLevel level)) {
            return false;
        }

        LevelRequests requests = requests(level);
        LinkRequest ownRequest = requests.byWaxer.get(waxer.getUUID());
        if (ownRequest != null) {
            return true;
        }

        LinkRequest reserved = requests.byTarget.get(target);
        if (reserved != null) {
            return true;
        }

        EntityMaid helper = findHelper(level, waxer, target, requests);
        if (helper == null) {
            return false;
        }

        LinkRequest request = new LinkRequest(
                target.immutable(), waxer.getUUID(), helper.getUUID(),
                level.getGameTime() + REQUEST_TIMEOUT_TICKS
        );
        link(requests, request);

        helper.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        waxer.setBegging(true);
        waxer.getLookControl().setLookAt(helper, 30.0F, 30.0F);
        // verified: maid_code InitSounds and EntityMaid#playSound, 2026-08-03
        // TODO: Replace this with a dedicated call-for-help voice if TLM adds one.
        waxer.playSound(InitSounds.MAID_FIND_TARGET.get(), 1.0F, 1.0F);
        return true;
    }

    public static boolean canWaxerTarget(EntityMaid waxer, BlockPos target) {
        if (!(waxer.level() instanceof ServerLevel level)) {
            return true;
        }
        LinkRequest request = requests(level).byTarget.get(target);
        return request == null || request.waxerId.equals(waxer.getUUID());
    }

    public static void tick(ServerLevel level, EntityMaid maid, BlockOperation operation, long gameTime) {
        if (operation == BlockOperation.WAXING) {
            tickWaxer(level, maid, gameTime);
        } else if (operation == BlockOperation.RUST_REMOVAL) {
            tickHelper(level, maid, gameTime);
        }
    }

    private static void tickWaxer(ServerLevel level, EntityMaid waxer, long gameTime) {
        LevelRequests requests = requests(level);
        LinkRequest request = requests.byWaxer.get(waxer.getUUID());
        if (request == null) {
            return;
        }

        if (!isWaxerValid(waxer, request.target)) {
            remove(requests, request, level);
            return;
        }

        EntityMaid helper = getMaid(level, request.helperId);
        if (!isHelperValid(helper, waxer, request.target, requests, request)) {
            EntityMaid replacement = findHelper(level, waxer, request.target, requests);
            if (replacement == null) {
                waxDirectly(requests, request, level);
                return;
            }
            reassignHelper(requests, request, replacement);
            helper = replacement;
        }

        if (gameTime >= request.expiresAt) {
            waxDirectly(requests, request, level);
            return;
        }

        waxer.setBegging(true);
        // verified: maid_code MaidWalkToLivingEntityTask uses this API for maid navigation, 2026-08-03
        BehaviorUtils.setWalkAndLookTargetMemories(waxer, request.target, 0.35F, 2);
        waxer.getLookControl().setLookAt(helper, 30.0F, 30.0F);

        if (!BlockOperation.RUST_REMOVAL.canApply(level, request.target)) {
            if (request.target.closerToCenterThan(waxer.position(), INTERACTION_DISTANCE)) {
                finishWaxing(requests, request, level, helper, true);
            }
        }
    }

    private static void tickHelper(ServerLevel level, EntityMaid helper, long gameTime) {
        LevelRequests requests = requests(level);
        LinkRequest request = requests.byHelper.get(helper.getUUID());
        if (request == null) {
            return;
        }

        EntityMaid waxer = getMaid(level, request.waxerId);
        if (!isWaxerValid(waxer, request.target)
                || !isHelperValid(helper, waxer, request.target, requests, request)) {
            return;
        }

        if (!BlockOperation.RUST_REMOVAL.canApply(level, request.target)) {
            return;
        }

        if (!request.target.closerToCenterThan(helper.position(), INTERACTION_DISTANCE)
                || !isVisible(helper, request.target)) {
            BehaviorUtils.setWalkAndLookTargetMemories(helper, request.target, 0.6F, 2);
            return;
        }

        helper.getLookControl().setLookAt(
                request.target.getX() + 0.5D,
                request.target.getY() + 0.5D,
                request.target.getZ() + 0.5D
        );
        if (gameTime < request.nextScrapeAt) {
            return;
        }

        ItemStack tool = BlockOperation.RUST_REMOVAL.findTool(helper);
        if (tool.isEmpty()) {
            return;
        }
        if (BlockOperation.RUST_REMOVAL.apply(helper, request.target, tool)) {
            request.nextScrapeAt = gameTime + SCRAPE_INTERVAL_TICKS;
            if (!BlockOperation.RUST_REMOVAL.canApply(level, request.target)
                    && request.target.closerToCenterThan(waxer.position(), INTERACTION_DISTANCE)) {
                finishWaxing(requests, request, level, helper, true);
            }
        }
    }

    private static EntityMaid findHelper(ServerLevel level, EntityMaid waxer, BlockPos target,
                                         LevelRequests requests) {
        return level.getEntitiesOfClass(
                        EntityMaid.class,
                        waxer.getBoundingBox().inflate(HELPER_SEARCH_RADIUS),
                        helper -> isHelperCandidate(helper, waxer, target)
                                && !requests.byHelper.containsKey(helper.getUUID())
                ).stream()
                .min(Comparator.comparingDouble(helper -> helper.distanceToSqr(
                        target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D)))
                .orElse(null);
    }

    private static boolean isHelperCandidate(EntityMaid helper, EntityMaid waxer, BlockPos target) {
        return helper != waxer
                && helper.isAlive()
                && Objects.equals(helper.getOwnerUUID(), waxer.getOwnerUUID())
                && helper.getOwnerUUID() != null
                && helper.getTask().getUid().equals(RustRemovalTask.UID)
                && helper.getScheduleDetail() == Activity.WORK
                && helper.canBrainMoving()
                && helper.isWithinRestriction(target)
                && helper.canDestroyBlock(target)
                && !BlockOperation.RUST_REMOVAL.findTool(helper).isEmpty();
    }

    private static boolean isHelperValid(EntityMaid helper, EntityMaid waxer, BlockPos target,
                                         LevelRequests requests, LinkRequest request) {
        return helper != null
                && requests.byHelper.get(helper.getUUID()) == request
                && isHelperCandidate(helper, waxer, target);
    }

    private static boolean isWaxerValid(EntityMaid waxer, BlockPos target) {
        return waxer != null
                && waxer.isAlive()
                && waxer.getTask().getUid().equals(WaxingTask.UID)
                && waxer.canBrainMoving()
                && waxer.isWithinRestriction(target)
                && !BlockOperation.WAXING.findTool(waxer).isEmpty()
                && BlockOperation.WAXING.canApply(waxer.level(), target);
    }

    private static void reassignHelper(LevelRequests requests, LinkRequest request, EntityMaid helper) {
        requests.byHelper.remove(request.helperId);
        request.helperId = helper.getUUID();
        requests.byHelper.put(request.helperId, request);
        helper.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void waxDirectly(LevelRequests requests, LinkRequest request, ServerLevel level) {
        EntityMaid helper = getMaid(level, request.helperId);
        finishWaxing(requests, request, level, helper, false);
    }

    private static void finishWaxing(LevelRequests requests, LinkRequest request, ServerLevel level,
                                     EntityMaid helper, boolean thankHelper) {
        EntityMaid waxer = getMaid(level, request.waxerId);
        if (waxer == null) {
            remove(requests, request, level);
            return;
        }
        if (!request.target.closerToCenterThan(waxer.position(), INTERACTION_DISTANCE)
                || !isVisible(waxer, request.target)) {
            BehaviorUtils.setWalkAndLookTargetMemories(waxer, request.target, 0.35F, 2);
            return;
        }

        ItemStack wax = BlockOperation.WAXING.findTool(waxer);
        boolean applied = !wax.isEmpty() && BlockOperation.WAXING.apply(waxer, request.target, wax);
        if (applied && thankHelper && helper != null && helper.isAlive()) {
            waxer.getLookControl().setLookAt(helper, 30.0F, 30.0F);
            helper.getLookControl().setLookAt(waxer, 30.0F, 30.0F);
            waxer.swing(InteractionHand.OFF_HAND);
            level.sendParticles(ParticleTypes.HEART,
                    waxer.getX(), waxer.getY() + waxer.getBbHeight(), waxer.getZ(),
                    3, 0.25D, 0.2D, 0.25D, 0.02D);
            // TODO: Replace this with a dedicated thank-you voice if TLM adds one.
            waxer.playSound(InitSounds.MAID_ITEM_GET.get(), 1.0F, 1.0F);
        }
        remove(requests, request, level);
    }

    private static void link(LevelRequests requests, LinkRequest request) {
        requests.byTarget.put(request.target, request);
        requests.byWaxer.put(request.waxerId, request);
        requests.byHelper.put(request.helperId, request);
    }

    private static void remove(LevelRequests requests, LinkRequest request, ServerLevel level) {
        requests.byTarget.remove(request.target, request);
        requests.byWaxer.remove(request.waxerId, request);
        requests.byHelper.remove(request.helperId, request);

        EntityMaid waxer = getMaid(level, request.waxerId);
        if (waxer != null) {
            waxer.setBegging(false);
            waxer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            waxer.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        EntityMaid helper = getMaid(level, request.helperId);
        if (helper != null) {
            helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            helper.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
    }

    private static EntityMaid getMaid(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static boolean isVisible(EntityMaid maid, BlockPos pos) {
        BlockHitResult hit = maid.level().clip(new ClipContext(
                maid.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static LevelRequests requests(ServerLevel level) {
        return REQUESTS.computeIfAbsent(level, ignored -> new LevelRequests());
    }

    private static final class LevelRequests {
        private final Map<BlockPos, LinkRequest> byTarget = new HashMap<>();
        private final Map<UUID, LinkRequest> byWaxer = new HashMap<>();
        private final Map<UUID, LinkRequest> byHelper = new HashMap<>();
    }

    private static final class LinkRequest {
        private final BlockPos target;
        private final UUID waxerId;
        private UUID helperId;
        private final long expiresAt;
        private long nextScrapeAt;

        private LinkRequest(BlockPos target, UUID waxerId, UUID helperId, long expiresAt) {
            this.target = target;
            this.waxerId = waxerId;
            this.helperId = helperId;
            this.expiresAt = expiresAt;
        }
    }
}
