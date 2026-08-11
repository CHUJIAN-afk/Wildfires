package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;

/** Pure projection math for the square 8x8 pixel bodies actually drawn on the sky sphere. */
public final class CelestialDiscGeometry {

    public static final double SKY_SPHERE_RADIUS = 100.0D;
    public static final double PIXEL_COVER_RADIUS = 99.9D;
    public static final double LUNAR_ECLIPSE_LAYER_RADIUS = 99.65D;
    public static final double LUNAR_PENUMBRA_NORMALIZED_WIDTH = 2.0D / 8.0D;
    public static final double SUN_TEXTURE_HALF_SIZE = 30.0D;
    public static final double MOON_TEXTURE_HALF_SIZE = 20.0D;
    public static final double ATLAS_BODY_FRACTION = 4.0D / 16.0D;
    public static final double DEFAULT_SUN_SCALE = 0.725D;
    public static final double DEFAULT_MOON_SCALE = 1.0D;
    private static final double[][] SQUARE_CORNERS = {
            {-1.0D, -1.0D}, {1.0D, -1.0D}, {1.0D, 1.0D}, {-1.0D, 1.0D}
    };
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private CelestialDiscGeometry() {
    }

    public static double sunBodyHalfSize(double sunScale) {
        return atlasBodyHalfSize(SUN_TEXTURE_HALF_SIZE * sunScale);
    }

    public static double moonBodyHalfSize(double moonScale, double normalizedMoonDistance) {
        if (!Double.isFinite(normalizedMoonDistance) || normalizedMoonDistance <= 0.0D) {
            return 0.0D;
        }
        return atlasBodyHalfSize(MOON_TEXTURE_HALF_SIZE * moonScale / normalizedMoonDistance);
    }

    public static double tangentHalfExtent(double bodyHalfSize) {
        return tangentHalfExtent(bodyHalfSize, SKY_SPHERE_RADIUS);
    }

    public static double tangentHalfExtent(double bodyHalfSize, double renderRadius) {
        if (!Double.isFinite(bodyHalfSize) || bodyHalfSize <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(renderRadius) || renderRadius <= 0.0D) {
            return 0.0D;
        }
        return bodyHalfSize / renderRadius;
    }

    /** Fraction of the first rendered square pixel body covered by the second rendered square body. */
    public static double squareCoverage(CelestialVector firstDirection, CelestialVector secondDirection,
                                        CelestialVector celestialNorth, double firstHalfTangent,
                                        double secondHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(secondHalfTangent)
                || !finiteDirection(firstDirection) || !finiteDirection(secondDirection)) {
            return 0.0D;
        }
        CelestialVector first = firstDirection.normalized();
        CelestialVector second = secondDirection.normalized();
        Basis firstBasis = stableBasis(first, celestialNorth);
        Basis secondBasis = stableBasis(second, celestialNorth);
        Scratch scratch = SCRATCH.get();
        for (int index = 0; index < SQUARE_CORNERS.length; index++) {
            double[] corner = SQUARE_CORNERS[index];
            CelestialVector ray = second.add(secondBasis.right().scale(corner[0] * secondHalfTangent))
                    .add(secondBasis.up().scale(corner[1] * secondHalfTangent));
            double forward = ray.dot(first);
            if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
                return 0.0D;
            }
            double x = ray.dot(firstBasis.right()) / forward;
            double y = ray.dot(firstBasis.up()) / forward;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return 0.0D;
            }
            scratch.x1[index] = x;
            scratch.y1[index] = y;
        }
        int count = clip(scratch.x1, scratch.y1, 4, scratch.x2, scratch.y2,
                true, -firstHalfTangent, true);
        count = clip(scratch.x2, scratch.y2, count, scratch.x1, scratch.y1,
                true, firstHalfTangent, false);
        count = clip(scratch.x1, scratch.y1, count, scratch.x2, scratch.y2,
                false, -firstHalfTangent, true);
        count = clip(scratch.x2, scratch.y2, count, scratch.x1, scratch.y1,
                false, firstHalfTangent, false);
        double area = polygonArea(scratch.x1, scratch.y1, count);
        double firstArea = 4.0D * firstHalfTangent * firstHalfTangent;
        return clamp(area / firstArea, 0.0D, 1.0D);
    }

    /**
     * Projects a square shadow center into the first body's pixel coordinates while keeping the
     * shadow aligned to that 8x8 pixel grid. This is the exact geometry consumed by the lunar
     * eclipse shader, where a radius of one means an umbra equal in size to the rendered Moon.
     */
    public static AlignedSquare alignedSquareProjection(CelestialVector firstDirection,
                                                        CelestialVector shadowDirection,
                                                        CelestialVector celestialNorth,
                                                        double firstHalfTangent,
                                                        double shadowHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(shadowHalfTangent)
                || !finiteDirection(firstDirection) || !finiteDirection(shadowDirection)) {
            return AlignedSquare.NONE;
        }
        CelestialVector first = firstDirection.normalized();
        CelestialVector shadow = shadowDirection.normalized();
        double forward = shadow.dot(first);
        if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
            return AlignedSquare.NONE;
        }
        Basis basis = stableBasis(first, celestialNorth);
        double centerScale = forward * firstHalfTangent;
        double centerX = shadow.dot(basis.right()) / centerScale;
        double centerY = shadow.dot(basis.up()) / centerScale;
        double radius = shadowHalfTangent / firstHalfTangent;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !positiveFinite(radius)) {
            return AlignedSquare.NONE;
        }
        return new AlignedSquare(centerX, centerY, radius, true);
    }

    /** Fraction of the first square covered by the exact axis-aligned square returned above. */
    public static double alignedSquareCoverage(CelestialVector firstDirection,
                                               CelestialVector shadowDirection,
                                               CelestialVector celestialNorth,
                                               double firstHalfTangent,
                                               double shadowHalfTangent) {
        AlignedSquare square = alignedSquareProjection(firstDirection, shadowDirection, celestialNorth,
                firstHalfTangent, shadowHalfTangent);
        return alignedSquareCoverage(square);
    }

    /** Fraction of the normalized lunar square covered by an already projected aligned square. */
    public static double alignedSquareCoverage(AlignedSquare square) {
        if (!square.valid()) {
            return 0.0D;
        }
        double xOverlap = Math.max(0.0D, Math.min(1.0D, square.centerX() + square.radius())
                - Math.max(-1.0D, square.centerX() - square.radius()));
        double yOverlap = Math.max(0.0D, Math.min(1.0D, square.centerY() + square.radius())
                - Math.max(-1.0D, square.centerY() - square.radius()));
        return clamp(xOverlap * yOverlap / 4.0D, 0.0D, 1.0D);
    }

    public static Basis stableBasis(CelestialVector bodyDirection, CelestialVector celestialNorth) {
        CelestialVector direction = finiteUnit(bodyDirection, new CelestialVector(0.0D, 1.0D, 0.0D));
        CelestialVector north = finiteUnit(celestialNorth, new CelestialVector(0.0D, 0.0D, 1.0D));
        CelestialVector up = north.subtract(direction.scale(direction.dot(north)));
        if (up.lengthSquared() < 1.0E-12D) {
            CelestialVector fallback = leastAlignedAxis(direction);
            up = fallback.subtract(direction.scale(direction.dot(fallback)));
        }
        up = finiteUnit(up, new CelestialVector(0.0D, 0.0D, 1.0D));
        CelestialVector right = finiteUnit(cross(up, direction), new CelestialVector(1.0D, 0.0D, 0.0D));
        up = finiteUnit(cross(direction, right), up);
        return new Basis(right, up);
    }

    public static double atlasBodyHalfSize(double textureHalfSize) {
        double result = textureHalfSize * ATLAS_BODY_FRACTION;
        return positiveFinite(result) ? result : 0.0D;
    }

    private static int clip(double[] inputX, double[] inputY, int count,
                            double[] outputX, double[] outputY, boolean xAxis,
                            double boundary, boolean keepGreater) {
        if (count == 0) {
            return 0;
        }
        int outputCount = 0;
        double previousX = inputX[count - 1];
        double previousY = inputY[count - 1];
        boolean previousInside = inside(previousX, previousY, xAxis, boundary, keepGreater);
        for (int index = 0; index < count; index++) {
            double currentX = inputX[index];
            double currentY = inputY[index];
            boolean currentInside = inside(currentX, currentY, xAxis, boundary, keepGreater);
            if (currentInside != previousInside) {
                double previousValue = xAxis ? previousX : previousY;
                double currentValue = xAxis ? currentX : currentY;
                double denominator = currentValue - previousValue;
                double fraction = Math.abs(denominator) < 1.0E-15D
                        ? 0.0D : clamp((boundary - previousValue) / denominator, 0.0D, 1.0D);
                outputX[outputCount] = previousX + (currentX - previousX) * fraction;
                outputY[outputCount] = previousY + (currentY - previousY) * fraction;
                outputCount++;
            }
            if (currentInside) {
                outputX[outputCount] = currentX;
                outputY[outputCount] = currentY;
                outputCount++;
            }
            previousX = currentX;
            previousY = currentY;
            previousInside = currentInside;
        }
        return outputCount;
    }

    private static boolean inside(double x, double y, boolean xAxis,
                                  double boundary, boolean keepGreater) {
        double value = xAxis ? x : y;
        return keepGreater ? value >= boundary : value <= boundary;
    }

    private static double polygonArea(double[] x, double[] y, int count) {
        if (count < 3) {
            return 0.0D;
        }
        double twiceArea = 0.0D;
        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;
            twiceArea += x[index] * y[next] - y[index] * x[next];
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static boolean finiteDirection(CelestialVector vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z()) && vector.lengthSquared() > 1.0E-12D;
    }

    private static CelestialVector finiteUnit(CelestialVector vector, CelestialVector fallback) {
        return finiteDirection(vector) ? vector.normalized() : fallback;
    }

    private static CelestialVector leastAlignedAxis(CelestialVector direction) {
        double x = Math.abs(direction.x());
        double y = Math.abs(direction.y());
        double z = Math.abs(direction.z());
        if (x <= y && x <= z) {
            return new CelestialVector(1.0D, 0.0D, 0.0D);
        }
        return y <= z ? new CelestialVector(0.0D, 1.0D, 0.0D)
                : new CelestialVector(0.0D, 0.0D, 1.0D);
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Scratch {
        private final double[] x1 = new double[12];
        private final double[] y1 = new double[12];
        private final double[] x2 = new double[12];
        private final double[] y2 = new double[12];
    }

    public record Basis(CelestialVector right, CelestialVector up) {
    }

    public record AlignedSquare(double centerX, double centerY, double radius, boolean valid) {
        public static final AlignedSquare NONE = new AlignedSquare(0.0D, 0.0D, 0.0D, false);
    }
}
