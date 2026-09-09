package com.seedscout.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ContextMenu {
    public record Item(Component label, Runnable action) {}

    private static final int ITEM_H = 12;
    private static final int PAD = 4;
    private static final int BORDER = 0xFF0B0E12;
    private static final int BACKGROUND = 0xFF1E242B;
    private static final int HOVER = 0xFF3A7BD5;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int TITLE = 0xFF7C8A96;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Component title;
    private final List<Item> items;

    public ContextMenu(Font font, int clickX, int clickY, int screenWidth, int screenHeight, Component title, List<Item> items) {
        this.title = title;
        this.items = items;
        int w = font.width(title);
        for (Item item : items) w = Math.max(w, font.width(item.label()));
        this.width = w + PAD * 2 + 4;
        this.height = ITEM_H * (items.size() + 1) + PAD;
        this.x = Math.max(0, Math.min(clickX, screenWidth - width));
        this.y = Math.max(0, Math.min(clickY, screenHeight - height));
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    private int itemTop(int index) {
        return y + PAD / 2 + ITEM_H * (index + 1);
    }

    public void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        context.fill(x, y, x + width, y + height, BACKGROUND);
        context.text(font, title, x + PAD, y + PAD / 2 + 2, TITLE, false);
        for (int i = 0; i < items.size(); i++) {
            int top = itemTop(i);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ITEM_H;
            if (hovered) context.fill(x + 1, top, x + width - 1, top + ITEM_H, HOVER);
            context.text(font, items.get(i).label(), x + PAD, top + 2, TEXT, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        if (button != 0) return true;
        for (int i = 0; i < items.size(); i++) {
            int top = itemTop(i);
            if (my >= top && my < top + ITEM_H) {
                items.get(i).action().run();
                return true;
            }
        }
        return true;
    }
}
