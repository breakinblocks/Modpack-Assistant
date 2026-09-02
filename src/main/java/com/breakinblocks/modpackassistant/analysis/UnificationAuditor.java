package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class UnificationAuditor {
    private static final Set<String> CONVENTIONAL_NAMESPACES = Set.of("c", "forge");

    public record TagReport(String registry, ResourceLocation tag, List<ResourceLocation> entries) {
        public String family() {
            String path = tag.getPath();
            int slash = path.indexOf('/');
            return slash < 0 ? path : path.substring(0, slash);
        }
    }

    private final List<TagReport> unresolved = new ArrayList<>();
    private final List<TagReport> empty = new ArrayList<>();
    private final Map<String, int[]> familyCounts = new TreeMap<>();
    private final Object2IntOpenHashMap<String> surplusByMod = new Object2IntOpenHashMap<>();
    @Nullable
    private final String namespaceFilter;
    private int resolved;

    public UnificationAuditor(@Nullable String namespaceFilter) {
        this.namespaceFilter = namespaceFilter;
    }

    public int unresolvedCount() {
        return unresolved.size();
    }

    public int emptyCount() {
        return empty.size();
    }

    public int resolvedCount() {
        return resolved;
    }

    public boolean isMaterialTag(TagKey<?> tag) {
        ResourceLocation id = tag.location();
        boolean namespaceOk = namespaceFilter != null ? id.getNamespace().equals(namespaceFilter) : CONVENTIONAL_NAMESPACES.contains(id.getNamespace());
        return namespaceOk && id.getPath().indexOf('/') > 0;
    }

    public <T> void audit(Registry<T> registry) {
        String registryName = registry.key().location().getPath();
        registry.getTags().forEach(pair -> {
            TagKey<T> tag = pair.getFirst();
            if (!isMaterialTag(tag)) {
                return;
            }
            HolderSet.Named<T> set = pair.getSecond();
            List<ResourceLocation> entries = new ArrayList<>();
            for (Holder<T> holder : set) {
                holder.unwrapKey().map(ResourceKey::location).ifPresent(entries::add);
            }
            entries.sort(Comparator.naturalOrder());
            TagReport report = new TagReport(registryName, tag.location(), entries);
            int[] counts = familyCounts.computeIfAbsent(registryName + ":" + report.family(), ignored -> new int[3]);
            if (entries.isEmpty()) {
                empty.add(report);
                counts[1]++;
            } else if (entries.size() == 1) {
                resolved++;
                counts[2]++;
            } else {
                unresolved.add(report);
                counts[0]++;
                for (int i = 1; i < entries.size(); i++) {
                    surplusByMod.addTo(entries.get(i).getNamespace(), 1);
                }
            }
        });
    }

    public String log(ReportWriter.Context context) {
        List<String> lines = new ArrayList<>(context.commentLines());
        lines.add("");
        lines.add("Unresolved tags (" + unresolved.size() + ")");
        lines.add("=".repeat(60));
        unresolved.stream().sorted(Comparator.comparingInt((TagReport r) -> r.entries().size()).reversed().thenComparing(r -> r.tag().toString())).forEach(report -> {
            lines.add("[" + report.registry() + "] #" + report.tag() + " (" + report.entries().size() + ")");
            for (ResourceLocation entry : report.entries()) {
                lines.add("    " + entry + "  (" + entry.getNamespace() + ")");
            }
        });
        lines.add("");
        lines.add("Empty tags (" + empty.size() + ")");
        lines.add("=".repeat(60));
        empty.stream().sorted(Comparator.comparing(r -> r.tag().toString())).forEach(report -> lines.add("[" + report.registry() + "] #" + report.tag()));
        lines.add("");
        lines.add("Resolved tags: " + resolved);
        lines.add("");
        lines.add("Per family (unresolved / empty / resolved)");
        lines.add("=".repeat(60));
        familyCounts.forEach((family, counts) -> lines.add(family + ": " + counts[0] + " / " + counts[1] + " / " + counts[2]));
        lines.add("");
        lines.add("Surplus entries by mod");
        lines.add("=".repeat(60));
        surplusByMod.object2IntEntrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getIntValue()));
        return String.join("\n", lines) + "\n";
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("registry", "tag", "family", "status", "entry_count", "entries");
        for (TagReport report : unresolved) {
            csv.row(report.registry(), report.tag(), report.family(), "unresolved", report.entries().size(), join(report.entries()));
        }
        for (TagReport report : empty) {
            csv.row(report.registry(), report.tag(), report.family(), "empty", 0, "");
        }
        return csv.content();
    }

    private static String join(List<ResourceLocation> entries) {
        StringBuilder builder = new StringBuilder();
        for (ResourceLocation entry : entries) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(entry);
        }
        return builder.toString();
    }
}
