package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TileKeyTest {
    @Test
    void spanPerLod() {
        assertEquals(512, TileKey.tileSpanBlocks(0));
        assertEquals(2048, TileKey.tileSpanBlocks(1));
        assertEquals(8192, TileKey.tileSpanBlocks(2));
    }

    @Test
    void fromBlockHandlesNegatives() {
        assertEquals(new TileKey(0, 0, 0), TileKey.fromBlock(0, 0, 0));
        assertEquals(new TileKey(0, 0, 0), TileKey.fromBlock(0, 511, 511));
        assertEquals(new TileKey(0, 1, 0), TileKey.fromBlock(0, 512, 0));
        assertEquals(new TileKey(0, -1, -1), TileKey.fromBlock(0, -1, -1));
        assertEquals(new TileKey(2, -1, 0), TileKey.fromBlock(2, -8192, 0));
        assertEquals(new TileKey(2, -2, 0), TileKey.fromBlock(2, -8193, 0));
    }

    @Test
    void originIsTileTimesSpan() {
        TileKey key = new TileKey(1, -3, 2);
        assertEquals(-6144, key.originX());
        assertEquals(4096, key.originZ());
    }

    @Test
    void lodForScalePicksLargestAtOrBelow() {
        assertEquals(0, TileKey.lodForScale(1.0));
        assertEquals(0, TileKey.lodForScale(4.0));
        assertEquals(0, TileKey.lodForScale(15.9));
        assertEquals(1, TileKey.lodForScale(16.0));
        assertEquals(1, TileKey.lodForScale(63.0));
        assertEquals(2, TileKey.lodForScale(64.0));
        assertEquals(2, TileKey.lodForScale(1000.0));
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
        int overBudgetAtLod2 = 8192 * 23 - 1;
        assertEquals(2, TileKey.lodForView(4.0, 0, 0, overBudgetAtLod2, overBudgetAtLod2));
    }
}
