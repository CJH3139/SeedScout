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
import net.minecraft.resources.Identifier;

public final class FeatureIndex {
    public static final int REGION_CHUNKS = 16;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16;
    public static final int MAX_REGIONS_PER_QUERY = 256;

    @FunctionalInterface
    public interface ChunkProbe {
        List<FeatureFinder.FeatureHit> probe(Identifier featureId, int chunkX, int chunkZ);
    }

    private record RegionKey(Identifier featureId, int regionX, int regionZ) {}

    private final ChunkProbe probe;
    private final Executor worker;
    private final Executor mainThread;
    private final Map<RegionKey, List<FeatureFinder.FeatureHit>> regions = new HashMap<>();
    private final Set<RegionKey> pending = new HashSet<>();
    private int generation = 0;

    public FeatureIndex(ChunkProbe probe, Executor worker, Executor mainThread) {
        this.probe = probe;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    public List<FeatureFinder.FeatureHit> query(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, Set<Identifier> featureIds) {
        List<FeatureFinder.FeatureHit> out = new ArrayList<>();
        if (featureIds.isEmpty()) return out;
        int minRx = Math.floorDiv(minBlockX, REGION_BLOCKS);
        int maxRx = Math.floorDiv(maxBlockX, REGION_BLOCKS);
        int minRz = Math.floorDiv(minBlockZ, REGION_BLOCKS);
        int maxRz = Math.floorDiv(maxBlockZ, REGION_BLOCKS);
        long count = (long) (maxRx - minRx + 1) * (maxRz - minRz + 1);
        if (count > MAX_REGIONS_PER_QUERY) return out;
        for (Identifier id : featureIds) {
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
                    if (pending.add(key)) submit(key);
                }
            }
        }
        return out;
    }

    private void submit(RegionKey key) {
        final int gen = generation;
        worker.execute(() -> {
            List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
            try {
                int baseX = key.regionX() * REGION_CHUNKS;
                int baseZ = key.regionZ() * REGION_CHUNKS;
                for (int cx = 0; cx < REGION_CHUNKS; cx++) {
                    for (int cz = 0; cz < REGION_CHUNKS; cz++) {
                        hits.addAll(probe.probe(key.featureId(), baseX + cx, baseZ + cz));
                    }
                }
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.warn("Feature probe failed for {}", key, t);
                mainThread.execute(() -> {
                    if (gen == generation) pending.remove(key);
                });
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

    public int pendingCount() {
        return pending.size();
    }

    public void clear() {
        generation++;
        regions.clear();
        pending.clear();
    }
}
