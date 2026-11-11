package com.seedscout.neoforge;

import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import com.seedscout.waypoint.BeamRenderer;
import com.seedscout.waypoint.HudArrowRenderer;
import com.seedscout.waypoint.ShareHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = SeedScoutClient.MOD_ID, dist = Dist.CLIENT)
public final class SeedScoutNeoForge {
    public SeedScoutNeoForge(IEventBus modBus) {
        SeedScoutClient.init(FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            KeyMapping.Category category = new KeyMapping.Category(SeedScoutClient.KEY_CATEGORY);
            event.registerCategory(category);
            event.register(SeedScoutClient.createOpenMapKey(category));
        });
        modBus.addListener(RegisterGuiLayersEvent.class, event ->
                event.registerAboveAll(SeedScoutClient.HUD_LAYER, HudArrowRenderer::render));
        modBus.addListener(RegisterRenderPipelinesEvent.class, event -> event.registerPipeline(Compat.SEE_THROUGH));

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(SubmitCustomGeometryEvent.class, event ->
                BeamRenderer.render(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState()));
        gameBus.addListener(ClientChatReceivedEvent.Player.class, event -> ShareHandler.onChat(event.getMessage()));
        gameBus.addListener(ClientChatReceivedEvent.System.class, event ->
                ShareHandler.onGameMessage(event.getMessage(), event.isOverlay()));
        gameBus.addListener(RegisterClientCommandsEvent.class, event ->
                event.getDispatcher().register(ShareHandler.<CommandSourceStack>command()));
        gameBus.addListener(ClientTickEvent.Post.class, event -> SeedScoutClient.tick(Minecraft.getInstance()));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event ->
                SeedScoutClient.disconnect(Minecraft.getInstance()));
        gameBus.addListener(GameShuttingDownEvent.class, event -> SeedScoutClient.stopping());
    }
}
