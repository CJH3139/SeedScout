package com.seedscout.worldgen;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mirrors vanilla {@code MonsterRoomFeature.place} call for call, so the random stream and the
 * written blocks match the game exactly.
 */
public final class DungeonRoom {
    private static final Block CHEST = net.minecraft.world.level.block.Blocks.CHEST;
    private static final Block SPAWNER = net.minecraft.world.level.block.Blocks.SPAWNER;
    private static final BlockState AIR = net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState();
    private static final BlockState COBBLESTONE = net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLESTONE = net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE.defaultBlockState();

    /**
     * Block access for {@link #place}. {@code get} must return a non-solid state (void air) below
     * {@code minY()}, and {@code set} must persist writes, since the chest and spawner checks read them back.
     */
    public interface Blocks {
        BlockState get(int x, int y, int z);

        void set(int x, int y, int z, BlockState state);

        int minY();
    }

    private DungeonRoom() {}

    public static Optional<DungeonMob> place(Blocks blocks, RandomSource random, int x, int y, int z) {
        int xr = random.nextInt(2) + 2;
        int minX = -xr - 1;
        int maxX = xr + 1;
        int zr = random.nextInt(2) + 2;
        int minZ = -zr - 1;
        int maxZ = zr + 1;
        int holeCount = 0;

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    boolean solid = blocks.get(x + dx, y + dy, z + dz).isSolid();
                    if (dy == -1 && !solid) return Optional.empty();
                    if (dy == 4 && !solid) return Optional.empty();
                    if ((dx == minX || dx == maxX || dz == minZ || dz == maxZ) && dy == 0
                            && blocks.get(x + dx, y, z + dz).isAir() && blocks.get(x + dx, y + 1, z + dz).isAir()) {
                        holeCount++;
                    }
                }
            }
        }

        if (holeCount < 1 || holeCount > 5) return Optional.empty();

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    int wx = x + dx;
                    int wy = y + dy;
                    int wz = z + dz;
                    BlockState wallState = blocks.get(wx, wy, wz);
                    if (dx == minX || dy == -1 || dz == minZ || dx == maxX || dy == 4 || dz == maxZ) {
                        if (wy >= blocks.minY() && !blocks.get(wx, wy - 1, wz).isSolid()) {
                            blocks.set(wx, wy, wz, AIR);
                        } else if (wallState.isSolid() && !wallState.is(CHEST)) {
                            if (dy == -1 && random.nextInt(4) != 0) {
                                safeSet(blocks, wx, wy, wz, MOSSY_COBBLESTONE);
                            } else {
                                safeSet(blocks, wx, wy, wz, COBBLESTONE);
                            }
                        }
                    } else if (!wallState.is(CHEST) && !wallState.is(SPAWNER)) {
                        safeSet(blocks, wx, wy, wz, AIR);
                    }
                }
            }
        }

        for (int cc = 0; cc < 2; cc++) {
            for (int i = 0; i < 3; i++) {
                int xc = x + random.nextInt(xr * 2 + 1) - xr;
                int zc = z + random.nextInt(zr * 2 + 1) - zr;
                if (blocks.get(xc, y, zc).isAir()) {
                    int wallCount = 0;
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        if (blocks.get(xc + direction.getStepX(), y, zc + direction.getStepZ()).isSolid()) {
                            wallCount++;
                        }
                    }
                    if (wallCount == 1) {
                        safeSet(blocks, xc, y, zc, reorientChest(blocks, xc, y, zc));
                        if (blocks.get(xc, y, zc).is(CHEST)) {
                            random.nextLong();
                        }
                        break;
                    }
                }
            }
        }

        safeSet(blocks, x, y, z, SPAWNER.defaultBlockState());
        // Vanilla returns true for an unreplaceable origin but places no spawner, so no dungeon exists.
        if (!blocks.get(x, y, z).is(SPAWNER)) return Optional.empty();
        return Optional.of(DungeonMob.fromRoll(random.nextInt(4)));
    }

    private static void safeSet(Blocks blocks, int x, int y, int z, BlockState state) {
        if (!blocks.get(x, y, z).is(BlockTags.FEATURES_CANNOT_REPLACE)) {
            blocks.set(x, y, z, state);
        }
    }

    // StructurePiece.reorient applied to a default chest.
    private static BlockState reorientChest(Blocks blocks, int x, int y, int z) {
        BlockState chest = CHEST.defaultBlockState();
        Direction solidNeighbor = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState state = blocks.get(x + direction.getStepX(), y, z + direction.getStepZ());
            if (state.is(CHEST)) return chest;
            if (state.isSolidRender()) {
                if (solidNeighbor != null) {
                    solidNeighbor = null;
                    break;
                }
                solidNeighbor = direction;
            }
        }
        if (solidNeighbor != null) {
            return chest.setValue(HorizontalDirectionalBlock.FACING, solidNeighbor.getOpposite());
        }
        Direction lockDir = chest.getValue(HorizontalDirectionalBlock.FACING);
        if (solidRender(blocks, x, y, z, lockDir)) lockDir = lockDir.getOpposite();
        if (solidRender(blocks, x, y, z, lockDir)) lockDir = lockDir.getClockWise();
        if (solidRender(blocks, x, y, z, lockDir)) lockDir = lockDir.getOpposite();
        return chest.setValue(HorizontalDirectionalBlock.FACING, lockDir);
    }

    private static boolean solidRender(Blocks blocks, int x, int y, int z, Direction direction) {
        return blocks.get(x + direction.getStepX(), y, z + direction.getStepZ()).isSolidRender();
    }
}
