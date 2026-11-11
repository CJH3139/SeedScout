package com.seedscout.compat;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
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
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/** Minecraft 26.1 implementations of the calls that differ between supported versions. */
public final class Compat {
    public static final RenderPipeline SEE_THROUGH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("seedscout", "pipeline/beam"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private Compat() {}

    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static boolean hudHidden(Minecraft client) {
        return client.options.hideGui;
    }

    public static List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
        return RegistryDataLoader.WORLDGEN_REGISTRIES;
    }

    public static List<PackResources> openPack(Pack pack) {
        return List.of(pack.open());
    }

    public static PackResources vanillaPack() {
        return ServerPacksSource.createVanillaPackSource();
    }

    public static RandomState randomState(NoiseGeneratorSettings settings, HolderLookup.Provider lookup, long seed) {
        return RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
    }

    public static BiomeLookup biomes(BiomeSource source, RandomState randomState) {
        Climate.Sampler sampler = randomState.sampler();
        return new BiomeLookup() {
            @Override
            public Holder<Biome> noiseBiome(int quartX, int quartY, int quartZ) {
                return source.getNoiseBiome(quartX, quartY, quartZ, sampler);
            }

            @Override
            public Set<Holder<Biome>> biomesWithin(int blockX, int blockY, int blockZ, int radius) {
                return source.getBiomesWithin(blockX, blockY, blockZ, radius, sampler);
            }
        };
    }

    public static void positions(PlacementModifier modifier, PlacementContext context, RandomSource random,
                                 BlockPos pos, Consumer<BlockPos> out) {
        modifier.getPositions(context, random, pos).forEach(out);
    }

    public static RenderType seeThroughLayer(String name) {
        return RenderType.create(name, RenderSetup.builder(SEE_THROUGH).sortOnUpload().createRenderSetup());
    }
}
