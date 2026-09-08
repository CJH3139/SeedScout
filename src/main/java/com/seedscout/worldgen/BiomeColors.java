package com.seedscout.worldgen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class BiomeColors {
    public static final int UNKNOWN = 0xFF7F7F7F;
    private static final Map<Identifier, Integer> COLORS = new HashMap<>();

    static {
        put("the_void", 0x000000);
        put("plains", 0x8DB360);
        put("sunflower_plains", 0xB5DB88);
        put("snowy_plains", 0xFFFFFF);
        put("ice_spikes", 0xB4DCDC);
        put("desert", 0xFA9418);
        put("swamp", 0x07F9B2);
        put("mangrove_swamp", 0x67352B);
        put("forest", 0x056621);
        put("flower_forest", 0x2D8E49);
        put("birch_forest", 0x307444);
        put("dark_forest", 0x40511A);
        put("pale_garden", 0x8C8C8C);
        put("old_growth_birch_forest", 0x589C6C);
        put("old_growth_pine_taiga", 0x596651);
        put("old_growth_spruce_taiga", 0x818E79);
        put("taiga", 0x0B6659);
        put("snowy_taiga", 0x31554A);
        put("savanna", 0xBDB25F);
        put("savanna_plateau", 0xA79D64);
        put("windswept_hills", 0x606060);
        put("windswept_gravelly_hills", 0x888888);
        put("windswept_forest", 0x589C6C);
        put("windswept_savanna", 0xE5DA87);
        put("jungle", 0x537B09);
        put("sparse_jungle", 0x628B17);
        put("bamboo_jungle", 0x768E14);
        put("badlands", 0xD94515);
        put("eroded_badlands", 0xFF6D3D);
        put("wooded_badlands", 0xB09765);
        put("meadow", 0x2C4205);
        put("cherry_grove", 0xFFB7D8);
        put("grove", 0x47726C);
        put("snowy_slopes", 0xC4C4C4);
        put("frozen_peaks", 0xA0A0A0);
        put("jagged_peaks", 0xDCDCC8);
        put("stony_peaks", 0x7B8F74);
        put("river", 0x0000FF);
        put("frozen_river", 0xA0A0FF);
        put("beach", 0xFADE55);
        put("snowy_beach", 0xFAF0C0);
        put("stony_shore", 0xA2A284);
        put("warm_ocean", 0x0000AC);
        put("lukewarm_ocean", 0x000090);
        put("deep_lukewarm_ocean", 0x000040);
        put("ocean", 0x000070);
        put("deep_ocean", 0x000030);
        put("cold_ocean", 0x202070);
        put("deep_cold_ocean", 0x202038);
        put("frozen_ocean", 0x7070D6);
        put("deep_frozen_ocean", 0x404090);
        put("mushroom_fields", 0xFF00FF);
        put("dripstone_caves", 0x7B6254);
        put("lush_caves", 0x283C00);
        put("deep_dark", 0x0E252A);
    }

    private BiomeColors() {}

    private static void put(String path, int rgb) {
        COLORS.put(Identifier.withDefaultNamespace(path), 0xFF000000 | rgb);
    }

    public static int colorOf(Identifier biomeId) {
        return COLORS.getOrDefault(biomeId, UNKNOWN);
    }
}
