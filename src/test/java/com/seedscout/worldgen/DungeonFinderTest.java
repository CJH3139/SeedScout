package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DungeonFinderTest {
    private static final List<Identifier> MOBS = List.of(
            DungeonMob.ZOMBIE.gridId(), DungeonMob.SKELETON.gridId(), DungeonMob.SPIDER.gridId());
    private static final Comparator<FeatureFinder.FeatureHit> ORDER = Comparator
            .comparing((FeatureFinder.FeatureHit h) -> h.featureId().toString())
            .thenComparingInt(FeatureFinder.FeatureHit::blockX)
            .thenComparingInt(FeatureFinder.FeatureHit::blockY)
            .thenComparingInt(FeatureFinder.FeatureHit::blockZ);

    private static SeedWorld world;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
    }

    private static List<FeatureFinder.FeatureHit> area(SeedWorld w, Identifier id, int minX, int minZ, int size) {
        List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
        for (int cx = minX; cx < minX + size; cx++) {
            for (int cz = minZ; cz < minZ + size; cz++) {
                hits.addAll(w.featuresInChunk(id, cx, cz));
            }
        }
        return hits;
    }

    private static List<FeatureFinder.FeatureHit> sorted(List<FeatureFinder.FeatureHit> hits) {
        return hits.stream().sorted(ORDER).toList();
    }

    @Test
    void idsAreExposed() {
        assertEquals(Identifier.fromNamespaceAndPath("seedscout", "dungeon"), DungeonFinder.ANY);
        assertEquals(List.of(DungeonFinder.ANY, DungeonMob.ZOMBIE.gridId(), DungeonMob.SKELETON.gridId(), DungeonMob.SPIDER.gridId()),
                DungeonFinder.IDS);
        assertTrue(world.featureIds().containsAll(DungeonFinder.IDS), "dungeon ids: " + world.featureIds());
        for (Identifier id : DungeonFinder.IDS) assertTrue(world.isFeature(id), id.toString());
    }

    @Test
    void anyIsUnionOfMobs() {
        List<FeatureFinder.FeatureHit> any = area(world, DungeonFinder.ANY, 0, 0, 8);
        List<FeatureFinder.FeatureHit> union = new ArrayList<>();
        for (Identifier mob : MOBS) {
            List<FeatureFinder.FeatureHit> hits = area(world, mob, 0, 0, 8);
            for (FeatureFinder.FeatureHit hit : hits) assertEquals(mob, hit.featureId());
            union.addAll(hits);
        }
        assertFalse(any.isEmpty(), "no dungeons in chunks 0..7 x 0..7");
        assertEquals(sorted(any), sorted(union), "ANY vs mob union in chunks 0..7 x 0..7");
    }

    @Test
    void oneTerrainPassForAllFilters() {
        List<FeatureFinder.FeatureHit> any = area(world, DungeonFinder.ANY, 12, 0, 4);
        int built = world.terrain().builtChunks();
        for (Identifier mob : MOBS) area(world, mob, 12, 0, 4);
        assertEquals(built, world.terrain().builtChunks(), "mob filters rebuilt terrain");
        assertFalse(any.isEmpty(), "no dungeons in chunks 12..15 x 0..3");
    }

    @Test
    void hitsAreSane() {
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                for (FeatureFinder.FeatureHit hit : world.featuresInChunk(DungeonFinder.ANY, cx, cz)) {
                    assertTrue(hit.blockY() >= -58 && hit.blockY() <= 319, "y out of range: " + hit);
                    assertTrue(MOBS.contains(hit.featureId()), "id: " + hit);
                    assertEquals(cx, hit.blockX() >> 4, "x outside chunk: " + hit);
                    assertEquals(cz, hit.blockZ() >> 4, "z outside chunk: " + hit);
                }
            }
        }
    }

    @Test
    void edgeRoomReadsNeighbor() {
        FeatureFinder.FeatureHit edge = null;
        for (FeatureFinder.FeatureHit hit : area(world, DungeonFinder.ANY, 0, 0, 8)) {
            int lx = hit.blockX() & 15;
            if (lx <= 2 || lx >= 13) {
                edge = hit;
                break;
            }
        }
        assertNotNull(edge, "no dungeon within 2 blocks of an x chunk edge in chunks 0..7");
        int cx = edge.blockX() >> 4;
        int cz = edge.blockZ() >> 4;
        int neighborX = (edge.blockX() & 15) <= 2 ? cx - 1 : cx + 1;
        List<FeatureFinder.FeatureHit> expected = world.featuresInChunk(DungeonFinder.ANY, cx, cz);

        SeedWorld alone = SeedWorld.create(1L);
        List<FeatureFinder.FeatureHit> aloneHits = alone.featuresInChunk(DungeonFinder.ANY, cx, cz);
        int built = alone.terrain().builtChunks();
        alone.terrain().get((neighborX << 4) + 8, 0, (cz << 4) + 8);
        assertEquals(built, alone.terrain().builtChunks(), "neighbor chunk " + neighborX + " " + cz + " was not built");
        assertEquals(expected, aloneHits);

        SeedWorld neighborFirst = SeedWorld.create(1L);
        neighborFirst.featuresInChunk(DungeonFinder.ANY, neighborX, cz);
        assertEquals(expected, neighborFirst.featuresInChunk(DungeonFinder.ANY, cx, cz));
    }

    @Test
    void parallelQueriesAgree() throws Exception {
        // A fresh world, so the threads race on cold terrain and dungeon caches.
        SeedWorld fresh = SeedWorld.create(1L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<FeatureFinder.FeatureHit>>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> area(fresh, DungeonFinder.ANY, 4, 4, 4)));
            }
            List<FeatureFinder.FeatureHit> first = futures.get(0).get();
            for (Future<List<FeatureFinder.FeatureHit>> future : futures) {
                assertEquals(first, future.get(), "thread results differ in chunks 4..7 x 4..7");
            }
            assertFalse(first.isEmpty(), "no dungeons in chunks 4..7 x 4..7");
            assertEquals(sorted(area(world, DungeonFinder.ANY, 4, 4, 4)), sorted(first));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lastSpawnerAtAPositionWins() {
        Map<Long, BlockState> states = new HashMap<>();
        states.put(BlockPos.asLong(1, 2, 3), Blocks.SPAWNER.defaultBlockState());
        DungeonRoom.Blocks blocks = new DungeonRoom.Blocks() {
            @Override
            public BlockState get(int x, int y, int z) {
                return states.getOrDefault(BlockPos.asLong(x, y, z), Blocks.AIR.defaultBlockState());
            }

            @Override
            public void set(int x, int y, int z, BlockState state) {
                states.put(BlockPos.asLong(x, y, z), state);
            }

            @Override
            public int minY() {
                return -64;
            }
        };
        FeatureFinder.FeatureHit first = new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), 1, 2, 3);
        FeatureFinder.FeatureHit erased = new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), 5, 2, 3);
        FeatureFinder.FeatureHit last = new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), 1, 2, 3);
        assertEquals(List.of(last), DungeonFinder.finalHits(List.of(first, erased, last), blocks));
    }

    /**
     * Spawners from a real 26.3 world of seed 1 (DungeonAudit, radius 32 around 0,0), all matched in every audit run
     * and with the room clear of chunk edges, where real results depend on chunk scheduling.
     */
    private static final List<FeatureFinder.FeatureHit> recordedDungeonsSeed1 = List.of(
            new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), -198, -31, -389),
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), -200, -25, 262),
            new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), -230, -4, 390),
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), -26, -25, 362),
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), -501, 0, 104),
            new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), 105, -32, 88),
            new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), 182, 24, 170),
            new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), 212, -21, 59),
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), 315, -7, 11),
            new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), 409, -44, -265));

    /** Real dungeons of the same audit that SeedScout missed until it replayed mineshaft pieces. */
    private static final List<FeatureFinder.FeatureHit> recordedMineshaftDungeonsSeed1 = List.of(
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), -503, -25, 143),
            new FeatureFinder.FeatureHit(DungeonMob.SKELETON.gridId(), -483, -19, 431),
            new FeatureFinder.FeatureHit(DungeonMob.ZOMBIE.gridId(), -479, 8, -347),
            new FeatureFinder.FeatureHit(DungeonMob.SPIDER.gridId(), 291, -9, -458));

    @Test
    void findsRecordedDungeons() {
        assertFinds(recordedDungeonsSeed1);
    }

    @Test
    void findsRecordedMineshaftDungeons() {
        assertFinds(recordedMineshaftDungeonsSeed1);
    }

    @Test
    void resultsDoNotDependOnQueryOrder() {
        // Chunks around the recorded mineshaft dungeon at -503 -25 143 (chunk -32,8), so mineshaft pieces are replayed
        // into several chunks and as neighbors of each other.
        List<long[]> chunks = new ArrayList<>();
        for (int cx = -34; cx <= -30; cx++) {
            for (int cz = 6; cz <= 10; cz++) chunks.add(new long[]{cx, cz});
        }
        Map<Long, List<FeatureFinder.FeatureHit>> forward = new HashMap<>();
        SeedWorld first = SeedWorld.create(1L);
        for (long[] c : chunks) forward.put(BlockPos.asLong((int) c[0], 0, (int) c[1]), first.featuresInChunk(DungeonFinder.ANY, (int) c[0], (int) c[1]));
        Map<Long, List<FeatureFinder.FeatureHit>> backward = new HashMap<>();
        SeedWorld second = SeedWorld.create(1L);
        for (int i = chunks.size() - 1; i >= 0; i--) {
            long[] c = chunks.get(i);
            backward.put(BlockPos.asLong((int) c[0], 0, (int) c[1]), second.featuresInChunk(DungeonFinder.ANY, (int) c[0], (int) c[1]));
        }
        assertTrue(forward.values().stream().anyMatch(hits -> hits.contains(recordedMineshaftDungeonsSeed1.get(0))),
                "area lost its mineshaft dungeon");
        assertEquals(forward, backward, "dungeons depend on the order chunks were queried in");
    }

    private static void assertFinds(List<FeatureFinder.FeatureHit> recorded) {
        for (FeatureFinder.FeatureHit expected : recorded) {
            List<FeatureFinder.FeatureHit> hits = world.featuresInChunk(DungeonFinder.ANY, expected.blockX() >> 4, expected.blockZ() >> 4);
            assertTrue(hits.contains(expected), "missing " + expected + ", chunk has " + hits);
        }
    }

    @Test
    void noDungeonsOutsideOverworld() {
        Set<Identifier> ids = Set.copyOf(SeedWorld.create(1L, Dimension.NETHER).featureIds());
        for (Identifier id : DungeonFinder.IDS) assertFalse(ids.contains(id), id.toString());
    }
}
