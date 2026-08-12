package first.wildfires.client.space.render;

import first.wildfires.thirdparty.genesisadapt.GenesisCubeAtlasLayout;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Seam-safe Wildfires fallback surfaces for resource packs that do not supply Genesis cubemaps. */
public final class OrbitProceduralTexture {

    private OrbitProceduralTexture() {
    }

    public static Rgba surface(ResourceLocation body, GenesisCubeAtlasLayout.Direction direction) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(direction, "direction");
        Palette palette = palette(body);
        double seed = unitSeed(body);
        double latitude = Math.abs(direction.y());
        double broad = fbm(direction, seed, 4);
        double detail = fbm(new GenesisCubeAtlasLayout.Direction(
                direction.z(), direction.x(), direction.y()), seed + 0.317D, 3);
        double bands = 0.5D + 0.5D * Math.sin((direction.y() * palette.bandFrequency
                + seed * 3.0D) * Math.PI);
        double mix = clamp(broad * 0.62D + detail * 0.22D
                + bands * palette.bandWeight, 0.0D, 1.0D);
        Rgba color = blend(palette.low, palette.high, smooth(mix));
        if (palette.iceCaps && latitude > 0.72D) {
            color = blend(color, new Rgba(0.88D, 0.94D, 1.0D, 1.0D),
                    smooth((latitude - 0.72D) / 0.24D));
        }
        return color;
    }

    /** Tint for the NTM-style distant point, derived from the same surface identity. */
    public static Rgba pointColor(ResourceLocation body) {
        Palette palette = palette(Objects.requireNonNull(body, "body"));
        return blend(palette.low, palette.high, 0.68D);
    }

    /** Seam-safe orbital cloud coverage sampled from one shared three-dimensional field. */
    public static Rgba cloud(ResourceLocation body, GenesisCubeAtlasLayout.Direction direction) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(direction, "direction");
        double seed = unitSeed(body) + 7.193D;
        double broad = fbm(direction, seed, 5);
        GenesisCubeAtlasLayout.Direction folded = new GenesisCubeAtlasLayout.Direction(
                direction.y(), direction.z(), direction.x());
        double detail = fbm(folded, seed + 0.431D, 4);
        double coverage = smooth(clamp((broad * 0.72D + detail * 0.28D - 0.47D) / 0.34D,
                0.0D, 1.0D));
        coverage *= coverage;
        return new Rgba(1.0D, 1.0D, 1.0D, coverage);
    }

    public static double unitSeed(ResourceLocation body) {
        Objects.requireNonNull(body, "body");
        long hash = 0xcbf29ce484222325L;
        String text = body.toString();
        for (int index = 0; index < text.length(); index++) {
            hash ^= text.charAt(index);
            hash *= 0x100000001b3L;
        }
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static double fbm(GenesisCubeAtlasLayout.Direction direction, double seed, int octaves) {
        double value = 0.0D;
        double amplitude = 0.58D;
        double total = 0.0D;
        double frequency = 1.35D;
        for (int octave = 0; octave < octaves; octave++) {
            double sample = valueNoise(direction.x() * frequency, direction.y() * frequency,
                    direction.z() * frequency, seed + octave * 19.19D);
            value += sample * amplitude;
            total += amplitude;
            amplitude *= 0.50D;
            frequency *= 2.03D;
        }
        return clamp(value / total, 0.0D, 1.0D);
    }

    /** Continuous 3-D value noise: cubemap faces sample one shared field, so edges stay seamless. */
    private static double valueNoise(double x, double y, double z, double seed) {
        long x0 = (long) Math.floor(x);
        long y0 = (long) Math.floor(y);
        long z0 = (long) Math.floor(z);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double fz = fade(z - z0);
        double x00 = lerp(hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed), fx);
        double x10 = lerp(hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed), fx);
        double x01 = lerp(hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed), fx);
        double x11 = lerp(hash(x0, y0 + 1, z0 + 1, seed),
                hash(x0 + 1, y0 + 1, z0 + 1, seed), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static double hash(long x, long y, long z, double seed) {
        long bits = Double.doubleToLongBits(seed);
        bits ^= x * 0x9E3779B97F4A7C15L;
        bits ^= y * 0xC2B2AE3D27D4EB4FL;
        bits ^= z * 0x165667B19E3779F9L;
        bits ^= bits >>> 30;
        bits *= 0xBF58476D1CE4E5B9L;
        bits ^= bits >>> 27;
        bits *= 0x94D049BB133111EBL;
        bits ^= bits >>> 31;
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static Palette palette(ResourceLocation body) {
        return switch (body.getPath()) {
            case "earth" -> new Palette(new Rgba(0.018D, 0.08D, 0.34D, 1.0D),
                    new Rgba(0.22D, 0.58D, 0.18D, 1.0D), 0.04D, 5.0D, true);
            case "moon" -> new Palette(new Rgba(0.12D, 0.13D, 0.15D, 1.0D),
                    new Rgba(0.72D, 0.72D, 0.69D, 1.0D), 0.03D, 4.0D, false);
            case "mars" -> new Palette(new Rgba(0.22D, 0.045D, 0.018D, 1.0D),
                    new Rgba(0.86D, 0.31D, 0.075D, 1.0D), 0.05D, 5.0D, true);
            case "mercury" -> new Palette(new Rgba(0.12D, 0.11D, 0.10D, 1.0D),
                    new Rgba(0.68D, 0.61D, 0.52D, 1.0D), 0.02D, 4.0D, false);
            case "venus" -> new Palette(new Rgba(0.36D, 0.16D, 0.035D, 1.0D),
                    new Rgba(0.98D, 0.72D, 0.22D, 1.0D), 0.30D, 8.0D, false);
            case "jupiter" -> new Palette(new Rgba(0.31D, 0.13D, 0.055D, 1.0D),
                    new Rgba(0.96D, 0.76D, 0.52D, 1.0D), 0.58D, 13.0D, false);
            case "saturn" -> new Palette(new Rgba(0.43D, 0.29D, 0.12D, 1.0D),
                    new Rgba(0.98D, 0.87D, 0.61D, 1.0D), 0.55D, 16.0D, false);
            case "uranus" -> new Palette(new Rgba(0.12D, 0.43D, 0.53D, 1.0D),
                    new Rgba(0.58D, 0.96D, 0.98D, 1.0D), 0.42D, 10.0D, false);
            case "neptune" -> new Palette(new Rgba(0.018D, 0.055D, 0.30D, 1.0D),
                    new Rgba(0.17D, 0.47D, 0.98D, 1.0D), 0.38D, 12.0D, false);
            case "pluto", "charon" -> new Palette(new Rgba(0.13D, 0.10D, 0.09D, 1.0D),
                    new Rgba(0.72D, 0.58D, 0.43D, 1.0D), 0.04D, 5.0D, true);
            case "nemesis" -> new Palette(new Rgba(0.50D, 0.025D, 0.01D, 1.0D),
                    new Rgba(1.0D, 0.42D, 0.07D, 1.0D), 0.28D, 9.0D, false);
            default -> {
                double seed = unitSeed(body);
                Rgba low = new Rgba(0.055D + seed * 0.16D,
                        0.06D + (1.0D - seed) * 0.15D, 0.08D + seed * 0.18D, 1.0D);
                Rgba high = new Rgba(0.40D + seed * 0.40D,
                        0.35D + (1.0D - seed) * 0.34D, 0.31D + seed * 0.32D, 1.0D);
                yield new Palette(low, high, 0.08D, 6.0D, body.getPath().contains("moon"));
            }
        };
    }

    private static Rgba blend(Rgba from, Rgba to, double progress) {
        double value = clamp(progress, 0.0D, 1.0D);
        return new Rgba(lerp(from.red, to.red, value), lerp(from.green, to.green, value),
                lerp(from.blue, to.blue, value), lerp(from.alpha, to.alpha, value));
    }

    private static double smooth(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Rgba(double red, double green, double blue, double alpha) {
        public Rgba {
            if (!unit(red) || !unit(green) || !unit(blue) || !unit(alpha)) {
                throw new IllegalArgumentException("Procedural color components must be within 0..1");
            }
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    private record Palette(Rgba low, Rgba high, double bandWeight,
                           double bandFrequency, boolean iceCaps) {
    }
}
