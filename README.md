# SeedScout

A client-side Fabric mod for Minecraft 26.2 that puts a Chunkbase-style seed map inside the game. Press a key, see the biome map for your world's seed, search for structures, and set a waypoint that guides you there with a HUD arrow and a beam in the world.

## Features

- Biome map generated from the world seed using Minecraft's own world generation code, so biome shapes and structure positions match the game exactly. Overworld, Nether, and End.
- Pan and zoom with six levels of detail. Tiles are computed on background threads, shown blurry first and sharpened as they finish, prefetched around the view, and cached on disk so a seed you have looked at before opens instantly.
- Structure icons for every structure type in the current dimension, toggleable per type. Toggles are remembered. When icons would overlap, they merge into one icon with a count; click it to zoom in.
- Search by structure type or biome and radius (2000, 5000, 10000, or 50000 blocks). Results are listed nearest first with coordinates and distance, and highlighted on the map. Right-click a result to copy its coordinates.
- Biome Y selector for the Overworld: view the map at Y 64, 32, 0, -40, or -60 to find cave biomes such as lush caves, dripstone caves, and deep dark.
- Click a result or an icon on the map, or right-click anywhere on the map, to set a waypoint. A HUD arrow shows direction and distance, and a colored beam marks the spot through terrain. The waypoint clears itself when you arrive.
- Go to any coordinates by typing them into the panel, copy the waypoint or map center to the clipboard, and see which way you are facing on the map.
- Slime chunk overlay, chunk and region grid, and world spawn marker.
- In singleplayer the map uses the world's own generation settings, so data packs and mods that change biomes or structure placement (Terralith, for example) are shown correctly.
- Works on multiplayer servers: enter the seed once per server and it is remembered.

<img width="3840" height="2100" alt="image" src="https://github.com/user-attachments/assets/b0999fe2-5d92-405e-bbe9-d4c443830676" />

## Usage

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 and put [Fabric API](https://modrinth.com/mod/fabric-api) and the SeedScout jar in your `mods` folder.
2. In a world, press `Y` (rebindable in Controls under SeedScout) to open the map.
3. In singleplayer the seed is read automatically. On a server, type the seed in the field at the top of the side panel and click Apply.
4. The map opens on your current dimension; switch with the Overworld, Nether, and End tabs. In the structure grid, left-click an icon to show or hide that type on the map and right-click it to make it the search target.
5. Pick Structures or Biomes, a target and a radius in the right panel, and click Search. Click a result to set a waypoint and close the map, or right-click it to copy its coordinates. Right-click anywhere on the map for a menu: set a waypoint there, copy the coordinates, or teleport (sends a `/tp` command, so it only works where you have permission).
6. Type coordinates such as `640 816` or `x=640 z=816` into the Go to field and press Enter to jump there. The Biome Y row switches the Overworld map between surface and cave layers.
7. Follow the arrow. The arrow and beam show only while you are in the target's dimension. Press `Y` again or Escape to close the map at any time. Clear removes the waypoint, Copy puts its coordinates on the clipboard.

## Building

Requires JDK 25. Built for Minecraft 26.2; the 1.21.11 version lives on the `1.21.11` branch.

```
./gradlew build
```

The jar is written to `build/libs`.

## Configuration

Settings are stored in `config/seedscout.json`: saved seeds per server address, enabled structure toggles, the last search radius, the overlay toggles, the biome Y layer, and the `diskCache` switch. Cached map tiles live in `seedscout/tiles` inside the game folder, capped at about 512 MB; delete the folder any time to free space.

## Limitations

- Structure validation mirrors the game's biome and terrain checks for each structure type, but does not assemble jigsaw pieces, so a village or outpost can very rarely be shown where the game skipped it.
- On multiplayer servers the map assumes vanilla generation; custom world generation data packs are only picked up in singleplayer, where the world's own settings are available.
- The Biome Y layers sample the biome at a fixed height rather than the real terrain surface, so the default 64 layer can show cave biomes under high mountains.
- The search is centered on the map's current center, so click Center on player first if you want results relative to your position.
- The first time the map opens in a game session it takes a second or two while world generation data loads.
- Very dense structure types (mineshafts, buried treasure) are searched within a smaller effective radius, and their icons are hidden when zoomed far out.

## License

SeedScout is released under the [Apache License 2.0](LICENSE). You may use, modify, and redistribute it, including in commercial projects, as long as you keep the copyright and license notices and note any changes you made.
