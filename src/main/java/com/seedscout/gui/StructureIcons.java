package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class StructureIcons {
    public static final Identifier GENERIC = Identifier.fromNamespaceAndPath(SeedScoutClient.MOD_ID, "textures/gui/icons/generic.png");
    public static final Identifier ARROW = Identifier.fromNamespaceAndPath(SeedScoutClient.MOD_ID, "textures/gui/arrow.png");

    private static final Map<Identifier, Identifier> ICON_CACHE = new HashMap<>();
    private static final Map<Identifier, Identifier> MAP_SPRITES = new HashMap<>();
    private static final Map<Identifier, Item> ITEMS = new HashMap<>();

    static {
        sprite("village_plains", "plains_village");
        sprite("village_desert", "desert_village");
        sprite("village_savanna", "savanna_village");
        sprite("village_snowy", "snowy_village");
        sprite("village_taiga", "taiga_village");
        sprite("mansion", "woodland_mansion");
        sprite("monument", "ocean_monument");
        sprite("jungle_pyramid", "jungle_temple");
        sprite("swamp_hut", "swamp_hut");
        sprite("trial_chambers", "trial_chambers");

        item("stronghold", Items.ENDER_EYE);
        item("ancient_city", Items.SCULK_SHRIEKER);
        item("pillager_outpost", Items.CROSSBOW);
        item("buried_treasure", Items.HEART_OF_THE_SEA);
        item("desert_pyramid", Items.CHISELED_SANDSTONE);
        item("igloo", Items.SNOW_BLOCK);
        item("trail_ruins", Items.BRUSH);
        item("mineshaft", Items.RAIL);
        item("mineshaft_mesa", Items.RAIL);
        item("shipwreck", Items.OAK_BOAT);
        item("shipwreck_beached", Items.OAK_BOAT);
        item("ocean_ruin_cold", Items.PRISMARINE_BRICKS);
        item("ocean_ruin_warm", Items.PRISMARINE_BRICKS);
        item("ruined_portal", Items.CRYING_OBSIDIAN);
        item("ruined_portal_desert", Items.CRYING_OBSIDIAN);
        item("ruined_portal_jungle", Items.CRYING_OBSIDIAN);
        item("ruined_portal_swamp", Items.CRYING_OBSIDIAN);
        item("ruined_portal_mountain", Items.CRYING_OBSIDIAN);
        item("ruined_portal_ocean", Items.CRYING_OBSIDIAN);
        item("ruined_portal_nether", Items.CRYING_OBSIDIAN);
        item("fortress", Items.BLAZE_ROD);
        item("bastion_remnant", Items.PIGLIN_HEAD);
        item("nether_fossil", Items.BONE);
        item("end_city", Items.PURPUR_BLOCK);
    }

    private StructureIcons() {}

    private static void sprite(String structurePath, String spriteName) {
        MAP_SPRITES.put(Identifier.withDefaultNamespace(structurePath), Identifier.withDefaultNamespace(spriteName));
    }

    private static void item(String structurePath, Item item) {
        ITEMS.put(Identifier.withDefaultNamespace(structurePath), item);
    }

    public static Identifier iconFor(Identifier structureId) {
        return ICON_CACHE.computeIfAbsent(structureId, id -> {
            Identifier candidate = Identifier.fromNamespaceAndPath(SeedScoutClient.MOD_ID, "textures/gui/icons/" + id.getPath() + ".png");
            boolean exists = Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent();
            return exists ? candidate : GENERIC;
        });
    }

    public static void drawIcon(GuiGraphicsExtractor context, Identifier structureId, int x, int y) {
        Identifier icon = iconFor(structureId);
        if (!icon.equals(GENERIC)) {
            context.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 16, 16, 16, 16);
            return;
        }
        Identifier spriteName = MAP_SPRITES.get(structureId);
        if (spriteName != null) {
            TextureAtlasSprite sprite = context.getSprite(new SpriteId(Sheets.MAP_DECORATIONS_SHEET, spriteName));
            context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16);
            return;
        }
        Item item = ITEMS.get(structureId);
        if (item != null) {
            context.item(new ItemStack(item), x, y);
            return;
        }
        context.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 16, 16, 16, 16, colorFor(structureId));
    }

    public static int colorFor(Identifier structureId) {
        float hue = (structureId.toString().hashCode() & 0xFFFF) / 65536f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, 0.7f, 0.95f) & 0xFFFFFF);
    }

    public static String displayName(Identifier structureId) {
        String[] parts = structureId.getPath().split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
