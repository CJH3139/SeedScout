package com.seedscout.worldgen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Confirmed dungeons per chunk on disk, one file per 32x32 chunk region, so a chunk is only checked against terrain
 * once per seed and profile. Chunks with no dungeon are stored too. Best effort like the tile store: unreadable files
 * are dropped and failed writes are ignored. Thread-safe.
 */
public final class DungeonStore {
    /** Bump when anything that changes which dungeons are found changes, so old answers are not reused. */
    public static final int FORMAT = 1;
    private static final int MAGIC = 0x53534447;
    private static final int REGION_SHIFT = 5;
    private static final int REGION_CHUNKS = 1 << (REGION_SHIFT * 2);
    private static final int MAX_REGIONS = 64;
    private static final DungeonMob[] MOBS = DungeonMob.values();

    private final Path root;
    private final Map<Long, Region> regions = new LinkedHashMap<>(64, 0.75f, true);

    public DungeonStore(Path cacheRoot, String versionTag, String dimension, long seed, String profile) {
        this.root = cacheRoot.resolve("dungeons-v" + FORMAT + "-" + sanitize(versionTag)).resolve(dimension)
                .resolve(Long.toString(seed)).resolve(sanitize(profile));
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class Region {
        final Map<Integer, List<FeatureFinder.FeatureHit>> chunks = new HashMap<>();
        boolean dirty;
    }

    /** This chunk's dungeons if they were stored, mob ids as hit ids. */
    Optional<List<FeatureFinder.FeatureHit>> get(int chunkX, int chunkZ) {
        Region region = region(chunkX, chunkZ);
        synchronized (region) {
            return Optional.ofNullable(region.chunks.get(localIndex(chunkX, chunkZ)));
        }
    }

    void put(int chunkX, int chunkZ, List<FeatureFinder.FeatureHit> hits) {
        Region region = region(chunkX, chunkZ);
        synchronized (region) {
            region.chunks.put(localIndex(chunkX, chunkZ), List.copyOf(hits));
            region.dirty = true;
        }
    }

    /** Writes every region with new chunks. Safe to call from any thread at any time. */
    public void flush() {
        List<Map.Entry<Long, Region>> dirty = new ArrayList<>();
        synchronized (regions) {
            for (Map.Entry<Long, Region> entry : regions.entrySet()) dirty.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<Long, Region> entry : dirty) save(entry.getKey(), entry.getValue());
    }

    /** Writes under the region's lock, so two writers of one region cannot land out of order. */
    private void save(long key, Region region) {
        synchronized (region) {
            if (!region.dirty) return;
            write(key, encode(region));
            region.dirty = false;
        }
    }

    private Region region(int chunkX, int chunkZ) {
        long key = ((long) (chunkX >> REGION_SHIFT) << 32) ^ ((chunkZ >> REGION_SHIFT) & 0xFFFFFFFFL);
        Region region;
        List<Map.Entry<Long, Region>> evicted = new ArrayList<>();
        synchronized (regions) {
            region = regions.get(key);
            if (region == null) {
                region = new Region();
                regions.put(key, region);
                // Loaded inside the lock, so no other thread sees the region half filled. Region files are small.
                read(key, chunkX, chunkZ, region);
                var it = regions.entrySet().iterator();
                while (regions.size() > MAX_REGIONS && it.hasNext()) {
                    Map.Entry<Long, Region> eldest = it.next();
                    if (eldest.getValue() == region) continue;
                    it.remove();
                    evicted.add(eldest);
                }
            }
        }
        for (Map.Entry<Long, Region> entry : evicted) save(entry.getKey(), entry.getValue());
        return region;
    }

    private static int localIndex(int chunkX, int chunkZ) {
        int mask = (1 << REGION_SHIFT) - 1;
        return ((chunkX & mask) << REGION_SHIFT) | (chunkZ & mask);
    }

    Path pathFor(long key) {
        int rx = (int) (key >> 32);
        int rz = (int) key;
        return root.resolve(rx + "_" + rz + ".dungeons");
    }

    private static byte[] encode(Region region) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT);
            out.writeShort(region.chunks.size());
            for (Map.Entry<Integer, List<FeatureFinder.FeatureHit>> entry : region.chunks.entrySet()) {
                out.writeShort(entry.getKey());
                out.writeByte(entry.getValue().size());
                for (FeatureFinder.FeatureHit hit : entry.getValue()) {
                    out.writeByte(mobOf(hit).ordinal());
                    out.writeByte(((hit.blockX() & 15) << 4) | (hit.blockZ() & 15));
                    out.writeShort(hit.blockY());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private static DungeonMob mobOf(FeatureFinder.FeatureHit hit) {
        for (DungeonMob mob : MOBS) {
            if (mob.gridId().equals(hit.featureId())) return mob;
        }
        throw new IllegalArgumentException("not a dungeon hit: " + hit);
    }

    private void read(long key, int chunkX, int chunkZ, Region region) {
        Path path = pathFor(key);
        if (!Files.isRegularFile(path)) return;
        int baseX = (chunkX >> REGION_SHIFT) << REGION_SHIFT;
        int baseZ = (chunkZ >> REGION_SHIFT) << REGION_SHIFT;
        Map<Integer, List<FeatureFinder.FeatureHit>> chunks = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(path)))) {
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) throw new IOException("bad header");
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int local = in.readUnsignedShort();
                if (local >= REGION_CHUNKS) throw new IOException("bad chunk index");
                int cx = baseX + (local >> REGION_SHIFT);
                int cz = baseZ + (local & ((1 << REGION_SHIFT) - 1));
                int n = in.readUnsignedByte();
                List<FeatureFinder.FeatureHit> hits = new ArrayList<>(n);
                for (int j = 0; j < n; j++) {
                    int mob = in.readUnsignedByte();
                    if (mob >= MOBS.length) throw new IOException("bad mob");
                    int xz = in.readUnsignedByte();
                    int y = in.readShort();
                    hits.add(new FeatureFinder.FeatureHit(MOBS[mob].gridId(), (cx << 4) | (xz >> 4), y, (cz << 4) | (xz & 15)));
                }
                chunks.put(local, List.copyOf(hits));
            }
            if (in.available() != 0) throw new IOException("trailing bytes");
        } catch (IOException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // nothing else to do with an unreadable cache file
            }
            return;
        }
        region.chunks.putAll(chunks);
    }

    private void write(long key, byte[] bytes) {
        Path path = pathFor(key);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the disk cache is best effort; the dungeons are still known in memory
        }
    }
}
