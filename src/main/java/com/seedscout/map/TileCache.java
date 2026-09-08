package com.seedscout.map;

import com.seedscout.SeedScoutClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

public final class TileCache {
    public static final int CAPACITY = 512;

    private enum State { PENDING, READY, FAILED }

    private static final class Entry {
        State state = State.PENDING;
        Identifier textureId;
    }

    private final TextureManager textureManager;
    private final LinkedHashMap<TileKey, Entry> entries = new LinkedHashMap<>(256, 0.75f, true);
    private volatile Set<TileKey> visible = Set.of();
    private volatile int generation = 0;

    public TileCache(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public Optional<Identifier> textureFor(TileKey key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.state != State.READY) {
            return Optional.empty();
        }
        return Optional.of(entry.textureId);
    }

    public boolean isFailed(TileKey key) {
        Entry entry = entries.get(key);
        return entry != null && entry.state == State.FAILED;
    }

    public int pendingCount() {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.state == State.PENDING) count++;
        }
        return count;
    }

    public void request(List<TileKey> visibleKeys, double centerX, double centerZ, TilePixels.ColorSampler sampler) {
        if (visibleKeys.size() > CAPACITY) {
            visible = Set.of();
            evict();
            return;
        }
        visible = Set.copyOf(visibleKeys);
        List<TileKey> missing = new ArrayList<>();
        for (TileKey key : visibleKeys) {
            if (!entries.containsKey(key)) {
                missing.add(key);
            }
        }
        final int gen = generation;
        for (TileKey key : missing) {
            Entry entry = new Entry();
            entries.put(key, entry);
            double span = TileKey.tileSpanBlocks(key.lod());
            double dx = key.originX() + span / 2 - centerX;
            double dz = key.originZ() + span / 2 - centerZ;
            double priority = dx * dx + dz * dz;
            MapWorker.submitOrdered(priority, () -> renderJob(key, gen, sampler));
        }
        evict();
    }

    private void renderJob(TileKey key, int gen, TilePixels.ColorSampler sampler) {
        MinecraftClient client = MinecraftClient.getInstance();
        Set<TileKey> visibleSnapshot = visible;
        if (gen != generation || !visibleSnapshot.contains(key)) {
            client.execute(() -> {
                if (gen == generation) entries.remove(key);
            });
            return;
        }
        NativeImage image = null;
        try {
            int[] pixels = TilePixels.render(key, sampler);
            image = new NativeImage(TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, false);
            for (int pz = 0; pz < TileKey.TILE_PIXELS; pz++) {
                for (int px = 0; px < TileKey.TILE_PIXELS; px++) {
                    image.setColorArgb(px, pz, pixels[pz * TileKey.TILE_PIXELS + px]);
                }
            }
        } catch (Throwable t) {
            SeedScoutClient.LOGGER.error("Tile {} failed", key, t);
            if (image != null) {
                image.close();
            }
            client.execute(() -> markFailed(key, gen));
            return;
        }
        final NativeImage finalImage = image;
        client.execute(() -> upload(key, gen, finalImage));
    }

    private void markFailed(TileKey key, int gen) {
        if (gen != generation) return;
        Entry entry = entries.get(key);
        if (entry != null) entry.state = State.FAILED;
    }

    private void upload(TileKey key, int gen, NativeImage image) {
        Entry entry = gen == generation ? entries.get(key) : null;
        if (entry == null) {
            image.close();
            return;
        }
        Identifier id = Identifier.of(SeedScoutClient.MOD_ID,
                "tile/" + key.lod() + "/" + key.tileX() + "/" + key.tileZ() + "/" + gen);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
        textureManager.registerTexture(id, texture);
        entry.textureId = id;
        entry.state = State.READY;
    }

    private void evict() {
        Iterator<Map.Entry<TileKey, Entry>> iterator = entries.entrySet().iterator();
        while (entries.size() > CAPACITY && iterator.hasNext()) {
            Map.Entry<TileKey, Entry> oldest = iterator.next();
            if (visible.contains(oldest.getKey())) {
                continue;
            }
            destroy(oldest.getValue());
            iterator.remove();
        }
    }

    private void destroy(Entry entry) {
        if (entry.state == State.READY) {
            textureManager.destroyTexture(entry.textureId);
        }
    }

    public void clear() {
        generation++;
        for (Entry entry : entries.values()) {
            destroy(entry);
        }
        entries.clear();
        visible = Set.of();
    }
}
