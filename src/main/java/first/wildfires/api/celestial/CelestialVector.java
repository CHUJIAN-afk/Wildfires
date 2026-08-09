package first.wildfires.api.celestial;

/** Immutable double-precision vector used by the common celestial API. */
public record CelestialVector(double x, double y, double z) {

    public static final CelestialVector ZERO = new CelestialVector(0.0D, 0.0D, 0.0D);

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public CelestialVector normalized() {
        double length = length();
        return length > 1.0E-12D ? scale(1.0D / length) : ZERO;
    }

    public CelestialVector scale(double factor) {
        return new CelestialVector(x * factor, y * factor, z * factor);
    }

    public CelestialVector add(CelestialVector other) {
        return new CelestialVector(x + other.x, y + other.y, z + other.z);
    }

    public CelestialVector subtract(CelestialVector other) {
        return new CelestialVector(x - other.x, y - other.y, z - other.z);
    }

    public CelestialVector negated() {
        return new CelestialVector(-x, -y, -z);
    }

    public double dot(CelestialVector other) {
        return x * other.x + y * other.y + z * other.z;
    }
}
