package com.seedscout.worldgen;

import com.seedscout.compat.BiomeLookup;
import com.seedscout.compat.Compat;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

public final class FeatureFinder {
    public record FeatureHit(Identifier featureId, int blockX, int blockY, int blockZ) {}

    public static final List<Identifier> SUPPORTED = List.of(Identifier.withDefaultNamespace("amethyst_geode"));

    record Entry(PlacedFeature feature, int step, int index) {}

    private final long seed;
    private final ChunkGenerator generator;
    private final BiomeSource biomeSource;
    private final BiomeLookup biomes;
    private final LevelHeightAccessor heightAccessor;
    private final WorldGenLevel level;
    private final List<FeatureSorter.StepFeatureData> steps;
    private final HolderLookup.RegistryLookup<PlacedFeature> registry;
    private final Map<Identifier, Entry> entries = new LinkedHashMap<>();

    FeatureFinder(long seed, ChunkGenerator generator, BiomeSource biomeSource, BiomeLookup biomes,
                  LevelHeightAccessor heightAccessor, HolderLookup.Provider lookup) {
        this.seed = seed;
        this.generator = generator;
        this.biomeSource = biomeSource;
        this.biomes = biomes;
        this.heightAccessor = heightAccessor;
        this.level = createLevel();
        this.steps = featuresPerStep();
        this.registry = lookup.lookupOrThrow(Registries.PLACED_FEATURE);
        for (Identifier id : SUPPORTED) {
            Entry entry = resolve(id);
            if (entry != null) entries.put(id, entry);
        }
    }

    /** The placed feature with its decoration step and feature index, or null if it is not generated here. */
    Entry resolve(Identifier id) {
        Optional<Holder.Reference<PlacedFeature>> holder = registry.get(ResourceKey.create(Registries.PLACED_FEATURE, id));
        if (holder.isEmpty()) return null;
        int step = stepOf(holder.get());
        if (step < 0 || step >= steps.size()) return null;
        int index;
        try {
            index = steps.get(step).indexMapping().applyAsInt(holder.get().value());
        } catch (RuntimeException e) {
            return null;
        }
        return new Entry(holder.get().value(), step, index);
    }

    long seed() {
        return seed;
    }

    public List<Identifier> available() {
        return List.copyOf(entries.keySet());
    }

    public boolean supports(Identifier id) {
        return entries.containsKey(id);
    }

    public List<FeatureHit> inChunk(Identifier featureId, int chunkX, int chunkZ) {
        Entry entry = entries.get(featureId);
        if (entry == null) return List.of();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorationSeed = random.setDecorationSeed(seed, chunkX << 4, chunkZ << 4);
        random.setFeatureSeed(decorationSeed, entry.index(), entry.step());
        List<FeatureHit> hits = new ArrayList<>(1);
        try {
            walk(entry, random, chunkX, chunkZ, pos -> hits.add(new FeatureHit(featureId, pos.getX(), pos.getY(), pos.getZ())));
        } catch (UnsupportedOperationException e) {
            return List.of();
        }
        return hits;
    }

    /**
     * Runs the placement modifiers of {@code entry} for one chunk on an already seeded {@code random}, calling
     * {@code leaf} at each final position as soon as it is produced, like vanilla's lazy placement. Throws
     * {@link UnsupportedOperationException} if a modifier asks the level for something it cannot answer.
     */
    void walk(Entry entry, RandomSource random, int chunkX, int chunkZ, Consumer<BlockPos> leaf) {
        BlockPos origin = new BlockPos(chunkX << 4, heightAccessor.getMinY(), chunkZ << 4);
        PlacementContext context = new PlacementContext(level, generator, Optional.of(entry.feature()));
        walk(entry.feature().placement(), 0, context, random, origin, leaf);
    }

    private static void walk(List<PlacementModifier> modifiers, int depth, PlacementContext context, RandomSource random,
                             BlockPos pos, Consumer<BlockPos> leaf) {
        if (depth == modifiers.size()) {
            leaf.accept(pos);
            return;
        }
        Compat.positions(modifiers.get(depth), context, random, pos,
                next -> walk(modifiers, depth + 1, context, random, next, leaf));
    }

    private int stepOf(Holder<PlacedFeature> feature) {
        for (Holder<Biome> biome : biomeSource.possibleBiomes()) {
            List<HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
            for (int step = 0; step < steps.size(); step++) {
                if (steps.get(step).contains(feature)) return step;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private List<FeatureSorter.StepFeatureData> featuresPerStep() {
        try {
            Field field = ChunkGenerator.class.getDeclaredField("featuresPerStep");
            field.setAccessible(true);
            return ((Supplier<List<FeatureSorter.StepFeatureData>>) field.get(generator)).get();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()),
                    biome -> biome.value().getGenerationSettings().features(), true);
        }
    }

    private WorldGenLevel createLevel() {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBiome" -> {
                        BlockPos p = (BlockPos) args[0];
                        yield biomes.noiseBiome(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2);
                    }
                    case "getNoiseBiome", "getUncachedNoiseBiome" ->
                            biomes.noiseBiome((Integer) args[0], (Integer) args[1], (Integer) args[2]);
                    case "getMinY" -> heightAccessor.getMinY();
                    case "getHeight" -> heightAccessor.getHeight();
                    case "getMaxY" -> heightAccessor.getMaxY();
                    case "getSeed" -> seed;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SeedScoutFeatureLevel";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
