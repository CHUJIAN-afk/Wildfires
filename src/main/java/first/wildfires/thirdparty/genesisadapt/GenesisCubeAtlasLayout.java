/*
 * Adapted from the VS: Genesis 3x2 planet cubemap contract.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Wildfires modifications: isolated the face/UV contract from VS2 and rendering dependencies,
 * added validated direction sampling for deterministic fallback texture generation.
 */
package first.wildfires.thirdparty.genesisadapt;

import java.util.List;
import java.util.Objects;

/** VS: Genesis face order: north, west, south / east, down, up. */
public final class GenesisCubeAtlasLayout {

    public static final int COLUMNS = 3;
    public static final int ROWS = 2;
    public static final List<Face> FACES = List.of(
            new Face(0, 0, 0, Axis.Z, -1, "north"),
            new Face(1, 1, 0, Axis.X, -1, "west"),
            new Face(2, 2, 0, Axis.Z, 1, "south"),
            new Face(3, 0, 1, Axis.X, 1, "east"),
            new Face(4, 1, 1, Axis.Y, -1, "down"),
            new Face(5, 2, 1, Axis.Y, 1, "up"));

    private GenesisCubeAtlasLayout() {
    }

    public static Face face(int index) {
        if (index < 0 || index >= FACES.size()) {
            throw new IllegalArgumentException("Genesis cube face index must be within 0..5: " + index);
        }
        return FACES.get(index);
    }

    public static Uv atlasUv(Face face, double localU, double localV) {
        Objects.requireNonNull(face, "face");
        requireUnit(localU, "localU");
        requireUnit(localV, "localV");
        return new Uv((face.column + localU) / COLUMNS, (face.row + localV) / ROWS);
    }

    /** Normalized direction using the same per-face orientation as Genesis PlanetRenderer. */
    public static Direction direction(Face face, double localU, double localV) {
        Objects.requireNonNull(face, "face");
        requireUnit(localU, "localU");
        requireUnit(localV, "localV");
        double horizontal = localU * 2.0D - 1.0D;
        double vertical = 1.0D - localV * 2.0D;
        double x;
        double y;
        double z;
        switch (face.index) {
            case 0 -> { x = -horizontal; y = vertical; z = -1.0D; }
            case 1 -> { x = -1.0D; y = vertical; z = horizontal; }
            case 2 -> { x = horizontal; y = vertical; z = 1.0D; }
            case 3 -> { x = 1.0D; y = vertical; z = -horizontal; }
            case 4 -> { x = horizontal; y = -1.0D; z = vertical; }
            case 5 -> { x = horizontal; y = 1.0D; z = -vertical; }
            default -> throw new IllegalStateException("Unexpected Genesis cube face " + face.index);
        }
        double inverseLength = 1.0D / Math.sqrt(x * x + y * y + z * z);
        return new Direction(x * inverseLength, y * inverseLength, z * inverseLength);
    }

    public static Position position(Face face, double localU, double localV) {
        Direction direction = direction(face, localU, localV);
        double largest = Math.max(Math.abs(direction.x),
                Math.max(Math.abs(direction.y), Math.abs(direction.z)));
        return new Position(direction.x / largest, direction.y / largest, direction.z / largest);
    }

    public static Uv equirectangularUv(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        double length = direction.length();
        if (!(length > 1.0E-12D)) {
            throw new IllegalArgumentException("Equirectangular direction must be non-zero");
        }
        double x = direction.x / length;
        double y = direction.y / length;
        double z = direction.z / length;
        double u = Math.atan2(z, x) / (Math.PI * 2.0D) + 0.5D;
        u -= Math.floor(u);
        double v = Math.max(0.0D, Math.min(1.0D, 0.5D - Math.asin(y) / Math.PI));
        return new Uv(u, v);
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be finite and within 0..1");
        }
    }

    public enum Axis { X, Y, Z }

    public record Face(int index, int column, int row, Axis axis, int sign, String name) {
        public Face {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(name, "name");
            if (index < 0 || index > 5 || column < 0 || column >= COLUMNS
                    || row < 0 || row >= ROWS || (sign != -1 && sign != 1)) {
                throw new IllegalArgumentException("Invalid Genesis cube atlas face");
            }
        }

        public Direction normal() {
            return switch (axis) {
                case X -> new Direction(sign, 0.0D, 0.0D);
                case Y -> new Direction(0.0D, sign, 0.0D);
                case Z -> new Direction(0.0D, 0.0D, sign);
            };
        }
    }

    public record Uv(double u, double v) {
    }

    public record Direction(double x, double y, double z) {
        public Direction {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Cube direction must be finite");
            }
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }

    public record Position(double x, double y, double z) {
    }
}
