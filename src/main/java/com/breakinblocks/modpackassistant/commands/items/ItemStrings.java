package com.breakinblocks.modpackassistant.commands.items;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class ItemStrings {
    private ItemStrings() {
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String modId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    public static String giveString(ItemStack stack, HolderLookup.Provider lookup) {
        return itemId(stack) + componentBlock(stack, lookup);
    }

    public static String componentBlock(ItemStack stack, HolderLookup.Provider lookup) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return "";
        }
        Tag encoded = DataComponentPatch.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), patch).getOrThrow();
        if (!(encoded instanceof CompoundTag components) || components.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String key : components.keySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(key);
            Tag value = components.get(key);
            if (!key.startsWith("!") && value != null) {
                builder.append('=').append(value);
            }
        }
        return builder.append(']').toString();
    }

    public static String countedGiveString(ItemStack stack, HolderLookup.Provider lookup) {
        String give = giveString(stack, lookup);
        return stack.getCount() > 1 ? stack.getCount() + " " + give : give;
    }
}
