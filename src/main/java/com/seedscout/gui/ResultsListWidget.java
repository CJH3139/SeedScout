package com.seedscout.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ResultsListWidget extends ObjectSelectionList<ResultsListWidget.Entry> {
    private static final int ROW_HEIGHT = 20;

    public record Row(String title, String subtitle, Identifier structureId, int color, Runnable onPick, Runnable onSecondary) {}

    private Component status = Component.empty();

    public ResultsListWidget(Minecraft client, int width, int height, int y) {
        super(client, width, height, y, ROW_HEIGHT);
    }

    public void setRows(List<Row> rows, Component emptyText) {
        clearEntries();
        for (Row row : rows) {
            addEntry(new Entry(row));
        }
        status = rows.isEmpty() ? emptyText : Component.empty();
    }

    public void setStatus(Component text) {
        clearEntries();
        status = text;
    }

    @Override
    public int getRowWidth() {
        return width - 8;
    }

    @Override
    protected int scrollBarX() {
        return getX() + width - 6;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractWidgetRenderState(context, mouseX, mouseY, deltaTicks);
        if (children().isEmpty() && !status.getString().isEmpty()) {
            context.centeredText(minecraft.font, status, getX() + width / 2, getY() + 10, 0xFFBBBBBB);
        }
    }

    public final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Row row;

        Entry(Row row) {
            this.row = row;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getX() + 2;
            int y = getY() + 1;
            if (row.structureId() != null) {
                StructureIcons.drawIcon(context, row.structureId(), x, y);
            } else {
                StructureIcons.drawSwatch(context, row.color(), x, y);
            }
            context.text(minecraft.font, row.title(), x + 20, y + 1, hovered ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            context.text(minecraft.font, row.subtitle(), x + 20, y + 10, 0xFF8FA1B3, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (click.button() == 0) {
                row.onPick().run();
                return true;
            }
            if (click.button() == 1 && row.onSecondary() != null) {
                row.onSecondary().run();
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(row.title());
        }
    }
}
