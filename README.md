# Modpack Assistant

Server-side commands for modpack development and server administration on NeoForge 26.1.2.
World inspection, region editing, loot and spawn simulation, recipe and tag audits, and the usual
admin conveniences. No blocks, items, or GUIs. Vanilla clients work with every feature except
automatic clipboard copying, which falls back to click-to-copy chat.

Every command is available as `/modpackassistant ...` or `/ma ...`. Camel-case names also accept
their lowercase spelling (`/ma scanores` works the same as `/ma scanOres`).

## Commands

Admin and player (permission level 2 unless noted):

| Command | Purpose |
|---|---|
| `/toggledownfall` | Flip overworld weather between clear and rain |
| `/ma devenv <true/false>` | Freeze or restore daylight, weather and mob spawning |
| `/ma opsword` | Give a netherite sword enchanted to level 255 |
| `/ma enchant add <enchantment> <0-255>` | Enchant the held item past normal limits |
| `/ma enchant remove <enchantment>` | Strip one enchantment from the held item |
| `/ma repair [player]` | Repair the held item |
| `/ma heal [player]`, `/ma feed [player]` | Restore health and hunger |
| `/ma god [player]` | Toggle invulnerability |
| `/ma nightvision` | Toggle permanent night vision |
| `/ma tpd <dimension> [targets]` | Move entities to another dimension safely |
| `/ma print <source>`, `/ma hand`, `/ma copy <source> [format]` | Item data to chat or clipboard (permission configurable) |

World editing:

| Command | Purpose |
|---|---|
| `/ma clear <radius> [keep <ores/ores_and_modded/nothing> / remove <predicate>] [protect_bedrock]` | Mass-delete blocks across a chunk region |
| `/ma drain [location] <radius>` | Flood-fill remove a connected body of fluid |
| `/ma kill <type>`, `/ma kill by <entity>` | Bulk entity removal |
| `/ma minearea <radius> [harvest]` | Simulate mining every ore in a region and bank the drops in barrels |
| `/ma testStructureLoot <structure> [samples]`, `... clear` | Chests of generated loot per structure loot table, with signs |
| `/ma cancel` | Abort the active long-running operation |

Analysis and reports, all read-only, each writing a file under `logs/modpackassistant/`:

| Command | Purpose |
|---|---|
| `/ma scanOres <chunk_radius> [min_y] [max_y]` | Ore distribution by block and by height |
| `/ma locateBlock <block> <chunk_radius>` | Every placement of one block in a region, nearest first with click-to-teleport coordinates |
| `/ma simulateLoot <iterations> <loot_table> [luck]` | Drop statistics for a loot table |
| `/ma simulateSpawns <biome> <dimension> <ticks>` | Estimated natural spawning without placing entities |
| `/ma findConflicts [type]` | Recipes that consume the same inputs |
| `/ma findUncraftables [namespace]` | Items with no recipe, loot table, or trade source |
| `/ma auditUnification [namespace]` | Material tags holding several items, or none |
| `/ma exportTags <item/block/entity/fluid> [json/csv]` | Every registered object with its tags |
| `/ma mapBiomes <radius> [interval] [y]` | Biome coverage over an area, without loading chunks |

Long-running operations run as jobs on the server tick, one every few ticks, and only one at a
time. They report progress and can be stopped with `/ma cancel`.

## Configuration

`config/modpackassistant-common.toml` holds the permission level for item inspection, the radius,
iteration, and block caps for each expensive command, the report directory, and the job interval.

## License

MIT, see [LICENSE.md](LICENSE.md).
