package com.seedscout.map;

public final class MapViewport {
    public static final double MIN_SCALE = 0.25;

    public static final double MAX_SCALE = 64.0;

    public int left;
    public int top;
    public int width;
    public int height;
    public double centerX;
    public double centerZ;

    public double scale = 4.0;

    public double screenToWorldX(double sx) {
        return centerX + (sx - (left + width / 2.0)) * scale;
    }

    public double screenToWorldZ(double sy) {
        return centerZ + (sy - (top + height / 2.0)) * scale;
    }

    public double worldToScreenX(double wx) {
        return left + width / 2.0 + (wx - centerX) / scale;
    }

    public double worldToScreenZ(double wz) {
        return top + height / 2.0 + (wz - centerZ) / scale;
    }

    public void pan(double screenDx, double screenDy) {
        centerX -= screenDx * scale;
        centerZ -= screenDy * scale;
    }

    public void zoomAt(double sx, double sy, double factor) {
        double wx = screenToWorldX(sx);
        double wz = screenToWorldZ(sy);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        centerX = wx - (sx - (left + width / 2.0)) * scale;
        centerZ = wz - (sy - (top + height / 2.0)) * scale;
    }

    public int minBlockX() {
        return (int) Math.floor(screenToWorldX(left));
    }

    public int maxBlockX() {
        return (int) Math.ceil(screenToWorldX(left + width));
    }

    public int minBlockZ() {
        return (int) Math.floor(screenToWorldZ(top));
    }

    public int maxBlockZ() {
        return (int) Math.ceil(screenToWorldZ(top + height));
    }

    public int lod() {
        return TileKey.lodForView(scale, minBlockX(), minBlockZ(), maxBlockX(), maxBlockZ());
    }

    public boolean contains(double sx, double sy) {
        return sx >= left && sx < left + width && sy >= top && sy < top + height;
    }
}
