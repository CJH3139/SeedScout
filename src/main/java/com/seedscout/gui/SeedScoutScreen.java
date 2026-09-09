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
import com.seedscout.worldgen.BiomeHit;
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

    private static SessionKey loadingKey;

    private static SessionKey errorKey;

    private static SessionKey pendingKey;
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
    private static List<SearchResult> lastResults = List.of();
    private static boolean biomeMode;
    private static Identifier lastBiome = Identifier.withDefaultNamespace("plains");
    private static boolean waypointsTab;
    private CycleButton<Boolean> modeButton;
    private Button resultsTabButton;
    private Button waypointsTabButton;
    private CycleButton<Boolean> slimeButton;

    private static boolean searching;

    private static Long lastAppliedSeed;
    private static Dimension currentDimension = Dimension.OVERWORLD;
    private final Button[] dimensionButtons = new Button[Dimension.values().length];

    private record SessionKey(long seed, Dimension dimension) {}

    public SeedScoutScreen(Minecraft client) {
        super(Component.translatable("seedscout.screen.title"));
        if (client.player != null) {
            currentDimension = Dimension.fromLevel(client.player.level().dimension());
        }
    }

    @Override
    protected void init() {
        WaypointState.ensureLoaded(minecraft);
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

        int dx = 226;
        int[] widths = {64, 50, 40};
        for (Dimension d : Dimension.values()) {
            Button b = Button.builder(d.displayName(), btn -> switchDimension(d))
                    .bounds(dx, 20, widths[d.ordinal()], 18).build();
            dimensionButtons[d.ordinal()] = b;
            addRenderableWidget(b);
            dx += widths[d.ordinal()] + 4;
        }
        updateDimensionButtons();

        rebuildToggleBar();
        buildRightPanel();
    }

    void rebuildToggleBar() {
        updateDimensionButtons();
        if (session == null) {
            toggleBar = null;
            return;
        }
        List<Identifier> ids = session.world().allStructures().stream()
                .map(SeedWorld::idOf)
                .toList();
        toggleBar = new StructureToggleBar(392, 20, width - 392 - 4, ids, session.enabledStructures(), () -> {
            config.enabledStructures = session.enabledStructures().stream().map(Identifier::toString).sorted().toList();
            SeedScoutClient.saveConfig();
        }, this::selectStructure);
    }

    void buildRightPanel() {
        int px = width - RIGHT_PANEL_WIDTH + 6;
        int pw = RIGHT_PANEL_WIDTH - 12;
        int y = TOP_BAR_HEIGHT + 6;
        closePicker();
        for (var w : new net.minecraft.client.gui.components.AbstractWidget[]{modeButton, structureButton, radiusButton, searchButton,
                resultsTabButton, waypointsTabButton, resultsList, centerButton, clearButton, beamButton, slimeButton}) {
            if (w != null) removeWidget(w);
        }

        modeButton = CycleButton.<Boolean>builder(b -> Component.translatable(b ? "seedscout.screen.mode_biome" : "seedscout.screen.mode_structure"), biomeMode)
                .withValues(List.of(Boolean.FALSE, Boolean.TRUE))
                .displayOnlyValue()
                .create(px, y, pw, 20, Component.empty(), (b, v) -> {
                    biomeMode = v;
                    closePicker();
                    if (structureButton != null) structureButton.setMessage(targetLabel());
                });
        addRenderableWidget(modeButton);
        y += 24;

        List<Identifier> structureIds = session == null ? List.of(lastStructure) : session.world().allStructures().stream()
                .map(SeedWorld::idOf).toList();
        if (!structureIds.contains(lastStructure)) lastStructure = structureIds.get(0);
        List<Identifier> biomeIds = session == null ? List.of(lastBiome) : session.world().allBiomes().stream()
                .map(SeedWorld::idOf).toList();
        if (!biomeIds.contains(lastBiome)) lastBiome = biomeIds.get(0);
        structureButton = Button.builder(targetLabel(), b -> togglePicker(biomeMode ? biomeIds : structureIds))
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

        resultsTabButton = Button.builder(Component.translatable("seedscout.screen.tab_results"), b -> {
                    waypointsTab = false;
                    refreshList();
                })
                .bounds(px, y, pw / 2 - 2, 18).build();
        waypointsTabButton = Button.builder(Component.translatable("seedscout.screen.tab_waypoints"), b -> {
                    waypointsTab = true;
                    refreshList();
                })
                .bounds(px + pw / 2 + 2, y, pw / 2 - 2, 18).build();
        addRenderableWidget(resultsTabButton);
        addRenderableWidget(waypointsTabButton);
        y += 22;

        resultsList = new ResultsListWidget(minecraft, pw, Math.max(20, height - y - 54), y);
        resultsList.setX(px);
        addRenderableWidget(resultsList);
        refreshList();

        int bottomY = height - 26;
        int third = (pw - 4) / 3;
        beamButton = CycleButton.onOffBuilder(config.showBeam)
                .create(px, bottomY, third, 20, Component.translatable("seedscout.screen.beam"), (b, v) -> {
                    config.showBeam = v;
                    SeedScoutClient.saveConfig();
                });
        addRenderableWidget(beamButton);
        slimeButton = CycleButton.onOffBuilder(config.showSlimeChunks)
                .create(px + third + 2, bottomY, third, 20, Component.translatable("seedscout.screen.slime"), (b, v) -> {
                    config.showSlimeChunks = v;
                    SeedScoutClient.saveConfig();
                });
        addRenderableWidget(slimeButton);
        clearButton = Button.builder(
                        Component.translatable("seedscout.screen.clear_waypoint"), b -> {
                            WaypointState.clear();
                            b.active = false;
                            refreshList();
                        })
                .bounds(px + 2 * (third + 2), bottomY, pw - 2 * (third + 2), 20).build();
        clearButton.active = WaypointState.get().isPresent();
        addRenderableWidget(clearButton);
        centerButton = Button.builder(
                        Component.translatable("seedscout.screen.center"), b -> centerOnPlayer())
                .bounds(px, height - 50, pw, 20).build();
        addRenderableWidget(centerButton);
    }

    private void refreshList() {
        if (resultsList == null) return;
        resultsTabButton.active = waypointsTab;
        waypointsTabButton.active = !waypointsTab;
        if (waypointsTab) {
            List<ResultsListWidget.Row> rows = new java.util.ArrayList<>();
            for (Waypoint w : WaypointState.saved()) {
                boolean active = WaypointState.get().map(w::equals).orElse(false);
                String title = (active ? "> " : "") + w.name();
                String sub = w.x() + ", " + w.z() + "  " + Dimension.fromLevelId(w.dimension()).displayName().getString();
                rows.add(new ResultsListWidget.Row(title, sub, w.structureId(), WaypointState.colorOf(w),
                        () -> {
                            WaypointState.activate(w);
                            onClose();
                        },
                        () -> {
                            WaypointState.remove(w);
                            refreshList();
                            if (clearButton != null) clearButton.active = WaypointState.get().isPresent();
                        }));
            }
            resultsList.setRows(rows, Component.translatable("seedscout.screen.no_waypoints"));
        } else if (searching) {
            resultsList.setStatus(Component.translatable("seedscout.screen.searching"));
        } else {
            List<ResultsListWidget.Row> rows = new java.util.ArrayList<>();
            for (SearchResult r : lastResults) {
                String sub = r.x() + ", " + r.z() + "  " + Math.round(r.distance()) + " m";
                rows.add(new ResultsListWidget.Row(r.name(), sub, r.structureId(), r.color(), () -> pickResult(r), null));
            }
            resultsList.setRows(rows, Component.translatable("seedscout.screen.no_results"));
        }
    }

    private Component targetLabel() {
        return Component.literal(StructureIcons.displayName(biomeMode ? lastBiome : lastStructure));
    }

    private void selectStructure(Identifier id) {
        if (biomeMode) {
            lastBiome = id;
        } else {
            lastStructure = id;
        }
        if (structureButton != null) structureButton.setMessage(targetLabel());
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
        picker = new StructurePickerWidget(minecraft, x, y + 22, w, h - 22, ids, biomeMode, id -> {
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
        boolean biomes = biomeMode;
        Identifier targetId = biomes ? lastBiome : lastStructure;
        int radiusBlocks = radiusButton.getValue();
        MapSession current = session;
        searching = true;
        lastResults = List.of();
        waypointsTab = false;
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
        WaypointState.reset();
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
                        screen.rebuildToggleBar();
                        screen.buildRightPanel();
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
                        screen.buildRightPanel();
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
        updateDimensionButtons();
        if (minecraft.player != null && Dimension.fromLevel(minecraft.player.level().dimension()) == dimension) {
            centerOnPlayer();
        } else {
            viewport.centerX = 0;
            viewport.centerZ = 0;
        }
        Long seed = session != null ? Long.valueOf(session.seed()) : lastAppliedSeed;
        java.util.OptionalLong resolved = SeedSource.resolve(minecraft, config);
        if (resolved.isPresent()) seed = resolved.getAsLong();
        if (seed != null) ensureSession(seed);
    }

    private void updateDimensionButtons() {
        for (Dimension d : Dimension.values()) {
            Button b = dimensionButtons[d.ordinal()];
            if (b != null) b.active = d != currentDimension;
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
        for (SearchResult hit : lastResults) {
            int sx = (int) Math.round(viewport.worldToScreenX(hit.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(hit.z()));
            int r = 11;
            context.fill(sx - r, sy - r, sx + r, sy - r + 2, 0xFFFFD700);
            context.fill(sx - r, sy + r - 2, sx + r, sy + r, 0xFFFFD700);
            context.fill(sx - r, sy - r, sx - r + 2, sy + r, 0xFFFFD700);
            context.fill(sx + r - 2, sy - r, sx + r, sy + r, 0xFFFFD700);
            if (viewport.lod() >= 2 || hit.structureId() == null) {
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
        Identifier mapDimension = session.dimension().levelId();
        for (Waypoint wp : WaypointState.saved()) {
            if (!wp.dimension().equals(mapDimension)) continue;
            boolean active = WaypointState.get().map(wp::equals).orElse(false);
            int sx = (int) Math.round(viewport.worldToScreenX(wp.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(wp.z()));
            int color = active ? 0xFFFF4040 : WaypointState.colorOf(wp);
            int arm = active ? 10 : 6;
            context.fill(sx - 1, sy - arm, sx + 1, sy + arm, color);
            context.fill(sx - arm, sy - 1, sx + arm, sy + 1, color);
        }
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
                    WaypointState.set(new Waypoint(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), id, session.dimension().levelId()));
                    onClose();
                    return true;
                }
            }
        }
        if (session != null && click.button() == 1 && viewport.contains(click.x(), click.y())) {
            int bx = (int) Math.floor(viewport.screenToWorldX(click.x()));
            int bz = (int) Math.floor(viewport.screenToWorldZ(click.y()));
            String name = Component.translatable("seedscout.waypoint.marker", bx, bz).getString();
            WaypointState.set(new Waypoint(name, bx, bz, null, session.dimension().levelId()));
            waypointsTab = true;
            refreshList();
            if (clearButton != null) clearButton.active = true;
            return true;
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
