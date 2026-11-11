package com.seedscout.compat;

import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/** Minecraft 26.3 implementations of the calls that differ between supported versions. */
public final class Compat {
    public static final RenderPipeline SEE_THROUGH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("seedscout", "pipeline/beam"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private Compat() {}

    public static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    public static boolean hudHidden(Minecraft client) {
        return client.gui.hud.isHidden();
    }

    public static List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
        return RegistryDataLoader.WORLD_REGISTRIES;
    }

    public static List<PackResources> openPack(Pack pack) {
        return pack.open().toList();
    }

    public static PackResources vanillaPack() {
        return ServerPacksSource.createVanillaPackSource().fullResources();
    }

    public static RandomState randomState(NoiseGeneratorSettings settings, HolderLookup.Provider lookup, long seed) {
        return RandomState.create(lookup.lookupOrThrow(Registries.NOISE), seed, settings);
    }

    public static BiomeLookup biomes(BiomeSource source, RandomState randomState) {
        BiomeResolver resolver = source.createUncachedResolver(randomState);
        return new BiomeLookup() {
            @Override
            public Holder<Biome> noiseBiome(int quartX, int quartY, int quartZ) {
                return resolver.getNoiseBiome(quartX, quartY, quartZ);
            }

            @Override
            public Set<Holder<Biome>> biomesWithin(int blockX, int blockY, int blockZ, int radius) {
                return resolver.getBiomesWithin(blockX, blockY, blockZ, radius);
            }
        };
    }

    public static void positions(PlacementModifier modifier, PlacementContext context, RandomSource random,
                                 BlockPos pos, Consumer<BlockPos> out) {
        modifier.modify(context, random, pos, out);
    }

    public static RenderType seeThroughLayer(String name) {
        return RenderType.create(name, RenderSetup.builder(SEE_THROUGH).sortOnUpload().createRenderSetup());
    }
}
