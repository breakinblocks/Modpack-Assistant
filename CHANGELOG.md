# Changelog

## 26.1.2-1.0.2

### Added

- `/ma locateBlock <block> <chunk_radius>` finds every placement of a block across a chunk region,
  lists the nearest ten in chat with click-to-teleport coordinates, and writes the full list to
  `logs/modpackassistant/blocks/`. Handy for checking that a worldgen feature is actually placing.

## 26.1.2-1.0.1

### Fixed

- `/ma testStructureLoot` now takes the structure as a registry key argument, matching vanilla `/place structure`. 

## 26.1.2-1.0.0

Initial Release
