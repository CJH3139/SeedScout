package com.seedscout.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class MarkerClusterer {
    public static final class Cluster<T> {
        private double sumX;
        private double sumY;
        private final List<T> members = new ArrayList<>();

        public double x() {
            return sumX / members.size();
        }

        public double y() {
            return sumY / members.size();
        }

        public List<T> members() {
            return members;
        }

        public int size() {
            return members.size();
        }

        private void add(T item, double x, double y) {
            members.add(item);
            sumX += x;
            sumY += y;
        }
    }

    private record Cell(int cx, int cy) {}

    private MarkerClusterer() {}

    public static <T> List<Cluster<T>> cluster(List<T> items, ToDoubleFunction<T> xOf, ToDoubleFunction<T> yOf, double radius) {
        List<Cluster<T>> clusters = new ArrayList<>();
        if (radius <= 0) {
            for (T item : items) {
                Cluster<T> c = new Cluster<>();
                c.add(item, xOf.applyAsDouble(item), yOf.applyAsDouble(item));
                clusters.add(c);
            }
            return clusters;
        }
        Map<Cell, List<Cluster<T>>> grid = new HashMap<>();
        double radiusSq = radius * radius;
        for (T item : items) {
            double x = xOf.applyAsDouble(item);
            double y = yOf.applyAsDouble(item);
            int cx = (int) Math.floor(x / radius);
            int cy = (int) Math.floor(y / radius);
            Cluster<T> best = null;
            double bestDist = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Cluster<T>> bucket = grid.get(new Cell(cx + dx, cy + dy));
                    if (bucket == null) continue;
                    for (Cluster<T> c : bucket) {
                        double ddx = c.x() - x;
                        double ddy = c.y() - y;
                        double d = ddx * ddx + ddy * ddy;
                        if (d <= radiusSq && d < bestDist) {
                            bestDist = d;
                            best = c;
                        }
                    }
                }
            }
            if (best == null) {
                best = new Cluster<>();
                clusters.add(best);
                grid.computeIfAbsent(new Cell(cx, cy), k -> new ArrayList<>()).add(best);
            }
            best.add(item, x, y);
        }
        return clusters;
    }
}
