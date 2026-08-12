package first.wildfires.client.space;

import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;

import java.util.Optional;
import java.util.UUID;

/** Minimal client-only holder for the latest validated station observation packet. */
public final class SpaceClientState {

    private static ObservationContext current;
    private static boolean installed;

    private SpaceClientState() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ObservationContextResolver.installClientBridge(new Bridge());
    }

    public static synchronized Optional<ObservationContext> current() {
        return Optional.ofNullable(current);
    }

    public static synchronized Object cacheIdentity() {
        return current;
    }

    public static synchronized void clear() {
        current = null;
    }

    private static synchronized ObservationContextResolver.UpdateResult accept(ObservationContext incoming) {
        if (current == null || !current.stationId().equals(incoming.stationId())) {
            current = incoming;
            return ObservationContextResolver.UpdateResult.ACCEPTED;
        }
        if (incoming.stationRevision() < current.stationRevision()
                || incoming.celestialRegistryGeneration() < current.celestialRegistryGeneration()) {
            return ObservationContextResolver.UpdateResult.STALE;
        }
        if (incoming.stationRevision() == current.stationRevision()) {
            if (incoming.celestialRegistryGeneration() == current.celestialRegistryGeneration()) {
                return incoming.equals(current)
                        ? ObservationContextResolver.UpdateResult.IDEMPOTENT
                        : ObservationContextResolver.UpdateResult.CONFLICT;
            }
            ObservationContext sameGeneration = new ObservationContext(incoming.stationId(),
                    incoming.stationRevision(), incoming.region(), incoming.currentBody(), incoming.status(),
                    incoming.journey(), current.celestialRegistryGeneration());
            if (!sameGeneration.equals(current)) {
                return ObservationContextResolver.UpdateResult.CONFLICT;
            }
        }
        current = incoming;
        return ObservationContextResolver.UpdateResult.ACCEPTED;
    }

    private static synchronized ObservationContextResolver.UpdateResult remove(UUID stationId, long revision) {
        if (current == null || !current.stationId().equals(stationId)) {
            return ObservationContextResolver.UpdateResult.IGNORED;
        }
        if (revision < current.stationRevision()) {
            return ObservationContextResolver.UpdateResult.STALE;
        }
        current = null;
        return ObservationContextResolver.UpdateResult.REMOVED;
    }

    private static final class Bridge implements ObservationContextResolver.ClientBridge {
        @Override
        public Optional<ObservationContext> current() {
            return SpaceClientState.current();
        }

        @Override
        public ObservationContextResolver.UpdateResult accept(ObservationContext context) {
            return SpaceClientState.accept(context);
        }

        @Override
        public ObservationContextResolver.UpdateResult remove(UUID stationId, long revision) {
            return SpaceClientState.remove(stationId, revision);
        }

        @Override
        public void clear() {
            SpaceClientState.clear();
        }
    }
}
