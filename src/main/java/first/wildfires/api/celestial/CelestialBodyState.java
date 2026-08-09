package first.wildfires.api.celestial;

import net.minecraft.resources.ResourceLocation;

/** Position and appearance of one body at a single observation point. */
public record CelestialBodyState(ResourceLocation id,
                                 ResourceLocation parentId,
                                 CelestialVector geocentricPosition,
                                 CelestialVector observerDirection,
                                 double distance,
                                 double angularRadiusRadians,
                                 double altitudeRadians,
                                 double brightness,
                                 double illuminatedFraction,
                                 double occultation) {
}
