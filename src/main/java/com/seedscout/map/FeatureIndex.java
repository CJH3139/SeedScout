package com.seedscout.map;

import com.seedscout.SeedScoutClient;
import com.seedscout.worldgen.FeatureFinder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;

public final class FeatureIndex {
    public static final int REGION_CHUNKS = 16;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
    public static final int MAX_REGIONS_PER_QUERY = 256;
    /**
     * Costly features (dungeons build terrain per chunk) use smaller regions, so a screen needs fewer chunks outside the
     * view and a region that leaves the view stops soon.
     */
    public static final int COSTLY_REGION_CHUNKS = 4;
    /** Costly regions queue after the visible map tiles (0 plus squared distance) and before the tile prefetch ring (1e15). */
    public static final double COSTLY_PRIORITY = 1.0e14;

    @FunctionalInterface
    public interface ChunkProbe {
        List<FeatureFinder.FeatureHit> probe(Identifier featureId, int chunkX, int chunkZ);
    }

    /** Runs a job off the main thread; lower priority values run first. */
    @FunctionalInterface
    public interface Scheduler {
        void submit(double priority, Runnable task);
    }

    private record RegionKey(Identifier featureId, int regionX, int regionZ) {}

    private final ChunkProbe probe;
    private final Scheduler worker;
    private final Executor mainThread;
    private final Predicate<Identifier> costly;
    private final int costlyRegionChunks;
    private final Map<RegionKey, List<FeatureFinder.FeatureHit>> regions = new HashMap<>();
    private final Set<RegionKey> pending = new HashSet<>();
    /** Costly regions the last query needed; a costly job stops before its next chunk once its region is not here. */
    private volatile Set<RegionKey> wanted = Set.of();
    private volatile int generation = 0;

    public FeatureIndex(ChunkProbe probe, Executor worker, Executor mainThread) {
        this(probe, (priority, task) -> worker.execute(task), mainThread, id -> false, COSTLY_REGION_CHUNKS);
    }

    /**
     * Regions of features matching {@code costly} are {@code costlyRegionChunks} chunks wide, are submitted at
     * {@link #COSTLY_PRIORITY} plus their squared distance from the view center, and stop when no longer wanted. Other
     * features use {@link #REGION_CHUNKS} wide regions at priority 0 and always finish.
     */
    public FeatureIndex(ChunkProbe probe, Scheduler worker, Executor mainThread, Predicate<Identifier> costly, int costlyRegionChunks) {
        this.probe = probe;
        this.worker = worker;
        this.mainThread = mainThread;
        this.costly = costly;
        this.costlyRegionChunks = costlyRegionChunks;
    }

    private int regionChunks(boolean slow) {
        return slow ? costlyRegionChunks : REGION_CHUNKS;
    }

    public List<FeatureFinder.FeatureHit> query(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, Set<Identifier> featureIds) {
        List<FeatureFinder.FeatureHit> out = new ArrayList<>();
        if (featureIds.isEmpty()) {
            wanted = Set.of();
            return out;
        }
        Set<RegionKey> nowWanted = new HashSet<>();
        List<RegionKey> toSubmit = new ArrayList<>();
        for (Identifier id : featureIds) {
            boolean slow = costly.test(id);
            int regionBlocks = regionChunks(slow) * 16;
            int minRx = Math.floorDiv(minBlockX, regionBlocks);
            int maxRx = Math.floorDiv(maxBlockX, regionBlocks);
            int minRz = Math.floorDiv(minBlockZ, regionBlocks);
            int maxRz = Math.floorDiv(maxBlockZ, regionBlocks);
            long count = (long) (maxRx - minRx + 1) * (maxRz - minRz + 1);
            if (count > MAX_REGIONS_PER_QUERY) continue;
            for (int rx = minRx; rx <= maxRx; rx++) {
                for (int rz = minRz; rz <= maxRz; rz++) {
                    RegionKey key = new RegionKey(id, rx, rz);
                    List<FeatureFinder.FeatureHit> known = regions.get(key);
                    if (known != null) {
                        for (FeatureFinder.FeatureHit hit : known) {
                            if (hit.blockX() >= minBlockX && hit.blockX() <= maxBlockX
                                    && hit.blockZ() >= minBlockZ && hit.blockZ() <= maxBlockZ) {
                                out.add(hit);
                            }
                        }
                        continue;
                    }
                    if (slow) nowWanted.add(key);
                    if (pending.add(key)) toSubmit.add(key);
                }
            }
        }
        // Published before submitting, so a new job never sees the previous query's set.
        wanted = nowWanted.isEmpty() ? Set.of() : nowWanted;
        double centerX = (minBlockX + (double) maxBlockX) / 2;
        double centerZ = (minBlockZ + (double) maxBlockZ) / 2;
        for (RegionKey key : toSubmit) submit(key, centerX, centerZ);
        return out;
    }

    private void submit(RegionKey key, double centerX, double centerZ) {
        final int gen = generation;
        boolean slow = costly.test(key.featureId());
        int size = regionChunks(slow);
        double priority = 0.0;
        if (slow) {
            double half = size * 8.0;
            double dx = key.regionX() * size * 16.0 + half - centerX;
            double dz = key.regionZ() * size * 16.0 + half - centerZ;
            priority = COSTLY_PRIORITY + dx * dx + dz * dz;
        }
        worker.submit(priority, () -> {
            List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
            try {
                int baseX = key.regionX() * size;
                int baseZ = key.regionZ() * size;
                for (int cx = 0; cx < size; cx++) {
                    for (int cz = 0; cz < size; cz++) {
                        if (slow && (gen != generation || !wanted.contains(key))) {
                            // Abandoned: the partial hits are dropped and the region asked for again when it is wanted.
                            release(key, gen);
                            return;
                        }
                        hits.addAll(probe.probe(key.featureId(), baseX + cx, baseZ + cz));
                    }
                }
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.warn("Feature probe failed for {}", key, t);
                release(key, gen);
                return;
            }
            List<FeatureFinder.FeatureHit> result = List.copyOf(hits);
            mainThread.execute(() -> {
                if (gen == generation) {
                    pending.remove(key);
                    regions.put(key, result);
                }
            });
        });
    }

    private void release(RegionKey key, int gen) {
        mainThread.execute(() -> {
            if (gen == generation) pending.remove(key);
        });
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Nothing costly is wanted until the next query, so running costly regions stop; for when the map is not shown. */
    public void dropWanted() {
        wanted = Set.of();
    }

    public void clear() {
        generation++;
        wanted = Set.of();
        regions.clear();
        pending.clear();
    }
}
