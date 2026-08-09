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
    MERCURY(4879, 87.968, 57.909, 115.88, 7.004, 0.1, false, null),
    VENUS(12104, 224.695, 108.210, 583.92, 3.395, 0.1, false, null),
    MARS(6792, 779.94, 227.956, 779.94, 1.848, 0.4, false, null),
    JUPITER(142984, 4330.595, 778.479, 4330.595, 1.304, 0.125, false, null),
    SATURN(120536, 10746.94, 1432.041, 378.09, 2.486, 0.15, false, null),
    URANUS(51118, 30588.74, 2867.043, 369.66, 0.770, 0.4, false, null),
    NEPTUNE(49528, 59799.9, 4514.953, 367.49, 1.770, 0.4, false, null),
    PLUTO(2376, 90560.0, 5869.656, 366.73, 17.160, 5.0, false, null),
    PERSEPHONE(50968, 5_478_630.0, 73302.956643, 365.242, 16.0, 5.0, false, null),
    NEMESIS(571936, 4_054_186.2, 14_190_792.6, 365.242, 10.0, 20.0, false, null),
    GANYMEDE(5268.2, 7.15455296, 1.0704, 4330.595, 0.2, 1.0, false, JUPITER),
    CALLISTO(4820.6, 16.6890184, 1.8827, 4330.595, 0.192, 1.0, false, JUPITER),
    IO(3643.2, 1.769137786, 0.4217, 4330.595, 0.05, 1.0, false, JUPITER),
    EUROPA(3121.6, 3.551181, 0.669151, 4330.595, 0.47, 1.0, false, JUPITER),
    TITAN(5149.46, 15.945, 1.22187, 378.09, 0.348, 1.0, true, SATURN),
    TRITON(2706.8, 5.876854, 1.22187, 367.49, 156.885, 1.0, false, NEPTUNE),
    CHARON(1212.5, 6.3872304, 0.709518, 366.73, 0.08, 12.0, false, PLUTO);

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
    private final double scaleFactor;
    private final boolean retrograde;
    private final CelestialBodies parent;
    private final ResourceLocation id;
    private final CelestialBodyParameters defaultParameters;

    CelestialBodies(double diameterKm, double orbitalDays, double semiMajorMillionKm,
                    double synodicDays, double inclinationDegrees, double scaleFactor,
                    boolean retrograde, CelestialBodies parent) {
        this.diameterKm = diameterKm;
        this.orbitalDays = orbitalDays;
        this.semiMajorMillionKm = semiMajorMillionKm;
        this.synodicDays = synodicDays;
        this.inclinationRadians = Math.toRadians(inclinationDegrees);
        this.scaleFactor = scaleFactor;
        this.retrograde = retrograde;
        this.parent = parent;
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
    /** Satellites orbit in their parent's inclined plane, then add their own relative inclination. */
    public double orbitalPlaneInclinationRadians() {
        return orbitalPlaneInclinationRadians(CelestialPlanetSettings.DEFAULT);
    }
    public double scaleFactor() { return scaleFactor; }
    public boolean retrograde() { return retrograde; }
    public CelestialBodies parent() { return parent; }

    public static CelestialBodies byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public CelestialBodyParameters defaultParameters() {
        return defaultParameters;
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears) {
        return calculate(frame, calendarYears, CelestialPlanetSettings.DEFAULT);
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                                      CelestialPlanetSettings settings) {
        CelestialVector[] positions = new CelestialVector[ORDERED.length];
        double earthOrbitalDays = settings.earthOrbitalDays();
        double astronomicalDays = calendarYears * earthOrbitalDays
                + (284.0D / 365.0D + 0.5D) * earthOrbitalDays;
        CelestialVector earth = CelestialMath.orbitalPosition(settings.earthSemiMajorMillionKm(),
                earthOrbitalDays, 0.0D, false, astronomicalDays);
        List<CelestialBodyState> states = new ArrayList<>(ORDERED.length);
        for (CelestialBodies body : ORDERED) {
            CelestialBodyParameters parameters = settings.parameters(body);
            CelestialVector origin = body.parent == null ? CelestialVector.ZERO : positions[body.parent.ordinal()];
            CelestialVector relative = CelestialMath.orbitalPosition(parameters.semiMajorMillionKm(),
                    parameters.orbitalDays(), body.orbitalPlaneInclinationRadians(settings), body.retrograde,
                    astronomicalDays);
            CelestialVector heliocentric = origin == null ? relative : origin.add(relative);
            positions[body.ordinal()] = heliocentric;
            CelestialVector geocentric = heliocentric.subtract(earth);
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
        double ownInclination = settings.parameters(this).inclinationRadians();
        return parent == null ? ownInclination
                : parent.orbitalPlaneInclinationRadians(settings) + ownInclination;
    }

    public static void validateDefinitions() {
        if (values().length != 17) {
            throw new IllegalStateException("Expected all 17 TFCCaelum orbiting bodies, found " + values().length);
        }
        for (CelestialBodies body : values()) {
            if (!(body.diameterKm > 0.0D) || !(body.orbitalDays > 0.0D) || !(body.semiMajorMillionKm > 0.0D)
                    || !(body.synodicDays > 0.0D) || !(body.scaleFactor > 0.0D)
                    || !Double.isFinite(body.inclinationRadians) || (body.parent != null
                    && body.parent.ordinal() >= body.ordinal())) {
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

    private static Map<ResourceLocation, CelestialBodies> createIdIndex() {
        Map<ResourceLocation, CelestialBodies> index = new HashMap<>();
        for (CelestialBodies body : ORDERED) {
            index.put(body.id, body);
        }
        return Map.copyOf(index);
    }
}
