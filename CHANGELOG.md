# Changelog

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
