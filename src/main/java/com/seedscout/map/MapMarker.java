package com.seedscout.map;

import com.seedscout.worldgen.FeatureFinder;
import com.seedscout.worldgen.RegionHit;
import com.seedscout.worldgen.SeedWorld;
import net.minecraft.resources.Identifier;

public record MapMarker(Identifier id, int blockX, int blockZ, Integer blockY) {
    public static MapMarker of(RegionHit hit) {
        return new MapMarker(SeedWorld.idOf(hit.structure()), hit.blockX(), hit.blockZ(), null);
    }

    public static MapMarker of(FeatureFinder.FeatureHit hit) {
        return new MapMarker(hit.featureId(), hit.blockX(), hit.blockZ(), hit.blockY());
    }
}
