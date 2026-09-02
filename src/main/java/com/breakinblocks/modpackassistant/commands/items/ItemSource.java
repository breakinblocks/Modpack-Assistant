package com.breakinblocks.modpackassistant.commands.items;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ItemSource {
    HAND("hand", player -> single(player.getMainHandItem())),
    OFFHAND("offhand", player -> single(player.getOffhandItem())),
    HOTBAR("hotbar", player -> slots(player, 0, Inventory.getSelectionSize())),
    INVENTORY("inventory", player -> slots(player, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE)),
    INVENTORY_AND_HOTBAR("inventory_and_hotbar", player -> slots(player, 0, Inventory.INVENTORY_SIZE)),
    ARMOR("armor", player -> nonEmpty(player.getInventory().armor)),
    EVERYTHING("everything", player -> {
        List<ItemStack> items = slots(player, 0, Inventory.INVENTORY_SIZE);
        items.addAll(nonEmpty(player.getInventory().armor));
        items.addAll(nonEmpty(player.getInventory().offhand));
        return items;
    }),
    TARGET_INVENTORY("target_inventory", ItemSource::targetInventory);

    public static final double TARGET_RANGE = 20.0D;
    private static final ItemSource[] VALUES = values();

    private final String name;
    private final Function<ServerPlayer, List<ItemStack>> collector;

    ItemSource(String name, Function<ServerPlayer, List<ItemStack>> collector) {
        this.name = name;
        this.collector = collector;
    }

    public String sourceName() {
        return name;
    }

    public List<ItemStack> collect(ServerPlayer player) {
        return collector.apply(player);
    }

    @Nullable
    public static ItemSource byName(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        for (ItemSource source : VALUES) {
            if (source.name.equals(lower)) {
                return source;
            }
        }
        return null;
    }

    public static String names() {
        return Arrays.stream(VALUES).map(ItemSource::sourceName).collect(Collectors.joining(", "));
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(VALUES).map(ItemSource::sourceName), builder);
    }

    private static List<ItemStack> single(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>(1);
        if (!stack.isEmpty()) {
            items.add(stack);
        }
        return items;
    }

    private static List<ItemStack> slots(ServerPlayer player, int from, int to) {
        List<ItemStack> items = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    private static List<ItemStack> nonEmpty(Iterable<ItemStack> stacks) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    private static List<ItemStack> targetInventory(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        HitResult hit = player.pick(TARGET_RANGE, 0.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return items;
        }
        IItemHandler handler = player.level().getCapability(Capabilities.ItemHandler.BLOCK, blockHit.getBlockPos(), blockHit.getDirection());
        if (handler == null) {
            return items;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }
}
