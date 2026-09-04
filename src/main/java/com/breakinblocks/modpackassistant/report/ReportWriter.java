package com.breakinblocks.modpackassistant.report;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.util.Messages;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportWriter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    public enum Family {
        LOOT("loot"),
        RECIPES("recipes"),
        ORES("ores"),
        SPAWNS("spawns"),
        TAGS("tags"),
        BIOMES("biomes"),
        BLOCKS("blocks"),
        REGISTRY("registry");

        private final String directory;

        Family(String directory) {
            this.directory = directory;
        }
    }

    public static final class Context {
        private final Map<String, String> entries = new LinkedHashMap<>();

        public Context(CommandSourceStack source, String command) {
            ServerLevel level = source.getLevel();
            entries.put("mod", ModpackAssistant.MOD_ID + " " + ModList.get().getModContainerById(ModpackAssistant.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown"));
            entries.put("minecraft", SharedConstants.getCurrentVersion().name());
            entries.put("command", command);
            entries.put("caller", source.getTextName());
            entries.put("dimension", level.dimension().identifier().toString());
            entries.put("seed", Long.toString(level.getSeed()));
            entries.put("timestamp", ISO.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }

        public Context note(String key, Object value) {
            entries.put(key, String.valueOf(value));
            return this;
        }

        public List<String> headerLines() {
            List<String> lines = new ArrayList<>();
            entries.forEach((key, value) -> lines.add(key + ": " + value));
            return lines;
        }

        public List<String> commentLines() {
            List<String> lines = new ArrayList<>();
            entries.forEach((key, value) -> lines.add("# " + key + ": " + value));
            return lines;
        }

        public JsonObject json() {
            JsonObject object = new JsonObject();
            entries.forEach(object::addProperty);
            return object;
        }
    }

    private ReportWriter() {
    }

    public static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path directory(Family family) {
        return gameDirectory().resolve(MAConfig.reportDirectory()).resolve(family.directory);
    }

    public static Path file(Family family, String subject, String extension) {
        String stamp = STAMP.format(ZonedDateTime.now(ZoneOffset.UTC));
        return directory(family).resolve(sanitize(subject) + "-" + stamp + "." + extension);
    }

    public static String sanitize(String subject) {
        String cleaned = subject.replace(':', '_').replace('/', '_').replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "report" : cleaned;
    }

    public static void writeLines(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    public static void writeString(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public static String relative(Path path) {
        return gameDirectory().relativize(path).toString().replace('\\', '/');
    }

    public static MutableComponent pathMessage(Path path) {
        String relative = relative(path);
        MutableComponent link = Component.literal(relative).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(relative))
                .withHoverEvent(new HoverEvent.ShowText(Messages.REPORT_CLICK.get())));
        return Messages.REPORT_WRITTEN.get(link);
    }

    public static Component failureMessage(Path path, IOException error) {
        return Messages.REPORT_FAILED.get(relative(path), String.valueOf(error.getMessage()));
    }

    @Nullable
    public static Path deliver(Run run, Family family, String subject, String extension, String content) {
        Path path = file(family, subject, extension);
        try {
            writeString(path, content);
            run.message(pathMessage(path));
            return path;
        } catch (IOException e) {
            ModpackAssistant.LOGGER.error("Failed to write report {}", path, e);
            run.message(failureMessage(path, e));
            return null;
        }
    }
}
