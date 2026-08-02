package com.billadom.maidpatina.task;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

public final class MaidPatinaMoveTask extends MaidMoveToBlockTask {
    private static final int TARGET_SCAN_RADIUS = 2;
    private final BlockOperation operation;

    public MaidPatinaMoveTask(BlockOperation operation, float movementSpeed) {
        super(movementSpeed, 4);
        this.operation = operation;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!findTool(maid).isEmpty()) {
            searchForDestination(level, maid);
        }
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos basePos) {
        BlockPos center = basePos.above();
        for (BlockPos target : BlockPos.betweenClosed(
                center.offset(-TARGET_SCAN_RADIUS, -TARGET_SCAN_RADIUS, -TARGET_SCAN_RADIUS),
                center.offset(TARGET_SCAN_RADIUS, TARGET_SCAN_RADIUS, TARGET_SCAN_RADIUS))) {
            if (maid.isWithinRestriction(target)
                    && operation.canApply(level, target)
                    && isVisibleFromWorkPosition(level, maid, basePos, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisibleFromWorkPosition(ServerLevel level, EntityMaid maid, BlockPos basePos, BlockPos target) {
        Vec3 eyeAtWorkPosition = Vec3.atCenterOf(basePos).add(0.0D, 1.5D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(
                eyeAtWorkPosition, Vec3.atCenterOf(target),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private ItemStack findTool(EntityMaid maid) {
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
