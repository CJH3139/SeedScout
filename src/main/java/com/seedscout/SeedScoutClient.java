package com.seedscout;

import com.seedscout.config.SeedScoutConfig;
import com.seedscout.gui.SeedScoutScreen;
import com.seedscout.map.MapWorker;
import com.seedscout.waypoint.BeamRenderer;
import com.seedscout.waypoint.HudArrowRenderer;
import com.seedscout.waypoint.WaypointState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SeedScoutClient implements ClientModInitializer {
    public static final String MOD_ID = "seedscout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyBinding OPEN_MAP = new KeyBinding(
            "key.seedscout.open_map",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            KeyBinding.Category.create(Identifier.of(MOD_ID, "seedscout")));

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
        KeyBindingHelper.registerKeyBinding(OPEN_MAP);
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "waypoint_arrow"), HudArrowRenderer::render);

        BeamRenderer.init();
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(BeamRenderer::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            WaypointState.tick(client);
            while (OPEN_MAP.wasPressed()) {
                if (client.player == null) continue;
                if (client.currentScreen instanceof SeedScoutScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new SeedScoutScreen(client));
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
