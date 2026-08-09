package first.wildfires.client.celestial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Deterministic, side-effect-free merger for Caelum-compatible star tables. */
final class StarTableLoader {

    private StarTableLoader() {
    }

    static Result load(Map<ResourceLocation, JsonElement> resources) {
        List<Star> stars = new ArrayList<>();
        List<Error> errors = new ArrayList<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    try {
                        ParsedFile parsed = parse(entry.getValue());
                        if (parsed.replace()) {
                            stars.clear();
                        }
                        stars.addAll(parsed.stars());
                    } catch (RuntimeException exception) {
                        errors.add(new Error(entry.getKey(), exception.getMessage()));
                    }
                });
        return new Result(List.copyOf(stars), List.copyOf(errors), resources.size());
    }

    private static ParsedFile parse(JsonElement element) {
        JsonObject root = element.getAsJsonObject();
        boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
        JsonArray entries = root.getAsJsonArray("stars");
        if (entries == null) {
            throw new IllegalArgumentException("missing stars array");
        }
        List<Star> stars = new ArrayList<>(entries.size());
        for (JsonElement value : entries) {
            JsonObject star = value.getAsJsonObject();
            String colorText = star.has("color") ? star.get("color").getAsString() : "ffffff";
            if (colorText.startsWith("#")) {
                colorText = colorText.substring(1);
            }
            if (!colorText.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("invalid RGB value " + colorText);
            }
            double ascension = finite(star, "ascension");
            double declination = finite(star, "declination");
            double magnitude = finite(star, "magnitude");
            stars.add(new Star(star.has("name") ? star.get("name").getAsString() : "",
                    ascension, declination, magnitude, Integer.parseInt(colorText, 16)));
        }
        return new ParsedFile(replace, stars);
    }

    private static double finite(JsonObject star, String name) {
        double value = star.get(name).getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite " + name);
        }
        return value;
    }

    record Result(List<Star> stars, List<Error> errors, int resourceCount) {
    }

    record Error(ResourceLocation resource, String message) {
    }

    record Star(String name, double ascension, double declination, double magnitude, int rgb) {
    }

    private record ParsedFile(boolean replace, List<Star> stars) {
    }
}
