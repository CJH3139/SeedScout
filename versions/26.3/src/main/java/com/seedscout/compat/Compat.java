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
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyStructureManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

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

    public static boolean placeMonsterRoom(WorldGenLevel level, RandomSource random, BlockPos origin) {
        return new MonsterRoomFeature().place(level, null, random, origin);
    }

    public static ProtoChunk buildTerrain(NoiseBasedChunkGenerator generator, RandomState randomState, HolderLookup.Provider lookup,
                                          LevelHeightAccessor height, long seed, ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, PalettedContainerFactory.create(registryAccess(lookup)), null);
        generator.createBiomes(randomState, Blender.empty(), EmptyStructureManager.INSTANCE, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        BiomeResolver resolver = generator.getBiomeSource().createUncachedResolver(randomState);
        BiomeManager biomes = new BiomeManager(regionBiomes(chunk, resolver), BiomeManager.obfuscateSeed(seed));
        // A null region makes carvers read their biomes from the resolver; null possible biomes skips an optimization only.
        generator.buildTerrain(chunk, Blender.empty(), randomState, EmptyStructureManager.INSTANCE, biomes, null, null).join();
        chunk.setPersistedStatus(ChunkStatus.TERRAIN);
        return chunk;
    }

    // What a WorldGenRegion around this chunk reads: the chunk's own biomes inside it, its neighbors' (unblended) biomes outside.
    private static BiomeResolver regionBiomes(ProtoChunk chunk, BiomeResolver resolver) {
        ChunkPos pos = chunk.getPos();
        int minQuartY = QuartPos.fromBlock(chunk.getMinY());
        int maxQuartY = minQuartY + QuartPos.fromBlock(chunk.getHeight()) - 1;
        return (quartX, quartY, quartZ) -> QuartPos.toSection(quartX) == pos.x() && QuartPos.toSection(quartZ) == pos.z()
                ? chunk.getNoiseBiome(quartX, quartY, quartZ)
                : resolver.getNoiseBiome(quartX, Mth.clamp(quartY, minQuartY, maxQuartY), quartZ);
    }

    private static RegistryAccess registryAccess(HolderLookup.Provider lookup) {
        if (lookup instanceof RegistryAccess access) return access;
        throw new IllegalArgumentException("Terrain needs a RegistryAccess, got " + lookup.getClass().getName());
    }

    /** Fuzzed block biomes straight from the biome source, as a level without chunks would give them. */
    public static BiomeManager biomeManager(BiomeSource source, RandomState randomState, long seed) {
        return new BiomeManager(source.createUncachedResolver(randomState), BiomeManager.obfuscateSeed(seed));
    }

    /** A structure start as {@code ChunkGenerator.tryGenerateStructure} makes it, with no template manager. */
    public static StructureStart generateStructure(Holder<Structure> structure, ResourceKey<Level> dimension, RegistryAccess registries,
                                                   ChunkGenerator generator, BiomeSource biomeSource, RandomState randomState,
                                                   long seed, ChunkPos pos, LevelHeightAccessor height) {
        return structure.value().generate(structure, dimension, registries, generator, biomeSource,
                randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()), randomState, null, seed, pos, 0, height,
                structure.value().biomes()::contains);
    }
}
