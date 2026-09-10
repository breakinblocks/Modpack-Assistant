# Changelog

## 1.21.1-1.0.3

### Changed

- Changed `/ma copy` output to use a quantity of 1 by default while retaining larger stack counts and
  item components ([#1](https://github.com/breakinblocks/Modpack-Assistant/issues/1)).

### Fixed

- Prevented read-only region scans from generating chunks and set a bounds for block-search results.
- Added loot-table dependency validation before simulation and excluded unsafe functions and global loot modifiers.
- Restored vanilla message fallbacks, argument suggestions, and lowercase aliases.
- Corrected shapeless matching, mirrored shaped recipes, and shaped/shapeless conflict detection.
- Made recipe comparisons, entity removal, and structure-loot testing bounded and cancellable.
- Corrected spawn simulation to honor declared pack sizes and recheck loaded chunks during simulation.
- Added rejection of oversized clipboard exports before network encoding.
- Required safe supported dimension-teleport destinations without clearing blocks.
- Preserved component mining drops and prevented report-file overwrites.

## 1.21.1-1.0.2

### Added

- Added `/ma locateBlock <block> <chunk_radius>` to find every placement of a block across a chunk region,
  list the nearest ten in chat with click-to-teleport coordinates, and write the full list to
  `logs/modpackassistant/blocks/`. Handy for checking that a worldgen feature is actually placing.

## 1.21.1-1.0.1

### Fixed

- Corrected `/ma testStructureLoot` to take the structure as a registry key argument, matching vanilla `/place structure`.

## 1.21.1-1.0.0

Initial Release
