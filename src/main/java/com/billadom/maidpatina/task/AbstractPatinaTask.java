package com.billadom.maidpatina.task;

import com.billadom.maidpatina.operation.BlockOperation;
import com.billadom.maidpatina.task.behavior.MaidPatinaApplyTask;
import com.billadom.maidpatina.task.behavior.MaidPatinaCoordinationTask;
import com.billadom.maidpatina.task.behavior.MaidPatinaMoveTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public abstract class AbstractPatinaTask implements IMaidTask {
    private final BlockOperation operation;

    protected AbstractPatinaTask(BlockOperation operation) {
        this.operation = operation;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // Touhou Little Maid appends its activity-update behavior to this list.
        return new ArrayList<>(List.of(
                Pair.of(4, new MaidPatinaCoordinationTask(operation)),
                Pair.of(5, new MaidPatinaMoveTask(operation, 0.6F)),
                Pair.of(6, new MaidPatinaApplyTask(operation, 2.5D))
        ));
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Collections.singletonList(Pair.of(conditionName(), this::hasRequiredItem));
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        return hasRequiredItem(maid)
                ? FunctionCallSwitchResult.NO_CHANGE
                : FunctionCallSwitchResult.MISSING_REQUIRED_ITEM;
    }

    protected abstract String conditionName();

    protected final boolean hasRequiredItem(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (operation.isTool(stack)) {
                return true;
            }
        }
        return false;
    }
}
