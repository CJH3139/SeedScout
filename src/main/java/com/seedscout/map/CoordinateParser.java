package com.seedscout.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CoordinateParser {
    public record Coordinates(int x, int z) {}

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern AXIS_LABEL = Pattern.compile("(?i)[xyz]\\s*[=:]");

    private CoordinateParser() {}

    public static Optional<Coordinates> parse(String text) {
        if (text == null) return Optional.empty();
        String cleaned = AXIS_LABEL.matcher(text).replaceAll(" ");
        Matcher m = NUMBER.matcher(cleaned);
        List<Integer> numbers = new ArrayList<>();
        while (m.find()) {
            try {
                numbers.add((int) Math.floor(Double.parseDouble(m.group())));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (numbers.size() == 2) {
            return Optional.of(new Coordinates(numbers.get(0), numbers.get(1)));
        }
        if (numbers.size() == 3) {
            return Optional.of(new Coordinates(numbers.get(0), numbers.get(2)));
        }
        return Optional.empty();
    }
}
