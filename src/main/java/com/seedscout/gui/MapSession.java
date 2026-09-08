package com.seedscout.gui;

import com.seedscout.SeedScoutClient;
import com.seedscout.map.MapWorker;
import com.seedscout.map.StructureIndex;
import com.seedscout.map.TileCache;
import com.seedscout.map.TilePixels;
import com.seedscout.worldgen.BiomeColors;
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
    private final List<Holder.Reference<StructureSet>> sets;
    private final Set<Identifier> enabledStructures = new HashSet<>();

    private MapSession(SeedWorld world, TextureManager textureManager) {
        this.world = world;
        this.tiles = new TileCache(textureManager);
        this.biomeColors = (x, z) -> BiomeColors.colorOf(SeedWorld.idOf(world.biomeAt(x, z)));
        this.sets = world.allStructureSets();
        for (String id : SeedScoutClient.config().enabledStructures) {
            enabledStructures.add(Identifier.parse(id));
        }

        List<StructureIndex.SetInfo> infos = new ArrayList<>();
        for (Holder.Reference<StructureSet> set : sets) {
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
                (setId, rx, rz) -> world.structureInRegion(setById(setId), rx, rz),
                setId -> world.concentricRingHits(setById(setId)),
                MapWorker::submit,
                client::execute);
    }

    private Holder.Reference<StructureSet> setById(Identifier id) {
        for (Holder.Reference<StructureSet> set : sets) {
            if (SeedWorld.idOf(set).equals(id)) return set;
        }
        throw new IllegalArgumentException("Unknown structure set " + id);
    }

    public static MapSession open(long seed, TextureManager textureManager) {
        return new MapSession(SeedWorld.create(seed), textureManager);
    }

    public SeedWorld world() { return world; }
    public TileCache tiles() { return tiles; }
    public StructureIndex structures() { return structures; }
    public TilePixels.ColorSampler biomeColors() { return biomeColors; }
    public long seed() { return world.seed(); }
    public Set<Identifier> enabledStructures() { return enabledStructures; }

    public void close() {
        tiles.clear();
        structures.clear();
    }
}
