package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TileKeyTest {
    @Test
    void spanPerLod() {
        assertEquals(512, TileKey.tileSpanBlocks(0));
        assertEquals(1024, TileKey.tileSpanBlocks(1));
        assertEquals(2048, TileKey.tileSpanBlocks(2));
        assertEquals(16384, TileKey.tileSpanBlocks(5));
    }

    @Test
    void fromBlockHandlesNegatives() {
        assertEquals(new TileKey(0, 0, 0), TileKey.fromBlock(0, 0, 0));
        assertEquals(new TileKey(0, 0, 0), TileKey.fromBlock(0, 511, 511));
        assertEquals(new TileKey(0, 1, 0), TileKey.fromBlock(0, 512, 0));
        assertEquals(new TileKey(0, -1, -1), TileKey.fromBlock(0, -1, -1));
        assertEquals(new TileKey(4, -1, 0), TileKey.fromBlock(4, -8192, 0));
        assertEquals(new TileKey(4, -2, 0), TileKey.fromBlock(4, -8193, 0));
    }

    @Test
    void originIsTileTimesSpan() {
        TileKey key = new TileKey(2, -3, 2);
        assertEquals(-6144, key.originX());
        assertEquals(4096, key.originZ());
    }

    @Test
    void lodForScalePicksLargestAtOrBelow() {
        assertEquals(0, TileKey.lodForScale(1.0));
        assertEquals(0, TileKey.lodForScale(4.0));
        assertEquals(0, TileKey.lodForScale(5.6));
        assertEquals(1, TileKey.lodForScale(5.7));
        assertEquals(2, TileKey.lodForScale(11.4));
        assertEquals(3, TileKey.lodForScale(22.7));
        assertEquals(4, TileKey.lodForScale(45.3));
        assertEquals(5, TileKey.lodForScale(90.6));
        assertEquals(5, TileKey.lodForScale(1000.0));
    }

    @Test
    void coveringIncludesEdgeTiles() {
        List<TileKey> keys = TileKey.covering(0, -1, 0, 512, 10);
        assertEquals(3, keys.size());
        assertTrue(keys.contains(new TileKey(0, -1, 0)));
        assertTrue(keys.contains(new TileKey(0, 0, 0)));
        assertTrue(keys.contains(new TileKey(0, 1, 0)));
    }

    @Test
    void coveringReturnsNothingAboveTheTileBudget() {
        assertTrue(TileKey.covering(0, 0, 0, 512 * 100 - 1, 512 * 100 - 1).isEmpty());

        assertEquals(484, TileKey.covering(0, 0, 0, 512 * 22 - 1, 512 * 22 - 1).size());
    }

    @Test
    void lodForViewFitsAtNaturalLod() {
        assertEquals(0, TileKey.lodForView(4.0, 0, 0, 10, 10));
    }

    @Test
    void lodForViewStepsUpWhenNaturalLodExceedsBudget() {
        int overBudgetAtLod0 = 512 * 23 - 1;

        assertEquals(1, TileKey.lodForView(4.0, 0, 0, overBudgetAtLod0, overBudgetAtLod0));
    }

    @Test
    void lodForViewStopsAtCoarsestLodEvenOverBudget() {
        int overBudgetAtLod5 = 16384 * 23 - 1;
        assertEquals(5, TileKey.lodForView(4.0, 0, 0, overBudgetAtLod5, overBudgetAtLod5));
    }

    @Test
    void ringSurroundsTheVisibleBlock() {
        List<TileKey> visible = TileKey.covering(0, 0, 0, 1023, 1023);
        assertEquals(4, visible.size());
        List<TileKey> ring = TileKey.ring(visible);
        assertEquals(12, ring.size());
        assertTrue(ring.contains(new TileKey(0, -1, -1)));
        assertTrue(ring.contains(new TileKey(0, 2, 2)));
        assertTrue(ring.stream().noneMatch(visible::contains));
        assertEquals(new TileKey(2, 0, 0), TileKey.coarserCovering(new TileKey(0, 3, 3), 2));
    }
}
