package com.seedscout.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seedscout.worldgen.DungeonFinder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureGridTest {
    private static final Identifier VILLAGE = Identifier.withDefaultNamespace("village_plains");
    private static final Identifier GEODE = Identifier.withDefaultNamespace("amethyst_geode");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Identifier> ids() {
        List<Identifier> ids = new ArrayList<>(List.of(VILLAGE, GEODE));
        ids.addAll(DungeonFinder.IDS);
        return ids;
    }

    @Test
    void allLeavesDungeonsOff() {
        Set<Identifier> enabled = new HashSet<>();
        AtomicInteger changes = new AtomicInteger();
        StructureGrid grid = new StructureGrid(0, 0, 4, ids(), enabled, changes::incrementAndGet, id -> {}, () -> null);
        grid.setAll(true);
        assertEquals(Set.of(VILLAGE, GEODE), enabled);
        assertEquals(1, changes.get());
    }

    @Test
    void allKeepsDungeonsAlreadyOn() {
        Set<Identifier> enabled = new HashSet<>(Set.of(DungeonFinder.ANY));
        StructureGrid grid = new StructureGrid(0, 0, 4, ids(), enabled, () -> {}, id -> {}, () -> null);
        grid.setAll(true);
        assertEquals(Set.of(VILLAGE, GEODE, DungeonFinder.ANY), enabled);
    }

    @Test
    void noneClearsDungeonsToo() {
        Set<Identifier> enabled = new HashSet<>(ids());
        StructureGrid grid = new StructureGrid(0, 0, 4, ids(), enabled, () -> {}, id -> {}, () -> null);
        grid.setAll(false);
        assertTrue(enabled.isEmpty());
    }
}
