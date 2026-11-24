package com.seedscout.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Nearest-first chunk search on several threads. Chunks are handed out in square rings from the center, and the search
 * stops once rings 0..r are all scanned and the k-th nearest hit is no farther than {@code r * 16} blocks: every block of
 * an unscanned chunk is farther than that, so the result is exactly the k nearest hits within the scanned square.
 *
 * <p>The calling thread scans chunks too and only waits for helpers that already took a chunk, so it never waits on
 * queued work. Helpers run on a dedicated pool, never on the caller's pool.
 */
public final class RingSearch {
    interface ChunkHits {
        List<FeatureFinder.FeatureHit> inChunk(int chunkX, int chunkZ);
    }

    /** Called with chunks scanned so far and chunks in the whole square, from any search thread. */
    public interface Progress {
        void update(int scanned, int total);
    }

    static final int THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    private static final ThreadPoolExecutor HELPERS = new ThreadPoolExecutor(
            THREADS - 1, THREADS - 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "SeedScout-search");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            });

    static {
        HELPERS.allowCoreThreadTimeOut(true);
    }

    private final ChunkHits source;
    private final int centerX;
    private final int centerZ;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int maxResults;
    private final int total;
    private final BooleanSupplier cancelled;
    private final Progress progress;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();
    private final boolean[] done;
    private final List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
    private int next;
    private int prefix;
    private int scanned;
    private int active;
    private boolean stopped;
    private Throwable failure;

    private RingSearch(ChunkHits source, int centerX, int centerZ, int radiusChunks, int maxResults, int maxChunks,
                       BooleanSupplier cancelled, Progress progress) {
        this.source = source;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.centerChunkX = centerX >> 4;
        this.centerChunkZ = centerZ >> 4;
        this.maxResults = maxResults;
        long side = 2L * radiusChunks + 1;
        this.total = (int) Math.min(side * side, maxChunks);
        this.cancelled = cancelled;
        this.progress = progress;
        this.done = new boolean[total];
    }

    /**
     * The {@code maxResults} hits nearest to the center within {@code radiusChunks} rings, nearest first, using
     * {@code threads} threads in all (the caller included). A cancelled search returns what it has so far.
     */
    static List<FeatureFinder.FeatureHit> find(ChunkHits source, int centerX, int centerZ, int radiusChunks, int maxResults,
                                               int maxChunks, int threads, BooleanSupplier cancelled, Progress progress) {
        RingSearch search = new RingSearch(source, centerX, centerZ, radiusChunks, maxResults, maxChunks, cancelled, progress);
        return search.run(HELPERS, Math.max(1, Math.min(threads, THREADS)));
    }

    private List<FeatureFinder.FeatureHit> run(Executor helpers, int threads) {
        for (int i = 1; i < threads; i++) helpers.execute(this::work);
        work();
        lock.lock();
        try {
            stopped = true;
            while (active > 0) idle.awaitUninterruptibly();
            if (failure instanceof RuntimeException e) throw e;
            if (failure instanceof Error e) throw e;
            return nearest(hits, maxResults);
        } finally {
            lock.unlock();
        }
    }

    private void work() {
        int index = claim();
        while (index >= 0) {
            List<FeatureFinder.FeatureHit> found;
            try {
                found = source.inChunk(centerChunkX + dx(index), centerChunkZ + dz(index));
            } catch (Throwable t) {
                fail(t);
                return;
            }
            index = finish(index, found);
        }
    }

    private int claim() {
        lock.lock();
        try {
            return claimLocked();
        } finally {
            lock.unlock();
        }
    }

    private int claimLocked() {
        if (!stopped && cancelled.getAsBoolean()) stopped = true;
        if (stopped || next >= total) return -1;
        active++;
        return next++;
    }

    /** Records a scanned chunk and claims the next one, or returns -1 when this thread should stop. */
    private int finish(int index, List<FeatureFinder.FeatureHit> found) {
        int scannedNow;
        int claimed;
        lock.lock();
        try {
            active--;
            done[index] = true;
            hits.addAll(found);
            scanned++;
            while (prefix < total && done[prefix]) {
                prefix++;
                if (prefix == ringEnd(ringOf(prefix - 1)) && provenNearest(ringOf(prefix - 1))) stopped = true;
            }
            claimed = claimLocked();
            scannedNow = scanned;
            if (active == 0) idle.signalAll();
        } finally {
            lock.unlock();
        }
        try {
            progress.update(scannedNow, total);
        } catch (Throwable t) {
            // Fails the search like a chunk that threw, so the caller cannot wait forever on this thread's claim.
            fail(t, claimed >= 0);
            return -1;
        }
        return claimed;
    }

    private void fail(Throwable t) {
        fail(t, true);
    }

    /** {@code claimed}: this thread holds a claimed chunk, which is given up. */
    private void fail(Throwable t, boolean claimed) {
        lock.lock();
        try {
            if (claimed) active--;
            if (failure == null) failure = t;
            stopped = true;
            if (active == 0) idle.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** With rings 0..r scanned, any other chunk's blocks are more than {@code r * 16} blocks from the center. */
    private boolean provenNearest(int ring) {
        if (hits.size() < maxResults) return false;
        double limit = (double) ring * 16;
        int within = 0;
        for (FeatureFinder.FeatureHit hit : hits) {
            if (distanceSq(hit) <= limit * limit && ++within >= maxResults) return true;
        }
        return false;
    }

    private List<FeatureFinder.FeatureHit> nearest(List<FeatureFinder.FeatureHit> all, int limit) {
        // Ties break by position, so the result does not depend on which thread finished first.
        return all.stream().sorted(Comparator.comparingDouble(this::distanceSq)
                        .thenComparingInt(FeatureFinder.FeatureHit::blockX)
                        .thenComparingInt(FeatureFinder.FeatureHit::blockZ)
                        .thenComparingInt(FeatureFinder.FeatureHit::blockY))
                .limit(limit).toList();
    }

    private double distanceSq(FeatureFinder.FeatureHit hit) {
        double dx = hit.blockX() - centerX;
        double dz = hit.blockZ() - centerZ;
        return dx * dx + dz * dz;
    }

    /** Ring of the chunk at this index: ring 0 is the center, ring r holds indices (2r-1)^2 to (2r+1)^2 - 1. */
    static int ringOf(int index) {
        int r = (int) Math.ceil((Math.sqrt(index + 1.0) - 1) / 2);
        while (r > 0 && (2L * r - 1) * (2L * r - 1) > index) r--;
        while ((2L * r + 1) * (2L * r + 1) <= index) r++;
        return r;
    }

    /** One past the last index of ring r. */
    private int ringEnd(int r) {
        long side = 2L * r + 1;
        return (int) Math.min(side * side, total);
    }

    static int dx(int index) {
        return offset(index)[0];
    }

    static int dz(int index) {
        return offset(index)[1];
    }

    /** Walks ring r's perimeter: top edge left to right, right edge down, bottom edge right to left, left edge up. */
    static int[] offset(int index) {
        int r = ringOf(index);
        if (r == 0) return new int[]{0, 0};
        int i = index - (2 * r - 1) * (2 * r - 1);
        int side = 2 * r;
        if (i < side) return new int[]{-r + i, -r};
        i -= side;
        if (i < side) return new int[]{r, -r + i};
        i -= side;
        if (i < side) return new int[]{r - i, r};
        i -= side;
        return new int[]{-r, r - i};
    }
}
