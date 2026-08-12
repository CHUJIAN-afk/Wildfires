package first.wildfires.space;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/** Stable keys for the only world added by the Wildfires space system. */
public final class SpaceDimensions {

    public static final ResourceKey<Level> ORBIT = ResourceKey.create(
            Registries.DIMENSION, Wildfires.rl("orbit"));
    public static final ResourceKey<DimensionType> ORBIT_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE, Wildfires.rl("orbit"));

    private SpaceDimensions() {
    }
}
