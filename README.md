# SeedScout

A client-side Fabric mod for Minecraft 26.2 that puts a Chunkbase-style seed map inside the game. Press a key, see the biome map for your world's seed, search for structures, and set a waypoint that guides you there with a HUD arrow and a beam in the world.

## Features

- Biome map generated from the world seed using Minecraft's own world generation code, so biome shapes and structure positions match the game exactly. Overworld, Nether, and End.
- Pan and zoom with three levels of detail. Tiles are computed on a background thread and cached.
- Structure icons for every vanilla structure type in the current dimension, toggleable per type. Toggles are remembered.
- Search by structure type and radius (2000, 5000, 10000, or 50000 blocks). Results are listed nearest first with coordinates and distance, and highlighted on the map.
- Click a result or an icon on the map, or right-click anywhere on the map, to set a waypoint. A HUD arrow shows direction and distance, and a colored beam marks the spot through terrain. The waypoint clears itself when you arrive.
- Slime chunk overlay and world spawn marker.
- Works on multiplayer servers: enter the seed once per server and it is remembered.

<img width="3840" height="2100" alt="image" src="https://github.com/user-attachments/assets/b0999fe2-5d92-405e-bbe9-d4c443830676" />

## Usage

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 and put [Fabric API](https://modrinth.com/mod/fabric-api) and the SeedScout jar in your `mods` folder.
2. In a world, press `Y` (rebindable in Controls under SeedScout) to open the map.
3. In singleplayer the seed is read automatically. On a server, type the seed in the field at the top of the side panel and click Apply.
4. The map opens on your current dimension; switch with the Overworld, Nether, and End tabs. In the structure grid, left-click an icon to show or hide that type on the map and right-click it to make it the search target.
5. Pick Structures or Biomes, a target and a radius in the right panel, and click Search. Click a result to set a waypoint and close the map. Right-click anywhere on the map to drop a marker.
6. Follow the arrow. The arrow and beam show only while you are in the target's dimension. Press `Y` again or Escape to close the map at any time. Clear waypoint removes the target.

## Building

Requires JDK 25. Built for Minecraft 26.2; the 1.21.11 version lives on the `1.21.11` branch.

```
./gradlew build
```

The jar is written to `build/libs`.

## Configuration

Settings are stored in `config/seedscout.json`: saved seeds per server address, enabled structure toggles, and the last search radius.

## Limitations

- Structure validation mirrors the game's biome and terrain checks for each structure type, but does not assemble jigsaw pieces, so a village or outpost can very rarely be shown where the game skipped it.
- Servers with custom world generation data packs are not supported; the map assumes vanilla generation.
- The search is centered on the map's current center, so click Center on player first if you want results relative to your position.
- The first time the map opens in a game session it takes a second or two while world generation data loads.
- Very dense structure types (mineshafts, buried treasure) are searched within a smaller effective radius, and their icons are hidden when zoomed far out.

## License

SeedScout is released under the [Apache License 2.0](LICENSE). You may use, modify, and redistribute it, including in commercial projects, as long as you keep the copyright and license notices and note any changes you made.
