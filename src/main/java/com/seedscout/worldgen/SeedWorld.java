package com.seedscout.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryValidator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SeedWorld {
    private static final int SAMPLE_QUART_Y = 64 >> 2;

    public static final int MAX_SEARCH_REGIONS = 20000;

    private final long seed;
    private final MultiNoiseBiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final ChunkGeneratorStructureState calculator;
    private final HolderLookup.RegistryLookup<Structure> structureRegistry;
    private final List<Holder.Reference<StructureSet>> structureSets;
    private final List<Holder.Reference<Structure>> structures;

    public static SeedWorld create(long seed) {
        return new SeedWorld(seed, VanillaWrapperLookupHolder.INSTANCE);
    }

    private static final class VanillaWrapperLookupHolder {
        static final HolderLookup.Provider INSTANCE = createVanillaWrapperLookup();
    }

    private static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_ENTRIES = List.of(
            new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunctions.DIRECT_CODEC, RegistryValidator.none()),
            new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC, RegistryValidator.none()));

    private static HolderLookup.Provider createVanillaWrapperLookup() {
        PackResources vanillaPack = ServerPacksSource.createVanillaPackSource();
        try (MultiPackResourceManager resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanillaPack))) {
            List<HolderLookup.RegistryLookup<?>> staticRegistries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).listRegistries().toList();
            return RegistryDataLoader.load(resourceManager, staticRegistries, WORLDGEN_ENTRIES, Runnable::run).join();
        }
    }

    SeedWorld(long seed, HolderLookup.Provider lookup) {
        this.seed = seed;
        NoiseGeneratorSettings settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        RandomState noiseConfig = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        this.sampler = noiseConfig.sampler();

        Holder<MultiNoiseBiomeSourceParameterList> params = lookup
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        this.biomeSource = MultiNoiseBiomeSource.createFromPreset(params);

        HolderLookup.RegistryLookup<StructureSet> setRegistry = lookup.lookupOrThrow(Registries.STRUCTURE_SET);
        this.calculator = ChunkGeneratorStructureState.createForNormal(noiseConfig, seed, biomeSource, setRegistry);
        this.structureSets = setRegistry.listElements().toList();
        this.structureRegistry = lookup.lookupOrThrow(Registries.STRUCTURE);
        this.structures = structureRegistry.listElements()
                .sorted(Comparator.comparing(e -> idOf(e).getPath()))
                .toList();
    }

    public long seed() {
        return seed;
    }

    public Holder<Biome> biomeAt(int blockX, int blockZ) {
        return biomeSource.getNoiseBiome(blockX >> 2, SAMPLE_QUART_Y, blockZ >> 2, sampler);
    }

    public List<Holder.Reference<Structure>> allStructures() {
        return structures;
    }

    public List<Holder.Reference<StructureSet>> allStructureSets() {
        return structureSets;
    }

    public Holder<Structure> structure(Identifier id) {
        return structureRegistry.getOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
    }

    public List<Holder.Reference<StructureSet>> structureSetsFor(Holder<Structure> structure) {
        List<Holder.Reference<StructureSet>> result = new ArrayList<>();
        for (Holder.Reference<StructureSet> set : structureSets) {
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                if (entry.structure().equals(structure)) {
                    result.add(set);
                    break;
                }
            }
        }
        return result;
    }

    public Optional<RegionHit> structureInRegion(Holder<StructureSet> set, int regionX, int regionZ) {
        if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
            return Optional.empty();
        }
        int spacing = spread.spacing();
        ChunkPos start = spread.getPotentialStructureChunk(calculator.getLevelSeed(), regionX * spacing, regionZ * spacing);
        return hitAt(set, spread, start);
    }

    public List<RegionHit> concentricRingHits(Holder<StructureSet> set) {
        if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement rings)) {
            return List.of();
        }

        List<ChunkPos> positions = calculator.getRingPositionsFor(rings);
        if (positions == null) {
            return List.of();
        }
        List<RegionHit> hits = new ArrayList<>();
        for (ChunkPos pos : positions) {
            hitAt(set, rings, pos).ifPresent(hits::add);
        }
        return hits;
    }

    private Optional<RegionHit> hitAt(Holder<StructureSet> set, StructurePlacement placement, ChunkPos chunk) {
        if (!placement.isStructureChunk(calculator, chunk.x(), chunk.z())) {
            return Optional.empty();
        }
        Holder<Biome> biome = biomeAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
            if (entry.structure().value().biomes().contains(biome)) {
                BlockPos locate = placement.getLocatePos(chunk);
                return Optional.of(new RegionHit(entry.structure(), chunk, locate.getX(), locate.getZ()));
            }
        }
        return Optional.empty();
    }

    public List<StructureHit> findStructures(Holder<Structure> structure, ChunkPos center, int radiusChunks, int maxResults) {
        int centerBlockX = center.getMiddleBlockX();
        int centerBlockZ = center.getMiddleBlockZ();
        List<StructureHit> hits = new ArrayList<>();
        for (Holder.Reference<StructureSet> set : structureSetsFor(structure)) {
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.spacing();
                int regionRadius = Math.floorDiv(radiusChunks, spacing) + 1;
                long regionCount = (2L * regionRadius + 1) * (2L * regionRadius + 1);
                if (regionCount > MAX_SEARCH_REGIONS) {
                    regionRadius = (int) ((Math.sqrt(MAX_SEARCH_REGIONS) - 1) / 2);
                }
                int cx = Math.floorDiv(center.x(), spacing);
                int cz = Math.floorDiv(center.z(), spacing);
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

    public static Identifier idOf(Holder<?> entry) {
        return entry.unwrapKey().map(ResourceKey::identifier).orElseThrow();
    }
}
