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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

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

    protected final MinecraftClient client;
    protected final SeedScoutConfig config = SeedScoutClient.config();
    private boolean dragging;
    private TextFieldWidget seedField;
    private ButtonWidget applyButton;
    private StructureToggleBar toggleBar;
    private ButtonWidget structureButton;
    private StructurePickerWidget picker;
    private TextFieldWidget pickerFilter;
    private CyclingButtonWidget<Integer> radiusButton;
    private ButtonWidget searchButton;
    private ResultsListWidget resultsList;
    private ButtonWidget centerButton;
    private ButtonWidget clearButton;
    private CyclingButtonWidget<Boolean> beamButton;
    private static Identifier lastStructure = Identifier.ofVanilla("village_plains");
    private static List<StructureHit> lastResults = List.of();

    private static boolean searching;

    private static Long lastAppliedSeed;

    public SeedScoutScreen(MinecraftClient client) {
        super(Text.translatable("seedscout.screen.title"));
        this.client = client;
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

        OptionalLong seed = SeedSource.resolve(client, config);
        if (seed.isPresent()) {
            ensureSession(seed.getAsLong());
        } else if (lastAppliedSeed != null) {
            ensureSession(lastAppliedSeed);
        } else {
            clearSession();
        }

        seedField = new TextFieldWidget(textRenderer, 6, 20, 160, 18,
                Text.translatable("seedscout.screen.seed_placeholder"));
        seedField.setMaxLength(64);
        seedField.setPlaceholder(Text.translatable("seedscout.screen.seed_placeholder"));
        seedField.setChangedListener(this::updateSeedFieldColor);
        seedField.setText(currentSeedText());
        boolean singleplayer = client.getServer() != null;
        seedField.setEditable(!singleplayer);
        addDrawableChild(seedField);

        applyButton = ButtonWidget.builder(
                        Text.translatable("seedscout.screen.apply"), b -> applySeed())
                .dimensions(170, 20, 50, 18)
                .build();
        applyButton.active = !singleplayer;
        addDrawableChild(applyButton);

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
        if (structureButton != null) remove(structureButton);
        if (radiusButton != null) remove(radiusButton);
        if (searchButton != null) remove(searchButton);
        if (resultsList != null) remove(resultsList);
        if (centerButton != null) remove(centerButton);
        if (clearButton != null) remove(clearButton);
        if (beamButton != null) remove(beamButton);

        List<Identifier> ids = session == null ? List.of(lastStructure) : session.world().allStructures().stream()
                .map(SeedWorld::idOf).toList();
        if (!ids.contains(lastStructure)) lastStructure = ids.get(0);
        structureButton = ButtonWidget.builder(structureLabel(), b -> togglePicker(ids))
                .dimensions(px, y, pw, 20).build();
        addDrawableChild(structureButton);
        y += 24;

        List<Integer> radii = List.of(2000, 5000, 10000, 50000);
        int radius = radii.contains(config.lastRadius) ? config.lastRadius : 5000;
        radiusButton = CyclingButtonWidget
                .<Integer>builder(r -> Text.translatable("seedscout.screen.radius_value", r), radius)
                .values(radii)
                .build(px, y, pw, 20, Text.translatable("seedscout.screen.radius"), (b, v) -> {
                    config.lastRadius = v;
                    SeedScoutClient.saveConfig();
                });
        addDrawableChild(radiusButton);
        y += 24;

        searchButton = ButtonWidget.builder(
                        Text.translatable("seedscout.screen.search"), b -> runSearch())
                .dimensions(px, y, pw, 20).build();
        searchButton.active = session != null && !searching;
        addDrawableChild(searchButton);
        y += 24;

        resultsList = new ResultsListWidget(client, pw, Math.max(20, height - y - 54), y, this::pickResult);
        resultsList.setX(px);
        if (searching) {
            resultsList.setStatus(Text.translatable("seedscout.screen.searching"));
        } else {
            resultsList.setResults(lastResults);
        }
        addDrawableChild(resultsList);

        int bottomY = height - 26;
        beamButton = CyclingButtonWidget.onOffBuilder(config.showBeam)
                .build(px, bottomY, pw / 2 - 2, 20, Text.translatable("seedscout.screen.beam"), (b, v) -> {
                    config.showBeam = v;
                    SeedScoutClient.saveConfig();
                });
        addDrawableChild(beamButton);
        centerButton = ButtonWidget.builder(
                        Text.translatable("seedscout.screen.center"), b -> centerOnPlayer())
                .dimensions(px, height - 50, pw, 20).build();
        addDrawableChild(centerButton);
        clearButton = ButtonWidget.builder(
                        Text.translatable("seedscout.screen.clear_waypoint"), b -> {
                            WaypointState.clear();
                            b.active = false;
                        })
                .dimensions(px + pw / 2 + 2, bottomY, pw / 2 - 2, 20).build();
        clearButton.active = WaypointState.get().isPresent();
        addDrawableChild(clearButton);
    }

    private Text structureLabel() {
        return Text.literal(StructureIcons.displayName(lastStructure));
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
        pickerFilter = new TextFieldWidget(textRenderer, x, y, w, 18, Text.translatable("seedscout.screen.filter"));
        pickerFilter.setPlaceholder(Text.translatable("seedscout.screen.filter"));
        pickerFilter.setMaxLength(32);
        picker = new StructurePickerWidget(client, x, y + 22, w, h - 22, ids, id -> {
            selectStructure(id);
            closePicker();
        });
        pickerFilter.setChangedListener(text -> picker.filter(text));
        addDrawableChild(pickerFilter);
        addDrawableChild(picker);
        setFocused(pickerFilter);
        pickerFilter.setFocused(true);
    }

    private void closePicker() {
        if (picker != null) remove(picker);
        if (pickerFilter != null) remove(pickerFilter);
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
        resultsList.setStatus(Text.translatable("seedscout.screen.searching"));
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
                client.execute(() -> {
                    if (session != current) return;
                    searching = false;
                    if (client.currentScreen instanceof SeedScoutScreen s) {
                        s.resultsList.setStatus(Text.translatable("seedscout.screen.search_failed"));
                        s.searchButton.active = true;
                    }
                });
                return;
            }
            client.execute(() -> {
                if (session != current) return;
                searching = false;
                lastResults = hits;
                if (client.currentScreen instanceof SeedScoutScreen s) {
                    s.resultsList.setResults(hits);
                    s.searchButton.active = true;
                }
            });
        });
    }

    protected void pickResult(StructureHit hit) {
        Identifier id = SeedWorld.idOf(hit.structure());
        WaypointState.set(new Waypoint(StructureIcons.displayName(id), hit.blockX(), hit.blockZ(), id));
        close();
    }

    protected void centerOnPlayer() {
        if (client.player != null) {
            viewport.centerX = client.player.getX();
            viewport.centerZ = client.player.getZ();
            viewportInitialized = true;
        }
    }

    private String currentSeedText() {
        if (client.getServer() != null) {
            return Long.toString(client.getServer().getOverworld().getSeed());
        }
        return SeedSource.storageKey(client).flatMap(config::seedFor).orElse("");
    }

    private void updateSeedFieldColor(String text) {
        seedField.setEditableColor(SeedParser.parse(text).isPresent() ? 0xFFFFFF : 0xFF5555);
    }

    private void applySeed() {
        OptionalLong parsed = SeedParser.parse(seedField.getText());
        if (parsed.isEmpty()) {
            updateSeedFieldColor(seedField.getText());
            return;
        }
        SeedSource.storageKey(client).ifPresent(key -> {
            config.putSeed(key, seedField.getText().trim());
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
                MapSession fresh = MapSession.open(seed, client.getTextureManager());
                client.execute(() -> {
                    boolean seedChanged = session == null || session.seed() != seed;
                    if (session != null) session.close();
                    session = fresh;
                    lastResults = List.of();
                    searching = false;
                    if (client.currentScreen instanceof SeedScoutScreen screen) {
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
                client.execute(() -> {
                    if (session != null) {
                        session.close();
                        session = null;
                    }
                    sessionError = Text.translatable("seedscout.screen.unsupported").getString();
                    errorSeed = seed;
                    lastResults = List.of();
                    searching = false;
                    if (client.currentScreen instanceof SeedScoutScreen screen) {
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
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, BACKGROUND);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderMap(context);
        renderStructureIcons(context);
        context.fill(0, 0, width, TOP_BAR_HEIGHT, PANEL);
        context.fill(width - RIGHT_PANEL_WIDTH, TOP_BAR_HEIGHT, width, height, PANEL);
        context.drawTextWithShadow(textRenderer, title, 6, 6, TEXT);
        if (toggleBar != null) toggleBar.render(context, textRenderer, mouseX, mouseY);
        if (picker != null) {
            context.fill(pickerFilter.getX() - 4, pickerFilter.getY() - 4, pickerFilter.getX() + pickerFilter.getWidth() + 4,
                    picker.getY() + picker.getHeight() + 4, 0xFF0B0E12);
            context.fill(pickerFilter.getX() - 3, pickerFilter.getY() - 3, pickerFilter.getX() + pickerFilter.getWidth() + 3,
                    picker.getY() + picker.getHeight() + 3, PANEL);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
        renderOverlays(context, mouseX, mouseY);
    }

    private void renderMap(DrawContext context) {
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
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture.get(), sx, sy, 0, 0, w, h,
                        TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, TileKey.TILE_PIXELS);
            } else {
                context.fill(sx, sy, sx + w, sy + h, session.tiles().isFailed(key) ? FAILED : PLACEHOLDER);
            }
        }
        context.disableScissor();
    }

    private void renderStructureIcons(DrawContext context) {
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
        }
        WaypointState.get().ifPresent(wp -> {
            int sx = (int) Math.round(viewport.worldToScreenX(wp.x()));
            int sy = (int) Math.round(viewport.worldToScreenZ(wp.z()));
            context.fill(sx - 1, sy - 10, sx + 1, sy + 10, 0xFFFF4040);
            context.fill(sx - 10, sy - 1, sx + 10, sy + 1, 0xFFFF4040);
        });
        if (client.player != null) {
            int sx = (int) Math.round(viewport.worldToScreenX(client.player.getX()));
            int sy = (int) Math.round(viewport.worldToScreenZ(client.player.getZ()));
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(sx, sy);

            matrices.rotate((float) Math.toRadians(client.player.getYaw() + 180f));
            context.drawTexture(RenderPipelines.GUI_TEXTURED, StructureIcons.ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
            matrices.popMatrix();
        }
        context.disableScissor();
    }

    protected void renderOverlays(DrawContext context, int mouseX, int mouseY) {
        int mapCenterX = viewport.left + viewport.width / 2;
        int mapCenterY = viewport.top + viewport.height / 2;
        if (sessionError != null) {
            context.drawCenteredTextWithShadow(textRenderer, sessionError, mapCenterX, mapCenterY, TEXT);
        } else if (session == null && !sessionLoading) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("seedscout.screen.no_seed"), mapCenterX, mapCenterY, TEXT);
        } else if (sessionLoading) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("seedscout.screen.loading"), mapCenterX, mapCenterY, TEXT);
        }
        if (viewport.contains(mouseX, mouseY)) {
            int bx = (int) Math.floor(viewport.screenToWorldX(mouseX));
            int bz = (int) Math.floor(viewport.screenToWorldZ(mouseY));
            context.drawTextWithShadow(textRenderer, "X " + bx + "  Z " + bz + "  LOD " + viewport.lod(),
                    viewport.left + 4, viewport.top + viewport.height - 12, TEXT);
        }
        int pending = session == null ? 0 : session.tiles().pendingCount() + session.structures().pendingCount();
        if (pending > 0) {
            char spinner = "|/-\\".charAt((int) ((System.currentTimeMillis() / 120) % 4));
            context.drawTextWithShadow(textRenderer, spinner + " " + pending, viewport.left + viewport.width - 40,
                    viewport.top + 4, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
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
                    close();
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
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
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
    public boolean keyPressed(KeyInput input) {
        if (picker != null && input.isEscape()) {
            closePicker();
            return true;
        }
        boolean typing = (seedField != null && seedField.isFocused()) || (pickerFilter != null && pickerFilter.isFocused());
        if (!typing && SeedScoutClient.OPEN_MAP.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}
