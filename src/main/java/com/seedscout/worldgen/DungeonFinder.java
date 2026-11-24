package com.seedscout.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Finds monster rooms by replaying vanilla decoration for {@code monster_room} and {@code monster_room_deep} on
 * generated terrain: placement and {@link DungeonRoom#place} share one random per feature, depth-first.
 */
public final class DungeonFinder {
    public static final Identifier ANY = Identifier.fromNamespaceAndPath("seedscout", "dungeon");
    public static final List<Identifier> IDS = List.of(ANY,
            DungeonMob.ZOMBIE.gridId(), DungeonMob.SKELETON.gridId(), DungeonMob.SPIDER.gridId());

    private static final List<Identifier> PLACED = List.of(
            Identifier.withDefaultNamespace("monster_room"), Identifier.withDefaultNamespace("monster_room_deep"));
    private static final int CAPACITY = 65536;
    private static final BlockState VOID = Blocks.VOID_AIR.defaultBlockState();

    private final FeatureFinder features;
    private final StructureReplay structures;
    private final TerrainSampler terrain;
    private final LevelHeightAccessor height;
    private final List<FeatureFinder.Entry> entries;
    private volatile DungeonStore store;
    private final Map<Long, CompletableFuture<List<FeatureFinder.FeatureHit>>> cache = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<List<FeatureFinder.FeatureHit>>> eldest) {
            return size() > CAPACITY;
        }
    };

    private DungeonFinder(FeatureFinder features, StructureReplay structures, TerrainSampler terrain, LevelHeightAccessor height,
                          List<FeatureFinder.Entry> entries) {
        this.features = features;
        this.structures = structures;
        this.terrain = terrain;
        this.height = height;
        this.entries = entries;
    }

    /**
     * Null unless both placed features generate in this dimension. Builds no terrain. {@code structures} may be null,
     * which skips the structure pieces vanilla places before dungeons.
     */
    static DungeonFinder create(FeatureFinder features, StructureReplay structures, TerrainSampler terrain, LevelHeightAccessor height) {
        List<FeatureFinder.Entry> entries = new ArrayList<>();
        for (Identifier id : PLACED) {
            FeatureFinder.Entry entry = features.resolve(id);
            if (entry == null) return null;
            entries.add(entry);
        }
        entries.sort(Comparator.comparingInt(FeatureFinder.Entry::step).thenComparingInt(FeatureFinder.Entry::index));
        return new DungeonFinder(features, structures, terrain, height, List.copyOf(entries));
    }

    /** Chunks are read from and written to {@code store} from now on; null turns the disk cache off. */
    void useStore(DungeonStore store) {
        this.store = store;
    }

    DungeonStore store() {
        return store;
    }

    public static boolean isDungeon(Identifier id) {
        return IDS.contains(id);
    }

    /** Confirmed dungeons whose spawner lies in this chunk: all of them for {@link #ANY}, else only that mob. */
    public List<FeatureFinder.FeatureHit> inChunk(Identifier id, int chunkX, int chunkZ) {
        if (!isDungeon(id)) return List.of();
        List<FeatureFinder.FeatureHit> all = all(chunkX, chunkZ);
        if (id.equals(ANY)) return all;
        return all.stream().filter(hit -> hit.featureId().equals(id)).toList();
    }

    private List<FeatureFinder.FeatureHit> all(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        CompletableFuture<List<FeatureFinder.FeatureHit>> future;
        boolean owner = false;
        synchronized (cache) {
            future = cache.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                cache.put(key, future);
                owner = true;
            }
        }
        if (owner) {
            try {
                future.complete(computeOrLoad(chunkX, chunkZ));
            } catch (RuntimeException | Error e) {
                synchronized (cache) {
                    cache.remove(key, future);
                }
                future.completeExceptionally(e);
                throw e;
            }
        }
        return future.join();
    }

    private List<FeatureFinder.FeatureHit> computeOrLoad(int chunkX, int chunkZ) {
        DungeonStore disk = store;
        if (disk == null) return compute(chunkX, chunkZ);
        Optional<List<FeatureFinder.FeatureHit>> stored = disk.get(chunkX, chunkZ);
        if (stored.isPresent()) return stored.get();
        List<FeatureFinder.FeatureHit> hits = compute(chunkX, chunkZ);
        disk.put(chunkX, chunkZ, hits);
        return hits;
    }

    private List<FeatureFinder.FeatureHit> compute(int chunkX, int chunkZ) {
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorationSeed = random.setDecorationSeed(features.seed(), chunkX << 4, chunkZ << 4);
        Overlay blocks = new Overlay();
        List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
        // Structures of a step place before its features, so the dungeons' own step is included. This reseeds random.
        if (structures != null) structures.placeAround(blocks, random, chunkX, chunkZ, entries.get(entries.size() - 1).step());
        for (FeatureFinder.Entry entry : entries) {
            random.setFeatureSeed(decorationSeed, entry.index(), entry.step());
            List<FeatureFinder.FeatureHit> entryHits = new ArrayList<>();
            try {
                features.walk(entry, random, chunkX, chunkZ, pos ->
                        DungeonRoom.place(blocks, random, pos.getX(), pos.getY(), pos.getZ()).ifPresent(mob ->
                                entryHits.add(new FeatureFinder.FeatureHit(mob.gridId(), pos.getX(), pos.getY(), pos.getZ()))));
            } catch (UnsupportedOperationException e) {
                // The proxy level cannot answer this feature's placement; drop its hits but keep the other feature's.
                continue;
            }
            hits.addAll(entryHits);
        }
        return finalHits(hits, blocks);
    }

    /**
     * Hits as the finished chunk shows them, in placement order. A later room's walls can turn an earlier spawner
     * into air, and that dungeon is gone in the game too. A later room centered on an existing spawner cannot replace
     * the block, but vanilla still sets the mob on that spawner's block entity, so the last hit per position wins.
     */
    static List<FeatureFinder.FeatureHit> finalHits(List<FeatureFinder.FeatureHit> hits, DungeonRoom.Blocks blocks) {
        Map<Long, FeatureFinder.FeatureHit> byPos = new LinkedHashMap<>();
        for (FeatureFinder.FeatureHit hit : hits) {
            long key = BlockPos.asLong(hit.blockX(), hit.blockY(), hit.blockZ());
            byPos.remove(key);
            byPos.put(key, hit);
        }
        return byPos.values().stream()
                .filter(hit -> blocks.get(hit.blockX(), hit.blockY(), hit.blockZ()).is(Blocks.SPAWNER))
                .toList();
    }

    /** Terrain reads with this chunk's dungeon writes layered on top. Outside the build height is void air. */
    private final class Overlay implements DungeonRoom.Blocks {
        private final Map<Long, BlockState> writes = new HashMap<>();
        private final TerrainSampler.Reader reader = terrain.reader();

        @Override
        public BlockState get(int x, int y, int z) {
            if (y < height.getMinY() || y > height.getMaxY()) return VOID;
            BlockState written = writes.get(BlockPos.asLong(x, y, z));
            return written != null ? written : reader.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (y < height.getMinY() || y > height.getMaxY()) return;
            writes.put(BlockPos.asLong(x, y, z), state);
        }

        @Override
        public int minY() {
            return height.getMinY();
        }
    }
}
