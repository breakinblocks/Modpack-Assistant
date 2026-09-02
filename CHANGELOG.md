# Changelog

## Unreleased

### Changed

- Ported to Minecraft 26.1.2 / NeoForge 26.1.2.x, built on Java 25.
- Weather and world time are now server-wide, so `/toggledownfall` and `/ma devenv` set them through the server rather than per level. `devenv` sets every registered world clock to noon.
- Permission checks use the 26.1 permission API. `/ma devenv` toggles the renamed `advance_time`, `spawn_mobs` and `advance_weather` game rules.
- Villager trade sources for `/ma findUncraftables` are read from the `villager_trade` datapack registry instead of the removed hardcoded trade tables.
- Recipe inputs and results for `/ma findConflicts` come from `PlacementInfo` and recipe displays.
- The structure loot test record is stored as server saved data at `data/modpackassistant/test_loot.dat`.
- Datagen moved from `runData` to `runClientData`; the GameTest template moved to `data/modpackassistant/structure/empty.nbt`.

## 1.0.0

### Added

- `/modpackassistant` command root with `/ma` alias and lowercase aliases for every camel-case subcommand.
- Admin commands: `toggledownfall`, `devenv`, `opsword`, `enchant add/remove`, `repair`, `heal`, `feed`, `god`, `nightvision`, `tpd`.
- Item data extraction: `print`, `hand`, `copy` with eight item sources and nine output formats, including component syntax, NBT, SNBT, JSON, KubeJS, CraftTweaker and CSV.
- Region editing: `clear` with explicit `keep` and `remove` grammar and bedrock protection on by default, `drain` with a bounded incremental flood fill, `kill` with a data-driven protection tag, `minearea` with selectable harvest mode, `testStructureLoot` with signed chests and a `clear` that removes exactly what was placed.
- Analysis reports under `logs/modpackassistant/`: `scanOres`, `simulateLoot`, `simulateSpawns`, `findConflicts`, `findUncraftables`, `auditUnification`, `exportTags`, `mapBiomes`.
- A shared tick-paced job scheduler for every long-running operation, with progress reporting, overlap refusal and `cancel`.
- Common config for permissions, limits, the report directory and the job interval.
- GameTest suite covering the scheduler, region geometry, report formats, and the world-editing commands.
