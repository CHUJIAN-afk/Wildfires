package first.wildfires.space.route;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Minimal client intent. Every world-sensitive fact is revalidated by the server. */
public record StationTravelRequest(BlockPos computerPos, UUID stationId, long expectedRevision,
                                   ResourceLocation routeId) {

    public StationTravelRequest {
        computerPos = Objects.requireNonNull(computerPos, "computerPos").immutable();
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(routeId, "routeId");
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Expected station revision must be non-negative");
        }
    }
}
