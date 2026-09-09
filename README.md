# SeedScout

Chunkbase inside Minecraft. A client-side Fabric mod for 26.2 that shows the seed map of your world, finds structures and biomes, and guides you there with a HUD arrow and a beam.

<img width="3840" height="2091" alt="image" src="https://github.com/user-attachments/assets/57fe846b-215c-485f-b10b-01a9db424394" />

## Features

- **Exact map.** Biomes and structures come from Minecraft's own world generation, so they match the game. Overworld, Nether, and End. In singleplayer, data packs like Terralith are picked up automatically.
- **Fast.** Six zoom levels, background rendering, blurry-first loading, and a disk cache so seeds you have seen open instantly.
- **Find things.** Search any structure, biome, or amethyst geode within 2k to 50k blocks. Results list nearest first and are highlighted on the map. Biome Y layers reveal lush caves, dripstone caves, and deep dark.
- **Get there.** Click a result or icon to set a waypoint. A HUD arrow and a beam through terrain lead you to it.
- **Extras.** Hover for biome name and coordinates, go to typed coordinates, shift-drag to measure, slime chunks, chunk grid, spawn marker, share locations in chat with click-to-follow for other SeedScout users, teleport (needs permission), and PNG export.
- **Servers.** Enter the seed once per server and it is remembered.

## Usage

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2 with [Fabric API](https://modrinth.com/mod/fabric-api), then drop the SeedScout jar in `mods`.
2. Press `Y` in a world to open the map. Singleplayer reads the seed itself; on a server, type it in the panel and click Apply.
3. Left-click icons in the structure grid to show or hide a type, right-click one to make it the search target. Pick a radius and click Search.
4. Click a result or a map icon to set a waypoint and close the map. Right-click anything for more: waypoint, copy, share in chat, teleport.
5. Follow the arrow. Press `Y` or Escape to close the map, Clear to drop the waypoint.

## Building

Requires JDK 25.

```
./gradlew build
```

The jar lands in `build/libs`. The 1.21.11 version lives on the `1.21.11` branch.

## Notes

- Settings live in `config/seedscout.json`. Cached tiles live in `seedscout/tiles` in the game folder, capped at about 512 MB; delete the folder any time.
- On multiplayer servers the map assumes vanilla generation.
- Structure checks mirror the game's biome and terrain rules but do not assemble jigsaw pieces, so a village can very rarely be shown where the game skipped it. Geodes that would touch water or lava are also skipped by the game but still shown here.
- The search is centered on the map's center, so click Center first if you want results near you.

## License

[Apache License 2.0](LICENSE).
