package com.seedscout.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class SeedParserTest {
    @Test
    void parsesNumericSeed() {
        assertEquals(OptionalLong.of(123456789L), SeedParser.parse("123456789"));
    }

    @Test
    void parsesNegativeNumericSeed() {
        assertEquals(OptionalLong.of(-99L), SeedParser.parse(" -99 "));
    }

    @Test
    void hashesTextSeedLikeVanilla() {
        assertEquals(OptionalLong.of((long) "hello".hashCode()), SeedParser.parse("hello"));
    }

    @Test
    void rejectsEmptyOrBlank() {
        assertTrue(SeedParser.parse("").isEmpty());
        assertTrue(SeedParser.parse("   ").isEmpty());
    }
}
