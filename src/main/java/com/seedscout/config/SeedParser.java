package com.seedscout.config;

import java.util.OptionalLong;

public final class SeedParser {
    private SeedParser() {}

    public static OptionalLong parse(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(trimmed));
        } catch (NumberFormatException ignored) {
            return OptionalLong.of(trimmed.hashCode());
        }
    }
}
