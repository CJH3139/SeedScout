package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TilePixelsTest {
    private static final TilePixels.ColorSampler SAMPLER = (x, z) -> (x << 16) ^ (z & 0xFFFF);

    @Test
    void samplesPixelCentersAtLodResolution() {
        TileKey key = new TileKey(1, -1, 2);
        int[] pixels = TilePixels.render(key, SAMPLER);
        assertEquals(TileKey.TILE_PIXELS * TileKey.TILE_PIXELS, pixels.length);

        int bpp = TileKey.blocksPerPixel(1);
        assertEquals(expectedColor(key, bpp, 0, 0), pixels[0]);
        assertEquals(expectedColor(key, bpp, 5, 7), pixels[7 * TileKey.TILE_PIXELS + 5]);

        int last = TileKey.TILE_PIXELS - 1;
        assertEquals(expectedColor(key, bpp, last, last), pixels[last * TileKey.TILE_PIXELS + last]);
    }

    @Test
    void samplesAtLod0Resolution() {
        TileKey key = new TileKey(0, 3, -2);
        int[] pixels = TilePixels.render(key, SAMPLER);
        int bpp = TileKey.blocksPerPixel(0);
        assertEquals(expectedColor(key, bpp, 9, 4), pixels[4 * TileKey.TILE_PIXELS + 9]);
    }

    @Test
    void samplesAtLod2Resolution() {
        TileKey key = new TileKey(2, -5, 6);
        int[] pixels = TilePixels.render(key, SAMPLER);
        int bpp = TileKey.blocksPerPixel(2);
        assertEquals(expectedColor(key, bpp, 12, 30), pixels[30 * TileKey.TILE_PIXELS + 12]);
    }

    private static int expectedColor(TileKey key, int bpp, int px, int pz) {
        int ex = key.originX() + px * bpp + bpp / 2;
        int ez = key.originZ() + pz * bpp + bpp / 2;
        return (ex << 16) ^ (ez & 0xFFFF);
    }
}
