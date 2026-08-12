package first.wildfires.space.celestial;

import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-authored station snapshot used for one observer in the shared orbit level. */
public record ObservationContext(UUID stationId,
                                 long stationRevision,
                                 StationRegion region,
                                 ResourceLocation currentBody,
                                 StationStatus status,
                                 Optional<ObservationJourney> journey,
                                 long celestialRegistryGeneration) {

    public ObservationContext {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(currentBody, "currentBody");
        Objects.requireNonNull(status, "status");
        journey = Objects.requireNonNull(journey, "journey");
        if (stationRevision < 0L || celestialRegistryGeneration < 0L) {
            throw new IllegalArgumentException("Observation revisions and generations must be non-negative");
        }
    }

    public static ObservationContext from(StationRecord station, long registryGeneration) {
        Objects.requireNonNull(station, "station");
        return new ObservationContext(station.stationId(), station.revision(), station.region(),
                station.currentBody(), station.status(), station.journey().map(ObservationJourney::from),
                registryGeneration);
    }

    public boolean contains(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && x >= region.minX() && x < (double) region.maxX() + 1.0D
                && z >= region.minZ() && z < (double) region.maxZ() + 1.0D;
    }
}
