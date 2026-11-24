package com.seedscout.fabric;

import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import com.seedscout.waypoint.BeamRenderer;
import com.seedscout.waypoint.HudArrowRenderer;
import com.seedscout.waypoint.ShareHandler;
import com.seedscout.worldgen.DungeonAudit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class SeedScoutFabric implements ClientModInitializer {
    private static final String AUDIT_WORLD = "seedscout-audit";
    private static final long AUDIT_SEED = 1L;

    @Override
    public void onInitializeClient() {
        SeedScoutClient.init(FabricLoader.getInstance().getGameDir(), FabricLoader.getInstance().getConfigDir());
        KeyMappingHelper.registerKeyMapping(
                SeedScoutClient.createOpenMapKey(KeyMapping.Category.register(SeedScoutClient.KEY_CATEGORY)));
        HudElementRegistry.addLast(SeedScoutClient.HUD_LAYER, HudArrowRenderer::render);

        RenderPipelines.register(Compat.SEE_THROUGH);
        LevelRenderEvents.COLLECT_SUBMITS.register(context ->
                BeamRenderer.render(context.poseStack(), context.submitNodeCollector(), context.levelState()));

        ClientReceiveMessageEvents.CHAT.register((message, signed, profile, params, timestamp) ->
                ShareHandler.onChat(message));
        ClientReceiveMessageEvents.GAME.register(ShareHandler::onGameMessage);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ShareHandler.<FabricClientCommandSource>command()));

        ClientTickEvents.END_CLIENT_TICK.register(SeedScoutClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SeedScoutClient.disconnect(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SeedScoutClient.stopping());
        registerDungeonAudit();
    }

    /**
     * Dev runs only, with {@code -Dseedscout.audit=cx,cz,radius}: creates a fresh seed 1 world from the title screen,
     * audits {@link DungeonAudit} against its real chunks once the integrated server starts, logs the report and quits.
     */
    private static void registerDungeonAudit() {
        String spec = System.getProperty("seedscout.audit");
        if (spec == null || !FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        String[] parts = spec.split(",");
        int centerX = Integer.parseInt(parts[0].trim());
        int centerZ = Integer.parseInt(parts[1].trim());
        int radius = Integer.parseInt(parts[2].trim());
        boolean[] created = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (created[0] || !(Compat.screen(client) instanceof TitleScreen title)) return;
            created[0] = true;
            if (client.getLevelSource().levelExists(AUDIT_WORLD)) {
                SeedScoutClient.LOGGER.error("Dungeon audit: delete saves/{} first, it must be generated fresh", AUDIT_WORLD);
                client.stop();
                return;
            }
            SeedScoutClient.LOGGER.info("Dungeon audit: creating world {} with seed {}", AUDIT_WORLD, AUDIT_SEED);
            LevelSettings settings = new LevelSettings(AUDIT_WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT,
                    true, WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel(AUDIT_WORLD, settings, new WorldOptions(AUDIT_SEED, true, false),
                    WorldPresets::createNormalWorldDimensions, title);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                if (server.overworld().getSeed() != AUDIT_SEED) {
                    SeedScoutClient.LOGGER.error("Dungeon audit: world seed is {}, expected {}", server.overworld().getSeed(), AUDIT_SEED);
                    return;
                }
                long start = System.nanoTime();
                DungeonAudit.Report report = DungeonAudit.run(server, centerX, centerZ, radius);
                for (String line : report.lines()) SeedScoutClient.LOGGER.info("Dungeon audit: {}", line);
                SeedScoutClient.LOGGER.info("Dungeon audit result: real={} found={} falsePositives={} mobMismatches={} center={},{} radius={} seconds={}",
                        report.real(), report.found(), report.falsePositives(), report.mobMismatches(), centerX, centerZ, radius,
                        (System.nanoTime() - start) / 1_000_000_000L);
            } catch (RuntimeException e) {
                SeedScoutClient.LOGGER.error("Dungeon audit failed", e);
            } finally {
                Minecraft client = Minecraft.getInstance();
                client.execute(client::stop);
            }
        });
    }
}
