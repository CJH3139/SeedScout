package com.seedscout;

import com.mojang.blaze3d.platform.InputConstants;
import com.seedscout.config.SeedScoutConfig;
import com.seedscout.gui.SeedScoutScreen;
import com.seedscout.map.MapWorker;
import com.seedscout.waypoint.BeamRenderer;
import com.seedscout.waypoint.HudArrowRenderer;
import com.seedscout.waypoint.WaypointState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedScoutClient implements ClientModInitializer {
    public static final String MOD_ID = "seedscout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.seedscout.open_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "seedscout")));

    private static SeedScoutConfig config;

    public static SeedScoutConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.save(SeedScoutConfig.DEFAULT_PATH);
    }

    @Override
    public void onInitializeClient() {
        config = SeedScoutConfig.load(SeedScoutConfig.DEFAULT_PATH);
        KeyMappingHelper.registerKeyMapping(OPEN_MAP);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "waypoint_arrow"), HudArrowRenderer::render);

        BeamRenderer.init();
        LevelRenderEvents.COLLECT_SUBMITS.register(BeamRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WaypointState.tick(client);
            while (OPEN_MAP.consumeClick()) {
                if (client.player == null) continue;
                if (client.gui.screen() instanceof SeedScoutScreen) {
                    client.gui.setScreen(null);
                } else {
                    client.gui.setScreen(new SeedScoutScreen(client));
                }
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            WaypointState.clear();
            SeedScoutScreen.resetForNewWorld();
        }));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MapWorker.shutdown());
        LOGGER.info("SeedScout loaded");
    }
}
