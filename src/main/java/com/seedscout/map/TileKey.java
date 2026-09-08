package com.seedscout.map;

import java.util.ArrayList;
import java.util.List;

public record TileKey(int lod, int tileX, int tileZ) {
    public static final int TILE_PIXELS = 128;

    public static final int MAX_TILES_PER_VIEW = 512;
    private static final int[] BLOCKS_PER_PIXEL = {4, 16, 64};
    public static final int LOD_COUNT = BLOCKS_PER_PIXEL.length;

    public static int blocksPerPixel(int lod) {
        return BLOCKS_PER_PIXEL[lod];
    }

    public static int tileSpanBlocks(int lod) {
        return TILE_PIXELS * blocksPerPixel(lod);
    }

    public static TileKey fromBlock(int lod, int blockX, int blockZ) {
        int span = tileSpanBlocks(lod);
        return new TileKey(lod, Math.floorDiv(blockX, span), Math.floorDiv(blockZ, span));
    }

    public int originX() {
        return tileX * tileSpanBlocks(lod);
    }

    public int originZ() {
        return tileZ * tileSpanBlocks(lod);
    }

    public static int lodForScale(double blocksPerScreenPixel) {
        int lod = 0;
        for (int i = 0; i < LOD_COUNT; i++) {
            if (BLOCKS_PER_PIXEL[i] <= blocksPerScreenPixel) {
                lod = i;
            }
        }
        return lod;
    }

    public static List<TileKey> covering(int lod, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        TileKey min = fromBlock(lod, minBlockX, minBlockZ);
        TileKey max = fromBlock(lod, maxBlockX, maxBlockZ);
        long count = tileCount(min, max);
        if (count > MAX_TILES_PER_VIEW) {
            return List.of();
        }
        List<TileKey> keys = new ArrayList<>();
        for (int tx = min.tileX; tx <= max.tileX; tx++) {
            for (int tz = min.tileZ; tz <= max.tileZ; tz++) {
                keys.add(new TileKey(lod, tx, tz));
            }
        }
        return keys;
    }

    public static int lodForView(double scale, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int lod = lodForScale(scale);
        while (lod < LOD_COUNT - 1
                && tileCount(fromBlock(lod, minBlockX, minBlockZ), fromBlock(lod, maxBlockX, maxBlockZ)) > MAX_TILES_PER_VIEW) {
            lod++;
        }
        return lod;
    }

    private static long tileCount(TileKey min, TileKey max) {
        return (long) (max.tileX - min.tileX + 1) * (max.tileZ - min.tileZ + 1);
    }
}
