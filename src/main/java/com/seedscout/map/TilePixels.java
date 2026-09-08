package com.seedscout.map;

public final class TilePixels {
    @FunctionalInterface
    public interface ColorSampler {
        int colorAt(int blockX, int blockZ);
    }

    private TilePixels() {}

    public static int[] render(TileKey key, ColorSampler sampler) {
        int size = TileKey.TILE_PIXELS;
        int bpp = TileKey.blocksPerPixel(key.lod());
        int half = bpp / 2;
        int originX = key.originX();
        int originZ = key.originZ();
        int[] pixels = new int[size * size];
        for (int pz = 0; pz < size; pz++) {
            int blockZ = originZ + pz * bpp + half;
            int row = pz * size;
            for (int px = 0; px < size; px++) {
                pixels[row + px] = sampler.colorAt(originX + px * bpp + half, blockZ);
            }
        }
        return pixels;
    }
}
