package com.seedscout.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SegmentedControl {
    public record Segment(Supplier<Component> label, BooleanSupplier selected, Runnable onClick) {
        public static Segment of(Component label, BooleanSupplier selected, Runnable onClick) {
            return new Segment(() -> label, selected, onClick);
        }
    }

    // Vanilla button sprites: the selected segment looks pressed in, the rest are normal stone buttons.
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_PRESSED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final int TEXT_ON = 0xFFFFFF55;
    private static final int TEXT_OFF = 0xFFFFFFFF;

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
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            int left = segmentLeft(i);
            int right = segmentRight(i) - (i < segments.size() - 1 ? 1 : 0);
            boolean selected = segment.selected().getAsBoolean();
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + height;
            Identifier sprite = selected ? BUTTON_PRESSED : (hovered ? BUTTON_HOVER : BUTTON);
            context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left, y, right - left, height);
            String text = segment.label().get().getString();
            int avail = right - left - 6;
            while (text.length() > 1 && font.width(text) > avail) {
                text = text.substring(0, text.length() - 1);
            }
            int textX = (left + right) / 2 - font.width(text) / 2;
            int textY = y + (height - 8) / 2;
            context.text(font, text, textX, textY, selected ? TEXT_ON : TEXT_OFF, true);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || mx < x || mx >= x + width || my < y || my >= y + height) {
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
