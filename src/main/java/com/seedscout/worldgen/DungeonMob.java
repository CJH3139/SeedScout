package com.seedscout.worldgen;

import net.minecraft.resources.Identifier;

public enum DungeonMob {
    SKELETON("skeleton_dungeon"),
    ZOMBIE("zombie_dungeon"),
    SPIDER("spider_dungeon");

    private final Identifier gridId;

    DungeonMob(String path) {
        this.gridId = Identifier.fromNamespaceAndPath("seedscout", path);
    }

    public Identifier gridId() {
        return gridId;
    }

    /** Maps vanilla's {@code MOBS[random.nextInt(4)]} roll: skeleton, zombie, zombie, spider. */
    public static DungeonMob fromRoll(int roll) {
        return switch (roll) {
            case 0 -> SKELETON;
            case 1, 2 -> ZOMBIE;
            case 3 -> SPIDER;
            default -> throw new IllegalArgumentException("roll " + roll);
        };
    }
}
