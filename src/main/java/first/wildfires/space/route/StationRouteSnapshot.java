package first.wildfires.space.route;

import first.wildfires.space.celestial.CelestialBindingValidator;
import first.wildfires.space.celestial.CelestialKind;
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
            } else if (!isTravelBody(celestials, route.fromBody())) {
                reason = "origin celestial is not a non-stellar travel body: " + route.fromBody();
            } else if (!isTravelBody(celestials, route.toBody())) {
                reason = "target celestial is not a non-stellar travel body: " + route.toBody();
            }
            if (reason == null) {
                accepted.put(entryId, route);
            } else {
                rejected.put(entryId, reason);
            }
        });
        addFreeTransfers(accepted, celestials);
        return new StationRouteSnapshot(generation, celestials.generation(), accepted, rejected);
    }

    /**
     * Stable orbit is the transfer gate. Every validated non-star celestial becomes reachable,
     * while an explicit data-pack route remains authoritative for its directed endpoint pair.
     */
    private static void addFreeTransfers(Map<ResourceLocation, StationRouteDefinition> accepted,
                                         CelestialRegistrySnapshot celestials) {
        List<ResourceLocation> bodies = celestials.validation().resolved().values().stream()
                .filter(CelestialBindingValidator.ResolvedDefinition::routeAvailable)
                .filter(resolved -> resolved.definition().kind() != CelestialKind.STAR)
                .map(CelestialBindingValidator.ResolvedDefinition::id)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
        for (ResourceLocation origin : bodies) {
            for (ResourceLocation target : bodies) {
                if (origin.equals(target) || hasEnabledDirectedRoute(accepted, origin, target)) {
                    continue;
                }
                StationRouteDefinition route = StationRouteDefinition.freeTransfer(origin, target);
                accepted.putIfAbsent(route.id(), route);
            }
        }
    }

    private static boolean hasEnabledDirectedRoute(Map<ResourceLocation, StationRouteDefinition> routes,
                                                   ResourceLocation origin, ResourceLocation target) {
        return routes.values().stream().anyMatch(route -> route.enabled()
                && route.connects(origin, target));
    }

    /** A star may illuminate a route, but it is never a stable-orbit station endpoint. */
    public static boolean isTravelBody(CelestialRegistrySnapshot celestials, ResourceLocation id) {
        return celestials.lookup(celestials.generation(), id).definition()
                .filter(CelestialBindingValidator.ResolvedDefinition::routeAvailable)
                .map(CelestialBindingValidator.ResolvedDefinition::definition)
                .map(definition -> definition.kind() != CelestialKind.STAR)
                .orElse(false);
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
