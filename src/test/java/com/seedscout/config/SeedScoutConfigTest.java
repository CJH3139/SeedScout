package com.seedscout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedScoutConfigTest {
    @Test
    void roundTripsThroughDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seedscout.json");
        SeedScoutConfig config = SeedScoutConfig.defaults();
        config.putSeed("play.example.net", "123");
        config.lastRadius = 10000;
        config.showBeam = false;
        config.guiScale = 2;
        config.save(file);

        SeedScoutConfig loaded = SeedScoutConfig.load(file);
        assertEquals("123", loaded.seedFor("play.example.net").orElseThrow());
        assertEquals(10000, loaded.lastRadius);
        assertEquals(false, loaded.showBeam);
        assertEquals(2, loaded.guiScale);
        assertEquals(config.enabledStructures, loaded.enabledStructures);
    }

    @Test
    void repeatedSavesReplaceTheFileAndLeaveNoTempBehind(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seedscout.json");
        SeedScoutConfig config = SeedScoutConfig.defaults();
        config.save(file);
        config.putSeed("play.example.net", "7");
        config.save(file);

        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("seedscout.json.tmp")), "the temp file is moved, not left behind");
        assertEquals("7", SeedScoutConfig.load(file).seedFor("play.example.net").orElseThrow());
    }

    @Test
    void missingFileGivesDefaults(@TempDir Path dir) {
        SeedScoutConfig loaded = SeedScoutConfig.load(dir.resolve("nope.json"));
        assertEquals(5000, loaded.lastRadius);
        assertTrue(loaded.enabledStructures.contains("minecraft:village_plains"));
        assertTrue(loaded.seeds.isEmpty());
    }

    @Test
    void corruptFileGivesDefaultsAndLeavesFileAlone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seedscout.json");
        Files.writeString(file, "{ this is not json");
        SeedScoutConfig loaded = SeedScoutConfig.load(file);
        assertEquals(5000, loaded.lastRadius);
        assertEquals("{ this is not json", Files.readString(file));
    }

    @Test
    void olderConfigWithoutGuiScaleFollowsGameScale(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seedscout.json");
        Files.writeString(file, "{ \"lastRadius\": 3000 }");
        assertEquals(0, SeedScoutConfig.load(file).guiScale);
    }

    @Test
    void dungeonRadiusDefaultsAndClamps(@TempDir Path dir) throws Exception {
        assertEquals(150, SeedScoutConfig.defaults().lastDungeonRadius);
        Path file = dir.resolve("seedscout.json");
        Files.writeString(file, "{ \"lastRadius\": 3000 }");
        assertEquals(150, SeedScoutConfig.load(file).lastDungeonRadius);
        // 250, 500 and 1000 were the radii before measuring; old configs fall back to the default.
        for (int bad : new int[] {0, -5, 300, 5000, 250, 500, 1000}) {
            Files.writeString(file, "{ \"lastDungeonRadius\": " + bad + " }");
            assertEquals(150, SeedScoutConfig.load(file).lastDungeonRadius, "radius " + bad);
        }
        for (int ok : new int[] {100, 150, 200}) {
            Files.writeString(file, "{ \"lastDungeonRadius\": " + ok + " }");
            assertEquals(ok, SeedScoutConfig.load(file).lastDungeonRadius);
        }
    }

    @Test
    void dungeonsAreNotEnabledByDefault() {
        assertFalse(SeedScoutConfig.defaults().enabledStructures.stream().anyMatch(s -> s.startsWith("seedscout:")));
    }

    @Test
    void negativeGuiScaleIsReset(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seedscout.json");
        Files.writeString(file, "{ \"guiScale\": -3 }");
        assertEquals(0, SeedScoutConfig.load(file).guiScale);
    }
}
