package com.seedscout.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BearingTest {
    @Test
    void targetStraightAheadIsZero() {
        assertEquals(0f, Bearing.relativeDegrees(0, 0, 0f, 0, 100), 1e-4);
        assertEquals(0f, Bearing.relativeDegrees(0, 0, 90f, -100, 0), 1e-4);
    }

    @Test
    void targetToTheRightIsPositive() {
        assertEquals(90f, Bearing.relativeDegrees(0, 0, 0f, -100, 0), 1e-4);
    }

    @Test
    void targetBehindIs180() {
        assertEquals(180f, Math.abs(Bearing.relativeDegrees(0, 0, 0f, 0, -100)), 1e-4);
    }

    @Test
    void targetToTheLeftIsNegative() {
        assertEquals(-90f, Bearing.relativeDegrees(0, 0, 180f, -100, 0), 1e-4);
    }

    @Test
    void wrapsAround() {
        assertEquals(-100f, Bearing.relativeDegrees(0, 0, -170f, -100, 0), 1e-4);
    }

    @Test
    void distanceIsHorizontal() {
        assertEquals(5.0, Bearing.distance(0, 0, 3, 4), 1e-9);
    }
}
