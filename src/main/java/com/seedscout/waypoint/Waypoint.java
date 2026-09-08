package com.seedscout.waypoint;

import net.minecraft.util.Identifier;

public record Waypoint(String name, int x, int z, Identifier structureId) {}
