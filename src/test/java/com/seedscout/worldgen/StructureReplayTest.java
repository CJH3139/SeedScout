package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureReplayTest {
    private static SeedWorld world;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
    }

    private static StructureReplay replay() {
        HolderLookup.Provider lookup = SeedWorld.vanillaLookup();
        StructureReplay replay = StructureReplay.create(1L, Level.OVERWORLD, lookup, world.chunkGenerator(),
                world.chunkGenerator().getBiomeSource(), world.randomState(),
                world.chunkGenerator().createState(lookup.lookupOrThrow(Registries.STRUCTURE_SET), world.randomState(), 1L),
                world.heightAccessor());
        assertNotNull(replay, "no mineshafts in the overworld");
        return replay;
    }

    /** Solid stone everywhere, recording every write, so the result is only what the structure pieces place. */
    private static final class Recorder implements DungeonRoom.Blocks {
        final Map<Long, BlockState> writes = new HashMap<>();

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState written = writes.get(BlockPos.asLong(x, y, z));
            return written != null ? written : Blocks.STONE.defaultBlockState();
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            writes.put(BlockPos.asLong(x, y, z), state);
        }

        @Override
        public int minY() {
            return world.heightAccessor().getMinY();
        }
    }

    private static Map<Long, BlockState> place(StructureReplay replay, int chunkX, int chunkZ) {
        Recorder blocks = new Recorder();
        replay.placeAround(blocks, new WorldgenRandom(new XoroshiroRandomSource(0L)), chunkX, chunkZ, 3);
        return blocks.writes;
    }

    private static long differences(Map<Long, BlockState> a, Map<Long, BlockState> b) {
        Set<Long> keys = new HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        return keys.stream().filter(k -> a.get(k) != b.get(k)).count();
    }

    @Test
    void replayDoesNotDependOnEarlierReplays() {
        // Chunks around the mineshaft that the recorded dungeon at -503 -25 143 depends on.
        List<int[]> chunks = new ArrayList<>();
        for (int cx = -40; cx <= -24; cx++) {
            for (int cz = 0; cz <= 16; cz++) chunks.add(new int[]{cx, cz});
        }
        StructureReplay shared = replay();
        Map<Long, Map<Long, BlockState>> firstPass = new HashMap<>();
        for (int[] c : chunks) firstPass.put(BlockPos.asLong(c[0], 0, c[1]), place(shared, c[0], c[1]));
        assertTrue(firstPass.values().stream().anyMatch(writes -> !writes.isEmpty()), "no mineshaft pieces near chunk -32,8");

        Map<Long, Map<Long, BlockState>> again = new HashMap<>();
        for (int[] c : chunks) again.put(BlockPos.asLong(c[0], 0, c[1]), place(shared, c[0], c[1]));
        StructureReplay fresh = replay();
        Map<Long, Map<Long, BlockState>> reversed = new HashMap<>();
        for (int i = chunks.size() - 1; i >= 0; i--) {
            int[] c = chunks.get(i);
            reversed.put(BlockPos.asLong(c[0], 0, c[1]), place(fresh, c[0], c[1]));
        }
        for (int[] c : chunks) {
            long key = BlockPos.asLong(c[0], 0, c[1]);
            assertTrue(firstPass.get(key).equals(again.get(key)), "replaying chunk " + c[0] + "," + c[1] + " again changed "
                    + differences(firstPass.get(key), again.get(key)) + " blocks");
            assertTrue(firstPass.get(key).equals(reversed.get(key)), "chunk " + c[0] + "," + c[1] + " depends on query order in "
                    + differences(firstPass.get(key), reversed.get(key)) + " blocks");
        }
    }
}
