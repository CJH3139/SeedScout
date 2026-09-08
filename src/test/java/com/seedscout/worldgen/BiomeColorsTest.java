package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class BiomeColorsTest {
    private static final List<String> OVERWORLD = List.of(
            "the_void", "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert", "swamp", "mangrove_swamp",
            "forest", "flower_forest", "birch_forest", "dark_forest", "pale_garden", "old_growth_birch_forest",
            "old_growth_pine_taiga", "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau",
            "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle",
            "sparse_jungle", "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands", "meadow", "cherry_grove",
            "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "river", "frozen_river", "beach",
            "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean",
            "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "mushroom_fields", "dripstone_caves",
            "lush_caves", "deep_dark");

    @Test
    void everyOverworldBiomeHasAColor() {
        for (String path : OVERWORLD) {
            int color = BiomeColors.colorOf(Identifier.withDefaultNamespace(path));
            assertNotEquals(BiomeColors.UNKNOWN, color, path);
            assertEquals(0xFF, color >>> 24, path + " must be opaque");
        }
    }

    @Test
    void unknownBiomeFallsBackToGrey() {
        assertEquals(BiomeColors.UNKNOWN, BiomeColors.colorOf(Identifier.fromNamespaceAndPath("somemod", "weird")));
    }
}
