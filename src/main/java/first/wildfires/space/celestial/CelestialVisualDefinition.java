package first.wildfires.space.celestial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Common-side immutable presentation data for a square near-body renderer and its shells. */
public record CelestialVisualDefinition(
        Optional<ResourceLocation> surfaceAtlas,
        boolean proceduralSurface,
        ResourceLocation surfaceAtlasLayout,
        ResourceLocation nearBodyRenderer,
        Atmosphere atmosphere,
        CloudLayer clouds) {

    private static final Codec<CelestialVisualDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("surface_atlas")
                    .forGetter(CelestialVisualDefinition::surfaceAtlas),
            Codec.BOOL.optionalFieldOf("procedural_surface", false)
                    .forGetter(CelestialVisualDefinition::proceduralSurface),
            ResourceLocation.CODEC.optionalFieldOf("surface_atlas_layout",
                    id("three_by_two_v1")).forGetter(CelestialVisualDefinition::surfaceAtlasLayout),
            ResourceLocation.CODEC.optionalFieldOf("near_body_renderer",
                    id("cube")).forGetter(CelestialVisualDefinition::nearBodyRenderer),
            Atmosphere.CODEC.optionalFieldOf("atmosphere", Atmosphere.NONE)
                    .forGetter(CelestialVisualDefinition::atmosphere),
            CloudLayer.CODEC.optionalFieldOf("clouds", CloudLayer.NONE)
                    .forGetter(CelestialVisualDefinition::clouds)
    ).apply(instance, CelestialVisualDefinition::new));

    public static final Codec<CelestialVisualDefinition> CODEC = RAW_CODEC.comapFlatMap(
            CelestialVisualDefinition::validated,
            definition -> definition);

    public CelestialVisualDefinition {
        surfaceAtlas = Objects.requireNonNull(surfaceAtlas, "surfaceAtlas");
        Objects.requireNonNull(surfaceAtlasLayout, "surfaceAtlasLayout");
        Objects.requireNonNull(nearBodyRenderer, "nearBodyRenderer");
        Objects.requireNonNull(atmosphere, "atmosphere");
        Objects.requireNonNull(clouds, "clouds");
        validate(surfaceAtlas, proceduralSurface, atmosphere, clouds);
    }

    private static DataResult<CelestialVisualDefinition> validated(CelestialVisualDefinition definition) {
        try {
            validate(definition.surfaceAtlas, definition.proceduralSurface,
                    definition.atmosphere, definition.clouds);
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validate(Optional<ResourceLocation> surfaceAtlas, boolean proceduralSurface,
                                 Atmosphere atmosphere, CloudLayer clouds) {
        if (surfaceAtlas.isEmpty() && !proceduralSurface) {
            throw new IllegalArgumentException(
                    "A celestial surface requires surface_atlas, procedural_surface, or both for fallback");
        }
        if (clouds.enabled()) {
            if (!atmosphere.enabled()) {
                throw new IllegalArgumentException("An enabled orbital cloud shell requires an atmosphere shell");
            }
            if (clouds.radiusMultiplier() >= atmosphere.radiusMultiplier()) {
                throw new IllegalArgumentException("Cloud shell radius must be inside atmosphere shell: "
                        + clouds.radiusMultiplier() + " >= " + atmosphere.radiusMultiplier());
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    public record Atmosphere(
            boolean enabled,
            double radiusMultiplier,
            double density,
            Color color,
            Optional<Color> sunsetColor,
            Optional<Color> nightColor,
            double dayBrightness,
            double sunsetBrightness,
            double nightBrightness,
            double dayTransition,
            double nightTransition,
            double limbStrength,
            double limbPower,
            double maxOpacity,
            double exposure) {

        public static final Atmosphere NONE = new Atmosphere(false, 1.0D, 0.0D,
                new Color(0.0D, 0.0D, 0.0D), Optional.empty(), Optional.empty(),
                1.0D, 1.0D, 1.0D, 3.0D, 4.0D, 0.6D, 2.0D, 0.72D, 1.0D);

        private static final Codec<Atmosphere> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Atmosphere::enabled),
                Codec.DOUBLE.optionalFieldOf("radius_multiplier", 1.025D)
                        .forGetter(Atmosphere::radiusMultiplier),
                Codec.DOUBLE.optionalFieldOf("density", 1.0D).forGetter(Atmosphere::density),
                Color.CODEC.optionalFieldOf("color", new Color(1.0D, 1.0D, 1.0D))
                        .forGetter(Atmosphere::color),
                Color.CODEC.optionalFieldOf("sunset_color").forGetter(Atmosphere::sunsetColor),
                Color.CODEC.optionalFieldOf("night_color").forGetter(Atmosphere::nightColor),
                Codec.DOUBLE.optionalFieldOf("day_brightness", 1.0D)
                        .forGetter(Atmosphere::dayBrightness),
                Codec.DOUBLE.optionalFieldOf("sunset_brightness", 1.0D)
                        .forGetter(Atmosphere::sunsetBrightness),
                Codec.DOUBLE.optionalFieldOf("night_brightness", 1.0D)
                        .forGetter(Atmosphere::nightBrightness),
                Codec.DOUBLE.optionalFieldOf("day_transition", 3.0D)
                        .forGetter(Atmosphere::dayTransition),
                Codec.DOUBLE.optionalFieldOf("night_transition", 4.0D)
                        .forGetter(Atmosphere::nightTransition),
                Codec.DOUBLE.optionalFieldOf("limb_strength", 0.6D)
                        .forGetter(Atmosphere::limbStrength),
                Codec.DOUBLE.optionalFieldOf("limb_power", 2.0D)
                        .forGetter(Atmosphere::limbPower),
                Codec.DOUBLE.optionalFieldOf("max_opacity", 0.72D)
                        .forGetter(Atmosphere::maxOpacity),
                Codec.DOUBLE.optionalFieldOf("exposure", 1.0D)
                        .forGetter(Atmosphere::exposure)
        ).apply(instance, Atmosphere::new));

        public static final Codec<Atmosphere> CODEC = RAW_CODEC.comapFlatMap(
                Atmosphere::validated,
                atmosphere -> atmosphere);

        public Atmosphere {
            Objects.requireNonNull(color, "color");
            sunsetColor = Objects.requireNonNull(sunsetColor, "sunsetColor");
            nightColor = Objects.requireNonNull(nightColor, "nightColor");
            if (!Double.isFinite(radiusMultiplier) || !Double.isFinite(density)
                    || !Double.isFinite(dayBrightness) || !Double.isFinite(sunsetBrightness)
                    || !Double.isFinite(nightBrightness) || !Double.isFinite(dayTransition)
                    || !Double.isFinite(nightTransition) || !Double.isFinite(limbStrength)
                    || !Double.isFinite(limbPower) || !Double.isFinite(maxOpacity)
                    || !Double.isFinite(exposure)) {
                throw new IllegalArgumentException("Atmosphere numeric values must be finite");
            }
            if (enabled && (radiusMultiplier <= 1.0D || radiusMultiplier > 16.0D)) {
                throw new IllegalArgumentException("Enabled atmosphere radius_multiplier must be within (1, 16]");
            }
            if (!enabled && radiusMultiplier < 1.0D) {
                throw new IllegalArgumentException("Disabled atmosphere radius_multiplier cannot be below 1");
            }
            if (density < 0.0D || density > 100.0D) {
                throw new IllegalArgumentException("Atmosphere density must be within 0..100");
            }
            if (dayBrightness < 0.0D || dayBrightness > 8.0D
                    || sunsetBrightness < 0.0D || sunsetBrightness > 8.0D
                    || nightBrightness < 0.0D || nightBrightness > 8.0D) {
                throw new IllegalArgumentException("Atmosphere brightness must be within 0..8");
            }
            if (dayTransition <= 0.0D || dayTransition > 32.0D
                    || nightTransition <= 0.0D || nightTransition > 32.0D) {
                throw new IllegalArgumentException("Atmosphere transitions must be within (0, 32]");
            }
            if (limbStrength < 0.0D || limbStrength > 8.0D
                    || limbPower < 0.25D || limbPower > 8.0D) {
                throw new IllegalArgumentException("Atmosphere limb strength/power are outside safe bounds");
            }
            if (maxOpacity < 0.0D || maxOpacity > 1.0D) {
                throw new IllegalArgumentException("Atmosphere max_opacity must be within 0..1");
            }
            if (exposure < 0.0D || exposure > 8.0D) {
                throw new IllegalArgumentException("Atmosphere exposure must be within 0..8");
            }
        }

        public Color resolvedSunsetColor() {
            return sunsetColor.orElseGet(() -> color.multiply(0.78D, 0.62D, 0.52D));
        }

        public Color resolvedNightColor() {
            return nightColor.orElseGet(() -> color.multiply(0.20D, 0.10D, 0.40D));
        }

        private static DataResult<Atmosphere> validated(Atmosphere atmosphere) {
            try {
                return DataResult.success(new Atmosphere(atmosphere.enabled, atmosphere.radiusMultiplier,
                        atmosphere.density, atmosphere.color, atmosphere.sunsetColor,
                        atmosphere.nightColor, atmosphere.dayBrightness, atmosphere.sunsetBrightness,
                        atmosphere.nightBrightness, atmosphere.dayTransition, atmosphere.nightTransition,
                        atmosphere.limbStrength, atmosphere.limbPower, atmosphere.maxOpacity,
                        atmosphere.exposure));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }

    public record CloudLayer(
            boolean enabled,
            CloudMapping mapping,
            Optional<ResourceLocation> texture,
            boolean procedural,
            double radiusMultiplier,
            double opacity,
            Color tint,
            double rotationPeriodTfcDays,
            Axis rotationAxis,
            double rotationOffset,
            double shadowStrength) {

        public static final CloudLayer NONE = new CloudLayer(false, CloudMapping.EQUIRECTANGULAR,
                Optional.empty(), false, 1.0D, 0.0D, new Color(1.0D, 1.0D, 1.0D),
                1.0D, Axis.UP, 0.0D, 0.0D);

        private static final Codec<CloudLayer> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CloudLayer::enabled),
                CloudMapping.CODEC.optionalFieldOf("mapping", CloudMapping.EQUIRECTANGULAR)
                        .forGetter(CloudLayer::mapping),
                ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(CloudLayer::texture),
                Codec.BOOL.optionalFieldOf("procedural", false).forGetter(CloudLayer::procedural),
                Codec.DOUBLE.optionalFieldOf("radius_multiplier", 1.012D)
                        .forGetter(CloudLayer::radiusMultiplier),
                Codec.DOUBLE.optionalFieldOf("opacity", 0.7D).forGetter(CloudLayer::opacity),
                Color.CODEC.optionalFieldOf("tint", new Color(1.0D, 1.0D, 1.0D))
                        .forGetter(CloudLayer::tint),
                Codec.DOUBLE.optionalFieldOf("rotation_period_tfc_days", 1.0D)
                        .forGetter(CloudLayer::rotationPeriodTfcDays),
                Axis.CODEC.optionalFieldOf("rotation_axis", Axis.UP).forGetter(CloudLayer::rotationAxis),
                Codec.DOUBLE.optionalFieldOf("rotation_offset", 0.0D).forGetter(CloudLayer::rotationOffset),
                Codec.DOUBLE.optionalFieldOf("shadow_strength", 0.0D).forGetter(CloudLayer::shadowStrength)
        ).apply(instance, CloudLayer::new));

        public static final Codec<CloudLayer> CODEC = RAW_CODEC.comapFlatMap(
                CloudLayer::validated,
                clouds -> clouds);

        public CloudLayer {
            Objects.requireNonNull(mapping, "mapping");
            texture = Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(tint, "tint");
            Objects.requireNonNull(rotationAxis, "rotationAxis");
            if (!Double.isFinite(radiusMultiplier) || !Double.isFinite(opacity)
                    || !Double.isFinite(rotationPeriodTfcDays) || !Double.isFinite(rotationOffset)
                    || !Double.isFinite(shadowStrength)) {
                throw new IllegalArgumentException("Cloud radius, opacity, rotation and shadow values must be finite");
            }
            if (enabled) {
                if (radiusMultiplier <= 1.0D || radiusMultiplier > 16.0D) {
                    throw new IllegalArgumentException("Enabled cloud radius_multiplier must be within (1, 16]");
                }
                if (texture.isEmpty() && !procedural) {
                    throw new IllegalArgumentException(
                            "Enabled clouds require a texture, procedural generation, or both for fallback");
                }
                if (mapping == CloudMapping.PROCEDURAL && !procedural) {
                    throw new IllegalArgumentException("Procedural cloud mapping requires procedural generation");
                }
                if (mapping != CloudMapping.PROCEDURAL && texture.isEmpty()) {
                    throw new IllegalArgumentException("Textured cloud mapping requires a texture source");
                }
            }
            if (opacity < 0.0D || opacity > 1.0D) {
                throw new IllegalArgumentException("Cloud opacity must be within 0..1");
            }
            if (rotationPeriodTfcDays <= 0.0D || rotationPeriodTfcDays > 1_000_000.0D) {
                throw new IllegalArgumentException("Cloud rotation period must be within (0, 1000000]");
            }
            if (rotationOffset < 0.0D || rotationOffset >= 1.0D) {
                throw new IllegalArgumentException("Cloud rotation offset must be within [0, 1)");
            }
            if (shadowStrength < 0.0D || shadowStrength > 1.0D) {
                throw new IllegalArgumentException("Cloud shadow strength must be within 0..1");
            }
        }

        private static DataResult<CloudLayer> validated(CloudLayer clouds) {
            try {
                return DataResult.success(new CloudLayer(clouds.enabled, clouds.mapping, clouds.texture,
                        clouds.procedural, clouds.radiusMultiplier, clouds.opacity, clouds.tint,
                        clouds.rotationPeriodTfcDays, clouds.rotationAxis, clouds.rotationOffset,
                        clouds.shadowStrength));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }

    public enum CloudMapping {
        EQUIRECTANGULAR("equirectangular"),
        CUBE_ATLAS("cube_atlas"),
        PROCEDURAL("procedural");

        public static final Codec<CloudMapping> CODEC = Codec.STRING.comapFlatMap(
                id -> {
                    for (CloudMapping mapping : values()) {
                        if (mapping.id.equals(id)) {
                            return DataResult.success(mapping);
                        }
                    }
                    return DataResult.error(() -> "Unknown cloud mapping: " + id);
                },
                CloudMapping::id);

        private final String id;

        CloudMapping(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Color(double red, double green, double blue) {

        public static final Codec<Color> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
                values -> values.size() == 3
                        ? validated(new Color(values.get(0), values.get(1), values.get(2)))
                        : DataResult.error(() -> "Color must contain exactly three components"),
                color -> List.of(color.red, color.green, color.blue));

        public Color {
            if (!unit(red) || !unit(green) || !unit(blue)) {
                throw new IllegalArgumentException("Color components must be finite and within 0..1");
            }
        }

        private static DataResult<Color> validated(Color color) {
            try {
                return DataResult.success(new Color(color.red, color.green, color.blue));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }

        public Color multiply(double redMultiplier, double greenMultiplier, double blueMultiplier) {
            return new Color(Math.min(1.0D, red * redMultiplier),
                    Math.min(1.0D, green * greenMultiplier),
                    Math.min(1.0D, blue * blueMultiplier));
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    public record Axis(double x, double y, double z) {

        public static final Axis UP = new Axis(0.0D, 1.0D, 0.0D);

        public static final Codec<Axis> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
                values -> values.size() == 3
                        ? validated(new Axis(values.get(0), values.get(1), values.get(2)))
                        : DataResult.error(() -> "Axis must contain exactly three components"),
                axis -> List.of(axis.x, axis.y, axis.z));

        public Axis {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || x * x + y * y + z * z < 1.0E-12D) {
                throw new IllegalArgumentException("Rotation axis must be finite and non-zero");
            }
        }

        private static DataResult<Axis> validated(Axis axis) {
            try {
                return DataResult.success(new Axis(axis.x, axis.y, axis.z));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
