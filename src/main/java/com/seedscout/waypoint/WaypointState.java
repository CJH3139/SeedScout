package com.seedscout.waypoint;

import com.seedscout.SeedScoutClient;
import com.seedscout.config.SeedScoutConfig;
import com.seedscout.gui.StructureIcons;
import com.seedscout.worldgen.SeedSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

public final class WaypointState {
    public static final double ARRIVE_DISTANCE = 16.0;
    public static final int CUSTOM_COLOR = 0xFFFFD700;

    private static Waypoint current;
    private static final List<Waypoint> saved = new ArrayList<>();
    private static String worldKey;

    private WaypointState() {}

    public static Optional<Waypoint> get() {
        return Optional.ofNullable(current);
    }

    public static List<Waypoint> saved() {
        return Collections.unmodifiableList(saved);
    }

    public static void set(Waypoint waypoint) {
        current = waypoint;
        if (!saved.contains(waypoint)) {
            saved.add(waypoint);
            persist();
        }
    }

    public static void activate(Waypoint waypoint) {
        current = waypoint;
    }

    public static void remove(Waypoint waypoint) {
        saved.remove(waypoint);
        if (waypoint.equals(current)) current = null;
        persist();
    }

    public static void clear() {
        current = null;
    }

    public static void reset() {
        current = null;
        saved.clear();
        worldKey = null;
    }

    public static int colorOf(Waypoint waypoint) {
        return waypoint.structureId() == null ? CUSTOM_COLOR : StructureIcons.colorFor(waypoint.structureId());
    }

    public static String worldKey(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            return "singleplayer:" + server.getWorldData().getLevelName();
        }
        return SeedSource.storageKey(client).orElse("unknown");
    }

    public static void ensureLoaded(Minecraft client) {
        if (client.player == null) return;
        String key = worldKey(client);
        if (key.equals(worldKey)) return;
        worldKey = key;
        current = null;
        saved.clear();
        SeedScoutConfig config = SeedScoutClient.config();
        for (SeedScoutConfig.SavedWaypoint w : config.waypoints.getOrDefault(key, List.of())) {
            if (w.name == null || w.dimension == null) continue;
            Identifier structure = w.structureId == null ? null : Identifier.tryParse(w.structureId);
            saved.add(new Waypoint(w.name, w.x, w.z, structure, Identifier.parse(w.dimension)));
        }
    }

    private static void persist() {
        if (worldKey == null) return;
        List<SeedScoutConfig.SavedWaypoint> out = new ArrayList<>();
        for (Waypoint w : saved) {
            SeedScoutConfig.SavedWaypoint s = new SeedScoutConfig.SavedWaypoint();
            s.name = w.name();
            s.x = w.x();
            s.z = w.z();
            s.structureId = w.structureId() == null ? null : w.structureId().toString();
            s.dimension = w.dimension().toString();
            out.add(s);
        }
        SeedScoutClient.config().waypoints.put(worldKey, out);
        SeedScoutClient.saveConfig();
    }

    public static boolean playerInDimension(Minecraft client, Waypoint waypoint) {
        return client.player != null && client.player.level().dimension().identifier().equals(waypoint.dimension());
    }

    public static void tick(Minecraft client) {
        ensureLoaded(client);
        if (current == null || client.player == null) return;
        if (!playerInDimension(client, current)) return;
        double distance = Bearing.distance(client.player.getX(), client.player.getZ(), current.x() + 0.5, current.z() + 0.5);
        if (distance <= ARRIVE_DISTANCE) {
            client.player.sendSystemMessage(Component.translatable("seedscout.waypoint.arrived", current.name()));
            current = null;
        }
    }
}
