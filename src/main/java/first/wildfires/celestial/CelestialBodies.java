package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialVector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Three-dimensional planets and satellites retained from TFCCaelum. */
public enum CelestialBodies {
    // radius/period values and J2000 ecliptic inclinations/nodes; spin obliquity is physical.
    MERCURY(4879, 87.9691, 57.909, 115.88, 7.005, 48.331, 318.235, 7.037, 0.1,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    VENUS(12104, 224.701, 108.210, 583.92, 3.3946, 76.680, 30.187, 1.239, 0.1,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    MARS(6792, 686.980, 227.956, 779.94, 1.850, 49.558, 352.908, 26.718, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    JUPITER(142984, 4332.59, 778.479, 398.88, 1.303, 100.464, 247.818, 2.217, 0.125,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    SATURN(120536, 10759.22, 1432.041, 378.09, 2.485, 113.665, 79.528, 28.052, 0.15,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    URANUS(51118, 30688.5, 2867.043, 369.66, 0.773, 74.006, 257.647, 82.278, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    NEPTUNE(49528, 60182.0, 4514.953, 367.49, 1.770, 131.784, 319.235, 28.026, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    PLUTO(2376, 90560.0, 5869.656, 366.73, 17.160, 110.299, 137.351, 112.816, 5.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    PERSEPHONE(50968, 5_478_630.0, 73302.956643, 365.242, 16.0, 0.0, 0.0, 0.0, 5.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    NEMESIS(571936, 4_054_186.2, 14_190_792.6, 365.242, 10.0, 0.0, 0.0, 0.0, 20.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    // JPL mean elements use each satellite's stated ecliptic/Laplace/equatorial reference plane.
    GANYMEDE(5268.2, 7.15455296, 1.0704, 4330.595, 0.20, 58.5, 0.0, 0.20, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.2, 64.6),
    CALLISTO(4820.6, 16.6890184, 1.8827, 4330.595, 0.30, 309.1, 0.0, 0.30, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.7, 64.8),
    IO(3643.2, 1.769137786, 0.4217, 4330.595, 0.0, 0.0, 0.0, 0.0, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.1, 64.5),
    EUROPA(3121.6, 3.551181, 0.669151, 4330.595, 0.50, 184.0, 0.0, 0.50, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.1, 64.5),
    TITAN(5149.46, 15.945, 1.22187, 378.09, 0.30, 78.6, 0.0, 0.30, 1.0,
            false, SATURN, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 36.4, 84.0),
    TRITON(2706.8, 5.876854, 0.354759, 367.49, 157.3, 178.1, 0.0, 157.3, 1.0,
            false, NEPTUNE, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 299.8, 43.1),
    CHARON(1212.5, 6.3872304, 0.019596, 366.73, 0.0, 0.0, 0.0, 0.0, 12.0,
            false, PLUTO, OrbitReferenceFrame.PARENT_EQUATOR, 0.0, 90.0);

    public static final double EARTH_ORBITAL_DAYS = 365.242D;
    public static final double EARTH_DIAMETER_KM = 12742.0D;
    public static final double EARTH_SEMI_MAJOR_AXIS = 149.598D;
    private static final CelestialBodies[] ORDERED = values();
    private static final Map<ResourceLocation, CelestialBodies> BY_ID = createIdIndex();

    private final double diameterKm;
    private final double orbitalDays;
    private final double semiMajorMillionKm;
    private final double synodicDays;
    private final double inclinationRadians;
    private final double ascendingNodeRadians;
    private final double spinPoleLongitudeRadians;
    private final double axialTiltRadians;
    private final double scaleFactor;
    private final boolean retrograde;
    private final CelestialBodies parent;
    private final OrbitReferenceFrame orbitReferenceFrame;
    private final double referencePoleRightAscensionRadians;
    private final double referencePoleDeclinationRadians;
    private final ResourceLocation id;
    private final CelestialBodyParameters defaultParameters;

    CelestialBodies(double diameterKm, double orbitalDays, double semiMajorMillionKm,
                    double synodicDays, double inclinationDegrees, double ascendingNodeDegrees,
                    double spinPoleLongitudeDegrees, double axialTiltDegrees, double scaleFactor,
                    boolean retrograde, CelestialBodies parent,
                    OrbitReferenceFrame orbitReferenceFrame,
                    double referencePoleRightAscensionDegrees,
                    double referencePoleDeclinationDegrees) {
        this.diameterKm = diameterKm;
        this.orbitalDays = orbitalDays;
        this.semiMajorMillionKm = semiMajorMillionKm;
        this.synodicDays = synodicDays;
        this.inclinationRadians = Math.toRadians(inclinationDegrees);
        this.ascendingNodeRadians = Math.toRadians(ascendingNodeDegrees);
        this.spinPoleLongitudeRadians = Math.toRadians(spinPoleLongitudeDegrees);
        this.axialTiltRadians = Math.toRadians(axialTiltDegrees);
        this.scaleFactor = scaleFactor;
        this.retrograde = retrograde;
        this.parent = parent;
        this.orbitReferenceFrame = orbitReferenceFrame;
        this.referencePoleRightAscensionRadians = Math.toRadians(referencePoleRightAscensionDegrees);
        this.referencePoleDeclinationRadians = Math.toRadians(referencePoleDeclinationDegrees);
        this.id = ResourceLocation.fromNamespaceAndPath("wildfires", name().toLowerCase());
        this.defaultParameters = new CelestialBodyParameters(diameterKm, orbitalDays, semiMajorMillionKm,
                synodicDays, this.inclinationRadians);
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("wildfires",
                "textures/sky/planets/" + name().toLowerCase() + ".png");
    }

    public double diameterKm() { return diameterKm; }
    public double orbitalDays() { return orbitalDays; }
    public double semiMajorMillionKm() { return semiMajorMillionKm; }
    public double synodicDays() { return synodicDays; }
    public double inclinationRadians() { return inclinationRadians; }
    public double ascendingNodeRadians() { return ascendingNodeRadians; }
    public double spinPoleLongitudeRadians() { return spinPoleLongitudeRadians; }
    public double axialTiltRadians() { return axialTiltRadians; }
    /** Inclination of the resulting physical orbit plane against the common ecliptic. */
    public double orbitalPlaneInclinationRadians() {
        return orbitalPlaneInclinationRadians(CelestialPlanetSettings.DEFAULT);
    }
    public CelestialVector orbitalPlaneNormalEcliptic() {
        return orbitalNormal(CelestialPlanetSettings.DEFAULT);
    }
    /** Orbit-plane normal after applying the synchronized per-body inclination settings. */
    public CelestialVector orbitalPlaneNormalEcliptic(CelestialPlanetSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Celestial planet settings cannot be null");
        }
        return orbitalNormal(settings);
    }
    public CelestialVector spinAxisEcliptic() {
        if (parent != null) {
            return orbitalNormal(CelestialPlanetSettings.DEFAULT);
        }
        double longitude = spinPoleLongitudeRadians;
        return new CelestialVector(Math.sin(axialTiltRadians) * Math.cos(longitude),
                Math.sin(axialTiltRadians) * Math.sin(longitude),
                Math.cos(axialTiltRadians)).normalized();
    }
    public double scaleFactor() { return scaleFactor; }
    public boolean retrograde() { return retrograde; }
    public CelestialBodies parent() { return parent; }
    public OrbitReferenceFrame orbitReferenceFrame() { return orbitReferenceFrame; }

    /** Normal of the source element reference plane, expressed in the common J2000 ecliptic frame. */
    public CelestialVector orbitalReferenceNormalEcliptic() {
        return switch (orbitReferenceFrame) {
            case ECLIPTIC -> new CelestialVector(0.0D, 0.0D, 1.0D);
            case PARENT_EQUATOR -> parent.spinAxisEcliptic();
            case J2000_EQUATORIAL_POLE -> equatorialPoleToEcliptic(
                    referencePoleRightAscensionRadians, referencePoleDeclinationRadians);
        };
    }

    public static CelestialBodies byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public CelestialBodyParameters defaultParameters() {
        return defaultParameters;
    }

    /** Upgrades untouched values emitted by the former TFCCaelum-derived defaults. */
    public CelestialBodyParameters migrateLegacyDefaults(CelestialBodyParameters configured) {
        CelestialBodyParameters legacy = legacyDefaultParameters();
        if (legacy == null) {
            return configured;
        }
        return new CelestialBodyParameters(
                migrate(configured.diameterKm(), legacy.diameterKm(), defaultParameters.diameterKm()),
                migrate(configured.orbitalDays(), legacy.orbitalDays(), defaultParameters.orbitalDays()),
                migrate(configured.semiMajorMillionKm(), legacy.semiMajorMillionKm(),
                        defaultParameters.semiMajorMillionKm()),
                migrate(configured.synodicDays(), legacy.synodicDays(), defaultParameters.synodicDays()),
                migrate(configured.inclinationRadians(), legacy.inclinationRadians(),
                        defaultParameters.inclinationRadians()));
    }

    private CelestialBodyParameters legacyDefaultParameters() {
        return switch (this) {
            case MERCURY -> legacy(4879, 87.968, 57.909, 115.88, 7.004);
            case VENUS -> legacy(12104, 224.695, 108.210, 583.92, 3.395);
            case MARS -> legacy(6792, 779.94, 227.956, 779.94, 1.848);
            case JUPITER -> legacy(142984, 4330.595, 778.479, 4330.595, 1.304);
            case SATURN -> legacy(120536, 10746.94, 1432.041, 378.09, 2.486);
            case URANUS -> legacy(51118, 30588.74, 2867.043, 369.66, 0.770);
            case NEPTUNE -> legacy(49528, 59799.9, 4514.953, 367.49, 1.770);
            case PLUTO -> legacy(2376, 90560.0, 5869.656, 366.73, 17.160);
            case CALLISTO -> legacy(4820.6, 16.6890184, 1.8827, 4330.595, 0.192);
            case IO -> legacy(3643.2, 1.769137786, 0.4217, 4330.595, 0.05);
            case EUROPA -> legacy(3121.6, 3.551181, 0.669151, 4330.595, 0.47);
            case TITAN -> legacy(5149.46, 15.945, 1.22187, 378.09, 0.348);
            case TRITON -> legacy(2706.8, 5.876854, 0.354759, 367.49, 23.115);
            case CHARON -> legacy(1212.5, 6.3872304, 0.019596, 366.73, 0.08);
            default -> null;
        };
    }

    private static CelestialBodyParameters legacy(double diameter, double period, double axis,
                                                   double synodic, double inclinationDegrees) {
        return new CelestialBodyParameters(diameter, period, axis, synodic,
                Math.toRadians(inclinationDegrees));
    }

    private static double migrate(double configured, double legacy, double corrected) {
        return Double.doubleToLongBits(configured) == Double.doubleToLongBits(legacy)
                ? corrected : configured;
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears) {
        return calculate(frame, calendarYears, CelestialPlanetSettings.DEFAULT);
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                                      CelestialPlanetSettings settings) {
        return calculate(frame, calendarYears, settings, CelestialOrbitalPhases.ZERO);
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                                      CelestialPlanetSettings settings,
                                                      CelestialOrbitalPhases phases) {
        CelestialVector[] positions = new CelestialVector[ORDERED.length];
        double earthOrbitalDays = settings.earthOrbitalDays();
        double astronomicalDays = calendarYears * earthOrbitalDays
                + (284.0D / 365.0D + 0.5D) * earthOrbitalDays;
        double referenceFrameTurns = phases.turns(CelestialOrbitalPhases.EARTH);
        CelestialVector earth = rotateEcliptic(CelestialMath.orbitalPosition(
                settings.earthSemiMajorMillionKm(), earthOrbitalDays, 0.0D, 0.0D,
                false, astronomicalDays), referenceFrameTurns);
        List<CelestialBodyState> states = new ArrayList<>(ORDERED.length);
        for (CelestialBodies body : ORDERED) {
            CelestialBodyParameters parameters = settings.parameters(body);
            CelestialVector origin = body.parent == null ? CelestialVector.ZERO : positions[body.parent.ordinal()];
            CelestialVector unrotatedRelative = body.parent == null
                    ? CelestialMath.orbitalPosition(parameters.semiMajorMillionKm(),
                            parameters.orbitalDays(), parameters.inclinationRadians(),
                            body.ascendingNodeRadians, body.retrograde, astronomicalDays,
                            phases.turns(body.id()))
                    : CelestialMath.satelliteOrbitalPosition(parameters.semiMajorMillionKm(),
                            parameters.orbitalDays(), body.orbitalReferenceNormalEcliptic(),
                            parameters.inclinationRadians(), body.ascendingNodeRadians,
                            body.retrograde, astronomicalDays, phases.turns(body.id()));
            CelestialVector relative = rotateEcliptic(unrotatedRelative, referenceFrameTurns);
            CelestialVector heliocentric = origin == null ? relative : origin.add(relative);
            positions[body.ordinal()] = heliocentric;
            // The world phase rotates the inertial reference frame, not TFC's seasonal axes.
            // Undo that common rotation before exposing Earth-local directions and station visuals.
            CelestialVector geocentric = rotateEcliptic(heliocentric.subtract(earth),
                    -referenceFrameTurns);
            CelestialVector equatorial = rotateEclipticVector(geocentric.normalized());
            CelestialVector direction = CelestialMath.equatorialToHorizon(equatorial, frame.latitude(),
                    frame.localSiderealAngle());
            double distance = Math.max(1.0E-6D, geocentric.length());
            double angularRadius = Math.atan2(parameters.diameterKm() * 0.5D, distance * 1_000_000.0D);
            double altitude = Math.asin(Math.max(-1.0D, Math.min(1.0D, direction.y())));
            states.add(new CelestialBodyState(body.id,
                    body.parent == null ? null : body.parent.id,
                    geocentric, direction, distance, angularRadius, altitude,
                    Math.min(1.0D, 1.0D / Math.sqrt(distance)), 1.0D, 0.0D));
        }
        return states;
    }

    private double orbitalPlaneInclinationRadians(CelestialPlanetSettings settings) {
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, orbitalNormal(settings).z())));
    }

    private CelestialVector orbitalNormal(CelestialPlanetSettings settings) {
        double inclination = settings.parameters(this).inclinationRadians();
        if (parent == null) {
            return new CelestialVector(Math.sin(inclination) * Math.sin(ascendingNodeRadians),
                    -Math.sin(inclination) * Math.cos(ascendingNodeRadians),
                    Math.cos(inclination)).normalized();
        }
        CelestialVector atNode = CelestialMath.satelliteOrbitalPosition(1.0D, 4.0D,
                orbitalReferenceNormalEcliptic(), inclination, ascendingNodeRadians, retrograde, 0.0D);
        CelestialVector quarter = CelestialMath.satelliteOrbitalPosition(1.0D, 4.0D,
                orbitalReferenceNormalEcliptic(), inclination, ascendingNodeRadians, retrograde, 1.0D);
        return cross(atNode, quarter).normalized();
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    public static void validateDefinitions() {
        if (values().length != 17) {
            throw new IllegalStateException("Expected all 17 TFCCaelum orbiting bodies, found " + values().length);
        }
        for (CelestialBodies body : values()) {
            if (!(body.diameterKm > 0.0D) || !(body.orbitalDays > 0.0D) || !(body.semiMajorMillionKm > 0.0D)
                    || !(body.synodicDays > 0.0D) || !(body.scaleFactor > 0.0D)
                    || !Double.isFinite(body.inclinationRadians)
                    || !Double.isFinite(body.ascendingNodeRadians)
                    || !Double.isFinite(body.axialTiltRadians) || (body.parent != null
                    && body.parent.ordinal() >= body.ordinal())
                    || body.orbitReferenceFrame == OrbitReferenceFrame.PARENT_EQUATOR
                    && body.parent == null) {
                throw new IllegalStateException("Invalid celestial body definition: " + body.name());
            }
            CelestialVector first = CelestialMath.orbitalPosition(body.semiMajorMillionKm, body.orbitalDays,
                    body.inclinationRadians, body.retrograde, 12345.678D);
            CelestialVector second = CelestialMath.orbitalPosition(body.semiMajorMillionKm, body.orbitalDays,
                    body.inclinationRadians, body.retrograde, 12345.678D);
            if (!first.equals(second)) {
                throw new IllegalStateException("Non-deterministic orbit definition: " + body.name());
            }
        }
    }

    private static CelestialVector rotateEclipticVector(CelestialVector vector) {
        double cos = Math.cos(CelestialMath.AXIAL_TILT);
        double sin = Math.sin(CelestialMath.AXIAL_TILT);
        return new CelestialVector(vector.x(), vector.y() * cos - vector.z() * sin,
                vector.y() * sin + vector.z() * cos).normalized();
    }

    private static CelestialVector rotateEcliptic(CelestialVector vector, double turns) {
        double angle = CelestialMath.TAU * turns;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new CelestialVector(vector.x() * cosine - vector.y() * sine,
                vector.x() * sine + vector.y() * cosine, vector.z());
    }

    private static CelestialVector equatorialPoleToEcliptic(double rightAscension, double declination) {
        double cosDeclination = Math.cos(declination);
        double x = cosDeclination * Math.cos(rightAscension);
        double equatorialY = cosDeclination * Math.sin(rightAscension);
        double equatorialZ = Math.sin(declination);
        double cosObliquity = Math.cos(CelestialMath.AXIAL_TILT);
        double sinObliquity = Math.sin(CelestialMath.AXIAL_TILT);
        return new CelestialVector(x,
                equatorialY * cosObliquity + equatorialZ * sinObliquity,
                -equatorialY * sinObliquity + equatorialZ * cosObliquity).normalized();
    }

    public enum OrbitReferenceFrame {
        ECLIPTIC,
        PARENT_EQUATOR,
        J2000_EQUATORIAL_POLE
    }

    private static Map<ResourceLocation, CelestialBodies> createIdIndex() {
        Map<ResourceLocation, CelestialBodies> index = new HashMap<>();
        for (CelestialBodies body : ORDERED) {
            index.put(body.id, body);
        }
        return Map.copyOf(index);
    }
}
