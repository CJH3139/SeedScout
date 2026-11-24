package com.seedscout.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Dev-only accuracy check: generates real chunks on a running server and compares their dungeon spawners with
 * {@link DungeonFinder}. Must run on the server thread.
 */
public final class DungeonAudit {
    private static final Map<Identifier, Identifier> DUNGEON_MOBS = Map.of(
            Identifier.withDefaultNamespace("zombie"), DungeonMob.ZOMBIE.gridId(),
            Identifier.withDefaultNamespace("skeleton"), DungeonMob.SKELETON.gridId(),
            Identifier.withDefaultNamespace("spider"), DungeonMob.SPIDER.gridId());

    private DungeonAudit() {}

    /**
     * Totals over the compared chunks. {@code lines} holds one line per miss, false positive and mob mismatch, then
     * one per match, then notes.
     */
    public record Report(int real, int found, int falsePositives, int mobMismatches, List<String> lines) {}

    private record Spawner(int x, int y, int z, Identifier mob) {}

    /**
     * Compares chunks within {@code radiusChunks} of the center. Chunks one ring further out are generated too, so a
     * compared chunk's neighbors have decorated before it is read.
     */
    public static Report run(MinecraftServer server, int centerChunkX, int centerChunkZ, int radiusChunks) {
        ServerLevel level = server.overworld();
        int load = radiusChunks + 1;
        for (int dx = -load; dx <= load; dx++) {
            for (int dz = -load; dz <= load; dz++) {
                level.setChunkForced(centerChunkX + dx, centerChunkZ + dz, true);
            }
        }
        try {
            for (int dx = -load; dx <= load; dx++) {
                for (int dz = -load; dz <= load; dz++) {
                    level.getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, true);
                }
            }
            List<long[]> chunks = new ArrayList<>();
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    chunks.add(new long[]{centerChunkX + dx, centerChunkZ + dz});
                }
            }
            List<Spawner> real = new ArrayList<>();
            Map<Identifier, Integer> otherSpawners = new HashMap<>();
            for (long[] c : chunks) {
                LevelChunk chunk = (LevelChunk) level.getChunk((int) c[0], (int) c[1], ChunkStatus.FULL, true);
                for (Spawner spawner : spawners(level, chunk)) {
                    Identifier grid = DUNGEON_MOBS.get(spawner.mob());
                    if (grid == null) {
                        otherSpawners.merge(spawner.mob(), 1, Integer::sum);
                    } else {
                        real.add(new Spawner(spawner.x(), spawner.y(), spawner.z(), grid));
                    }
                }
            }
            List<FeatureFinder.FeatureHit> predicted = predict(SeedWorld.fromServer(server, Dimension.OVERWORLD), chunks);
            return compare(level, real, predicted, chunks.size(), otherSpawners);
        } finally {
            for (int dx = -load; dx <= load; dx++) {
                for (int dz = -load; dz <= load; dz++) {
                    level.setChunkForced(centerChunkX + dx, centerChunkZ + dz, false);
                }
            }
        }
    }

    private static List<Spawner> spawners(Level level, ChunkAccess chunk) {
        List<Spawner> found = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(Blocks.SPAWNER))) continue;
            int baseY = chunk.getMinY() + (i << 4);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y, z).is(Blocks.SPAWNER)) continue;
                        BlockPos pos = new BlockPos(minX + x, baseY + y, minZ + z);
                        found.add(new Spawner(pos.getX(), pos.getY(), pos.getZ(), mobAt(level, pos)));
                    }
                }
            }
        }
        return found;
    }

    private static Identifier mobAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SpawnerBlockEntity spawner)) {
            return Identifier.fromNamespaceAndPath("seedscout", "no_block_entity");
        }
        Entity entity = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
        return entity == null ? Identifier.fromNamespaceAndPath("seedscout", "no_entity") : EntityType.getKey(entity.getType());
    }

    private static List<FeatureFinder.FeatureHit> predict(SeedWorld world, List<long[]> chunks) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        try {
            List<Future<List<FeatureFinder.FeatureHit>>> futures = new ArrayList<>();
            for (long[] c : chunks) {
                futures.add(pool.submit(() -> world.featuresInChunk(DungeonFinder.ANY, (int) c[0], (int) c[1])));
            }
            List<FeatureFinder.FeatureHit> hits = new ArrayList<>();
            for (Future<List<FeatureFinder.FeatureHit>> future : futures) {
                hits.addAll(future.get());
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("Dungeon prediction failed", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Report compare(Level level, List<Spawner> real, List<FeatureFinder.FeatureHit> predicted, int chunkCount,
                                  Map<Identifier, Integer> otherSpawners) {
        Map<Long, FeatureFinder.FeatureHit> byPos = new HashMap<>();
        for (FeatureFinder.FeatureHit hit : predicted) byPos.put(BlockPos.asLong(hit.blockX(), hit.blockY(), hit.blockZ()), hit);
        Set<Long> realPos = new HashSet<>();
        List<String> problems = new ArrayList<>();
        List<String> matches = new ArrayList<>();
        int mismatches = 0;
        real.sort(Comparator.comparingInt(Spawner::x).thenComparingInt(Spawner::z).thenComparingInt(Spawner::y));
        for (Spawner spawner : real) {
            long key = BlockPos.asLong(spawner.x(), spawner.y(), spawner.z());
            realPos.add(key);
            FeatureFinder.FeatureHit hit = byPos.get(key);
            if (hit == null) {
                problems.add("miss " + describe(spawner.x(), spawner.y(), spawner.z()) + " mob=" + spawner.mob().getPath()
                        + nearby(predicted, spawner.x(), spawner.y(), spawner.z()) + context(level, spawner.x(), spawner.y(), spawner.z()));
            } else if (!hit.featureId().equals(spawner.mob())) {
                mismatches++;
                problems.add("mob " + describe(spawner.x(), spawner.y(), spawner.z()) + " real=" + spawner.mob().getPath()
                        + " predicted=" + hit.featureId().getPath());
            } else {
                matches.add("match " + spawner.x() + " " + spawner.y() + " " + spawner.z() + " " + spawner.mob().getPath());
            }
        }
        int falsePositives = 0;
        for (FeatureFinder.FeatureHit hit : predicted) {
            if (realPos.contains(BlockPos.asLong(hit.blockX(), hit.blockY(), hit.blockZ()))) continue;
            falsePositives++;
            BlockPos pos = new BlockPos(hit.blockX(), hit.blockY(), hit.blockZ());
            problems.add("false " + describe(hit.blockX(), hit.blockY(), hit.blockZ()) + " mob=" + hit.featureId().getPath()
                    + " realBlock=" + blockId(level, pos)
                    + " floor=" + blockId(level, pos.below()) + context(level, hit.blockX(), hit.blockY(), hit.blockZ()));
        }
        List<String> lines = new ArrayList<>(problems);
        lines.addAll(matches);
        lines.add("chunks compared=" + chunkCount + ", non-dungeon spawners skipped=" + otherSpawners
                + ", structure pieces cut short by level method=" + StructureReplay.pieceFailures());
        return new Report(real.size(), predicted.size(), falsePositives, mismatches, List.copyOf(lines));
    }

    // What else touches the room's largest possible box: structure pieces and fluids, the usual reasons a replay differs.
    private static String context(Level level, int x, int y, int z) {
        if (!(level instanceof ServerLevel server)) return "";
        BoundingBox room = new BoundingBox(x - 4, y - 1, z - 4, x + 4, y + 4, z + 4);
        Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<String> structures = new TreeSet<>();
        for (Structure structure : server.structureManager().getAllStructuresAt(new BlockPos(x, y, z)).keySet()) {
            for (BlockPos probe : List.of(new BlockPos(x, y, z), new BlockPos(room.minX(), y, room.minZ()),
                    new BlockPos(room.maxX(), y, room.minZ()), new BlockPos(room.minX(), y, room.maxZ()), new BlockPos(room.maxX(), y, room.maxZ()))) {
                StructureStart start = server.structureManager().getStructureAt(probe, structure);
                if (start.isValid() && start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().intersects(room))) {
                    structures.add(String.valueOf(registry.getKey(structure)));
                    break;
                }
            }
        }
        int fluids = 0;
        for (BlockPos pos : BlockPos.betweenClosed(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ())) {
            if (!level.getFluidState(pos).isEmpty()) fluids++;
        }
        return " structures=" + structures + " fluids=" + fluids;
    }

    private static String blockId(Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
    }

    private static String describe(int x, int y, int z) {
        return x + " " + y + " " + z + " (chunk " + (x >> 4) + "," + (z >> 4) + " local " + (x & 15) + "," + (z & 15) + ")";
    }

    // The closest predicted spawner in the same chunk, to tell a positional offset from a missing room.
    private static String nearby(List<FeatureFinder.FeatureHit> predicted, int x, int y, int z) {
        FeatureFinder.FeatureHit best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (FeatureFinder.FeatureHit hit : predicted) {
            if (hit.blockX() >> 4 != x >> 4 || hit.blockZ() >> 4 != z >> 4) continue;
            int d = Math.abs(hit.blockX() - x) + Math.abs(hit.blockY() - y) + Math.abs(hit.blockZ() - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = hit;
            }
        }
        return best == null ? " nearestPredicted=none"
                : " nearestPredicted=" + best.blockX() + " " + best.blockY() + " " + best.blockZ();
    }
}
