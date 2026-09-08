package com.seedscout.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
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
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestRegistryBootstrap.bindVanillaTags();
        world = SeedWorld.create(1L);
    }

    @Test
    void biomeAtReturnsAnOverworldBiome() {
        Holder<?> biome = world.biomeAt(0, 0);
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
        Holder<Structure> village = world.structure(Identifier.withDefaultNamespace("village_plains"));
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
        Holder<Structure> stronghold = world.structure(Identifier.withDefaultNamespace("stronghold"));
        List<StructureHit> hits = world.findStructures(stronghold, new ChunkPos(0, 0), 100000 / 16, 3);
        assertEquals(3, hits.size());
    }

    @Test
    void denseStructureSearchStaysBounded() {
        Holder<Structure> mineshaft = world.structure(Identifier.withDefaultNamespace("mineshaft"));
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
        Holder<Structure> village = world.structure(Identifier.withDefaultNamespace("village_plains"));
        StructureHit nearestVillage = world.findStructures(village, new ChunkPos(0, 0), 10000 / 16, 1).get(0);
        assertEquals(RECORDED_VILLAGE_X, nearestVillage.blockX());
        assertEquals(RECORDED_VILLAGE_Z, nearestVillage.blockZ());

        Holder<Structure> monument = world.structure(Identifier.withDefaultNamespace("monument"));
        StructureHit nearestMonument = world.findStructures(monument, new ChunkPos(0, 0), 10000 / 16, 1).get(0);
        assertEquals(RECORDED_MONUMENT_X, nearestMonument.blockX());
        assertEquals(RECORDED_MONUMENT_Z, nearestMonument.blockZ());

        Holder<Structure> stronghold = world.structure(Identifier.withDefaultNamespace("stronghold"));
        StructureHit nearestStronghold = world.findStructures(stronghold, new ChunkPos(0, 0), 100000 / 16, 1).get(0);
        assertEquals(RECORDED_STRONGHOLD_X, nearestStronghold.blockX());
        assertEquals(RECORDED_STRONGHOLD_Z, nearestStronghold.blockZ());
    }

    @Test
    void staticBlockRegistryTagsRemainBoundAfterSeedWorldCreate() {
        assertTrue(BuiltInRegistries.BLOCK.getOrThrow(BlockTags.LEAVES).size() > 0, "block registry should still know its LEAVES tag");
        assertTrue(Blocks.OAK_LEAVES.defaultBlockState().is(BlockTags.LEAVES), "oak leaves should still report being in the LEAVES tag");
        Holder<Block> stoneEntry = BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE);
        assertTrue(stoneEntry.tags().findAny().isPresent(), "stone's registry entry should still carry at least one tag");
    }
}
