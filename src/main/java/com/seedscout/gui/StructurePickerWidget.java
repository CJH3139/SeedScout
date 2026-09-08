package com.seedscout.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class StructurePickerWidget extends AlwaysSelectedEntryListWidget<StructurePickerWidget.Entry> {
    private static final int ROW_HEIGHT = 20;
    private final List<Identifier> all;
    private final Consumer<Identifier> onPick;

    public StructurePickerWidget(MinecraftClient client, int x, int y, int width, int height, List<Identifier> all, Consumer<Identifier> onPick) {
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
                addEntry(new Entry(id));
            }
        }
        setScrollY(0);
    }

    @Override
    public int getRowWidth() {
        return width - 8;
    }

    @Override
    protected int getScrollbarX() {
        return getX() + width - 6;
    }

    public final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
        private final Identifier id;

        Entry(Identifier id) {
            this.id = id;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = getX() + 2;
            int y = getY() + 2;
            StructureIcons.drawIcon(context, id, x, y);
            context.drawTextWithShadow(client.textRenderer, StructureIcons.displayName(id), x + 20, y + 4,
                    hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (click.button() == 0) {
                onPick.accept(id);
                return true;
            }
            return false;
        }

        @Override
        public Text getNarration() {
            return Text.literal(StructureIcons.displayName(id));
        }
    }
}
