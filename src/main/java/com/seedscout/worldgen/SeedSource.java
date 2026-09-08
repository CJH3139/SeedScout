package com.seedscout.worldgen;

import com.seedscout.config.SeedParser;
import com.seedscout.config.SeedScoutConfig;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

public final class SeedSource {
    private SeedSource() {}

    public static Optional<String> storageKey(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            return Optional.empty();
        }
        ServerData info = client.getCurrentServer();
        if (info == null || info.ip == null || info.ip.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(info.ip);
    }

    public static OptionalLong resolve(Minecraft client, SeedScoutConfig config) {
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return OptionalLong.of(server.overworld().getSeed());
        }
        return storageKey(client)
                .flatMap(config::seedFor)
                .map(SeedParser::parse)
                .orElse(OptionalLong.empty());
    }
}
