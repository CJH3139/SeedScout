package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.config.SeedParser;
import com.seedscout.config.SeedScoutConfig;
import com.seedscout.map.MapViewport;
import com.seedscout.map.MapWorker;
import com.seedscout.map.TileKey;
import com.seedscout.worldgen.Dimension;
import com.seedscout.worldgen.RegionHit;
import com.seedscout.worldgen.SeedSource;
import com.seedscout.worldgen.SeedWorld;
import com.seedscout.waypoint.Waypoint;
import com.seedscout.waypoint.WaypointState;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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
    protected static final int PANEL_EDGE = 0xFF0B0E12;
    protected static final int SEPARATOR = 0xFF2E3640;
    protected static final int TEXT = 0xFFE0E0E0;
    protected static final int MUTED = 0xFF7C8A96;
    protected static final int PLACEHOLDER = 0xFF2A2A2A;
    protected static final int FAILED = 0xFF5A1E1E;
    protected static final int PANEL_WIDTH = 190;
    private static final int PAD = 6;
    private static final int SEG_H = 14;
    private static final int BTN_H = 16;
    private static final int GAP = 3;
    private static final List<Integer> RADII = List.of(2000, 5000, 10000, 50000);

    protected static MapSession session;
    private static boolean sessionLoading;
    private static String sessionError;
    private static SessionKey loadingKey;
    private static SessionKey errorKey;
    private static SessionKey pendingKey;
    protected static final MapViewport viewport = new MapViewport();
    private static boolean viewportInitialized;
    private static Identifier lastStructure = Identifier.withDefaultNamespace("village_plains");
    private static Identifier lastBiome = Identifier.withDefaultNamespace("plains");
    private static List<SearchResult> lastResults = List.of();
    private static boolean biomeMode;
    private static boolean searching;
    private static Long lastAppliedSeed;
    private static Dimension currentDimension = Dimension.OVERWORLD;

    protected final SeedScoutConfig config = SeedScoutClient.config();
    private boolean dragging;
    private EditBox seedField;
    private Button applyButton;
    private StructureGrid grid;
    private Button targetButton;
    private StructurePickerWidget picker;
    private EditBox pickerFilter;
    private Button searchButton;
    private ResultsListWidget resultsList;
    private final List<SegmentedControl> controls = new ArrayList<>();
    private int gridTop;
    private int hintY;
    private int searchCaptionY;
    private int statusY;

    private record SessionKey(long seed, Dimension dimension) {}

    public SeedScoutScreen(Minecraft client) {
        super(Component.translatable("seedscout.screen.title"));
        if (client.player != null) {
            currentDimension = Dimension.fromLevel(client.player.level().dimension());
        }
    }

    private int panelX() {
        return width - PANEL_WIDTH + PAD;
    }

    private int panelW() {
        return PANEL_WIDTH - 2 * PAD;
    }

    @Override
    protected void init() {
        viewport.left = 0;
        viewport.top = 0;
        viewport.width = width - PANEL_WIDTH;
        viewport.height = height;
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
        buildPanel();
    }

    void buildPanel() {
        int px = panelX();
        int pw = panelW();
        int y = PAD;
        closePicker();
        for (AbstractWidget w : new AbstractWidget[]{seedField, applyButton, targetButton, searchButton, resultsList}) {
            if (w != null) removeWidget(w);
        }
        controls.clear();

        y += 12;
        boolean singleplayer = minecraft.getSingleplayerServer() != null;
        seedField = new EditBox(font, px, y, pw - 40, BTN_H, Component.translatable("seedscout.screen.seed_placeholder"));
        seedField.setMaxLength(64);
        seedField.setHint(Component.translatable("seedscout.screen.seed_placeholder"));
        seedField.setResponder(this::updateSeedFieldColor);
        seedField.setValue(currentSeedText());
        seedField.setEditable(!singleplayer);
        addRenderableWidget(seedField);
        applyButton = Button.builder(Component.translatable("seedscout.screen.apply"), b -> applySeed())
                .bounds(px + pw - 36, y, 36, BTN_H).build();
        applyButton.active = !singleplayer;
        addRenderableWidget(applyButton);
        y += BTN_H + GAP + 1;

        List<SegmentedControl.Segment> dims = new ArrayList<>();
        for (Dimension d : Dimension.values()) {
            dims.add(SegmentedControl.Segment.of(d.displayName(), () -> currentDimension == d, () -> switchDimension(d)));
        }
        controls.add(new SegmentedControl(px, y, pw, SEG_H, dims));
        y += SEG_H + GAP + 4;

        List<Identifier> structureIds = session == null ? List.of(lastStructure) : session.world().allStructures().stream()
                .map(SeedWorld::idOf).toList();
        if (!structureIds.contains(lastStructure)) lastStructure = structureIds.get(0);
        List<Identifier> biomeIds = session == null ? List.of(lastBiome) : session.world().allBiomes().stream()
                .map(SeedWorld::idOf).toList();
        if (!biomeIds.contains(lastBiome)) lastBiome = biomeIds.get(0);

        gridTop = y + 10;
        if (session != null) {
            grid = new StructureGrid(px, gridTop, pw / StructureGrid.CELL, structureIds, session.enabledStructures(),
                    this::saveToggles, id -> {
                        setBiomeMode(false);
                        selectTarget(id);
                    }, () -> biomeMode ? null : lastStructure);
            controls.add(new SegmentedControl(px + pw - 60, y - 1, 60, 10, List.of(
                    SegmentedControl.Segment.of(Component.translatable("seedscout.screen.all"), () -> false, () -> grid.setAll(true)),
                    SegmentedControl.Segment.of(Component.translatable("seedscout.screen.none"), () -> false, () -> grid.setAll(false)))));
            y = gridTop + grid.height() + 2;
            hintY = y;
            y += 10;
        } else {
            grid = null;
            hintY = -1;
            y = gridTop;
        }
        y += 4;

        searchCaptionY = y;
        y += 10;
        controls.add(new SegmentedControl(px, y, pw, SEG_H, List.of(
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.mode_structure"), () -> !biomeMode, () -> setBiomeMode(false)),
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.mode_biome"), () -> biomeMode, () -> setBiomeMode(true)))));
        y += SEG_H + GAP;

        targetButton = Button.builder(targetLabel(), b -> togglePicker(biomeMode ? biomeIds : structureIds))
                .bounds(px, y, pw, BTN_H).build();
        addRenderableWidget(targetButton);
        y += BTN_H + GAP;

        List<SegmentedControl.Segment> radii = new ArrayList<>();
        for (int r : RADII) {
            radii.add(SegmentedControl.Segment.of(Component.translatable("seedscout.screen.radius_short", r / 1000),
                    () -> config.lastRadius == r, () -> {
                        config.lastRadius = r;
                        SeedScoutClient.saveConfig();
                    }));
        }
        if (!RADII.contains(config.lastRadius)) config.lastRadius = 5000;
        controls.add(new SegmentedControl(px, y, pw, SEG_H, radii));
        y += SEG_H + GAP;

        searchButton = Button.builder(Component.translatable("seedscout.screen.search"), b -> runSearch())
                .bounds(px, y, pw, BTN_H).build();
        searchButton.active = session != null && !searching;
        addRenderableWidget(searchButton);
        y += BTN_H + GAP + 2;

        int bottomRow = height - PAD - SEG_H;
        statusY = bottomRow - 12;
        int listBottom = statusY - 4;
        resultsList = new ResultsListWidget(minecraft, pw, Math.max(20, listBottom - y), y);
        resultsList.setX(px);
        addRenderableWidget(resultsList);
        refreshList();

        controls.add(new SegmentedControl(px, bottomRow, pw, SEG_H, List.of(
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.beam"), () -> config.showBeam, () -> {
                    config.showBeam = !config.showBeam;
                    SeedScoutClient.saveConfig();
                }),
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.slime"), () -> config.showSlimeChunks, () -> {
                    config.showSlimeChunks = !config.showSlimeChunks;
                    SeedScoutClient.saveConfig();
                }),
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.center"), () -> false, this::centerOnPlayer),
                SegmentedControl.Segment.of(Component.translatable("seedscout.screen.clear_waypoint"), () -> false, WaypointState::clear))));
    }

    private void saveToggles() {
        if (session == null) return;
        config.enabledStructures = session.enabledStructures().stream().map(Identifier::toString).sorted().toList();
        SeedScoutClient.saveConfig();
    }

    private void setBiomeMode(boolean biomes) {
        if (biomeMode == biomes) return;
        biomeMode = biomes;
        closePicker();
        if (targetButton != null) targetButton.setMessage(targetLabel());
    }

    private void refreshList() {
        if (resultsList == null) return;
        if (searching) {
            resultsList.setStatus(Component.translatable("seedscout.screen.searching"));
            return;
        }
        List<ResultsListWidget.Row> rows = new ArrayList<>();
        for (SearchResult r : lastResults) {
            String sub = r.x() + ", " + r.z() + "   " + Math.round(r.distance()) + " m";
            rows.add(new ResultsListWidget.Row(r.name(), sub, r.structureId(), r.color(), () -> pickResult(r), null));
        }
        resultsList.setRows(rows, Component.translatable("seedscout.screen.no_results"));
    }

    private Component targetLabel() {
        return Component.literal(StructureIcons.displayName(biomeMode ? lastBiome : lastStructure));
    }

    private void selectTarget(Identifier id) {
        if (biomeMode) {
            lastBiome = id;
        } else {
            lastStructure = id;
        }
        if (targetButton != null) targetButton.setMessage(targetLabel());
    }

    private void togglePicker(List<Identifier> ids) {
        if (picker != null) {
            closePicker();
            return;
        }
        int w = 200;
        int x = width - PANEL_WIDTH - w - 8;
        int y = PAD;
        int h = Math.min(300, height - y - 10);
        pickerFilter = new EditBox(font, x, y, w, BTN_H, Component.translatable("seedscout.screen.filter"));
        pickerFilter.setHint(Component.translatable("seedscout.screen.filter"));
        pickerFilter.setMaxLength(32);
        picker = new StructurePickerWidget(minecraft, x, y + BTN_H + GAP, w, h - BTN_H - GAP, ids, biomeMode, id -> {
            selectTarget(id);
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
        boolean biomes = biomeMode;
        Identifier targetId = biomes ? lastBiome : lastStructure;
        int radiusBlocks = config.lastRadius;
        MapSession current = session;
        searching = true;
        lastResults = List.of();
        refreshList();
        searchButton.active = false;
        int cx = (int) Math.floor(viewport.centerX);
        int cz = (int) Math.floor(viewport.centerZ);
        ChunkPos center = new ChunkPos(cx >> 4, cz >> 4);
        MapWorker.submit(() -> {
            List<SearchResult> results;
            try {
                if (biomes) {
                    results = current.world().findBiome(current.world().biome(targetId), cx, cz, radiusBlocks, 20)
                            .stream().map(SearchResult::of).toList();
                } else {
                    results = current.world().findStructures(current.world().structure(targetId), center, radiusBlocks / 16, 20)
                            .stream().map(SearchResult::of).toList();
                }
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
                lastResults = results;
                if (minecraft.gui.screen() instanceof SeedScoutScreen s) {
                    s.refreshList();
                    s.searchButton.active = true;
                }
            });
        });
    }

    protected void pickResult(SearchResult result) {
        WaypointState.set(new Waypoint(result.name(), result.x(), result.z(), result.structureId(), session.dimension().levelId()));
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
        if (session != null && session.seed() == parsedSeed && session.dimension() == currentDimension) {
            return;
        }
        errorKey = null;
        sessionError = null;
        ensureSession(parsedSeed);
    }

    private static void clearSession() {
        if (session != null) session.close();
        session = null;
        sessionError = null;
        errorKey = null;
        sessionLoading = false;
        loadingKey = null;
        pendingKey = null;
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
        SessionKey key = new SessionKey(seed, currentDimension);
        if (session != null && session.seed() == seed && session.dimension() == key.dimension()) return;
        if (errorKey != null) {
            if (errorKey.equals(key)) return;
            errorKey = null;
            sessionError = null;
        }
        if (sessionLoading) {
            if (key.equals(loadingKey)) return;
            pendingKey = key;
            return;
        }
        sessionLoading = true;
        loadingKey = key;
        MapWorker.submit(() -> {
            try {
                MapSession fresh = MapSession.open(seed, key.dimension(), minecraft.getTextureManager());
                minecraft.execute(() -> {
                    boolean seedChanged = session == null || session.seed() != seed || session.dimension() != key.dimension();
                    if (session != null) session.close();
                    session = fresh;
                    lastResults = List.of();
                    searching = false;
                    if (minecraft.gui.screen() instanceof SeedScoutScreen screen) {
                        screen.buildPanel();
                    }
                    sessionLoading = false;
                    loadingKey = null;
                    if (seedChanged) viewportInitialized = false;
                    takePendingExcept(key);
                });
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.error("Could not build worldgen for seed {}", seed, t);
                minecraft.execute(() -> {
                    if (session != null) {
                        session.close();
                        session = null;
                    }
                    sessionError = Component.translatable("seedscout.screen.unsupported").getString();
                    errorKey = key;
                    lastResults = List.of();
                    searching = false;
                    if (minecraft.gui.screen() instanceof SeedScoutScreen screen) {
                        screen.buildPanel();
                    }
                    sessionLoading = false;
                    loadingKey = null;
                    takePendingExcept(key);
                });
            }
        });
    }

    private void takePendingExcept(SessionKey justHandled) {
        SessionKey next = pendingKey;
        pendingKey = null;
        if (next != null && !next.equals(justHandled)) {
            currentDimension = next.dimension();
            ensureSession(next.seed());
        }
    }

    private void switchDimension(Dimension dimension) {
        if (dimension == currentDimension) return;
        currentDimension = dimension;
        if (minecraft.player != null && Dimension.fromLevel(minecraft.player.level().dimension()) == dimension) {
            centerOnPlayer();
        } else {
            viewport.centerX = 0;
            viewport.centerZ = 0;
        }
        Long seed = session != null ? Long.valueOf(session.seed()) : lastAppliedSeed;
        OptionalLong resolved = SeedSource.resolve(minecraft, config);
        if (resolved.isPresent()) seed = resolved.getAsLong();
        if (seed != null) ensureSession(seed);
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
        renderMapOverlays(context, mouseX, mouseY);
        int px = panelX();
        int pw = panelW();
        context.fill(width - PANEL_WIDTH, 0, width, height, PANEL);
        context.fill(width - PANEL_WIDTH, 0, width - PANEL_WIDTH + 1, height, PANEL_EDGE);
        context.text(font, title, px, PAD + 1, TEXT, false);
        if (grid != null) {
            context.text(font, Component.translatable("seedscout.screen.structures"), px, gridTop - 10, MUTED, false);
            grid.render(context, font, mouseX, mouseY);
            context.text(font, Component.translatable("seedscout.screen.grid_hint"), px, hintY, MUTED, false);
        }
        context.fill(px, searchCaptionY - 3, px + pw, searchCaptionY - 2, SEPARATOR);
        context.text(font, Component.translatable("seedscout.screen.search_caption"), px, searchCaptionY, MUTED, false);
        context.fill(px, statusY - 3, px + pw, statusY - 2, SEPARATOR);
        renderStatus(context, mouseX, mouseY);
        for (SegmentedControl control : controls) control.render(context, font, mouseX, mouseY);
        if (picker != null) {
            context.fill(pickerFilter.getX() - 4, pickerFilter.getY() - 4, pickerFilter.getX() + pickerFilter.getWidth() + 4,
                    picker.getY() + picker.getHeight() + 4, PANEL_EDGE);
            context.fill(pickerFilter.getX() - 3, pickerFilter.getY() - 3, pickerFilter.getX() + pickerFilter.getWidth() + 3,
                    picker.getY() + picker.getHeight() + 3, PANEL);
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    private void renderStatus(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int px = panelX();
        String line;
        if (viewport.contains(mouseX, mouseY)) {
            int bx = (int) Math.floor(viewport.screenToWorldX(mouseX));
            int bz = (int) Math.floor(viewport.screenToWorldZ(mouseY));
            line = Component.translatable("seedscout.screen.status", bx, bz, TileKey.blocksPerPixel(viewport.lod())).getString();
        } else {
            line = Component.translatable("seedscout.screen.status", (int) viewport.centerX, (int) viewport.centerZ,
                    TileKey.blocksPerPixel(viewport.lod())).getString();
        }
        int pending = session == null ? 0 : session.tiles().pendingCount() + session.structures().pendingCount();
        if (pending > 0) {
            char spinner = "|/-\\".charAt((int) ((System.currentTimeMillis() / 120) % 4));
            line = spinner + " " + Component.translatable("seedscout.screen.pending", pending).getString();
        }
        context.text(font, line, px, statusY, MUTED, false);
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
            } else if (!drawCoarseFallback(context, key, sx, sy, w, h)) {
                context.fill(sx, sy, sx + w, sy + h, session.tiles().isFailed(key) ? FAILED : PLACEHOLDER);
            }
        }
        if (config.showSlimeChunks && session.dimension() == Dimension.OVERWORLD && viewport.scale <= 2.0) {
            int minCx = viewport.minBlockX() >> 4;
            int maxCx = viewport.maxBlockX() >> 4;
            int minCz = viewport.minBlockZ() >> 4;
            int maxCz = viewport.maxBlockZ() >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    if (!session.isSlimeChunk(cx, cz)) continue;
                    int sx = (int) Math.floor(viewport.worldToScreenX(cx << 4));
                    int sy = (int) Math.floor(viewport.worldToScreenZ(cz << 4));
                    int ex = (int) Math.floor(viewport.worldToScreenX((cx << 4) + 16));
                    int ey = (int) Math.floor(viewport.worldToScreenZ((cz << 4) + 16));
                    context.fill(sx, sy, ex, ey, 0x7030FF30);
                }
            }
        }
        context.disableScissor();
    }

    private boolean drawCoarseFallback(GuiGraphicsExtractor context, TileKey key, int sx, int sy, int w, int h) {
        for (int lod = key.lod() + 1; lod < TileKey.LOD_COUNT; lod++) {
            TileKey coarse = TileKey.coarserCovering(key, lod);
            var texture = session.tiles().textureFor(coarse);
            if (texture.isEmpty()) continue;
            int ratio = TileKey.tileSpanBlocks(lod) / TileKey.tileSpanBlocks(key.lod());
            float region = (float) TileKey.TILE_PIXELS / ratio;
            float u = (float) (key.originX() - coarse.originX()) / TileKey.tileSpanBlocks(lod) * TileKey.TILE_PIXELS;
            float v = (float) (key.originZ() - coarse.originZ()) / TileKey.tileSpanBlocks(lod) * TileKey.TILE_PIXELS;
            context.blit(RenderPipelines.GUI_TEXTURED, texture.get(), sx, sy, u, v, w, h,
                    Math.max(1, Math.round(region)), Math.max(1, Math.round(region)), TileKey.TILE_PIXELS, TileKey.TILE_PIXELS);
            return true;
        }
        return false;
    }

    private void renderStructureIcons(GuiGraphicsExtractor context) {
        if (session == null) return;
        List<RegionHit> hits = session.structures().query(
                viewport.minBlockX(), viewport.minBlockZ(), viewport.maxBlockX(), viewport.maxBlockZ(),
                session.enabledStructures());
        context.enableScissor(viewport.left, viewport.top, viewport.left + viewport.width, viewport.top + viewport.height);
        boolean dots = TileKey.blocksPerPixel(viewport.lod()) >= 64;
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
        for (SearchResult hit : lastResults) {
            int sx = (int) Math.round(viewport.worldToScreenX(hit.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(hit.z()));
            int r = 11;
            context.fill(sx - r, sy - r, sx + r, sy - r + 2, 0xFFFFD700);
            context.fill(sx - r, sy + r - 2, sx + r, sy + r, 0xFFFFD700);
            context.fill(sx - r, sy - r, sx - r + 2, sy + r, 0xFFFFD700);
            context.fill(sx + r - 2, sy - r, sx + r, sy + r, 0xFFFFD700);
            if (TileKey.blocksPerPixel(viewport.lod()) >= 64 || hit.structureId() == null) {
                context.fill(sx - 3, sy - 3, sx + 3, sy + 3, hit.color());
            } else {
                StructureIcons.drawIcon(context, hit.structureId(), sx - 8, sy - 8);
            }
        }
        if (minecraft.level != null) {
            var respawn = minecraft.level.getRespawnData();
            if (respawn.dimension().identifier().equals(session.dimension().levelId())) {
                int sx = (int) Math.round(viewport.worldToScreenX(respawn.pos().getX()));
                int sy = (int) Math.round(viewport.worldToScreenZ(respawn.pos().getZ()));
                StructureIcons.drawMapSprite(context, "target_point", sx - 8, sy - 8);
            }
        }
        WaypointState.get().ifPresent(wp -> {
            if (!wp.dimension().equals(session.dimension().levelId())) return;
            int sx = (int) Math.round(viewport.worldToScreenX(wp.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(wp.z()));
            context.fill(sx - 1, sy - 10, sx + 1, sy + 10, 0xFFFF4040);
            context.fill(sx - 10, sy - 1, sx + 10, sy + 1, 0xFFFF4040);
        });
        if (minecraft.player != null && Dimension.fromLevel(minecraft.player.level().dimension()) == session.dimension()) {
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

    private void renderMapOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int mapCenterX = viewport.left + viewport.width / 2;
        int mapCenterY = viewport.top + viewport.height / 2;
        if (sessionError != null) {
            context.centeredText(font, sessionError, mapCenterX, mapCenterY, TEXT);
        } else if (session == null && !sessionLoading) {
            context.centeredText(font, Component.translatable("seedscout.screen.no_seed"), mapCenterX, mapCenterY, TEXT);
        } else if (sessionLoading) {
            context.centeredText(font, Component.translatable("seedscout.screen.loading"), mapCenterX, mapCenterY, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (picker != null && !pickerContains(click.x(), click.y())) {
            closePicker();
            return true;
        }
        for (SegmentedControl control : controls) {
            if (control.mouseClicked(click.x(), click.y(), click.button())) return true;
        }
        if (grid != null && grid.mouseClicked(click.x(), click.y(), click.button())) return true;
        if (super.mouseClicked(click, doubled)) return true;
        if (!viewport.contains(click.x(), click.y()) || session == null) {
            return false;
        }
        if (click.button() == 0 && TileKey.blocksPerPixel(viewport.lod()) < 64) {
            List<RegionHit> hits = session.structures().query(
                    viewport.minBlockX(), viewport.minBlockZ(), viewport.maxBlockX(), viewport.maxBlockZ(),
                    session.enabledStructures());
            for (RegionHit hit : hits) {
                double sx = viewport.worldToScreenX(hit.blockX());
                double sy = viewport.worldToScreenZ(hit.blockZ());
                if (Math.abs(click.x() - sx) <= 8 && Math.abs(click.y() - sy) <= 8) {
                    Identifier id = SeedWorld.idOf(hit.structure());
                    WaypointState.set(new Waypoint(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), id, session.dimension().levelId()));
                    onClose();
                    return true;
                }
            }
        }
        if (click.button() == 1) {
            int bx = (int) Math.floor(viewport.screenToWorldX(click.x()));
            int bz = (int) Math.floor(viewport.screenToWorldZ(click.y()));
            String name = Component.translatable("seedscout.waypoint.marker", bx, bz).getString();
            WaypointState.set(new Waypoint(name, bx, bz, null, session.dimension().levelId()));
            return true;
        }
        if (click.button() == 0) {
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
