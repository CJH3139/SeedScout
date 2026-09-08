package com.seedscout.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class StructurePickerWidget extends ObjectSelectionList<StructurePickerWidget.Entry> {
    private static final int ROW_HEIGHT = 20;
    private final List<Identifier> all;
    private final Consumer<Identifier> onPick;

    public StructurePickerWidget(Minecraft client, int x, int y, int width, int height, List<Identifier> all, Consumer<Identifier> onPick) {
        super(client, width, height, y, ROW_HEIGHT);
        setX(x);
        this.all = all;
        this.onPick = onPick;
        filter("");
    }

    public void filter(String text) {
        clearEntries();
        String query = text.trim().toLowerCase(Locale.ROOT);
        for (Identifier id : all) {
            String name = StructureIcons.displayName(id).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || name.contains(query) || id.getPath().contains(query)) {
                addEntry(new com.seedscout.gui.StructurePickerWidget.Entry(id));
            }
        }
        setScrollAmount(0);
    }

    @Override
    public int getRowWidth() {
        return width - 8;
    }

    @Override
    protected int scrollBarX() {
        return getX() + width - 6;
    }

    public final class Entry extends ObjectSelectionList.Entry<com.seedscout.gui.StructurePickerWidget.Entry> {
        private final Identifier id;

        Entry(Identifier id) {
            this.id = id;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getX() + 2;
            int y = getY() + 2;
            StructureIcons.drawIcon(context, id, x, y);
            context.text(minecraft.font, StructureIcons.displayName(id), x + 20, y + 4,
                    hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (click.button() == 0) {
                onPick.accept(id);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(StructureIcons.displayName(id));
        }
    }
}
