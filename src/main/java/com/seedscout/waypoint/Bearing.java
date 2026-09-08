package com.seedscout.waypoint;

public final class Bearing {
    private Bearing() {}

    public static float targetYaw(double px, double pz, double tx, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float relativeDegrees(double px, double pz, float playerYaw, double tx, double tz) {
        float delta = targetYaw(px, pz, tx, tz) - playerYaw;
        delta %= 360f;
        if (delta > 180f) delta -= 360f;
        if (delta <= -180f) delta += 360f;
        return delta;
    }

    public static double distance(double px, double pz, double tx, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
