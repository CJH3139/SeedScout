package com.seedscout.waypoint;

import com.seedscout.gui.StructureIcons;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WaypointState {
    public static final double ARRIVE_DISTANCE = 16.0;
    public static final int CUSTOM_COLOR = 0xFFFFD700;

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

    public static int colorOf(Waypoint waypoint) {
        return waypoint.structureId() == null ? CUSTOM_COLOR : StructureIcons.colorFor(waypoint.structureId());
    }

    public static boolean playerInDimension(Minecraft client, Waypoint waypoint) {
        return client.player != null && client.player.level().dimension().identifier().equals(waypoint.dimension());
    }

    public static void tick(Minecraft client) {
        if (current == null || client.player == null) return;
        if (!playerInDimension(client, current)) return;
        double distance = Bearing.distance(client.player.getX(), client.player.getZ(), current.x() + 0.5, current.z() + 0.5);
        if (distance <= ARRIVE_DISTANCE) {
            client.player.sendSystemMessage(Component.translatable("seedscout.waypoint.arrived", current.name()));
            current = null;
        }
    }
}
