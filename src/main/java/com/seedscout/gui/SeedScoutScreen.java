package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.config.SeedParser;
import com.seedscout.config.SeedScoutConfig;
import com.seedscout.map.MapViewport;
import com.seedscout.map.MapWorker;
import com.seedscout.map.TileKey;
import com.seedscout.worldgen.RegionHit;
import com.seedscout.worldgen.SeedSource;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.worldgen.StructureHit;
import com.seedscout.waypoint.Waypoint;
import com.seedscout.waypoint.WaypointState;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

public class SeedScoutScreen extends Screen {
    protected static final int BACKGROUND = 0xFF101418;
    protected static final int PANEL = 0xFF1E242B;
    protected static final int TEXT = 0xFFE0E0E0;
    protected static final int PLACEHOLDER = 0xFF2A2A2A;
    protected static final int FAILED = 0xFF5A1E1E;
    protected static final int TOP_BAR_HEIGHT = 48;
    protected static final int RIGHT_PANEL_WIDTH = 180;

    protected static MapSession session;
    private static boolean sessionLoading;
    private static String sessionError;

    private static Long loadingSeed;

    private static Long errorSeed;

    private static Long pendingSeed;
    protected static final MapViewport viewport = new MapViewport();
    private static boolean viewportInitialized;

    protected final SeedScoutConfig config = SeedScoutClient.config();
    private boolean dragging;
    private EditBox seedField;
    private Button applyButton;
    private StructureToggleBar toggleBar;
    private Button structureButton;
    private StructurePickerWidget picker;
    private EditBox pickerFilter;
    private CycleButton<Integer> radiusButton;
    private Button searchButton;
    private ResultsListWidget resultsList;
    private Button centerButton;
    private Button clearButton;
    private CycleButton<Boolean> beamButton;
    private static Identifier lastStructure = Identifier.withDefaultNamespace("village_plains");
    private static List<StructureHit> lastResults = List.of();

    private static boolean searching;

    private static Long lastAppliedSeed;

    public SeedScoutScreen(Minecraft client) {
        super(Component.translatable("seedscout.screen.title"));
    }

    @Override
    protected void init() {
        viewport.left = 0;
        viewport.top = TOP_BAR_HEIGHT;
        viewport.width = width - RIGHT_PANEL_WIDTH;
        viewport.height = height - TOP_BAR_HEIGHT;
        if (!viewportInitialized) {
            centerOnPlayer();
        }

        OptionalLong seed = SeedSource.resolve(minecraft, config);
        if (seed.isPresent()) {
            ensureSession(seed.getAsLong());
        } else if (lastAppliedSeed != null) {
            ensureSession(lastAppliedSeed);
        } else {
            clearSession();
        }

        seedField = new EditBox(font, 6, 20, 160, 18,
                Component.translatable("seedscout.screen.seed_placeholder"));
        seedField.setMaxLength(64);
        seedField.setHint(Component.translatable("seedscout.screen.seed_placeholder"));
        seedField.setResponder(this::updateSeedFieldColor);
        seedField.setValue(currentSeedText());
        boolean singleplayer = minecraft.getSingleplayerServer() != null;
        seedField.setEditable(!singleplayer);
        addRenderableWidget(seedField);

        applyButton = Button.builder(
                        Component.translatable("seedscout.screen.apply"), b -> applySeed())
                .bounds(170, 20, 50, 18)
                .build();
        applyButton.active = !singleplayer;
        addRenderableWidget(applyButton);

        rebuildToggleBar();
        buildRightPanel();
    }

    void rebuildToggleBar() {
        if (session == null) {
            toggleBar = null;
            return;
        }
        List<Identifier> ids = session.world().allStructures().stream()
                .map(SeedWorld::idOf)
                .filter(id -> !id.getPath().startsWith("nether") && !id.getPath().equals("end_city")
                        && !id.getPath().equals("bastion_remnant") && !id.getPath().equals("fortress"))
                .toList();
        toggleBar = new StructureToggleBar(230, 20, width - 230 - 4, ids, session.enabledStructures(), () -> {
            config.enabledStructures = session.enabledStructures().stream().map(Identifier::toString).sorted().toList();
            SeedScoutClient.saveConfig();
        }, this::selectStructure);
    }

    void buildRightPanel() {
        int px = width - RIGHT_PANEL_WIDTH + 6;
        int pw = RIGHT_PANEL_WIDTH - 12;
        int y = TOP_BAR_HEIGHT + 6;
        closePicker();
        if (structureButton != null) removeWidget(structureButton);
        if (radiusButton != null) removeWidget(radiusButton);
        if (searchButton != null) removeWidget(searchButton);
        if (resultsList != null) removeWidget(resultsList);
        if (centerButton != null) removeWidget(centerButton);
        if (clearButton != null) removeWidget(clearButton);
        if (beamButton != null) removeWidget(beamButton);

        List<Identifier> ids = session == null ? List.of(lastStructure) : session.world().allStructures().stream()
                .map(SeedWorld::idOf).toList();
        if (!ids.contains(lastStructure)) lastStructure = ids.get(0);
        structureButton = Button.builder(structureLabel(), b -> togglePicker(ids))
                .bounds(px, y, pw, 20).build();
        addRenderableWidget(structureButton);
        y += 24;

        List<Integer> radii = List.of(2000, 5000, 10000, 50000);
        int radius = radii.contains(config.lastRadius) ? config.lastRadius : 5000;
        radiusButton = CycleButton
                .<Integer>builder(r -> Component.translatable("seedscout.screen.radius_value", r), radius)
                .withValues(radii)
                .create(px, y, pw, 20, Component.translatable("seedscout.screen.radius"), (b, v) -> {
                    config.lastRadius = v;
                    SeedScoutClient.saveConfig();
                });
        addRenderableWidget(radiusButton);
        y += 24;

        searchButton = Button.builder(
                        Component.translatable("seedscout.screen.search"), b -> runSearch())
                .bounds(px, y, pw, 20).build();
        searchButton.active = session != null && !searching;
        addRenderableWidget(searchButton);
        y += 24;

        resultsList = new ResultsListWidget(minecraft, pw, Math.max(20, height - y - 54), y, this::pickResult);
        resultsList.setX(px);
        if (searching) {
            resultsList.setStatus(Component.translatable("seedscout.screen.searching"));
        } else {
            resultsList.setResults(lastResults);
        }
        addRenderableWidget(resultsList);

        int bottomY = height - 26;
        beamButton = CycleButton.onOffBuilder(config.showBeam)
                .create(px, bottomY, pw / 2 - 2, 20, Component.translatable("seedscout.screen.beam"), (b, v) -> {
                    config.showBeam = v;
                    SeedScoutClient.saveConfig();
                });
        addRenderableWidget(beamButton);
        centerButton = Button.builder(
                        Component.translatable("seedscout.screen.center"), b -> centerOnPlayer())
                .bounds(px, height - 50, pw, 20).build();
        addRenderableWidget(centerButton);
        clearButton = Button.builder(
                        Component.translatable("seedscout.screen.clear_waypoint"), b -> {
                            WaypointState.clear();
                            b.active = false;
                        })
                .bounds(px + pw / 2 + 2, bottomY, pw / 2 - 2, 20).build();
        clearButton.active = WaypointState.get().isPresent();
        addRenderableWidget(clearButton);
    }

    private Component structureLabel() {
        return Component.literal(StructureIcons.displayName(lastStructure));
    }

    private void selectStructure(Identifier id) {
        lastStructure = id;
        if (structureButton != null) structureButton.setMessage(structureLabel());
    }

    private void togglePicker(List<Identifier> ids) {
        if (picker != null) {
            closePicker();
            return;
        }
        int w = 220;
        int x = width - RIGHT_PANEL_WIDTH - w - 8;
        int y = TOP_BAR_HEIGHT + 6;
        int h = Math.min(320, height - y - 10);
        pickerFilter = new EditBox(font, x, y, w, 18, Component.translatable("seedscout.screen.filter"));
        pickerFilter.setHint(Component.translatable("seedscout.screen.filter"));
        pickerFilter.setMaxLength(32);
        picker = new StructurePickerWidget(minecraft, x, y + 22, w, h - 22, ids, id -> {
            selectStructure(id);
            closePicker();
        });
        pickerFilter.setResponder(text -> picker.filter(text));
        addRenderableWidget(pickerFilter);
        addRenderableWidget(picker);
        setFocused(pickerFilter);
        pickerFilter.setFocused(true);
    }

    private void closePicker() {
        if (picker != null) removeWidget(picker);
        if (pickerFilter != null) removeWidget(pickerFilter);
        picker = null;
        pickerFilter = null;
    }

    private boolean pickerContains(double mx, double my) {
        if (picker == null) return false;
        int left = pickerFilter.getX() - 4;
        int top = pickerFilter.getY() - 4;
        int right = pickerFilter.getX() + pickerFilter.getWidth() + 4;
        int bottom = picker.getY() + picker.getHeight() + 4;
        return mx >= left && mx < right && my >= top && my < bottom;
    }

    private void runSearch() {
        if (session == null) return;
        Identifier structureId = lastStructure;
        int radiusBlocks = radiusButton.getValue();
        MapSession current = session;
        searching = true;
        lastResults = List.of();
        resultsList.setStatus(Component.translatable("seedscout.screen.searching"));
        searchButton.active = false;
        double cx = viewport.centerX;
        double cz = viewport.centerZ;
        ChunkPos center = new ChunkPos((int) Math.floor(cx) >> 4, (int) Math.floor(cz) >> 4);
        MapWorker.submit(() -> {
            List<StructureHit> hits;
            try {
                hits = current.world().findStructures(current.world().structure(structureId), center, radiusBlocks / 16, 20);
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.error("Search failed", t);
                minecraft.execute(() -> {
                    if (session != current) return;
                    searching = false;
                    if (minecraft.gui.screen() instanceof SeedScoutScreen s) {
                        s.resultsList.setStatus(Component.translatable("seedscout.screen.search_failed"));
                        s.searchButton.active = true;
                    }
                });
                return;
            }
            minecraft.execute(() -> {
                if (session != current) return;
                searching = false;
                lastResults = hits;
                if (minecraft.gui.screen() instanceof SeedScoutScreen s) {
                    s.resultsList.setResults(hits);
                    s.searchButton.active = true;
                }
            });
        });
    }

    protected void pickResult(StructureHit hit) {
        Identifier id = SeedWorld.idOf(hit.structure());
        WaypointState.set(new Waypoint(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), id));
        onClose();
    }

    protected void centerOnPlayer() {
        if (minecraft.player != null) {
            viewport.centerX = minecraft.player.getX();
            viewport.centerZ = minecraft.player.getZ();
            viewportInitialized = true;
        }
    }

    private String currentSeedText() {
        if (minecraft.getSingleplayerServer() != null) {
            return Long.toString(minecraft.getSingleplayerServer().overworld().getSeed());
        }
        return SeedSource.storageKey(minecraft).flatMap(config::seedFor).orElse("");
    }

    private void updateSeedFieldColor(String text) {
        seedField.setTextColor(SeedParser.parse(text).isPresent() ? 0xFFFFFF : 0xFF5555);
    }

    private void applySeed() {
        OptionalLong parsed = SeedParser.parse(seedField.getValue());
        if (parsed.isEmpty()) {
            updateSeedFieldColor(seedField.getValue());
            return;
        }
        SeedSource.storageKey(minecraft).ifPresent(key -> {
            config.putSeed(key, seedField.getValue().trim());
            SeedScoutClient.saveConfig();
        });
        long parsedSeed = parsed.getAsLong();
        lastAppliedSeed = parsedSeed;
        if (session != null && session.seed() == parsedSeed) {
            return;
        }
        errorSeed = null;
        sessionError = null;
        ensureSession(parsedSeed);
    }

    private static void clearSession() {
        if (session != null) session.close();
        session = null;
        sessionError = null;
        errorSeed = null;
        sessionLoading = false;
        loadingSeed = null;
        pendingSeed = null;
        viewportInitialized = false;
        lastResults = List.of();
        searching = false;
    }

    public static void resetForNewWorld() {
        clearSession();
        lastResults = List.of();
        searching = false;
        lastAppliedSeed = null;
        viewportInitialized = false;
    }

    protected void ensureSession(long seed) {
        if (session != null && session.seed() == seed) return;
        if (errorSeed != null) {
            if (errorSeed == seed) return;
            errorSeed = null;
            sessionError = null;
        }
        if (sessionLoading) {
            if (loadingSeed != null && loadingSeed == seed) return;
            pendingSeed = seed;
            return;
        }
        sessionLoading = true;
        loadingSeed = seed;
        MapWorker.submit(() -> {
            try {
                MapSession fresh = MapSession.open(seed, minecraft.getTextureManager());
                minecraft.execute(() -> {
                    boolean seedChanged = session == null || session.seed() != seed;
                    if (session != null) session.close();
                    session = fresh;
                    lastResults = List.of();
                    searching = false;
                    if (minecraft.gui.screen() instanceof SeedScoutScreen screen) {
                        screen.rebuildToggleBar();
                        screen.buildRightPanel();
                    }
                    sessionLoading = false;
                    loadingSeed = null;
                    if (seedChanged) viewportInitialized = false;
                    takePendingExcept(seed);
                });
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.error("Could not build worldgen for seed {}", seed, t);
                minecraft.execute(() -> {
                    if (session != null) {
                        session.close();
                        session = null;
                    }
                    sessionError = Component.translatable("seedscout.screen.unsupported").getString();
                    errorSeed = seed;
                    lastResults = List.of();
                    searching = false;
                    if (minecraft.gui.screen() instanceof SeedScoutScreen screen) {
                        screen.buildRightPanel();
                    }
                    sessionLoading = false;
                    loadingSeed = null;
                    takePendingExcept(seed);
                });
            }
        });
    }

    private void takePendingExcept(long justHandledSeed) {
        Long next = pendingSeed;
        pendingSeed = null;
        if (next != null && next != justHandledSeed) {
            ensureSession(next);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        renderMap(context);
        renderStructureIcons(context);
        context.fill(0, 0, width, TOP_BAR_HEIGHT, PANEL);
        context.fill(width - RIGHT_PANEL_WIDTH, TOP_BAR_HEIGHT, width, height, PANEL);
        context.text(font, title, 6, 6, TEXT);
        if (toggleBar != null) toggleBar.render(context, font, mouseX, mouseY);
        if (picker != null) {
            context.fill(pickerFilter.getX() - 4, pickerFilter.getY() - 4, pickerFilter.getX() + pickerFilter.getWidth() + 4,
                    picker.getY() + picker.getHeight() + 4, 0xFF0B0E12);
            context.fill(pickerFilter.getX() - 3, pickerFilter.getY() - 3, pickerFilter.getX() + pickerFilter.getWidth() + 3,
                    picker.getY() + picker.getHeight() + 3, PANEL);
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        renderOverlays(context, mouseX, mouseY);
    }

    private void renderMap(GuiGraphicsExtractor context) {
        if (session == null) return;
        int lod = viewport.lod();
        List<TileKey> keys = TileKey.covering(lod, viewport.minBlockX(), viewport.minBlockZ(),
                viewport.maxBlockX(), viewport.maxBlockZ());
        session.tiles().request(keys, viewport.centerX, viewport.centerZ, session.biomeColors());

        context.enableScissor(viewport.left, viewport.top, viewport.left + viewport.width, viewport.top + viewport.height);
        int span = TileKey.tileSpanBlocks(lod);
        for (TileKey key : keys) {
            int sx = (int) Math.floor(viewport.worldToScreenX(key.originX()));
            int sy = (int) Math.floor(viewport.worldToScreenZ(key.originZ()));
            int ex = (int) Math.floor(viewport.worldToScreenX(key.originX() + span));
            int ey = (int) Math.floor(viewport.worldToScreenZ(key.originZ() + span));
            int w = Math.max(1, ex - sx);
            int h = Math.max(1, ey - sy);
            var texture = session.tiles().textureFor(key);
            if (texture.isPresent()) {
                context.blit(RenderPipelines.GUI_TEXTURED, texture.get(), sx, sy, 0, 0, w, h,
                        TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, TileKey.TILE_PIXELS);
            } else {
                context.fill(sx, sy, sx + w, sy + h, session.tiles().isFailed(key) ? FAILED : PLACEHOLDER);
            }
        }
        context.disableScissor();
    }

    private void renderStructureIcons(GuiGraphicsExtractor context) {
        if (session == null) return;
        List<RegionHit> hits = session.structures().query(
                viewport.minBlockX(), viewport.minBlockZ(), viewport.maxBlockX(), viewport.maxBlockZ(),
                session.enabledStructures());
        context.enableScissor(viewport.left, viewport.top, viewport.left + viewport.width, viewport.top + viewport.height);
        boolean dots = viewport.lod() >= 2;
        for (RegionHit hit : hits) {
            int sx = (int) Math.round(viewport.worldToScreenX(hit.blockX()));
            int sy = (int) Math.round(viewport.worldToScreenZ(hit.blockZ()));
            Identifier id = SeedWorld.idOf(hit.structure());
            if (dots) {
                context.fill(sx - 2, sy - 2, sx + 2, sy + 2, StructureIcons.colorFor(id));
            } else {
                StructureIcons.drawIcon(context, id, sx - 8, sy - 8);
            }
        }
        for (StructureHit hit : lastResults) {
            int sx = (int) Math.round(viewport.worldToScreenX(hit.blockX()));
            int sy = (int) Math.round(viewport.worldToScreenZ(hit.blockZ()));
            int r = 11;
            context.fill(sx - r, sy - r, sx + r, sy - r + 2, 0xFFFFD700);
            context.fill(sx - r, sy + r - 2, sx + r, sy + r, 0xFFFFD700);
            context.fill(sx - r, sy - r, sx - r + 2, sy + r, 0xFFFFD700);
            context.fill(sx + r - 2, sy - r, sx + r, sy + r, 0xFFFFD700);
            Identifier resultId = SeedWorld.idOf(hit.structure());
            if (viewport.lod() >= 2) {
                context.fill(sx - 2, sy - 2, sx + 2, sy + 2, StructureIcons.colorFor(resultId));
            } else {
                StructureIcons.drawIcon(context, resultId, sx - 8, sy - 8);
            }
        }
        WaypointState.get().ifPresent(wp -> {
            int sx = (int) Math.round(viewport.worldToScreenX(wp.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(wp.z()));
            context.fill(sx - 1, sy - 10, sx + 1, sy + 10, 0xFFFF4040);
            context.fill(sx - 10, sy - 1, sx + 10, sy + 1, 0xFFFF4040);
        });
        if (minecraft.player != null) {
            int sx = (int) Math.round(viewport.worldToScreenX(minecraft.player.getX()));
            int sy = (int) Math.round(viewport.worldToScreenZ(minecraft.player.getZ()));
            var matrices = context.pose();
            matrices.pushMatrix();
            matrices.translate(sx, sy);

            matrices.rotate((float) Math.toRadians(minecraft.player.getYRot() + 180f));
            context.blit(RenderPipelines.GUI_TEXTURED, StructureIcons.ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
            matrices.popMatrix();
        }
        context.disableScissor();
    }

    protected void renderOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int mapCenterX = viewport.left + viewport.width / 2;
        int mapCenterY = viewport.top + viewport.height / 2;
        if (sessionError != null) {
            context.centeredText(font, sessionError, mapCenterX, mapCenterY, TEXT);
        } else if (session == null && !sessionLoading) {
            context.centeredText(font, Component.translatable("seedscout.screen.no_seed"), mapCenterX, mapCenterY, TEXT);
        } else if (sessionLoading) {
            context.centeredText(font, Component.translatable("seedscout.screen.loading"), mapCenterX, mapCenterY, TEXT);
        }
        if (viewport.contains(mouseX, mouseY)) {
            int bx = (int) Math.floor(viewport.screenToWorldX(mouseX));
            int bz = (int) Math.floor(viewport.screenToWorldZ(mouseY));
            context.text(font, "X " + bx + "  Z " + bz + "  LOD " + viewport.lod(),
                    viewport.left + 4, viewport.top + viewport.height - 12, TEXT);
        }
        int pending = session == null ? 0 : session.tiles().pendingCount() + session.structures().pendingCount();
        if (pending > 0) {
            char spinner = "|/-\\".charAt((int) ((System.currentTimeMillis() / 120) % 4));
            context.text(font, spinner + " " + pending, viewport.left + viewport.width - 40,
                    viewport.top + 4, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (picker != null && !pickerContains(click.x(), click.y())) {
            closePicker();
            return true;
        }
        if (super.mouseClicked(click, doubled)) return true;
        if (toggleBar != null && toggleBar.mouseClicked(click.x(), click.y(), click.button())) return true;
        if (session != null && click.button() == 0 && viewport.contains(click.x(), click.y()) && viewport.lod() < 2) {
            List<RegionHit> hits = session.structures().query(
                    viewport.minBlockX(), viewport.minBlockZ(), viewport.maxBlockX(), viewport.maxBlockZ(),
                    session.enabledStructures());
            for (RegionHit hit : hits) {
                double sx = viewport.worldToScreenX(hit.blockX());
                double sy = viewport.worldToScreenZ(hit.blockZ());
                if (Math.abs(click.x() - sx) <= 8 && Math.abs(click.y() - sy) <= 8) {
                    Identifier id = SeedWorld.idOf(hit.structure());
                    WaypointState.set(new Waypoint(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), id));
                    onClose();
                    return true;
                }
            }
        }
        if (click.button() == 0 && viewport.contains(click.x(), click.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging && click.button() == 0) {
            viewport.pan(offsetX, offsetY);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (picker != null && pickerContains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (viewport.contains(mouseX, mouseY) && verticalAmount != 0) {
            viewport.zoomAt(mouseX, mouseY, verticalAmount > 0 ? 0.8 : 1.25);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (picker != null && input.isEscape()) {
            closePicker();
            return true;
        }
        boolean typing = (seedField != null && seedField.isFocused()) || (pickerFilter != null && pickerFilter.isFocused());
        if (!typing && SeedScoutClient.OPEN_MAP.matches(input)) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }
}
