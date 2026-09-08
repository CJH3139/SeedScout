package com.seedscout.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.VanillaDataPackProvider;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.processor.StructureProcessorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

public final class SeedWorld {
    private static final int SAMPLE_QUART_Y = 64 >> 2;

    public static final int MAX_SEARCH_REGIONS = 20000;

    private final long seed;
    private final MultiNoiseBiomeSource biomeSource;
    private final MultiNoiseUtil.MultiNoiseSampler sampler;
    private final StructurePlacementCalculator calculator;
    private final RegistryWrapper.Impl<Structure> structureRegistry;
    private final List<RegistryEntry.Reference<StructureSet>> structureSets;
    private final List<RegistryEntry.Reference<Structure>> structures;

    public static SeedWorld create(long seed) {
        return new SeedWorld(seed, VanillaWrapperLookupHolder.INSTANCE);
    }

    private static final class VanillaWrapperLookupHolder {
        static final RegistryWrapper.WrapperLookup INSTANCE = createVanillaWrapperLookup();
    }

    private static final List<RegistryLoader.Entry<?>> WORLDGEN_ENTRIES = List.of(
            new RegistryLoader.Entry<>(RegistryKeys.CONFIGURED_CARVER, ConfiguredCarver.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.CONFIGURED_FEATURE, ConfiguredFeature.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.PLACED_FEATURE, PlacedFeature.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.STRUCTURE, Structure.STRUCTURE_CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.STRUCTURE_SET, StructureSet.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.PROCESSOR_LIST, StructureProcessorType.PROCESSORS_CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.TEMPLATE_POOL, StructurePool.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.BIOME, Biome.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.NOISE_PARAMETERS, DoublePerlinNoiseSampler.NoiseParameters.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.DENSITY_FUNCTION, DensityFunction.CODEC, false),
            new RegistryLoader.Entry<>(RegistryKeys.CHUNK_GENERATOR_SETTINGS, ChunkGeneratorSettings.CODEC, false));

    private static RegistryWrapper.WrapperLookup createVanillaWrapperLookup() {
        ResourcePack vanillaPack = VanillaDataPackProvider.createDefaultPack();
        try (LifecycledResourceManagerImpl resourceManager = new LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, List.of(vanillaPack))) {
            List<RegistryWrapper.Impl<?>> staticRegistries = DynamicRegistryManager.of(Registries.REGISTRIES).stream().toList();
            return RegistryLoader.loadFromResource(resourceManager, staticRegistries, WORLDGEN_ENTRIES);
        }
    }

    SeedWorld(long seed, RegistryWrapper.WrapperLookup lookup) {
        this.seed = seed;
        ChunkGeneratorSettings settings = lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                .getOrThrow(ChunkGeneratorSettings.OVERWORLD).value();
        NoiseConfig noiseConfig = NoiseConfig.create(settings, lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS), seed);
        this.sampler = noiseConfig.getMultiNoiseSampler();

        RegistryEntry<MultiNoiseBiomeSourceParameterList> params = lookup
                .getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        this.biomeSource = MultiNoiseBiomeSource.create(params);

        RegistryWrapper.Impl<StructureSet> setRegistry = lookup.getOrThrow(RegistryKeys.STRUCTURE_SET);
        this.calculator = StructurePlacementCalculator.create(noiseConfig, seed, biomeSource, setRegistry);
        this.structureSets = setRegistry.streamEntries().toList();
        this.structureRegistry = lookup.getOrThrow(RegistryKeys.STRUCTURE);
        this.structures = structureRegistry.streamEntries()
                .sorted(Comparator.comparing(e -> idOf(e).getPath()))
                .toList();
    }

    public long seed() {
        return seed;
    }

    public RegistryEntry<Biome> biomeAt(int blockX, int blockZ) {
        return biomeSource.getBiome(blockX >> 2, SAMPLE_QUART_Y, blockZ >> 2, sampler);
    }

    public List<RegistryEntry.Reference<Structure>> allStructures() {
        return structures;
    }

    public List<RegistryEntry.Reference<StructureSet>> allStructureSets() {
        return structureSets;
    }

    public RegistryEntry<Structure> structure(Identifier id) {
        return structureRegistry.getOrThrow(RegistryKey.of(RegistryKeys.STRUCTURE, id));
    }

    public List<RegistryEntry.Reference<StructureSet>> structureSetsFor(RegistryEntry<Structure> structure) {
        List<RegistryEntry.Reference<StructureSet>> result = new ArrayList<>();
        for (RegistryEntry.Reference<StructureSet> set : structureSets) {
            for (StructureSet.WeightedEntry entry : set.value().structures()) {
                if (entry.structure().equals(structure)) {
                    result.add(set);
                    break;
                }
            }
        }
        return result;
    }

    public Optional<RegionHit> structureInRegion(RegistryEntry<StructureSet> set, int regionX, int regionZ) {
        if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
            return Optional.empty();
        }
        int spacing = spread.getSpacing();
        ChunkPos start = spread.getStartChunk(calculator.getStructureSeed(), regionX * spacing, regionZ * spacing);
        return hitAt(set, spread, start);
    }

    public List<RegionHit> concentricRingHits(RegistryEntry<StructureSet> set) {
        if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement rings)) {
            return List.of();
        }

        List<ChunkPos> positions = calculator.getPlacementPositions(rings);
        if (positions == null) {
            return List.of();
        }
        List<RegionHit> hits = new ArrayList<>();
        for (ChunkPos pos : positions) {
            hitAt(set, rings, pos).ifPresent(hits::add);
        }
        return hits;
    }

    private Optional<RegionHit> hitAt(RegistryEntry<StructureSet> set, StructurePlacement placement, ChunkPos chunk) {
        if (!placement.shouldGenerate(calculator, chunk.x, chunk.z)) {
            return Optional.empty();
        }
        RegistryEntry<Biome> biome = biomeAt(chunk.getCenterX(), chunk.getCenterZ());
        for (StructureSet.WeightedEntry entry : set.value().structures()) {
            if (entry.structure().value().getValidBiomes().contains(biome)) {
                BlockPos locate = placement.getLocatePos(chunk);
                return Optional.of(new RegionHit(entry.structure(), chunk, locate.getX(), locate.getZ()));
            }
        }
        return Optional.empty();
    }

    public List<StructureHit> findStructures(RegistryEntry<Structure> structure, ChunkPos center, int radiusChunks, int maxResults) {
        int centerBlockX = center.getCenterX();
        int centerBlockZ = center.getCenterZ();
        List<StructureHit> hits = new ArrayList<>();
        for (RegistryEntry.Reference<StructureSet> set : structureSetsFor(structure)) {
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.getSpacing();
                int regionRadius = Math.floorDiv(radiusChunks, spacing) + 1;
                long regionCount = (2L * regionRadius + 1) * (2L * regionRadius + 1);
                if (regionCount > MAX_SEARCH_REGIONS) {
                    regionRadius = (int) ((Math.sqrt(MAX_SEARCH_REGIONS) - 1) / 2);
                }
                int cx = Math.floorDiv(center.x, spacing);
                int cz = Math.floorDiv(center.z, spacing);
                for (int rx = cx - regionRadius; rx <= cx + regionRadius; rx++) {
                    for (int rz = cz - regionRadius; rz <= cz + regionRadius; rz++) {
                        Optional<RegionHit> hit = structureInRegion(set, rx, rz);
                        if (hit.isPresent() && hit.get().structure().equals(structure)) {
                            hits.add(StructureHit.of(hit.get(), centerBlockX, centerBlockZ));
                        }
                    }
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement) {
                for (RegionHit hit : concentricRingHits(set)) {
                    if (hit.structure().equals(structure)) {
                        hits.add(StructureHit.of(hit, centerBlockX, centerBlockZ));
                    }
                }
            }
        }
        double radiusBlocks = radiusChunks * 16.0;
        return hits.stream()
                .filter(h -> h.distance() <= radiusBlocks)
                .sorted(Comparator.comparingDouble(StructureHit::distance))
                .limit(maxResults)
                .toList();
    }

    public static Identifier idOf(RegistryEntry<?> entry) {
        return entry.getKey().map(RegistryKey::getValue).orElseThrow();
    }
}
