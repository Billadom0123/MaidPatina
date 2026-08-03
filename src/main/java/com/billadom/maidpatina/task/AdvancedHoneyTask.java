package com.billadom.maidpatina.task;

import com.billadom.maidpatina.MaidPatina;
import com.billadom.maidpatina.task.behavior.MaidAdvancedHoneyTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.FunctionCallSwitchResult;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class AdvancedHoneyTask implements IMaidTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaidPatina.MOD_ID, "advanced_honey");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.HONEY_BOTTLE.getDefaultInstance();
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        // TODO: Replace this placeholder with a dedicated "HONEY... HONEY..." voice.
        return SoundUtil.environmentSound(maid, InitSounds.MAID_IDLE.get(), 0.5F);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // Touhou Little Maid appends an activity-update behavior to this list.
        return new ArrayList<>(List.of(Pair.of(5, new MaidAdvancedHoneyTask())));
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return List.of(
                Pair.of("has_hive", AdvancedHoneyTask::hasHive),
                Pair.of("has_flower", AdvancedHoneyTask::hasFlower),
                Pair.of("has_harvest_tool_in_backpack", AdvancedHoneyTask::hasHarvestToolInBackpack)
        );
    }

    @Override
    public FunctionCallSwitchResult onFunctionCallSwitch(EntityMaid maid) {
        return hasAllRequirements(maid)
                ? FunctionCallSwitchResult.NO_CHANGE
                : FunctionCallSwitchResult.MISSING_REQUIRED_ITEM;
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return 20.0F;
    }

    @Override
    public String getMaidActionSummary() {
        return "Use a flower and a hive in the head-display or either hand to help a bee produce one honey item";
    }

    public static boolean hasAllRequirements(EntityMaid maid) {
        return hasHive(maid)
                && hasFlower(maid)
                && hasHarvestToolInBackpack(maid);
    }

    public static boolean hasHive(EntityMaid maid) {
        return getHivePosition(maid).isPresent();
    }

    public static boolean hasFlower(EntityMaid maid) {
        return getFlower(maid).is(ItemTags.FLOWERS);
    }

    public static Optional<DisplayPosition> getFlowerPosition(EntityMaid maid) {
        return findPosition(maid, stack -> stack.is(ItemTags.FLOWERS));
    }

    public static ItemStack getFlower(EntityMaid maid) {
        return getFlowerPosition(maid).map(position -> position.getItem(maid)).orElse(ItemStack.EMPTY);
    }

    public static Optional<DisplayPosition> getHivePosition(EntityMaid maid) {
        return findPosition(maid, AdvancedHoneyTask::isHive);
    }

    public static ItemStack getHive(EntityMaid maid) {
        return getHivePosition(maid).map(position -> position.getItem(maid)).orElse(ItemStack.EMPTY);
    }

    private static Optional<DisplayPosition> findPosition(EntityMaid maid, Predicate<ItemStack> predicate) {
        // Priority requested for both flowers and hives: head display > main hand > offhand.
        for (DisplayPosition position : DisplayPosition.values()) {
            if (predicate.test(position.getItem(maid))) {
                return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    private static boolean isHive(ItemStack stack) {
        return stack.is(Items.BEEHIVE) || stack.is(Items.BEE_NEST);
    }

    public static boolean hasHarvestToolInBackpack(EntityMaid maid) {
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.canPerformAction(ToolActions.SHEARS_HARVEST) || stack.is(Items.GLASS_BOTTLE)) {
                return true;
            }
        }
        return false;
    }

    public enum DisplayPosition {
        HEAD {
            @Override
            ItemStack getItem(EntityMaid maid) {
                // verified: TLM 1.5.3 EntityMaid#getBackpackShowItem mirrors
                // MaidBackpackHandler.BACKPACK_ITEM_SLOT (slot 5), 2026-08-03
                return maid.getBackpackShowItem();
            }
        },
        MAIN_HAND {
            @Override
            ItemStack getItem(EntityMaid maid) {
                return maid.getMainHandItem();
            }
        },
        OFF_HAND {
            @Override
            ItemStack getItem(EntityMaid maid) {
                return maid.getOffhandItem();
            }
        };

        abstract ItemStack getItem(EntityMaid maid);
    }
}
