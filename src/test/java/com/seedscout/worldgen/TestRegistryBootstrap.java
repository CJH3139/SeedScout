package com.seedscout.worldgen;

import com.seedscout.SeedScoutClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

public final class TestRegistryBootstrap {
    private TestRegistryBootstrap() {}

    public static void bindVanillaTags() {
        PackResources vanillaPack = ServerPacksSource.createVanillaPackSource();
        try (MultiPackResourceManager resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanillaPack))) {
            RegistryAccess.Frozen staticRegistryManager = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            List<Registry.PendingTags<?>> pendingTagLoads = new ArrayList<>();
            staticRegistryManager.registries().forEach(entry -> {
                try {
                    Registry<?> registry = entry.value();
                    if (registry instanceof MappedRegistry<?> simpleRegistry) {
                        simpleRegistry.bindAllTagsToEmpty();
                    }
                    registry.freeze();
                    pendingTagLoads.addAll(TagLoader.loadTagsForExistingRegistries(resourceManager, new RegistryAccess.ImmutableRegistryAccess(List.of(registry))));
                } catch (RuntimeException e) {
                    SeedScoutClient.LOGGER.warn("Test registry bootstrap could not bind tags for {}", entry.key(), e);
                }
            });
            pendingTagLoads.forEach(Registry.PendingTags::apply);
        }
    }
}
