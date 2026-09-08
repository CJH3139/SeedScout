package com.seedscout.worldgen;

import com.seedscout.config.SeedParser;
import com.seedscout.config.SeedScoutConfig;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

public final class SeedSource {
    private SeedSource() {}

    public static Optional<String> storageKey(MinecraftClient client) {
        if (client.getServer() != null) {
            return Optional.empty();
        }
        ServerInfo info = client.getCurrentServerEntry();
        if (info == null || info.address == null || info.address.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(info.address);
    }

    public static OptionalLong resolve(MinecraftClient client, SeedScoutConfig config) {
        IntegratedServer server = client.getServer();
        if (server != null) {
            return OptionalLong.of(server.getOverworld().getSeed());
        }
        return storageKey(client)
                .flatMap(config::seedFor)
                .map(SeedParser::parse)
                .orElse(OptionalLong.empty());
    }
}
