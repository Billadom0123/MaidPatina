package com.billadom.maidpatina.task;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidArriveAtBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.Comparator;
import java.util.Optional;

public final class MaidPatinaApplyTask extends MaidArriveAtBlockTask {
    private static final int INTERACTION_SCAN_RADIUS = 3;

    public MaidPatinaApplyTask(BlockOperation operation, double closeEnoughDistance) {
        super(closeEnoughDistance, (maid, ignoredWorkPos) -> applyNearest(maid, operation));
    }

    private static void applyNearest(EntityMaid maid, BlockOperation operation) {
        ItemStack tool = findTool(maid, operation);
        if (tool.isEmpty()) {
            return;
        }

        BlockPos center = maid.blockPosition();
        findNearestVisibleTarget(maid, operation, center).ifPresent(pos -> operation.apply(maid, pos, tool));
    }

    private static Optional<BlockPos> findNearestVisibleTarget(EntityMaid maid, BlockOperation operation, BlockPos center) {
        return BlockPos.betweenClosedStream(
                        center.offset(-INTERACTION_SCAN_RADIUS, -INTERACTION_SCAN_RADIUS, -INTERACTION_SCAN_RADIUS),
                        center.offset(INTERACTION_SCAN_RADIUS, INTERACTION_SCAN_RADIUS, INTERACTION_SCAN_RADIUS))
                .filter(maid::isWithinRestriction)
                .filter(pos -> operation.canApply(maid.level(), pos))
                .filter(pos -> isVisible(maid, pos))
                .map(BlockPos::immutable)
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(maid.position())));
    }

    private static boolean isVisible(EntityMaid maid, BlockPos pos) {
        Vec3 target = Vec3.atCenterOf(pos);
        BlockHitResult hit = maid.level().clip(new ClipContext(
                maid.getEyePosition(), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static ItemStack findTool(EntityMaid maid, BlockOperation operation) {
        IItemHandler inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (operation.isTool(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
