package com.seedscout.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public record BiomeHit(Holder<Biome> biome, int blockX, int blockZ, double distance) {}
