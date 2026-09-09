package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoordinateParserTest {
    @Test
    void acceptsCommonSeparators() {
        assertEquals(new CoordinateParser.Coordinates(640, 816), CoordinateParser.parse("640 816").orElseThrow());
        assertEquals(new CoordinateParser.Coordinates(640, 816), CoordinateParser.parse("640, 816").orElseThrow());
        assertEquals(new CoordinateParser.Coordinates(640, 816), CoordinateParser.parse("640,816").orElseThrow());
        assertEquals(new CoordinateParser.Coordinates(-12, 7), CoordinateParser.parse("x=-12 z=7").orElseThrow());
        assertEquals(new CoordinateParser.Coordinates(-12, 7), CoordinateParser.parse("X: -12  Z: 7").orElseThrow());
    }

    @Test
    void threeNumbersDropTheMiddleY() {
        assertEquals(new CoordinateParser.Coordinates(100, -300), CoordinateParser.parse("100 64 -300").orElseThrow());
        assertEquals(new CoordinateParser.Coordinates(100, -300), CoordinateParser.parse("100.5 64.0 -299.2").orElseThrow());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(CoordinateParser.parse("").isEmpty());
        assertTrue(CoordinateParser.parse("village").isEmpty());
        assertTrue(CoordinateParser.parse("1").isEmpty());
        assertTrue(CoordinateParser.parse("1 2 3 4").isEmpty());
        assertTrue(CoordinateParser.parse(null).isEmpty());
    }
}
