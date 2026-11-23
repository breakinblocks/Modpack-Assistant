# Changelog

## 26.3-1.0.9

First release for Minecraft 26.3 and NeoForge 26.3.0.115

### Added

- `/mpa chunkthroughput <radius> [x] [z]` generates every chunk within a circle of that many chunks
  around the given block position (or the player, or world spawn) and reports how many chunks per
  second the server generated, overall and per worker thread. Chunks that were already loaded are
  counted separately. The run shows progress and can be stopped with `/mpa cancel`. Radius goes up
  to 5000 chunks. Contributed by @Asbestosstar in breakinblocks/Modpack-Assistant#5.
- `/mpa showBarriers on` draws every barrier block as a see-through red cube with the barrier
  sign, with no faces between neighbouring barriers, so walls and floors read as one surface.
  `/mpa showBarriers off` hides them again. The setting is saved in the client config, starts off,
  and needs the mod on the client. Based on Show Barriers by Rylex
  (LegendaryRylex/showbarriers, MIT).

### Changed

- `/mpa simulateSpawns` reads the spawn list from the dimension's natural mob spawns with the
  biome's applied on top, since 26.3 moved spawn settings off the biome. Timeline changes to spawns
  are not included, and the report header says so.
- The loot safety check behind `/mpa simulateLoot` and `/mpa testStructureLoot` follows the 26.3
  loot format, where modifiers, conditions and nested tables can be plain references. Tag
  references to modifiers, conditions or tables are refused.
- The config file is now `config/modpackassistant-local.toml`, since 26.3 replaced common configs
  with local ones. Settings in an old `modpackassistant-common.toml` are not carried over; copy
  them across by hand.

## 26.1.2-1.0.9

### Added

- `/mpa chunkthroughput <radius> [x] [z]` generates every chunk within a circle of that many chunks
  around the given block position (or the player, or world spawn) and reports how many chunks per
  second the server generated, overall and per worker thread. Chunks that were already loaded are
  counted separately. The run shows progress and can be stopped with `/mpa cancel`. Radius goes up
  to 5000 chunks. Contributed by @Asbestosstar in breakinblocks/Modpack-Assistant#5.
- `/mpa showBarriers on` draws every barrier block as a see-through red cube with the barrier
  sign, with no faces between neighbouring barriers, so walls and floors read as one surface.
  `/mpa showBarriers off` hides them again. The setting is saved in the client config, starts off,
  and needs the mod on the client. Based on Show Barriers by Rylex
  (LegendaryRylex/showbarriers, MIT).

## 26.1.2-1.0.8

### Added

- `/mpa showoff player <name|UUID>` opens the showoff preview on a player model wearing that
  player's skin, including outer layers and slim arms. The name or UUID can also be typed into the
  preview of `/mpa showoff entity minecraft:mannequin`.
- A Limbs panel in the player preview sets pitch, yaw and roll for the head, body, arms and legs,
  from -180 to 180 degrees each.
- An equipment popup in the player preview fills the armor and hand slots from a searchable item
  list. Equipment follows the limb pose and shows in screenshots.
- `ShowoffClient.capture(...)` renders a structure, entity or mannequin straight to a PNG file
  without opening the preview, for client mods and tools that make images in bulk. Mannequin
  captures take `equipment` and `Pose` NBT and wait for the skin to download. The README has the
  details.

## 26.1.2-1.0.7

### Added

- Yaw and Pitch sliders under the showoff preview set either angle on its own, in whole degrees.
  Scroll over a slider to step it by one degree.

### Changed

- A newly opened showoff preview keeps the yaw and pitch of the last one until the game is closed,
  whether they were set by a slider, a drag or `/mpa showoff angle`. Zoom and pan still reset.
  Reset view goes back to the default angle for later previews too. Scripts that need a known angle
  should send `angle` or `reset` after opening.

## 26.1.2-1.0.6

### Fixed

- Sped up `/mpa findConflicts` on large packs.

## 26.1.2-1.0.5

### Added

- `/mpa showoff structure <template>`, `/mpa showoff file <name>` and `/mpa showoff entity <entity> [nbt]`
  open an isometric preview of a structure template, a structure file from the structure grab folder,
  or an entity, with drag to rotate, right-drag to pan, scroll to zoom, a 16 colour or transparent
  background, and a screenshot button that saves only the preview.
  `angle`, `zoom`, `pan`, `reset`, `background`, `screenshot` and `close` subcommands do the same
  from commands, so a script or MCP server can drive it through `/execute as <player>`. Needs the
  mod on the client.

### Changed

- `/mpa opsword` no longer puts Knockback on the sword.

### Fixed

- `/mpa copy` run by a fake player, such as a mod's automation, no longer fails with an error; it
  prints the click-to-copy text instead.

## 26.1.2-1.0.4

### Added

- `/mpa structureGrab <from> <to> [name]` saves a region of the world as a structure file, taking
  its corners the way `/fill` does, or from `pos1`/`pos2` corners set while walking a build.
  Writes both `.nbt` and `.snbt` into `modpackassistant/structures/`.

### Changed

- The short command alias is now `/mpa` instead of `/ma`, which clashed with Mystical Agriculture.

## 26.1.2-1.0.3

### Fixed

- Fixed analysis scans generating terrain and bounded retained block-search results.
- Fixed unsafe loot simulation functions, referenced resources, global modifiers, and excessive workloads.
- Fixed vanilla-client command arguments, English message fallbacks, and argument-free lowercase aliases.
- Corrected recipe matching for ingredient reassignment, mirrors, shaped/shapeless overlaps, and 26.1.2 empty-slot layouts.
- Fixed unsupported recipe displays being misclassified or interrupting scans
- Split recipe comparisons, entity removal, structure loot work, and registry reviews into cancellable scheduled batches.
- Fixed oversized clipboard exports and omitted redundant single-item quantities in plain output.
- Fixed unsafe teleport destinations and kept destination chunks active with temporary portal tickets.
- Fixed mining output losing item components.
- Fixed report filename collisions and prevented existing reports from being overwritten.
- Fixed partial structure-loot placement and cleanup records being lost on cancellation or failure.
- Fixed ore scans potentially accepting height bands entirely outside the dimension.
- Fixed drain jobs removing replacement fluids that no longer matched the selected fluid.
- Corrected obtainability reports to identify unreadable sources and qualify registered trade availability.

### Added

- Added regression tests

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
