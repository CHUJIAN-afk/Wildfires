/*
 * Adapted from NTM: Space OrbitalStation, SolarSystem and SkyProviderCelestial.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: ported the visual contracts to Forge 1.20.1 records, replaced the
 * NTM solar system with CelestialState, retained real Wildfires radii instead of NTM's cap, and
 * added direct-line jump phasing, distance-scaled visual travel and a three-dimensional
 * target-lock presentation.
 */
package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialPlanetSettings;
import first.wildfires.celestial.CelestialRuntimeSettings;
import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.route.StationTransferTopology;
import first.wildfires.space.station.StationJumpTimings;
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
import java.util.UUID;

/** Pure NTM-style orbit presentation rules driven only by the unified Wildfires ephemeris. */
public final class OrbitVisualRules {

    private static final String WILDFIRES_NAMESPACE = "wildfires";

    public static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(
            WILDFIRES_NAMESPACE, "earth");
    public static final ResourceLocation SUN = ResourceLocation.fromNamespaceAndPath(
            WILDFIRES_NAMESPACE, "sun");
    public static final ResourceLocation MOON = ResourceLocation.fromNamespaceAndPath(
            WILDFIRES_NAMESPACE, "moon");
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
    /** Fixed shader budget; Jupiter's four Galilean moons fit without a dynamic uniform array. */
    public static final int MAX_SATELLITE_SHADOWS = 4;
    private static final int MIN_LOCAL_TRANSFER_SAMPLES = 96;
    private static final int MAX_LOCAL_TRANSFER_SAMPLES = 512;
    private static final int MAX_INTER_SYSTEM_TRANSFER_SAMPLES = 2_048;
    private static final int LOCAL_TRANSFER_SAMPLES_PER_ORBIT = 24;
    private static final int LOCAL_TRANSFER_CACHE_SIZE = 24;
    private static final int INTER_SYSTEM_TRANSFER_CACHE_SIZE = 24;

    private static final double EARTH_RADIUS_MILLION_KM = 0.006371D;
    private static final double DEPTH_REFERENCE_MILLION_KM = 0.02D;
    private static final double DEPTH_LOG_SCALE = 18.0D;
    private static final double TAU = Math.PI * 2.0D;
    private static final double SQRT_TWO = Math.sqrt(2.0D);
    private static final double SQRT_THREE = Math.sqrt(3.0D);
    private static final CelestialVector NTM_ECLIPTIC_NORTH =
            ntmFrameVector(new CelestialVector(0.0D, 0.0D, 1.0D));
    private static final Map<ResourceLocation, Quaternionf> BODY_ROTATION_BASES =
            createBodyRotationBases();
    private static final Map<LocalTransferCacheKey, CachedLocalTransferPlan> LOCAL_TRANSFER_CACHE =
            new LinkedHashMap<>(LOCAL_TRANSFER_CACHE_SIZE + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<LocalTransferCacheKey, CachedLocalTransferPlan> eldest) {
                    return size() > LOCAL_TRANSFER_CACHE_SIZE;
                }
            };
    private static final Map<InterSystemTransferCacheKey, CachedInterSystemTransferPlan>
            INTER_SYSTEM_TRANSFER_CACHE = new LinkedHashMap<>(
            INTER_SYSTEM_TRANSFER_CACHE_SIZE + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<InterSystemTransferCacheKey,
                                CachedInterSystemTransferPlan> eldest) {
                    return size() > INTER_SYSTEM_TRANSFER_CACHE_SIZE;
                }
            };

    private OrbitVisualRules() {
    }

    public static Frame frame(ObservationContext context, CelestialState state, double gameTime) {
        return frame(context, state, gameTime, state.calendarTicks(), 0.0D, 8);
    }

    public static Frame frame(ObservationContext context, CelestialState state, double gameTime,
                              double visualCalendarTicks) {
        return frame(context, state, gameTime, visualCalendarTicks, 0.0D, 8);
    }

    public static Frame frame(ObservationContext context, CelestialState state, double gameTime,
                              double visualCalendarTicks, double calendarTicksPerGameTick) {
        return frame(context, state, gameTime, visualCalendarTicks, calendarTicksPerGameTick, 8);
    }

    public static Frame frame(ObservationContext context, CelestialState state, double gameTime,
                              double visualCalendarTicks, double calendarTicksPerGameTick,
                              int calendarDaysInMonth) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        if (!Double.isFinite(gameTime) || gameTime < 0.0D) {
            throw new IllegalArgumentException("Visual game time must be finite and non-negative");
        }
        if (!Double.isFinite(visualCalendarTicks) || visualCalendarTicks < 0.0D) {
            throw new IllegalArgumentException("Visual calendar time must be finite and non-negative");
        }
        if (!Double.isFinite(calendarTicksPerGameTick) || calendarTicksPerGameTick < 0.0D) {
            throw new IllegalArgumentException("Calendar rate must be finite and non-negative");
        }
        if (calendarDaysInMonth <= 0) {
            throw new IllegalArgumentException("Calendar days in month must be positive");
        }

        // A phase packet can reach the client a frame or two after the authoritative boundary.
        // The stale deceleration snapshot already contains that exact boundary, so predict only
        // the fixed ten-tick reveal instead of holding a point and jumping when ARRIVING arrives.
        context = extrapolateJumpReveal(context, gameTime);

        Map<ResourceLocation, BodyEphemeris> ephemeris = bodyEphemeris(state);
        ObservationJourney journey = context.journey().orElse(null);
        JumpLine frameJumpLine = journey != null && journey.mode() == StationTravelMode.JUMP
                && (journey.phase() == StationJourneyPhase.DEPARTING
                || journey.phase().isJumpPhase() || journey.phase() == StationJourneyPhase.ARRIVING)
                ? jumpLine(journey, ephemeris) : null;
        CelestialVector observer = observerPosition(context, ephemeris, gameTime,
                visualCalendarTicks, calendarTicksPerGameTick, calendarDaysInMonth, frameJumpLine);
        double viewRotationRadians = viewRotationRadians(context, ephemeris, gameTime,
                visualCalendarTicks, calendarTicksPerGameTick, calendarDaysInMonth);
        BodyEphemeris sunEphemeris = requireBody(ephemeris, SUN);
        CelestialVector sunPosition = sunEphemeris.position();
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
            CelestialVector relativeDirection = normalizedAtLength(relative, distance);
            double apparentSize = apparentSize(body.radius(), distance);
            double cubeAlpha = cubeAlphaFor(apparentSize);
            double pointAlpha = pointAlphaFor(apparentSize);
            CelestialVector bodyFromSun = body.position().subtract(sunPosition);
            double bodySunDistance = bodyFromSun.length();
            CelestialVector incomingLight = normalizedAtLength(bodyFromSun, bodySunDistance);
            CelestialVector lightDirection = ntmFrameVector(incomingLight);
            double sunHalfTangent = sunEphemeris.radius() / bodySunDistance;
            double shadowSunHalfTangent = sunEphemeris.radius()
                    / Math.max(1.0E-12D, bodySunDistance);
            List<SatelliteShadow> satelliteShadows = cubeAlpha > 0.001D
                    ? satelliteShadows(entry.getKey(), body, ephemeris, incomingLight,
                    shadowSunHalfTangent)
                    : List.of();
            double renderDistance = renderDistance(distance);
            BodyLayer layer = new BodyLayer(entry.getKey(), body.position(),
                    ntmFrameVector(relativeDirection), distance,
                    body.radius(), apparentSize, renderDistance,
                    cubeHalfSize(body.radius(), distance, renderDistance), pointAlpha, cubeAlpha,
                    lightDirection, sunHalfTangent, satelliteShadows);
            if (journey != null && journey.mode() == StationTravelMode.JUMP
                    && (journey.phase().isJumpPhase() || journey.phase() == StationJourneyPhase.ARRIVING)
                    && entry.getKey().equals(journey.toBody())) {
                layer = layer.jumpTargetApproach(jumpTargetReveal(journey, gameTime));
            }
            bodies.add(layer);
        }
        bodies.sort(Comparator.comparingDouble(BodyLayer::distance).reversed());

        BodyEphemeris sun = sunEphemeris;
        CelestialVector relativeSun = sun.position().subtract(observer);
        double rawSunDistance = relativeSun.length();
        double sunDistance = Math.max(1.0E-12D, rawSunDistance);
        double sunApparentSize = ntmSunApparentSize(context, ephemeris, sun, observer, gameTime);
        double sunRenderDistance = renderDistance(sunDistance);
        SunLayer sunLayer = new SunLayer(ntmFrameVector(
                normalizedAtLength(relativeSun, rawSunDistance)), sunDistance,
                sun.radius(), sunApparentSize, sunRenderDistance,
                Math.min(sunRenderDistance * 0.72D,
                        ntmBillboardHalfSize(sunApparentSize, sunRenderDistance)));
        OrbitIllumination illumination = orbitIllumination(observer, sun.position(), sunLayer,
                bodies, visualCalendarTicks);
        RelativisticVisualRules.State relativity = journey != null
                ? RelativisticVisualRules.state(journey, gameTime)
                : new RelativisticVisualRules.State(0.0D);
        CelestialVector visibleTargetDirection = sunLayer.direction();
        if (journey != null) {
            for (BodyLayer body : bodies) {
                if (journey.toBody().equals(body.body())) {
                    visibleTargetDirection = body.direction();
                    break;
                }
            }
        }
        CelestialVector velocityDirection = journey != null && usesFixedJumpDirection(journey, gameTime)
                ? jumpDirection(frameJumpLine, requireBody(ephemeris, journey.toBody()))
                : visibleTargetDirection;
        double targetLockStrength = journey != null
                ? jumpTargetLockStrength(journey, gameTime) : 0.0D;
        if (relativity.active()) {
            sunLayer = sunLayer.relativity(velocityDirection, relativity);
            List<BodyLayer> relativisticBodies = new ArrayList<>(bodies.size());
            for (BodyLayer body : bodies) {
                relativisticBodies.add(body.relativity(velocityDirection, relativity));
            }
            bodies = List.copyOf(relativisticBodies);
        }
        return new Frame(observer, viewRotationRadians, sunLayer, bodies, illumination, relativity,
                velocityDirection, targetLockStrength);
    }

    static ObservationContext extrapolateJumpReveal(ObservationContext context, double gameTime) {
        Objects.requireNonNull(context, "context");
        ObservationJourney journey = context.journey().orElse(null);
        if (journey == null || journey.mode() != StationTravelMode.JUMP
                || journey.phase() != StationJourneyPhase.JUMP_DECELERATING
                || journey.phaseStartedGameTime() > Long.MAX_VALUE - journey.phaseDurationTicks()) {
            return context;
        }
        long revealStartedGameTime = journey.phaseStartedGameTime() + journey.phaseDurationTicks();
        if (gameTime < revealStartedGameTime) {
            return context;
        }
        ObservationJourney reveal = new ObservationJourney(journey.fromBody(), journey.toBody(),
                journey.mode(), StationJourneyPhase.ARRIVING, revealStartedGameTime, 10L);
        return new ObservationContext(context.stationId(), context.stationRevision(), context.region(),
                journey.toBody(), context.status(), java.util.Optional.of(reveal),
                context.celestialRegistryGeneration());
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
        CelestialVector north = NTM_ECLIPTIC_NORTH;
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
        Vector3f local = new Vector3f();
        for (int xIndex = 0; xIndex < 2; xIndex++) {
            int x = xIndex == 0 ? -1 : 1;
            for (int yIndex = 0; yIndex < 2; yIndex++) {
                int y = yIndex == 0 ? -1 : 1;
                for (int zIndex = 0; zIndex < 2; zIndex++) {
                    int z = zIndex == 0 ? -1 : 1;
                    local.set(x, y, z).mul((float) cubeHalfSize);
                    cubeRotation.transform(local);
                    double cornerX = cubeCenter.x() + local.x;
                    double cornerY = cubeCenter.y() + local.y;
                    double cornerZ = cubeCenter.z() + local.z;
                    double forward = cornerX * sun.x() + cornerY * sun.y()
                            + cornerZ * sun.z();
                    if (!(forward > 1.0E-12D) || !Double.isFinite(forward)) {
                        return 0.0D;
                    }
                    points.add(new ProjectedPoint(
                            (cornerX * basis.right().x() + cornerY * basis.right().y()
                                    + cornerZ * basis.right().z()) / forward,
                            (cornerX * basis.up().x() + cornerY * basis.up().y()
                                    + cornerZ * basis.up().z()) / forward));
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
        Quaternionf cached = BODY_ROTATION_BASES.get(body);
        Quaternionf rotation = cached == null
                ? bodyRotationBase(body) : new Quaternionf(cached);
        return rotation.rotateY((float) surfaceRotationRadians(body, calendarTicks));
    }

    /** Sun-to-body incoming light in the common NTM render frame for any ephemeris body. */
    public static CelestialVector incomingLightDirection(ResourceLocation bodyId,
                                                          CelestialState state) {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(state, "state");
        Map<ResourceLocation, BodyEphemeris> ephemeris = bodyEphemeris(state);
        BodyEphemeris body = requireBody(ephemeris, bodyId);
        BodyEphemeris sun = requireBody(ephemeris, SUN);
        CelestialVector bodyFromSun = body.position().subtract(sun.position());
        return ntmFrameVector(normalizedAtLength(bodyFromSun, bodyFromSun.length()));
    }

    private static Quaternionf bodyRotationBase(ResourceLocation body) {
        CelestialVector renderAxis = ntmFrameVector(spinAxisEcliptic(body)).normalized();
        Vector3f axis = new Vector3f((float) renderAxis.x(), (float) renderAxis.y(),
                (float) renderAxis.z());
        return new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), axis);
    }

    private static Map<ResourceLocation, Quaternionf> createBodyRotationBases() {
        Map<ResourceLocation, Quaternionf> bases = new LinkedHashMap<>();
        bases.put(EARTH, bodyRotationBase(EARTH));
        bases.put(MOON, bodyRotationBase(MOON));
        for (CelestialBodies body : CelestialBodies.values()) {
            bases.put(body.id(), bodyRotationBase(body.id()));
        }
        return Map.copyOf(bases);
    }

    /**
     * Selects only child cubes whose conservative finite-star penumbra can touch the parent cube.
     * This is deliberately a broad CPU test: exact umbra/penumbra geometry remains per-fragment so
     * one three-dimensional shadow crosses planet faces, edges and corners without UV seams.
     */
    static List<SatelliteShadow> satelliteShadows(ResourceLocation parentId,
                                                   BodyEphemeris parent,
                                                   Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                   BodyEphemeris sun) {
        CelestialVector parentFromSun = parent.position().subtract(sun.position());
        double parentSunDistance = parentFromSun.length();
        CelestialVector incomingLight = parentSunDistance > 1.0E-12D
                ? parentFromSun.scale(1.0D / parentSunDistance) : CelestialVector.ZERO;
        double sunHalfTangent = sun.radius() / Math.max(1.0E-12D, parentSunDistance);
        return satelliteShadows(parentId, parent, ephemeris, incomingLight, sunHalfTangent);
    }

    /** Shared bound-body shadow frame for orbit and reusable-capsule surface transitions. */
    static SatelliteShadowFrame satelliteShadowFrame(ResourceLocation parentId,
                                                      CelestialState state) {
        Map<ResourceLocation, BodyEphemeris> ephemeris = bodyEphemeris(state);
        BodyEphemeris parent = requireBody(ephemeris, parentId);
        BodyEphemeris sun = requireBody(ephemeris, SUN);
        CelestialVector parentFromSun = parent.position().subtract(sun.position());
        double parentSunDistance = parentFromSun.length();
        double sunHalfTangent = sun.radius() / Math.max(1.0E-12D, parentSunDistance);
        return new SatelliteShadowFrame(parent.radius(), sunHalfTangent,
                satelliteShadows(parentId, parent, ephemeris, sun));
    }

    static List<SatelliteShadow> satelliteShadows(ResourceLocation parentId,
                                                   BodyEphemeris parent,
                                                   Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                   CelestialVector incomingLight,
                                                   double sunHalfTangent) {
        List<SatelliteShadowCandidate> candidates = new ArrayList<>();
        double parentCorner = SQRT_THREE * parent.radius();
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : ephemeris.entrySet()) {
            BodyEphemeris satellite = entry.getValue();
            if (!parentId.equals(satellite.parent())) {
                continue;
            }
            CelestialVector relative = satellite.position().subtract(parent.position());
            double signedLightDistance = relative.dot(incomingLight);
            double satelliteToParent = -signedLightDistance;
            if (!(satelliteToParent > satellite.radius()) || !Double.isFinite(satelliteToParent)) {
                continue;
            }
            CelestialVector lateralVector = relative.subtract(incomingLight.scale(signedLightDistance));
            double lateral = lateralVector.length();
            double satelliteCorner = SQRT_THREE * satellite.radius();
            double squareStarSpread = SQRT_TWO * sunHalfTangent * satelliteToParent;
            if (lateral > parentCorner + satelliteCorner + squareStarSpread) {
                continue;
            }
            double priority = satellite.radius() / satelliteToParent;
            candidates.add(new SatelliteShadowCandidate(new SatelliteShadow(entry.getKey(),
                    ntmFrameVector(relative), satellite.radius() / parent.radius()), priority));
        }
        candidates.sort(Comparator.comparingDouble(SatelliteShadowCandidate::priority).reversed()
                .thenComparing(candidate -> candidate.shadow().satellite().toString()));
        int selectedCount = Math.min(MAX_SATELLITE_SHADOWS, candidates.size());
        List<SatelliteShadow> selected = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selected.add(candidates.get(index).shadow());
        }
        return List.copyOf(selected);
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

    static double polygonArea(List<ProjectedPoint> polygon) {
        double twiceArea = 0.0D;
        int count = polygon.size();
        if (count == 0) {
            return 0.0D;
        }
        ProjectedPoint first = polygon.get(0);
        ProjectedPoint previous = first;
        for (int index = 1; index < count; index++) {
            ProjectedPoint current = polygon.get(index);
            twiceArea += previous.x() * current.y() - previous.y() * current.x();
            previous = current;
        }
        twiceArea += previous.x() * first.y() - previous.y() * first.x();
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
                                             BodyEphemeris sun, CelestialVector observer,
                                             double gameTime) {
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
            case JUMP_ACCELERATING, JUMP_CRUISING, JUMP_DECELERATING -> apparentSize(
                    sun.radius(), Math.max(1.0E-12D, sun.position().subtract(observer).length()))
                    * NTM_SUN_RENDER_SCALE;
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
                                              double gameTime, double calendarTicks,
                                              double calendarTicksPerGameTick,
                                              int calendarDaysInMonth) {
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
                + travelAngleRadians(context, journey, ephemeris, gameTime, calendarTicks,
                calendarTicksPerGameTick, calendarDaysInMonth));
        double progress = phaseProgress(journey, gameTime);
        double targetSkyAngle = 0.0D;
        return switch (journey.phase()) {
            // A jump departure already turns the complete sky through frameViewOrientation.  A
            // second NTM planar yaw makes both slerp endpoints move and can switch the shortest
            // quaternion arc midway through the otherwise continuous orbital phasing motion.
            case DEPARTING -> journey.mode() == StationTravelMode.JUMP
                    ? localSkyAngle
                    : circularLerpRadians(localSkyAngle, travelSkyAngle, progress);
            case CRUISE -> travelSkyAngle;
            case JUMP_ACCELERATING, JUMP_CRUISING, JUMP_DECELERATING -> travelSkyAngle;
            // Jump arrival already owns the complete 3-D sky orientation.  Letting its ordinary
            // yaw recover at the same time as target-lock slerp moves both quaternion endpoints;
            // near the halfway point their shortest-arc sign can change and rotate the sky by
            // almost 180 degrees in one frame.  A jump therefore unlocks between one fixed route
            // attitude and the final inertial attitude.  Ordinary travel retains NTM's planar yaw.
            case ARRIVING -> journey.mode() == StationTravelMode.JUMP
                    ? localSkyAngle
                    : circularLerpRadians(travelSkyAngle, targetSkyAngle, progress);
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
        ResourceLocation localPrimary = localSystemPrimary(fromId, toId, ephemeris);
        if (localPrimary != null) {
            CelestialVector route = requireBody(ephemeris, toId).position()
                    .subtract(requireBody(ephemeris, fromId).position());
            double planarLength = Math.hypot(route.x(), route.y());
            if (planarLength > 1.0E-12D) {
                return wrapRadians(-Math.atan2(route.y(), route.x()) + Math.PI * 0.5D);
            }
        }
        CelestialVector sun = requireBody(ephemeris, SUN).position();
        CelestialVector from = requireBody(ephemeris, fromId).position().subtract(sun);
        CelestialVector to = requireBody(ephemeris, toId).position().subtract(sun);
        double angleToOrigin = Math.atan2(-from.y(), -from.x());
        double angleToTarget = Math.atan2(to.y() - from.y(), to.x() - from.x());
        double apparentAngle = wrapRadians(angleToOrigin - angleToTarget);
        return wrapRadians(-apparentAngle + Math.PI * 0.5D);
    }

    /**
     * During cruise the route heading is taken from the locked transfer plan. Using live moon
     * positions here makes the complete sky yaw after a moving target even though the station has
     * already committed to an intercept arc.
     */
    private static double travelAngleRadians(ObservationContext context, ObservationJourney journey,
                                              Map<ResourceLocation, BodyEphemeris> ephemeris,
                                              double gameTime, double calendarTicks,
                                              double calendarTicksPerGameTick,
                                              int calendarDaysInMonth) {
        ResourceLocation primaryId = localSystemPrimary(journey.fromBody(), journey.toBody(), ephemeris);
        if (primaryId != null && journey.phase() == StationJourneyPhase.CRUISE) {
            LocalTransferPlan plan = localSystemTransferPlan(context, journey, ephemeris, primaryId,
                    gameTime, calendarTicks, calendarTicksPerGameTick, calendarDaysInMonth);
            CelestialVector route = plan.destination().safeOrbitPoint()
                    .subtract(plan.source().safeOrbitPoint());
            if (Math.hypot(route.x(), route.y()) > 1.0E-12D) {
                return wrapRadians(-Math.atan2(route.y(), route.x()) + Math.PI * 0.5D);
            }
        }
        if (primaryId == null && journey.phase() == StationJourneyPhase.CRUISE) {
            InterSystemTransferPlan plan = interSystemTransferPlan(context, journey, ephemeris,
                    gameTime, calendarTicks, calendarTicksPerGameTick, calendarDaysInMonth);
            return plan.headingRadians();
        }
        return travelAngleRadians(journey.fromBody(), journey.toBody(), ephemeris);
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

    /** Complete 3-D view contract: jump alignment overrides the ordinary planar route heading. */
    static Quaternionf frameViewOrientation(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        Quaternionf ordinary = stationViewOrientation(frame.viewRotationRadians());
        if (!(frame.targetLockStrength() > 0.0D)) {
            return ordinary;
        }
        Quaternionf locked = targetLockOrientation(frame.velocityDirection());
        return new Quaternionf(ordinary).slerp(locked, (float) frame.targetLockStrength());
    }

    static Quaternionf targetLockOrientation(CelestialVector direction) {
        CelestialVector target = Objects.requireNonNull(direction, "direction").normalized();
        Vector3f forward = new Vector3f((float) target.x(), (float) target.y(), (float) target.z());
        // ntmFrameVector maps ecliptic north to +X.  Supplying that fixed roll reference avoids
        // rotationTo's undefined 180-degree axis, which could make the deck flip midway through
        // an otherwise continuous target turn.  Only an effectively exact polar route uses the
        // deterministic fallback, keeping ordinary near-polar motion on one continuous branch.
        Vector3f stableUp = Math.abs(forward.x) < 0.999999F
                ? new Vector3f(1.0F, 0.0F, 0.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
        return new Quaternionf().lookAlong(forward, stableUp);
    }

    private static double jumpTargetLockStrength(ObservationJourney journey, double gameTime) {
        if (journey.mode() != StationTravelMode.JUMP) return 0.0D;
        return switch (journey.phase()) {
            case DEPARTING -> smoothStep01(phaseProgress(journey, gameTime));
            case JUMP_ACCELERATING, JUMP_CRUISING, JUMP_DECELERATING -> 1.0D;
            case ARRIVING -> {
                double elapsed = gameTime - journey.phaseStartedGameTime();
                if (elapsed <= 10.0D) yield 1.0D;
                double remainingProgress = (elapsed - 10.0D)
                        / Math.max(1.0D, journey.phaseDurationTicks() - 10.0D);
                yield 1.0D - smoothStep01(remainingProgress);
            }
            default -> 0.0D;
        };
    }

    /**
     * Keep the departure turn, every straight-flight phase and the complete arrival unlock on the
     * same route direction.  The target and observer may move onto the target-centred orbit after
     * the ten-tick reveal, but changing this slerp endpoint at the same time would make its shortest
     * quaternion arc switch branches and visibly flip the complete sky.
     */
    private static boolean usesFixedJumpDirection(ObservationJourney journey, double gameTime) {
        if (journey.mode() != StationTravelMode.JUMP) return false;
        if (journey.phase() == StationJourneyPhase.DEPARTING || journey.phase().isJumpPhase()) {
            return true;
        }
        return journey.phase() == StationJourneyPhase.ARRIVING;
    }

    /** Direct unobstructed heading held from the end of orbital phasing through target reveal. */
    private static CelestialVector jumpDirection(JumpLine line, BodyEphemeris target) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(target, "target");
        return ntmFrameVector(target.position().subtract(line.source()).normalized());
    }

    private static CelestialVector observerPosition(ObservationContext context,
                                                     Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                     double gameTime,
                                                     double calendarTicks,
                                                     double calendarTicksPerGameTick,
                                                     int calendarDaysInMonth,
                                                     JumpLine frameJumpLine) {
        if (context.journey().isEmpty()) {
            return orbitPosition(context.currentBody(), ephemeris, gameTime);
        }
        ObservationJourney journey = context.journey().orElseThrow();
        StationJourneyPhase phase = journey.phase();
        if (phase == StationJourneyPhase.DEPARTING && journey.mode() == StationTravelMode.JUMP) {
            CelestialVector currentOrbit = orbitPosition(journey.fromBody(), ephemeris,
                    journey.phaseStartedGameTime());
            CelestialVector clearLock = Objects.requireNonNull(frameJumpLine, "frameJumpLine").source();
            BodyEphemeris source = requireBody(ephemeris, journey.fromBody());
            double orbitRadius = currentOrbit.subtract(source.position()).length();
            return sphericalArc(source.position(), currentOrbit, clearLock,
                    smoothStep01(phaseProgress(journey, gameTime)), orbitRadius);
        }
        if (phase == StationJourneyPhase.DEPARTING) {
            return orbitPosition(journey.fromBody(), ephemeris, gameTime);
        }
        if (phase == StationJourneyPhase.CRUISE) {
            // A context packet can arrive one or more ticks after the authoritative phase change.
            // Once cruise has ended, follow the target's live stable orbit instead of freezing at
            // the old intercept point and then jumping when ARRIVING finally reaches the client.
            if (gameTime >= journey.phaseStartedGameTime() + journey.phaseDurationTicks()) {
                return orbitPosition(journey.toBody(), ephemeris, gameTime);
            }
            double phaseProgress = phaseProgress(journey, gameTime);
            ResourceLocation localPrimary = localSystemPrimary(
                    journey.fromBody(), journey.toBody(), ephemeris);
            if (localPrimary != null) {
                LocalTransferPlan plan = localSystemTransferPlan(context, journey, ephemeris,
                        localPrimary, gameTime, calendarTicks, calendarTicksPerGameTick,
                        calendarDaysInMonth);
                return localSystemTransferPosition(plan, phaseProgress);
            }
            InterSystemTransferPlan plan = interSystemTransferPlan(context, journey, ephemeris,
                    gameTime, calendarTicks, calendarTicksPerGameTick, calendarDaysInMonth);
            return interSystemTransferPosition(plan, phaseProgress,
                    requireBody(ephemeris, journey.toBody()).position());
        }
        if (phase.isJumpPhase()) {
            JumpLine line = Objects.requireNonNull(frameJumpLine, "frameJumpLine");
            return mix(line.source(), line.revealStart(),
                    jumpTravelProgress(journey, gameTime, line.joinSpeedFraction()));
        }
        if (phase == StationJourneyPhase.ARRIVING) {
            if (journey.mode() == StationTravelMode.JUMP) {
                JumpLine line = Objects.requireNonNull(frameJumpLine, "frameJumpLine");
                double elapsed = clamp(gameTime - journey.phaseStartedGameTime(), 0.0D,
                        journey.phaseDurationTicks());
                if (elapsed <= 10.0D) {
                    return mix(line.revealStart(), line.arrival(),
                            jumpArrivalApproachProgress(line, elapsed));
                }
                CelestialVector ordinaryOrbit = orbitPosition(journey.toBody(), ephemeris, gameTime);
                BodyEphemeris target = requireBody(ephemeris, journey.toBody());
                double orbitRadius = line.arrival().subtract(target.position()).length();
                double orbitProgress = (elapsed - 10.0D)
                        / Math.max(1.0D, journey.phaseDurationTicks() - 10.0D);
                return sphericalArc(target.position(), line.arrival(), ordinaryOrbit,
                        smoothStep01(orbitProgress), orbitRadius);
            }
            return orbitPosition(journey.toBody(), ephemeris, gameTime);
        }
        return orbitPosition(context.currentBody(), ephemeris, gameTime);
    }

    /**
     * Fixed 3/8/3-second displacement contract.  Smoothstep is the velocity curve, not the
     * position curve: integrating it makes acceleration start at zero, reach the exact cruise
     * speed at 3 seconds and stay constant for 8 seconds.  Deceleration then reaches the exact
     * non-zero speed used by the following ten-tick target reveal, rather than stopping at the
     * phase boundary.  Its denominator includes that route-geometry-dependent terminal speed.
     */
    static double jumpTravelProgress(ObservationJourney journey, double gameTime,
                                     double joinSpeedFraction) {
        Objects.requireNonNull(journey, "journey");
        double phase = phaseProgress(journey, gameTime);
        double acceleration = StationJumpTimings.ACCELERATION_TICKS;
        double cruise = StationJumpTimings.CRUISE_TICKS;
        double deceleration = StationJumpTimings.DECELERATION_TICKS;
        double terminalSpeed = clamp(joinSpeedFraction, 0.0D, 1.0D);
        double fullSpeedTicks = cruise + 0.5D * acceleration
                + 0.5D * deceleration * (1.0D + terminalSpeed);
        return switch (journey.phase()) {
            case JUMP_ACCELERATING -> acceleration * integratedSmoothStep(phase) / fullSpeedTicks;
            case JUMP_CRUISING -> (0.5D * acceleration + cruise * phase) / fullSpeedTicks;
            case JUMP_DECELERATING -> (0.5D * acceleration + cruise
                    + deceleration * (terminalSpeed * phase
                    + (1.0D - terminalSpeed) * integratedReverseSmoothStep(phase))) / fullSpeedTicks;
            default -> journey.phase() == StationJourneyPhase.ARRIVING ? 1.0D : 0.0D;
        };
    }

    /** Normalized current speed relative to this route's distance-derived cruise speed. */
    static double jumpSpeedFraction(ObservationJourney journey, double gameTime,
                                    double joinSpeedFraction) {
        double progress = phaseProgress(journey, gameTime);
        double terminalSpeed = clamp(joinSpeedFraction, 0.0D, 1.0D);
        return switch (journey.phase()) {
            case JUMP_ACCELERATING -> smoothStep01(progress);
            case JUMP_CRUISING -> 1.0D;
            case JUMP_DECELERATING -> terminalSpeed
                    + (1.0D - terminalSpeed) * smoothStep01(1.0D - progress);
            default -> 0.0D;
        };
    }

    /** Route-geometry-dependent non-zero speed shared by deceleration and target reveal. */
    static double jumpJoinSpeedFraction(ObservationJourney journey,
                                        Map<ResourceLocation, BodyEphemeris> ephemeris) {
        return jumpLine(journey, ephemeris).joinSpeedFraction();
    }

    /** Absolute visual cruise distance per server tick for this fixed-time route. */
    static double jumpCruiseDistancePerTick(ObservationJourney journey,
                                            Map<ResourceLocation, BodyEphemeris> ephemeris) {
        JumpLine line = jumpLine(journey, ephemeris);
        double equivalentFullSpeedTicks = jumpEquivalentFullSpeedTicks(line.joinSpeedFraction());
        return line.revealStart().subtract(line.source()).length() / equivalentFullSpeedTicks;
    }

    /** The first ten arrival ticks blend the target point to normal size while approach continues. */
    static double jumpTargetReveal(ObservationJourney journey, double gameTime) {
        if (journey.phase().isJumpPhase()) return 0.0D;
        if (journey.phase() != StationJourneyPhase.ARRIVING) return 1.0D;
        return smoothStep01((gameTime - journey.phaseStartedGameTime()) / 10.0D);
    }

    private static JumpLine jumpLine(ObservationJourney journey,
                                     Map<ResourceLocation, BodyEphemeris> ephemeris) {
        double accelerationStart = jumpAccelerationStart(journey);
        CelestialVector source = jumpLockPosition(journey.fromBody(), journey.toBody(), ephemeris,
                accelerationStart);
        BodyEphemeris target = requireBody(ephemeris, journey.toBody());
        CelestialVector inbound = target.position().subtract(source).normalized();
        double orbitRadius = target.radius()
                / Math.tan(Math.toRadians(NEAR_PHYSICAL_DIAMETER_DEGREES * 0.5D));
        CelestialVector arrival = target.position().subtract(inbound.scale(orbitRadius));
        CelestialVector revealStart = target.position().subtract(inbound.scale(orbitRadius * 8.0D));
        double jumpDistance = revealStart.subtract(source).length();
        double revealDistance = arrival.subtract(revealStart).length();
        double denominator = jumpDistance - 6.0D * revealDistance;
        double joinSpeedFraction = denominator > 0.0D
                ? Math.min(1.0D, 44.0D * revealDistance / denominator)
                : 1.0D;
        return new JumpLine(source, revealStart, arrival, joinSpeedFraction);
    }

    /**
     * Cubic Hermite approach with the deceleration endpoint's exact world speed and zero terminal
     * speed.  For ordinary interplanetary geometry the solved tangent is two, making this the
     * integral of a reverse smoothstep velocity; the bounded fallback remains monotonic.
     */
    private static double jumpArrivalApproachProgress(JumpLine line, double elapsedTicks) {
        double duration = 10.0D;
        double progress = clamp(elapsedTicks / duration, 0.0D, 1.0D);
        double revealDistance = line.arrival().subtract(line.revealStart()).length();
        if (!(revealDistance > 0.0D)) return 1.0D;
        double cruiseSpeed = line.revealStart().subtract(line.source()).length()
                / jumpEquivalentFullSpeedTicks(line.joinSpeedFraction());
        double initialTangent = clamp(line.joinSpeedFraction() * cruiseSpeed
                * duration / revealDistance, 0.0D, 3.0D);
        double progressSquared = progress * progress;
        double progressCubed = progressSquared * progress;
        return initialTangent * (progressCubed - 2.0D * progressSquared + progress)
                + (-2.0D * progressCubed + 3.0D * progressSquared);
    }

    private static double jumpEquivalentFullSpeedTicks(double joinSpeedFraction) {
        return StationJumpTimings.CRUISE_TICKS
                + 0.5D * StationJumpTimings.ACCELERATION_TICKS
                + 0.5D * StationJumpTimings.DECELERATION_TICKS
                * (1.0D + clamp(joinSpeedFraction, 0.0D, 1.0D));
    }

    private static double jumpAccelerationStart(ObservationJourney journey) {
        return switch (journey.phase()) {
            case DEPARTING -> journey.phaseStartedGameTime() + journey.phaseDurationTicks();
            case JUMP_ACCELERATING -> journey.phaseStartedGameTime();
            case JUMP_CRUISING -> journey.phaseStartedGameTime() - StationJumpTimings.ACCELERATION_TICKS;
            case JUMP_DECELERATING -> journey.phaseStartedGameTime()
                    - StationJumpTimings.ACCELERATION_TICKS - StationJumpTimings.CRUISE_TICKS;
            case ARRIVING -> journey.phaseStartedGameTime() - StationJumpTimings.ACCELERATION_TICKS
                    - StationJumpTimings.CRUISE_TICKS - StationJumpTimings.DECELERATION_TICKS;
            default -> journey.phaseStartedGameTime();
        };
    }

    private static double integratedSmoothStep(double value) {
        double x = clamp(value, 0.0D, 1.0D);
        return x * x * x - 0.5D * x * x * x * x;
    }

    private static double integratedReverseSmoothStep(double value) {
        double x = clamp(value, 0.0D, 1.0D);
        return x - x * x * x + 0.5D * x * x * x * x;
    }

    /**
     * Find the earliest station position with an unobstructed straight segment to the target.
     * Search the source orbit first (ordinary phasing); only if the entire orbit is blocked do we
     * advance along the existing safe interplanetary arc until a direct target lock is possible.
     */
    static CelestialVector jumpLockPosition(ResourceLocation fromId, ResourceLocation toId,
                                            Map<ResourceLocation, BodyEphemeris> ephemeris,
                                            double lockGameTime) {
        BodyEphemeris source = requireBody(ephemeris, fromId);
        BodyEphemeris target = requireBody(ephemeris, toId);
        JumpObstacles obstacles = jumpObstacles(toId, ephemeris);
        CelestialVector initial = orbitPosition(fromId, ephemeris, lockGameTime);
        double orbitRadius = initial.subtract(source.position()).length();
        double startAngle = Math.atan2(initial.y() - source.position().y(),
                initial.x() - source.position().x());
        for (int step = 0; step < 720; step++) {
            double angle = startAngle + TAU * step / 720.0D;
            double candidateX = source.position().x() + Math.cos(angle) * orbitRadius;
            double candidateY = source.position().y() + Math.sin(angle) * orbitRadius;
            // Preserve the former vector scale/add operation even for IEEE-754 signed zero.
            double candidateZ = source.position().z() + 0.0D * orbitRadius;
            if (hasClearTargetLine(candidateX, candidateY, candidateZ,
                    target.position(), obstacles)) {
                return new CelestialVector(candidateX, candidateY, candidateZ);
            }
        }
        CelestialVector targetOrbit = orbitPosition(toId, ephemeris, lockGameTime);
        for (int step = 1; step <= 720; step++) {
            CelestialVector candidate = safeTransferPosition(initial, targetOrbit, source, target,
                    step / 720.0D);
            if (hasClearTargetLine(candidate, target.position(), obstacles)) {
                return candidate;
            }
        }
        return initial;
    }

    static boolean hasClearTargetLine(CelestialVector observer, ResourceLocation targetId,
                                      CelestialVector targetPosition,
                                      Map<ResourceLocation, BodyEphemeris> ephemeris) {
        return hasClearTargetLine(observer, targetPosition, jumpObstacles(targetId, ephemeris));
    }

    private static JumpObstacles jumpObstacles(ResourceLocation targetId,
                                               Map<ResourceLocation, BodyEphemeris> ephemeris) {
        int size = ephemeris.containsKey(targetId) ? ephemeris.size() - 1 : ephemeris.size();
        double[] x = new double[size];
        double[] y = new double[size];
        double[] z = new double[size];
        double[] radius = new double[size];
        int index = 0;
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : ephemeris.entrySet()) {
            if (entry.getKey().equals(targetId)) continue;
            BodyEphemeris obstacle = entry.getValue();
            x[index] = obstacle.position().x();
            y[index] = obstacle.position().y();
            z[index] = obstacle.position().z();
            radius[index] = obstacle.radius() * Math.sqrt(3.0D) * 1.05D;
            index++;
        }
        return new JumpObstacles(x, y, z, radius);
    }

    private static boolean hasClearTargetLine(CelestialVector observer,
                                              CelestialVector targetPosition,
                                              JumpObstacles obstacles) {
        return hasClearTargetLine(observer.x(), observer.y(), observer.z(), targetPosition, obstacles);
    }

    private static boolean hasClearTargetLine(double observerX, double observerY, double observerZ,
                                              CelestialVector targetPosition,
                                              JumpObstacles obstacles) {
        double segmentX = targetPosition.x() - observerX;
        double segmentY = targetPosition.y() - observerY;
        double segmentZ = targetPosition.z() - observerZ;
        double segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
                + segmentZ * segmentZ;
        if (!(segmentLengthSquared > 1.0E-18D)) return false;
        for (int index = 0; index < obstacles.size(); index++) {
            double obstacleX = obstacles.x()[index];
            double obstacleY = obstacles.y()[index];
            double obstacleZ = obstacles.z()[index];
            double relativeX = obstacleX - observerX;
            double relativeY = obstacleY - observerY;
            double relativeZ = obstacleZ - observerZ;
            double along = clamp((relativeX * segmentX + relativeY * segmentY
                    + relativeZ * segmentZ) / segmentLengthSquared, 0.0D, 1.0D);
            double closestX = observerX + segmentX * along;
            double closestY = observerY + segmentY * along;
            double closestZ = observerZ + segmentZ * along;
            double separationX = closestX - obstacleX;
            double separationY = closestY - obstacleY;
            double separationZ = closestZ - obstacleZ;
            double separation = Math.sqrt(separationX * separationX + separationY * separationY
                    + separationZ * separationZ);
            if (separation <= obstacles.radius()[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * An NTM-styled transfer must leave the origin orbit tangentially instead of interpolating a
     * straight world-space chord.  A chord reaches the near side of the origin cube whenever the
     * target is behind it, exposing the station as if it had flown into the planet.  This cubic
     * path has outward-and-lateral control points at both stable orbit endpoints, so departure
     * clears the local body before the long transfer and arrival approaches the destination from
     * outside its orbit.
     */
    static CelestialVector safeTransferPosition(CelestialVector from, CelestialVector to,
                                                BodyEphemeris origin, BodyEphemeris target,
                                                double progress) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        double value = clamp(progress, 0.0D, 1.0D);
        CelestialVector route = to.subtract(from);
        if (!(route.length() > 1.0E-12D)) {
            return from;
        }
        CelestialVector originOutward = from.subtract(origin.position()).normalized();
        CelestialVector targetOutward = to.subtract(target.position()).normalized();
        CelestialVector lateral = eclipticLateral(route, originOutward);
        double originOrbit = from.subtract(origin.position()).length();
        double targetOrbit = to.subtract(target.position()).length();
        // The lateral lane must scale with the transfer chord as well as the two local orbits.
        // Otherwise a distant target directly behind the departure body still cuts back through
        // its cube before the small local-orbit clearance has had time to bend the Bezier curve.
        double clearance = Math.max(Math.max(origin.radius(), target.radius()) * 8.0D,
                Math.max((originOrbit + targetOrbit) * 2.0D, route.length() * 0.05D));
        CelestialVector firstControl = from.add(originOutward.add(lateral).normalized().scale(clearance));
        CelestialVector secondControl = to.add(targetOutward.add(lateral).normalized().scale(clearance));
        return cubicBezier(from, firstControl, secondControl, to, value);
    }

    /**
     * Locks an ordinary cross-system cruise in the Sun-following frame. NTM's transfer-cost code
     * climbs the body tree for moon-to-other-system journeys; the visual route mirrors that
     * topology by approaching a satellite from outside its parent system instead of aiming a
     * per-frame chord through the parent planet.
     */
    private static InterSystemTransferPlan interSystemTransferPlan(
            ObservationContext context, ObservationJourney journey,
            Map<ResourceLocation, BodyEphemeris> current, double gameTime,
            double calendarTicks, double calendarTicksPerGameTick, int calendarDaysInMonth) {
        double elapsed = clamp(gameTime - journey.phaseStartedGameTime(), 0.0D,
                journey.phaseDurationTicks());
        double departureCalendarTicks = calendarTicks - elapsed * calendarTicksPerGameTick;
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        InterSystemTransferCacheKey cacheKey = new InterSystemTransferCacheKey(context.stationId(),
                context.celestialRegistryGeneration(), journey.fromBody(), journey.toBody(),
                journey.phaseStartedGameTime(), journey.phaseDurationTicks(),
                Math.round(departureCalendarTicks),
                Double.doubleToLongBits(calendarTicksPerGameTick), calendarDaysInMonth, settings);
        BodyEphemeris currentSun = requireBody(current, SUN);
        synchronized (INTER_SYSTEM_TRANSFER_CACHE) {
            CachedInterSystemTransferPlan cached = INTER_SYSTEM_TRANSFER_CACHE.get(cacheKey);
            if (cached != null) {
                return translateInterSystemTransferPlan(cached.plan(),
                        currentSun.position().subtract(cached.sunAnchor()));
            }
        }

        List<Map<ResourceLocation, BodyEphemeris>> obstacleTimeline = interSystemObstacleTimeline(
                current, calendarTicks, calendarTicksPerGameTick, calendarDaysInMonth, elapsed,
                journey.phaseDurationTicks());
        Map<ResourceLocation, BodyEphemeris> departure = obstacleTimeline.get(0);
        Map<ResourceLocation, BodyEphemeris> arrival = obstacleTimeline.get(
                obstacleTimeline.size() - 1);
        CelestialVector from = orbitPosition(journey.fromBody(), departure,
                journey.phaseStartedGameTime());
        CelestialVector to = orbitPosition(journey.toBody(), arrival,
                journey.phaseStartedGameTime() + journey.phaseDurationTicks());
        InterSystemTransferPlan plan = chooseInterSystemTransferPlan(journey.fromBody(),
                journey.toBody(), from, to, departure, arrival, obstacleTimeline);
        synchronized (INTER_SYSTEM_TRANSFER_CACHE) {
            INTER_SYSTEM_TRANSFER_CACHE.put(cacheKey,
                    new CachedInterSystemTransferPlan(currentSun.position(), plan));
        }
        return plan;
    }

    private static CelestialVector interSystemTransferPosition(InterSystemTransferPlan plan,
                                                                double progress) {
        return interSystemTransferPosition(plan, progress,
                timelinePosition(plan.targetBodyTimeline(), progress));
    }

    private static CelestialVector interSystemTransferPosition(InterSystemTransferPlan plan,
                                                                double progress,
                                                                CelestialVector liveTargetBody) {
        double value = circularTransfer(clamp(progress, 0.0D, 1.0D));
        if (value <= plan.ingressProgress()) {
            double local = value / plan.ingressProgress();
            return cubicBezier(plan.from(), plan.departureControl(), plan.ingressControl(),
                    plan.ingress(), local);
        }
        double local = (value - plan.ingressProgress()) / (1.0D - plan.ingressProgress());
        CelestialVector relative;
        if (plan.captureOrbitJoinProgress() > 1.0E-12D
                && local <= plan.captureOrbitJoinProgress()) {
            double capture = local / plan.captureOrbitJoinProgress();
            double outerRadius = Math.min(plan.ingressRelative().length(),
                    plan.targetOuterRelative().length());
            relative = sphericalArc(CelestialVector.ZERO, plan.ingressRelative(),
                    plan.targetOuterRelative(), capture, outerRadius);
        } else if (plan.captureRadialJoinProgress() - plan.captureOrbitJoinProgress()
                > 1.0E-12D && local <= plan.captureRadialJoinProgress()) {
            double capture = (local - plan.captureOrbitJoinProgress())
                    / (plan.captureRadialJoinProgress() - plan.captureOrbitJoinProgress());
            relative = mix(plan.targetOuterRelative(), plan.targetGateRelative(), capture);
        } else {
            double phasing = (local - plan.captureRadialJoinProgress())
                    / Math.max(1.0E-12D, 1.0D - plan.captureRadialJoinProgress());
            relative = sphericalArc(CelestialVector.ZERO, plan.targetGateRelative(),
                    plan.toRelative(), phasing, plan.targetStableRadius());
        }
        // At the exact gate boundary the Sun-frame transfer must remain continuous. Blend from
        // the planned target anchor to the live authoritative target while still on the far outer
        // capture sphere; radial descent and final phasing then follow the live body exactly.
        CelestialVector plannedTargetBody = timelinePosition(plan.targetBodyTimeline(), progress);
        double liveAnchorBlend = plan.captureOrbitJoinProgress() > 1.0E-12D
                ? smoothStep01(local / plan.captureOrbitJoinProgress()) : 1.0D;
        return mix(plannedTargetBody, liveTargetBody, liveAnchorBlend).add(relative);
    }

    private static InterSystemTransferPlan chooseInterSystemTransferPlan(
            ResourceLocation fromId, ResourceLocation toId, CelestialVector from,
            CelestialVector to, Map<ResourceLocation, BodyEphemeris> departure,
            Map<ResourceLocation, BodyEphemeris> arrival,
            List<Map<ResourceLocation, BodyEphemeris>> obstacles) {
        BodyEphemeris origin = requireBody(departure, fromId);
        BodyEphemeris target = requireBody(arrival, toId);
        ResourceLocation sourcePrimaryId = endpointSystemPrimary(fromId, origin);
        ResourceLocation targetPrimaryId = endpointSystemPrimary(toId, target);
        BodyEphemeris sourcePrimary = requireBody(departure, sourcePrimaryId);
        BodyEphemeris targetPrimary = requireBody(arrival, targetPrimaryId);
        double lockedHeading = travelAngleRadians(fromId, toId, departure);
        CelestialVector route = to.subtract(from);
        CelestialVector sourceOut = from.subtract(sourcePrimary.position()).normalized();
        CelestialVector targetOut = to.subtract(targetPrimary.position()).normalized();
        CelestialVector sourceBodyOut = from.subtract(origin.position()).normalized();
        CelestialVector targetBodyOut = to.subtract(target.position()).normalized();
        if (sourceOut.length() < 1.0E-12D) {
            sourceOut = from.subtract(origin.position()).normalized();
        }
        if (targetOut.length() < 1.0E-12D) {
            targetOut = to.subtract(target.position()).normalized();
        }
        if (sourceBodyOut.length() < 1.0E-12D) sourceBodyOut = sourceOut;
        if (targetBodyOut.length() < 1.0E-12D) targetBodyOut = targetOut;
        CelestialVector planar = eclipticLateral(route, sourceOut);
        CelestialVector routeNormal = cross(sourceOut, route.normalized()).normalized();
        CelestialVector orbitalSide = routeNormal.length() > 1.0E-12D
                ? cross(routeNormal, route.normalized()).normalized() : planar;
        List<CelestialVector> sides = List.of(planar, planar.scale(-1.0D), orbitalSide,
                orbitalSide.scale(-1.0D), new CelestialVector(0.0D, 0.0D, 1.0D),
                new CelestialVector(0.0D, 0.0D, -1.0D));
        double sourceSystemRadius = systemGuardRadius(departure, sourcePrimaryId);
        double targetSystemRadius = systemGuardRadius(arrival, targetPrimaryId);
        List<CelestialVector> targetPrimaryTimeline = obstacles.stream()
                .map(frame -> requireBody(frame, targetPrimaryId).position()).toList();
        List<CelestialVector> targetBodyTimeline = obstacles.stream()
                .map(frame -> requireBody(frame, toId).position()).toList();
        double ingressProgress = 0.82D;
        double ingressPhaseProgress = inverseCircularTransfer(ingressProgress);
        CelestialVector ingressPrimary = timelinePosition(targetPrimaryTimeline,
                ingressPhaseProgress);
        double baseHandle = Math.max(route.length() * 0.05D,
                Math.max(sourceSystemRadius, targetSystemRadius));
        baseHandle = Math.max(baseHandle, Math.max(origin.radius(), target.radius()) * 8.0D);
        double bestClearance = Double.NEGATIVE_INFINITY;
        for (double multiplier : new double[]{1.0D, 1.5D, 2.25D, 3.5D, 5.5D, 8.0D,
                12.0D, 18.0D, 27.0D, 40.0D, 64.0D}) {
            double handle = baseHandle * multiplier;
            double captureScale = Math.sqrt(multiplier);
            for (CelestialVector side : sides) {
                CelestialVector lateral = side.normalized();
                // A moon's system-radial direction can point straight through the moon from the
                // station's current side. Leave along the endpoint body's own outward radius
                // before the heliocentric leg starts.
                CelestialVector firstDirection = sourceBodyOut.add(lateral).normalized();
                CelestialVector departureControl = from.add(firstDirection.scale(
                        Math.max(handle, sourceSystemRadius)));
                double ingressRadius = targetSystemRadius
                        * (1.35D + (captureScale - 1.0D) * 0.25D);
                CelestialVector ingressRelative = targetOut.scale(ingressRadius)
                        .add(lateral.scale(targetSystemRadius * 0.35D * captureScale));
                CelestialVector ingress = ingressPrimary.add(ingressRelative);
                CelestialVector ingressControl = ingress.add(targetOut.add(
                        lateral.scale(0.35D)).normalized().scale(
                        Math.max(handle * 0.35D, targetSystemRadius * 0.5D)));
                CelestialVector ingressTarget = timelinePosition(targetBodyTimeline,
                        ingressPhaseProgress);
                CelestialVector ingressTargetRelative = ingress.subtract(ingressTarget);
                // Once through the system gate, capture follows the target body itself. A fast
                // moon can no longer orbit repeatedly through a curve frozen to its parent.
                CelestialVector toRelative = to.subtract(target.position());
                double targetStableRadius = to.subtract(target.position()).length();
                double targetSafeRadius = Math.max(targetStableRadius, target.radius() * 6.0D);
                CelestialVector targetGateDirection = targetBodyOut
                        .add(lateral.scale(captureScale)).normalized();
                CelestialVector targetGateRelative = targetGateDirection.scale(targetSafeRadius);
                double targetOuterRadius = Math.max(ingressTargetRelative.length(),
                        Math.max(targetSystemRadius * 1.35D * captureScale, targetSafeRadius));
                CelestialVector targetOuterRelative = targetGateDirection.scale(targetOuterRadius);
                double departureLength = cubicBezierLength(from, departureControl,
                        ingressControl, ingress);
                // Capture never draws a chord through the target: orbit at an outer safe radius,
                // descend on one ray, then phase around the stable-orbit sphere.
                double captureOrbitLength = sphericalArcLength(CelestialVector.ZERO,
                        ingressTargetRelative, targetOuterRelative,
                        Math.min(ingressTargetRelative.length(), targetOuterRadius));
                double captureRadialLength = targetOuterRadius - targetSafeRadius;
                double phasingLength = sphericalArcLength(CelestialVector.ZERO,
                        targetGateRelative, toRelative, targetStableRadius);
                double captureTotal = Math.max(1.0E-12D,
                        captureOrbitLength + captureRadialLength + phasingLength);
                double captureOrbitJoinProgress = captureOrbitLength / captureTotal;
                double captureRadialJoinProgress = (captureOrbitLength + captureRadialLength)
                        / captureTotal;
                InterSystemTransferPlan candidate = new InterSystemTransferPlan(from, to,
                        departureControl, ingressControl, ingress, ingressTargetRelative,
                        targetOuterRelative, targetGateRelative, toRelative, targetBodyTimeline,
                        ingressProgress, captureOrbitJoinProgress, captureRadialJoinProgress,
                        targetStableRadius, departureLength + captureTotal,
                        lockedHeading);
                double clearance = interSystemTransferClearance(candidate, obstacles);
                if (clearance > bestClearance) {
                    bestClearance = clearance;
                }
                if (clearance > 0.0D) return candidate;
            }
        }
        throw new IllegalStateException("No collision-free inter-system transfer from " + fromId
                + " to " + toId + "; best swept clearance=" + bestClearance);
    }

    private static ResourceLocation endpointSystemPrimary(ResourceLocation id,
                                                          BodyEphemeris body) {
        return body.parent() != null && !body.parent().equals(SUN) ? body.parent() : id;
    }

    private static double systemGuardRadius(Map<ResourceLocation, BodyEphemeris> ephemeris,
                                            ResourceLocation primaryId) {
        BodyEphemeris primary = requireBody(ephemeris, primaryId);
        double radius = primary.radius() * SQRT_THREE * 1.05D;
        for (BodyEphemeris body : ephemeris.values()) {
            if (!primaryId.equals(body.parent())) continue;
            radius = Math.max(radius, body.position().subtract(primary.position()).length()
                    + body.radius() * SQRT_THREE * 1.05D);
        }
        return radius;
    }

    /**
     * NTM distinguishes parent/moon and sibling-moon transfers from interplanetary transfers.
     * Wildfires keeps that topology but evaluates the cruise around the common planet instead of
     * lifting the station onto an unrelated ecliptic-normal corridor.
     */
    static CelestialVector localSystemTransferPosition(CelestialVector from, CelestialVector to,
                                                       BodyEphemeris origin, BodyEphemeris target,
                                                       BodyEphemeris primary,
                                                       Map<ResourceLocation, BodyEphemeris> ephemeris,
                                                       double progress) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(ephemeris, "ephemeris");
        ResourceLocation primaryId = findId(primary, ephemeris);
        LocalTransferGate source = localTransferGate(from, origin, ephemeris);
        LocalTransferGate destination = localTransferGate(to, target, ephemeris);
        return localSystemTransferPosition(localTransferPlan(from, to, primary,
                origin, target,
                source, destination, List.of(ephemeris)), progress);
    }

    /**
     * A local transfer is planned in the common-primary frame. The source gate is reconstructed at
     * cruise start and stays fixed relative to that primary; the target gate is predicted at
     * cruise end, so a moving moon is intercepted instead of chased by a per-frame endpoint lerp.
     */
    private static LocalTransferPlan localSystemTransferPlan(ObservationContext context,
                                                             ObservationJourney journey,
                                                             Map<ResourceLocation, BodyEphemeris> current,
                                                             ResourceLocation primaryId,
                                                             double gameTime,
                                                             double calendarTicks,
                                                             double calendarTicksPerGameTick,
                                                             int calendarDaysInMonth) {
        double elapsed = clamp(gameTime - journey.phaseStartedGameTime(), 0.0D,
                journey.phaseDurationTicks());
        double departureCalendarTicks = calendarTicks - elapsed * calendarTicksPerGameTick;
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        LocalTransferCacheKey cacheKey = new LocalTransferCacheKey(context.stationId(),
                context.celestialRegistryGeneration(), journey.fromBody(), journey.toBody(),
                journey.phaseStartedGameTime(), journey.phaseDurationTicks(),
                Math.round(departureCalendarTicks),
                Double.doubleToLongBits(calendarTicksPerGameTick), calendarDaysInMonth,
                settings);
        BodyEphemeris currentPrimary = requireBody(current, primaryId);
        synchronized (LOCAL_TRANSFER_CACHE) {
            CachedLocalTransferPlan cached = LOCAL_TRANSFER_CACHE.get(cacheKey);
            if (cached != null) {
                return translateLocalTransferPlan(cached.plan(),
                        currentPrimary.position().subtract(cached.primaryAnchor()));
            }
        }
        double remaining = Math.max(0.0D, journey.phaseDurationTicks() - elapsed);
        Map<ResourceLocation, BodyEphemeris> departure = shiftedSystemEphemeris(current, primaryId,
                calendarTicks, -elapsed * calendarTicksPerGameTick, calendarDaysInMonth);
        Map<ResourceLocation, BodyEphemeris> arrival = shiftedSystemEphemeris(current, primaryId,
                calendarTicks, remaining * calendarTicksPerGameTick, calendarDaysInMonth);
        BodyEphemeris departurePrimary = requireBody(departure, primaryId);
        BodyEphemeris arrivalPrimary = requireBody(arrival, primaryId);
        Map<ResourceLocation, BodyEphemeris> departureFrame = translateSystemFrame(
                departure, departurePrimary.position(), currentPrimary.position(), primaryId);
        Map<ResourceLocation, BodyEphemeris> arrivalFrame = translateSystemFrame(
                arrival, arrivalPrimary.position(), currentPrimary.position(), primaryId);
        BodyEphemeris origin = requireBody(departureFrame, journey.fromBody());
        BodyEphemeris target = requireBody(arrivalFrame, journey.toBody());
        CelestialVector from = orbitPosition(journey.fromBody(), departureFrame,
                journey.phaseStartedGameTime());
        CelestialVector to = orbitPosition(journey.toBody(), arrivalFrame,
                journey.phaseStartedGameTime() + journey.phaseDurationTicks());
        LocalTransferGate source = localTransferGate(from, origin, departureFrame);
        LocalTransferGate destination = localTransferGate(to, target, arrivalFrame);
        List<Map<ResourceLocation, BodyEphemeris>> obstacleTimeline = localObstacleTimeline(
                current, primaryId, calendarTicks, calendarTicksPerGameTick,
                calendarDaysInMonth, elapsed, journey.phaseDurationTicks());
        LocalTransferPlan plan = localTransferPlan(from, to, currentPrimary,
                origin, target,
                source, destination, obstacleTimeline);
        synchronized (LOCAL_TRANSFER_CACHE) {
            LOCAL_TRANSFER_CACHE.put(cacheKey,
                    new CachedLocalTransferPlan(currentPrimary.position(), plan));
        }
        return plan;
    }

    /** Keeps a local journey in the moving common-primary frame instead of drawing a heliocentric chord. */
    private static Map<ResourceLocation, BodyEphemeris> translateSystemFrame(
            Map<ResourceLocation, BodyEphemeris> ephemeris, CelestialVector oldPrimary,
            CelestialVector newPrimary, ResourceLocation primaryId) {
        CelestialVector translation = newPrimary.subtract(oldPrimary);
        Map<ResourceLocation, BodyEphemeris> translated = new LinkedHashMap<>(ephemeris);
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : ephemeris.entrySet()) {
            BodyEphemeris body = entry.getValue();
            if (entry.getKey().equals(primaryId) || primaryId.equals(body.parent())) {
                translated.put(entry.getKey(), new BodyEphemeris(body.position().add(translation),
                        body.radius(), body.parent()));
            }
        }
        return Map.copyOf(translated);
    }

    private static Map<ResourceLocation, BodyEphemeris> shiftedSystemEphemeris(
            Map<ResourceLocation, BodyEphemeris> current, ResourceLocation primaryId,
            double currentCalendarTicks, double calendarTickOffset, int calendarDaysInMonth) {
        BodyEphemeris primary = requireBody(current, primaryId);
        Map<ResourceLocation, BodyEphemeris> shifted = new LinkedHashMap<>();
        shifted.put(primaryId, primary);
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        double targetTicks = currentCalendarTicks + calendarTickOffset;
        CelestialPlanetSettings planets = settings.planetSettings();
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : current.entrySet()) {
            BodyEphemeris present = entry.getValue();
            if (!primaryId.equals(present.parent())) continue;
            CelestialBodies definition = CelestialBodies.byId(entry.getKey());
            if (definition == null || definition.parent() == null || entry.getKey().equals(MOON)) continue;
            CelestialVector relative = present.position().subtract(primary.position());
            double astronomicalDayOffset = CelestialMath.calendarYears(
                    calendarTickOffset, calendarDaysInMonth) * planets.earthOrbitalDays();
            double angle = TAU * astronomicalDayOffset
                    / planets.parameters(definition).orbitalDays();
            if (definition.retrograde()) angle = -angle;
            CelestialVector rotated = rotateAroundAxis(relative,
                    definition.orbitalPlaneNormalEcliptic(planets), angle);
            shifted.put(entry.getKey(), new BodyEphemeris(primary.position().add(rotated),
                    present.radius(), present.parent()));
        }

        double synodicDays = settings.resolvedSynodicDays(calendarDaysInMonth);
        double anomalisticDays = settings.resolvedAnomalisticDays(calendarDaysInMonth);
        CelestialMath.Input currentInput = new CelestialMath.Input(0.0D, 1.0D,
                currentCalendarTicks, calendarDaysInMonth,
                synodicDays, anomalisticDays, settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale());
        CelestialMath.Input targetInput = new CelestialMath.Input(0.0D, 1.0D, targetTicks,
                calendarDaysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale());
        CelestialMath.Result currentFrame = CelestialMath.calculate(currentInput);
        CelestialMath.Result targetFrame = CelestialMath.calculate(targetInput);
        BodyEphemeris moon = primaryId.equals(EARTH) ? current.get(MOON) : null;
        if (moon != null) {
                CelestialVector modelCurrent = equatorialToEcliptic(currentFrame.moonGeocentric())
                        .scale(CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM
                                * currentFrame.moonDistance());
                CelestialVector modelTarget = equatorialToEcliptic(targetFrame.moonGeocentric())
                        .scale(CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM
                                * targetFrame.moonDistance());
                CelestialVector actual = moon.position().subtract(primary.position());
                CelestialVector predicted = applyOrbitalDelta(actual, modelCurrent, modelTarget);
                shifted.put(MOON, new BodyEphemeris(primary.position().add(predicted),
                        moon.radius(), moon.parent()));
        }
        return Map.copyOf(shifted);
    }

    /**
     * Advances the complete hierarchy while keeping the Sun at the current frame's anchor. A
     * cached inter-system plan can then follow the changing Earth-centred coordinate origin with
     * one Sun-anchor translation without chasing either endpoint every render frame.
     */
    private static Map<ResourceLocation, BodyEphemeris> shiftedSolarSystemEphemeris(
            Map<ResourceLocation, BodyEphemeris> current,
            Map<ResourceLocation, BodyEphemeris> modelCurrent,
            Map<ResourceLocation, BodyEphemeris> modelTarget) {
        BodyEphemeris sun = requireBody(current, SUN);
        BodyEphemeris modelCurrentSun = requireBody(modelCurrent, SUN);
        BodyEphemeris modelTargetSun = requireBody(modelTarget, SUN);
        Map<ResourceLocation, BodyEphemeris> shifted = new LinkedHashMap<>();
        shifted.put(SUN, sun);
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : current.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.equals(SUN)) continue;
            BodyEphemeris currentModelBody = modelCurrent.get(id);
            BodyEphemeris targetModelBody = modelTarget.get(id);
            if (currentModelBody == null || targetModelBody == null) continue;
            CelestialVector actualFromSun = entry.getValue().position().subtract(sun.position());
            CelestialVector modelFromSun = currentModelBody.position()
                    .subtract(modelCurrentSun.position());
            CelestialVector targetFromSun = targetModelBody.position()
                    .subtract(modelTargetSun.position());
            CelestialVector predicted = sun.position().add(actualFromSun)
                    .add(targetFromSun.subtract(modelFromSun));
            shifted.put(id, new BodyEphemeris(predicted, entry.getValue().radius(),
                    entry.getValue().parent()));
        }

        // Preserve unsupported data-driven children as parent-following obstacles.
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : current.entrySet()) {
            if (shifted.containsKey(entry.getKey())) continue;
            BodyEphemeris body = entry.getValue();
            BodyEphemeris currentParent = body.parent() != null ? current.get(body.parent()) : null;
            BodyEphemeris shiftedParent = body.parent() != null ? shifted.get(body.parent()) : null;
            CelestialVector position = currentParent != null && shiftedParent != null
                    ? shiftedParent.position().add(body.position().subtract(currentParent.position()))
                    : body.position();
            shifted.put(entry.getKey(), new BodyEphemeris(position, body.radius(), body.parent()));
        }
        return Map.copyOf(shifted);
    }

    private static Map<ResourceLocation, BodyEphemeris> modeledSolarSystemEphemeris(
            Map<ResourceLocation, BodyEphemeris> radii, double calendarTicks,
            int calendarDaysInMonth) {
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        double synodicDays = settings.resolvedSynodicDays(calendarDaysInMonth);
        double anomalisticDays = settings.resolvedAnomalisticDays(calendarDaysInMonth);
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(0.0D, 1.0D,
                calendarTicks, calendarDaysInMonth, synodicDays, anomalisticDays,
                settings.nodalYears(), settings.lunarInclinationRadians(), settings.sunScale(),
                settings.moonScale()));
        CelestialPlanetSettings planets = settings.planetSettings();
        CelestialVector sunPosition = new CelestialVector(Math.cos(frame.solarLongitude())
                * planets.earthSemiMajorMillionKm(), Math.sin(frame.solarLongitude())
                * planets.earthSemiMajorMillionKm(), 0.0D);
        Map<ResourceLocation, BodyEphemeris> modeled = new LinkedHashMap<>();
        BodyEphemeris sun = requireBody(radii, SUN);
        modeled.put(SUN, new BodyEphemeris(sunPosition, sun.radius(), sun.parent()));
        BodyEphemeris earth = requireBody(radii, EARTH);
        modeled.put(EARTH, new BodyEphemeris(CelestialVector.ZERO, earth.radius(), earth.parent()));
        BodyEphemeris moon = radii.get(MOON);
        if (moon != null) {
            CelestialVector moonPosition = equatorialToEcliptic(frame.moonGeocentric())
                    .scale(CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM * frame.moonDistance());
            modeled.put(MOON, new BodyEphemeris(moonPosition, moon.radius(), moon.parent()));
        }
        List<CelestialBodyState> bodies = CelestialBodies.calculate(frame,
                CelestialMath.calendarYears(calendarTicks, calendarDaysInMonth), planets,
                settings.orbitalPhases());
        for (CelestialBodyState state : bodies) {
            BodyEphemeris measured = radii.get(state.id());
            if (measured == null) continue;
            modeled.put(state.id(), new BodyEphemeris(state.geocentricPosition(),
                    measured.radius(), measured.parent()));
        }
        return Map.copyOf(modeled);
    }

    private static List<Map<ResourceLocation, BodyEphemeris>> interSystemObstacleTimeline(
            Map<ResourceLocation, BodyEphemeris> current, double calendarTicks,
            double calendarTicksPerGameTick, int calendarDaysInMonth, double elapsedGameTicks,
            long durationGameTicks) {
        int samples = interSystemTransferSampleCount(current, calendarTicksPerGameTick,
                durationGameTicks, calendarDaysInMonth);
        List<Map<ResourceLocation, BodyEphemeris>> timeline = new ArrayList<>(samples + 1);
        Map<ResourceLocation, BodyEphemeris> modelCurrent = modeledSolarSystemEphemeris(current,
                calendarTicks, calendarDaysInMonth);
        for (int step = 0; step <= samples; step++) {
            double journeyTick = durationGameTicks * (step / (double) samples);
            double offset = (journeyTick - elapsedGameTicks) * calendarTicksPerGameTick;
            Map<ResourceLocation, BodyEphemeris> modelTarget = modeledSolarSystemEphemeris(current,
                    calendarTicks + offset, calendarDaysInMonth);
            timeline.add(shiftedSolarSystemEphemeris(current, modelCurrent, modelTarget));
        }
        return List.copyOf(timeline);
    }

    private static int interSystemTransferSampleCount(
            Map<ResourceLocation, BodyEphemeris> current, double calendarTicksPerGameTick,
            long durationGameTicks, int calendarDaysInMonth) {
        double transferCalendarTicks = Math.abs(calendarTicksPerGameTick * durationGameTicks);
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        CelestialPlanetSettings planets = settings.planetSettings();
        double shortestDays = calendarDaysInMonth * 12.0D;
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : current.entrySet()) {
            if (entry.getKey().equals(MOON)) {
                shortestDays = Math.min(shortestDays,
                        settings.resolvedSynodicDays(calendarDaysInMonth));
                continue;
            }
            CelestialBodies body = CelestialBodies.byId(entry.getKey());
            if (body == null) continue;
            double tfcOrbitalDays = planets.parameters(body).orbitalDays()
                    * calendarDaysInMonth * 12.0D / planets.earthOrbitalDays();
            shortestDays = Math.min(shortestDays, tfcOrbitalDays);
        }
        double turns = transferCalendarTicks / (shortestDays * CelestialMath.TICKS_IN_DAY);
        int orbitalSamples = (int) Math.ceil(turns * LOCAL_TRANSFER_SAMPLES_PER_ORBIT);
        int perGameTickSamples = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, durationGameTicks));
        return Math.max(MIN_LOCAL_TRANSFER_SAMPLES, Math.min(MAX_INTER_SYSTEM_TRANSFER_SAMPLES,
                Math.max(orbitalSamples, perGameTickSamples)));
    }

    /**
     * Samples every moon in the shared primary frame across the complete cruise.  Candidate arcs
     * are checked against the obstacle position at the matching journey time; endpoint-only
     * envelopes miss fast moons that cross the route between those two snapshots.
     */
    private static List<Map<ResourceLocation, BodyEphemeris>> localObstacleTimeline(
            Map<ResourceLocation, BodyEphemeris> current, ResourceLocation primaryId,
            double calendarTicks, double calendarTicksPerGameTick, int calendarDaysInMonth,
            double elapsedGameTicks, long durationGameTicks) {
        int samples = localTransferSampleCount(current, primaryId, calendarTicksPerGameTick,
                durationGameTicks, calendarDaysInMonth);
        List<Map<ResourceLocation, BodyEphemeris>> timeline = new ArrayList<>(samples + 1);
        CelestialVector primaryPosition = requireBody(current, primaryId).position();
        for (int step = 0; step <= samples; step++) {
            double journeyTick = durationGameTicks * (step / (double) samples);
            double offset = (journeyTick - elapsedGameTicks) * calendarTicksPerGameTick;
            Map<ResourceLocation, BodyEphemeris> shifted = shiftedSystemEphemeris(current,
                    primaryId, calendarTicks, offset, calendarDaysInMonth);
            BodyEphemeris shiftedPrimary = requireBody(shifted, primaryId);
            timeline.add(translateSystemFrame(shifted, shiftedPrimary.position(),
                    primaryPosition, primaryId));
        }
        return List.copyOf(timeline);
    }

    private static int localTransferSampleCount(Map<ResourceLocation, BodyEphemeris> current,
                                                ResourceLocation primaryId,
                                                double calendarTicksPerGameTick,
                                                long durationGameTicks,
                                                int calendarDaysInMonth) {
        double transferCalendarTicks = Math.abs(calendarTicksPerGameTick * durationGameTicks);
        double shortestDays = Double.POSITIVE_INFINITY;
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : current.entrySet()) {
            if (!primaryId.equals(entry.getValue().parent())) continue;
            if (entry.getKey().equals(MOON)) {
                shortestDays = Math.min(shortestDays,
                        settings.resolvedSynodicDays(calendarDaysInMonth));
                continue;
            }
            CelestialBodies body = CelestialBodies.byId(entry.getKey());
            if (body != null) {
                CelestialPlanetSettings planets = settings.planetSettings();
                double tfcOrbitalDays = planets.parameters(body).orbitalDays()
                        * calendarDaysInMonth * 12.0D / planets.earthOrbitalDays();
                shortestDays = Math.min(shortestDays, tfcOrbitalDays);
            }
        }
        double turns = Double.isFinite(shortestDays)
                ? transferCalendarTicks / (shortestDays * CelestialMath.TICKS_IN_DAY) : 0.0D;
        int orbitalSamples = (int) Math.ceil(turns * LOCAL_TRANSFER_SAMPLES_PER_ORBIT);
        return Math.max(MIN_LOCAL_TRANSFER_SAMPLES,
                Math.min(MAX_LOCAL_TRANSFER_SAMPLES, orbitalSamples));
    }

    private static CelestialVector applyOrbitalDelta(CelestialVector actual,
                                                      CelestialVector modelCurrent,
                                                      CelestialVector modelTarget) {
        double currentLength = modelCurrent.length();
        double targetLength = modelTarget.length();
        if (!(currentLength > 1.0E-12D) || !(targetLength > 1.0E-12D)) return actual;
        CelestialVector from = modelCurrent.scale(1.0D / currentLength);
        CelestialVector to = modelTarget.scale(1.0D / targetLength);
        double dot = clamp(from.dot(to), -1.0D, 1.0D);
        CelestialVector rotated;
        if (dot > 0.999999999D) {
            rotated = actual;
        } else if (dot < -0.999999999D) {
            rotated = rotateAroundAxis(actual, perpendicular(from), Math.PI);
        } else {
            CelestialVector axis = cross(from, to).normalized();
            rotated = rotateAroundAxis(actual, axis, Math.acos(dot));
        }
        return rotated.scale(targetLength / currentLength);
    }

    private static CelestialVector rotateAroundAxis(CelestialVector vector, CelestialVector axis,
                                                     double angle) {
        CelestialVector normal = axis.normalized();
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return vector.scale(cosine).add(cross(normal, vector).scale(sine))
                .add(normal.scale(normal.dot(vector) * (1.0D - cosine)));
    }

    private static CelestialVector equatorialToEcliptic(CelestialVector vector) {
        double cosine = CelestialMath.AXIAL_TILT_COS;
        double sine = CelestialMath.AXIAL_TILT_SIN;
        return new CelestialVector(vector.x(), vector.y() * cosine + vector.z() * sine,
                -vector.y() * sine + vector.z() * cosine);
    }

    private static CelestialVector localSystemTransferPosition(LocalTransferPlan plan,
                                                               double progress) {
        double value = clamp(progress, 0.0D, 1.0D);
        if (value <= 0.0D) return plan.from();
        if (value >= 1.0D) return plan.to();
        double[] lengths = localTransferSegmentLengths(plan);
        double total = 0.0D;
        for (double length : lengths) total += length;
        if (!(total > 1.0E-12D)) return mix(plan.from(), plan.to(), value);
        // One global easing controls departure/arrival. Internal gates retain non-zero through-speed.
        double distance = smoothStep01(value) * total;
        int segment = 0;
        while (segment < lengths.length - 1
                && (lengths[segment] <= 1.0E-12D || distance > lengths[segment])) {
            distance -= lengths[segment++];
        }
        double local = lengths[segment] > 1.0E-12D ? distance / lengths[segment] : 1.0D;
        return switch (segment) {
            case 0 -> sphericalArc(plan.origin().position(), plan.from(),
                    plan.source().stablePhasingPoint(), local, plan.source().stableRadius());
            case 1 -> mix(plan.source().stablePhasingPoint(), plan.source().safeOrbitPoint(), local);
            case 2 -> commonPrimaryTransferArc(plan, local);
            case 3 -> mix(plan.destination().safeOrbitPoint(),
                    plan.destination().stablePhasingPoint(), local);
            default -> sphericalArc(plan.target().position(),
                    plan.destination().stablePhasingPoint(), plan.to(), local,
                    plan.destination().stableRadius());
        };
    }

    private static LocalTransferPlan localTransferPlan(CelestialVector from, CelestialVector to,
                                                        BodyEphemeris primary,
                                                        BodyEphemeris origin,
                                                        BodyEphemeris target,
                                                        LocalTransferGate source,
                                                        LocalTransferGate destination,
                                                        List<Map<ResourceLocation, BodyEphemeris>> obstacles) {
        PrimaryTransferArc arc = choosePrimaryTransferArc(from, to, primary, origin, target,
                source, destination, obstacles);
        return new LocalTransferPlan(from, to, primary, origin, target, source, destination, arc);
    }

    private static CelestialVector commonPrimaryTransferArc(LocalTransferPlan plan, double progress) {
        return cubicBezier(plan.source().safeOrbitPoint(), plan.arc().firstControl(),
                plan.arc().secondControl(), plan.destination().safeOrbitPoint(), progress);
    }

    private static double commonPrimaryTransferLength(LocalTransferPlan plan) {
        return plan.arc().length();
    }

    /**
     * Selects a bounded orbit-like cubic around the common primary.  The first candidates stay in
     * the endpoint orbital chord plane; ecliptic-normal candidates are fallbacks only when another
     * square moon blocks both planar sides.  This is a transfer orbit, not the old system-height
     * corridor: its controls scale from the actual endpoint orbital radii and remain centred on
     * the common planet.
     */
    private static PrimaryTransferArc choosePrimaryTransferArc(CelestialVector stationFrom,
                                                               CelestialVector stationTo,
                                                               BodyEphemeris primary,
                                                               BodyEphemeris origin,
                                                               BodyEphemeris target,
                                                               LocalTransferGate source,
                                                               LocalTransferGate destination,
                                                               List<Map<ResourceLocation, BodyEphemeris>> obstacles) {
        CelestialVector from = source.safeOrbitPoint();
        CelestialVector to = destination.safeOrbitPoint();
        CelestialVector route = to.subtract(from);
        CelestialVector fromOut = from.subtract(primary.position()).normalized();
        CelestialVector toOut = to.subtract(primary.position()).normalized();
        CelestialVector planar = eclipticLateral(route, fromOut);
        CelestialVector chordNormal = cross(fromOut, toOut).normalized();
        CelestialVector orbitalSide = chordNormal.length() > 1.0E-12D
                ? cross(chordNormal, route.normalized()).normalized() : planar;
        List<CelestialVector> sides = List.of(orbitalSide, orbitalSide.scale(-1.0D),
                planar, planar.scale(-1.0D), new CelestialVector(0.0D, 0.0D, 1.0D),
                new CelestialVector(0.0D, 0.0D, -1.0D));
        double fromRadius = from.subtract(primary.position()).length();
        double toRadius = to.subtract(primary.position()).length();
        double baseClearance = Math.max(primary.radius() * 8.0D,
                Math.max(route.length() * 0.35D, Math.min(fromRadius, toRadius) * 0.35D));
        PrimaryTransferArc best = null;
        double bestClearance = Double.NEGATIVE_INFINITY;
        for (double multiplier : new double[]{1.0D, 1.5D, 2.25D, 3.25D}) {
            double handle = baseClearance * multiplier;
            for (CelestialVector side : sides) {
                CelestialVector lateral = side.normalized();
                CelestialVector first = from.add(fromOut.add(lateral).normalized().scale(handle));
                CelestialVector second = to.add(toOut.add(lateral).normalized().scale(handle));
                PrimaryTransferArc candidate = new PrimaryTransferArc(first, second,
                        cubicBezierLength(from, first, second, to));
                LocalTransferPlan candidatePlan = new LocalTransferPlan(stationFrom, stationTo,
                        primary, origin, target, source, destination, candidate);
                double clearance = transferPlanClearance(candidatePlan, obstacles);
                if (clearance > bestClearance) {
                    best = candidate;
                    bestClearance = clearance;
                }
                if (clearance > 0.0D) return candidate;
            }
        }
        return Objects.requireNonNull(best, "local transfer arc fallback");
    }

    private static double transferPlanClearance(LocalTransferPlan plan,
                                                List<Map<ResourceLocation, BodyEphemeris>> obstacles) {
        double minimum = Double.POSITIVE_INFINITY;
        int samples = Math.max(2, obstacles.size() - 1);
        CelestialVector previousPoint = localSystemTransferPosition(plan, 0.0D);
        Map<ResourceLocation, BodyEphemeris> previousFrame = obstacles.get(0);
        for (int step = 1; step <= samples; step++) {
            double progress = step / (double) samples;
            CelestialVector point = localSystemTransferPosition(plan, progress);
            Map<ResourceLocation, BodyEphemeris> frame = obstacles.get(
                    Math.min(step, obstacles.size() - 1));
            minimum = Math.min(minimum, sweptTransferClearance(previousPoint, point,
                    previousFrame, frame));
            previousPoint = point;
            previousFrame = frame;
        }
        return minimum;
    }

    private static double interSystemTransferClearance(InterSystemTransferPlan plan,
                                                        List<Map<ResourceLocation,
                                                                BodyEphemeris>> obstacles) {
        double minimum = Double.POSITIVE_INFINITY;
        int samples = Math.max(2, obstacles.size() - 1);
        CelestialVector previousPoint = interSystemTransferPosition(plan, 0.0D);
        Map<ResourceLocation, BodyEphemeris> previousFrame = obstacles.get(0);
        for (int step = 1; step <= samples; step++) {
            double progress = step / (double) samples;
            CelestialVector point = interSystemTransferPosition(plan, progress);
            Map<ResourceLocation, BodyEphemeris> frame = obstacles.get(
                    Math.min(step, obstacles.size() - 1));
            minimum = Math.min(minimum, sweptTransferClearance(previousPoint, point,
                    previousFrame, frame));
            previousPoint = point;
            previousFrame = frame;
        }
        return minimum;
    }

    /** Conservative relative-motion sweep between adjacent path and ephemeris samples. */
    private static double sweptTransferClearance(CelestialVector stationStart,
                                                  CelestialVector stationEnd,
                                                  Map<ResourceLocation, BodyEphemeris> startFrame,
                                                  Map<ResourceLocation, BodyEphemeris> endFrame) {
        double minimum = Double.POSITIVE_INFINITY;
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : endFrame.entrySet()) {
            BodyEphemeris end = entry.getValue();
            BodyEphemeris start = startFrame.getOrDefault(entry.getKey(), end);
            CelestialVector relativeStart = stationStart.subtract(start.position());
            CelestialVector relativeEnd = stationEnd.subtract(end.position());
            CelestialVector relativeMotion = relativeEnd.subtract(relativeStart);
            double motionSquared = relativeMotion.dot(relativeMotion);
            double along = motionSquared > 1.0E-24D
                    ? clamp(-relativeStart.dot(relativeMotion) / motionSquared, 0.0D, 1.0D)
                    : 0.0D;
            double distance = relativeStart.add(relativeMotion.scale(along)).length();
            double guard = Math.max(start.radius(), end.radius()) * SQRT_THREE * 1.05D;
            minimum = Math.min(minimum, distance - guard);
        }
        return minimum;
    }

    private static LocalTransferPlan translateLocalTransferPlan(LocalTransferPlan plan,
                                                                 CelestialVector translation) {
        if (translation.length() < 1.0E-15D) return plan;
        BodyEphemeris primary = translate(plan.primary(), translation);
        BodyEphemeris origin = translate(plan.origin(), translation);
        BodyEphemeris target = translate(plan.target(), translation);
        LocalTransferGate source = translate(plan.source(), translation);
        LocalTransferGate destination = translate(plan.destination(), translation);
        PrimaryTransferArc arc = new PrimaryTransferArc(
                plan.arc().firstControl().add(translation),
                plan.arc().secondControl().add(translation), plan.arc().length());
        return new LocalTransferPlan(plan.from().add(translation), plan.to().add(translation),
                primary, origin, target, source, destination, arc);
    }

    private static InterSystemTransferPlan translateInterSystemTransferPlan(
            InterSystemTransferPlan plan, CelestialVector translation) {
        if (translation.length() < 1.0E-15D) return plan;
        List<CelestialVector> translatedTimeline = plan.targetBodyTimeline().stream()
                .map(position -> position.add(translation)).toList();
        return new InterSystemTransferPlan(plan.from().add(translation),
                plan.to().add(translation), plan.departureControl().add(translation),
                plan.ingressControl().add(translation), plan.ingress().add(translation),
                plan.ingressRelative(), plan.targetOuterRelative(), plan.targetGateRelative(),
                plan.toRelative(), translatedTimeline, plan.ingressProgress(),
                plan.captureOrbitJoinProgress(), plan.captureRadialJoinProgress(),
                plan.targetStableRadius(), plan.length(), plan.headingRadians());
    }

    private static CelestialVector timelinePosition(List<CelestialVector> timeline,
                                                    double progress) {
        if (timeline.size() == 1) return timeline.get(0);
        double index = clamp(progress, 0.0D, 1.0D) * (timeline.size() - 1);
        int first = Math.min((int) Math.floor(index), timeline.size() - 1);
        int second = Math.min(first + 1, timeline.size() - 1);
        return mix(timeline.get(first), timeline.get(second), index - first);
    }

    private static double inverseCircularTransfer(double value) {
        double low = 0.0D;
        double high = 1.0D;
        for (int step = 0; step < 48; step++) {
            double middle = (low + high) * 0.5D;
            if (circularTransfer(middle) < value) low = middle;
            else high = middle;
        }
        return (low + high) * 0.5D;
    }

    private static BodyEphemeris translate(BodyEphemeris body, CelestialVector translation) {
        return new BodyEphemeris(body.position().add(translation), body.radius(), body.parent());
    }

    private static LocalTransferGate translate(LocalTransferGate gate,
                                                CelestialVector translation) {
        return new LocalTransferGate(gate.stablePhasingPoint().add(translation),
                gate.safeOrbitPoint().add(translation), gate.stableRadius(), gate.safeRadius());
    }

    private static double cubicBezierLength(CelestialVector start, CelestialVector first,
                                            CelestialVector second, CelestialVector end) {
        double length = 0.0D;
        CelestialVector previous = start;
        for (int step = 1; step <= 64; step++) {
            CelestialVector point = cubicBezier(start, first, second, end, step / 64.0D);
            length += point.subtract(previous).length();
            previous = point;
        }
        return length;
    }

    private static double[] localTransferSegmentLengths(LocalTransferPlan plan) {
        return new double[]{
                sphericalArcLength(plan.origin().position(), plan.from(),
                        plan.source().stablePhasingPoint(), plan.source().stableRadius()),
                plan.source().safeOrbitPoint().subtract(plan.source().stablePhasingPoint()).length(),
                commonPrimaryTransferLength(plan),
                plan.destination().stablePhasingPoint().subtract(
                        plan.destination().safeOrbitPoint()).length(),
                sphericalArcLength(plan.target().position(),
                        plan.destination().stablePhasingPoint(), plan.to(),
                        plan.destination().stableRadius())
        };
    }

    private static double sphericalArcLength(CelestialVector center, CelestialVector from,
                                             CelestialVector to, double radius) {
        CelestialVector first = from.subtract(center).normalized();
        CelestialVector second = to.subtract(center).normalized();
        if (first.length() < 1.0E-12D || second.length() < 1.0E-12D) {
            return from.subtract(to).length();
        }
        return Math.acos(clamp(first.dot(second), -1.0D, 1.0D)) * radius;
    }

    private static LocalTransferGate localTransferGate(CelestialVector station,
                                                        BodyEphemeris body,
                                                        Map<ResourceLocation, BodyEphemeris> ephemeris) {
        CelestialVector stableOffset = station.subtract(body.position());
        double stableRadius = stableOffset.length();
        CelestialVector stableDirection = stableOffset.normalized();
        if (stableDirection.length() < 1.0E-12D) {
            stableDirection = new CelestialVector(1.0D, 0.0D, 0.0D);
        }
        double safeRadius = Math.max(stableRadius, body.radius() * 6.0D);
        CelestialVector phasingDirection = chooseClearPhasingDirection(body, stableRadius,
                stableDirection, ephemeris);
        CelestialVector stablePhasingPoint = body.position().add(phasingDirection.scale(stableRadius));
        CelestialVector safeOrbitPoint = body.position().add(phasingDirection.scale(safeRadius));
        return new LocalTransferGate(stablePhasingPoint, safeOrbitPoint, stableRadius, safeRadius);
    }

    private static CelestialVector chooseClearPhasingDirection(BodyEphemeris body,
                                                               double stableRadius,
                                                               CelestialVector stableDirection,
                                                               Map<ResourceLocation, BodyEphemeris> ephemeris) {
        CelestialVector first = eclipticLateral(stableDirection, stableDirection);
        CelestialVector best = first;
        double bestClearance = Double.NEGATIVE_INFINITY;
        for (int step = 0; step < 32; step++) {
            double z = -0.9375D + 1.875D * (step / 31.0D);
            double azimuth = step * Math.PI * (3.0D - Math.sqrt(5.0D));
            double radial = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
            CelestialVector direction = new CelestialVector(radial * Math.cos(azimuth),
                    radial * Math.sin(azimuth), z);
            double clearance = phasingClearance(body, stableRadius, direction, ephemeris);
            if (clearance > bestClearance) {
                best = direction;
                bestClearance = clearance;
            }
        }
        return best;
    }

    private static double phasingClearance(BodyEphemeris endpoint, double radius,
                                           CelestialVector direction,
                                           Map<ResourceLocation, BodyEphemeris> ephemeris) {
        CelestialVector point = endpoint.position().add(direction.scale(radius));
        double clearance = Double.POSITIVE_INFINITY;
        for (BodyEphemeris obstacle : ephemeris.values()) {
            if (obstacle == endpoint) continue;
            clearance = Math.min(clearance, point.subtract(obstacle.position()).length()
                    - obstacle.radius() * Math.sqrt(3.0D) * 1.05D);
        }
        return clearance;
    }

    private static ResourceLocation findId(BodyEphemeris needle,
                                           Map<ResourceLocation, BodyEphemeris> ephemeris) {
        for (Map.Entry<ResourceLocation, BodyEphemeris> entry : ephemeris.entrySet()) {
            if (entry.getValue() == needle) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("Local-system primary is absent");
    }

    /** Radius-interpolated great-circle arc with deterministic handling of opposite directions. */
    private static CelestialVector sphericalArc(CelestialVector center, CelestialVector from,
                                                CelestialVector to, double progress,
                                                double minimumRadius) {
        CelestialVector fromOffset = from.subtract(center);
        CelestialVector toOffset = to.subtract(center);
        double fromRadius = Math.max(minimumRadius, fromOffset.length());
        double toRadius = Math.max(minimumRadius, toOffset.length());
        CelestialVector fromDirection = fromOffset.normalized();
        CelestialVector toDirection = toOffset.normalized();
        if (fromDirection.length() < 1.0E-12D || toDirection.length() < 1.0E-12D) {
            return mix(from, to, clamp(progress, 0.0D, 1.0D));
        }
        double value = clamp(progress, 0.0D, 1.0D);
        double dot = clamp(fromDirection.dot(toDirection), -1.0D, 1.0D);
        CelestialVector direction;
        if (dot > 0.999999D) {
            direction = mix(fromDirection, toDirection, value).normalized();
        } else if (dot < -0.999999D) {
            CelestialVector axis = perpendicular(fromDirection);
            direction = fromDirection.scale(Math.cos(Math.PI * value))
                    .add(axis.scale(Math.sin(Math.PI * value))).normalized();
        } else {
            double angle = Math.acos(dot);
            double inverseSine = 1.0D / Math.sin(angle);
            direction = fromDirection.scale(Math.sin((1.0D - value) * angle) * inverseSine)
                    .add(toDirection.scale(Math.sin(value * angle) * inverseSine)).normalized();
        }
        double radius = Math.max(minimumRadius, lerp(fromRadius, toRadius, value));
        return center.add(direction.scale(radius));
    }

    private static CelestialVector perpendicular(CelestialVector direction) {
        CelestialVector axis = Math.abs(direction.z()) < 0.9D
                ? new CelestialVector(-direction.y(), direction.x(), 0.0D)
                : new CelestialVector(0.0D, -direction.z(), direction.y());
        return axis.normalized();
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    /** Returns the shared non-stellar primary for parent/child or sibling-moon transfers. */
    private static ResourceLocation localSystemPrimary(ResourceLocation fromId, ResourceLocation toId,
                                                       Map<ResourceLocation, BodyEphemeris> ephemeris) {
        BodyEphemeris from = requireBody(ephemeris, fromId);
        BodyEphemeris to = requireBody(ephemeris, toId);
        StationTransferTopology topology = StationTransferTopology.classify(
                topologyNode(fromId, from), topologyNode(toId, to));
        return switch (topology) {
            case PRIMARY_TO_SATELLITE -> fromId;
            case SATELLITE_TO_PRIMARY -> toId;
            case SIBLING_SATELLITES -> from.parent();
            case INTER_SYSTEM -> null;
        };
    }

    private static StationTransferTopology.Node topologyNode(ResourceLocation id,
                                                             BodyEphemeris body) {
        boolean satellite = body.parent() != null && !body.parent().equals(SUN);
        return new StationTransferTopology.Node(id, body.parent(),
                satellite ? CelestialKind.MOON : CelestialKind.PLANET);
    }

    private static CelestialVector eclipticLateral(CelestialVector route, CelestialVector fallback) {
        CelestialVector lateral = new CelestialVector(-route.y(), route.x(), 0.0D).normalized();
        if (lateral.length() > 1.0E-12D) {
            return lateral;
        }
        lateral = new CelestialVector(-fallback.y(), fallback.x(), 0.0D).normalized();
        return lateral.length() > 1.0E-12D ? lateral : new CelestialVector(0.0D, 1.0D, 0.0D);
    }

    private static CelestialVector cubicBezier(CelestialVector start, CelestialVector firstControl,
                                                CelestialVector secondControl, CelestialVector end,
                                                double progress) {
        double inverse = 1.0D - progress;
        return start.scale(inverse * inverse * inverse)
                .add(firstControl.scale(3.0D * inverse * inverse * progress))
                .add(secondControl.scale(3.0D * inverse * progress * progress))
                .add(end.scale(progress * progress * progress));
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
        bodies.put(EARTH, new BodyEphemeris(CelestialVector.ZERO, EARTH_RADIUS_MILLION_KM, SUN));
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
        bodies.put(state.id(), new BodyEphemeris(state.geocentricPosition(), radius,
                state.parentId()));
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

    private static double smoothStep01(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
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

    static CelestialVector normalizedAtLength(CelestialVector vector, double length) {
        return length > 1.0E-12D ? vector.scale(1.0D / length) : CelestialVector.ZERO;
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
                        SunLayer sun, List<BodyLayer> bodies, OrbitIllumination illumination,
                        RelativisticVisualRules.State relativity, CelestialVector velocityDirection,
                        double targetLockStrength) {
        public Frame {
            Objects.requireNonNull(observerPosition, "observerPosition");
            Objects.requireNonNull(sun, "sun");
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
            Objects.requireNonNull(illumination, "illumination");
            Objects.requireNonNull(relativity, "relativity");
            Objects.requireNonNull(velocityDirection, "velocityDirection");
            if (!Double.isFinite(viewRotationRadians)) {
                throw new IllegalArgumentException("Orbit view rotation must be finite");
            }
            if (!Double.isFinite(targetLockStrength)
                    || targetLockStrength < 0.0D || targetLockStrength > 1.0D) {
                throw new IllegalArgumentException("Target lock strength must be finite in [0,1]");
            }
        }
    }

    public record SunLayer(CelestialVector direction, double distance, double radius,
                           double apparentSize, double renderDistance, double renderHalfSize,
                           RelativisticVisualRules.Tint tint) {
        public SunLayer(CelestialVector direction, double distance, double radius, double apparentSize,
                        double renderDistance, double renderHalfSize) {
            this(direction, distance, radius, apparentSize, renderDistance, renderHalfSize,
                    new RelativisticVisualRules.Tint(1.0D, 1.0D, 1.0D, 1.0D));
        }
        public SunLayer {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(tint, "tint");
            if (!(distance > 0.0D) || !(radius > 0.0D) || !Double.isFinite(distance)
                    || !Double.isFinite(radius)
                    || !(apparentSize > 0.0D) || !Double.isFinite(apparentSize)
                    || !(renderDistance > 0.0D) || !Double.isFinite(renderDistance)
                    || !(renderHalfSize > 0.0D) || !Double.isFinite(renderHalfSize)) {
                throw new IllegalArgumentException("Invalid NTM sun layer");
            }
        }

        SunLayer relativity(CelestialVector velocity, RelativisticVisualRules.State state) {
            return new SunLayer(RelativisticVisualRules.aberrate(direction, velocity, state), distance, radius,
                    apparentSize, renderDistance, renderHalfSize * RelativisticVisualRules.angularScale(state),
                    RelativisticVisualRules.tint(direction, velocity, state));
        }
    }

    public record BodyLayer(ResourceLocation body, CelestialVector worldPosition,
                            CelestialVector direction, double distance, double radius,
                            double apparentSize, double renderDistance, double renderHalfSize,
                            double pointAlpha, double cubeAlpha,
                            CelestialVector incomingLightDirection, double sunHalfTangent,
                            List<SatelliteShadow> satelliteShadows, RelativisticVisualRules.Tint tint) {
        public BodyLayer(ResourceLocation body, CelestialVector worldPosition, CelestialVector direction,
                         double distance, double radius, double apparentSize, double renderDistance,
                         double renderHalfSize, double pointAlpha, double cubeAlpha,
                         CelestialVector incomingLightDirection, double sunHalfTangent,
                         List<SatelliteShadow> satelliteShadows) {
            this(body, worldPosition, direction, distance, radius, apparentSize, renderDistance, renderHalfSize,
                    pointAlpha, cubeAlpha, incomingLightDirection, sunHalfTangent, satelliteShadows,
                    new RelativisticVisualRules.Tint(1.0D, 1.0D, 1.0D, 1.0D));
        }
        public BodyLayer {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(worldPosition, "worldPosition");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(incomingLightDirection, "incomingLightDirection");
            Objects.requireNonNull(tint, "tint");
            satelliteShadows = List.copyOf(Objects.requireNonNull(satelliteShadows,
                    "satelliteShadows"));
            if (!(distance > 0.0D) || !(radius > 0.0D) || !(apparentSize > 0.0D)
                    || !(renderDistance > 0.0D) || !(renderHalfSize > 0.0D) || !Double.isFinite(distance)
                    || !Double.isFinite(radius) || !Double.isFinite(apparentSize)
                    || !Double.isFinite(renderDistance) || !Double.isFinite(renderHalfSize)
                    || renderHalfSize >= renderDistance || !unit(pointAlpha) || !unit(cubeAlpha)
                    || !(sunHalfTangent > 0.0D) || !Double.isFinite(sunHalfTangent)
                    || satelliteShadows.size() > MAX_SATELLITE_SHADOWS) {
                throw new IllegalArgumentException("Invalid orbit body visual layer");
            }
        }

        BodyLayer relativity(CelestialVector velocity, RelativisticVisualRules.State state) {
            double angular = RelativisticVisualRules.angularScale(state);
            return new BodyLayer(body, worldPosition, RelativisticVisualRules.aberrate(direction, velocity, state),
                    distance, radius, apparentSize * angular, renderDistance, renderHalfSize * angular,
                    pointAlpha, cubeAlpha, incomingLightDirection, sunHalfTangent, satelliteShadows,
                    RelativisticVisualRules.tint(direction, velocity, state));
        }

        BodyLayer jumpTargetApproach(double reveal) {
            double value = clamp(reveal, 0.0D, 1.0D);
            double pointHalfSize = renderDistance / 100.0D;
            return new BodyLayer(body, worldPosition, direction, distance, radius,
                    lerp(NTM_TRANSITION_MIN_SIZE * 0.5D, apparentSize, value), renderDistance,
                    lerp(pointHalfSize, renderHalfSize, value),
                    lerp(1.0D, pointAlpha, value), cubeAlpha * value,
                    incomingLightDirection, sunHalfTangent, satelliteShadows, tint);
        }

        private static boolean unit(double value) {
            return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
        }
    }

    record BodyEphemeris(CelestialVector position, double radius, ResourceLocation parent) {
        BodyEphemeris {
            Objects.requireNonNull(position, "position");
            if (!(radius > 0.0D) || !Double.isFinite(radius) || !finite(position)) {
                throw new IllegalArgumentException("Invalid body ephemeris");
            }
        }
    }

    record SatelliteShadowFrame(double parentRadius, double sunHalfTangent,
                                List<SatelliteShadow> shadows) {
        SatelliteShadowFrame {
            shadows = List.copyOf(Objects.requireNonNull(shadows, "shadows"));
            if (!(parentRadius > 0.0D) || !Double.isFinite(parentRadius)
                    || !(sunHalfTangent > 0.0D) || !Double.isFinite(sunHalfTangent)
                    || shadows.size() > MAX_SATELLITE_SHADOWS) {
                throw new IllegalArgumentException("Invalid satellite shadow frame");
            }
        }
    }

    public record SatelliteShadow(ResourceLocation satellite, CelestialVector relativePosition,
                                  double halfSize) {
        public SatelliteShadow {
            Objects.requireNonNull(satellite, "satellite");
            Objects.requireNonNull(relativePosition, "relativePosition");
            if (!finite(relativePosition) || !(halfSize > 0.0D) || !Double.isFinite(halfSize)) {
                throw new IllegalArgumentException("Invalid satellite shadow caster");
            }
        }
    }

    private record SatelliteShadowCandidate(SatelliteShadow shadow, double priority) {
    }

    private record JumpLine(CelestialVector source, CelestialVector revealStart,
                            CelestialVector arrival, double joinSpeedFraction) {
    }

    private record JumpObstacles(double[] x, double[] y, double[] z, double[] radius) {
        int size() {
            return x.length;
        }
    }

    private record LocalTransferGate(CelestialVector stablePhasingPoint,
                                     CelestialVector safeOrbitPoint,
                                     double stableRadius,
                                     double safeRadius) {
    }

    private record LocalTransferPlan(CelestialVector from, CelestialVector to,
                                     BodyEphemeris primary, BodyEphemeris origin,
                                     BodyEphemeris target, LocalTransferGate source,
                                     LocalTransferGate destination, PrimaryTransferArc arc) {
    }

    private record PrimaryTransferArc(CelestialVector firstControl,
                                      CelestialVector secondControl,
                                      double length) {
    }

    private record LocalTransferCacheKey(UUID stationId, long celestialGeneration,
                                         ResourceLocation fromBody, ResourceLocation toBody,
                                         long cruiseStartedGameTime, long cruiseDurationTicks,
                                         long departureCalendarTick, long calendarRateBits,
                                         int calendarDaysInMonth, CelestialRuntimeSettings settings) {
    }

    private record CachedLocalTransferPlan(CelestialVector primaryAnchor,
                                            LocalTransferPlan plan) {
    }

    private record InterSystemTransferPlan(CelestialVector from, CelestialVector to,
                                           CelestialVector departureControl,
                                           CelestialVector ingressControl,
                                           CelestialVector ingress,
                                           CelestialVector ingressRelative,
                                           CelestialVector targetOuterRelative,
                                           CelestialVector targetGateRelative,
                                           CelestialVector toRelative,
                                           List<CelestialVector> targetBodyTimeline,
                                           double ingressProgress,
                                           double captureOrbitJoinProgress,
                                           double captureRadialJoinProgress,
                                           double targetStableRadius,
                                           double length, double headingRadians) {
    }

    private record InterSystemTransferCacheKey(UUID stationId, long celestialGeneration,
                                               ResourceLocation fromBody,
                                               ResourceLocation toBody,
                                               long cruiseStartedGameTime,
                                               long cruiseDurationTicks,
                                               long departureCalendarTick,
                                               long calendarRateBits,
                                               int calendarDaysInMonth,
                                               CelestialRuntimeSettings settings) {
    }

    private record CachedInterSystemTransferPlan(CelestialVector sunAnchor,
                                                 InterSystemTransferPlan plan) {
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

    record ProjectedPoint(double x, double y) {
    }
}
