# SeedScout

A client-side Fabric mod for Minecraft 26.2 that puts a Chunkbase-style seed map inside the game. Press a key, see the biome map for your world's seed, search for structures, and set a waypoint that guides you there with a HUD arrow and a beam in the world.

## Features

- Biome map generated from the world seed using Minecraft's own world generation code, so biome shapes and structure positions match the game exactly.
- Pan and zoom with three levels of detail. Tiles are computed on a background thread and cached.
- Structure icons for every vanilla overworld structure type, toggleable per type. Toggles are remembered.
- Search by structure type and radius (2000, 5000, 10000, or 50000 blocks). Results are listed nearest first with coordinates and distance, and highlighted on the map.
- Click a result or an icon on the map to set a waypoint. A HUD arrow shows direction and distance, and a colored beam marks the spot through terrain. The waypoint clears itself when you arrive.
- Works on multiplayer servers: enter the seed once per server and it is remembered.

<img width="3840" height="2100" alt="image" src="https://github.com/user-attachments/assets/b0999fe2-5d92-405e-bbe9-d4c443830676" />

## Usage

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 and put [Fabric API](https://modrinth.com/mod/fabric-api) and the SeedScout jar in your `mods` folder.
2. In a world, press `Y` (rebindable in Controls under SeedScout) to open the map.
3. In singleplayer the seed is read automatically. On a server, type the seed in the field at the top and click Apply.
4. Use the toggles in the top bar to choose which structure types are drawn.
5. Pick a structure and radius in the right panel and click Search. Click a result to set a waypoint and close the map.
6. Follow the arrow. Press `Y` again or Escape to close the map at any time. Clear waypoint removes the target.

## Building

Requires JDK 25. Built for Minecraft 26.2; the 1.21.11 version lives on the `1.21.11` branch.

```
./gradlew build
```

The jar is written to `build/libs`.

## Configuration

Settings are stored in `config/seedscout.json`: saved seeds per server address, enabled structure toggles, and the last search radius.

## Limitations

- Overworld only.
- Structure validation is biome based. Woodland mansions, ocean monuments, desert pyramids, jungle temples, and trial chambers can occasionally show a position where the game decided not to generate the structure.
- Servers with custom world generation data packs are not supported; the map assumes vanilla generation.
- The search is centered on the map's current center, so click Center on player first if you want results relative to your position.
- The first time the map opens in a game session it takes a second or two while world generation data loads.
- Very dense structure types (mineshafts, buried treasure) are searched within a smaller effective radius, and their icons are hidden when zoomed far out.

## License

SeedScout is released under the [Apache License 2.0](LICENSE). You may use, modify, and redistribute it, including in commercial projects, as long as you keep the copyright and license notices and note any changes you made.
