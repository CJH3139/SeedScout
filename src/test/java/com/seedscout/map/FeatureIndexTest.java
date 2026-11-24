package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.worldgen.FeatureFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FeatureIndexTest {
    private static final Identifier DUNGEON = Identifier.fromNamespaceAndPath("seedscout", "dungeon");
    private static final Identifier GEODE = Identifier.withDefaultNamespace("amethyst_geode");
    private static final int COSTLY_BLOCKS = FeatureIndex.COSTLY_REGION_CHUNKS * 16;
    private static final int COSTLY_CHUNKS = FeatureIndex.COSTLY_REGION_CHUNKS * FeatureIndex.COSTLY_REGION_CHUNKS;

    private record Job(double priority, Runnable task) {}

    private static final class ManualScheduler implements FeatureIndex.Scheduler {
        final List<Job> queue = new ArrayList<>();

        @Override
        public void submit(double priority, Runnable task) {
            queue.add(new Job(priority, task));
        }

        void drain() {
            List<Job> copy = new ArrayList<>(queue);
            queue.clear();
            copy.forEach(job -> job.task().run());
        }
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

    /** One hit in the middle of every chunk, so a region's hit count is its chunk count. */
    private static List<FeatureFinder.FeatureHit> oneHit(Identifier id, int chunkX, int chunkZ) {
        return List.of(new FeatureFinder.FeatureHit(id, (chunkX << 4) + 8, 0, (chunkZ << 4) + 8));
    }

    private final ManualScheduler worker = new ManualScheduler();
    private final ManualExecutor main = new ManualExecutor();
    private final AtomicInteger probes = new AtomicInteger();

    private FeatureIndex index(FeatureIndex.ChunkProbe probe) {
        return new FeatureIndex(probe, worker, main, DUNGEON::equals, FeatureIndex.COSTLY_REGION_CHUNKS);
    }

    private FeatureIndex counting() {
        return index((id, cx, cz) -> {
            probes.incrementAndGet();
            return oneHit(id, cx, cz);
        });
    }

    private static List<FeatureFinder.FeatureHit> queryOneRegion(FeatureIndex index, Identifier id) {
        return index.query(0, 0, COSTLY_BLOCKS - 1, COSTLY_BLOCKS - 1, Set.of(id));
    }

    @Test
    void costlyRegionIsSmallAndCompletesWhileWanted() {
        FeatureIndex index = counting();
        assertTrue(queryOneRegion(index, DUNGEON).isEmpty());
        assertEquals(1, worker.queue.size());
        assertEquals(1, index.pendingCount());
        worker.drain();
        main.drain();
        assertEquals(COSTLY_CHUNKS, probes.get());
        assertEquals(0, index.pendingCount());
        assertEquals(COSTLY_CHUNKS, queryOneRegion(index, DUNGEON).size());
        assertTrue(worker.queue.isEmpty());
    }

    @Test
    void costlyRegionQueuesAfterVisibleTilesNearestFirst() {
        FeatureIndex index = counting();
        index.query(0, 0, 3 * COSTLY_BLOCKS - 1, COSTLY_BLOCKS - 1, Set.of(DUNGEON, GEODE));
        List<Double> costly = worker.queue.stream().map(Job::priority).filter(p -> p > 0).toList();
        assertEquals(3, costly.size());
        for (double priority : costly) assertTrue(priority >= FeatureIndex.COSTLY_PRIORITY);
        // Regions 0, 1, 2 along x; the view center is in region 1.
        assertTrue(costly.get(1) < costly.get(0));
        assertTrue(costly.get(1) < costly.get(2));
        // The geode region keeps its old priority.
        assertEquals(1, worker.queue.stream().filter(job -> job.priority() == 0.0).count());
    }

    @Test
    void costlyRegionNoLongerQueriedDoesNotRun() {
        FeatureIndex index = counting();
        queryOneRegion(index, DUNGEON);
        index.query(0, 0, COSTLY_BLOCKS - 1, COSTLY_BLOCKS - 1, Set.of());
        worker.drain();
        main.drain();
        assertEquals(0, probes.get());
        assertEquals(0, index.pendingCount());
        // Not cached as empty: asking again submits it again.
        assertTrue(queryOneRegion(index, DUNGEON).isEmpty());
        assertEquals(1, worker.queue.size());
    }

    @Test
    void costlyRegionOutOfViewIsNotWanted() {
        FeatureIndex index = counting();
        queryOneRegion(index, DUNGEON);
        // Panned far away: the old region is not part of this query.
        index.query(10 * COSTLY_BLOCKS, 0, 11 * COSTLY_BLOCKS - 1, COSTLY_BLOCKS - 1, Set.of(DUNGEON));
        worker.queue.get(0).task().run();
        assertEquals(0, probes.get());
    }

    @Test
    void abandonedRegionIsNotCachedWithPartialHits() {
        FeatureIndex[] holder = new FeatureIndex[1];
        holder[0] = index((id, cx, cz) -> {
            if (probes.incrementAndGet() == 3) holder[0].dropWanted();
            return oneHit(id, cx, cz);
        });
        FeatureIndex index = holder[0];
        queryOneRegion(index, DUNGEON);
        worker.drain();
        main.drain();
        assertEquals(3, probes.get());
        assertEquals(0, index.pendingCount());

        assertTrue(queryOneRegion(index, DUNGEON).isEmpty());
        assertEquals(1, worker.queue.size());
        worker.drain();
        main.drain();
        assertEquals(3 + COSTLY_CHUNKS, probes.get());
        assertEquals(COSTLY_CHUNKS, queryOneRegion(index, DUNGEON).size());
    }

    @Test
    void clearStopsCostlyRegions() {
        FeatureIndex index = counting();
        queryOneRegion(index, DUNGEON);
        index.clear();
        worker.drain();
        main.drain();
        assertEquals(0, probes.get());
        assertEquals(0, index.pendingCount());
    }

    @Test
    void cheapRegionsAlwaysFinish() {
        FeatureIndex index = counting();
        index.query(0, 0, FeatureIndex.REGION_BLOCKS - 1, FeatureIndex.REGION_BLOCKS - 1, Set.of(GEODE));
        index.dropWanted();
        index.query(0, 0, 0, 0, Set.of());
        worker.drain();
        main.drain();
        assertEquals(FeatureIndex.REGION_CHUNKS * FeatureIndex.REGION_CHUNKS, probes.get());
        assertEquals(FeatureIndex.REGION_CHUNKS * FeatureIndex.REGION_CHUNKS,
                index.query(0, 0, FeatureIndex.REGION_BLOCKS - 1, FeatureIndex.REGION_BLOCKS - 1, Set.of(GEODE)).size());
    }

    @Test
    void failedCostlyRegionIsAskedForAgain() {
        FeatureIndex index = index((id, cx, cz) -> {
            throw new IllegalStateException("terrain broke");
        });
        queryOneRegion(index, DUNGEON);
        worker.drain();
        main.drain();
        assertEquals(0, index.pendingCount());
        queryOneRegion(index, DUNGEON);
        assertEquals(1, worker.queue.size());
    }
}
