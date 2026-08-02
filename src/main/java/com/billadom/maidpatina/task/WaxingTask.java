package com.billadom.maidpatina.task;

import com.billadom.maidpatina.MaidPatina;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WaxingTask extends AbstractPatinaTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MaidPatina.MOD_ID, "waxing");

    public WaxingTask() {
        super(BlockOperation.WAXING);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.HONEYCOMB.getDefaultInstance();
    }

    @Override
    protected String conditionName() {
        return "has_waxing_item";
    }

    @Override
    public String getMaidActionSummary() {
        return "Wax nearby waxable copper blocks; requires honeycomb or another waxing item";
    }
}
