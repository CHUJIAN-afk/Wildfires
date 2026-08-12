package first.wildfires.space.station;

import java.util.Objects;
import java.util.Set;

/** Deterministic square-spiral allocator. Retired regions remain occupied until explicitly cleared. */
public final class StationRegionAllocator {

    private static final long MAX_GRID_RADIUS = (SpaceConstants.MAX_WORLD_COORDINATE
            - SpaceConstants.REGION_HALF_SIZE + 1L) / SpaceConstants.REGION_SIZE;
    private static final long MAX_SPIRAL_INDEX = square(2L * MAX_GRID_RADIUS + 1L) - 1L;

    private StationRegionAllocator() {
    }

    public static Allocation allocate(Set<StationRegion> occupied, Set<StationRegion> retired,
                                      long nextOrdinal) {
        Objects.requireNonNull(occupied, "occupied");
        Objects.requireNonNull(retired, "retired");
        long ordinal = Math.max(1L, nextOrdinal);
        while (ordinal <= MAX_SPIRAL_INDEX) {
            StationRegion candidate = regionAt(ordinal);
            long following = Math.incrementExact(ordinal);
            if (!candidate.reserved() && !occupied.contains(candidate) && !retired.contains(candidate)) {
                return new Allocation(candidate, following);
            }
            ordinal = following;
        }
        throw new IllegalStateException("No station region remains inside the Minecraft world boundary");
    }

    /** Index zero is the reserved management region; ordinary allocation starts at index one. */
    public static StationRegion regionAt(long index) {
        if (index < 0L || index > MAX_SPIRAL_INDEX) {
            throw new IllegalArgumentException("Station region spiral index outside supported range: " + index);
        }
        if (index == 0L) {
            return new StationRegion(0, 0);
        }

        long ring = (long) Math.ceil((Math.sqrt(index + 1.0D) - 1.0D) / 2.0D);
        while (square(2L * ring + 1L) <= index) {
            ring++;
        }
        while (ring > 0L && square(2L * ring - 1L) > index) {
            ring--;
        }
        long previousSquare = square(2L * ring - 1L);
        long offset = index - previousSquare;
        long side = 2L * ring;
        long x;
        long z;
        if (offset < side) {
            x = ring;
            z = -ring + 1L + offset;
        } else if ((offset -= side) < side) {
            x = ring - 1L - offset;
            z = ring;
        } else if ((offset -= side) < side) {
            x = -ring;
            z = ring - 1L - offset;
        } else {
            offset -= side;
            x = -ring + 1L + offset;
            z = -ring;
        }
        return new StationRegion(Math.toIntExact(x), Math.toIntExact(z));
    }

    private static long square(long value) {
        return Math.multiplyExact(value, value);
    }

    public record Allocation(StationRegion region, long nextOrdinal) {
        public Allocation {
            Objects.requireNonNull(region, "region");
            if (region.reserved() || nextOrdinal < 2L) {
                throw new IllegalArgumentException("Invalid ordinary station allocation");
            }
        }
    }
}
