package com.seedscout.worldgen;

import com.seedscout.compat.Compat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/** Block reads from real generated terrain (noise, surface and carvers), built per chunk on demand. */
final class TerrainSampler {
    // A ring search reads each chunk's neighbors in the rings before and after it, about 8 * ring chunks away in scan
    // order. 256 holds two full rings of the largest dungeon search (200 blocks, ring 12) with room for the threads in
    // flight; at 64 most chunks were built about three times. About 50 KB per chunk.
    private static final int CAPACITY = 256;
    private static final BlockState ABOVE = Blocks.AIR.defaultBlockState();
    private static final BlockState BELOW = Blocks.BEDROCK.defaultBlockState();

    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final HolderLookup.Provider lookup;
    private final LevelHeightAccessor height;
    private final long seed;
    private final AtomicInteger built = new AtomicInteger();
    private final Map<Long, CompletableFuture<ProtoChunk>> chunks = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CompletableFuture<ProtoChunk>> eldest) {
            return size() > CAPACITY;
        }
    };

    TerrainSampler(NoiseBasedChunkGenerator generator, RandomState randomState, HolderLookup.Provider lookup,
                   LevelHeightAccessor height, long seed) {
        this.generator = generator;
        this.randomState = randomState;
        this.lookup = lookup;
        this.height = height;
        this.seed = seed;
    }

    BlockState get(int x, int y, int z) {
        if (y < height.getMinY()) return BELOW;
        if (y > height.getMaxY()) return ABOVE;
        return chunk(x >> 4, z >> 4).getBlockState(new BlockPos(x, y, z));
    }

    int minY() {
        return height.getMinY();
    }

    /**
     * A read cursor for one thread; see {@link Reader}. Reads served from the remembered chunk do not refresh its LRU
     * order, so a chunk a Reader is busy with can still be evicted. That stays correct (the Reader keeps its own
     * reference), but another caller asking for that chunk afterwards builds it again, which is only extra work.
     */
    Reader reader() {
        return new Reader();
    }

    /**
     * Same reads as {@link TerrainSampler#get}, but remembers the last chunk so runs of reads in one chunk skip the
     * shared lock and the LRU reorder. Not thread-safe: each caller thread uses its own, for a bounded job.
     */
    final class Reader {
        private long lastKey;
        private ProtoChunk last;

        BlockState get(int x, int y, int z) {
            if (y < height.getMinY()) return BELOW;
            if (y > height.getMaxY()) return ABOVE;
            long key = ChunkPos.pack(x >> 4, z >> 4);
            if (last == null || key != lastKey) {
                last = chunk(x >> 4, z >> 4);
                lastKey = key;
            }
            return last.getBlockState(new BlockPos(x, y, z));
        }
    }

    int builtChunks() {
        return built.get();
    }

    private ProtoChunk chunk(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        CompletableFuture<ProtoChunk> future;
        boolean owner = false;
        synchronized (chunks) {
            future = chunks.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                chunks.put(key, future);
                owner = true;
            }
        }
        if (owner) {
            try {
                future.complete(Compat.buildTerrain(generator, randomState, lookup, height, seed, new ChunkPos(chunkX, chunkZ)));
                built.incrementAndGet();
            } catch (RuntimeException | Error e) {
                synchronized (chunks) {
                    chunks.remove(key, future);
                }
                future.completeExceptionally(e);
                throw e;
            }
        }
        return future.join();
    }
}
