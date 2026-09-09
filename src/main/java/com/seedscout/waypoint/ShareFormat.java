package com.seedscout.waypoint;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShareFormat {
    public record Shared(String name, int x, int z, String dimension) {}

    private static final Pattern PATTERN = Pattern.compile(
            "(.{1,48}?) @ (-?\\d{1,8}), (-?\\d{1,8}) \\[SeedScout/([a-z_]+)\\]");

    private ShareFormat() {}

    public static String format(String name, int x, int z, String dimension) {
        String clean = name.replaceAll("[\\[\\]@\\r\\n]", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() > 48) clean = clean.substring(0, 48).trim();
        if (clean.isEmpty()) clean = "Location";
        return clean + " @ " + x + ", " + z + " [SeedScout/" + dimension + "]";
    }

    public static Optional<Shared> parse(String text) {
        if (text == null || !text.contains("[SeedScout/")) return Optional.empty();
        Matcher m = PATTERN.matcher(text);
        if (!m.find()) return Optional.empty();
        String name = m.group(1);
        int cut = Math.max(name.lastIndexOf("> "), name.lastIndexOf(": "));
        if (cut >= 0 && cut + 2 < name.length()) name = name.substring(cut + 2);
        name = name.trim();
        if (name.isEmpty()) name = "Location";
        try {
            return Optional.of(new Shared(name, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), m.group(4)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
