package com.seedscout.compat;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Minecraft 26.2 implementations of the calls that differ between supported versions. */
public final class Compat {
    public static final RenderPipeline SEE_THROUGH = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("seedscout", "pipeline/beam"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    private static final StructureManager NO_STRUCTURES = new StructureManager(null, null, null) {
        @Override
        public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> matcher) {
            return List.of();
        }
    };

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

    public static boolean placeMonsterRoom(WorldGenLevel level, RandomSource random, BlockPos origin) {
        return new MonsterRoomFeature(NoneFeatureConfiguration.CODEC)
                .place(new FeaturePlaceContext<>(Optional.empty(), level, null, random, origin, NoneFeatureConfiguration.INSTANCE));
    }

    public static ProtoChunk buildTerrain(NoiseBasedChunkGenerator generator, RandomState randomState, HolderLookup.Provider lookup,
                                          LevelHeightAccessor height, long seed, ChunkPos pos) {
        RegistryAccess registries = registryAccess(lookup);
        TerrainChunk chunk = new TerrainChunk(pos, height, PalettedContainerFactory.create(registries));
        generator.createBiomes(randomState, Blender.empty(), NO_STRUCTURES, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        generator.fillFromNoise(Blender.empty(), randomState, NO_STRUCTURES, chunk).join();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
        BiomeManager biomes = new BiomeManager(regionBiomes(chunk, generator.getBiomeSource(), randomState.sampler()),
                BiomeManager.obfuscateSeed(seed));
        generator.buildSurface(chunk, new WorldGenerationContext(generator, height), randomState, NO_STRUCTURES, biomes, Blender.empty(), null);
        chunk.setPersistedStatus(ChunkStatus.SURFACE);
        applyCarvers(generator, registries, seed, randomState, biomes, chunk);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        chunk.releaseNoise();
        return chunk;
    }

    /** A proto chunk that can drop its noise sampler once terrain is done; cached chunks would otherwise keep it. */
    private static final class TerrainChunk extends ProtoChunk {
        TerrainChunk(ChunkPos pos, LevelHeightAccessor height, PalettedContainerFactory containers) {
            super(pos, UpgradeData.EMPTY, height, containers, null);
        }

        void releaseNoise() {
            noiseChunk = null;
        }
    }

    // NoiseBasedChunkGenerator.applyCarvers, reading carver biomes from the biome source instead of a WorldGenRegion.
    private static void applyCarvers(NoiseBasedChunkGenerator generator, RegistryAccess registries, long seed, RandomState randomState,
                                     BiomeManager biomeManager, ProtoChunk chunk) {
        BiomeSource source = generator.getBiomeSource();
        Climate.Sampler sampler = randomState.sampler();
        BiomeManager correctBiomeManager = biomeManager.withDifferentSource(
                (quartX, quartY, quartZ) -> source.getNoiseBiome(quartX, quartY, quartZ, sampler));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos pos = chunk.getPos();
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(c -> {
            throw new IllegalStateException("No noise chunk for " + c.getPos());
        });
        Aquifer aquifer = noiseChunk.aquifer();
        CarvingContext context = new CarvingContext(generator, registries, chunk.getHeightAccessorForGeneration(), noiseChunk,
                randomState, generator.generatorSettings().value().surfaceRule());
        CarvingMask mask = chunk.getOrCreateCarvingMask();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                ChunkPos sourcePos = new ChunkPos(pos.x() + dx, pos.z() + dz);
                BiomeGenerationSettings settings = generator.getBiomeGenerationSettings(source.getNoiseBiome(
                        QuartPos.fromBlock(sourcePos.getMinBlockX()), 0, QuartPos.fromBlock(sourcePos.getMinBlockZ()), sampler));
                int index = 0;
                for (Holder<ConfiguredWorldCarver<?>> carverHolder : settings.getCarvers()) {
                    ConfiguredWorldCarver<?> carver = carverHolder.value();
                    random.setLargeFeatureSeed(seed + index, sourcePos.x(), sourcePos.z());
                    if (carver.isStartChunk(random)) {
                        carver.carve(context, chunk, correctBiomeManager::getBiome, random, aquifer, sourcePos, mask);
                    }
                    index++;
                }
            }
        }
    }

    // What a WorldGenRegion around this chunk reads: the chunk's own biomes inside it, its neighbors' (unblended) biomes outside.
    private static BiomeManager.NoiseBiomeSource regionBiomes(ProtoChunk chunk, BiomeSource source, Climate.Sampler sampler) {
        ChunkPos pos = chunk.getPos();
        int minQuartY = QuartPos.fromBlock(chunk.getMinY());
        int maxQuartY = minQuartY + QuartPos.fromBlock(chunk.getHeight()) - 1;
        return (quartX, quartY, quartZ) -> QuartPos.toSection(quartX) == pos.x() && QuartPos.toSection(quartZ) == pos.z()
                ? chunk.getNoiseBiome(quartX, quartY, quartZ)
                : source.getNoiseBiome(quartX, Mth.clamp(quartY, minQuartY, maxQuartY), quartZ, sampler);
    }

    private static RegistryAccess registryAccess(HolderLookup.Provider lookup) {
        if (lookup instanceof RegistryAccess access) return access;
        throw new IllegalArgumentException("Terrain needs a RegistryAccess, got " + lookup.getClass().getName());
    }

    /** Fuzzed block biomes straight from the biome source, as a level without chunks would give them. */
    public static BiomeManager biomeManager(BiomeSource source, RandomState randomState, long seed) {
        return new BiomeManager((quartX, quartY, quartZ) -> source.getNoiseBiome(quartX, quartY, quartZ, randomState.sampler()), BiomeManager.obfuscateSeed(seed));
    }

    /** A structure start as {@code ChunkGenerator.tryGenerateStructure} makes it, with no template manager. */
    public static StructureStart generateStructure(Holder<Structure> structure, ResourceKey<Level> dimension, RegistryAccess registries,
                                                   ChunkGenerator generator, BiomeSource biomeSource, RandomState randomState,
                                                   long seed, ChunkPos pos, LevelHeightAccessor height) {
        return structure.value().generate(structure, dimension, registries, generator, biomeSource, randomState, null, seed, pos, 0, height,
                structure.value().biomes()::contains);
    }
}
