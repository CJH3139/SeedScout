package com.seedscout.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public enum Dimension {
    OVERWORLD("overworld", NoiseGeneratorSettings.OVERWORLD, Level.OVERWORLD),
    NETHER("nether", NoiseGeneratorSettings.NETHER, Level.NETHER),
    END("end", NoiseGeneratorSettings.END, Level.END);

    private final String key;
    private final ResourceKey<NoiseGeneratorSettings> noiseSettings;
    private final ResourceKey<Level> level;

    Dimension(String key, ResourceKey<NoiseGeneratorSettings> noiseSettings, ResourceKey<Level> level) {
        this.key = key;
        this.noiseSettings = noiseSettings;
        this.level = level;
    }

    public ResourceKey<NoiseGeneratorSettings> noiseSettings() {
        return noiseSettings;
    }

    public Identifier levelId() {
        return level.identifier();
    }

    public Component displayName() {
        return Component.translatable("seedscout.dimension." + key);
    }

    public BiomeSource createBiomeSource(HolderLookup.Provider lookup) {
        switch (this) {
            case OVERWORLD -> {
                Holder<MultiNoiseBiomeSourceParameterList> params = lookup
                        .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
                return MultiNoiseBiomeSource.createFromPreset(params);
            }
            case NETHER -> {
                Holder<MultiNoiseBiomeSourceParameterList> params = lookup
                        .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
                return MultiNoiseBiomeSource.createFromPreset(params);
            }
            default -> {
                return TheEndBiomeSource.create(lookup.lookupOrThrow(Registries.BIOME));
            }
        }
    }

    public static Dimension fromLevel(ResourceKey<Level> key) {
        for (Dimension d : values()) {
            if (d.level.equals(key)) return d;
        }
        return OVERWORLD;
    }

    public static Dimension fromLevelId(Identifier id) {
        for (Dimension d : values()) {
            if (d.levelId().equals(id)) return d;
        }
        return OVERWORLD;
    }
}
