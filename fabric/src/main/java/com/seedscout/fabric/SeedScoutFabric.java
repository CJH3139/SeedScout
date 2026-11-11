package com.seedscout.fabric;

import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import com.seedscout.waypoint.BeamRenderer;
import com.seedscout.waypoint.HudArrowRenderer;
import com.seedscout.waypoint.ShareHandler;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.RenderPipelines;

public final class SeedScoutFabric implements ClientModInitializer {
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
    }
}
