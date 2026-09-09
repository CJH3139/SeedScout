package com.seedscout.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class SegmentedControl {
    public record Segment(Supplier<Component> label, BooleanSupplier selected, Runnable onClick) {
        public static Segment of(Component label, BooleanSupplier selected, Runnable onClick) {
            return new Segment(() -> label, selected, onClick);
        }
    }

    private static final int ON = 0xFF3A7BD5;
    private static final int ON_HOVER = 0xFF4C8DE6;
    private static final int OFF = 0xFF2A313A;
    private static final int OFF_HOVER = 0xFF363E48;
    private static final int BORDER = 0xFF0B0E12;
    private static final int TEXT_ON = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFFB8C2CC;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<Segment> segments;

    public SegmentedControl(int x, int y, int width, int height, List<Segment> segments) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.segments = segments;
    }

    private int segmentLeft(int index) {
        return x + index * width / segments.size();
    }

    private int segmentRight(int index) {
        return x + (index + 1) * width / segments.size();
    }

    public void render(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY) {
        context.fill(x, y, x + width, y + height, BORDER);
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            int left = segmentLeft(i) + 1;
            int right = segmentRight(i) - 1;
            boolean selected = segment.selected().getAsBoolean();
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + height;
            int fill = selected ? (hovered ? ON_HOVER : ON) : (hovered ? OFF_HOVER : OFF);
            context.fill(left, y + 1, right, y + height - 1, fill);
            String text = segment.label().get().getString();
            int avail = right - left - 6;
            while (text.length() > 1 && font.width(text) > avail) {
                text = text.substring(0, text.length() - 1);
            }
            int textX = (left + right) / 2 - font.width(text) / 2;
            int textY = y + (height - 8) / 2;
            context.text(font, text, textX, textY, selected ? TEXT_ON : TEXT_OFF, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || mx < x || mx >= x + width || my < y || my >= y + height) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (mx >= segmentLeft(i) && mx < segmentRight(i)) {
                segments.get(i).onClick().run();
                return true;
            }
        }
        return false;
    }
}
