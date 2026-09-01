package first.wildfires.compats.irons_spellbooks;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Deterministic timing and planar-geometry contracts for Galaxy Hymn's impact shards. */
public final class GalaxyHymnSpaceShardMath {

    public static final int SHARD_COUNT = 40;
    public static final int TRAVEL_TICKS = 44;
    public static final int MIN_TRAVEL_TICKS = 36;
    public static final int MAX_TRAVEL_TICKS = 54;
    public static final int POST_TRAVEL_RAMP_TICKS = 12;
    public static final int COLLAPSE_START_TICKS = 120;
    public static final int COLLAPSE_STAGGER_TICKS = 12;
    public static final int COLLAPSE_DURATION_TICKS = 20;
    public static final int SPARK_LIFETIME_TICKS = 24;
    public static final int TOTAL_DURATION_TICKS = COLLAPSE_START_TICKS + COLLAPSE_STAGGER_TICKS
            + COLLAPSE_DURATION_TICKS + SPARK_LIFETIME_TICKS;

    private GalaxyHymnSpaceShardMath() {
    }

    /** Cubic ease-out: radial speed falls continuously to exactly zero at the stop radius. */
    public static double travelDistance(double stopDistance, float age) {
        return travelDistance(stopDistance, age, TRAVEL_TICKS);
    }

    public static double travelDistance(double stopDistance, float age, int travelTicks) {
        double u = Mth.clamp(age / Math.max(1, travelTicks), 0.0F, 1.0F);
        double remaining = 1.0D - u;
        return stopDistance * (1.0D - remaining * remaining * remaining);
    }

    /** Derivative of {@link #travelDistance}; exposed so the zero-speed endpoint stays testable. */
    public static double travelSpeed(double stopDistance, float age) {
        return travelSpeed(stopDistance, age, TRAVEL_TICKS);
    }

    public static double travelSpeed(double stopDistance, float age, int travelTicks) {
        if (age < 0.0F || age >= travelTicks) {
            return 0.0D;
        }
        double remaining = 1.0D - age / travelTicks;
        return 3.0D * stopDistance * remaining * remaining / travelTicks;
    }

    /**
     * Integrated residual-motion time after the explosive ease-out. Its derivative starts at zero,
     * reaches one over {@link #POST_TRAVEL_RAMP_TICKS}, and then remains one. Multiplying this value
     * by a small linear or angular speed avoids snapping a shard to a completely static endpoint.
     */
    public static double postTravelMotionTicks(float age, int travelTicks) {
        double postTravelAge = Math.max(0.0D, age - Math.max(1, travelTicks));
        if (postTravelAge <= 0.0D) {
            return 0.0D;
        }
        if (postTravelAge >= POST_TRAVEL_RAMP_TICKS) {
            return postTravelAge - POST_TRAVEL_RAMP_TICKS * 0.5D;
        }
        double u = postTravelAge / POST_TRAVEL_RAMP_TICKS;
        return POST_TRAVEL_RAMP_TICKS * (u * u * u - 0.5D * u * u * u * u);
    }

    /** Instantaneous fraction of the configured residual linear/angular speed. */
    public static double postTravelSpeedFraction(float age, int travelTicks) {
        double u = Mth.clamp((age - Math.max(1, travelTicks)) / POST_TRAVEL_RAMP_TICKS,
                0.0F, 1.0F);
        return u * u * (3.0D - 2.0D * u);
    }

    /**
     * Two-stage linear-size growth tied to each shard's own explosive travel time. The shard reaches
     * its original size exactly when radial speed reaches zero, then takes the same time again to
     * reach the requested double size.
     */
    public static float growthScale(float age, int travelTicks) {
        float duration = Math.max(1, travelTicks);
        if (age <= duration) {
            return smoothStep(Mth.clamp(age / duration, 0.0F, 1.0F));
        }
        float secondStage = Mth.clamp((age - duration) / duration, 0.0F, 1.0F);
        return 1.0F + smoothStep(secondStage);
    }

    public static float collapseScale(float age, int collapseStart) {
        if (age <= collapseStart) {
            return 1.0F;
        }
        float u = Mth.clamp((age - collapseStart) / COLLAPSE_DURATION_TICKS, 0.0F, 1.0F);
        float remaining = 1.0F - u;
        return remaining * remaining * (3.0F - 2.0F * remaining);
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    /** Every generated vertex is an affine combination of two basis vectors and is therefore coplanar. */
    public static Vec3 planarPoint(Vec3 center, Vec3 basisU, Vec3 basisV, double x, double y) {
        return center.add(basisU.scale(x)).add(basisV.scale(y));
    }
}
