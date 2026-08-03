package com.billadom.maidpatina.task.behavior;

import com.billadom.maidpatina.task.AdvancedHoneyTask;
import com.billadom.maidpatina.task.AdvancedHoneyTask.DisplayPosition;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MaidAdvancedHoneyTask extends Behavior<EntityMaid> {
    private static final double SEARCH_RADIUS = 20.0D;
    private static final double HEAD_ARRIVAL_DISTANCE_SQR = 1.0D * 1.0D;
    private static final double HAND_ARRIVAL_DISTANCE_SQR = 1.5D * 1.5D;
    private static final double COLLISION_MARGIN = 0.12D;
    private static final int POLLINATION_TICKS = 5 * 20;
    private static final int DEPOSIT_TICKS = 2 * 20;
    private static final int BEE_COOLDOWN_TICKS = 5 * 60 * 20;
    private static final int SEARCH_INTERVAL_TICKS = 20;
    private static final int MAX_SESSION_TICKS = 60 * 20;
    private static final double BEE_SPEED = 1.0D;

    private static final Map<UUID, UUID> RESERVATIONS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private Bee bee;
    private Phase phase = Phase.POLLINATE;
    private int phaseTicks;
    private long nextSearchTime;
    private float interactionYaw;

    public MaidAdvancedHoneyTask() {
        super(ImmutableMap.of(), MAX_SESSION_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (level.getGameTime() < nextSearchTime
                || !maid.canBrainMoving()
                || !AdvancedHoneyTask.hasAllRequirements(maid)) {
            return false;
        }
        nextSearchTime = level.getGameTime() + SEARCH_INTERVAL_TICKS;
        cleanupExpiredCooldowns(level.getGameTime());

        Optional<Bee> candidate = findCandidate(level, maid);
        if (candidate.isEmpty()) {
            return false;
        }

        Bee found = candidate.get();
        UUID previous = RESERVATIONS.putIfAbsent(found.getUUID(), maid.getUUID());
        if (previous != null && !previous.equals(maid.getUUID())) {
            return false;
        }
        bee = found;
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        phase = Phase.POLLINATE;
        phaseTicks = 0;
        interactionYaw = maid.yBodyRot;
        holdMaidStill(maid);
        DisplayPosition flowerPosition = AdvancedHoneyTask.getFlowerPosition(maid).orElseThrow();
        maid.swing(animationHand(flowerPosition));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return bee != null
                && bee.isAlive()
                && !bee.isBaby()
                && !bee.isAngry()
                && !bee.hasStung()
                && bee.getTarget() == null
                && bee.isFood(AdvancedHoneyTask.getFlower(maid))
                && maid.isWithinRestriction(bee.blockPosition())
                && maid.getUUID().equals(RESERVATIONS.get(bee.getUUID()))
                && (phase == Phase.DEPOSIT ? bee.hasNectar() : !bee.hasNectar())
                && AdvancedHoneyTask.hasAllRequirements(maid)
                && maid.canBrainMoving();
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        bee.getLookControl().setLookAt(maid, 30.0F, 30.0F);
        holdMaidStill(maid);

        switch (phase) {
            case POLLINATE -> tickPollination(maid);
            case DEPOSIT -> tickDeposit(level, maid, gameTime);
        }
    }

    private void tickPollination(EntityMaid maid) {
        holdInteractionFacing(maid);
        DisplayPosition flowerPosition = AdvancedHoneyTask.getFlowerPosition(maid).orElseThrow();
        BeeTarget flowerTarget = targetFor(maid, bee, flowerPosition, interactionYaw);
        moveBeeTo(flowerTarget.navigationPoint());
        double distanceSqr = bee.getBoundingBox().getCenter().distanceToSqr(flowerTarget.interactionPoint());
        boolean atTarget = distanceSqr <= arrivalDistanceSqr(flowerPosition);
        if (atTarget) {
            phaseTicks++;
        }
        if (phaseTicks < POLLINATION_TICKS) {
            return;
        }

        bee.setHasNectar(true);
        bee.setStayOutOfHiveCountdown(DEPOSIT_TICKS + 40);
        phase = Phase.DEPOSIT;
        phaseTicks = 0;
    }

    private void tickDeposit(ServerLevel level, EntityMaid maid, long gameTime) {
        holdInteractionFacing(maid);
        DisplayPosition hivePosition = AdvancedHoneyTask.getHivePosition(maid).orElseThrow();
        BeeTarget hiveTarget = targetFor(maid, bee, hivePosition, interactionYaw);
        moveBeeTo(hiveTarget.navigationPoint());
        double distanceSqr = bee.getBoundingBox().getCenter().distanceToSqr(hiveTarget.interactionPoint());
        boolean atTarget = distanceSqr <= arrivalDistanceSqr(hivePosition);
        if (atTarget) {
            phaseTicks++;
        }
        if (phaseTicks < DEPOSIT_TICKS) {
            return;
        }

        if (produceHoney(level, maid)) {
            bee.dropOffNectar();
            bee.setStayOutOfHiveCountdown(40);
            COOLDOWNS.put(bee.getUUID(), gameTime + BEE_COOLDOWN_TICKS);
            maid.swing(animationHand(hivePosition));
            // TODO: Replace this placeholder with a dedicated thanks-to-the-bee voice/action.
            maid.playSound(InitSounds.MAID_ITEM_GET.get(), 1.0F, 1.0F);
        }
        doStop(level, maid, gameTime);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        clearMaidMovement(maid);
        if (bee != null) {
            RESERVATIONS.remove(bee.getUUID(), maid.getUUID());
            bee.getNavigation().stop();
        }
        bee = null;
        phase = Phase.POLLINATE;
        phaseTicks = 0;
    }

    private Optional<Bee> findCandidate(ServerLevel level, EntityMaid maid) {
        AABB searchBox = maid.getBoundingBox().inflate(SEARCH_RADIUS);
        long gameTime = level.getGameTime();
        return level.getEntitiesOfClass(Bee.class, searchBox, candidate -> isEligible(candidate, maid, gameTime, false))
                .stream()
                .filter(candidate -> candidate.distanceToSqr(maid) <= SEARCH_RADIUS * SEARCH_RADIUS)
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(maid)));
    }

    private static boolean isEligible(Bee candidate, EntityMaid maid, long gameTime, boolean allowOwnReservation) {
        UUID reservation = RESERVATIONS.get(candidate.getUUID());
        boolean reservationAvailable = reservation == null
                || (allowOwnReservation && reservation.equals(maid.getUUID()));
        return candidate.isAlive()
                && !candidate.isBaby()
                && !candidate.isAngry()
                && !candidate.hasStung()
                && !candidate.hasNectar()
                && candidate.getTarget() == null
                && candidate.isFood(AdvancedHoneyTask.getFlower(maid))
                && maid.isWithinRestriction(candidate.blockPosition())
                && COOLDOWNS.getOrDefault(candidate.getUUID(), 0L) <= gameTime
                && reservationAvailable;
    }

    private void moveBeeTo(Vec3 target) {
        bee.getNavigation().moveTo(target.x, target.y, target.z, BEE_SPEED);
        bee.getLookControl().setLookAt(target.x, target.y, target.z, 30.0F, 30.0F);
    }

    private static BeeTarget targetFor(EntityMaid maid, Bee bee, DisplayPosition position, float bodyYaw) {
        return switch (position) {
            case HEAD -> headTarget(maid, bee);
            case MAIN_HAND -> handTarget(maid, bee, InteractionHand.MAIN_HAND, bodyYaw);
            case OFF_HAND -> handTarget(maid, bee, InteractionHand.OFF_HAND, bodyYaw);
        };
    }

    private static double arrivalDistanceSqr(DisplayPosition position) {
        return position == DisplayPosition.HEAD
                ? HEAD_ARRIVAL_DISTANCE_SQR
                : HAND_ARRIVAL_DISTANCE_SQR;
    }

    private static InteractionHand animationHand(DisplayPosition position) {
        return position == DisplayPosition.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static BeeTarget handTarget(EntityMaid maid, Bee bee, InteractionHand hand, float bodyYaw) {
        float yaw = bodyYaw * ((float) Math.PI / 180.0F);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        boolean mainIsRight = maid.getMainArm() == HumanoidArm.RIGHT;
        boolean targetIsMain = hand == InteractionHand.MAIN_HAND;
        double sideSign = mainIsRight == targetIsMain ? 1.0D : -1.0D;
        double lateralDistance = maid.getBbWidth() * 0.5D + bee.getBbWidth() * 0.5D + COLLISION_MARGIN;
        Vec3 interactionPoint = maid.position()
                .add(0.0D, maid.getBbHeight() * 0.62D, 0.0D)
                .add(forward.scale(0.20D))
                .add(right.scale(lateralDistance * sideSign));
        Vec3 navigationPoint = interactionPoint.add(0.0D, -bee.getBbHeight() * 0.5D, 0.0D);
        return new BeeTarget(navigationPoint, interactionPoint);
    }

    private static BeeTarget headTarget(EntityMaid maid, Bee bee) {
        double navigationY = maid.getBoundingBox().maxY + COLLISION_MARGIN;
        Vec3 navigationPoint = new Vec3(maid.getX(), navigationY, maid.getZ());
        Vec3 interactionPoint = navigationPoint.add(0.0D, bee.getBbHeight() * 0.5D, 0.0D);
        return new BeeTarget(navigationPoint, interactionPoint);
    }

    private void holdInteractionFacing(EntityMaid maid) {
        maid.setYRot(interactionYaw);
        maid.setYHeadRot(interactionYaw);
        maid.yBodyRot = interactionYaw;
    }

    private static void holdMaidStill(EntityMaid maid) {
        maid.getNavigation().stop();
        Vec3 movement = maid.getDeltaMovement();
        maid.setDeltaMovement(0.0D, movement.y, 0.0D);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(maid.blockPosition()), 0.0F, 0));
    }

    private static boolean produceHoney(ServerLevel level, EntityMaid maid) {
        IItemHandler backpack = maid.getAvailableBackpackInv();
        ItemStack shears = findStack(backpack, stack -> stack.canPerformAction(ToolActions.SHEARS_HARVEST));
        if (!shears.isEmpty() && canInsert(backpack, new ItemStack(Items.HONEYCOMB))) {
            ItemHandlerHelper.insertItemStacked(backpack, new ItemStack(Items.HONEYCOMB), false);
            damageShears(shears, maid);
            level.playSound(null, maid, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        ItemStack bottle = findStack(backpack, stack -> stack.is(Items.GLASS_BOTTLE));
        if (!bottle.isEmpty() && canInsert(backpack, new ItemStack(Items.HONEY_BOTTLE))) {
            bottle.shrink(1);
            ItemHandlerHelper.insertItemStacked(backpack, new ItemStack(Items.HONEY_BOTTLE), false);
            level.playSound(null, maid, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    private static ItemStack findStack(IItemHandler inventory, java.util.function.Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (predicate.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean canInsert(IItemHandler inventory, ItemStack output) {
        return ItemHandlerHelper.insertItemStacked(inventory, output, true).isEmpty();
    }

    private static void damageShears(ItemStack shears, EntityMaid maid) {
        if (shears.isDamageableItem() && shears.hurt(1, maid.getRandom(), null)) {
            shears.shrink(1);
        }
    }

    private static void clearMaidMovement(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getNavigation().stop();
    }

    private static void cleanupExpiredCooldowns(long gameTime) {
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private enum Phase {
        POLLINATE,
        DEPOSIT
    }

    private record BeeTarget(Vec3 navigationPoint, Vec3 interactionPoint) {
    }
}
