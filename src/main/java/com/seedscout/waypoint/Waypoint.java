package com.seedscout.waypoint;

import net.minecraft.resources.Identifier;

public record Waypoint(String name, int x, int z, Identifier structureId, Identifier dimension) {}
