package com.seedscout.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.seedscout.worldgen.DungeonFinder;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class StructureGrid {
    public static final int CELL = 22;
    // Inventory slot bevel: dark top-left, light bottom-right. Off slots use a darker set.
    private static final int ON = 0xFF8B8B8B;
    private static final int ON_SHADOW = 0xFF373737;
    private static final int ON_LIGHT = 0xFFFFFFFF;
    private static final int OFF = 0xFF2B2B2B;
    private static final int OFF_SHADOW = 0xFF111111;
    private static final int OFF_LIGHT = 0xFF4A4A4A;
    private static final int DIM = 0x99000000;
    private static final int TARGET = 0xFFFFD700;

    private final int x;
    private final int y;
    private final int columns;
    private final List<Identifier> ids;
    private final Set<Identifier> enabled;
    private final Runnable onChange;
    private final Consumer<Identifier> onSelect;
    private final Supplier<Identifier> target;

    public StructureGrid(int x, int y, int columns, List<Identifier> ids, Set<Identifier> enabled,
                         Runnable onChange, Consumer<Identifier> onSelect, Supplier<Identifier> target) {
        this.x = x;
        this.y = y;
        this.columns = Math.max(1, columns);
        this.ids = ids;
        this.enabled = enabled;
        this.onChange = onChange;
        this.onSelect = onSelect;
        this.target = target;
    }

    public int rows() {
        return (ids.size() + columns - 1) / columns;
    }

    public int height() {
        return rows() * CELL;
    }

    public void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        Identifier hovered = null;
        Identifier selected = target.get();
        for (int i = 0; i < ids.size(); i++) {
            Identifier id = ids.get(i);
            int cx = x + (i % columns) * CELL;
            int cy = y + (i / columns) * CELL;
            boolean on = enabled.contains(id);
            drawSlot(context, cx, cy, CELL - 2, on);
            StructureIcons.drawIcon(context, id, cx + 2, cy + 2);
            if (!on) {
                context.fill(cx + 1, cy + 1, cx + CELL - 3, cy + CELL - 3, DIM);
            }
            if (id.equals(selected)) {
                context.fill(cx, cy, cx + CELL - 2, cy + 1, TARGET);
                context.fill(cx, cy + CELL - 3, cx + CELL - 2, cy + CELL - 2, TARGET);
                context.fill(cx, cy, cx + 1, cy + CELL - 2, TARGET);
                context.fill(cx + CELL - 3, cy, cx + CELL - 2, cy + CELL - 2, TARGET);
            }
            if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                hovered = id;
            }
        }
        if (hovered != null) {
            context.setTooltipForNextFrame(font, Component.literal(StructureIcons.displayName(hovered)), mouseX, mouseY);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor context, int x, int y, int size, boolean on) {
        context.fill(x, y, x + size, y + size, on ? ON : OFF);
        context.fill(x, y, x + size - 1, y + 1, on ? ON_SHADOW : OFF_SHADOW);
        context.fill(x, y, x + 1, y + size - 1, on ? ON_SHADOW : OFF_SHADOW);
        context.fill(x + 1, y + size - 1, x + size, y + size, on ? ON_LIGHT : OFF_LIGHT);
        context.fill(x + size - 1, y + 1, x + size, y + size, on ? ON_LIGHT : OFF_LIGHT);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) return false;
        if (mx < x || my < y || mx >= x + columns * CELL || my >= y + height()) return false;
        int col = (int) ((mx - x) / CELL);
        int row = (int) ((my - y) / CELL);
        int index = row * columns + col;
        if (col >= columns || index >= ids.size()) return false;
        Identifier id = ids.get(index);
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            onSelect.accept(id);
            return true;
        }
        if (!enabled.remove(id)) {
            enabled.add(id);
        }
        onChange.run();
        return true;
    }

    /** All turns on everything but dungeons, which build terrain and stay opt-in one by one; None clears them too. */
    public void setAll(boolean on) {
        if (on) {
            for (Identifier id : ids) {
                if (!DungeonFinder.isDungeon(id)) enabled.add(id);
            }
        } else {
            enabled.removeAll(ids);
        }
        onChange.run();
    }
}
