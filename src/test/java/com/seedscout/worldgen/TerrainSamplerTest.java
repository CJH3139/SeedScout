package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.compat.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class TerrainSamplerTest {
    // Hash of every block state in chunk (0, 0) for seed 1; a worldgen data change requires updating it.
    private static final int CHUNK_0_0_HASH = 0xd028a89b;

    private static SeedWorld world;
    private static TerrainSampler terrain;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
        terrain = world.terrain();
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private NoiseColumn base(int x, int z) {
        return world.chunkGenerator().getBaseColumn(x, z, world.heightAccessor(), world.randomState());
    }

    @Test
    void carvingOnlyRemovesSolid() {
        int minY = terrain.minY();
        int maxY = world.heightAccessor().getMaxY();
        int differences = 0;
        int hash = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                NoiseColumn column = base(x, z);
                for (int y = minY; y <= maxY; y++) {
                    boolean baseSolid = solid(column.getBlock(y));
                    boolean sampleSolid = solid(terrain.get(x, y, z));
                    hash = hash * 31 + terrain.get(x, y, z).toString().hashCode();
                    if (baseSolid != sampleSolid) {
                        differences++;
                        assertTrue(baseSolid && !sampleSolid, "solid added at " + x + " " + y + " " + z
                                + ": base " + column.getBlock(y) + ", sample " + terrain.get(x, y, z));
                    }
                }
            }
        }
        System.out.println("carvingOnlyRemovesSolid differences in chunk 0 0: " + differences + ", block hash " + Integer.toHexString(hash));
        assertEquals(Integer.toHexString(CHUNK_0_0_HASH), Integer.toHexString(hash), "chunk 0 0 block hash");
    }

    @Test
    void carversRan() {
        int minY = terrain.minY();
        int carved = 0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                for (int x = cx * 16; x < cx * 16 + 16; x++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        NoiseColumn column = base(x, z);
                        for (int y = minY + 1; y < 0; y++) {
                            if (solid(column.getBlock(y)) && !solid(terrain.get(x, y, z))) carved++;
                        }
                    }
                }
            }
        }
        System.out.println("carversRan carved blocks below y 0: " + carved);
        assertTrue(carved > 0, "no carved blocks below y 0 in chunks -2..2");
    }

    @Test
    void surfaceMatchesSurfaceHeight() {
        int asserted = 0;
        for (int i = 0; i < 16; i++) {
            int x = -200 + i * 29;
            int z = 150 - i * 23;
            int surface = world.surfaceHeight(x, z);
            NoiseColumn column = base(x, z);
            boolean carvedTop = false;
            for (int y = surface - 7; y <= surface; y++) {
                if (solid(column.getBlock(y)) != solid(terrain.get(x, y, z))) carvedTop = true;
            }
            if (carvedTop) continue;
            int top = world.heightAccessor().getMaxY();
            while (top > terrain.minY() && terrain.get(x, top, z).isAir()) top--;
            assertTrue(Math.abs(top - surface) <= 1, "column " + x + " " + z + ": sampled top " + top + ", surface " + surface);
            asserted++;
        }
        assertTrue(asserted >= 8, "only " + asserted + " columns were not carved at the top");
    }

    @Test
    void cachesChunks() {
        int before = terrain.builtChunks();
        for (int i = 0; i < 1000; i++) {
            terrain.get(640 + (i % 16), -60 + (i % 300), 960 + (i / 16 % 16));
        }
        assertEquals(before + 1, terrain.builtChunks());
    }

    @Test
    @EnabledIfSystemProperty(named = "seedscout.timing", matches = ".*")
    void buildTerrainTiming() {
        for (int i = 0; i < 5; i++) {
            Compat.buildTerrain(world.chunkGenerator(), world.randomState(), SeedWorld.vanillaLookup(), world.heightAccessor(), 1L,
                    new ChunkPos(100 + i, 100));
        }
        int runs = 20;
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            Compat.buildTerrain(world.chunkGenerator(), world.randomState(), SeedWorld.vanillaLookup(), world.heightAccessor(), 1L,
                    new ChunkPos(200 + i, -200));
        }
        double mean = (System.nanoTime() - start) / 1e6 / runs;
        System.out.printf("buildTerrain mean ms per chunk: %.1f%n", mean);
    }
}
