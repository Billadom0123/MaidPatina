package com.billadom.maidpatina;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    public static final TagKey<Item> RUST_REMOVAL_TOOLS = itemTag("rust_removal_tools");
    public static final TagKey<Item> WAXING_ITEMS = itemTag("waxing_items");

    private ModTags() {
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MaidPatina.MOD_ID, path));
    }
}
