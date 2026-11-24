package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DungeonStoreTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
    }

    private static DungeonStore store(Path dir) {
        return new DungeonStore(dir, "26.x test", "overworld", 1L, SeedWorld.VANILLA_PROFILE);
    }

    @Test
    void roundTripsChunksIncludingEmptyOnes(@TempDir Path dir) {
        DungeonStore first = store(dir);
        List<FeatureFinder.FeatureHit> hits = List.of(
                new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), -503, -25, 143),
                new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), -497, 8, 130));
        first.put(-32, 8, hits);
        first.put(-31, 8, List.of());
        first.put(40, -70, List.of(new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), 655, -58, -1105)));
        first.flush();

        DungeonStore second = store(dir);
        assertEquals(hits, second.get(-32, 8).orElseThrow());
        assertEquals(List.of(), second.get(-31, 8).orElseThrow());
        assertEquals(List.of(new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), 655, -58, -1105)), second.get(40, -70).orElseThrow());
        assertTrue(second.get(-30, 8).isEmpty(), "unknown chunk");
    }

    @Test
    void corruptFileIsDropped(@TempDir Path dir) throws IOException {
        DungeonStore first = store(dir);
        first.put(3, 3, List.of());
        first.flush();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        assertEquals(1, files.size());
        Files.write(files.get(0), new byte[]{1, 2, 3});
        assertTrue(store(dir).get(3, 3).isEmpty());
        assertFalse(Files.exists(files.get(0)));
    }

    @Test
    void storedChunksMatchFreshOnesAndSkipTerrain(@TempDir Path dir) {
        SeedWorld fresh = SeedWorld.create(1L);
        fresh.useDungeonStore(store(dir));
        List<FeatureFinder.FeatureHit> expected = new ArrayList<>();
        for (int cx = 4; cx < 8; cx++) {
            for (int cz = 4; cz < 8; cz++) expected.addAll(fresh.featuresInChunk(DungeonFinder.ANY, cx, cz));
        }
        fresh.flushDungeons();
        assertFalse(expected.isEmpty(), "no dungeons in chunks 4..7 x 4..7");

        SeedWorld reopened = SeedWorld.create(1L);
        reopened.useDungeonStore(store(dir));
        List<FeatureFinder.FeatureHit> loaded = new ArrayList<>();
        for (int cx = 4; cx < 8; cx++) {
            for (int cz = 4; cz < 8; cz++) loaded.addAll(reopened.featuresInChunk(DungeonFinder.ANY, cx, cz));
        }
        assertEquals(expected, loaded);
        assertEquals(0, reopened.terrain().builtChunks(), "stored chunks built terrain again");
    }
}
