package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in: {@code -Dseedscout.timing=true}. Times a 1000 block spider search on seed 1 around 0,0 as the search button
 * runs it (20 results, all search threads), cold and then again from the disk cache.
 */
@EnabledIfSystemProperty(named = "seedscout.timing", matches = ".*")
class DungeonSearchTimingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
    }

    private static List<FeatureFinder.FeatureHit> timed(String label, SeedWorld world) {
        return timed(label, world, 1000);
    }

    private static List<FeatureFinder.FeatureHit> timed(String label, SeedWorld world, int radius) {
        AtomicInteger scanned = new AtomicInteger();
        long start = System.nanoTime();
        List<FeatureFinder.FeatureHit> hits = world.findFeatures(DungeonMob.SPIDER.gridId(), 0, 0, radius, 20, () -> false,
                (s, total) -> scanned.accumulateAndGet(s, Math::max));
        double seconds = (System.nanoTime() - start) / 1e9;
        int built = world.terrain().builtChunks();
        System.out.printf("%s: %.1f s, %d threads, %d hits, farthest %.0f blocks, %d chunks scanned, %d terrain chunks built%n",
                label, seconds, RingSearch.THREADS, hits.size(),
                hits.isEmpty() ? 0 : Math.hypot(hits.getLast().blockX(), hits.getLast().blockZ()), scanned.get(), built);
        return hits;
    }

    @Test
    void spiderSearch1000(@TempDir Path dir) {
        SeedWorld cold = SeedWorld.create(1L);
        cold.useDungeonStore(new DungeonStore(dir, "timing", "overworld", 1L, SeedWorld.VANILLA_PROFILE));
        List<FeatureFinder.FeatureHit> first = timed("spider search 1000 cold", cold);
        cold.flushDungeons();
        assertFalse(first.isEmpty());

        SeedWorld warm = SeedWorld.create(1L);
        warm.useDungeonStore(new DungeonStore(dir, "timing", "overworld", 1L, SeedWorld.VANILLA_PROFILE));
        assertEquals(first, timed("spider search 1000 from disk cache", warm));
    }

    @Test
    void spiderSearchSmallerRadii() {
        for (int radius : new int[]{100, 150, 200, 250}) {
            timed("spider search " + radius + " cold", SeedWorld.create(1L), radius);
        }
    }
}
