package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class StructureIcons {
    public static final Identifier GENERIC = Identifier.of(SeedScoutClient.MOD_ID, "textures/gui/icons/generic.png");
    public static final Identifier ARROW = Identifier.of(SeedScoutClient.MOD_ID, "textures/gui/arrow.png");
    private static final Map<Identifier, Identifier> ICON_CACHE = new HashMap<>();

    private StructureIcons() {}

    public static Identifier iconFor(Identifier structureId) {
        return ICON_CACHE.computeIfAbsent(structureId, id -> {
            Identifier candidate = Identifier.of(SeedScoutClient.MOD_ID, "textures/gui/icons/" + id.getPath() + ".png");
            boolean exists = MinecraftClient.getInstance().getResourceManager().getResource(candidate).isPresent();
            return exists ? candidate : GENERIC;
        });
    }

    public static void drawIcon(DrawContext context, Identifier structureId, int x, int y) {
        Identifier icon = iconFor(structureId);
        if (icon.equals(GENERIC)) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 16, 16, 16, 16, colorFor(structureId));
        } else {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 16, 16, 16, 16);
        }
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
