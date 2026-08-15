package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;

import java.util.Objects;

/**
 * Client-only special-relativity presentation math.  It deliberately changes only rays arriving
 * at the camera: station coordinates, authoritative body positions, calendar time and gameplay
 * light are never Lorentz-transformed.
 */
public final class RelativisticVisualRules {

    /** Near-light cruise avoids singular gamma while still making the effect unmistakable. */
    public static final double CRUISE_BETA = 0.985D;
    /** Acceleration hands cruise a broad but obvious forward-star contraction. */
    public static final double ACCELERATION_END_ABERRATION_BETA = 0.82D;
    /** Eight seconds of cruise keep tightening the star field to this finite dramatic endpoint. */
    public static final double CRUISE_END_ABERRATION_BETA = 0.93D;
    /** Safety headroom above the journey curve prevents an accidental point-like sky collapse. */
    public static final double VISUAL_ABERRATION_MAX_BETA = 0.94D;
    public static final double CRUISE_RATE_TRANSITION_TICKS = 80.0D;
    public static final double CRUISE_SLOW_TICKS = 80.0D;
    public static final double CRUISE_SLOW_RATE_RATIO = 1.0D / 3.0D;
    /** A contraction/release rate at or above this value produces the full bounded star trail. */
    public static final double STAR_TRAIL_FULL_RATE_PER_TICK = 0.015D;
    /** Only the acceleration trail is allowed to finish retracting in the first cruise second. */
    public static final double ACCELERATION_TRAIL_RELEASE_TICKS = 20.0D;

    private static final double CRUISE_ABERRATION_DELTA = CRUISE_END_ABERRATION_BETA
            - ACCELERATION_END_ABERRATION_BETA;
    private static final double CRUISE_START_RATE_PER_TICK = CRUISE_ABERRATION_DELTA
            / (CRUISE_RATE_TRANSITION_TICKS * ((1.0D + CRUISE_SLOW_RATE_RATIO) * 0.5D
            + CRUISE_SLOW_RATE_RATIO * CRUISE_SLOW_TICKS / CRUISE_RATE_TRANSITION_TICKS));
    private static final double CRUISE_END_RATE_PER_TICK = CRUISE_START_RATE_PER_TICK
            * CRUISE_SLOW_RATE_RATIO;

    private RelativisticVisualRules() {
    }

    public static State state(ObservationJourney journey, double gameTime) {
        Objects.requireNonNull(journey, "journey");
        double progress = OrbitVisualRules.phaseProgress(journey, gameTime);
        double beta = switch (journey.phase()) {
            case JUMP_ACCELERATING -> CRUISE_BETA * smoothstep(progress);
            case JUMP_CRUISING -> CRUISE_BETA;
            case JUMP_DECELERATING -> CRUISE_BETA * smoothstep(1.0D - progress);
            default -> 0.0D;
        };
        double visualBeta = switch (journey.phase()) {
            case JUMP_ACCELERATING -> accelerationAberration(progress, journey.phaseDurationTicks());
            case JUMP_CRUISING -> cruiseAberration(gameTime - journey.phaseStartedGameTime());
            case JUMP_DECELERATING -> decelerationAberration(progress, journey.phaseDurationTicks());
            default -> 0.0D;
        };
        double trailStrength = switch (journey.phase()) {
            case JUMP_ACCELERATING -> normalizedTrailRate(
                    accelerationAberrationRate(progress, journey.phaseDurationTicks()))
                    * starTrailFadeIn(progress);
            case JUMP_CRUISING -> normalizedTrailRate(cruiseAberrationRate(
                    gameTime - journey.phaseStartedGameTime()))
                    * accelerationTrailRelease(gameTime - journey.phaseStartedGameTime());
            case JUMP_DECELERATING -> normalizedTrailRate(
                    decelerationAberrationRate(progress, journey.phaseDurationTicks()))
                    * starTrailEnvelope(progress);
            default -> 0.0D;
        };
        return new State(beta, visualBeta, trailStrength);
    }

    /** C1 handoff from the three-second fast contraction into the cruise's initial rate. */
    private static double accelerationAberration(double progress, long durationTicks) {
        double t = clamp(progress, 0.0D, 1.0D);
        double t2 = t * t;
        double t3 = t2 * t;
        double endTangent = CRUISE_START_RATE_PER_TICK * durationTicks;
        return (-2.0D * t3 + 3.0D * t2) * ACCELERATION_END_ABERRATION_BETA
                + (t3 - t2) * endTangent;
    }

    private static double accelerationAberrationRate(double progress, long durationTicks) {
        double t = clamp(progress, 0.0D, 1.0D);
        double endTangent = CRUISE_START_RATE_PER_TICK * durationTicks;
        return ((6.0D * t - 6.0D * t * t) * ACCELERATION_END_ABERRATION_BETA
                + (3.0D * t * t - 2.0D * t) * endTangent) / durationTicks;
    }

    /** Four seconds of easing rate followed by four seconds of genuine slow contraction. */
    private static double cruiseAberration(double elapsedTicks) {
        double elapsed = clamp(elapsedTicks, 0.0D,
                CRUISE_RATE_TRANSITION_TICKS + CRUISE_SLOW_TICKS);
        if (elapsed <= CRUISE_RATE_TRANSITION_TICKS) {
            double t = elapsed / CRUISE_RATE_TRANSITION_TICKS;
            return ACCELERATION_END_ABERRATION_BETA + CRUISE_RATE_TRANSITION_TICKS
                    * (CRUISE_START_RATE_PER_TICK * t
                    + 0.5D * (CRUISE_END_RATE_PER_TICK - CRUISE_START_RATE_PER_TICK) * t * t);
        }
        double transitionDelta = CRUISE_RATE_TRANSITION_TICKS
                * (CRUISE_START_RATE_PER_TICK + CRUISE_END_RATE_PER_TICK) * 0.5D;
        return ACCELERATION_END_ABERRATION_BETA + transitionDelta
                + CRUISE_END_RATE_PER_TICK * (elapsed - CRUISE_RATE_TRANSITION_TICKS);
    }

    private static double cruiseAberrationRate(double elapsedTicks) {
        double elapsed = clamp(elapsedTicks, 0.0D,
                CRUISE_RATE_TRANSITION_TICKS + CRUISE_SLOW_TICKS);
        if (elapsed <= CRUISE_RATE_TRANSITION_TICKS) {
            return lerp(CRUISE_START_RATE_PER_TICK, CRUISE_END_RATE_PER_TICK,
                    elapsed / CRUISE_RATE_TRANSITION_TICKS);
        }
        return CRUISE_END_RATE_PER_TICK;
    }

    /** C1 three-second release: it accepts the slow cruise rate and finishes at rest. */
    private static double decelerationAberration(double progress, long durationTicks) {
        double t = clamp(progress, 0.0D, 1.0D);
        double t2 = t * t;
        double t3 = t2 * t;
        double startTangent = CRUISE_END_RATE_PER_TICK * durationTicks;
        return (2.0D * t3 - 3.0D * t2 + 1.0D) * CRUISE_END_ABERRATION_BETA
                + (t3 - 2.0D * t2 + t) * startTangent;
    }

    private static double decelerationAberrationRate(double progress, long durationTicks) {
        double t = clamp(progress, 0.0D, 1.0D);
        double startTangent = CRUISE_END_RATE_PER_TICK * durationTicks;
        return ((6.0D * t * t - 6.0D * t) * CRUISE_END_ABERRATION_BETA
                + (3.0D * t * t - 4.0D * t + 1.0D) * startTangent) / durationTicks;
    }

    /** The acceleration trail appears smoothly but follows its physical contraction rate at 3 s. */
    private static double starTrailFadeIn(double progress) {
        return smoothstep(clamp(progress, 0.0D, 1.0D) / 0.10D);
    }

    /** Finishes the acceleration trail retraction over only the first second of cruise. */
    private static double accelerationTrailRelease(double elapsedTicks) {
        return smoothstep(1.0D - elapsedTicks / ACCELERATION_TRAIL_RELEASE_TICKS);
    }

    /** The unchanged three-second deceleration trail creates and retracts within its own phase. */
    private static double starTrailEnvelope(double progress) {
        double t = clamp(progress, 0.0D, 1.0D);
        double fadeIn = smoothstep(t / 0.10D);
        double fadeOut = smoothstep((1.0D - t) / 0.20D);
        return fadeIn * fadeOut;
    }

    private static double normalizedTrailRate(double ratePerTick) {
        return clamp(ratePerTick / STAR_TRAIL_FULL_RATE_PER_TICK, -1.0D, 1.0D);
    }

    /** Relativistic aberration, with source directions pulled toward the velocity direction. */
    public static CelestialVector aberrate(CelestialVector sourceDirection,
                                           CelestialVector velocityDirection, State state) {
        Objects.requireNonNull(sourceDirection, "sourceDirection");
        Objects.requireNonNull(velocityDirection, "velocityDirection");
        Objects.requireNonNull(state, "state");
        if (!state.active()) return sourceDirection.normalized();
        CelestialVector source = sourceDirection.normalized();
        CelestialVector velocity = velocityDirection.normalized();
        double dot = source.dot(velocity);
        double beta = state.aberrationBeta();
        double gamma = state.aberrationGamma();
        // The plus convention is appropriate for sky source vectors (camera -> source), whereas
        // the usual textbook expression is commonly written for incoming photon propagation.
        CelestialVector numerator = source.add(velocity.scale((gamma - 1.0D) * dot
                + gamma * beta));
        return numerator.scale(1.0D / (gamma * (1.0D + beta * dot))).normalized();
    }

    /**
     * Attachment-equivalent observed/source frequency ratio.  The attachment first aberrates a
     * moving-camera ray and then evaluates {@code z = sqrt(1-beta²)/(1+beta*cos(theta))-1}; its
     * black-body pass uses {@code k = 1/(1+z)}.  Expressed directly in our un-aberrated stationary
     * source direction this is {@code k = gamma*(1+beta*cos(theta))}.  Using the attachment's final
     * angle formula with this pre-aberration direction would invert the transverse Doppler effect.
     */
    public static double dopplerFactor(CelestialVector sourceDirection,
                                       CelestialVector velocityDirection, State state) {
        if (!state.active()) return 1.0D;
        double cosine = sourceDirection.normalized().dot(velocityDirection.normalized());
        return state.gamma() * (1.0D + state.beta() * cosine);
    }

    /** Angular compression keeps the target tiny at cruise and restores it through deceleration. */
    public static double angularScale(State state) {
        return state.active() ? 1.0D / state.gamma() : 1.0D;
    }

    /** Jump stars become readable early in acceleration without changing gameplay illumination. */
    public static double starVisibility(double ordinaryVisibility, State state) {
        double ordinary = clamp(ordinaryVisibility * 0.6D, 0.0D, 0.6D);
        if (!state.active()) return ordinary;
        double progress = Math.sqrt(clamp(state.beta() / CRUISE_BETA, 0.0D, 1.0D));
        return clamp(ordinary + 0.88D * progress, 0.0D, 1.0D);
    }

    /** RGB/brightness approximation for spectral shifts when a source texture has no physical spectrum. */
    public static Tint tint(CelestialVector sourceDirection, CelestialVector velocityDirection, State state) {
        double shift = dopplerFactor(sourceDirection, velocityDirection, state);
        double spectral = clamp(Math.log(Math.max(1.0E-12D, shift)) / Math.log(4.0D), -1.0D, 1.0D);
        double blue = Math.max(0.0D, spectral);
        double red = Math.max(0.0D, -spectral);
        // Preserve the attachment's monotonic k response while exposing it in an SDR framebuffer.
        // Blue-shifted rays get bounded headroom. Red-shifted rays retain no artificial hemisphere-
        // wide floor: a rear shoulder stays readable, while the exact rear can naturally vanish.
        double exposure = clamp(Math.log(Math.max(1.0E-12D, shift)) / Math.log(2.0D) / 10.0D,
                -1.0D, 1.0D);
        double brightness = shift >= 1.0D
                ? 1.0D + 2.5D * exposure
                : Math.pow(clamp(shift, 0.0D, 1.0D), 1.65D);
        double cosine = sourceDirection.normalized().dot(velocityDirection.normalized());
        double forwardCone = smoothstep(clamp((cosine - 0.45D) / 0.55D, 0.0D, 1.0D));
        brightness *= 1.0D + 1.8D * forwardCone * forwardCone
                * clamp(state.aberrationBeta() / VISUAL_ABERRATION_MAX_BETA, 0.0D, 1.0D);
        double redChannel = lerp(1.0D, 0.30D, blue);
        double greenChannel = lerp(1.0D, 0.65D, blue);
        double blueChannel = lerp(1.0D, 1.80D, blue);
        redChannel = lerp(redChannel, 1.80D, red);
        greenChannel = lerp(greenChannel, 0.42D, red);
        blueChannel = lerp(blueChannel, 0.18D, red);
        return new Tint(redChannel * brightness, greenChannel * brightness,
                blueChannel * brightness, brightness);
    }

    private static double smoothstep(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    public record State(double beta, double visualAberrationBeta, double starTrailStrength) {
        public State(double beta) {
            this(beta, Math.min(beta, VISUAL_ABERRATION_MAX_BETA), 0.0D);
        }

        public State(double beta, double visualAberrationBeta) {
            this(beta, visualAberrationBeta, 0.0D);
        }

        public State {
            if (!Double.isFinite(beta) || beta < 0.0D || beta >= 1.0D) {
                throw new IllegalArgumentException("Relativistic beta must be finite in [0,1)");
            }
            if (!Double.isFinite(visualAberrationBeta) || visualAberrationBeta < 0.0D
                    || visualAberrationBeta >= 1.0D) {
                throw new IllegalArgumentException("Visual aberration beta must be finite in [0,1)");
            }
            if (!Double.isFinite(starTrailStrength) || starTrailStrength < -1.0D
                    || starTrailStrength > 1.0D) {
                throw new IllegalArgumentException("Star trail strength must be finite in [-1,1]");
            }
        }

        public boolean active() { return beta > 1.0E-9D; }

        public double gamma() { return 1.0D / Math.sqrt(1.0D - beta * beta); }

        public double aberrationBeta() { return Math.min(visualAberrationBeta, VISUAL_ABERRATION_MAX_BETA); }

        public double aberrationGamma() {
            double visualBeta = aberrationBeta();
            return 1.0D / Math.sqrt(1.0D - visualBeta * visualBeta);
        }
    }

    public record Tint(double red, double green, double blue, double brightness) {
        public Tint {
            if (!Double.isFinite(red) || !Double.isFinite(green) || !Double.isFinite(blue)
                    || !Double.isFinite(brightness)) throw new IllegalArgumentException("Invalid relativistic tint");
        }
    }
}
