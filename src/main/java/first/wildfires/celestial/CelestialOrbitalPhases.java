package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Immutable world-specific orbital phase offsets, expressed as turns in the ecliptic frame. */
public final class CelestialOrbitalPhases {

    public static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
    private static final List<ResourceLocation> ORDERED_IDS = createOrderedIds();
    public static final CelestialOrbitalPhases ZERO = new CelestialOrbitalPhases(zeroPhases());

    private final Map<ResourceLocation, Double> turnsByBody;
    private final double earthTurns;
    private final double[] bodyTurns;

    public CelestialOrbitalPhases(Map<ResourceLocation, Double> turnsByBody) {
        Objects.requireNonNull(turnsByBody, "turnsByBody");
        if (!turnsByBody.keySet().equals(new java.util.LinkedHashSet<>(ORDERED_IDS))) {
            throw new IllegalArgumentException("Orbital phases must contain Earth and all configured bodies exactly");
        }
        Map<ResourceLocation, Double> validated = new LinkedHashMap<>();
        for (ResourceLocation id : ORDERED_IDS) {
            Double value = turnsByBody.get(id);
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Orbital phase must be finite for " + id);
            }
            validated.put(id, positiveModulo(value));
        }
        this.turnsByBody = Collections.unmodifiableMap(validated);
        this.earthTurns = validated.get(EARTH);
        this.bodyTurns = new double[CelestialBodies.values().length];
        for (CelestialBodies body : CelestialBodies.values()) {
            bodyTurns[body.ordinal()] = validated.get(body.id());
        }
    }

    public double turns(ResourceLocation body) {
        Double turns = turnsByBody.get(Objects.requireNonNull(body, "body"));
        if (turns == null) {
            throw new IllegalArgumentException("Unknown orbital phase body: " + body);
        }
        return turns;
    }

    public Map<ResourceLocation, Double> asMap() {
        return turnsByBody;
    }

    double earthTurns() {
        return earthTurns;
    }

    double turns(CelestialBodies body) {
        return bodyTurns[Objects.requireNonNull(body, "body").ordinal()];
    }

    public static List<ResourceLocation> orderedIds() {
        return ORDERED_IDS;
    }

    /**
     * Builds one creation-time ephemeris.  Heliocentric bodies occupy shuffled, jittered sectors
     * with a guaranteed angular gap, while satellites receive independent phases around parents.
     */
    public static CelestialOrbitalPhases random(RandomGenerator random, CelestialPlanetSettings settings) {
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(settings, "settings");
        List<CelestialBodies> heliocentric = new ArrayList<>();
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() == null) {
                heliocentric.add(body);
            }
        }

        int sectorCount = heliocentric.size() + 1;
        double sectorWidth = 1.0D / sectorCount;
        double rotation = random.nextDouble();
        List<Double> targets = new ArrayList<>(sectorCount);
        for (int index = 0; index < sectorCount; index++) {
            // At most +/-20% of a sector: adjacent initial longitudes retain >=60% sector spacing.
            double jitter = (random.nextDouble() - 0.5D) * sectorWidth * 0.4D;
            targets.add(positiveModulo(rotation + index * sectorWidth + jitter));
        }
        Collections.shuffle(targets, new java.util.Random(random.nextLong()));

        double astronomicalDayZero = (284.0D / 365.0D + 0.5D) * settings.earthOrbitalDays();
        double earthBaseTurns = astronomicalDayZero / settings.earthOrbitalDays();
        double globalTurns = positiveModulo(targets.get(0) - earthBaseTurns);
        Map<ResourceLocation, Double> phases = new LinkedHashMap<>();
        phases.put(EARTH, globalTurns);
        for (int index = 0; index < heliocentric.size(); index++) {
            CelestialBodies body = heliocentric.get(index);
            CelestialBodyParameters parameters = settings.parameters(body);
            double sign = body.retrograde() ? -1.0D : 1.0D;
            double baseTurns = sign * astronomicalDayZero / parameters.orbitalDays();
            double desiredLocalLongitude = CelestialMath.TAU
                    * positiveModulo(targets.get(index + 1) - globalTurns);
            double requiredAnomaly = anomalyForEclipticLongitude(desiredLocalLongitude,
                    parameters.inclinationRadians(), body.ascendingNodeRadians());
            phases.put(body.id(), positiveModulo(requiredAnomaly / CelestialMath.TAU - baseTurns));
        }
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() != null) {
                phases.put(body.id(), random.nextDouble());
            }
        }
        return new CelestialOrbitalPhases(phases);
    }

    /** Smallest circular separation between heliocentric bodies at creation, for validation/tests. */
    public double minimumInitialHeliocentricGap(CelestialPlanetSettings settings) {
        double astronomicalDayZero = (284.0D / 365.0D + 0.5D) * settings.earthOrbitalDays();
        List<Double> angles = new ArrayList<>();
        CelestialVector earth = rotateAroundEclipticNorth(CelestialMath.orbitalPosition(1.0D,
                settings.earthOrbitalDays(), 0.0D, 0.0D, false, astronomicalDayZero), turns(EARTH));
        angles.add(positiveModulo(Math.atan2(earth.y(), earth.x()) / CelestialMath.TAU));
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() == null) {
                CelestialBodyParameters parameters = settings.parameters(body);
                CelestialVector position = rotateAroundEclipticNorth(CelestialMath.orbitalPosition(1.0D,
                        parameters.orbitalDays(), parameters.inclinationRadians(),
                        body.ascendingNodeRadians(), body.retrograde(), astronomicalDayZero,
                        turns(body.id())), turns(EARTH));
                angles.add(positiveModulo(Math.atan2(position.y(), position.x()) / CelestialMath.TAU));
            }
        }
        angles.sort(Double::compareTo);
        double minimum = 1.0D;
        for (int index = 0; index < angles.size(); index++) {
            double current = angles.get(index);
            double next = index + 1 < angles.size() ? angles.get(index + 1) : angles.get(0) + 1.0D;
            minimum = Math.min(minimum, next - current);
        }
        return minimum;
    }

    private static List<ResourceLocation> createOrderedIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(EARTH);
        for (CelestialBodies body : CelestialBodies.values()) {
            ids.add(body.id());
        }
        return List.copyOf(ids);
    }

    private static Map<ResourceLocation, Double> zeroPhases() {
        Map<ResourceLocation, Double> phases = new LinkedHashMap<>();
        for (ResourceLocation id : ORDERED_IDS) {
            phases.put(id, 0.0D);
        }
        return phases;
    }

    private static double anomalyForEclipticLongitude(double longitude, double inclination,
                                                       double ascendingNode) {
        double relative = longitude - ascendingNode;
        return Math.atan2(Math.sin(relative), Math.cos(relative) * Math.cos(inclination));
    }

    private static CelestialVector rotateAroundEclipticNorth(CelestialVector vector, double turns) {
        double angle = CelestialMath.TAU * turns;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new CelestialVector(vector.x() * cosine - vector.y() * sine,
                vector.x() * sine + vector.y() * cosine, vector.z());
    }

    private static double positiveModulo(double value) {
        return value - Math.floor(value);
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof CelestialOrbitalPhases other
                && turnsByBody.equals(other.turnsByBody);
    }

    @Override
    public int hashCode() {
        return turnsByBody.hashCode();
    }

    @Override
    public String toString() {
        return "CelestialOrbitalPhases" + turnsByBody;
    }
}
