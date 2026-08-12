package first.wildfires.space.celestial;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resolves station context by observer position without ever caching one station for a whole level. */
public final class ObservationContextResolver {

    private static final ClientBridge EMPTY_CLIENT = new ClientBridge() {
        @Override
        public Optional<ObservationContext> current() {
            return Optional.empty();
        }

        @Override
        public UpdateResult accept(ObservationContext context) {
            return UpdateResult.IGNORED;
        }

        @Override
        public UpdateResult remove(UUID stationId, long revision) {
            return UpdateResult.IGNORED;
        }

        @Override
        public void clear() {
        }
    };

    private static volatile ClientBridge clientBridge = EMPTY_CLIENT;

    private ObservationContextResolver() {
    }

    public static Optional<ObservationContext> resolve(Level level, Vec3 observer) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(observer, "observer");
        if (level.dimension() != SpaceDimensions.ORBIT) {
            return Optional.empty();
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!Double.isFinite(observer.x) || !Double.isFinite(observer.z)
                    || observer.x < Integer.MIN_VALUE || observer.x > Integer.MAX_VALUE
                    || observer.z < Integer.MIN_VALUE || observer.z > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            return SpaceSavedData.get(serverLevel.getServer())
                    .stationAt((int) Math.floor(observer.x), (int) Math.floor(observer.z))
                    .map(station -> ObservationContext.from(station,
                            CelestialRegistryRuntime.current().generation()))
                    .filter(context -> context.contains(observer.x, observer.z));
        }
        return clientBridge.current().filter(context -> context.contains(observer.x, observer.z));
    }

    public static synchronized void installClientBridge(ClientBridge bridge) {
        clientBridge = Objects.requireNonNull(bridge, "bridge");
    }

    public static UpdateResult acceptClient(ObservationContext context) {
        return clientBridge.accept(Objects.requireNonNull(context, "context"));
    }

    public static UpdateResult removeClient(UUID stationId, long revision) {
        if (revision < 0L) {
            throw new IllegalArgumentException("Removed station revision must be non-negative");
        }
        return clientBridge.remove(Objects.requireNonNull(stationId, "stationId"), revision);
    }

    public static void clearClient() {
        clientBridge.clear();
    }

    public enum UpdateResult {
        ACCEPTED,
        IDEMPOTENT,
        STALE,
        CONFLICT,
        REMOVED,
        IGNORED
    }

    /** Installed only by client bootstrap; common and dedicated-server code contains no client types. */
    public interface ClientBridge {
        Optional<ObservationContext> current();

        UpdateResult accept(ObservationContext context);

        UpdateResult remove(UUID stationId, long revision);

        void clear();
    }
}
