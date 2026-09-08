package com.seedscout.map;

import com.seedscout.SeedScoutClient;
import com.seedscout.worldgen.RegionHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import net.minecraft.util.Identifier;

public final class StructureIndex {
    public static final int MAX_REGIONS_PER_QUERY = 4096;

    @FunctionalInterface
    public interface RegionProbe {
        Optional<RegionHit> probe(Identifier setId, int regionX, int regionZ);
    }

    @FunctionalInterface
    public interface RingProbe {
        List<RegionHit> ring(Identifier setId);
    }

    public record SetInfo(Identifier setId, int spacingChunks, boolean concentric, Set<Identifier> structureIds) {
        boolean anyEnabled(Set<Identifier> enabled) {
            for (Identifier id : structureIds) {
                if (enabled.contains(id)) return true;
            }
            return false;
        }
    }

    private record RegionKey(Identifier setId, int regionX, int regionZ) {}

    private final List<SetInfo> sets;
    private final RegionProbe regionProbe;
    private final RingProbe ringProbe;
    private final Executor worker;
    private final Executor mainThread;

    private final Map<RegionKey, Optional<RegionHit>> regions = new HashMap<>();
    private final Set<RegionKey> pendingRegions = new HashSet<>();
    private final Map<Identifier, List<RegionHit>> rings = new HashMap<>();
    private final Set<Identifier> pendingRings = new HashSet<>();
    private int generation = 0;

    public StructureIndex(List<SetInfo> sets, RegionProbe regionProbe, RingProbe ringProbe, Executor worker, Executor mainThread) {
        this.sets = sets;
        this.regionProbe = regionProbe;
        this.ringProbe = ringProbe;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    public List<RegionHit> query(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, Set<Identifier> enabledStructureIds) {
        List<RegionHit> hits = new ArrayList<>();
        for (SetInfo set : sets) {
            if (!set.anyEnabled(enabledStructureIds)) continue;
            if (set.concentric()) {
                collectRing(set, minBlockX, minBlockZ, maxBlockX, maxBlockZ, enabledStructureIds, hits);
            } else {
                collectRegions(set, minBlockX, minBlockZ, maxBlockX, maxBlockZ, enabledStructureIds, hits);
            }
        }
        return hits;
    }

    private void collectRegions(SetInfo set, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ,
                                Set<Identifier> enabled, List<RegionHit> out) {
        int spanBlocks = set.spacingChunks() * 16;
        int minRx = Math.floorDiv(minBlockX, spanBlocks);
        int maxRx = Math.floorDiv(maxBlockX, spanBlocks);
        int minRz = Math.floorDiv(minBlockZ, spanBlocks);
        int maxRz = Math.floorDiv(maxBlockZ, spanBlocks);
        long count = (long) (maxRx - minRx + 1) * (maxRz - minRz + 1);
        if (count > MAX_REGIONS_PER_QUERY) {
            return;
        }
        final int gen = generation;
        for (int rx = minRx; rx <= maxRx; rx++) {
            for (int rz = minRz; rz <= maxRz; rz++) {
                RegionKey key = new RegionKey(set.setId(), rx, rz);
                Optional<RegionHit> known = regions.get(key);
                if (known != null) {
                    known.filter(h -> enabled.contains(idOf(h))).ifPresent(out::add);
                    continue;
                }
                if (pendingRegions.add(key)) {
                    worker.execute(() -> {
                        Optional<RegionHit> result;
                        try {
                            result = regionProbe.probe(key.setId(), key.regionX(), key.regionZ());
                        } catch (Throwable t) {
                            SeedScoutClient.LOGGER.warn("Region probe failed for {}", key, t);
                            mainThread.execute(() -> {
                                if (gen == generation) pendingRegions.remove(key);
                            });
                            return;
                        }
                        Optional<RegionHit> finalResult = result;
                        mainThread.execute(() -> {
                            if (gen == generation) {
                                pendingRegions.remove(key);
                                regions.put(key, finalResult);
                            }
                        });
                    });
                }
            }
        }
    }

    private void collectRing(SetInfo set, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ,
                             Set<Identifier> enabled, List<RegionHit> out) {
        List<RegionHit> known = rings.get(set.setId());
        if (known != null) {
            for (RegionHit hit : known) {
                if (hit.blockX() >= minBlockX && hit.blockX() <= maxBlockX
                        && hit.blockZ() >= minBlockZ && hit.blockZ() <= maxBlockZ
                        && enabled.contains(idOf(hit))) {
                    out.add(hit);
                }
            }
            return;
        }
        final int gen = generation;
        if (pendingRings.add(set.setId())) {
            worker.execute(() -> {
                List<RegionHit> result;
                try {
                    result = ringProbe.ring(set.setId());
                } catch (Throwable t) {
                    SeedScoutClient.LOGGER.warn("Ring probe failed for {}", set.setId(), t);
                    mainThread.execute(() -> {
                        if (gen == generation) pendingRings.remove(set.setId());
                    });
                    return;
                }
                List<RegionHit> finalResult = result;
                mainThread.execute(() -> {
                    if (gen == generation) {
                        pendingRings.remove(set.setId());
                        rings.put(set.setId(), finalResult);
                    }
                });
            });
        }
    }

    private static Identifier idOf(RegionHit hit) {
        return com.seedscout.worldgen.SeedWorld.idOf(hit.structure());
    }

    public int pendingCount() {
        return pendingRegions.size() + pendingRings.size();
    }

    public void clear() {
        generation++;
        regions.clear();
        rings.clear();
        pendingRegions.clear();
        pendingRings.clear();
    }
}
