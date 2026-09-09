package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.map.FeatureIndex;
import com.seedscout.map.MapWorker;
import com.seedscout.map.StructureIndex;
import com.seedscout.map.TileCache;
import com.seedscout.map.TilePixels;
import com.seedscout.map.TileStore;
import com.seedscout.worldgen.BiomeColors;
import com.seedscout.worldgen.Dimension;
import com.seedscout.worldgen.SeedWorld;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

public final class MapSession {
    private static final long CACHE_MAX_BYTES = 512L * 1024 * 1024;
    private static final long CACHE_TARGET_BYTES = 384L * 1024 * 1024;
    private static boolean pruned;

    private final SeedWorld world;
    private final TextureManager textureManager;
    private final StructureIndex structures;
    private final FeatureIndex features;
    private final TilePixels.ColorSampler biomeColors;
    private final List<Holder<StructureSet>> sets;
    private final Set<Identifier> enabledStructures = new HashSet<>();
    private final Map<Long, Boolean> slimeCache = new HashMap<>();
    private TileCache tiles;
    private volatile int biomeY;

    private MapSession(SeedWorld world, TextureManager textureManager, int biomeY) {
        this.world = world;
        this.textureManager = textureManager;
        this.biomeY = world.dimension() == Dimension.OVERWORLD ? biomeY : SeedWorld.SURFACE_Y;
        this.tiles = openTiles();
        this.biomeColors = (x, z) -> BiomeColors.colorOf(SeedWorld.idOf(world.biomeAt(x, this.biomeY, z)));
        this.sets = world.allStructureSets();
        for (String id : SeedScoutClient.config().enabledStructures) {
            enabledStructures.add(Identifier.parse(id));
        }

        List<StructureIndex.SetInfo> infos = new ArrayList<>();
        for (Holder<StructureSet> set : sets) {
            Set<Identifier> ids = new HashSet<>();
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                ids.add(SeedWorld.idOf(entry.structure()));
            }
            if (set.value().placement() instanceof RandomSpreadStructurePlacement spread) {
                infos.add(new StructureIndex.SetInfo(SeedWorld.idOf(set), spread.spacing(), false, ids));
            } else if (set.value().placement() instanceof ConcentricRingsStructurePlacement) {
                infos.add(new StructureIndex.SetInfo(SeedWorld.idOf(set), 0, true, ids));
            }
        }
        Minecraft client = Minecraft.getInstance();
        this.structures = new StructureIndex(
                infos,
                (setId, rx, rz) -> world.structureInRegionConfirmed(setById(setId), rx, rz),
                setId -> world.concentricRingHits(setById(setId)),
                MapWorker::submit,
                client::execute);
        this.features = new FeatureIndex(world::featuresInChunk, MapWorker::submit, client::execute);
    }

    public FeatureIndex features() { return features; }

    public List<Identifier> featureIds() { return world.featureIds(); }

    public Set<Identifier> enabledFeatures() {
        Set<Identifier> out = new HashSet<>();
        for (Identifier id : world.featureIds()) {
            if (enabledStructures.contains(id)) out.add(id);
        }
        return out;
    }

    public static MapSession open(long seed, Dimension dimension, TextureManager textureManager, MinecraftServer server) {
        int biomeY = SeedScoutClient.config().biomeY;
        if (server != null) {
            try {
                return new MapSession(SeedWorld.fromServer(server, dimension), textureManager, biomeY);
            } catch (RuntimeException e) {
                SeedScoutClient.LOGGER.warn("Falling back to vanilla generation for {}: {}", dimension.levelId(), e.toString());
            }
        }
        return new MapSession(SeedWorld.create(seed, dimension), textureManager, biomeY);
    }

    private static Path cacheRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("seedscout").resolve("tiles");
    }

    private TileCache openTiles() {
        if (!SeedScoutClient.config().diskCache) {
            return new TileCache(textureManager, null);
        }
        Path rootDir = cacheRoot();
        synchronized (MapSession.class) {
            if (!pruned) {
                pruned = true;
                TileStore.prune(rootDir, CACHE_MAX_BYTES, CACHE_TARGET_BYTES);
            }
        }
        String version = SharedConstants.getCurrentVersion().name();
        String layer = world.profile() + "-y" + biomeY;
        TileStore store = new TileStore(rootDir, version, world.dimension().name().toLowerCase(Locale.ROOT), world.seed(), layer);
        return new TileCache(textureManager, store);
    }

    private Holder<StructureSet> setById(Identifier id) {
        for (Holder<StructureSet> set : sets) {
            if (SeedWorld.idOf(set).equals(id)) return set;
        }
        throw new IllegalArgumentException("Unknown structure set " + id);
    }

    public SeedWorld world() { return world; }
    public TileCache tiles() { return tiles; }
    public StructureIndex structures() { return structures; }
    public TilePixels.ColorSampler biomeColors() { return biomeColors; }
    public long seed() { return world.seed(); }
    public Dimension dimension() { return world.dimension(); }
    public int biomeY() { return biomeY; }
    public Set<Identifier> enabledStructures() { return enabledStructures; }

    public void setBiomeY(int y) {
        if (y == biomeY || world.dimension() != Dimension.OVERWORLD) return;
        tiles.clear();
        biomeY = y;
        tiles = openTiles();
    }

    public boolean isSlimeChunk(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        Boolean cached = slimeCache.get(key);
        if (cached == null) {
            cached = world.isSlimeChunk(chunkX, chunkZ);
            slimeCache.put(key, cached);
        }
        return cached;
    }

    public void close() {
        tiles.clear();
        structures.clear();
        features.clear();
    }
}
