package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MarkerClustererTest {
    private record P(double x, double y) {}

    @Test
    void farApartMarkersStaySeparate() {
        List<P> points = List.of(new P(0, 0), new P(100, 0), new P(0, 100));
        List<MarkerClusterer.Cluster<P>> clusters = MarkerClusterer.cluster(points, P::x, P::y, 10);
        assertEquals(3, clusters.size());
        for (MarkerClusterer.Cluster<P> c : clusters) assertEquals(1, c.size());
    }

    @Test
    void nearbyMarkersMergeAndAverage() {
        List<P> points = List.of(new P(0, 0), new P(4, 0), new P(2, 4), new P(50, 50));
        List<MarkerClusterer.Cluster<P>> clusters = MarkerClusterer.cluster(points, P::x, P::y, 10);
        assertEquals(2, clusters.size());
        MarkerClusterer.Cluster<P> big = clusters.get(0).size() == 3 ? clusters.get(0) : clusters.get(1);
        assertEquals(3, big.size());
        assertEquals(2.0, big.x(), 1e-9);
        assertEquals(4.0 / 3.0, big.y(), 1e-9);
    }

    @Test
    void mergesAcrossGridCellBoundaries() {
        List<P> points = List.of(new P(9.9, 0), new P(10.1, 0));
        List<MarkerClusterer.Cluster<P>> clusters = MarkerClusterer.cluster(points, P::x, P::y, 10);
        assertEquals(1, clusters.size());
    }

    @Test
    void zeroRadiusNeverMerges() {
        List<P> points = List.of(new P(0, 0), new P(0, 0));
        assertEquals(2, MarkerClusterer.cluster(points, P::x, P::y, 0).size());
    }

    @Test
    void everyItemLandsInExactlyOneCluster() {
        List<P> points = new ArrayList<>();
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) points.add(new P(random.nextDouble() * 300, random.nextDouble() * 300));
        List<MarkerClusterer.Cluster<P>> clusters = MarkerClusterer.cluster(points, P::x, P::y, 12);
        int total = 0;
        for (MarkerClusterer.Cluster<P> c : clusters) total += c.size();
        assertEquals(500, total);
        assertTrue(clusters.size() < 500);
    }
}
