package com.seedscout.worldgen;

import com.seedscout.SeedScoutClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaDataPackProvider;

public final class TestRegistryBootstrap {
    private TestRegistryBootstrap() {}

    public static void bindVanillaTags() {
        ResourcePack vanillaPack = VanillaDataPackProvider.createDefaultPack();
        try (LifecycledResourceManagerImpl resourceManager = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, List.of(vanillaPack))) {
            DynamicRegistryManager.Immutable staticRegistryManager = DynamicRegistryManager.of(Registries.REGISTRIES);
            List<Registry.PendingTagLoad<?>> pendingTagLoads = new ArrayList<>();
            staticRegistryManager.streamAllRegistries().forEach(entry -> {
                try {
                    Registry<?> registry = entry.value();
                    if (registry instanceof SimpleRegistry<?> simpleRegistry) {
                        simpleRegistry.resetTagEntries();
                    }
                    registry.freeze();
                    pendingTagLoads.addAll(TagGroupLoader.startReload(resourceManager, new DynamicRegistryManager.ImmutableImpl(List.of(registry))));
                } catch (RuntimeException e) {
                    SeedScoutClient.LOGGER.warn("Test registry bootstrap could not bind tags for {}", entry.key(), e);
                }
            });
            pendingTagLoads.forEach(Registry.PendingTagLoad::apply);
        }
    }
}
