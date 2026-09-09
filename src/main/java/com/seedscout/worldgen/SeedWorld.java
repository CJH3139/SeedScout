package com.seedscout.worldgen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.SinglePieceStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import net.minecraft.world.level.levelgen.structure.structures.IglooStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinStructure;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class SeedWorld {
    private static final int SAMPLE_QUART_Y = 64 >> 2;
    private static final Identifier END_CITY = Identifier.withDefaultNamespace("end_city");
    private static final Identifier DESERT_PYRAMID = Identifier.withDefaultNamespace("desert_pyramid");
    private static final long SLIME_SALT = 987234911L;

    public static final int MAX_SEARCH_REGIONS = 20000;
    public static final int SURFACE_Y = 64;
    public static final String VANILLA_PROFILE = "vanilla";

    private static final Field JIGSAW_START_HEIGHT = field(JigsawStructure.class, "startHeight");
    private static final Field JIGSAW_PROJECTION = field(JigsawStructure.class, "projectStartToHeightmap");

    private final long seed;
    private final Dimension dimension;
    private final String profile;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final RandomState randomState;
    private final NoiseBasedChunkGenerator chunkGenerator;
    private final LevelHeightAccessor heightAccessor;
    private final ChunkGeneratorStructureState calculator;
    private final HolderLookup.RegistryLookup<Structure> structureRegistry;
    private final HolderLookup.RegistryLookup<Biome> biomeRegistry;
    private final List<Holder<StructureSet>> structureSets;
    private final List<Holder<Structure>> structures;
    private final List<Holder<Biome>> biomes;
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> terrainCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static SeedWorld create(long seed) {
        return create(seed, Dimension.OVERWORLD);
    }

    public static SeedWorld create(long seed, Dimension dimension) {
        return new SeedWorld(seed, dimension, VanillaWrapperLookupHolder.INSTANCE);
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

    private static Field field(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static SeedWorld fromServer(MinecraftServer server, Dimension dimension) {
        ServerLevel level = server.getLevel(dimension.levelKey());
        if (level == null) {
            throw new IllegalStateException("Server has no level " + dimension.levelId());
        }
        ServerChunkCache chunks = level.getChunkSource();
        if (!(chunks.getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("Level " + dimension.levelId() + " does not use noise generation");
        }
        return new SeedWorld(level.getSeed(), dimension, server.registryAccess(), generator,
                chunks.randomState(), chunks.getGeneratorState(), profileOf(server));
    }

    public static String profileOf(MinecraftServer server) {
        List<String> packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
        packs.remove("vanilla");
        packs.removeIf(id -> id.startsWith("fabric"));
        if (packs.isEmpty()) {
            return VANILLA_PROFILE;
        }
        java.util.Collections.sort(packs);
        return "packs-" + Integer.toHexString(String.join("|", packs).hashCode());
    }

    SeedWorld(long seed, Dimension dimension, HolderLookup.Provider lookup) {
        this(seed, dimension, lookup, null, null, null, VANILLA_PROFILE);
    }

    private SeedWorld(long seed, Dimension dimension, HolderLookup.Provider lookup, NoiseBasedChunkGenerator generator,
                      RandomState randomState, ChunkGeneratorStructureState calculator, String profile) {
        this.seed = seed;
        this.dimension = dimension;
        this.profile = profile;
        Holder<NoiseGeneratorSettings> settingsHolder = generator != null ? generator.generatorSettings()
                : lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(dimension.noiseSettings());
        NoiseGeneratorSettings settings = settingsHolder.value();
        this.randomState = randomState != null ? randomState
                : RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), seed);
        this.sampler = this.randomState.sampler();
        this.biomeSource = generator != null ? generator.getBiomeSource() : dimension.createBiomeSource(lookup);
        this.chunkGenerator = generator != null ? generator : new NoiseBasedChunkGenerator(biomeSource, settingsHolder);
        this.heightAccessor = LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());

        HolderLookup.RegistryLookup<StructureSet> setRegistry = lookup.lookupOrThrow(Registries.STRUCTURE_SET);
        this.calculator = calculator != null ? calculator
                : ChunkGeneratorStructureState.createForNormal(this.randomState, seed, biomeSource, setRegistry);
        this.structureSets = List.copyOf(this.calculator.possibleStructureSets());
        this.structureRegistry = lookup.lookupOrThrow(Registries.STRUCTURE);
        this.biomeRegistry = lookup.lookupOrThrow(Registries.BIOME);
        Set<Holder<Structure>> present = new LinkedHashSet<>();
        for (Holder<StructureSet> set : structureSets) {
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                present.add(entry.structure());
            }
        }
        this.structures = present.stream()
                .sorted(Comparator.comparing(e -> idOf(e).getPath()))
                .toList();
        this.biomes = biomeSource.possibleBiomes().stream()
                .sorted(Comparator.comparing(e -> idOf(e).getPath()))
                .toList();
    }

    public long seed() {
        return seed;
    }

    public Dimension dimension() {
        return dimension;
    }

    public String profile() {
        return profile;
    }

    public boolean isVanilla() {
        return VANILLA_PROFILE.equals(profile);
    }

    public Holder<Biome> biomeAt(int blockX, int blockZ) {
        return biomeSource.getNoiseBiome(blockX >> 2, SAMPLE_QUART_Y, blockZ >> 2, sampler);
    }

    public Holder<Biome> biomeAt(int blockX, int blockY, int blockZ) {
        return biomeSource.getNoiseBiome(blockX >> 2, blockY >> 2, blockZ >> 2, sampler);
    }

    public List<Holder<Structure>> allStructures() {
        return structures;
    }

    public List<Holder<StructureSet>> allStructureSets() {
        return structureSets;
    }

    public List<Holder<Biome>> allBiomes() {
        return biomes;
    }

    public Holder<Structure> structure(Identifier id) {
        return structureRegistry.getOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
    }

    public Holder<Biome> biome(Identifier id) {
        return biomeRegistry.getOrThrow(ResourceKey.create(Registries.BIOME, id));
    }

    public boolean isSlimeChunk(int chunkX, int chunkZ) {
        return dimension == Dimension.OVERWORLD
                && WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, SLIME_SALT).nextInt(10) == 0;
    }

    public List<Holder<StructureSet>> structureSetsFor(Holder<Structure> structure) {
        List<Holder<StructureSet>> result = new ArrayList<>();
        for (Holder<StructureSet> set : structureSets) {
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

    public Optional<RegionHit> structureInRegionConfirmed(Holder<StructureSet> set, int regionX, int regionZ) {
        return structureInRegion(set, regionX, regionZ).filter(this::confirm);
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
        List<StructureSet.StructureSelectionEntry> entries = set.value().structures();
        Holder<Structure> chosen = null;
        if (entries.size() == 1) {
            if (canGenerate(entries.get(0).structure(), chunk)) {
                chosen = entries.get(0).structure();
            }
        } else {
            List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(entries);
            WorldgenRandom random = makeRandom(chunk);
            int total = 0;
            for (StructureSet.StructureSelectionEntry option : options) {
                total += option.weight();
            }
            while (!options.isEmpty()) {
                int choice = random.nextInt(total);
                int index = 0;
                for (StructureSet.StructureSelectionEntry option : options) {
                    choice -= option.weight();
                    if (choice < 0) break;
                    index++;
                }
                StructureSet.StructureSelectionEntry picked = options.get(index);
                if (canGenerate(picked.structure(), chunk)) {
                    chosen = picked.structure();
                    break;
                }
                options.remove(index);
                total -= picked.weight();
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        BlockPos locate = placement.getLocatePos(chunk);
        return Optional.of(new RegionHit(chosen, chunk, locate.getX(), locate.getZ()));
    }

    private WorldgenRandom makeRandom(ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(calculator.getLevelSeed(), chunk.x(), chunk.z());
        return random;
    }

    private boolean canGenerate(Holder<Structure> holder, ChunkPos chunk) {
        BlockPos pos = cheapGenerationPos(holder, chunk);
        Holder<Biome> biome = biomeSource.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2, sampler);
        return holder.value().biomes().contains(biome);
    }

    private BlockPos cheapGenerationPos(Holder<Structure> holder, ChunkPos chunk) {
        Structure structure = holder.value();
        Identifier id = idOf(holder);
        int midX = chunk.getMiddleBlockX();
        int midZ = chunk.getMiddleBlockZ();
        if (structure instanceof JigsawStructure jigsaw) {
            return jigsawStart(jigsaw, chunk, midX, midZ);
        }
        if (structure instanceof WoodlandMansionStructure || id.equals(END_CITY)) {
            return new BlockPos(chunk.getBlockX(7), 64, chunk.getBlockZ(7));
        }
        if (structure instanceof StrongholdStructure) {
            return chunk.getWorldPosition();
        }
        if (structure instanceof MineshaftStructure) {
            return new BlockPos(midX, 50, chunk.getMinBlockZ());
        }
        return new BlockPos(midX, 64, midZ);
    }

    private boolean needsTerrainCheck(Holder<Structure> holder) {
        Structure structure = holder.value();
        return structure instanceof WoodlandMansionStructure || idOf(holder).equals(END_CITY)
                || structure instanceof SinglePieceStructure || structure instanceof OceanMonumentStructure;
    }

    public boolean confirm(RegionHit hit) {
        Holder<Structure> holder = hit.structure();
        if (!needsTerrainCheck(holder)) {
            return true;
        }
        ChunkPos chunk = hit.chunk();
        long key = chunk.pack() * 31L + idOf(holder).hashCode();
        Boolean cached = terrainCache.get(key);
        if (cached != null) {
            return cached;
        }
        Structure structure = holder.value();
        boolean ok;
        if (structure instanceof OceanMonumentStructure) {
            ok = true;
            int x = chunk.getBlockX(9);
            int z = chunk.getBlockZ(9);
            for (Holder<Biome> biome : biomeSource.getBiomesWithin(x, chunkGenerator.getSeaLevel(), z, 29, sampler)) {
                if (!biome.is(BiomeTags.REQUIRED_OCEAN_MONUMENT_SURROUNDING)) {
                    ok = false;
                    break;
                }
            }
        } else if (structure instanceof SinglePieceStructure) {
            ok = surfaceHeight(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()) >= chunkGenerator.getSeaLevel();
        } else {
            ok = lowestIn5by5Offset7(chunk) != null;
        }
        terrainCache.put(key, ok);
        return ok;
    }

    private BlockPos jigsawStart(JigsawStructure jigsaw, ChunkPos chunk, int midX, int midZ) {
        if (JIGSAW_START_HEIGHT == null || JIGSAW_PROJECTION == null) {
            return new BlockPos(midX, 64, midZ);
        }
        try {
            HeightProvider startHeight = (HeightProvider) JIGSAW_START_HEIGHT.get(jigsaw);
            @SuppressWarnings("unchecked")
            Optional<Heightmap.Types> projection = (Optional<Heightmap.Types>) JIGSAW_PROJECTION.get(jigsaw);
            int y = startHeight.sample(makeRandom(chunk), new WorldGenerationContext(chunkGenerator, heightAccessor));
            if (projection.isPresent()) {
                y += SAMPLE_QUART_Y << 2;
            }
            return new BlockPos(midX, y, midZ);
        } catch (IllegalAccessException e) {
            return new BlockPos(midX, 64, midZ);
        }
    }

    private BlockPos lowestIn5by5Offset7(ChunkPos chunk) {
        Rotation rotation = Rotation.getRandom(makeRandom(chunk));
        int offsetX = 5;
        int offsetZ = 5;
        if (rotation == Rotation.CLOCKWISE_90) {
            offsetX = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            offsetX = -5;
            offsetZ = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            offsetZ = -5;
        }
        int blockX = chunk.getBlockX(7);
        int blockZ = chunk.getBlockZ(7);
        int lowest = Math.min(
                Math.min(surfaceHeight(blockX, blockZ), surfaceHeight(blockX, blockZ + offsetZ)),
                Math.min(surfaceHeight(blockX + offsetX, blockZ), surfaceHeight(blockX + offsetX, blockZ + offsetZ)));
        if (lowest < 60) {
            return null;
        }
        return new BlockPos(blockX, lowest, blockZ);
    }


    public int surfaceHeight(int x, int z) {
        return chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
    }

    public List<StructureHit> findStructures(Holder<Structure> structure, ChunkPos center, int radiusChunks, int maxResults) {
        int centerBlockX = center.getMiddleBlockX();
        int centerBlockZ = center.getMiddleBlockZ();
        List<StructureHit> hits = new ArrayList<>();
        for (Holder<StructureSet> set : structureSetsFor(structure)) {
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
        List<StructureHit> confirmed = new ArrayList<>();
        for (StructureHit hit : hits.stream()
                .filter(h -> h.distance() <= radiusBlocks)
                .sorted(Comparator.comparingDouble(StructureHit::distance))
                .toList()) {
            if (confirm(new RegionHit(hit.structure(), hit.chunk(), hit.blockX(), hit.blockZ()))) {
                confirmed.add(hit);
                if (confirmed.size() >= maxResults) break;
            }
        }
        return confirmed;
    }

    public List<BiomeHit> findBiome(Holder<Biome> target, int centerX, int centerZ, int radiusBlocks, int maxResults) {
        return findBiome(target, centerX, centerZ, radiusBlocks, maxResults, SURFACE_Y);
    }

    public List<BiomeHit> findBiome(Holder<Biome> target, int centerX, int centerZ, int radiusBlocks, int maxResults, int y) {
        int step = Math.max(16, radiusBlocks / 160);
        int clusterDistance = Math.max(256, step * 8);
        Identifier targetId = idOf(target);
        List<BiomeHit> hits = new ArrayList<>();
        for (int r = 0; r <= radiusBlocks; r += step) {
            if (r == 0) {
                sampleBiome(targetId, centerX, y, centerZ, centerX, centerZ, clusterDistance, hits);
            } else {
                for (int d = -r; d <= r; d += step) {
                    sampleBiome(targetId, centerX + d, y, centerZ - r, centerX, centerZ, clusterDistance, hits);
                    sampleBiome(targetId, centerX + d, y, centerZ + r, centerX, centerZ, clusterDistance, hits);
                }
                for (int d = -r + step; d <= r - step; d += step) {
                    sampleBiome(targetId, centerX - r, y, centerZ + d, centerX, centerZ, clusterDistance, hits);
                    sampleBiome(targetId, centerX + r, y, centerZ + d, centerX, centerZ, clusterDistance, hits);
                }
            }
            if (hits.size() >= maxResults * 2) break;
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(BiomeHit::distance))
                .limit(maxResults)
                .toList();
    }

    private void sampleBiome(Identifier targetId, int x, int y, int z, int centerX, int centerZ, int clusterDistance, List<BiomeHit> hits) {
        Holder<Biome> found = biomeAt(x, y, z);
        if (!idOf(found).equals(targetId)) return;
        for (BiomeHit existing : hits) {
            if (Math.abs(existing.blockX() - x) <= clusterDistance && Math.abs(existing.blockZ() - z) <= clusterDistance) {
                return;
            }
        }
        double dx = x - centerX;
        double dz = z - centerZ;
        hits.add(new BiomeHit(found, x, z, Math.sqrt(dx * dx + dz * dz)));
    }

    public static Identifier idOf(Holder<?> entry) {
        return entry.unwrapKey().map(ResourceKey::identifier).orElseThrow();
    }
}
