package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class RingSearchTest {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("seedscout", "test");

    @Test
    void ringsCoverTheSquareOnce() {
        Set<Long> seen = new HashSet<>();
        int radius = 7;
        int side = 2 * radius + 1;
        for (int i = 0; i < side * side; i++) {
            int[] o = RingSearch.offset(i);
            assertEquals(RingSearch.ringOf(i), Math.max(Math.abs(o[0]), Math.abs(o[1])), "ring of index " + i);
            assertTrue(seen.add(((long) o[0] << 32) ^ (o[1] & 0xFFFFFFFFL)), "offset twice at index " + i);
        }
        assertEquals(side * side, seen.size());
        assertEquals(0, RingSearch.ringOf(0));
        assertEquals(1, RingSearch.ringOf(1));
        assertEquals(1, RingSearch.ringOf(8));
        assertEquals(2, RingSearch.ringOf(9));
    }

    /** Sparse random hits in every chunk; seeded per chunk so every query of a chunk agrees. */
    private static List<FeatureFinder.FeatureHit> sparse(int chunkX, int chunkZ, AtomicInteger calls) {
        calls.incrementAndGet();
        Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L);
        List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
        if (random.nextInt(40) == 0) {
            hits.add(new FeatureFinder.FeatureHit(ID, (chunkX << 4) + random.nextInt(16), 0, (chunkZ << 4) + random.nextInt(16)));
        }
        return hits;
    }

    @Test
    void matchesBruteForceNearest() {
        for (int threads : new int[]{1, 2, 7}) {
            for (int[] center : new int[][]{{0, 0}, {15, 15}, {-1, 7}, {1234, -5678}}) {
                AtomicInteger calls = new AtomicInteger();
                int radius = 40;
                int k = 12;
                List<FeatureFinder.FeatureHit> found = RingSearch.find((x, z) -> sparse(x, z, calls), center[0], center[1], radius, k,
                        Integer.MAX_VALUE, threads, () -> false, (s, t) -> {});
                List<FeatureFinder.FeatureHit> all = new ArrayList<>();
                AtomicInteger unused = new AtomicInteger();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        all.addAll(sparse((center[0] >> 4) + dx, (center[1] >> 4) + dz, unused));
                    }
                }
                Comparator<FeatureFinder.FeatureHit> byDistance = Comparator.comparingDouble((FeatureFinder.FeatureHit h) -> {
                    double x = h.blockX() - center[0];
                    double z = h.blockZ() - center[1];
                    return x * x + z * z;
                }).thenComparingInt(FeatureFinder.FeatureHit::blockX).thenComparingInt(FeatureFinder.FeatureHit::blockZ)
                        .thenComparingInt(FeatureFinder.FeatureHit::blockY);
                List<FeatureFinder.FeatureHit> expected = all.stream().sorted(byDistance).limit(k).toList();
                assertEquals(expected, found, "threads " + threads + " center " + center[0] + " " + center[1]);
                assertTrue(calls.get() < (2 * radius + 1) * (2 * radius + 1), "never stopped early: " + calls.get());
            }
        }
    }

    @Test
    void scansWholeSquareWhenTooFewHits() {
        AtomicInteger calls = new AtomicInteger();
        List<FeatureFinder.FeatureHit> found = RingSearch.find((x, z) -> sparse(x, z, calls), 0, 0, 5, 1000,
                Integer.MAX_VALUE, 4, () -> false, (s, t) -> {});
        assertEquals(11 * 11, calls.get());
        assertTrue(found.size() < 1000);
    }

    @Test
    void cancelStopsScheduling() {
        AtomicInteger calls = new AtomicInteger();
        RingSearch.find((x, z) -> {
            calls.incrementAndGet();
            return List.of();
        }, 0, 0, 100, 5, Integer.MAX_VALUE, 4, () -> calls.get() >= 50, (s, t) -> {});
        assertTrue(calls.get() < 60, "kept scanning after cancel: " + calls.get());
    }

    @Test
    void failurePropagates() {
        assertThrows(IllegalStateException.class, () -> RingSearch.find((x, z) -> {
            if (x == 2 && z == 2) throw new IllegalStateException("boom");
            return List.of();
        }, 0, 0, 5, 5, Integer.MAX_VALUE, 4, () -> false, (s, t) -> {}));
    }

    @Test
    void progressFailureOnAHelperPropagatesInsteadOfHanging() {
        // Only helpers throw, so the caller must learn of it from the helper's claim being given up.
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThrows(IllegalStateException.class, () ->
                RingSearch.find((x, z) -> {
                    sleep(2);
                    return List.of();
                }, 0, 0, 5, 5, Integer.MAX_VALUE, 4, () -> false, (scanned, total) -> {
                    if (Thread.currentThread().getName().equals("SeedScout-search")) {
                        throw new IllegalStateException("progress readout broke");
                    }
                })));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void progressFailureOnTheLastChunkPropagates() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThrows(IllegalStateException.class, () ->
                RingSearch.find((x, z) -> List.of(), 0, 0, 1, 5, Integer.MAX_VALUE, 1, () -> false, (scanned, total) -> {
                    if (scanned == total) throw new IllegalStateException("progress readout broke");
                })));
    }
}
