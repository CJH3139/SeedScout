package com.seedscout.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.level.biome.Climate;
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

    private record Entry(PlacedFeature feature, int step, int index) {}

    private final long seed;
    private final ChunkGenerator generator;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final LevelHeightAccessor heightAccessor;
    private final WorldGenLevel level;
    private final Map<Identifier, Entry> entries = new LinkedHashMap<>();

    FeatureFinder(long seed, ChunkGenerator generator, BiomeSource biomeSource, Climate.Sampler sampler,
                  LevelHeightAccessor heightAccessor, HolderLookup.Provider lookup) {
        this.seed = seed;
        this.generator = generator;
        this.biomeSource = biomeSource;
        this.sampler = sampler;
        this.heightAccessor = heightAccessor;
        this.level = createLevel();
        List<FeatureSorter.StepFeatureData> steps = featuresPerStep();
        HolderLookup.RegistryLookup<PlacedFeature> registry = lookup.lookupOrThrow(Registries.PLACED_FEATURE);
        for (Identifier id : SUPPORTED) {
            Optional<Holder.Reference<PlacedFeature>> holder = registry.get(ResourceKey.create(Registries.PLACED_FEATURE, id));
            if (holder.isEmpty()) continue;
            int step = stepOf(holder.get());
            if (step < 0 || step >= steps.size()) continue;
            int index;
            try {
                index = steps.get(step).indexMapping().applyAsInt(holder.get().value());
            } catch (RuntimeException e) {
                continue;
            }
            entries.put(id, new Entry(holder.get().value(), step, index));
        }
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
        BlockPos origin = new BlockPos(chunkX << 4, heightAccessor.getMinY(), chunkZ << 4);
        PlacementContext context = new PlacementContext(level, generator, Optional.of(entry.feature()));
        List<FeatureHit> hits = new ArrayList<>(1);
        try {
            walk(entry.feature().placement(), 0, context, random, origin, featureId, hits);
        } catch (UnsupportedOperationException e) {
            return List.of();
        }
        return hits;
    }

    private static void walk(List<PlacementModifier> modifiers, int depth, PlacementContext context, RandomSource random,
                             BlockPos pos, Identifier featureId, List<FeatureHit> out) {
        if (depth == modifiers.size()) {
            out.add(new FeatureHit(featureId, pos.getX(), pos.getY(), pos.getZ()));
            return;
        }
        modifiers.get(depth).getPositions(context, random, pos)
                .forEach(next -> walk(modifiers, depth + 1, context, random, next, featureId, out));
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
                        yield biomeSource.getNoiseBiome(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2, sampler);
                    }
                    case "getNoiseBiome", "getUncachedNoiseBiome" ->
                            biomeSource.getNoiseBiome((Integer) args[0], (Integer) args[1], (Integer) args[2], sampler);
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
