package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SeedWorldTest {
    private static final int RECORDED_VILLAGE_X = 640;
    private static final int RECORDED_VILLAGE_Z = 816;
    private static final int RECORDED_MONUMENT_X = 816;
    private static final int RECORDED_MONUMENT_Z = -272;
    private static final int RECORDED_STRONGHOLD_X = -1136;
    private static final int RECORDED_STRONGHOLD_Z = 848;

    private static SeedWorld world;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
    }

    @Test
    void biomeAtReturnsAnOverworldBiome() {
        RegistryEntry<?> biome = world.biomeAt(0, 0);
        assertNotNull(biome);
        assertEquals("minecraft", SeedWorld.idOf(biome).getNamespace());
    }

    @Test
    void listsVanillaStructures() {
        List<String> ids = world.allStructures().stream().map(e -> SeedWorld.idOf(e).toString()).toList();
        assertTrue(ids.contains("minecraft:village_plains"));
        assertTrue(ids.contains("minecraft:stronghold"));
    }

    @Test
    void villageSearchFindsSomethingWithin10kBlocks() {
        RegistryEntry<Structure> village = world.structure(Identifier.ofVanilla("village_plains"));
        List<StructureHit> hits = world.findStructures(village, new ChunkPos(0, 0), 10000 / 16, 5);
        assertFalse(hits.isEmpty());
        for (StructureHit hit : hits) {
            assertEquals(village, hit.structure());
        }
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).distance() <= hits.get(i).distance(), "sorted by distance");
        }
    }

    @Test
    void strongholdSearchReturnsRingPositions() {
        RegistryEntry<Structure> stronghold = world.structure(Identifier.ofVanilla("stronghold"));
        List<StructureHit> hits = world.findStructures(stronghold, new ChunkPos(0, 0), 100000 / 16, 3);
        assertEquals(3, hits.size());
    }

    @Test
    void denseStructureSearchStaysBounded() {
        RegistryEntry<Structure> mineshaft = world.structure(Identifier.ofVanilla("mineshaft"));
        List<StructureHit> hits = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> world.findStructures(mineshaft, new ChunkPos(0, 0), 50000 / 16, 20));
        assertFalse(hits.isEmpty(), "mineshafts are dense enough to appear inside the shrunk radius");
        for (StructureHit hit : hits) {
            assertEquals(mineshaft, hit.structure());
        }
        for (int i = 1; i < hits.size(); i++) {
            assertTrue(hits.get(i - 1).distance() <= hits.get(i).distance(), "sorted by distance");
        }
    }

    @Test
    void matchesInGameLocateForSeed1() {
        RegistryEntry<Structure> village = world.structure(Identifier.ofVanilla("village_plains"));
        StructureHit nearestVillage = world.findStructures(village, new ChunkPos(0, 0), 10000 / 16, 1).get(0);
        assertEquals(RECORDED_VILLAGE_X, nearestVillage.blockX());
        assertEquals(RECORDED_VILLAGE_Z, nearestVillage.blockZ());

        RegistryEntry<Structure> monument = world.structure(Identifier.ofVanilla("monument"));
        StructureHit nearestMonument = world.findStructures(monument, new ChunkPos(0, 0), 10000 / 16, 1).get(0);
        assertEquals(RECORDED_MONUMENT_X, nearestMonument.blockX());
        assertEquals(RECORDED_MONUMENT_Z, nearestMonument.blockZ());

        RegistryEntry<Structure> stronghold = world.structure(Identifier.ofVanilla("stronghold"));
        StructureHit nearestStronghold = world.findStructures(stronghold, new ChunkPos(0, 0), 100000 / 16, 1).get(0);
        assertEquals(RECORDED_STRONGHOLD_X, nearestStronghold.blockX());
        assertEquals(RECORDED_STRONGHOLD_Z, nearestStronghold.blockZ());
    }

    @Test
    void staticBlockRegistryTagsRemainBoundAfterSeedWorldCreate() {
        assertTrue(Registries.BLOCK.getOrThrow(BlockTags.LEAVES).size() > 0, "block registry should still know its LEAVES tag");
        assertTrue(Blocks.OAK_LEAVES.getDefaultState().isIn(BlockTags.LEAVES), "oak leaves should still report being in the LEAVES tag");
        RegistryEntry<Block> stoneEntry = Registries.BLOCK.getEntry(Blocks.STONE);
        assertTrue(stoneEntry.streamTags().findAny().isPresent(), "stone's registry entry should still carry at least one tag");
    }
}
