package com.breakinblocks.modpackassistant.commands.items;

import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return new ItemInput(stack.getItemHolder(), stack.getComponentsPatch()).serialize(lookup);
    }

    public static String componentBlock(ItemStack stack, HolderLookup.Provider lookup) {
        String full = giveString(stack, lookup);
        String id = itemId(stack);
        return full.startsWith(id) ? full.substring(id.length()) : full;
    }

    public static String countedGiveString(ItemStack stack, HolderLookup.Provider lookup) {
        String give = giveString(stack, lookup);
        return stack.getCount() > 1 ? stack.getCount() + " " + give : give;
    }
}
