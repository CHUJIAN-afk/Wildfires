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
    private static final CelestialVector X_AXIS = new CelestialVector(1.0D, 0.0D, 0.0D);
    private static final CelestialVector Y_AXIS = new CelestialVector(0.0D, 1.0D, 0.0D);
    private static final CelestialVector Z_AXIS = new CelestialVector(0.0D, 0.0D, 1.0D);
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
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(secondHalfTangent)) {
            return 0.0D;
        }
        double firstLengthSquared = finiteLengthSquared(firstDirection);
        if (!(firstLengthSquared > 1.0E-12D)) {
            return 0.0D;
        }
        double secondLengthSquared = finiteLengthSquared(secondDirection);
        if (!(secondLengthSquared > 1.0E-12D)) {
            return 0.0D;
        }
        double firstInverse = 1.0D / Math.sqrt(firstLengthSquared);
        double firstX = firstDirection.x() * firstInverse;
        double firstY = firstDirection.y() * firstInverse;
        double firstZ = firstDirection.z() * firstInverse;
        double secondInverse = 1.0D / Math.sqrt(secondLengthSquared);
        double secondX = secondDirection.x() * secondInverse;
        double secondY = secondDirection.y() * secondInverse;
        double secondZ = secondDirection.z() * secondInverse;
        Scratch scratch = SCRATCH.get();
        prepareStableBasis(scratch, firstX, firstY, firstZ, celestialNorth);
        double firstRightX = scratch.basisRightX;
        double firstRightY = scratch.basisRightY;
        double firstRightZ = scratch.basisRightZ;
        double firstUpX = scratch.basisUpX;
        double firstUpY = scratch.basisUpY;
        double firstUpZ = scratch.basisUpZ;
        prepareStableBasis(scratch, secondX, secondY, secondZ, celestialNorth);
        return squareCoverageProjectedRaw(firstX, firstY, firstZ,
                secondX, secondY, secondZ,
                firstRightX, firstRightY, firstRightZ,
                firstUpX, firstUpY, firstUpZ,
                scratch, firstHalfTangent, secondHalfTangent);
    }

    /**
     * Prepares the immutable first-disc direction and basis for callers that compare many second
     * discs against the same sky direction. The scalar operations exactly match the corresponding
     * portion of {@link #squareCoverage}.
     */
    static PreparedSquare prepareFirstSquare(CelestialVector firstDirection,
                                             CelestialVector celestialNorth) {
        double firstLengthSquared = finiteLengthSquared(firstDirection);
        if (!(firstLengthSquared > 1.0E-12D)) {
            return PreparedSquare.NONE;
        }
        CelestialVector first = normalized(firstDirection, firstLengthSquared);
        return new PreparedSquare(first, celestialNorth,
                stableBasis(first, celestialNorth), true);
    }

    /** Same result as {@link #squareCoverage}, reusing only an already prepared first disc. */
    static double squareCoverage(PreparedSquare first, CelestialVector secondDirection,
                                 double firstHalfTangent, double secondHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(secondHalfTangent)
                || first == null || !first.valid()) {
            return 0.0D;
        }
        return squareCoveragePrepared(first, secondDirection,
                firstHalfTangent, secondHalfTangent);
    }

    /** Internal hot path for a first square and radii already validated by their preparer. */
    static double squareCoveragePrepared(PreparedSquare first, CelestialVector secondDirection,
                                         double firstHalfTangent, double secondHalfTangent) {
        double secondLengthSquared = finiteLengthSquared(secondDirection);
        if (!(secondLengthSquared > 1.0E-12D)) {
            return 0.0D;
        }
        CelestialVector second = normalized(secondDirection, secondLengthSquared);
        Basis secondBasis = stableBasis(second, first.celestialNorth());
        return squareCoverageProjected(first.direction(), second, first.basis(), secondBasis,
                firstHalfTangent, secondHalfTangent);
    }

    /**
     * Trusted regional-eclipse path that reproduces the caller's vector normalization followed by
     * {@link #squareCoveragePrepared}'s second normalization without materializing either
     * intermediate vector. Both length calculations and both scaling stages intentionally remain.
     */
    static double squareCoveragePreparedRaw(PreparedSquare first,
                                             double rawSecondX, double rawSecondY,
                                             double rawSecondZ, double firstHalfTangent,
                                             double secondHalfTangent) {
        double callerLengthSquared = rawSecondX * rawSecondX + rawSecondY * rawSecondY
                + rawSecondZ * rawSecondZ;
        double callerLength = Math.sqrt(callerLengthSquared);
        if (!(callerLength > 1.0E-12D)) {
            return 0.0D;
        }
        double callerInverse = 1.0D / callerLength;
        double onceNormalizedX = rawSecondX * callerInverse;
        double onceNormalizedY = rawSecondY * callerInverse;
        double onceNormalizedZ = rawSecondZ * callerInverse;
        if (!Double.isFinite(onceNormalizedX) || !Double.isFinite(onceNormalizedY)
                || !Double.isFinite(onceNormalizedZ)) {
            return 0.0D;
        }
        double secondLengthSquared = onceNormalizedX * onceNormalizedX
                + onceNormalizedY * onceNormalizedY + onceNormalizedZ * onceNormalizedZ;
        if (!(secondLengthSquared > 1.0E-12D)) {
            return 0.0D;
        }
        double secondInverse = 1.0D / Math.sqrt(secondLengthSquared);
        double secondX = onceNormalizedX * secondInverse;
        double secondY = onceNormalizedY * secondInverse;
        double secondZ = onceNormalizedZ * secondInverse;
        Scratch scratch = SCRATCH.get();
        prepareStableBasis(scratch, secondX, secondY, secondZ, first.celestialNorth());
        return squareCoverageProjectedRaw(first.direction().x(), first.direction().y(),
                first.direction().z(), secondX, secondY, secondZ,
                first.basis().right().x(), first.basis().right().y(),
                first.basis().right().z(), first.basis().up().x(),
                first.basis().up().y(), first.basis().up().z(),
                scratch, firstHalfTangent, secondHalfTangent);
    }

    private static double squareCoverageProjected(CelestialVector first, CelestialVector second,
                                                  Basis firstBasis, Basis secondBasis,
                                                  double firstHalfTangent,
                                                  double secondHalfTangent) {
        Scratch scratch = SCRATCH.get();
        for (int index = 0; index < SQUARE_CORNERS.length; index++) {
            double[] corner = SQUARE_CORNERS[index];
            double rightScale = corner[0] * secondHalfTangent;
            double upScale = corner[1] * secondHalfTangent;
            double rayX = (second.x() + secondBasis.right().x() * rightScale)
                    + secondBasis.up().x() * upScale;
            double rayY = (second.y() + secondBasis.right().y() * rightScale)
                    + secondBasis.up().y() * upScale;
            double rayZ = (second.z() + secondBasis.right().z() * rightScale)
                    + secondBasis.up().z() * upScale;
            double forward = rayX * first.x() + rayY * first.y() + rayZ * first.z();
            if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
                return 0.0D;
            }
            double x = (rayX * firstBasis.right().x() + rayY * firstBasis.right().y()
                    + rayZ * firstBasis.right().z()) / forward;
            double y = (rayX * firstBasis.up().x() + rayY * firstBasis.up().y()
                    + rayZ * firstBasis.up().z()) / forward;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return 0.0D;
            }
            scratch.x1[index] = x;
            scratch.y1[index] = y;
        }
        return clippedCoverage(scratch, firstHalfTangent);
    }

    private static double squareCoverageProjectedRaw(double firstX, double firstY,
                                                      double firstZ, double secondX,
                                                      double secondY, double secondZ,
                                                      double firstRightX,
                                                      double firstRightY,
                                                      double firstRightZ,
                                                      double firstUpX, double firstUpY,
                                                      double firstUpZ, Scratch scratch,
                                                      double firstHalfTangent,
                                                      double secondHalfTangent) {
        for (int index = 0; index < SQUARE_CORNERS.length; index++) {
            double[] corner = SQUARE_CORNERS[index];
            double rightScale = corner[0] * secondHalfTangent;
            double upScale = corner[1] * secondHalfTangent;
            double rayX = (secondX + scratch.basisRightX * rightScale)
                    + scratch.basisUpX * upScale;
            double rayY = (secondY + scratch.basisRightY * rightScale)
                    + scratch.basisUpY * upScale;
            double rayZ = (secondZ + scratch.basisRightZ * rightScale)
                    + scratch.basisUpZ * upScale;
            double forward = rayX * firstX + rayY * firstY + rayZ * firstZ;
            if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
                return 0.0D;
            }
            double x = (rayX * firstRightX + rayY * firstRightY
                    + rayZ * firstRightZ) / forward;
            double y = (rayX * firstUpX + rayY * firstUpY
                    + rayZ * firstUpZ) / forward;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return 0.0D;
            }
            scratch.x1[index] = x;
            scratch.y1[index] = y;
        }
        return clippedCoverage(scratch, firstHalfTangent);
    }

    private static double clippedCoverage(Scratch scratch, double firstHalfTangent) {
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

    /** Scalar form of {@link #stableBasis} used only by the regional-eclipse scratch path. */
    private static void prepareStableBasis(Scratch scratch, double bodyX, double bodyY,
                                           double bodyZ, CelestialVector celestialNorth) {
        double directionLengthSquared = bodyX * bodyX + bodyY * bodyY + bodyZ * bodyZ;
        double directionX;
        double directionY;
        double directionZ;
        if (!Double.isFinite(bodyX) || !Double.isFinite(bodyY) || !Double.isFinite(bodyZ)
                || !(directionLengthSquared > 1.0E-12D)) {
            directionX = 0.0D;
            directionY = 1.0D;
            directionZ = 0.0D;
        } else {
            double directionLength = Math.sqrt(directionLengthSquared);
            double directionInverse = 1.0D / directionLength;
            directionX = bodyX * directionInverse;
            directionY = bodyY * directionInverse;
            directionZ = bodyZ * directionInverse;
        }

        double northX;
        double northY;
        double northZ;
        if (celestialNorth == null) {
            northX = 0.0D;
            northY = 0.0D;
            northZ = 1.0D;
        } else {
            double rawNorthX = celestialNorth.x();
            double rawNorthY = celestialNorth.y();
            double rawNorthZ = celestialNorth.z();
            double northLengthSquared = rawNorthX * rawNorthX + rawNorthY * rawNorthY
                    + rawNorthZ * rawNorthZ;
            if (!Double.isFinite(rawNorthX) || !Double.isFinite(rawNorthY)
                    || !Double.isFinite(rawNorthZ) || !(northLengthSquared > 1.0E-12D)) {
                northX = 0.0D;
                northY = 0.0D;
                northZ = 1.0D;
            } else {
                double northLength = Math.sqrt(northLengthSquared);
                double northInverse = 1.0D / northLength;
                northX = rawNorthX * northInverse;
                northY = rawNorthY * northInverse;
                northZ = rawNorthZ * northInverse;
            }
        }

        double projection = directionX * northX + directionY * northY
                + directionZ * northZ;
        double upX = northX - directionX * projection;
        double upY = northY - directionY * projection;
        double upZ = northZ - directionZ * projection;
        double upLengthSquared = upX * upX + upY * upY + upZ * upZ;
        if (upLengthSquared < 1.0E-12D) {
            double absoluteX = Math.abs(directionX);
            double absoluteY = Math.abs(directionY);
            double absoluteZ = Math.abs(directionZ);
            double fallbackX;
            double fallbackY;
            double fallbackZ;
            if (absoluteX <= absoluteY && absoluteX <= absoluteZ) {
                fallbackX = 1.0D;
                fallbackY = 0.0D;
                fallbackZ = 0.0D;
            } else if (absoluteY <= absoluteZ) {
                fallbackX = 0.0D;
                fallbackY = 1.0D;
                fallbackZ = 0.0D;
            } else {
                fallbackX = 0.0D;
                fallbackY = 0.0D;
                fallbackZ = 1.0D;
            }
            projection = directionX * fallbackX + directionY * fallbackY
                    + directionZ * fallbackZ;
            upX = fallbackX - directionX * projection;
            upY = fallbackY - directionY * projection;
            upZ = fallbackZ - directionZ * projection;
            upLengthSquared = upX * upX + upY * upY + upZ * upZ;
        }
        if (!Double.isFinite(upX) || !Double.isFinite(upY) || !Double.isFinite(upZ)
                || !(upLengthSquared > 1.0E-12D)) {
            upX = 0.0D;
            upY = 0.0D;
            upZ = 1.0D;
        } else {
            double upLength = Math.sqrt(upLengthSquared);
            double upInverse = 1.0D / upLength;
            upX *= upInverse;
            upY *= upInverse;
            upZ *= upInverse;
        }

        double rightX = upY * directionZ - upZ * directionY;
        double rightY = upZ * directionX - upX * directionZ;
        double rightZ = upX * directionY - upY * directionX;
        double rightLengthSquared = rightX * rightX + rightY * rightY + rightZ * rightZ;
        if (!Double.isFinite(rightX) || !Double.isFinite(rightY) || !Double.isFinite(rightZ)
                || !(rightLengthSquared > 1.0E-12D)) {
            rightX = 1.0D;
            rightY = 0.0D;
            rightZ = 0.0D;
        } else {
            double rightLength = Math.sqrt(rightLengthSquared);
            double rightInverse = 1.0D / rightLength;
            rightX *= rightInverse;
            rightY *= rightInverse;
            rightZ *= rightInverse;
        }

        double correctedUpX = directionY * rightZ - directionZ * rightY;
        double correctedUpY = directionZ * rightX - directionX * rightZ;
        double correctedUpZ = directionX * rightY - directionY * rightX;
        double correctedLengthSquared = correctedUpX * correctedUpX
                + correctedUpY * correctedUpY + correctedUpZ * correctedUpZ;
        if (!Double.isFinite(correctedUpX) || !Double.isFinite(correctedUpY)
                || !Double.isFinite(correctedUpZ)
                || !(correctedLengthSquared > 1.0E-12D)) {
            correctedUpX = upX;
            correctedUpY = upY;
            correctedUpZ = upZ;
        } else {
            double correctedLength = Math.sqrt(correctedLengthSquared);
            double correctedInverse = 1.0D / correctedLength;
            correctedUpX *= correctedInverse;
            correctedUpY *= correctedInverse;
            correctedUpZ *= correctedInverse;
        }
        scratch.basisRightX = rightX;
        scratch.basisRightY = rightY;
        scratch.basisRightZ = rightZ;
        scratch.basisUpX = correctedUpX;
        scratch.basisUpY = correctedUpY;
        scratch.basisUpZ = correctedUpZ;
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
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(shadowHalfTangent)) {
            return AlignedSquare.NONE;
        }
        double firstLengthSquared = finiteLengthSquared(firstDirection);
        if (!(firstLengthSquared > 1.0E-12D)) {
            return AlignedSquare.NONE;
        }
        double shadowLengthSquared = finiteLengthSquared(shadowDirection);
        if (!(shadowLengthSquared > 1.0E-12D)) {
            return AlignedSquare.NONE;
        }
        double firstInverse = 1.0D / Math.sqrt(firstLengthSquared);
        double firstX = firstDirection.x() * firstInverse;
        double firstY = firstDirection.y() * firstInverse;
        double firstZ = firstDirection.z() * firstInverse;
        double shadowInverse = 1.0D / Math.sqrt(shadowLengthSquared);
        double shadowX = shadowDirection.x() * shadowInverse;
        double shadowY = shadowDirection.y() * shadowInverse;
        double shadowZ = shadowDirection.z() * shadowInverse;
        double forward = shadowX * firstX + shadowY * firstY + shadowZ * firstZ;
        if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
            return AlignedSquare.NONE;
        }
        Scratch scratch = SCRATCH.get();
        prepareStableBasis(scratch, firstX, firstY, firstZ, celestialNorth);
        double centerScale = forward * firstHalfTangent;
        double centerX = (shadowX * scratch.basisRightX + shadowY * scratch.basisRightY
                + shadowZ * scratch.basisRightZ) / centerScale;
        double centerY = (shadowX * scratch.basisUpX + shadowY * scratch.basisUpY
                + shadowZ * scratch.basisUpZ) / centerScale;
        double radius = shadowHalfTangent / firstHalfTangent;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !positiveFinite(radius)) {
            return AlignedSquare.NONE;
        }
        return new AlignedSquare(centerX, centerY, radius, true);
    }

    /**
     * Exact allocation-free equivalent of passing {@code shadowDirection.negated()} to
     * {@link #alignedSquareProjection}. Lunar-eclipse scans use the anti-solar direction only for
     * this projection, so materializing the intermediate vector cannot affect the public result.
     */
    static AlignedSquare alignedSquareProjectionNegatedShadow(
            CelestialVector firstDirection, CelestialVector shadowDirection,
            CelestialVector celestialNorth, double firstHalfTangent,
            double shadowHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(shadowHalfTangent)) {
            return AlignedSquare.NONE;
        }
        double firstLengthSquared = finiteLengthSquared(firstDirection);
        if (!(firstLengthSquared > 1.0E-12D)) {
            return AlignedSquare.NONE;
        }
        if (shadowDirection == null) {
            return AlignedSquare.NONE;
        }
        double shadowInputX = -shadowDirection.x();
        double shadowInputY = -shadowDirection.y();
        double shadowInputZ = -shadowDirection.z();
        double shadowLengthSquared = Double.isFinite(shadowInputX)
                && Double.isFinite(shadowInputY) && Double.isFinite(shadowInputZ)
                ? shadowInputX * shadowInputX + shadowInputY * shadowInputY
                + shadowInputZ * shadowInputZ : Double.NaN;
        if (!(shadowLengthSquared > 1.0E-12D)) {
            return AlignedSquare.NONE;
        }
        double firstInverse = 1.0D / Math.sqrt(firstLengthSquared);
        double firstX = firstDirection.x() * firstInverse;
        double firstY = firstDirection.y() * firstInverse;
        double firstZ = firstDirection.z() * firstInverse;
        double shadowInverse = 1.0D / Math.sqrt(shadowLengthSquared);
        double shadowX = shadowInputX * shadowInverse;
        double shadowY = shadowInputY * shadowInverse;
        double shadowZ = shadowInputZ * shadowInverse;
        double forward = shadowX * firstX + shadowY * firstY + shadowZ * firstZ;
        if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
            return AlignedSquare.NONE;
        }
        Scratch scratch = SCRATCH.get();
        prepareStableBasis(scratch, firstX, firstY, firstZ, celestialNorth);
        double centerScale = forward * firstHalfTangent;
        double centerX = (shadowX * scratch.basisRightX + shadowY * scratch.basisRightY
                + shadowZ * scratch.basisRightZ) / centerScale;
        double centerY = (shadowX * scratch.basisUpX + shadowY * scratch.basisUpY
                + shadowZ * scratch.basisUpZ) / centerScale;
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
        return alignedSquareCoverage(square.centerX(), square.centerY(), square.radius());
    }

    /** Same axis-aligned overlap formula without requiring a short-lived record wrapper. */
    static double alignedSquareCoverage(double centerX, double centerY, double radius) {
        double xOverlap = Math.max(0.0D, Math.min(1.0D, centerX + radius)
                - Math.max(-1.0D, centerX - radius));
        double yOverlap = Math.max(0.0D, Math.min(1.0D, centerY + radius)
                - Math.max(-1.0D, centerY - radius));
        return clamp(xOverlap * yOverlap / 4.0D, 0.0D, 1.0D);
    }

    public static Basis stableBasis(CelestialVector bodyDirection, CelestialVector celestialNorth) {
        CelestialVector direction = normalizedOrFallback(bodyDirection, Y_AXIS);
        CelestialVector north = normalizedOrFallback(celestialNorth, Z_AXIS);
        double projection = direction.x() * north.x() + direction.y() * north.y()
                + direction.z() * north.z();
        double upX = north.x() - direction.x() * projection;
        double upY = north.y() - direction.y() * projection;
        double upZ = north.z() - direction.z() * projection;
        double upLengthSquared = upX * upX + upY * upY + upZ * upZ;
        if (upLengthSquared < 1.0E-12D) {
            CelestialVector fallback = leastAlignedAxis(direction);
            projection = direction.x() * fallback.x() + direction.y() * fallback.y()
                    + direction.z() * fallback.z();
            upX = fallback.x() - direction.x() * projection;
            upY = fallback.y() - direction.y() * projection;
            upZ = fallback.z() - direction.z() * projection;
            upLengthSquared = upX * upX + upY * upY + upZ * upZ;
        }
        CelestialVector up = normalizedOrFallback(upX, upY, upZ, upLengthSquared, Z_AXIS);
        double rightX = up.y() * direction.z() - up.z() * direction.y();
        double rightY = up.z() * direction.x() - up.x() * direction.z();
        double rightZ = up.x() * direction.y() - up.y() * direction.x();
        CelestialVector right = normalizedOrFallback(rightX, rightY, rightZ, X_AXIS);
        double correctedUpX = direction.y() * right.z() - direction.z() * right.y();
        double correctedUpY = direction.z() * right.x() - direction.x() * right.z();
        double correctedUpZ = direction.x() * right.y() - direction.y() * right.x();
        return new Basis(right, normalizedOrFallback(correctedUpX, correctedUpY,
                correctedUpZ, up));
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
        for (int index = 0; index < count - 1; index++) {
            int next = index + 1;
            twiceArea += x[index] * y[next] - y[index] * x[next];
        }
        twiceArea += x[count - 1] * y[0] - y[count - 1] * x[0];
        return Math.abs(twiceArea) * 0.5D;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static double finiteLengthSquared(CelestialVector vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z()) ? vector.lengthSquared() : Double.NaN;
    }

    private static CelestialVector normalized(CelestialVector vector, double lengthSquared) {
        return vector.scale(1.0D / Math.sqrt(lengthSquared));
    }

    private static CelestialVector normalizedOrFallback(CelestialVector vector,
                                                        CelestialVector fallback) {
        return vector == null ? fallback
                : normalizedOrFallback(vector.x(), vector.y(), vector.z(), fallback);
    }

    private static CelestialVector normalizedOrFallback(double x, double y, double z,
                                                        CelestialVector fallback) {
        double lengthSquared = x * x + y * y + z * z;
        return normalizedOrFallback(x, y, z, lengthSquared, fallback);
    }

    private static CelestialVector normalizedOrFallback(double x, double y, double z,
                                                        double lengthSquared,
                                                        CelestialVector fallback) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !(lengthSquared > 1.0E-12D)) {
            return fallback;
        }
        double length = Math.sqrt(lengthSquared);
        double inverse = 1.0D / length;
        return new CelestialVector(x * inverse, y * inverse, z * inverse);
    }

    private static CelestialVector leastAlignedAxis(CelestialVector direction) {
        double x = Math.abs(direction.x());
        double y = Math.abs(direction.y());
        double z = Math.abs(direction.z());
        if (x <= y && x <= z) {
            return X_AXIS;
        }
        return y <= z ? Y_AXIS : Z_AXIS;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Scratch {
        private final double[] x1 = new double[12];
        private final double[] y1 = new double[12];
        private final double[] x2 = new double[12];
        private final double[] y2 = new double[12];
        private double basisRightX;
        private double basisRightY;
        private double basisRightZ;
        private double basisUpX;
        private double basisUpY;
        private double basisUpZ;
    }

    public record Basis(CelestialVector right, CelestialVector up) {
    }

    record PreparedSquare(CelestialVector direction, CelestialVector celestialNorth,
                          Basis basis, boolean valid) {
        private static final PreparedSquare NONE = new PreparedSquare(
                CelestialVector.ZERO, CelestialVector.ZERO,
                new Basis(CelestialVector.ZERO, CelestialVector.ZERO), false);
    }

    public record AlignedSquare(double centerX, double centerY, double radius, boolean valid) {
        public static final AlignedSquare NONE = new AlignedSquare(0.0D, 0.0D, 0.0D, false);
    }
}
