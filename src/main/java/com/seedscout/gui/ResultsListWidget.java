package com.seedscout.gui;

import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

public final class ResultsListWidget extends AlwaysSelectedEntryListWidget<ResultsListWidget.Entry> {
    private static final int ROW_HEIGHT = 22;
    private final Consumer<StructureHit> onPick;
    private final List<StructureHit> results = new ArrayList<>();
    private Text status = Text.empty();

    public ResultsListWidget(MinecraftClient client, int width, int height, int y, Consumer<StructureHit> onPick) {
        super(client, width, height, y, ROW_HEIGHT);
        this.onPick = onPick;
    }

    public void setResults(List<StructureHit> hits) {
        clearEntries();
        results.clear();
        results.addAll(hits);
        for (StructureHit hit : hits) {
            addEntry(new Entry(hit));
        }
        status = hits.isEmpty() ? Text.translatable("seedscout.screen.no_results") : Text.empty();
    }

    public void setStatus(Text text) {
        clearEntries();
        results.clear();
        status = text;
    }

    @Override
    public int getRowWidth() {
        return width - 8;
    }

    @Override
    protected int getScrollbarX() {
        return getX() + width - 6;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.renderWidget(context, mouseX, mouseY, deltaTicks);
        if (results.isEmpty() && !status.getString().isEmpty()) {
            context.drawCenteredTextWithShadow(client.textRenderer, status, getX() + width / 2, getY() + 10, 0xFFBBBBBB);
        }
    }

    public final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        private final StructureHit hit;

        Entry(StructureHit hit) {
            this.hit = hit;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getX() + 2;
            int y = getY() + 2;
            String name = StructureIcons.displayName(SeedWorld.idOf(hit.structure()));
            String coords = hit.blockX() + ", " + hit.blockZ() + "  " + Math.round(hit.distance()) + " m";
            context.drawTextWithShadow(client.textRenderer, name, x, y, hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            context.drawTextWithShadow(client.textRenderer, coords, x, y + 10, 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (click.button() == 0) {
                onPick.accept(hit);
                return true;
            }
            return false;
        }

        @Override
        public Text getNarration() {
            return Text.literal(StructureIcons.displayName(SeedWorld.idOf(hit.structure())));
        }
    }
}
