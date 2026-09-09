package com.seedscout.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.seedscout.SeedScoutClient;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public final class TileCache {
    public static final int CAPACITY = 512;
    private static final int COARSE_STEP = 2;

    private enum State { PENDING, READY, FAILED }

    private static final class Entry {
        State state = State.PENDING;
        Identifier textureId;
    }

    private final TextureManager textureManager;
    private final LinkedHashMap<TileKey, Entry> entries = new LinkedHashMap<>(256, 0.75f, true);
    private volatile Set<TileKey> visible = Set.of();
    private volatile int generation = 0;

    private final TileStore store;

    public TileCache(TextureManager textureManager) {
        this(textureManager, null);
    }

    public TileCache(TextureManager textureManager, TileStore store) {
        this.textureManager = textureManager;
        this.store = store;
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
        Set<TileKey> wanted = new HashSet<>(visibleKeys);
        List<TileKey> coarse = new ArrayList<>();
        if (!visibleKeys.isEmpty()) {
            int lod = visibleKeys.get(0).lod();
            int coarseLod = Math.min(lod + COARSE_STEP, TileKey.LOD_COUNT - 1);
            if (coarseLod > lod) {
                for (TileKey key : visibleKeys) {
                    TileKey parent = TileKey.coarserCovering(key, coarseLod);
                    if (wanted.add(parent)) coarse.add(parent);
                }
            }
        }
        List<TileKey> ring = List.of();
        if (visibleKeys.size() * 2 + coarse.size() < CAPACITY / 2) {
            ring = TileKey.ring(visibleKeys);
            wanted.addAll(ring);
        }
        visible = Set.copyOf(wanted);
        final int gen = generation;
        submitMissing(coarse, centerX, centerZ, sampler, gen, -1.0e15);
        submitMissing(visibleKeys, centerX, centerZ, sampler, gen, 0.0);
        submitMissing(ring, centerX, centerZ, sampler, gen, 1.0e15);
        evict();
    }

    private void submitMissing(List<TileKey> keys, double centerX, double centerZ, TilePixels.ColorSampler sampler, int gen, double bias) {
        for (TileKey key : keys) {
            if (entries.containsKey(key)) continue;
            Entry entry = new Entry();
            entries.put(key, entry);
            double span = TileKey.tileSpanBlocks(key.lod());
            double dx = key.originX() + span / 2 - centerX;
            double dz = key.originZ() + span / 2 - centerZ;
            double priority = bias + dx * dx + dz * dz;
            MapWorker.submitOrdered(priority, () -> renderJob(key, gen, sampler));
        }
    }

    private void renderJob(TileKey key, int gen, TilePixels.ColorSampler sampler) {
        Minecraft client = Minecraft.getInstance();
        Set<TileKey> visibleSnapshot = visible;
        if (gen != generation || !visibleSnapshot.contains(key)) {
            client.execute(() -> {
                if (gen == generation) entries.remove(key);
            });
            return;
        }
        NativeImage image = null;
        try {
            int[] pixels = store == null ? null : store.load(key).orElse(null);
            boolean fromDisk = pixels != null;
            if (!fromDisk) {
                pixels = TilePixels.render(key, sampler, () -> gen != generation || !visible.contains(key));
            }
            if (pixels == null) {
                client.execute(() -> {
                    if (gen == generation) entries.remove(key);
                });
                return;
            }
            if (!fromDisk && store != null) {
                store.save(key, pixels);
            }
            image = new NativeImage(TileKey.TILE_PIXELS, TileKey.TILE_PIXELS, false);
            for (int pz = 0; pz < TileKey.TILE_PIXELS; pz++) {
                for (int px = 0; px < TileKey.TILE_PIXELS; px++) {
                    image.setPixel(px, pz, pixels[pz * TileKey.TILE_PIXELS + px]);
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
        Identifier id = Identifier.fromNamespaceAndPath(SeedScoutClient.MOD_ID,
                "tile/" + key.lod() + "/" + key.tileX() + "/" + key.tileZ() + "/" + gen);
        DynamicTexture texture = new DynamicTexture(id::toString, image);
        textureManager.register(id, texture);
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
            textureManager.release(entry.textureId);
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
