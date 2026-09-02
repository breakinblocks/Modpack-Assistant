package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.JsonWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class TagExporter<T> {
    public record Entry(ResourceLocation id, List<String> tags) {
    }

    private final Registry<T> registry;
    private final List<Holder.Reference<T>> holders;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> distinctTags = new TreeSet<>();
    private int untagged;

    public TagExporter(Registry<T> registry) {
        this.registry = registry;
        this.holders = registry.holders().toList();
    }

    public int size() {
        return holders.size();
    }

    public int distinctTagCount() {
        return distinctTags.size();
    }

    public int untagged() {
        return untagged;
    }

    public String registryName() {
        return registry.key().location().toString();
    }

    public void process(int from, int to) {
        for (int i = from; i < Math.min(to, holders.size()); i++) {
            Holder.Reference<T> holder = holders.get(i);
            List<String> tags = holder.tags().map(TagKey::location).map(ResourceLocation::toString).sorted().toList();
            if (tags.isEmpty()) {
                untagged++;
            }
            distinctTags.addAll(tags);
            entries.add(new Entry(holder.key().location(), tags));
        }
    }

    public String json(ReportWriter.Context context) {
        JsonObject root = new JsonObject();
        root.add("header", context.json());
        JsonObject objects = new JsonObject();
        for (Entry entry : entries) {
            JsonArray tags = new JsonArray();
            entry.tags().forEach(tags::add);
            objects.add(entry.id().toString(), tags);
        }
        root.add("objects", objects);
        return JsonWriter.toPrettyString(root);
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("id", "mod", "tag_count", "tags");
        entries.stream().sorted((a, b) -> a.id().compareTo(b.id())).forEach(entry ->
                csv.row(entry.id(), entry.id().getNamespace(), entry.tags().size(), String.join(";", entry.tags())));
        return csv.content();
    }
}
