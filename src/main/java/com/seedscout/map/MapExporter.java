package com.seedscout.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class MapExporter {
    public record Dot(int blockX, int blockZ, int color, int radius) {}

    private MapExporter() {}

    public static Path export(MapViewport viewport, Function<TileKey, Optional<Identifier>> textureFor,
                              Function<Identifier, AbstractTexture> textures, List<Dot> dots,
                              Path directory, String baseName) throws IOException {
        int width = viewport.width;
        int height = viewport.height;
        int lod = viewport.lod();
        int bpp = TileKey.blocksPerPixel(lod);
        Files.createDirectories(directory);
        Path file = directory.resolve(baseName + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".png");
        try (NativeImage image = new NativeImage(width, height, false)) {
            TileKey lastKey = null;
            NativeImage lastPixels = null;
            for (int sy = 0; sy < height; sy++) {
                int blockZ = (int) Math.floor(viewport.screenToWorldZ(viewport.top + sy + 0.5));
                for (int sx = 0; sx < width; sx++) {
                    int blockX = (int) Math.floor(viewport.screenToWorldX(viewport.left + sx + 0.5));
                    TileKey key = TileKey.fromBlock(lod, blockX, blockZ);
                    if (!key.equals(lastKey)) {
                        lastKey = key;
                        lastPixels = null;
                        Optional<Identifier> id = textureFor.apply(key);
                        if (id.isPresent() && textures.apply(id.get()) instanceof DynamicTexture dynamic) {
                            lastPixels = dynamic.getPixels();
                        }
                    }
                    int color = 0xFF202020;
                    if (lastPixels != null) {
                        int px = Math.min(TileKey.TILE_PIXELS - 1, (blockX - key.originX()) / bpp);
                        int pz = Math.min(TileKey.TILE_PIXELS - 1, (blockZ - key.originZ()) / bpp);
                        color = lastPixels.getPixel(px, pz);
                    }
                    image.setPixel(sx, sy, color);
                }
            }
            for (Dot dot : dots) {
                int cx = (int) Math.round(viewport.worldToScreenX(dot.blockX())) - viewport.left;
                int cy = (int) Math.round(viewport.worldToScreenZ(dot.blockZ())) - viewport.top;
                for (int dy = -dot.radius(); dy <= dot.radius(); dy++) {
                    for (int dx = -dot.radius(); dx <= dot.radius(); dx++) {
                        int x = cx + dx;
                        int y = cy + dy;
                        if (x < 0 || y < 0 || x >= width || y >= height) continue;
                        boolean edge = Math.abs(dx) == dot.radius() || Math.abs(dy) == dot.radius();
                        image.setPixel(x, y, edge ? 0xFF000000 : dot.color());
                    }
                }
            }
            image.writeToFile(file);
        }
        return file;
    }
}
