package first.wildfires.space.celestial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import first.wildfires.space.environment.CelestialEnvironment;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Versioned data-pack definition that augments a celestial ID with surface, environment and visuals. */
public record CelestialDefinition(
        int schemaVersion,
        CelestialKind kind,
        Optional<ResourceLocation> parent,
        Optional<ResourceLocation> surfaceDimension,
        boolean landable,
        CelestialEnvironment environment,
        CelestialVisualDefinition visual) {

    public static final int SCHEMA_VERSION = 1;
    public static final ResourceLocation ORBIT_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "wildfires", "orbit");

    private static final Codec<CelestialDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(CelestialDefinition::schemaVersion),
            CelestialKind.CODEC.fieldOf("kind").forGetter(CelestialDefinition::kind),
            ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(CelestialDefinition::parent),
            ResourceLocation.CODEC.optionalFieldOf("surface_dimension")
                    .forGetter(CelestialDefinition::surfaceDimension),
            Codec.BOOL.optionalFieldOf("landable", false).forGetter(CelestialDefinition::landable),
            CelestialEnvironment.CODEC.fieldOf("environment").forGetter(CelestialDefinition::environment),
            CelestialVisualDefinition.CODEC.fieldOf("visual").forGetter(CelestialDefinition::visual)
    ).apply(instance, CelestialDefinition::new));

    public static final Codec<CelestialDefinition> CODEC = RAW_CODEC.comapFlatMap(
            CelestialDefinition::validated,
            definition -> definition);

    public CelestialDefinition {
        Objects.requireNonNull(kind, "kind");
        parent = Objects.requireNonNull(parent, "parent");
        surfaceDimension = Objects.requireNonNull(surfaceDimension, "surfaceDimension");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(visual, "visual");
        validate(schemaVersion, kind, parent, surfaceDimension, landable);
    }

    /** A declared landing flag is only a request; the server must still resolve the existing dimension. */
    public boolean requestsLanding() {
        return landable && surfaceDimension.isPresent();
    }

    private static DataResult<CelestialDefinition> validated(CelestialDefinition definition) {
        try {
            validate(definition.schemaVersion, definition.kind, definition.parent,
                    definition.surfaceDimension, definition.landable);
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validate(int schemaVersion, CelestialKind kind,
                                 Optional<ResourceLocation> parent,
                                 Optional<ResourceLocation> surfaceDimension,
                                 boolean landable) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported celestial schema_version: " + schemaVersion);
        }
        if (surfaceDimension.filter(ORBIT_DIMENSION::equals).isPresent()) {
            throw new IllegalArgumentException("wildfires:orbit cannot be used as a celestial surface dimension");
        }
        if (kind == CelestialKind.STAR && parent.isPresent()) {
            throw new IllegalArgumentException("A star definition cannot have a parent celestial");
        }
        if (kind == CelestialKind.STAR && (landable || surfaceDimension.isPresent())) {
            throw new IllegalArgumentException("A star definition cannot expose a landable surface");
        }
    }
}
