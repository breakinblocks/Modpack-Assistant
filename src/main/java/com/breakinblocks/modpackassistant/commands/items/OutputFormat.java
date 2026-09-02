package com.breakinblocks.modpackassistant.commands.items;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.JsonWriter;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public enum OutputFormat {
    PLAIN("plain", OutputFormat::plain),
    COMPONENTS("components", OutputFormat::components),
    NBT("nbt", (items, lookup) -> itemsTag(items, lookup).toString()),
    SNBT("snbt", (items, lookup) -> new SnbtPrinterTagVisitor().visit(itemsTag(items, lookup))),
    JSON("json", OutputFormat::json),
    KUBEJS("kubejs", OutputFormat::kubejs),
    KUBEJS_NATIVE("kubejs_native", OutputFormat::kubejsNative),
    CRAFTTWEAKER("crafttweaker", OutputFormat::crafttweaker),
    CSV("csv", OutputFormat::csv);

    private static final OutputFormat[] VALUES = values();
    private static final Gson COMPACT = new Gson();
    private static final String INDENT = "  ";

    private final String name;
    private final BiFunction<List<ItemStack>, HolderLookup.Provider, String> writer;

    OutputFormat(String name, BiFunction<List<ItemStack>, HolderLookup.Provider, String> writer) {
        this.name = name;
        this.writer = writer;
    }

    public String formatName() {
        return name;
    }

    public String write(List<ItemStack> items, HolderLookup.Provider lookup) {
        return writer.apply(items, lookup);
    }

    @Nullable
    public static OutputFormat byName(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        for (OutputFormat format : VALUES) {
            if (format.name.equals(lower)) {
                return format;
            }
        }
        return null;
    }

    public static String names() {
        return Arrays.stream(VALUES).map(OutputFormat::formatName).collect(Collectors.joining(", "));
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(VALUES).map(OutputFormat::formatName), builder);
    }

    private static String plain(List<ItemStack> items, HolderLookup.Provider lookup) {
        return items.stream()
                .map(stack -> stack.getCount() + " " + ItemStrings.giveString(stack, lookup))
                .collect(Collectors.joining("\n"));
    }

    private static String components(List<ItemStack> items, HolderLookup.Provider lookup) {
        return items.stream()
                .map(stack -> ItemStrings.giveString(stack, lookup) + " " + stack.getCount())
                .collect(Collectors.joining("\n"));
    }

    private static CompoundTag itemsTag(List<ItemStack> items, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        var ops = lookup.createSerializationContext(NbtOps.INSTANCE);
        for (ItemStack stack : items) {
            Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
            list.add(encoded);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("items", list);
        return tag;
    }

    private static String json(List<ItemStack> items, HolderLookup.Provider lookup) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : items) {
            JsonObject object = new JsonObject();
            object.addProperty("item", ItemStrings.itemId(stack));
            object.addProperty("count", stack.getCount());
            String block = ItemStrings.componentBlock(stack, lookup);
            if (!block.isEmpty()) {
                object.addProperty("components", block);
            }
            array.add(object);
        }
        return JsonWriter.toPrettyString(array);
    }

    private static String kubejs(List<ItemStack> items, HolderLookup.Provider lookup) {
        List<String> entries = new ArrayList<>();
        for (ItemStack stack : items) {
            List<String> fields = new ArrayList<>();
            fields.add(INDENT + INDENT + "item: " + quote(ItemStrings.itemId(stack)));
            fields.add(INDENT + INDENT + "count: " + stack.getCount());
            String block = ItemStrings.componentBlock(stack, lookup);
            if (!block.isEmpty()) {
                fields.add(INDENT + INDENT + "components: " + quote(block));
            }
            entries.add(INDENT + "{\n" + String.join(",\n", fields) + "\n" + INDENT + "}");
        }
        return "[\n" + String.join(",\n", entries) + "\n]";
    }

    private static String kubejsNative(List<ItemStack> items, HolderLookup.Provider lookup) {
        List<String> entries = new ArrayList<>();
        for (ItemStack stack : items) {
            String block = ItemStrings.componentBlock(stack, lookup);
            if (block.isEmpty()) {
                String prefix = stack.getCount() > 1 ? stack.getCount() + "x " : "";
                entries.add(INDENT + quote(prefix + ItemStrings.itemId(stack)));
            } else {
                entries.add(INDENT + "Item.of(" + quote(ItemStrings.itemId(stack) + block) + ", " + stack.getCount() + ")");
            }
        }
        return "[\n" + String.join(",\n", entries) + "\n]";
    }

    private static String crafttweaker(List<ItemStack> items, HolderLookup.Provider lookup) {
        List<String> entries = new ArrayList<>();
        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        for (ItemStack stack : items) {
            StringBuilder builder = new StringBuilder(INDENT).append("<item:").append(ItemStrings.itemId(stack)).append('>');
            DataComponentPatch patch = stack.getComponentsPatch();
            if (!patch.isEmpty()) {
                JsonElement encoded = DataComponentPatch.CODEC.encodeStart(ops, patch).getOrThrow();
                for (Map.Entry<String, JsonElement> entry : encoded.getAsJsonObject().entrySet()) {
                    if (entry.getKey().startsWith("!")) {
                        continue;
                    }
                    builder.append(".withJsonComponent(<componenttype:").append(entry.getKey()).append(">, ").append(COMPACT.toJson(entry.getValue())).append(')');
                }
            }
            if (stack.getCount() > 1) {
                builder.append(" * ").append(stack.getCount());
            }
            entries.add(builder.toString());
        }
        return "[\n" + String.join(",\n", entries) + "\n]";
    }

    private static String csv(List<ItemStack> items, HolderLookup.Provider lookup) {
        CsvWriter csv = new CsvWriter().row("item", "count", "components");
        for (ItemStack stack : items) {
            csv.row(ItemStrings.itemId(stack), stack.getCount(), ItemStrings.componentBlock(stack, lookup));
        }
        return csv.content().stripTrailing();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
