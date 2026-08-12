package first.wildfires.space.celestial;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable validated registry generation used to invalidate reload-sensitive space caches. */
public record CelestialRegistrySnapshot(
        long generation,
        CelestialBindingValidator.Report validation,
        Set<ResourceLocation> removedDefinitions) {

    public CelestialRegistrySnapshot {
        if (generation < 0L) {
            throw new IllegalArgumentException("Registry generation must be non-negative");
        }
        Objects.requireNonNull(validation, "validation");
        removedDefinitions = Set.copyOf(new LinkedHashSet<>(
                Objects.requireNonNull(removedDefinitions, "removedDefinitions")));
    }

    public static CelestialRegistrySnapshot empty() {
        return new CelestialRegistrySnapshot(0L,
                new CelestialBindingValidator.Report(Map.of(), Map.of()), Set.of());
    }

    public static CelestialRegistrySnapshot reload(
            CelestialRegistrySnapshot previous,
            long generation,
            Map<ResourceLocation, CelestialDefinition> definitions,
            Set<ResourceLocation> existingDimensions,
            Predicate<ResourceLocation> resourceExists) {
        Objects.requireNonNull(previous, "previous");
        if (generation <= previous.generation) {
            throw new IllegalArgumentException("Reload generation must advance: "
                    + generation + " <= " + previous.generation);
        }
        CelestialBindingValidator.Report validation = CelestialBindingValidator.validate(
                definitions, existingDimensions, resourceExists);
        Set<ResourceLocation> removed = new LinkedHashSet<>(previous.validation.definitions().keySet());
        removed.removeAll(validation.definitions().keySet());
        return new CelestialRegistrySnapshot(generation, validation, removed);
    }

    public boolean matchesGeneration(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    public Lookup lookup(long expectedGeneration, ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (!matchesGeneration(expectedGeneration)) {
            return new Lookup(LookupStatus.STALE_GENERATION, Optional.empty());
        }
        Optional<CelestialBindingValidator.ResolvedDefinition> resolved = validation.get(id);
        if (resolved.isPresent()) {
            return new Lookup(LookupStatus.PRESENT, resolved);
        }
        return new Lookup(removedDefinitions.contains(id) ? LookupStatus.REMOVED : LookupStatus.MISSING,
                Optional.empty());
    }

    public enum LookupStatus {
        PRESENT,
        REMOVED,
        MISSING,
        STALE_GENERATION
    }

    public record Lookup(
            LookupStatus status,
            Optional<CelestialBindingValidator.ResolvedDefinition> definition) {

        public Lookup {
            Objects.requireNonNull(status, "status");
            definition = Objects.requireNonNull(definition, "definition");
            if ((status == LookupStatus.PRESENT) != definition.isPresent()) {
                throw new IllegalArgumentException("Only a present lookup may carry a definition");
            }
        }
    }
}
