package first.wildfires.space.celestial;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/** Resolves a declared celestial surface against the server's actually loaded levels. */
public final class CelestialSurfaceBindingResolver {

    private CelestialSurfaceBindingResolver() {
    }

    public static Optional<LoadedSurfaceBinding> resolve(MinecraftServer server,
                                                          ResourceLocation bodyId) {
        return resolve(server, CelestialRegistryRuntime.current(), bodyId);
    }

    /**
     * The validated snapshot records reload-time diagnostics, but it is not the final authority for
     * level availability. During an integrated-server {@code /reload}, Forge can expose a dynamic
     * registry view whose dimension keys are temporarily empty even though the ServerLevel remains
     * loaded. Landing therefore requires both the data-pack declaration and the live server level.
     */
    public static Optional<LoadedSurfaceBinding> resolve(MinecraftServer server,
                                                          CelestialRegistrySnapshot snapshot,
                                                          ResourceLocation bodyId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(bodyId, "bodyId");
        CelestialBindingValidator.ResolvedDefinition resolved = snapshot
                .lookup(snapshot.generation(), bodyId).definition().orElse(null);
        if (resolved == null || !resolved.definition().requestsLanding()) {
            return Optional.empty();
        }
        ResourceLocation dimension = resolved.definition().surfaceDimension().orElseThrow();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = server.getLevel(key);
        return level == null ? Optional.empty()
                : Optional.of(new LoadedSurfaceBinding(dimension, level));
    }

    public record LoadedSurfaceBinding(ResourceLocation dimension, ServerLevel level) {
        public LoadedSurfaceBinding {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(level, "level");
            if (!level.dimension().location().equals(dimension)) {
                throw new IllegalArgumentException("Loaded surface dimension does not match its level");
            }
        }
    }
}
