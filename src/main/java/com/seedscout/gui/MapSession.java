package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.map.MapWorker;
import com.seedscout.map.StructureIndex;
import com.seedscout.map.TileCache;
import com.seedscout.map.TilePixels;
import com.seedscout.worldgen.BiomeColors;
import com.seedscout.worldgen.Dimension;
import com.seedscout.worldgen.SeedWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

public final class MapSession {
    private final SeedWorld world;
    private final TileCache tiles;
    private final StructureIndex structures;
    private final TilePixels.ColorSampler biomeColors;
    private final List<Holder<StructureSet>> sets;
    private final Set<Identifier> enabledStructures = new HashSet<>();

    private MapSession(SeedWorld world, TextureManager textureManager) {
        this.world = world;
        this.tiles = new TileCache(textureManager, SeedScoutClient.config().diskCache ? openStore(world) : null);
        this.biomeColors = (x, z) -> BiomeColors.colorOf(SeedWorld.idOf(world.biomeAt(x, z)));
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
    }

    private static final long CACHE_MAX_BYTES = 512L * 1024 * 1024;
    private static final long CACHE_TARGET_BYTES = 384L * 1024 * 1024;
    private static boolean pruned;

    private static java.nio.file.Path cacheRoot() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("seedscout").resolve("tiles");
    }

    private static com.seedscout.map.TileStore openStore(SeedWorld world) {
        java.nio.file.Path rootDir = cacheRoot();
        synchronized (MapSession.class) {
            if (!pruned) {
                pruned = true;
                com.seedscout.map.TileStore.prune(rootDir, CACHE_MAX_BYTES, CACHE_TARGET_BYTES);
            }
        }
        String version = net.minecraft.SharedConstants.getCurrentVersion().name();
        return new com.seedscout.map.TileStore(rootDir, version, world.dimension().name().toLowerCase(java.util.Locale.ROOT), world.seed());
    }

    private Holder<StructureSet> setById(Identifier id) {
        for (Holder<StructureSet> set : sets) {
            if (SeedWorld.idOf(set).equals(id)) return set;
        }
        throw new IllegalArgumentException("Unknown structure set " + id);
    }

    public static MapSession open(long seed, Dimension dimension, TextureManager textureManager) {
        return new MapSession(SeedWorld.create(seed, dimension), textureManager);
    }

    public SeedWorld world() { return world; }
    public TileCache tiles() { return tiles; }
    public StructureIndex structures() { return structures; }
    public TilePixels.ColorSampler biomeColors() { return biomeColors; }
    public long seed() { return world.seed(); }
    public Dimension dimension() { return world.dimension(); }

    private final java.util.Map<Long, Boolean> slimeCache = new java.util.HashMap<>();

    public boolean isSlimeChunk(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        Boolean cached = slimeCache.get(key);
        if (cached == null) {
            cached = world.isSlimeChunk(chunkX, chunkZ);
            slimeCache.put(key, cached);
        }
        return cached;
    }
    public Set<Identifier> enabledStructures() { return enabledStructures; }

    public void close() {
        tiles.clear();
        structures.clear();
    }
}
