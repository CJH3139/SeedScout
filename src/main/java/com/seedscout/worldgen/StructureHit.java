package com.seedscout.worldgen;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

public record StructureHit(RegistryEntry<Structure> structure, ChunkPos chunk, int blockX, int blockZ, double distance) {
    public static StructureHit of(RegionHit hit, int centerBlockX, int centerBlockZ) {
        double dx = hit.blockX() - centerBlockX;
        double dz = hit.blockZ() - centerBlockZ;
        return new StructureHit(hit.structure(), hit.chunk(), hit.blockX(), hit.blockZ(), Math.sqrt(dx * dx + dz * dz));
    }
}
