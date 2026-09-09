package com.seedscout.gui;

import com.seedscout.worldgen.BiomeColors;
import com.seedscout.worldgen.BiomeHit;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import net.minecraft.resources.Identifier;

public record SearchResult(String name, int x, int z, double distance, Identifier structureId, int color) {
    public static SearchResult of(StructureHit hit) {
        Identifier id = SeedWorld.idOf(hit.structure());
        return new SearchResult(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), hit.distance(), id, StructureIcons.colorFor(id));
    }

    public static SearchResult of(com.seedscout.worldgen.FeatureFinder.FeatureHit hit, int centerX, int centerZ) {
        double dx = hit.blockX() - centerX;
        double dz = hit.blockZ() - centerZ;
        return new SearchResult(StructureIcons.displayName(hit.featureId()) + "  y " + hit.blockY(), hit.blockX(), hit.blockZ(),
                Math.sqrt(dx * dx + dz * dz), hit.featureId(), StructureIcons.colorFor(hit.featureId()));
    }

    public static SearchResult of(BiomeHit hit) {
        Identifier id = SeedWorld.idOf(hit.biome());
        return new SearchResult(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), hit.distance(), null, BiomeColors.colorOf(id));
    }
}
