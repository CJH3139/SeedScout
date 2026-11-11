package com.seedscout;

import com.mojang.blaze3d.platform.InputConstants;
import com.seedscout.compat.Compat;
import com.seedscout.config.SeedScoutConfig;
import com.seedscout.gui.SeedScoutScreen;
import com.seedscout.map.MapWorker;
import com.seedscout.waypoint.WaypointState;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedScoutClient {
    public static final String MOD_ID = "seedscout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier KEY_CATEGORY = Identifier.fromNamespaceAndPath(MOD_ID, "seedscout");
    public static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath(MOD_ID, "waypoint_arrow");

    public static KeyMapping OPEN_MAP;

    private static SeedScoutConfig config;
    private static Path configPath;
    private static Path gameDir;

    private SeedScoutClient() {}

    public static SeedScoutConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.save(configPath);
    }

    public static Path gameDir() {
        return gameDir;
    }

    public static KeyMapping createOpenMapKey(KeyMapping.Category category) {
        OPEN_MAP = new KeyMapping("key.seedscout.open_map", InputConstants.KEY_Y, category);
        return OPEN_MAP;
    }

    public static void init(Path gameDirectory, Path configDirectory) {
        gameDir = gameDirectory;
        configPath = configDirectory.resolve("seedscout.json");
        config = SeedScoutConfig.load(configPath);
        LOGGER.info("SeedScout loaded");
    }

    public static void tick(Minecraft client) {
        WaypointState.tick(client);
        while (OPEN_MAP.consumeClick()) {
            if (client.player == null) continue;
            if (Compat.screen(client) instanceof SeedScoutScreen) {
                Compat.setScreen(client, null);
            } else {
                Compat.setScreen(client, new SeedScoutScreen(client));
            }
        }
    }

    public static void disconnect(Minecraft client) {
        client.execute(() -> {
            WaypointState.clear();
            SeedScoutScreen.resetForNewWorld();
        });
    }

    public static void stopping() {
        MapWorker.shutdown();
    }
}
