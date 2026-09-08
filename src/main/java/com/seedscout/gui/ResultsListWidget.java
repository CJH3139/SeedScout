package com.seedscout.gui;

import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ResultsListWidget extends ObjectSelectionList<ResultsListWidget.Entry> {
    private static final int ROW_HEIGHT = 22;
    private final Consumer<StructureHit> onPick;
    private final List<StructureHit> results = new ArrayList<>();
    private Component status = Component.empty();

    public ResultsListWidget(Minecraft client, int width, int height, int y, Consumer<StructureHit> onPick) {
        super(client, width, height, y, ROW_HEIGHT);
        this.onPick = onPick;
    }

    public void setResults(List<StructureHit> hits) {
        clearEntries();
        results.clear();
        results.addAll(hits);
        for (StructureHit hit : hits) {
            addEntry(new com.seedscout.gui.ResultsListWidget.Entry(hit));
        }
        status = hits.isEmpty() ? Component.translatable("seedscout.screen.no_results") : Component.empty();
    }

    public void setStatus(Component text) {
        clearEntries();
        results.clear();
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
        if (results.isEmpty() && !status.getString().isEmpty()) {
            context.centeredText(minecraft.font, status, getX() + width / 2, getY() + 10, 0xFFBBBBBB);
        }
    }

    public final class Entry extends ObjectSelectionList.Entry<com.seedscout.gui.ResultsListWidget.Entry> {
        private final StructureHit hit;

        Entry(StructureHit hit) {
            this.hit = hit;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getX() + 2;
            int y = getY() + 2;
            String name = StructureIcons.displayName(SeedWorld.idOf(hit.structure()));
            String coords = hit.blockX() + ", " + hit.blockZ() + "  " + Math.round(hit.distance()) + " m";
            context.text(minecraft.font, name, x, y, hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            context.text(minecraft.font, coords, x, y + 10, 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (click.button() == 0) {
                onPick.accept(hit);
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.literal(StructureIcons.displayName(SeedWorld.idOf(hit.structure())));
        }
    }
}
