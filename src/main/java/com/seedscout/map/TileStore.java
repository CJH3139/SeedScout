package com.seedscout.map;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class TileStore {
    public static final int FORMAT = 1;
    private static final int PIXELS = TileKey.TILE_PIXELS * TileKey.TILE_PIXELS;
    private static final int RAW_BYTES = PIXELS * 4;

    private final Path root;

    public TileStore(Path cacheRoot, String versionTag, String dimension, long seed) {
        this.root = cacheRoot.resolve("v" + FORMAT + "-" + sanitize(versionTag)).resolve(dimension).resolve(Long.toString(seed));
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    Path pathFor(TileKey key) {
        return root.resolve(Integer.toString(key.lod())).resolve(key.tileX() + "_" + key.tileZ() + ".tile");
    }

    public Optional<int[]> load(TileKey key) {
        Path path = pathFor(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            byte[] packed = Files.readAllBytes(path);
            Inflater inflater = new Inflater();
            inflater.setInput(packed);
            byte[] raw = new byte[RAW_BYTES];
            int produced = inflater.inflate(raw);
            boolean complete = inflater.finished();
            inflater.end();
            if (produced != RAW_BYTES || !complete) {
                Files.deleteIfExists(path);
                return Optional.empty();
            }
            int[] pixels = new int[PIXELS];
            ByteBuffer.wrap(raw).asIntBuffer().get(pixels);
            return Optional.of(pixels);
        } catch (IOException | DataFormatException e) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // nothing else to do with an unreadable cache file
            }
            return Optional.empty();
        }
    }

    public void save(TileKey key, int[] pixels) {
        if (pixels.length != PIXELS) {
            throw new IllegalArgumentException("Unexpected tile size " + pixels.length);
        }
        Path path = pathFor(key);
        try {
            Files.createDirectories(path.getParent());
            ByteBuffer raw = ByteBuffer.allocate(RAW_BYTES);
            raw.asIntBuffer().put(pixels);
            Deflater deflater = new Deflater(Deflater.BEST_SPEED);
            deflater.setInput(raw.array());
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(RAW_BYTES / 8);
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            deflater.end();
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, out.toByteArray());
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the disk cache is best effort; the tile is still shown from memory
        }
    }

    public static void prune(Path cacheRoot, long maxBytes, long targetBytes) {
        if (!Files.isDirectory(cacheRoot)) return;
        List<Path> files = new ArrayList<>();
        long total = 0;
        try (Stream<Path> walk = Files.walk(cacheRoot)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                    total += Files.size(p);
                }
            }
        } catch (IOException e) {
            return;
        }
        if (total <= maxBytes) return;
        files.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return Long.MAX_VALUE;
            }
        }));
        for (Path p : files) {
            if (total <= targetBytes) break;
            try {
                long size = Files.size(p);
                Files.deleteIfExists(p);
                total -= size;
            } catch (IOException ignored) {
                // skip files that cannot be removed
            }
        }
    }
}
