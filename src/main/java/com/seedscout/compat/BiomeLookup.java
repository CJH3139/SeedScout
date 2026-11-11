package com.seedscout.compat;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Samples a biome source without caching, so it is safe to share between worker threads. */
public interface BiomeLookup {
    Holder<Biome> noiseBiome(int quartX, int quartY, int quartZ);

    Set<Holder<Biome>> biomesWithin(int blockX, int blockY, int blockZ, int radius);
}
