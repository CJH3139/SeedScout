package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.worldgen.RegionHit;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.TestRegistryBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureIndexTest {
    private static final Identifier VILLAGES = Identifier.ofVanilla("villages");
    private static final Identifier VILLAGE_PLAINS = Identifier.ofVanilla("village_plains");
    private static final Identifier VILLAGE_DESERT = Identifier.ofVanilla("village_desert");
    private static final Identifier STRONGHOLD = Identifier.ofVanilla("stronghold");

    private static SeedWorld world;
    private static RegistryEntry<Structure> plains;
    private static RegistryEntry<Structure> desert;
    private static RegistryEntry<Structure> stronghold;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
        plains = world.structure(VILLAGE_PLAINS);
        desert = world.structure(VILLAGE_DESERT);
        stronghold = world.structure(STRONGHOLD);
    }

    private static final class ManualExecutor implements Executor {
        final List<Runnable> queue = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void drain() {
            List<Runnable> copy = new ArrayList<>(queue);
            queue.clear();
            copy.forEach(Runnable::run);
        }
    }

    private static RegionHit hit(int rx, int rz) {
        return new RegionHit(plains, new ChunkPos(rx * 34, rz * 34), rx * 34 * 16, rz * 34 * 16);
    }

    @Test
    void queriesEveryRegionOverlappingTheViewportOnce() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        List<int[]> probed = new ArrayList<>();
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(VILLAGES, 34, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> { probed.add(new int[]{rx, rz}); return Optional.of(hit(rx, rz)); },
                setId -> List.of(),
                worker, main);

        List<RegionHit> first = index.query(0, 0, 600, 600, Set.of(VILLAGE_PLAINS));
        assertTrue(first.isEmpty());
        assertEquals(4, worker.queue.size());
        worker.drain();
        main.drain();

        List<RegionHit> second = index.query(0, 0, 600, 600, Set.of(VILLAGE_PLAINS));
        assertEquals(4, second.size());
        assertEquals(4, probed.size(), "each region probed once");
    }

    @Test
    void skipsSetsWhoseStructuresAreDisabled() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(VILLAGES, 34, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> Optional.of(hit(rx, rz)),
                setId -> List.of(),
                worker, main);
        index.query(0, 0, 600, 600, Set.of(Identifier.ofVanilla("monument")));
        assertTrue(worker.queue.isEmpty());
    }

    @Test
    void concentricSetsAreComputedOnceAndFilteredToViewport() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        Identifier strongholds = Identifier.ofVanilla("strongholds");
        int[] calls = {0};
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(strongholds, 0, true, Set.of(STRONGHOLD))),
                (setId, rx, rz) -> Optional.empty(),
                setId -> { calls[0]++; return List.of(
                        new RegionHit(stronghold, new ChunkPos(10, 10), 160, 160),
                        new RegionHit(stronghold, new ChunkPos(500, 500), 8000, 8000)); },
                worker, main);
        index.query(0, 0, 1000, 1000, Set.of(STRONGHOLD));
        worker.drain();
        main.drain();
        List<RegionHit> hits = index.query(0, 0, 1000, 1000, Set.of(STRONGHOLD));
        assertEquals(1, hits.size());
        assertEquals(160, hits.get(0).blockX());
        index.query(0, 0, 1000, 1000, Set.of(STRONGHOLD));
        assertEquals(1, calls[0]);
    }

    @Test
    void clearDiscardsPendingAndCachedResultsForStaleGeneration() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        int[] probeCalls = {0};
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(VILLAGES, 34, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> { probeCalls[0]++; return Optional.of(hit(rx, rz)); },
                setId -> List.of(),
                worker, main);

        index.query(0, 0, 600, 600, Set.of(VILLAGE_PLAINS));
        assertEquals(4, index.pendingCount());

        index.clear();
        assertEquals(0, index.pendingCount());

        worker.drain();
        main.drain();
        assertEquals(4, probeCalls[0], "stale probes still ran once when drained");
        assertEquals(0, index.pendingCount(), "a stale completion must not leave a dangling pending marker");

        List<RegionHit> stale = index.query(0, 0, 600, 600, Set.of(VILLAGE_PLAINS));
        assertTrue(stale.isEmpty(), "the stale result was discarded, so nothing is cached yet");
        assertEquals(4, index.pendingCount(), "a fresh probe is dispatched per region");
        assertEquals(4, probeCalls[0], "the fresh probes are queued but not yet run");

        worker.drain();
        main.drain();
        List<RegionHit> fresh = index.query(0, 0, 600, 600, Set.of(VILLAGE_PLAINS));
        assertEquals(4, fresh.size());
        assertEquals(8, probeCalls[0], "4 stale + 4 fresh probes ran in total");
    }

    @Test
    void filtersHitsByEnabledStructureId() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        List<int[]> probed = new ArrayList<>();
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(VILLAGES, 34, false, Set.of(VILLAGE_PLAINS, VILLAGE_DESERT))),
                (setId, rx, rz) -> {
                    probed.add(new int[]{rx, rz});
                    RegistryEntry<Structure> structure = rx == 0 ? plains : desert;
                    return Optional.of(new RegionHit(structure, new ChunkPos(rx * 34, rz * 34), rx * 34 * 16, rz * 34 * 16));
                },
                setId -> List.of(),
                worker, main);

        index.query(0, 0, 600, 0, Set.of(VILLAGE_PLAINS));
        worker.drain();
        main.drain();
        List<RegionHit> hits = index.query(0, 0, 600, 0, Set.of(VILLAGE_PLAINS));

        assertEquals(2, probed.size(), "both regions were still probed once");
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).blockX(), "only the village_plains hit is enabled");
    }

    @Test
    void dispatchesNothingWhenAQueryWouldExceedTheRegionBudget() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        List<int[]> probed = new ArrayList<>();

        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(Identifier.ofVanilla("mineshafts"), 1, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> { probed.add(new int[]{rx, rz}); return Optional.of(hit(rx, rz)); },
                setId -> List.of(),
                worker, main);

        List<RegionHit> hits = index.query(0, 0, 1_000_000, 1_000_000, Set.of(VILLAGE_PLAINS));

        assertTrue(hits.isEmpty(), "an over-budget query returns nothing");
        assertTrue(worker.queue.isEmpty(), "an over-budget query queues no probe jobs");
        assertEquals(0, probed.size(), "no probe was dispatched");
        assertEquals(0, index.pendingCount(), "nothing was marked pending");
    }

    @Test
    void queriesInsideTheRegionBudgetStillDispatch() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(Identifier.ofVanilla("mineshafts"), 1, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> Optional.of(hit(rx, rz)),
                setId -> List.of(),
                worker, main);

        index.query(0, 0, 1023, 1023, Set.of(VILLAGE_PLAINS));
        assertEquals(StructureIndex.MAX_REGIONS_PER_QUERY, worker.queue.size());
    }

    @Test
    void probesNegativeRegionsCorrectly() {
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor main = new ManualExecutor();
        List<int[]> probed = new ArrayList<>();
        StructureIndex index = new StructureIndex(
                List.of(new StructureIndex.SetInfo(VILLAGES, 34, false, Set.of(VILLAGE_PLAINS))),
                (setId, rx, rz) -> { probed.add(new int[]{rx, rz}); return Optional.of(hit(rx, rz)); },
                setId -> List.of(),
                worker, main);

        index.query(-600, -600, -1, -1, Set.of(VILLAGE_PLAINS));
        assertEquals(4, worker.queue.size());
        worker.drain();
        main.drain();

        assertEquals(4, probed.size());
        for (int[] rxrz : probed) {
            assertTrue(rxrz[0] == -2 || rxrz[0] == -1, "regionX should be -2 or -1, was " + rxrz[0]);
            assertTrue(rxrz[1] == -2 || rxrz[1] == -1, "regionZ should be -2 or -1, was " + rxrz[1]);
        }
    }
}
