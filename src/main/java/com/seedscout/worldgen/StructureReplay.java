package com.seedscout.worldgen;

import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Replays the structures vanilla places into a chunk before its dungeons, as {@code ChunkGenerator.applyBiomeDecoration}
 * does: per decoration step, each structure in registry order gets {@code setFeatureSeed(decorationSeed, index, step)}
 * and its starts that reach the chunk place their pieces, clipped to the chunk. Only mineshafts are replayed; their
 * corridors are the air a dungeon most often depends on.
 */
final class StructureReplay {
    private static final Set<Identifier> REPLAYED = Set.of(
            Identifier.withDefaultNamespace("mineshaft"), Identifier.withDefaultNamespace("mineshaft_mesa"));
    // ChunkGenerator.createReferences looks this many chunks out for starts.
    private static final int REFERENCE_RANGE = 8;
    private static final int CAPACITY = 65536;

    private record Placed(Holder<Structure> holder, Structure structure, int step, int index) {}

    private final long seed;
    private final ResourceKey<Level> dimension;
    private final RegistryAccess registries;
    private final ChunkGenerator generator;
    private final BiomeSource biomeSource;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState calculator;
    private final LevelHeightAccessor height;
    private final List<Holder<StructureSet>> sets;
    private final List<Placed> placed;
    private final BiomeManager biomes;
    // Which replayed structures start in a chunk. Starts themselves are rebuilt per placeAround call: their pieces
    // carry state that vanilla's single placement per chunk changes (MineShaftCorridor.hasPlacedSpider).
    private final Map<Long, List<Holder<Structure>>> startHolders = new ConcurrentHashMap<>();
    private static final Map<String, Long> PIECE_FAILURES = new ConcurrentHashMap<>();
    private static final int HEIGHT_STRIPES = 16;
    private static final int HEIGHT_STRIPE_CAPACITY = 1 << 13;
    // Noise heights by column and heightmap type. Pure, so sharing them across calls and threads is safe. Primitive
    // maps in locked stripes: 16 * 8192 entries is about 3 MB, where boxed entries cost several times that.
    private final Long2IntOpenHashMap[] baseHeights = new Long2IntOpenHashMap[HEIGHT_STRIPES];

    private StructureReplay(long seed, ResourceKey<Level> dimension, RegistryAccess registries, ChunkGenerator generator,
                            BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState calculator,
                            LevelHeightAccessor height, List<Holder<StructureSet>> sets, List<Placed> placed) {
        this.seed = seed;
        this.dimension = dimension;
        this.registries = registries;
        this.generator = generator;
        this.biomeSource = biomeSource;
        this.randomState = randomState;
        this.calculator = calculator;
        this.height = height;
        this.sets = sets;
        this.placed = placed;
        this.biomes = Compat.biomeManager(biomeSource, randomState, seed);
        for (int i = 0; i < HEIGHT_STRIPES; i++) baseHeights[i] = new Long2IntOpenHashMap();
    }

    /** Null if no replayed structure generates here or the lookup is not a full registry access. */
    static StructureReplay create(long seed, ResourceKey<Level> dimension, HolderLookup.Provider lookup, ChunkGenerator generator,
                                  BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState calculator,
                                  LevelHeightAccessor height) {
        if (!(lookup instanceof RegistryAccess registries)) return null;
        List<Holder<StructureSet>> sets = new ArrayList<>();
        for (Holder<StructureSet> set : calculator.possibleStructureSets()) {
            if (set.value().structures().stream().anyMatch(entry -> replayed(entry.structure()))) sets.add(set);
        }
        if (sets.isEmpty()) return null;
        // Vanilla groups the structure registry by step in registry order; the index within a step seeds the random.
        List<Placed> placed = new ArrayList<>();
        int[] next = new int[64];
        for (Holder.Reference<Structure> holder : registries.lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
            int step = holder.value().step().ordinal();
            int index = next[step]++;
            if (replayed(holder)) placed.add(new Placed(holder, holder.value(), step, index));
        }
        return new StructureReplay(seed, dimension, registries, generator, biomeSource, randomState, calculator, height,
                List.copyOf(sets), List.copyOf(placed));
    }

    private static boolean replayed(Holder<Structure> structure) {
        return structure.unwrapKey().map(key -> REPLAYED.contains(key.identifier())).orElse(false);
    }

    /**
     * Writes into {@code blocks} what the replayed structures of steps up to {@code lastStep} place in this chunk and
     * its eight neighbors, neighbors first. Vanilla only has a neighbor's pieces if that neighbor decorated first,
     * which depends on chunk scheduling; assuming it did matched the real game best near chunk edges (the audit in
     * {@link DungeonAudit} measured 3 false positives in 94 hits this way, against 9 with this chunk's pieces only).
     * Leaves {@code random} reseeded; callers seed it again for their own features.
     */
    void placeAround(DungeonRoom.Blocks blocks, WorldgenRandom random, int chunkX, int chunkZ, int lastStep) {
        // Fresh starts for this call only, shared by the nine placements like vanilla shares one start across chunks.
        Map<Long, List<StructureStart>> starts = new HashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                place(starts, blocks, random, chunkX + dx, chunkZ + dz, lastStep);
            }
        }
        place(starts, blocks, random, chunkX, chunkZ, lastStep);
    }

    /**
     * Pieces cut short, by the level method that stopped them, over all replays so far. {@code getLevel} is the
     * expected chest minecart case; anything else means the proxy level is missing a method.
     */
    static Map<String, Long> pieceFailures() {
        return Map.copyOf(PIECE_FAILURES);
    }

    private void place(Map<Long, List<StructureStart>> starts, DungeonRoom.Blocks blocks, WorldgenRandom random,
                       int chunkX, int chunkZ, int lastStep) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        List<StructureStart> reaching = new ArrayList<>();
        // Vanilla keeps a chunk's references in a LongOpenHashSet and places starts in its iteration order.
        LongOpenHashSet references = new LongOpenHashSet();
        for (int dx = -REFERENCE_RANGE; dx <= REFERENCE_RANGE; dx++) {
            for (int dz = -REFERENCE_RANGE; dz <= REFERENCE_RANGE; dz++) {
                int sx = chunkX + dx;
                int sz = chunkZ + dz;
                for (StructureStart start : startsAt(starts, sx, sz)) {
                    if (start.getBoundingBox().intersects(minX, minZ, minX + 15, minZ + 15)) {
                        references.add(ChunkPos.pack(sx, sz));
                        break;
                    }
                }
            }
        }
        if (references.isEmpty()) return;
        long decorationSeed = random.setDecorationSeed(seed, minX, minZ);
        for (LongIterator it = references.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            reaching.addAll(startsAt(starts, ChunkPos.getX(key), ChunkPos.getZ(key)));
        }
        BoundingBox writable = new BoundingBox(minX, height.getMinY() + 1, minZ, minX + 15, height.getMaxY(), minZ + 15);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        WorldGenLevel level = level(blocks, chunkPos);
        for (Placed entry : placed) {
            if (entry.step() > lastStep) continue;
            random.setFeatureSeed(decorationSeed, entry.index(), entry.step());
            for (StructureStart start : reaching) {
                if (start.getStructure() != entry.structure()) continue;
                if (!start.getBoundingBox().intersects(minX, minZ, minX + 15, minZ + 15)) continue;
                placeInChunk(start, level, random, writable, chunkPos);
            }
        }
    }

    // StructureStart.placeInChunk, one piece at a time so a piece the proxy cannot finish does not drop the rest.
    private void placeInChunk(StructureStart start, WorldGenLevel level, WorldgenRandom random, BoundingBox box, ChunkPos chunkPos) {
        List<StructurePiece> pieces = start.getPieces();
        if (pieces.isEmpty()) return;
        BoundingBox first = pieces.get(0).getBoundingBox();
        BlockPos center = first.getCenter();
        BlockPos reference = new BlockPos(center.getX(), first.minY(), center.getZ());
        for (StructurePiece piece : pieces) {
            if (!piece.getBoundingBox().intersects(box)) continue;
            try {
                piece.postProcess(level, null, generator, random, box, chunkPos, reference);
            } catch (UnsupportedOperationException e) {
                // The proxy throws with the method name. getLevel is a chest minecart, which needs a real level: the
                // piece's blocks so far stay, vanilla would go on placing. Others are counted for the audit to show.
                String method = String.valueOf(e.getMessage());
                if (PIECE_FAILURES.merge(method, 1L, Long::sum) == 1L && !method.equals("getLevel")) {
                    SeedScoutClient.LOGGER.warn("Dungeon structure replay: the proxy level has no {}, so some {} pieces are cut short",
                            method, start.getStructure().getClass().getSimpleName());
                }
            }
        }
    }

    /** Valid starts of the replayed sets in this chunk, built fresh into {@code starts} on first use in a call. */
    private List<StructureStart> startsAt(Map<Long, List<StructureStart>> starts, int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        List<StructureStart> local = starts.get(key);
        if (local != null) return local;
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        List<Holder<Structure>> holders = startHolders.get(key);
        List<StructureStart> result;
        if (holders != null) {
            result = new ArrayList<>(holders.size());
            for (Holder<Structure> holder : holders) generate(holder, pos).ifPresent(result::add);
        } else {
            result = select(pos);
            if (startHolders.size() > CAPACITY) startHolders.clear();
            startHolders.put(key, result.stream().map(start -> holderOf(start.getStructure())).toList());
        }
        starts.put(key, result);
        return result;
    }

    /** The starts {@code ChunkGenerator.createStructures} makes for the replayed sets in this chunk. */
    private List<StructureStart> select(ChunkPos pos) {
        List<StructureStart> found = new ArrayList<>(1);
        for (Holder<StructureSet> set : sets) {
            if (!set.value().placement().isStructureChunk(calculator, pos.x(), pos.z())) continue;
            List<StructureSet.StructureSelectionEntry> entries = set.value().structures();
            if (entries.size() == 1) {
                generate(entries.get(0).structure(), pos).ifPresent(found::add);
                continue;
            }
            List<StructureSet.StructureSelectionEntry> options = new ArrayList<>(entries);
            WorldgenRandom selection = new WorldgenRandom(new LegacyRandomSource(0L));
            selection.setLargeFeatureSeed(calculator.getLevelSeed(), pos.x(), pos.z());
            int total = 0;
            for (StructureSet.StructureSelectionEntry option : options) total += option.weight();
            while (!options.isEmpty()) {
                int choice = selection.nextInt(total);
                int index = 0;
                for (StructureSet.StructureSelectionEntry option : options) {
                    choice -= option.weight();
                    if (choice < 0) break;
                    index++;
                }
                StructureSet.StructureSelectionEntry picked = options.get(index);
                Optional<StructureStart> start = generate(picked.structure(), pos);
                if (start.isPresent()) {
                    found.add(start.get());
                    break;
                }
                options.remove(index);
                total -= picked.weight();
            }
        }
        return found.stream().filter(start -> replayed(start.getStructure())).toList();
    }

    private Holder<Structure> holderOf(Structure structure) {
        for (Placed entry : placed) {
            if (entry.structure() == structure) return entry.holder();
        }
        throw new IllegalStateException("not a replayed structure");
    }

    private boolean replayed(Structure structure) {
        for (Placed entry : placed) {
            if (entry.structure() == structure) return true;
        }
        return false;
    }

    private Optional<StructureStart> generate(Holder<Structure> structure, ChunkPos pos) {
        StructureStart start = Compat.generateStructure(structure, dimension, registries, generator, biomeSource, randomState,
                calculator.getLevelSeed(), pos, height);
        return start.isValid() ? Optional.of(start) : Optional.empty();
    }

    /** A level over {@code blocks} for structure pieces: reads and writes go to the overlay, ticks and block entities nowhere. */
    private WorldGenLevel level(DungeonRoom.Blocks blocks, ChunkPos chunkPos) {
        ProtoChunk[] scratch = new ProtoChunk[1];
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getBlockState" -> {
                BlockPos p = (BlockPos) args[0];
                yield blocks.get(p.getX(), p.getY(), p.getZ());
            }
            case "getFluidState" -> {
                BlockPos p = (BlockPos) args[0];
                yield blocks.get(p.getX(), p.getY(), p.getZ()).getFluidState();
            }
            case "setBlock" -> {
                BlockPos p = (BlockPos) args[0];
                blocks.set(p.getX(), p.getY(), p.getZ(), (BlockState) args[1]);
                yield true;
            }
            case "isStateAtPosition" -> {
                BlockPos p = (BlockPos) args[0];
                @SuppressWarnings("unchecked")
                Predicate<BlockState> test = (Predicate<BlockState>) args[1];
                yield test.test(blocks.get(p.getX(), p.getY(), p.getZ()));
            }
            case "getBlockEntity" -> args.length == 1 ? null : Optional.empty();
            case "scheduleTick" -> null;
            case "getChunk" -> {
                // Structure pieces mark shape updates on the chunk; a throwaway chunk absorbs them.
                if (scratch[0] == null) {
                    scratch[0] = new ProtoChunk(chunkPos, UpgradeData.EMPTY, height, PalettedContainerFactory.create(registries), null);
                }
                yield scratch[0];
            }
            case "getBiomeManager" -> biomes;
            case "getMinY" -> height.getMinY();
            case "getHeight" -> {
                if (args == null) yield height.getHeight();
                if (args.length != 3 || !(args[0] instanceof Heightmap.Types type)) yield unsupported(method.getName());
                // A chunk being decorated still has the heightmaps its noise primed.
                int x = (Integer) args[1];
                int z = (Integer) args[2];
                yield baseHeight(type, x, z);
            }
            case "getMaxY" -> height.getMaxY();
            case "getSeed" -> seed;
            case "registryAccess" -> registries;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "SeedScoutStructureLevel";
            default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : unsupported(method.getName());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private int baseHeight(Heightmap.Types type, int x, int z) {
        long key = BlockPos.asLong(x, type.ordinal(), z);
        Long2IntOpenHashMap stripe = baseHeights[(int) (HashCommon.mix(key) & (HEIGHT_STRIPES - 1))];
        synchronized (stripe) {
            if (stripe.containsKey(key)) return stripe.get(key);
        }
        int value = generator.getBaseHeight(x, z, type, height, randomState);
        synchronized (stripe) {
            if (stripe.size() >= HEIGHT_STRIPE_CAPACITY) stripe.clear();
            stripe.put(key, value);
        }
        return value;
    }

    private static Object unsupported(String name) {
        throw new UnsupportedOperationException(name);
    }
}
