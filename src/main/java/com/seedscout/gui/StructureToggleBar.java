package com.seedscout.gui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.Set;

public final class StructureToggleBar {
    private static final int CELL = 20;
    private static final int ARROW_WIDTH = 12;
    private static final int ON = 0xFF3A7BD5;
    private static final int OFF = 0xFF2A2A2A;

    private final int x;
    private final int y;
    private final int width;
    private final List<Identifier> ids;
    private final Set<Identifier> enabled;
    private final Runnable onChange;
    private final Consumer<Identifier> onSelect;
    private int offset = 0;

    public StructureToggleBar(int x, int y, int width, List<Identifier> ids, Set<Identifier> enabled, Runnable onChange, Consumer<Identifier> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.ids = ids;
        this.enabled = enabled;
        this.onChange = onChange;
        this.onSelect = onSelect;
    }

    private int visibleCount() {
        return Math.max(1, (width - 2 * ARROW_WIDTH) / CELL);
    }

    private boolean overflows() {
        return ids.size() > visibleCount();
    }

    public void render(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY) {
        int startX = x + (overflows() ? ARROW_WIDTH : 0);
        int count = Math.min(visibleCount(), ids.size() - offset);
        Identifier hovered = null;
        for (int i = 0; i < count; i++) {
            Identifier id = ids.get(offset + i);
            int cx = startX + i * CELL;
            boolean on = enabled.contains(id);
            context.fill(cx, y, cx + CELL - 2, y + CELL - 2, on ? ON : OFF);
            StructureIcons.drawIcon(context, id, cx + 1, y + 1);
            if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2) {
                hovered = id;
            }
        }
        if (overflows()) {
            context.text(textRenderer, "<", x + 2, y + 5, 0xFFFFFFFF);
            context.text(textRenderer, ">", x + width - ARROW_WIDTH + 3, y + 5, 0xFFFFFFFF);
        }
        if (hovered != null) {
            context.setTooltipForNextFrame(textRenderer, Component.literal(StructureIcons.displayName(hovered)), mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if ((button != 0 && button != 1) || my < y || my >= y + CELL) return false;
        if (overflows()) {
            if (mx >= x && mx < x + ARROW_WIDTH) {
                offset = Math.max(0, offset - visibleCount());
                return true;
            }
            if (mx >= x + width - ARROW_WIDTH && mx < x + width) {
                offset = Math.min(ids.size() - visibleCount(), offset + visibleCount());
                return true;
            }
        }
        int startX = x + (overflows() ? ARROW_WIDTH : 0);
        int index = (int) ((mx - startX) / CELL);
        if (mx < startX || index < 0 || offset + index >= ids.size() || index >= visibleCount()) return false;
        Identifier id = ids.get(offset + index);
        if (button == 1) {
            onSelect.accept(id);
            return true;
        }
        if (!enabled.remove(id)) {
            enabled.add(id);
        }
        onChange.run();
        return true;
    }
}
