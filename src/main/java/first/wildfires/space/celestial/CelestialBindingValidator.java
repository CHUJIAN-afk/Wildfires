package first.wildfires.space.celestial;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Resolves parent, existing-dimension and render-resource availability without creating worlds. */
public final class CelestialBindingValidator {

    public static final int MAX_DEFINITIONS = 4096;
    public static final int MAX_ID_LENGTH = 256;

    private CelestialBindingValidator() {
    }

    public static Report validate(Map<ResourceLocation, CelestialDefinition> definitions,
                                  Set<ResourceLocation> existingDimensions,
                                  Predicate<ResourceLocation> resourceExists) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(existingDimensions, "existingDimensions");
        Objects.requireNonNull(resourceExists, "resourceExists");
        if (definitions.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Too many celestial definitions: " + definitions.size());
        }

        List<Map.Entry<ResourceLocation, CelestialDefinition>> ordered = definitions.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();
        Map<ResourceLocation, List<Issue>> issues = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CelestialDefinition> entry : ordered) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey(), "celestial id");
            CelestialDefinition definition = Objects.requireNonNull(entry.getValue(), "celestial definition");
            List<Issue> bodyIssues = new ArrayList<>();
            issues.put(id, bodyIssues);
            if (id.toString().length() > MAX_ID_LENGTH) {
                bodyIssues.add(error(IssueCode.ID_TOO_LONG, "Celestial id exceeds " + MAX_ID_LENGTH + ": " + id));
            }
            definition.parent().ifPresent(parent -> {
                if (parent.equals(id)) {
                    bodyIssues.add(error(IssueCode.SELF_PARENT, "Celestial cannot parent itself: " + id));
                } else if (!definitions.containsKey(parent)) {
                    bodyIssues.add(error(IssueCode.MISSING_PARENT,
                            "Missing parent " + parent + " for " + id));
                }
            });
        }
        markParentCycles(definitions, issues);

        Map<ResourceLocation, ResolvedDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CelestialDefinition> entry : ordered) {
            ResourceLocation id = entry.getKey();
            CelestialDefinition definition = entry.getValue();
            List<Issue> bodyIssues = issues.get(id);

            Optional<ResourceLocation> resolvedSurface = Optional.empty();
            boolean landingAvailable = false;
            if (definition.landable()) {
                if (definition.surfaceDimension().isEmpty()) {
                    bodyIssues.add(warning(IssueCode.MISSING_SURFACE_BINDING,
                            "Landing requested without a surface dimension for " + id));
                } else {
                    ResourceLocation surface = definition.surfaceDimension().orElseThrow();
                    if (existingDimensions.contains(surface)) {
                        resolvedSurface = Optional.of(surface);
                        landingAvailable = true;
                    } else {
                        bodyIssues.add(warning(IssueCode.UNAVAILABLE_SURFACE_DIMENSION,
                                "Surface dimension is not present on this server for " + id + ": " + surface));
                    }
                }
            }

            VisualSource surfaceSource = resolveVisualSource(definition.visual().surfaceAtlas(),
                    definition.visual().proceduralSurface(), resourceExists, bodyIssues,
                    IssueCode.MISSING_SURFACE_RESOURCE, "surface atlas", id);
            VisualSource cloudSource = VisualSource.NONE;
            if (definition.visual().clouds().enabled()) {
                cloudSource = resolveVisualSource(definition.visual().clouds().texture(),
                        definition.visual().clouds().procedural(), resourceExists, bodyIssues,
                        IssueCode.MISSING_CLOUD_RESOURCE, "cloud texture", id);
            }
            boolean routeAvailable = bodyIssues.stream().noneMatch(issue -> issue.severity == Severity.ERROR);
            resolved.put(id, new ResolvedDefinition(id, definition, resolvedSurface, landingAvailable,
                    routeAvailable, surfaceSource, cloudSource, bodyIssues));
        }
        return new Report(definitions, resolved);
    }

    private static VisualSource resolveVisualSource(Optional<ResourceLocation> texture, boolean procedural,
                                                    Predicate<ResourceLocation> resourceExists,
                                                    List<Issue> issues, IssueCode missingCode,
                                                    String description, ResourceLocation bodyId) {
        if (texture.isPresent()) {
            ResourceLocation resource = texture.orElseThrow();
            if (resourceExists.test(resource)) {
                return VisualSource.TEXTURE;
            }
            if (procedural) {
                issues.add(warning(missingCode, "Missing " + description + " for " + bodyId
                        + "; using procedural fallback: " + resource));
                return VisualSource.PROCEDURAL;
            }
            issues.add(error(missingCode, "Missing " + description + " for " + bodyId + ": " + resource));
            return VisualSource.MISSING;
        }
        return procedural ? VisualSource.PROCEDURAL : VisualSource.NONE;
    }

    private static void markParentCycles(Map<ResourceLocation, CelestialDefinition> definitions,
                                         Map<ResourceLocation, List<Issue>> issues) {
        Set<ResourceLocation> alreadyChecked = new LinkedHashSet<>();
        for (ResourceLocation start : definitions.keySet()) {
            if (alreadyChecked.contains(start)) {
                continue;
            }
            List<ResourceLocation> path = new ArrayList<>();
            Map<ResourceLocation, Integer> positions = new HashMap<>();
            ResourceLocation current = start;
            while (current != null && definitions.containsKey(current) && !alreadyChecked.contains(current)) {
                Integer previousPosition = positions.putIfAbsent(current, path.size());
                if (previousPosition != null) {
                    for (int index = previousPosition; index < path.size(); index++) {
                        ResourceLocation cyclic = path.get(index);
                        issues.get(cyclic).add(error(IssueCode.PARENT_CYCLE,
                                "Celestial parent cycle contains " + cyclic));
                    }
                    break;
                }
                path.add(current);
                current = definitions.get(current).parent().orElse(null);
            }
            alreadyChecked.addAll(path);
        }
    }

    private static Issue error(IssueCode code, String message) {
        return new Issue(Severity.ERROR, code, message);
    }

    private static Issue warning(IssueCode code, String message) {
        return new Issue(Severity.WARNING, code, message);
    }

    public enum Severity {
        WARNING,
        ERROR
    }

    public enum IssueCode {
        ID_TOO_LONG,
        SELF_PARENT,
        MISSING_PARENT,
        PARENT_CYCLE,
        MISSING_SURFACE_BINDING,
        UNAVAILABLE_SURFACE_DIMENSION,
        MISSING_SURFACE_RESOURCE,
        MISSING_CLOUD_RESOURCE
    }

    public enum VisualSource {
        NONE,
        TEXTURE,
        PROCEDURAL,
        MISSING
    }

    public record Issue(Severity severity, IssueCode code, String message) {

        public Issue {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    public record ResolvedDefinition(
            ResourceLocation id,
            CelestialDefinition definition,
            Optional<ResourceLocation> surfaceDimension,
            boolean landingAvailable,
            boolean routeAvailable,
            VisualSource surfaceSource,
            VisualSource cloudSource,
            List<Issue> issues) {

        public ResolvedDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(definition, "definition");
            surfaceDimension = Objects.requireNonNull(surfaceDimension, "surfaceDimension");
            Objects.requireNonNull(surfaceSource, "surfaceSource");
            Objects.requireNonNull(cloudSource, "cloudSource");
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            if (landingAvailable && surfaceDimension.isEmpty()) {
                throw new IllegalArgumentException("Landing cannot be available without a resolved surface dimension");
            }
        }
    }

    public record Report(
            Map<ResourceLocation, CelestialDefinition> definitions,
            Map<ResourceLocation, ResolvedDefinition> resolved) {

        public Report {
            definitions = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(definitions, "definitions")));
            resolved = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(resolved, "resolved")));
        }

        public Optional<ResolvedDefinition> get(ResourceLocation id) {
            return Optional.ofNullable(resolved.get(id));
        }

        public Collection<Issue> issues() {
            return resolved.values().stream().flatMap(value -> value.issues().stream()).toList();
        }

        public boolean hasErrors() {
            return issues().stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }
}
