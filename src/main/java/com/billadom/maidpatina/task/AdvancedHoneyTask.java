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
                Pair.of("has_hive_in_main_hand", AdvancedHoneyTask::hasHiveInMainHand),
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
        return "Use a main-hand hive and an offhand or head-display flower to help a bee pollinate, then produce one honey item";
    }

    public static boolean hasAllRequirements(EntityMaid maid) {
        return hasHiveInMainHand(maid)
                && hasFlower(maid)
                && hasHarvestToolInBackpack(maid);
    }

    public static boolean hasHiveInMainHand(EntityMaid maid) {
        ItemStack stack = maid.getMainHandItem();
        return stack.is(Items.BEEHIVE) || stack.is(Items.BEE_NEST);
    }

    public static boolean hasFlower(EntityMaid maid) {
        return getFlower(maid).is(ItemTags.FLOWERS);
    }

    public static boolean hasHeadFlower(EntityMaid maid) {
        // verified: TLM 1.5.3 EntityMaid#getBackpackShowItem mirrors MaidBackpackHandler.BACKPACK_ITEM_SLOT (slot 5), 2026-08-03
        return maid.getBackpackShowItem().is(ItemTags.FLOWERS);
    }

    public static ItemStack getFlower(EntityMaid maid) {
        // The displayed/head flower takes precedence when both positions contain valid flowers.
        return hasHeadFlower(maid) ? maid.getBackpackShowItem() : maid.getOffhandItem();
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
}
