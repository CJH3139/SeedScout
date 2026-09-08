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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;

public final class MapSession {
    private final SeedWorld world;
    private final TileCache tiles;
    private final StructureIndex structures;
    private final TilePixels.ColorSampler biomeColors;
    private final List<RegistryEntry.Reference<StructureSet>> sets;
    private final Set<Identifier> enabledStructures = new HashSet<>();

    private MapSession(SeedWorld world, TextureManager textureManager) {
        this.world = world;
        this.tiles = new TileCache(textureManager);
        this.biomeColors = (x, z) -> BiomeColors.colorOf(SeedWorld.idOf(world.biomeAt(x, z)));
        this.sets = world.allStructureSets();
        for (String id : SeedScoutClient.config().enabledStructures) {
            enabledStructures.add(Identifier.of(id));
        }

        List<StructureIndex.SetInfo> infos = new ArrayList<>();
        for (RegistryEntry.Reference<StructureSet> set : sets) {
            Set<Identifier> ids = new HashSet<>();
            for (StructureSet.WeightedEntry entry : set.value().structures()) {
                ids.add(SeedWorld.idOf(entry.structure()));
            }
            if (set.value().placement() instanceof RandomSpreadStructurePlacement spread) {
                infos.add(new StructureIndex.SetInfo(SeedWorld.idOf(set), spread.getSpacing(), false, ids));
            } else if (set.value().placement() instanceof ConcentricRingsStructurePlacement) {
                infos.add(new StructureIndex.SetInfo(SeedWorld.idOf(set), 0, true, ids));
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        this.structures = new StructureIndex(
                infos,
                (setId, rx, rz) -> world.structureInRegion(setById(setId), rx, rz),
                setId -> world.concentricRingHits(setById(setId)),
                MapWorker::submit,
                client::execute);
    }

    private RegistryEntry.Reference<StructureSet> setById(Identifier id) {
        for (RegistryEntry.Reference<StructureSet> set : sets) {
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
