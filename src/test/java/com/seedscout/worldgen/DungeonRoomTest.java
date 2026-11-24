package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.compat.Compat;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DungeonRoomTest {
    private static final int OX = 0;
    private static final int OY = 64;
    private static final int OZ = 0;
    private static BlockState STONE;
    private static BlockState AIR;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        STONE = Blocks.STONE.defaultBlockState();
        AIR = Blocks.AIR.defaultBlockState();
    }

    @Test
    void solidBoxWithNoOpeningFails() {
        assertTrue(placeFixed(stoneBox()).isEmpty());
    }

    @Test
    void oneOpeningPasses() {
        assertTrue(placeFixed(withHoles(stoneBox(), 1)).isPresent());
    }

    @Test
    void fiveOpeningsPass() {
        assertTrue(placeFixed(withHoles(stoneBox(), 5)).isPresent());
    }

    @Test
    void sixOpeningsFail() {
        assertTrue(placeFixed(withHoles(stoneBox(), 6)).isEmpty());
    }

    @Test
    void missingFloorFails() {
        Grid grid = withHoles(stoneBox(), 1);
        grid.set(OX, OY - 1, OZ, AIR);
        assertTrue(placeFixed(grid).isEmpty());
    }

    @Test
    void missingCeilingFails() {
        Grid grid = withHoles(stoneBox(), 1);
        grid.set(OX, OY + 4, OZ, AIR);
        assertTrue(placeFixed(grid).isEmpty());
    }

    @Test
    void waterOpeningIsNotAnOpening() {
        Grid grid = stoneBox();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int r = 3; r <= 4; r++) {
            grid.set(OX + r, OY, OZ, water);
            grid.set(OX + r, OY + 1, OZ, water);
        }
        assertTrue(placeFixed(grid).isEmpty());
    }

    @Test
    void nearWorldBottom() {
        int minY = -64;
        int successes = 0;
        for (long seed = 0; seed < 20; seed++) {
            Grid grid = new Grid(minY);
            int y = minY + 1;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    grid.set(dx, minY, dz, Blocks.BEDROCK.defaultBlockState());
                    for (int dy = 1; dy <= 6; dy++) {
                        grid.set(dx, minY + dy, dz, STONE);
                    }
                }
            }
            grid.set(3, y, 0, AIR);
            grid.set(3, y + 1, 0, AIR);
            grid.set(4, y, 0, AIR);
            grid.set(4, y + 1, 0, AIR);
            if (assertMatchesVanilla(grid, seed, 0, y, 0)) successes++;
        }
        assertTrue(successes > 0, "successes " + successes);
    }

    @Test
    void matchesVanilla() {
        int successes = 0;
        for (long seed = 0; seed < 200; seed++) {
            if (assertMatchesVanilla(caves(seed), seed, OX, OY, OZ)) successes++;
        }
        assertTrue(successes > 10, "successes " + successes);
        assertTrue(successes < 190, "successes " + successes);
    }

    private static boolean assertMatchesVanilla(Grid grid, long seed, int x, int y, int z) {
        Grid ours = grid.copy();
        RandomSource ourRandom = RandomSource.create(seed);
        Optional<DungeonMob> mob = DungeonRoom.place(ours, ourRandom, x, y, z);

        Grid theirs = grid.copy();
        EntityType<?>[] spawned = new EntityType<?>[1];
        RandomSource vanillaRandom = RandomSource.create(seed);
        boolean carved = Compat.placeMonsterRoom(vanillaLevel(theirs, spawned), vanillaRandom, new BlockPos(x, y, z));
        // Vanilla still returns true when the spawner could not be placed; only a spawned mob counts as a dungeon.
        boolean placed = carved && spawned[0] != null;

        assertEquals(placed, mob.isPresent(), "seed " + seed);
        assertEquals(theirs.blocks, ours.blocks, "seed " + seed);
        assertEquals(vanillaRandom.nextLong(), ourRandom.nextLong(), "random state, seed " + seed);
        if (placed) {
            String expected = BuiltInRegistries.ENTITY_TYPE.getKey(spawned[0]).getPath();
            assertEquals(expected + "_dungeon", mob.get().gridId().getPath(), "seed " + seed);
        }
        return placed;
    }

    private static WorldGenLevel vanillaLevel(Grid grid, EntityType<?>[] spawned) {
        Map<BlockPos, BlockEntity> entities = new HashMap<>();
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> grid.get((BlockPos) args[0]);
                    case "isEmptyBlock" -> grid.get((BlockPos) args[0]).isAir();
                    case "getMinY" -> grid.minY();
                    case "setBlock" -> {
                        BlockPos pos = (BlockPos) args[0];
                        grid.set(pos.getX(), pos.getY(), pos.getZ(), (BlockState) args[1]);
                        yield true;
                    }
                    case "getBlockEntity" -> {
                        BlockPos pos = ((BlockPos) args[0]).immutable();
                        BlockState state = grid.get(pos);
                        if (state.is(Blocks.CHEST)) {
                            yield entities.computeIfAbsent(pos, p -> new ChestBlockEntity(p, state));
                        }
                        if (state.is(Blocks.SPAWNER)) {
                            yield entities.computeIfAbsent(pos, p -> new SpawnerBlockEntity(p, state) {
                                @Override
                                public void setEntityId(EntityType<?> type, RandomSource random) {
                                    spawned[0] = type;
                                    super.setEntityId(type, random);
                                }
                            });
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "DungeonRoomTest level";
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Optional<DungeonMob> placeFixed(Grid grid) {
        return DungeonRoom.place(grid, RandomSource.create(1L), OX, OY, OZ);
    }

    private static Grid stoneBox() {
        Grid grid = new Grid(-64);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 7; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    grid.set(OX + dx, OY + dy, OZ + dz, STONE);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    grid.set(OX + dx, OY + dy, OZ + dz, AIR);
                }
            }
        }
        return grid;
    }

    // Holes at dx/dz = +-3 and +-4 so they sit on the wall ring for every rolled room size.
    private static Grid withHoles(Grid grid, int count) {
        int[][] spots = {{3, -1}, {3, 1}, {-3, -1}, {-3, 1}, {-1, 3}, {1, 3}, {-1, -3}, {1, -3}};
        for (int i = 0; i < count; i++) {
            for (int r = 3; r <= 4; r++) {
                int hx = spots[i][0] == 3 || spots[i][0] == -3 ? Integer.signum(spots[i][0]) * r : spots[i][0];
                int hz = spots[i][1] == 3 || spots[i][1] == -3 ? Integer.signum(spots[i][1]) * r : spots[i][1];
                grid.set(OX + hx, OY, OZ + hz, AIR);
                grid.set(OX + hx, OY + 1, OZ + hz, AIR);
            }
        }
        return grid;
    }

    // A carved room of random size with random two-tall tunnels, stray pockets and the odd bedrock block.
    private static Grid caves(long seed) {
        RandomSource noise = RandomSource.create(seed * 31 + 7);
        Grid grid = new Grid(-64);
        for (int dx = -7; dx <= 7; dx++) {
            for (int dy = -3; dy <= 7; dy++) {
                for (int dz = -7; dz <= 7; dz++) {
                    grid.set(OX + dx, OY + dy, OZ + dz, STONE);
                }
            }
        }
        int rx = 1 + noise.nextInt(3);
        int rz = 1 + noise.nextInt(3);
        carve(grid, noise, -rx, rx, 0, 3, -rz, rz);
        int tunnels = noise.nextInt(8);
        for (int t = 0; t < tunnels; t++) {
            int offset = noise.nextInt(7) - 3;
            int top = noise.nextInt(4) == 0 ? 0 : 1;
            switch (noise.nextInt(4)) {
                case 0 -> carve(grid, noise, 0, 7, 0, top, offset, offset);
                case 1 -> carve(grid, noise, -7, 0, 0, top, offset, offset);
                case 2 -> carve(grid, noise, offset, offset, 0, top, 0, 7);
                default -> carve(grid, noise, offset, offset, 0, top, -7, 0);
            }
        }
        if (noise.nextInt(4) == 0) {
            int cx = noise.nextInt(13) - 6;
            int cy = noise.nextInt(9) - 2;
            int cz = noise.nextInt(13) - 6;
            carve(grid, noise, cx - 1, cx + 1, cy - 1, cy + 1, cz - 1, cz + 1);
        }
        if (noise.nextInt(3) == 0) {
            grid.set(OX + noise.nextInt(11) - 5, OY + noise.nextInt(7) - 1, OZ + noise.nextInt(11) - 5, Blocks.BEDROCK.defaultBlockState());
        }
        return grid;
    }

    private static void carve(Grid grid, RandomSource noise, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int dx = x0; dx <= x1; dx++) {
            for (int dy = y0; dy <= y1; dy++) {
                for (int dz = z0; dz <= z1; dz++) {
                    grid.set(OX + dx, OY + dy, OZ + dz, noise.nextInt(8) == 0 ? Blocks.CAVE_AIR.defaultBlockState() : AIR);
                }
            }
        }
    }

    private static final class Grid implements DungeonRoom.Blocks {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final int minY;

        Grid(int minY) {
            this.minY = minY;
        }

        Grid copy() {
            Grid copy = new Grid(minY);
            copy.blocks.putAll(blocks);
            return copy;
        }

        BlockState get(BlockPos pos) {
            return get(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public BlockState get(int x, int y, int z) {
            if (y < minY) return Blocks.VOID_AIR.defaultBlockState();
            return blocks.getOrDefault(new BlockPos(x, y, z), AIR);
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            blocks.put(new BlockPos(x, y, z), state);
        }

        @Override
        public int minY() {
            return minY;
        }
    }
}
