package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapViewportTest {
    private MapViewport viewport() {
        MapViewport v = new MapViewport();
        v.left = 0;
        v.top = 0;
        v.width = 400;
        v.height = 200;
        v.centerX = 0;
        v.centerZ = 0;
        v.scale = 2.0;
        return v;
    }

    @Test
    void centerOfScreenIsCenterOfWorld() {
        MapViewport v = viewport();
        assertEquals(0.0, v.screenToWorldX(200), 1e-9);
        assertEquals(0.0, v.screenToWorldZ(100), 1e-9);
        assertEquals(200.0, v.worldToScreenX(0), 1e-9);
        assertEquals(100.0, v.worldToScreenZ(0), 1e-9);
    }

    @Test
    void scaleConvertsPixelsToBlocks() {
        MapViewport v = viewport();
        assertEquals(20.0, v.screenToWorldX(210), 1e-9);
        assertEquals(-200.0, v.screenToWorldZ(0), 1e-9);
    }

    @Test
    void panMovesCenterAgainstDrag() {
        MapViewport v = viewport();
        v.pan(10, -5);
        assertEquals(-20.0, v.centerX, 1e-9);
        assertEquals(10.0, v.centerZ, 1e-9);
    }

    @Test
    void zoomKeepsWorldPointUnderCursorFixed() {
        MapViewport v = viewport();
        double wx = v.screenToWorldX(300);
        double wz = v.screenToWorldZ(50);
        v.zoomAt(300, 50, 0.5);
        assertEquals(1.0, v.scale, 1e-9);
        assertEquals(wx, v.screenToWorldX(300), 1e-9);
        assertEquals(wz, v.screenToWorldZ(50), 1e-9);
    }

    @Test
    void zoomIsClamped() {
        assertEquals(64.0, MapViewport.MAX_SCALE, 1e-9);
        MapViewport v = viewport();
        v.zoomAt(0, 0, 1e-9);
        assertEquals(MapViewport.MIN_SCALE, v.scale, 1e-9);
        v.zoomAt(0, 0, 1e9);
        assertEquals(64.0, v.scale, 1e-9);
        assertEquals(2, v.lod(), "the clamped scale still lands on the coarsest LOD");
    }

    @Test
    void blockBoundsAndLod() {
        MapViewport v = viewport();
        assertEquals(-400, v.minBlockX());
        assertEquals(400, v.maxBlockX());
        assertEquals(-200, v.minBlockZ());
        assertEquals(200, v.maxBlockZ());
        assertEquals(0, v.lod());
        v.scale = 70;
        assertEquals(2, v.lod());
    }
}
