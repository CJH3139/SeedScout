package com.seedscout.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShareFormatTest {
    @Test
    void roundTrips() {
        String text = ShareFormat.format("Village Plains", 640, -816, "overworld");
        assertEquals("Village Plains @ 640, -816 [SeedScout/overworld]", text);
        ShareFormat.Shared shared = ShareFormat.parse(text).orElseThrow();
        assertEquals("Village Plains", shared.name());
        assertEquals(640, shared.x());
        assertEquals(-816, shared.z());
        assertEquals("overworld", shared.dimension());
    }

    @Test
    void parsesInsideAChatLine() {
        ShareFormat.Shared shared = ShareFormat.parse("<Steve> Bastion Remnant @ 192, 0 [SeedScout/nether]").orElseThrow();
        assertEquals("Bastion Remnant", shared.name());
        assertEquals("nether", shared.dimension());
    }

    @Test
    void sanitizesNames() {
        String text = ShareFormat.format("weird [name] @ here", 1, 2, "end");
        assertTrue(ShareFormat.parse(text).isPresent());
        assertEquals("weird name here", ShareFormat.parse(text).orElseThrow().name());
        assertEquals("Marker 1, 2", ShareFormat.parse("[Admin] Steve: Marker 1, 2 @ 1, 2 [SeedScout/overworld]").orElseThrow().name());
    }

    @Test
    void ignoresUnrelatedText() {
        assertTrue(ShareFormat.parse("hello").isEmpty());
        assertTrue(ShareFormat.parse("x @ 1, 2 [SeedScout/]").isEmpty());
        assertTrue(ShareFormat.parse(null).isEmpty());
    }
}
