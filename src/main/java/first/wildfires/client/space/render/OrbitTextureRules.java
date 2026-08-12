package first.wildfires.client.space.render;

import first.wildfires.space.celestial.CelestialVisualDefinition;

import java.util.Objects;

/** Pure source-selection policy used before any render-thread texture allocation. */
public final class OrbitTextureRules {

    private OrbitTextureRules() {
    }

    public static SourceChoice surface(CelestialVisualDefinition visual, boolean resourceExists) {
        Objects.requireNonNull(visual, "visual");
        return visual.surfaceAtlas().isPresent() && resourceExists
                && visual.surfaceAtlasLayout().getNamespace().equals("wildfires")
                && visual.surfaceAtlasLayout().getPath().equals("three_by_two_v1")
                ? SourceChoice.DIRECT_CUBE_ATLAS : SourceChoice.PROCEDURAL;
    }

    public enum SourceChoice {
        DIRECT_CUBE_ATLAS,
        PROCEDURAL
    }
}
