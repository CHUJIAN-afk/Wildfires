/*
 * Adapted from NTM: Space OrbitalStation, SolarSystem and SkyProviderCelestial.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: ported the visual contracts to Forge 1.20.1 records, replaced the
 * NTM solar system with CelestialState, and retained real Wildfires radii instead of NTM's cap.
 */
package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.station.StationJourneyPhase;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure NTM-style orbit presentation rules driven only by the unified Wildfires ephemeris. */
public final class OrbitVisualRules {

    private static final String WILDFIRES_NAMESPACE = "wildfires";

    public static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(
            WILDFIRES_NAMESPACE, "earth");
    public static final ResourceLocation SUN = ResourceLocation.fromNamespaceAndPath(
            WILDFIRES_NAMESPACE, "sun");
    public static final double NTM_TRANSITION_MIN_SIZE = 0.01D;
    public static final double NTM_TRANSITION_MAX_SIZE = 0.5D;
    public static final double NTM_RENDER_SCALE = 180.0D;
    public static final double NTM_SUN_RENDER_SCALE = 4.0D;
    public static final double BODY_DISTANCE = 82.0D;
    /** NTM WorldProviderOrbit feeds a 7200-second physical period into its KSP-time conversion. */
    public static final double NTM_ORBITAL_PERIOD_SECONDS = 7_200.0D;
    public static final double NTM_KSP_DAY_SECONDS = 21_600.0D;
    public static final double NTM_MINECRAFT_DAY_TICKS = 24_000.0D;
    /** Exact result of NTM calculatePositionSatellite: 7200 / 21600 * 24000 = 8000 ticks. */
    public static final double VISUAL_ORBIT_TICKS = NTM_ORBITAL_PERIOD_SECONDS
            / NTM_KSP_DAY_SECONDS * NTM_MINECRAFT_DAY_TICKS;
    public static final double NEAR_PHYSICAL_DIAMETER_DEGREES = 35.0D;
    public static final double MAX_BODY_RENDER_DISTANCE = 300.0D;
    /** Recorded upstream visual tilt; retained as provenance, not used as a physical deck normal. */
    public static final double NTM_SOURCE_ORBITAL_TILT_DEGREES = 80.0D;
    /** Wildfires requirement: player +Y is exactly perpendicular to the ecliptic, not 10 degrees off. */
    public static final double STATION_ECLIPTIC_TILT_DEGREES = 90.0D;
    /** NTM orbit star floor reaches full strength between these heliocentric distances. */
    public static final double NTM_STAR_DISTANCE_START_MILLION_KM = 9.0D;
    public static final double NTM_STAR_DISTANCE_END_MILLION_KM = 30.0D;

    private static final double EARTH_RADIUS_MILLION_KM = 0.006371D;
    private static final double DEPTH_REFERENCE_MILLION_KM = 0.02D;
    private static final double DEPTH_LOG_SCALE = 18.0D;
    private static final double TAU = Math.PI * 2.0D;

    private OrbitVisualRules() {
    }

    public static Frame frame(ObservationContext context, CelestialState state, double gameTime) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        if (!Double.isFinite(gameTime) || gameTime < 0.0D) {
            throw new IllegalArgumentException("Visual game time must be finite and non-negative");
        }

        Map<ResourceLocation, BodyEphemeris> ephemeris = bodyEphemeris(state);
        CelestialVector observer = observerPosition(context, ephemeris, gameTime);
        double viewRotationRadians = viewRotationRadians(context, ephemeris, gameTime);
        CelestialVector sunPosition = requireBody(ephemeris, SUN).position();
        List<BodyLayer> bodies = new ArrayList<>(ephemeris.size() - 1);
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : ephemeris.entrySet()) {
            if (entry.getKey().equals(SUN)) {
                continue;
            }
            BodyEphemeris body = entry.getValue();
            CelestialVector relative = body.position().subtract(observer);
            double distance = relative.length();
            if (!(distance > 1.0E-12D) || !Double.isFinite(distance)) {
                continue;
            }
            double apparentSize = apparentSize(body.radius(), distance);
            double cubeAlpha = cubeAlphaFor(apparentSize);
            double pointAlpha = pointAlphaFor(apparentSize);
            CelestialVector lightDirection = ntmFrameVector(
                    body.position().subtract(sunPosition).normalized());
            double renderDistance = renderDistance(distance);
            bodies.add(new BodyLayer(entry.getKey(), body.position(),
                    ntmFrameVector(relative.normalized()), distance,
                    body.radius(), apparentSize, renderDistance,
                    cubeHalfSize(body.radius(), distance, renderDistance), pointAlpha, cubeAlpha,
                    lightDirection));
        }
        bodies.sort(Comparator.comparingDouble(BodyLayer::distance).reversed());

        BodyEphemeris sun = requireBody(ephemeris, SUN);
        CelestialVector relativeSun = sun.position().subtract(observer);
        double sunDistance = Math.max(1.0E-12D, relativeSun.length());
        double sunApparentSize = ntmSunApparentSize(context, ephemeris, sun, gameTime);
        double sunRenderDistance = renderDistance(sunDistance);
        SunLayer sunLayer = new SunLayer(ntmFrameVector(relativeSun.normalized()), sunDistance,
                sun.radius(), sunApparentSize, sunRenderDistance,
                Math.min(sunRenderDistance * 0.72D,
                        ntmBillboardHalfSize(sunApparentSize, sunRenderDistance)));
        OrbitIllumination illumination = orbitIllumination(observer, sun.position(), sunLayer,
                bodies, state.calendarTicks());
        return new Frame(observer, viewRotationRadians, sunLayer, bodies, illumination);
    }

    /**
     * NTM orbit uses one eclipse amount for sunlight, solar power and the eclipse contribution to
     * star visibility. Its source phase approximation assumes flat planets; Wildfires replaces
     * only that coverage calculation with the exact projected hull of each rotated Genesis cube.
     */
    private static OrbitIllumination orbitIllumination(CelestialVector observer,
                                                        CelestialVector sunPosition,
                                                        SunLayer sun,
                                                        List<BodyLayer> bodies,
                                                        double calendarTicks) {
        double sunHalfTangent = sun.radius() / sun.distance();
        double occlusion = 0.0D;
        CelestialVector north = ntmFrameVector(new CelestialVector(0.0D, 0.0D, 1.0D));
        for (BodyLayer body : bodies) {
            if (body.distance() >= sun.distance()) {
                continue;
            }
            double coverage = projectedCubeCoverage(sun.direction(), sunHalfTangent,
                    body.direction().scale(body.distance()), body.radius(),
                    bodyRotation(body.body(), calendarTicks), north);
            // NTM getEclipseFactor retains the strongest foreground obscurer rather than adding
            // independent eclipse factors. Preserve that stable, non-overbrightening contract.
            occlusion = Math.max(occlusion, coverage);
        }
        double heliocentricDistance = observer.subtract(sunPosition).length();
        // NTM's thresholds are Kerbol-system million-kilometre values. Normalize the real
        // Wildfires system by Earth's current heliocentric distance before applying them, so
        // Earth corresponds to NTM Kerbin (13.59984) rather than looking permanently deep-space.
        double earthDistance = Math.max(1.0E-12D, sunPosition.length());
        double ntmEquivalentDistance = heliocentricDistance / earthDistance * 13.599840D;
        double distanceFloor = remap01(ntmEquivalentDistance,
                NTM_STAR_DISTANCE_START_MILLION_KM, NTM_STAR_DISTANCE_END_MILLION_KM);
        return new OrbitIllumination(1.0D - occlusion, occlusion,
                Math.max(occlusion, distanceFloor));
    }

    /** Exact perspective coverage of a square Sun by the convex silhouette of a rotated cube. */
    static double projectedCubeCoverage(CelestialVector sunDirection, double sunHalfTangent,
                                        CelestialVector cubeCenter, double cubeHalfSize,
                                        Quaternionf cubeRotation, CelestialVector celestialNorth) {
        if (!finite(sunDirection) || !finite(cubeCenter) || !(sunHalfTangent > 0.0D)
                || !(cubeHalfSize > 0.0D) || !Double.isFinite(sunHalfTangent)
                || !Double.isFinite(cubeHalfSize) || cubeRotation == null) {
            return 0.0D;
        }
        CelestialVector sun = sunDirection.normalized();
        first.wildfires.celestial.CelestialDiscGeometry.Basis basis =
                first.wildfires.celestial.CelestialDiscGeometry.stableBasis(sun, celestialNorth);
        List<ProjectedPoint> points = new ArrayList<>(8);
        for (int x : new int[]{-1, 1}) {
            for (int y : new int[]{-1, 1}) {
                for (int z : new int[]{-1, 1}) {
                    Vector3f local = new Vector3f(x, y, z).mul((float) cubeHalfSize);
                    cubeRotation.transform(local);
                    CelestialVector corner = cubeCenter.add(new CelestialVector(local.x, local.y, local.z));
                    double forward = corner.dot(sun);
                    if (!(forward > 1.0E-12D) || !Double.isFinite(forward)) {
                        return 0.0D;
                    }
                    points.add(new ProjectedPoint(corner.dot(basis.right()) / forward,
                            corner.dot(basis.up()) / forward));
                }
            }
        }
        List<ProjectedPoint> polygon = convexHull(points);
        polygon = clip(polygon, true, -sunHalfTangent, true);
        polygon = clip(polygon, true, sunHalfTangent, false);
        polygon = clip(polygon, false, -sunHalfTangent, true);
        polygon = clip(polygon, false, sunHalfTangent, false);
        return clamp(polygonArea(polygon) / (4.0D * sunHalfTangent * sunHalfTangent), 0.0D, 1.0D);
    }

    static Quaternionf bodyRotation(ResourceLocation body, double calendarTicks) {
        CelestialVector renderAxis = ntmFrameVector(spinAxisEcliptic(body)).normalized();
        Vector3f axis = new Vector3f((float) renderAxis.x(), (float) renderAxis.y(),
                (float) renderAxis.z());
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), axis)
                .rotateY((float) surfaceRotationRadians(body, calendarTicks));
    }

    /**
     * Cloud motion is a texture-space phase only. Rotating a cubic shell relative to its cubic
     * planet makes the two sets of faces and corners intersect, unlike concentric round spheres.
     */
    static double cloudTexturePhase(CelestialVisualDefinition.CloudLayer clouds,
                                    double calendarTicks) {
        Objects.requireNonNull(clouds, "clouds");
        double turns = calendarTicks / (clouds.rotationPeriodTfcDays() * 24_000.0D)
                + clouds.rotationOffset();
        return positiveModulo(turns);
    }

    private static List<ProjectedPoint> convexHull(List<ProjectedPoint> points) {
        List<ProjectedPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(ProjectedPoint::x).thenComparingDouble(ProjectedPoint::y));
        List<ProjectedPoint> hull = new ArrayList<>(16);
        for (ProjectedPoint point : sorted) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lower = hull.size();
        for (int index = sorted.size() - 2; index >= 0; index--) {
            ProjectedPoint point = sorted.get(index);
            while (hull.size() > lower && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        if (hull.size() > 1) {
            hull.remove(hull.size() - 1);
        }
        return hull;
    }

    private static List<ProjectedPoint> clip(List<ProjectedPoint> input, boolean xAxis,
                                             double boundary, boolean keepGreater) {
        if (input.isEmpty()) {
            return input;
        }
        List<ProjectedPoint> output = new ArrayList<>(input.size() + 4);
        ProjectedPoint previous = input.get(input.size() - 1);
        boolean previousInside = inside(previous, xAxis, boundary, keepGreater);
        for (ProjectedPoint current : input) {
            boolean currentInside = inside(current, xAxis, boundary, keepGreater);
            if (currentInside != previousInside) {
                double from = xAxis ? previous.x() : previous.y();
                double to = xAxis ? current.x() : current.y();
                double fraction = clamp((boundary - from) / (to - from), 0.0D, 1.0D);
                output.add(new ProjectedPoint(lerp(previous.x(), current.x(), fraction),
                        lerp(previous.y(), current.y(), fraction)));
            }
            if (currentInside) {
                output.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean inside(ProjectedPoint point, boolean xAxis,
                                  double boundary, boolean keepGreater) {
        double value = xAxis ? point.x() : point.y();
        return keepGreater ? value >= boundary : value <= boundary;
    }

    private static double cross(ProjectedPoint first, ProjectedPoint second, ProjectedPoint third) {
        return (second.x() - first.x()) * (third.y() - first.y())
                - (second.y() - first.y()) * (third.x() - first.x());
    }

    private static double polygonArea(List<ProjectedPoint> polygon) {
        double twiceArea = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            ProjectedPoint first = polygon.get(index);
            ProjectedPoint second = polygon.get((index + 1) % polygon.size());
            twiceArea += first.x() * second.y() - first.y() * second.x();
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    public static double phaseProgress(ObservationJourney journey, double gameTime) {
        Objects.requireNonNull(journey, "journey");
        if (!Double.isFinite(gameTime)) {
            throw new IllegalArgumentException("Visual game time must be finite");
        }
        if (journey.phaseDurationTicks() == 0L) {
            return 1.0D;
        }
        return clamp((gameTime - journey.phaseStartedGameTime()) / journey.phaseDurationTicks(),
                0.0D, 1.0D);
    }

    /**
     * NTM sizes the Sun from the body currently being left/orbited, interpolates source-to-target
     * sizes only during TRANSFER, and switches to the target before ARRIVING.  It deliberately
     * does not derive Sun size from the station's interpolated transfer position, which could make
     * a straight chord through the system produce a giant flare near the star.
     */
    private static double ntmSunApparentSize(ObservationContext context,
                                             Map<ResourceLocation, BodyEphemeris> ephemeris,
                                             BodyEphemeris sun, double gameTime) {
        if (context.journey().isEmpty()) {
            return sunSizeAtBody(context.currentBody(), ephemeris, sun);
        }
        ObservationJourney journey = context.journey().orElseThrow();
        double source = sunSizeAtBody(journey.fromBody(), ephemeris, sun);
        double target = sunSizeAtBody(journey.toBody(), ephemeris, sun);
        return switch (journey.phase()) {
            case DEPARTING -> source;
            case CRUISE -> lerp(source, target,
                    circularTransfer(phaseProgress(journey, gameTime)));
            case ARRIVING -> target;
            default -> sunSizeAtBody(context.currentBody(), ephemeris, sun);
        };
    }

    private static double sunSizeAtBody(ResourceLocation bodyId,
                                        Map<ResourceLocation, BodyEphemeris> ephemeris,
                                        BodyEphemeris sun) {
        double distance = requireBody(ephemeris, bodyId).position().subtract(sun.position()).length();
        return apparentSize(sun.radius(), Math.max(1.0E-12D, distance)) * NTM_SUN_RENDER_SCALE;
    }

    /** NTM render-space diameter: radians multiplied by the source's fixed scale of 180. */
    public static double apparentSize(double radius, double distance) {
        if (!Double.isFinite(radius) || !Double.isFinite(distance) || radius <= 0.0D || distance <= 0.0D) {
            throw new IllegalArgumentException("Apparent-size inputs must be finite and positive");
        }
        return 2.0D * Math.atan(radius / distance) * NTM_RENDER_SCALE;
    }

    public static double cubeAlphaFor(double apparentSize) {
        if (!Double.isFinite(apparentSize) || apparentSize < 0.0D) {
            throw new IllegalArgumentException("Apparent size must be finite and non-negative");
        }
        return remap01(apparentSize, NTM_TRANSITION_MIN_SIZE,
                NTM_TRANSITION_MAX_SIZE);
    }

    public static double pointAlphaFor(double apparentSize) {
        if (!Double.isFinite(apparentSize) || apparentSize < 0.0D) {
            throw new IllegalArgumentException("Apparent size must be finite and non-negative");
        }
        return clamp(apparentSize * 100.0D, 0.0D, 1.0D)
                * (1.0D - cubeAlphaFor(apparentSize));
    }

    /**
     * Compress astronomical distance monotonically into the sky projection.  Unlike the former
     * single-radius shell this preserves near/far ordering, so a distant body can never punch
     * through a nearer square planet when their screen projections overlap.
     */
    public static double renderDistance(double physicalDistance) {
        if (!(physicalDistance > 0.0D) || !Double.isFinite(physicalDistance)) {
            throw new IllegalArgumentException("Physical render distance must be finite and positive");
        }
        return Math.min(MAX_BODY_RENDER_DISTANCE, BODY_DISTANCE + DEPTH_LOG_SCALE
                * Math.log1p(physicalDistance / DEPTH_REFERENCE_MILLION_KM));
    }

    /** TFC-day sidereal periods; negative values intentionally retain retrograde rotation. */
    public static double rotationPeriodTfcDays(ResourceLocation body) {
        Objects.requireNonNull(body, "body");
        return switch (body.getPath()) {
            case "mercury" -> 58.646D;
            case "venus" -> -243.025D;
            case "earth" -> 0.99726968D;
            case "moon" -> 27.321661D;
            case "mars" -> 1.025957D;
            case "jupiter" -> 0.41354D;
            case "saturn" -> 0.44401D;
            case "uranus" -> -0.71833D;
            case "neptune" -> 0.67125D;
            case "pluto", "charon" -> 6.38723D;
            case "io" -> 1.769D;
            case "europa" -> 3.551D;
            case "ganymede" -> 7.155D;
            case "callisto" -> 16.689D;
            case "titan" -> 15.945D;
            // Triton's orbit normal already points along its retrograde angular momentum.  A
            // positive synchronous spin about that same pole preserves tidal locking; negating
            // both the pole sense and the period would reverse it a second time.
            case "triton" -> 5.877D;
            default -> 1.0D + OrbitProceduralTexture.unitSeed(body) * 8.0D;
        };
    }

    public static double surfaceRotationRadians(ResourceLocation body, double calendarTicks) {
        if (!Double.isFinite(calendarTicks)) {
            throw new IllegalArgumentException("Surface rotation time must be finite");
        }
        double periodTicks = rotationPeriodTfcDays(body) * 24_000.0D;
        return positiveModulo(calendarTicks / periodTicks) * TAU;
    }

    /** Physical spin axis in the common ecliptic frame; never a per-id random presentation tilt. */
    public static CelestialVector spinAxisEcliptic(ResourceLocation body) {
        Objects.requireNonNull(body, "body");
        CelestialBodies definition = CelestialBodies.byId(body);
        if (definition != null) {
            return definition.spinAxisEcliptic();
        }
        return switch (body.getPath()) {
            case "earth" -> axisFromObliquity(23.439281D, 90.0D);
            // Cassini-state approximation: the lunar pole is 1.543 degrees from ecliptic north,
            // opposite the orbit normal across the ecliptic at the J2000 node (125.08 + 90 deg).
            case "moon" -> axisFromObliquity(1.543D, 215.08D);
            default -> new CelestialVector(0.0D, 0.0D, 1.0D);
        };
    }

    private static CelestialVector axisFromObliquity(double obliquityDegrees,
                                                     double longitudeDegrees) {
        double tilt = Math.toRadians(obliquityDegrees);
        double longitude = Math.toRadians(longitudeDegrees);
        return new CelestialVector(Math.sin(tilt) * Math.cos(longitude),
                Math.sin(tilt) * Math.sin(longitude), Math.cos(tilt)).normalized();
    }

    /** Circular ease-in/out used only while the station is in the transfer (CRUISE) phase. */
    public static double circularTransfer(double progress) {
        double value = clamp(progress, 0.0D, 1.0D);
        if (value < 0.5D) {
            double doubled = value * 2.0D;
            return (1.0D - Math.sqrt(Math.max(0.0D, 1.0D - doubled * doubled * doubled))) * 0.5D;
        }
        double doubled = -2.0D * value + 2.0D;
        return (Math.sqrt(Math.max(0.0D, 1.0D - doubled * doubled * doubled)) + 1.0D) * 0.5D;
    }

    /**
     * Wildfires keeps the station deck in one inertial ecliptic attitude while it is orbiting.
     * The observer position already advances around the body every 8000 ticks; applying NTM's
     * local solar angle to the complete sky a second time cancels that relative motion and pins
     * the nearby planet to the station.  Only an active journey changes heading: departure turns
     * from the inertial attitude to the NTM route angle, cruise holds it, and arrival returns it.
     */
    private static double viewRotationRadians(ObservationContext context,
                                              Map<ResourceLocation, BodyEphemeris> ephemeris,
                                              double gameTime) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(ephemeris, "ephemeris");
        if (!Double.isFinite(gameTime)) {
            throw new IllegalArgumentException("Visual game time must be finite");
        }
        double localSkyAngle = 0.0D;
        if (context.journey().isEmpty()) {
            return localSkyAngle;
        }
        ObservationJourney journey = context.journey().orElseThrow();
        double travelSkyAngle = wrapRadians(Math.PI
                + travelAngleRadians(journey.fromBody(), journey.toBody(), ephemeris));
        double progress = phaseProgress(journey, gameTime);
        double targetSkyAngle = 0.0D;
        return switch (journey.phase()) {
            case DEPARTING -> circularLerpRadians(localSkyAngle, travelSkyAngle, progress);
            case CRUISE -> travelSkyAngle;
            case ARRIVING -> circularLerpRadians(travelSkyAngle, targetSkyAngle, progress);
            default -> localSkyAngle;
        };
    }

    /** NTM {@code clerp}: interpolate through the shortest signed turn instead of crossing 360°. */
    public static double circularLerpRadians(double from, double to, double progress) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || !Double.isFinite(progress)) {
            throw new IllegalArgumentException("Circular interpolation inputs must be finite");
        }
        double delta = wrapRadians(to - from);
        return wrapRadians(from + delta * clamp(progress, 0.0D, 1.0D));
    }

    /**
     * Port of NTM's {@code -calculateSingleAngle(from,to)+90deg}. Positions are converted from
     * Wildfires geocentric coordinates to heliocentric coordinates before calculating the route.
     */
    private static double travelAngleRadians(ResourceLocation fromId, ResourceLocation toId,
                                              Map<ResourceLocation, BodyEphemeris> ephemeris) {
        CelestialVector sun = requireBody(ephemeris, SUN).position();
        CelestialVector from = requireBody(ephemeris, fromId).position().subtract(sun);
        CelestialVector to = requireBody(ephemeris, toId).position().subtract(sun);
        double angleToOrigin = Math.atan2(-from.y(), -from.x());
        double angleToTarget = Math.atan2(to.y() - from.y(), to.x() - from.x());
        double apparentAngle = wrapRadians(angleToOrigin - angleToTarget);
        return wrapRadians(-apparentAngle + Math.PI * 0.5D);
    }

    /**
     * Wildfires ephemeris positions use an XY ecliptic with +Z as its normal. NTM renders its
     * zero-inclination orbit in YZ with +X as the normal before applying the recorded 80 degree
     * station-deck tilt. This right-handed permutation is therefore required before NTM's view
     * rotations; using the raw Wildfires vector tilts the ecliptic through the station floor.
     */
    static CelestialVector ntmFrameVector(CelestialVector source) {
        Objects.requireNonNull(source, "source");
        return new CelestialVector(source.z(), source.x(), -source.y());
    }

    /** Shared station-to-sky orientation; kept pure so the orbit matrix can be tested without Minecraft bootstrap. */
    static Quaternionf stationViewOrientation(double headingRadians) {
        if (!Double.isFinite(headingRadians)) {
            throw new IllegalArgumentException("Station heading must be finite");
        }
        return new Quaternionf().rotationX((float) Math.toRadians(STATION_ECLIPTIC_TILT_DEGREES))
                .rotateY((float) Math.toRadians(-90.0D))
                .rotateX((float) headingRadians);
    }

    private static CelestialVector observerPosition(ObservationContext context,
                                                     Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                     double gameTime) {
        if (context.journey().isEmpty()) {
            return orbitPosition(context.currentBody(), ephemeris, gameTime);
        }
        ObservationJourney journey = context.journey().orElseThrow();
        StationJourneyPhase phase = journey.phase();
        if (phase == StationJourneyPhase.DEPARTING) {
            return orbitPosition(journey.fromBody(), ephemeris, gameTime);
        }
        if (phase == StationJourneyPhase.CRUISE) {
            CelestialVector from = orbitPosition(journey.fromBody(), ephemeris, gameTime);
            CelestialVector to = orbitPosition(journey.toBody(), ephemeris, gameTime);
            return mix(from, to, circularTransfer(phaseProgress(journey, gameTime)));
        }
        if (phase == StationJourneyPhase.ARRIVING) {
            return orbitPosition(journey.toBody(), ephemeris, gameTime);
        }
        return orbitPosition(context.currentBody(), ephemeris, gameTime);
    }

    private static CelestialVector orbitPosition(ResourceLocation bodyId,
                                                 Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                 double gameTime) {
        BodyEphemeris body = requireBody(ephemeris, bodyId);
        double angle = localOrbitAngleRadians(gameTime);
        // The station's floor normal is the ecliptic north axis.  Its apparent local orbit is in
        // the same XY plane as NTM's satellite calculation, so the body visibly circles the station
        // instead of remaining pinned at a fixed point in the sky.
        CelestialVector unitOffset = new CelestialVector(Math.cos(angle), Math.sin(angle), 0.0D);
        double visualOrbitRadius = body.radius()
                / Math.tan(Math.toRadians(NEAR_PHYSICAL_DIAMETER_DEGREES * 0.5D));
        return body.position().add(unitOffset.scale(visualOrbitRadius));
    }

    private static double localOrbitAngleRadians(double gameTime) {
        return positiveModulo(gameTime / VISUAL_ORBIT_TICKS) * TAU;
    }

    private static Map<ResourceLocation, BodyEphemeris> bodyEphemeris(CelestialState state) {
        Map<ResourceLocation, BodyEphemeris> bodies = new LinkedHashMap<>();
        bodies.put(EARTH, new BodyEphemeris(CelestialVector.ZERO, EARTH_RADIUS_MILLION_KM));
        add(bodies, state.sun());
        add(bodies, state.moon());
        for (CelestialBodyState body : state.orbitingBodies()) {
            add(bodies, body);
        }
        return Map.copyOf(bodies);
    }

    private static void add(Map<ResourceLocation, BodyEphemeris> bodies, CelestialBodyState state) {
        Objects.requireNonNull(state, "state");
        double radius = Math.tan(state.angularRadiusRadians()) * state.distance();
        if (!finite(state.geocentricPosition()) || !(radius > 0.0D) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Celestial ephemeris contains an invalid body: " + state.id());
        }
        bodies.put(state.id(), new BodyEphemeris(state.geocentricPosition(), radius));
    }

    private static BodyEphemeris requireBody(Map<ResourceLocation, BodyEphemeris> ephemeris,
                                             ResourceLocation id) {
        BodyEphemeris body = ephemeris.get(id);
        if (body == null) {
            throw new IllegalArgumentException("Station visual body is absent from the unified ephemeris: " + id);
        }
        return body;
    }

    private static double ntmBillboardHalfSize(double apparentSize, double renderDistance) {
        return Math.max(0.015D, apparentSize * renderDistance / 100.0D);
    }

    /** Genesis cubes preserve the real angular radius; NTM's amplified units only drive LOD. */
    private static double cubeHalfSize(double radius, double distance, double renderDistance) {
        return Math.min(renderDistance * 0.72D,
                Math.max(0.015D, radius / distance * renderDistance));
    }

    private static CelestialVector mix(CelestialVector from, CelestialVector to, double progress) {
        return new CelestialVector(lerp(from.x(), to.x(), progress),
                lerp(from.y(), to.y(), progress), lerp(from.z(), to.z(), progress));
    }

    private static double remap01(double value, double minimum, double maximum) {
        return clamp((value - minimum) / (maximum - minimum), 0.0D, 1.0D);
    }

    private static double positiveModulo(double value) {
        return value - Math.floor(value);
    }

    private static double wrapRadians(double value) {
        double wrapped = value % TAU;
        if (wrapped >= Math.PI) {
            wrapped -= TAU;
        } else if (wrapped < -Math.PI) {
            wrapped += TAU;
        }
        return wrapped;
    }

    private static boolean finite(CelestialVector vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Frame(CelestialVector observerPosition, double viewRotationRadians,
                        SunLayer sun, List<BodyLayer> bodies, OrbitIllumination illumination) {
        public Frame {
            Objects.requireNonNull(observerPosition, "observerPosition");
            Objects.requireNonNull(sun, "sun");
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
            Objects.requireNonNull(illumination, "illumination");
            if (!Double.isFinite(viewRotationRadians)) {
                throw new IllegalArgumentException("Orbit view rotation must be finite");
            }
        }
    }

    public record SunLayer(CelestialVector direction, double distance, double radius,
                           double apparentSize, double renderDistance, double renderHalfSize) {
        public SunLayer {
            Objects.requireNonNull(direction, "direction");
            if (!(distance > 0.0D) || !(radius > 0.0D) || !Double.isFinite(distance)
                    || !Double.isFinite(radius)
                    || !(apparentSize > 0.0D) || !Double.isFinite(apparentSize)
                    || !(renderDistance > 0.0D) || !Double.isFinite(renderDistance)
                    || !(renderHalfSize > 0.0D) || !Double.isFinite(renderHalfSize)) {
                throw new IllegalArgumentException("Invalid NTM sun layer");
            }
        }
    }

    public record BodyLayer(ResourceLocation body, CelestialVector worldPosition,
                            CelestialVector direction, double distance, double radius,
                            double apparentSize, double renderDistance, double renderHalfSize,
                            double pointAlpha, double cubeAlpha,
                            CelestialVector incomingLightDirection) {
        public BodyLayer {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(worldPosition, "worldPosition");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(incomingLightDirection, "incomingLightDirection");
            if (!(distance > 0.0D) || !(radius > 0.0D) || !(apparentSize > 0.0D)
                    || !(renderDistance > 0.0D) || !(renderHalfSize > 0.0D) || !Double.isFinite(distance)
                    || !Double.isFinite(radius) || !Double.isFinite(apparentSize)
                    || !Double.isFinite(renderDistance) || !Double.isFinite(renderHalfSize)
                    || renderHalfSize >= renderDistance || !unit(pointAlpha) || !unit(cubeAlpha)) {
                throw new IllegalArgumentException("Invalid orbit body visual layer");
            }
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    private record BodyEphemeris(CelestialVector position, double radius) {
    }

    public record OrbitIllumination(double sunlight, double occlusion, double starVisibility) {
        public OrbitIllumination {
            if (!unit(sunlight) || !unit(occlusion) || !unit(starVisibility)
                    || Math.abs(sunlight + occlusion - 1.0D) > 1.0E-9D) {
                throw new IllegalArgumentException("Invalid NTM orbit illumination");
            }
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    private record ProjectedPoint(double x, double y) {
    }
}
