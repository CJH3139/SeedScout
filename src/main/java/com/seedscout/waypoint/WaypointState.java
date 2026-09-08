package com.seedscout.waypoint;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WaypointState {
    public static final double ARRIVE_DISTANCE = 16.0;
    private static Waypoint current;

    private WaypointState() {}

    public static Optional<Waypoint> get() {
        return Optional.ofNullable(current);
    }

    public static void set(Waypoint waypoint) {
        current = waypoint;
    }

    public static void clear() {
        current = null;
    }

    public static void tick(Minecraft client) {
        if (current == null || client.player == null) return;
        double distance = Bearing.distance(client.player.getX(), client.player.getZ(), current.x() + 0.5, current.z() + 0.5);
        if (distance <= ARRIVE_DISTANCE) {
            client.player.sendSystemMessage(Component.translatable("seedscout.waypoint.arrived", current.name()));
            current = null;
        }
    }
}
