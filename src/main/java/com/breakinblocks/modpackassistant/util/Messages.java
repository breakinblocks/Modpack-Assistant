package com.breakinblocks.modpackassistant.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Messages {
    private static final Map<String, String> ALL = new LinkedHashMap<>();

    public static final Msg PLAYER_ONLY = msg("failed.player_only", "This command can only be run in-game as a player");
    public static final Msg NO_ITEM = msg("failed.no_item", "%s is not holding anything");
    public static final Msg INVALID_SELECTOR = msg("failed.invalid_selector", "Unknown item source '%s'. Valid sources: %s");
    public static final Msg INVALID_FORMAT = msg("failed.invalid_format", "Unknown output format '%s'. Valid formats: %s");
    public static final Msg RADIUS_TOO_LARGE = msg("failed.radius_too_large", "Radius %s is above the configured maximum of %s (%s chunks). Raise %s in the config to allow it");
    public static final Msg DIMENSION_NOT_FOUND = msg("failed.dimension_not_found", "Dimension not found");

    public static final Msg RUN_STARTED = msg("run.started", "Run #%s started: %s. %s jobs queued, estimated %s at the current job interval");
    public static final Msg RUN_UNLOADED = msg("run.unloaded_chunks", "%s of %s chunks are not currently loaded and will be loaded one at a time");
    public static final Msg RUN_REFUSED = msg("run.refused", "%s is already running a %s (%s%% done, %s of %s jobs). Wait for it or use /ma cancel");
    public static final Msg RUN_PROGRESS = msg("run.progress", "Run #%s: %s%% (%s of %s jobs)");
    public static final Msg RUN_FINISHED = msg("run.finished", "Run #%s finished in %s");
    public static final Msg RUN_FAILED = msg("run.failed", "Run #%s failed after %s of %s jobs: %s. Remaining jobs discarded, see the server log");
    public static final Msg RUN_CANCELLED = msg("run.cancelled", "Run #%s (%s, owned by %s) cancelled after %s of %s jobs. Work already done is not rolled back");
    public static final Msg RUN_NOTHING = msg("run.nothing_running", "Nothing running");
    public static final Msg RUN_SERVER_STOPPING = msg("run.server_stopping", "Run #%s cancelled because the server is stopping");

    public static final Msg REPORT_WRITTEN = msg("report.written", "Report written to %s");
    public static final Msg REPORT_FAILED = msg("report.failed", "Could not write report %s: %s");
    public static final Msg REPORT_CLICK = msg("report.click", "Click to copy the path");

    public static final Msg CLIPBOARD_COPIED = msg("clipboard.copied", "Copied to clipboard");
    public static final Msg CLIPBOARD_CLICK = msg("clipboard.click", "Click to copy");

    public static final Msg WEATHER_CLEAR = msg("weather.clear", "Overworld weather set to clear for %s ticks");
    public static final Msg WEATHER_RAIN = msg("weather.rain", "Overworld weather set to rain for %s ticks");

    public static final Msg DEVENV_ON = msg("devenv.on", "Development mode on: daylight cycle, mob spawning and weather cycle disabled, time set to noon in every dimension");
    public static final Msg DEVENV_OFF = msg("devenv.off", "Development mode off: daylight cycle, mob spawning and weather cycle enabled");

    public static final Msg HEAL_DONE = msg("heal.done", "Healed %s");
    public static final Msg HEAL_TARGET = msg("heal.target", "You were healed by %s");
    public static final Msg FEED_DONE = msg("feed.done", "Fed %s");
    public static final Msg FEED_TARGET = msg("feed.target", "You were fed by %s");
    public static final Msg GOD_ON = msg("god.on", "God mode enabled for %s");
    public static final Msg GOD_OFF = msg("god.off", "God mode disabled for %s");
    public static final Msg GOD_TARGET_ON = msg("god.target_on", "%s enabled god mode for you");
    public static final Msg GOD_TARGET_OFF = msg("god.target_off", "%s disabled god mode for you");
    public static final Msg NIGHTVISION_ON = msg("nightvision.on", "Night vision enabled");
    public static final Msg NIGHTVISION_OFF = msg("nightvision.off", "Night vision disabled");
    public static final Msg REPAIR_DONE = msg("repair.done", "%s has been repaired");
    public static final Msg REPAIR_TARGET = msg("repair.target", "%s repaired your %s");
    public static final Msg REPAIR_NOT_DAMAGEABLE = msg("repair.not_damageable", "%s cannot be damaged, so there is nothing to repair");
    public static final Msg OPSWORD_GIVEN = msg("opsword.given", "Given the Opsword");
    public static final Msg OPSWORD_NAME = msg("opsword.name", "Opsword");

    public static final Msg ENCHANT_DONE = msg("enchant.done", "%s has been enchanted with %s");
    public static final Msg ENCHANT_REMOVED = msg("enchant.removed", "%s has been removed from %s");
    public static final Msg ENCHANT_INCOMPATIBLE = msg("enchant.incompatible", "%s cannot take %s: it does not apply to the item or conflicts with %s");
    public static final Msg ENCHANT_MISSING = msg("enchant.missing", "%s does not have %s");

    public static final Msg TPD_DONE = msg("tpd.done", "Moved %s entities to %s");
    public static final Msg TPD_CLEARED = msg("tpd.cleared", "Cleared %s blocks around the arrival point of %s");
    public static final Msg TPD_ENTITY_FAILED = msg("tpd.entity_failed", "Could not move %s: %s");

    public static final Msg PRINT_TAG_CLICK = msg("print.tag_click", "Click to copy the tag");

    public static final Msg KILL_START = msg("kill.start", "Removing %s in %s");
    public static final Msg KILL_START_BYPASS = msg("kill.start_bypass", "Removing every %s in %s, ignoring the kill protection tag");
    public static final Msg KILL_DONE = msg("kill.done", "Removed %s entities");
    public static final Msg KILL_NONE = msg("kill.none", "No %s found");
    public static final Msg KILL_TYPE_ALL = msg("kill.type.all", "non-player entities");
    public static final Msg KILL_TYPE_ANIMALS = msg("kill.type.animals", "animals");
    public static final Msg KILL_TYPE_MONSTERS = msg("kill.type.monsters", "monsters");
    public static final Msg KILL_TYPE_ITEMS = msg("kill.type.items", "dropped items");
    public static final Msg KILL_TYPE_XP = msg("kill.type.xp", "experience orbs");
    public static final Msg KILL_TYPE_PLAYERS = msg("kill.type.players", "players");
    public static final Msg KILL_TYPE_ME = msg("kill.type.me", "yourself");

    public static final Msg REGION_SPAN = msg("region.span", "%s by %s chunks");
    public static final Msg CLEAR_WARNING = msg("clear.warning", "Warning: removing blocks across %s (%s chunks). Expect lag while it runs");
    public static final Msg CLEAR_DONE = msg("clear.done", "Region clear finished: %s blocks removed across %s chunks");
    public static final Msg DRAIN_NO_FLUID = msg("drain.no_fluid", "No fluid found at %s or next to it");
    public static final Msg DRAIN_START = msg("drain.start", "Draining %s from %s within %s blocks");
    public static final Msg DRAIN_DONE = msg("drain.done", "Drained %s blocks of %s");
    public static final Msg DRAIN_TRUNCATED = msg("drain.truncated", "Drain stopped at the %s block cap set by max_drain_blocks. %s blocks removed, the body may continue past that");

    public static final Msg SCAN_NONE = msg("scan.none", "No ores found");
    public static final Msg SCAN_HEADER = msg("scan.header", "Ore distribution for %s, Y %s to %s (total: %s)");
    public static final Msg SCAN_START = msg("scan.start", "Scanning ores across %s (%s chunks), Y %s to %s");
    public static final Msg SCAN_PERCENT = msg("scan.percent", "%s%%");

    public static final Msg MINE_START = msg("mine.start", "Simulating %s mining across %s (%s chunks)");
    public static final Msg MINE_HEADER = msg("mine.header", "Simulated yield for %s (total: %s items)");
    public static final Msg MINE_NONE = msg("mine.none", "No ores found to mine");
    public static final Msg MINE_BARRELS = msg("mine.barrels", "Placed %s barrels starting at %s");
    public static final Msg MINE_UNPLACED = msg("mine.unplaced", "%s item types could not be placed because the grid ran out of free positions");

    public static final Msg CANCEL_PARTIAL = msg("cancel.partial", "Partial results were discarded");

    public static final Msg LOOT_MISSING_PARAMS = msg("loot.missing_params", "%s needs parameters this command cannot supply: %s (parameter set %s)");
    public static final Msg LOOT_START = msg("loot.start", "Rolling %s %s times at luck %s");
    public static final Msg LOOT_HEADER = msg("loot.header", "Loot simulation of %s: %s rolls, %s%% produced nothing");
    public static final Msg LOOT_LINE = msg("loot.line", "%s%% %s (total %s)");

    public static final Msg CONFLICTS_START = msg("conflicts.start", "Scanning %s recipes in %s buckets");
    public static final Msg CONFLICTS_DONE = msg("conflicts.done", "%s conflict groups, %s duplicate groups, %s dynamic recipes skipped");

    public static final Msg SPAWNS_NO_BIOME = msg("spawns.no_biome", "%s does not occur in the sampled chunks of %s. Biomes present: %s");
    public static final Msg SPAWNS_START = msg("spawns.start", "Simulating %s ticks (%s in-game days, about %s real minutes) of spawning in %s across %s sampled chunks");
    public static final Msg SPAWNS_HEADER = msg("spawns.header", "Simulated spawns over %s ticks in %s: %s individuals");

    public static final Msg TAGS_DONE = msg("tags.done", "Exported %s %s entries with %s distinct tags, %s untagged");

    public static final Msg UNIFY_DONE = msg("unify.done", "%s unresolved tags, %s empty tags, %s resolved");

    public static final Msg STRUCTLOOT_NONE = msg("structloot.none", "No loot tables found for %s. Tried: %s");
    public static final Msg STRUCTLOOT_PLACED = msg("structloot.placed", "Placed %s chests for %s loot tables starting at %s (%s confirmed, %s heuristic)");
    public static final Msg STRUCTLOOT_STANDING = msg("structloot.standing", "A previous structure loot test is still standing (%s blocks). Run /ma testStructureLoot clear first");
    public static final Msg STRUCTLOOT_CLEARED = msg("structloot.cleared", "Removed %s blocks from the previous structure loot test");
    public static final Msg STRUCTLOOT_NOTHING = msg("structloot.nothing", "No structure loot test to clear");

    public static final Msg BIOMES_TOO_MANY = msg("biomes.too_many", "%s samples exceeds the budget of %s. Use an interval of at least %s");
    public static final Msg BIOMES_START = msg("biomes.start", "Sampling %s biome points within %s blocks at Y %s, every %s blocks");
    public static final Msg BIOMES_HEADER = msg("biomes.header", "Biome coverage within %s blocks (%s samples, %s biomes)");

    public static final Msg UNCRAFT_DONE = msg("uncraft.done", "%s items with no known source across %s mods, %s creative-only");
    public static final Msg UNCRAFT_START = msg("uncraft.start", "Indexing recipes, %s loot tables, villager trades and creative tabs");

    public static final Msg PERCENT_LINE = msg("line.percent", "%s%% %s");
    public static final Msg SPAWNS_LINE = msg("spawns.line", "%s: %s spawned in %s attempts");
    public static final Msg SPAWNS_SKIPPED = msg("spawns.skipped", "Skipped rules: %s");
    public static final Msg TAGS_START = msg("tags.start", "Exporting %s entries from the %s registry as %s");
    public static final Msg UNIFY_START = msg("unify.start", "Auditing material tags%s");
    public static final Msg UNKNOWN_RECIPE_TYPE = msg("failed.unknown_recipe_type", "Unknown recipe type %s");
    public static final Msg UNKNOWN_LOOT_TABLE = msg("failed.unknown_loot_table", "Unknown loot table %s");
    public static final Msg TOO_MANY_ITERATIONS = msg("failed.too_many_iterations", "%s is above the configured maximum of %s. Raise %s in the config to allow it");

    public record Msg(String key) {
        public MutableComponent get(Object... args) {
            Object[] safe = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                safe[i] = arg instanceof Component || arg instanceof Number || arg instanceof Boolean || arg instanceof String ? arg : String.valueOf(arg);
            }
            return Component.translatable(key, safe);
        }
    }

    private Messages() {
    }

    private static Msg msg(String suffix, String english) {
        String key = "commands.modpackassistant." + suffix;
        ALL.put(key, english);
        return new Msg(key);
    }

    public static Map<String, String> all() {
        return Collections.unmodifiableMap(ALL);
    }
}
