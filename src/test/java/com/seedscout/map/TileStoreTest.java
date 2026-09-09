package com.seedscout.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TileStoreTest {
    @Test
    void roundTripsATile(@TempDir Path dir) {
        TileStore store = new TileStore(dir, "26.2", "overworld", 1L);
        TileKey key = new TileKey(1, -3, 7);
        int[] pixels = new int[TileKey.TILE_PIXELS * TileKey.TILE_PIXELS];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF000000 | (i * 2654435761L > 0 ? (int) (i * 2654435761L) & 0xFFFFFF : i);
        }
        assertTrue(store.load(key).isEmpty());
        store.save(key, pixels);
        Optional<int[]> loaded = store.load(key);
        assertTrue(loaded.isPresent());
        assertArrayEquals(pixels, loaded.get());
        assertTrue(Files.exists(store.pathFor(key)));
    }

    @Test
    void corruptFileIsDroppedAndTreatedAsMissing(@TempDir Path dir) throws Exception {
        TileStore store = new TileStore(dir, "26.2", "overworld", 1L);
        TileKey key = new TileKey(0, 0, 0);
        Path path = store.pathFor(key);
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{1, 2, 3, 4});
        assertTrue(store.load(key).isEmpty());
        assertFalse(Files.exists(path));
    }

    @Test
    void differentSeedsAndDimensionsDoNotShareFiles(@TempDir Path dir) {
        TileStore a = new TileStore(dir, "26.2", "overworld", 1L);
        TileStore b = new TileStore(dir, "26.2", "overworld", 2L);
        TileStore c = new TileStore(dir, "26.2", "nether", 1L);
        TileKey key = new TileKey(0, 0, 0);
        int[] pixels = new int[TileKey.TILE_PIXELS * TileKey.TILE_PIXELS];
        a.save(key, pixels);
        assertTrue(a.load(key).isPresent());
        assertTrue(b.load(key).isEmpty());
        assertTrue(c.load(key).isEmpty());
    }

    @Test
    void pruneDeletesOldestUntilUnderTarget(@TempDir Path dir) throws Exception {
        TileStore store = new TileStore(dir, "26.2", "overworld", 1L);
        int[] pixels = new int[TileKey.TILE_PIXELS * TileKey.TILE_PIXELS];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i;
        for (int i = 0; i < 6; i++) {
            TileKey key = new TileKey(0, i, 0);
            store.save(key, pixels);
            Files.setLastModifiedTime(store.pathFor(key), java.nio.file.attribute.FileTime.fromMillis(1_000_000L + i * 1000));
        }
        long oneFile = Files.size(store.pathFor(new TileKey(0, 0, 0)));
        TileStore.prune(dir, oneFile * 4, oneFile * 2);
        assertFalse(Files.exists(store.pathFor(new TileKey(0, 0, 0))));
        assertTrue(Files.exists(store.pathFor(new TileKey(0, 5, 0))));
    }
}
