package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.map.FeatureIndex;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Opt-in: {@code -Dseedscout.timing=true}. Times one screen of the dungeon overlay on seed 1 around 0,0 as the map runs
 * it: the map area of a 1920x1080 window at GUI scale 2 (770x520 GUI pixels), the map's worker count and a fresh world
 * per run, from the first query until every region is in. Run per scale and per costly region size.
 */
@EnabledIfSystemProperty(named = "seedscout.timing", matches = ".*")
class DungeonOverlayTimingTest {
    private static final int VIEW_WIDTH = 770;
    private static final int VIEW_HEIGHT = 520;
    private static final int WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
    }

    private record Job(double priority, long sequence, Runnable task) implements Runnable, Comparable<Job> {
        @Override
        public void run() {
            task.run();
        }

        @Override
        public int compareTo(Job other) {
            int byPriority = Double.compare(priority, other.priority);
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }

    private static void screen(double scale, int regionChunks) throws InterruptedException {
        SeedWorld world = SeedWorld.create(1L);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>());
        ConcurrentLinkedQueue<Runnable> main = new ConcurrentLinkedQueue<>();
        AtomicInteger probed = new AtomicInteger();
        FeatureIndex index = new FeatureIndex((id, cx, cz) -> {
            probed.incrementAndGet();
            return world.featuresInChunk(id, cx, cz);
        }, (priority, task) -> pool.execute(new Job(priority, SEQUENCE.incrementAndGet(), task)), main::add,
                DungeonFinder::isDungeon, regionChunks);
        int halfW = (int) Math.ceil(VIEW_WIDTH * scale / 2);
        int halfH = (int) Math.ceil(VIEW_HEIGHT * scale / 2);
        Set<Identifier> ids = Set.of(DungeonFinder.ANY);
        long start = System.nanoTime();
        List<FeatureFinder.FeatureHit> hits = index.query(-halfW, -halfH, halfW, halfH, ids);
        while (index.pendingCount() > 0) {
            Runnable r;
            while ((r = main.poll()) != null) r.run();
            Thread.sleep(20);
            hits = index.query(-halfW, -halfH, halfW, halfH, ids);
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        pool.shutdownNow();
        int viewChunks = (2 * halfW / 16 + 1) * (2 * halfH / 16 + 1);
        System.out.printf("overlay scale %.2f, %d-chunk regions: %.1f s wall, %d workers, %dx%d blocks, ~%d chunks in view,"
                        + " %d chunks probed, %d terrain chunks built, %d dungeons shown%n",
                scale, regionChunks, seconds, WORKERS, 2 * halfW, 2 * halfH, viewChunks, probed.get(),
                world.terrain().builtChunks(), hits.size());
        assertTrue(probed.get() > 0);
    }

    @Test
    void oneScreenPerScale() throws InterruptedException {
        for (double scale : new double[]{0.25, 0.5, 1.0}) {
            screen(scale, FeatureIndex.COSTLY_REGION_CHUNKS);
        }
    }

    @Test
    void oneScreenWithOldRegionSize() throws InterruptedException {
        screen(0.5, FeatureIndex.REGION_CHUNKS);
    }
}
