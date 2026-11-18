package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import com.seedscout.config.SeedScoutConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Settings screen opened from Mod Menu on Fabric and the Mods list on NeoForge. */
public final class SeedScoutConfigScreen extends Screen {
    private static final int BUTTON_W = 150;

    private final Screen parent;
    private final SeedScoutConfig config = SeedScoutClient.config();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public SeedScoutConfigScreen(Screen parent) {
        super(Component.translatable("seedscout.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        GridLayout grid = new GridLayout().columnSpacing(10).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(2);

        rows.addChild(CycleButton.builder(SeedScoutConfigScreen::scaleLabel, Math.min(config.guiScale, maxScale()))
                .withValues(scaleChoices())
                .create(0, 0, BUTTON_W, 20, Component.translatable("seedscout.config.gui_scale"),
                        (button, value) -> config.guiScale = value));
        rows.addChild(toggle("seedscout.config.disk_cache", config.diskCache, value -> config.diskCache = value));
        rows.addChild(toggle("seedscout.config.beam", config.showBeam, value -> config.showBeam = value));
        rows.addChild(toggle("seedscout.config.slime", config.showSlimeChunks, value -> config.showSlimeChunks = value));
        rows.addChild(toggle("seedscout.config.grid", config.showGrid, value -> config.showGrid = value));
        rows.addChild(toggle("seedscout.config.facing", config.showFacing, value -> config.showFacing = value));
        layout.addToContents(grid);

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        SeedScoutClient.saveConfig();
        Compat.setScreen(minecraft, parent);
    }

    private CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .create(0, 0, BUTTON_W, 20, Component.translatable(key), (button, value) -> setter.accept(value));
    }

    private int maxScale() {
        return minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
    }

    private List<Integer> scaleChoices() {
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i <= maxScale(); i++) choices.add(i);
        return choices;
    }

    private static Component scaleLabel(int scale) {
        return scale == 0 ? Component.translatable("seedscout.config.gui_scale.game") : Component.literal(Integer.toString(scale));
    }
}
