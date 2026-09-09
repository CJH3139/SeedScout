package com.seedscout.waypoint;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.seedscout.worldgen.Dimension;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;

public final class ShareHandler {
    private ShareHandler() {}

    public static void init() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, profile, params, timestamp) ->
                ShareFormat.parse(message.getString()).ifPresent(ShareHandler::offer));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String text = message.getString();
            if (text.startsWith("[SeedScout]")) return;
            ShareFormat.parse(text).ifPresent(ShareHandler::offer);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("seedscout").then(ClientCommands.literal("waypoint")
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("dimension", StringArgumentType.word())
                                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                                            String dimension = StringArgumentType.getString(ctx, "dimension");
                                                            String name = StringArgumentType.getString(ctx, "name");
                                                            WaypointState.set(new Waypoint(name, x, z, null, dimensionId(dimension)));
                                                            ctx.getSource().sendFeedback(Component.translatable("seedscout.share.set", name, x, z));
                                                            return 1;
                                                        }))))))));
    }

    public static void share(Minecraft client, String name, int x, int z, Identifier dimension) {
        if (client.player == null) return;
        client.player.connection.sendChat(ShareFormat.format(name, x, z, dimensionKey(dimension)));
    }

    private static void offer(ShareFormat.Shared shared) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null) return;
            String command = "/seedscout waypoint " + shared.x() + " " + shared.z() + " " + shared.dimension() + " " + shared.name();
            Component link = Component.translatable("seedscout.share.follow")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("seedscout.share.hover", shared.name(), shared.x(), shared.z()))));
            client.player.sendSystemMessage(Component.literal("[SeedScout] ").withStyle(ChatFormatting.GRAY).append(link));
        });
    }

    private static String dimensionKey(Identifier dimension) {
        return Dimension.fromLevelId(dimension).name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Identifier dimensionId(String key) {
        for (Dimension d : Dimension.values()) {
            if (d.name().equalsIgnoreCase(key)) return d.levelId();
        }
        return Dimension.OVERWORLD.levelId();
    }
}
