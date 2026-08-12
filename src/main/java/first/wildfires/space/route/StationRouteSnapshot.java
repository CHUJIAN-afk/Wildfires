package first.wildfires.space.route;

import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable validated route generation used by menus, travel transactions and journey ticks. */
public record StationRouteSnapshot(long generation,
                                   long celestialGeneration,
                                   Map<ResourceLocation, StationRouteDefinition> definitions,
                                   Map<ResourceLocation, String> rejected) {

    public StationRouteSnapshot {
        if (generation < 0L || celestialGeneration < 0L) {
            throw new IllegalArgumentException("Route generations must be non-negative");
        }
        definitions = Map.copyOf(Objects.requireNonNull(definitions, "definitions"));
        rejected = Map.copyOf(Objects.requireNonNull(rejected, "rejected"));
    }

    public static StationRouteSnapshot empty() {
        return new StationRouteSnapshot(0L, 0L, Map.of(), Map.of());
    }

    public static StationRouteSnapshot validate(long generation,
                                                Map<ResourceLocation, StationRouteDefinition> source,
                                                CelestialRegistrySnapshot celestials) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(celestials, "celestials");
        Map<ResourceLocation, StationRouteDefinition> accepted = new LinkedHashMap<>();
        Map<ResourceLocation, String> rejected = new LinkedHashMap<>();
        source.forEach((entryId, route) -> {
            String reason = null;
            if (!entryId.equals(route.id())) {
                reason = "registry id does not match route id " + route.id();
            } else if (celestials.lookup(celestials.generation(), route.fromBody()).status()
                    != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
                reason = "origin celestial is unavailable: " + route.fromBody();
            } else if (celestials.lookup(celestials.generation(), route.toBody()).status()
                    != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
                reason = "target celestial is unavailable: " + route.toBody();
            }
            if (reason == null) {
                accepted.put(entryId, route);
            } else {
                rejected.put(entryId, reason);
            }
        });
        return new StationRouteSnapshot(generation, celestials.generation(), accepted, rejected);
    }

    public Optional<StationRouteDefinition> route(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public List<StationRouteDefinition> routesFrom(ResourceLocation body) {
        return definitions.values().stream().filter(StationRouteDefinition::enabled)
                .filter(route -> route.fromBody().equals(body))
                .sorted(Comparator.comparing(route -> route.id().toString())).toList();
    }
}
