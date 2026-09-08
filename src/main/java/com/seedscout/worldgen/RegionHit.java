package com.seedscout.worldgen;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

public record RegionHit(RegistryEntry<Structure> structure, ChunkPos chunk, int blockX, int blockZ) {}
