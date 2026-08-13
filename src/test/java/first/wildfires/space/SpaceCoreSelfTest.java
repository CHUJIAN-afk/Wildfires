package first.wildfires.space;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialBindingValidator;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.environment.CelestialEnvironment;
import first.wildfires.space.station.StationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.station.StationJourneyService;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Plain-Java regression checks for the fixed-station journey domain core. */
public final class SpaceCoreSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MARS = id("mars");
    private static final ResourceLocation ROUTE_ID = id("earth_to_mars");
    private static final UUID JOURNEY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REQUESTER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private SpaceCoreSelfTest() {
    }

    public static void main(String[] args) {
        phaseIdsAreStableAndNeverUseOrdinals();
        routesAreDirectedAndStructurallyValidated();
        journeyValuesRejectImpossibleState();
        phaseProgressIsBounded();
        journeySnapshotsRoundTripAndRejectUnknownContracts();
        travelAdvancesAtExactBoundaries();
        oneAdvanceCanCrossEveryFinishedPhase();
        zeroDurationPhasesCompleteDeterministically();
        repeatedAndRewoundCallsAreIdempotent();
        restartedSnapshotsResumeFromServerGameTime();
        calendarRateNeverEntersJourneyAdvancement();
        celestialDefinitionCodecRoundTripsSquarePlanetAndOrbitalClouds();
        celestialContractsRejectUnsafeEnvironmentAndShells();
        missingSurfaceBindingStaysVisualButCannotRequestLanding();
        builtInCelestialsLoadAndOnlyEarthBindsOverworld();
        bindingValidationNeverCreatesOrGuessesDimensions();
        parentAndResourceFailuresAreExplicit();
        registryReloadInvalidatesOldGenerationsAndReportsRemovedBodies();
        System.out.println("SpaceCoreSelfTest: all checks passed");
    }

    private static void phaseIdsAreStableAndNeverUseOrdinals() {
        Map<StationJourneyPhase, String> expected = Map.of(
                StationJourneyPhase.ORBITING, "orbiting",
                StationJourneyPhase.DEPARTING, "departing",
                StationJourneyPhase.CRUISE, "cruise",
                StationJourneyPhase.JUMP_ACCELERATING, "jump_accelerating",
                StationJourneyPhase.JUMP_CRUISING, "jump_cruising",
                StationJourneyPhase.JUMP_DECELERATING, "jump_decelerating",
                StationJourneyPhase.ARRIVING, "arriving",
                StationJourneyPhase.FAULTED, "faulted");
        Set<String> uniqueIds = new HashSet<>();
        for (StationJourneyPhase phase : StationJourneyPhase.values()) {
            assertEquals(expected.get(phase), phase.id(), "stable phase id for " + phase.name());
            assertEquals(phase, StationJourneyPhase.fromId(phase.id()).orElseThrow(),
                    "phase id round-trip");
            assertTrue(uniqueIds.add(phase.id()), "duplicate phase id " + phase.id());
            assertFalse(phase.id().equals(Integer.toString(phase.ordinal())),
                    "phase id must not encode ordinal");
        }
        assertTrue(StationJourneyPhase.fromId("unknown").isEmpty(), "unknown phase must be rejected");
        assertTrue(StationJourneyPhase.fromId(null).isEmpty(), "null phase must be rejected");
    }

    private static void routesAreDirectedAndStructurallyValidated() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        assertTrue(route.connects(EARTH, MARS), "forward route");
        assertFalse(route.connects(MARS, EARTH), "route must remain directed");
        assertEquals(18L, route.totalDurationTicks(), "route total duration");
        assertEquals(5L, route.durationFor(StationJourneyPhase.DEPARTING), "departure duration");
        assertEquals(10L, route.durationFor(StationJourneyPhase.CRUISE), "cruise duration");
        assertEquals(3L, route.durationFor(StationJourneyPhase.ARRIVING), "arrival duration");

        assertThrows(IllegalArgumentException.class,
                () -> new StationRouteDefinition(ROUTE_ID, EARTH, EARTH, 1L, 1L, 1L, true),
                "route self-loop");
        assertThrows(IllegalArgumentException.class,
                () -> route(-1L, 0L, 0L, true), "negative route duration");
        assertThrows(IllegalArgumentException.class,
                () -> route(Long.MAX_VALUE, 1L, 0L, true), "overflowing route duration");
        assertThrows(IllegalArgumentException.class,
                () -> route.durationFor(StationJourneyPhase.ORBITING), "orbiting route duration");

        StationJourneyService.State orbiting = StationJourneyService.State.orbiting(EARTH, 0L);
        assertThrows(IllegalArgumentException.class,
                () -> StationJourneyService.start(orbiting, route(1L, 1L, 1L, false),
                        0L, JOURNEY_ID, REQUESTER_ID),
                "disabled route");
    }

    private static void journeyValuesRejectImpossibleState() {
        assertThrows(IllegalArgumentException.class,
                () -> journey(EARTH, EARTH, StationJourneyPhase.DEPARTING, 0L, 1L),
                "journey self-loop");
        assertThrows(IllegalArgumentException.class,
                () -> journey(EARTH, MARS, StationJourneyPhase.ORBITING, 0L, 1L),
                "ORBITING stored as active journey");
        assertThrows(IllegalArgumentException.class,
                () -> journey(EARTH, MARS, StationJourneyPhase.DEPARTING, -1L, 1L),
                "negative phase start");
        assertThrows(IllegalArgumentException.class,
                () -> journey(EARTH, MARS, StationJourneyPhase.DEPARTING, 0L, -1L),
                "negative phase duration");
        assertThrows(IllegalArgumentException.class,
                () -> journey(EARTH, MARS, StationJourneyPhase.DEPARTING, Long.MAX_VALUE, 1L),
                "overflowing phase end");
        assertThrows(IllegalArgumentException.class,
                () -> new StationJourneyService.State(MARS,
                        java.util.Optional.of(journey(EARTH, MARS, StationJourneyPhase.CRUISE, 0L, 1L)), 0L),
                "current body before commit");
        assertThrows(IllegalArgumentException.class,
                () -> StationJourneyService.State.orbiting(EARTH, -1L), "negative revision");
    }

    private static void phaseProgressIsBounded() {
        StationJourney journey = journey(EARTH, MARS, StationJourneyPhase.DEPARTING, 100L, 20L);
        assertClose(0.0D, journey.progressAt(50L), "progress before phase");
        assertClose(0.0D, journey.progressAt(100L), "progress at phase start");
        assertClose(0.5D, journey.progressAt(110L), "progress inside phase");
        assertClose(1.0D, journey.progressAt(120L), "progress at phase end");
        assertClose(1.0D, journey.progressAt(Long.MAX_VALUE), "progress after phase end");
        assertClose(1.0D,
                journey(EARTH, MARS, StationJourneyPhase.DEPARTING, 100L, 0L).progressAt(100L),
                "zero-duration phase progress");
    }

    private static void journeySnapshotsRoundTripAndRejectUnknownContracts() {
        StationJourney original = journey(EARTH, MARS, StationJourneyPhase.CRUISE, 105L, 10L);
        Map<String, String> snapshot = new HashMap<>(original.toSnapshot());
        snapshot.put("future_field", "ignored-by-version-one");
        assertEquals(original, StationJourney.fromSnapshot(snapshot), "snapshot round-trip");

        Map<String, String> unknownVersion = new HashMap<>(snapshot);
        unknownVersion.put("schema_version", "3");
        assertThrows(IllegalArgumentException.class,
                () -> StationJourney.fromSnapshot(unknownVersion), "unknown snapshot version");

        Map<String, String> unknownPhase = new HashMap<>(snapshot);
        unknownPhase.put("phase", "teleporting");
        assertThrows(IllegalArgumentException.class,
                () -> StationJourney.fromSnapshot(unknownPhase), "unknown snapshot phase");

        Map<String, String> invalidResource = new HashMap<>(snapshot);
        invalidResource.put("route_id", "not a valid id");
        assertThrows(IllegalArgumentException.class,
                () -> StationJourney.fromSnapshot(invalidResource), "invalid resource id");

        Map<String, String> emptyResource = new HashMap<>(snapshot);
        emptyResource.put("route_id", "");
        assertThrows(IllegalArgumentException.class,
                () -> StationJourney.fromSnapshot(emptyResource), "empty resource id");
    }

    private static void travelAdvancesAtExactBoundaries() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        StationJourneyService.TransitionResult started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 7L), route, 100L, JOURNEY_ID, REQUESTER_ID);
        assertEquals(8L, started.state().revision(), "journey start revision");
        assertEquals(StationJourneyPhase.DEPARTING, phase(started), "journey start phase");

        StationJourneyService.TransitionResult beforeBoundary = StationJourneyService.advance(
                started.state(), route, 104L);
        assertFalse(beforeBoundary.changed(), "progress-only tick must not change semantics");
        assertEquals(started.state(), beforeBoundary.state(), "progress-only state");

        StationJourneyService.TransitionResult cruise = StationJourneyService.advance(
                started.state(), route, 105L);
        assertEquals(StationJourneyPhase.CRUISE, phase(cruise), "departure boundary");
        assertEquals(EARTH, cruise.state().currentBody(), "body before cruise commit");
        assertEquals(9L, cruise.state().revision(), "departure boundary revision");

        StationJourneyService.TransitionResult arriving = StationJourneyService.advance(
                cruise.state(), route, 115L);
        assertEquals(StationJourneyPhase.ARRIVING, phase(arriving), "cruise boundary");
        assertEquals(MARS, arriving.state().currentBody(), "body commit point");
        assertEquals(10L, arriving.state().revision(), "body commit revision");
        assertTrue(arriving.changes().contains(StationJourneyService.Change.CURRENT_BODY_COMMITTED),
                "body commit semantic change");

        StationJourneyService.TransitionResult completed = StationJourneyService.advance(
                arriving.state(), route, 118L);
        assertTrue(completed.state().journey().isEmpty(), "journey completion");
        assertEquals(MARS, completed.state().currentBody(), "completed current body");
        assertEquals(11L, completed.state().revision(), "completion revision");
    }

    private static void oneAdvanceCanCrossEveryFinishedPhase() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        StationJourneyService.State started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 0L), route, 100L,
                JOURNEY_ID, REQUESTER_ID).state();
        StationJourneyService.TransitionResult completed = StationJourneyService.advance(started, route, 1_000L);
        assertEquals(MARS, completed.state().currentBody(), "large-jump target");
        assertTrue(completed.state().journey().isEmpty(), "large-jump completion");
        assertEquals(4L, completed.state().revision(), "one revision per semantic transition");
        assertEquals(4, completed.changes().size(), "large-jump semantic change set");
    }

    private static void zeroDurationPhasesCompleteDeterministically() {
        StationRouteDefinition route = route(0L, 0L, 0L, true);
        StationJourneyService.State started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 10L), route, 50L,
                JOURNEY_ID, REQUESTER_ID).state();
        StationJourneyService.TransitionResult completed = StationJourneyService.advance(started, route, 50L);
        assertEquals(MARS, completed.state().currentBody(), "zero-duration target");
        assertTrue(completed.state().journey().isEmpty(), "zero-duration completion");
        assertEquals(14L, completed.state().revision(), "zero-duration revision");
    }

    private static void repeatedAndRewoundCallsAreIdempotent() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        StationJourneyService.State started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 0L), route, 100L,
                JOURNEY_ID, REQUESTER_ID).state();
        StationJourneyService.TransitionResult rewound = StationJourneyService.advance(started, route, 99L);
        assertEquals(started, rewound.state(), "time rewind cannot roll back or advance");
        assertFalse(rewound.changed(), "time rewind change set");

        StationJourneyService.State cruise = StationJourneyService.advance(started, route, 105L).state();
        StationJourneyService.TransitionResult repeated = StationJourneyService.advance(cruise, route, 105L);
        assertEquals(cruise, repeated.state(), "repeated boundary call");
        assertFalse(repeated.changed(), "repeated boundary change set");

        StationJourneyService.State completed = StationJourneyService.advance(started, route, 1_000L).state();
        StationJourneyService.TransitionResult repeatedCompletion = StationJourneyService.advance(
                completed, route, 2_000L);
        assertEquals(completed, repeatedCompletion.state(), "repeated completion call");
        assertFalse(repeatedCompletion.changed(), "repeated completion change set");
    }

    private static void restartedSnapshotsResumeFromServerGameTime() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        StationJourneyService.State started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 20L), route, 100L,
                JOURNEY_ID, REQUESTER_ID).state();
        StationJourneyService.State cruise = StationJourneyService.advance(started, route, 105L).state();
        StationJourney restoredJourney = StationJourney.fromSnapshot(
                cruise.journey().orElseThrow().toSnapshot());
        StationJourneyService.State restored = new StationJourneyService.State(
                cruise.currentBody(), java.util.Optional.of(restoredJourney), cruise.revision());
        assertEquals(cruise, restored, "restart snapshot state");
        assertEquals(StationJourneyPhase.ARRIVING,
                phase(StationJourneyService.advance(restored, route, 115L)),
                "restart advances from persisted gameTime");
    }

    private static void calendarRateNeverEntersJourneyAdvancement() {
        StationRouteDefinition route = route(5L, 10L, 3L, true);
        StationJourneyService.State started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 0L), route, 100L,
                JOURNEY_ID, REQUESTER_ID).state();
        StationJourneyService.TransitionResult atNormalCalendar = advanceWithUnrelatedCalendarValue(
                started, route, 115L, 115L);
        StationJourneyService.TransitionResult atAcceleratedCalendar = advanceWithUnrelatedCalendarValue(
                started, route, 115L, 138_000L);
        assertEquals(atNormalCalendar, atAcceleratedCalendar,
                "TFC calendar ticks must not affect station journey advancement");
    }

    private static void celestialDefinitionCodecRoundTripsSquarePlanetAndOrbitalClouds() {
        String json = """
                {
                  "schema_version": 1,
                  "kind": "planet",
                  "parent": "wildfires:sun",
                  "surface_dimension": "minecraft:overworld",
                  "landable": true,
                  "environment": {
                    "total_pressure_kpa": 101.325,
                    "gases_kpa": {
                      "wildfires:oxygen": 21.2,
                      "wildfires:inert_air": 80.125
                    },
                    "hazards": []
                  },
                  "visual": {
                    "surface_atlas": "wildfires:textures/space/bodies/earth_cube.png",
                    "surface_atlas_layout": "wildfires:three_by_two_v1",
                    "near_body_renderer": "wildfires:cube",
                    "atmosphere": {
                      "enabled": true,
                      "radius_multiplier": 1.025,
                      "density": 1.0,
                      "color": [0.35, 0.55, 1.0],
                      "sunset_color": [0.55, 0.30, 0.14],
                      "night_color": [0.04, 0.03, 0.12],
                      "day_brightness": 1.1,
                      "sunset_brightness": 0.8,
                      "night_brightness": 0.3,
                      "day_transition": 3.0,
                      "night_transition": 4.0,
                      "limb_strength": 0.6,
                      "limb_power": 2.0,
                      "max_opacity": 0.72,
                      "exposure": 1.0
                    },
                    "clouds": {
                      "enabled": true,
                      "mapping": "equirectangular",
                      "texture": "wildfires:textures/space/clouds/earth_2x1.png",
                      "radius_multiplier": 1.012,
                      "opacity": 0.7,
                      "rotation_period_tfc_days": 1.08,
                      "rotation_axis": [0.0, 1.0, 0.0]
                    }
                  }
                }
                """;
        CelestialDefinition definition = parseDefinition(json);
        assertEquals(CelestialKind.PLANET, definition.kind(), "celestial kind");
        assertTrue(definition.requestsLanding(), "existing surface binding requests landing");
        assertTrue(definition.visual().clouds().enabled(), "orbital cloud shell enabled");
        assertEquals(CelestialVisualDefinition.CloudMapping.EQUIRECTANGULAR,
                definition.visual().clouds().mapping(), "cloud mapping");
        assertClose(101.325D, definition.environment().gasPressureSumKpa(), "gas pressure sum");

        JsonElement encoded = CelestialDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .getOrThrow(false, message -> {
                    throw new AssertionError("celestial encode failed: " + message);
                });
        CelestialDefinition decoded = CelestialDefinition.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> {
                    throw new AssertionError("celestial round-trip decode failed: " + message);
                });
        assertEquals(definition, decoded, "celestial codec round-trip");
        assertEquals("planet", CelestialKind.fromId("planet").orElseThrow().id(), "stable kind id");
        assertTrue(CelestialKind.fromId("unknown").isEmpty(), "unknown kind id");
    }

    private static void celestialContractsRejectUnsafeEnvironmentAndShells() {
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialEnvironment(Double.NaN, Map.of(), Set.of()),
                "non-finite pressure");
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialEnvironment(10.0D,
                        Map.of(id("oxygen"), 11.0D), Set.of()),
                "gas sum above total pressure");
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialVisualDefinition.Color(1.1D, 0.0D, 0.0D),
                "color outside unit range");

        CelestialVisualDefinition.Atmosphere atmosphere = new CelestialVisualDefinition.Atmosphere(
                true, 1.025D, 1.0D, new CelestialVisualDefinition.Color(0.3D, 0.5D, 1.0D),
                java.util.Optional.empty(), java.util.Optional.empty(),
                1.0D, 1.0D, 1.0D, 3.0D, 4.0D, 0.6D, 2.0D, 0.72D, 1.0D);
        assertClose(0.234D, atmosphere.resolvedSunsetColor().red(),
                "legacy atmosphere derives sunset red");
        assertClose(0.31D, atmosphere.resolvedSunsetColor().green(),
                "legacy atmosphere derives sunset green");
        assertClose(0.52D, atmosphere.resolvedSunsetColor().blue(),
                "legacy atmosphere derives sunset blue");
        assertClose(0.06D, atmosphere.resolvedNightColor().red(),
                "legacy atmosphere derives night red");
        assertClose(0.05D, atmosphere.resolvedNightColor().green(),
                "legacy atmosphere derives night green");
        assertClose(0.4D, atmosphere.resolvedNightColor().blue(),
                "legacy atmosphere derives night blue");
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialVisualDefinition.Atmosphere(true, 1.025D, 1.0D,
                        new CelestialVisualDefinition.Color(0.3D, 0.5D, 1.0D),
                        java.util.Optional.empty(), java.util.Optional.empty(),
                        1.0D, 1.0D, 1.0D, 3.0D, 4.0D, 0.6D, 2.0D, 1.1D, 1.0D),
                "atmosphere opacity above one");
        CelestialVisualDefinition.CloudLayer outsideAtmosphere = new CelestialVisualDefinition.CloudLayer(
                true, CelestialVisualDefinition.CloudMapping.EQUIRECTANGULAR,
                java.util.Optional.of(id("textures/space/clouds/test.png")), false,
                1.03D, 0.5D, new CelestialVisualDefinition.Color(1.0D, 1.0D, 1.0D),
                1.0D, CelestialVisualDefinition.Axis.UP, 0.0D, 0.5D);
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialVisualDefinition(java.util.Optional.of(id("textures/space/bodies/test.png")),
                        false, id("three_by_two_v1"), id("cube"), atmosphere, outsideAtmosphere),
                "cloud shell outside atmosphere");

        CelestialVisualDefinition.CloudLayer proceduralClouds = new CelestialVisualDefinition.CloudLayer(
                true, CelestialVisualDefinition.CloudMapping.PROCEDURAL, java.util.Optional.empty(), true,
                1.012D, 0.6D, new CelestialVisualDefinition.Color(1.0D, 1.0D, 1.0D),
                2.0D, CelestialVisualDefinition.Axis.UP, 0.25D, 0.4D);
        CelestialVisualDefinition proceduralVisual = new CelestialVisualDefinition(
                java.util.Optional.empty(), true, id("three_by_two_v1"), id("cube"), atmosphere,
                proceduralClouds);
        assertTrue(proceduralVisual.clouds().procedural(), "procedural cloud fallback");

        assertThrows(IllegalArgumentException.class,
                () -> new CelestialVisualDefinition.CloudLayer(true,
                        CelestialVisualDefinition.CloudMapping.EQUIRECTANGULAR,
                        java.util.Optional.empty(), false, 1.012D, 0.5D,
                        new CelestialVisualDefinition.Color(1.0D, 1.0D, 1.0D),
                        1.0D, CelestialVisualDefinition.Axis.UP, 0.0D, 0.0D),
                "enabled cloud shell without source");
    }

    private static void missingSurfaceBindingStaysVisualButCannotRequestLanding() {
        String json = """
                {
                  "schema_version": 1,
                  "kind": "planet",
                  "landable": true,
                  "environment": {"total_pressure_kpa": 0.0},
                  "visual": {
                    "surface_atlas": "wildfires:textures/space/bodies/unbound_cube.png",
                    "atmosphere": {"enabled": false, "radius_multiplier": 1.0, "density": 0.0},
                    "clouds": {"enabled": false, "radius_multiplier": 1.0, "opacity": 0.0,
                      "rotation_period_tfc_days": 1.0, "rotation_axis": [0.0, 1.0, 0.0]}
                  }
                }
                """;
        CelestialDefinition definition = parseDefinition(json);
        assertTrue(definition.landable(), "declared landing intent retained");
        assertTrue(definition.surfaceDimension().isEmpty(), "missing surface binding retained");
        assertFalse(definition.requestsLanding(), "missing surface binding cannot request landing");
        assertEquals(id("textures/space/bodies/unbound_cube.png"),
                definition.visual().surfaceAtlas().orElseThrow(), "unbound body visual retained");

        assertThrows(IllegalArgumentException.class,
                () -> new CelestialDefinition(CelestialDefinition.SCHEMA_VERSION, CelestialKind.PLANET,
                        java.util.Optional.empty(), java.util.Optional.of(CelestialDefinition.ORBIT_DIMENSION),
                        true, CelestialEnvironment.vacuum(), definition.visual()),
                "orbit dimension cannot be a planet surface");
        assertThrows(IllegalArgumentException.class,
                () -> new CelestialDefinition(2, CelestialKind.PLANET, java.util.Optional.empty(),
                        java.util.Optional.empty(), false, CelestialEnvironment.vacuum(), definition.visual()),
                "unknown celestial schema");
    }

    private static CelestialDefinition parseDefinition(String json) {
        return CelestialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, message -> {
                    throw new AssertionError("celestial decode failed: " + message);
                });
    }

    private static void builtInCelestialsLoadAndOnlyEarthBindsOverworld() {
        Map<ResourceLocation, CelestialDefinition> definitions = loadBuiltInCelestials();
        assertEquals(20, definitions.size(), "built-in celestial count");
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        long boundBodies = definitions.values().stream().filter(body -> body.surfaceDimension().isPresent()).count();
        assertEquals(1L, boundBodies, "only one built-in surface binding");
        assertEquals(overworld, definitions.get(EARTH).surfaceDimension().orElseThrow(),
                "Earth binds the existing overworld");
        for (Map.Entry<ResourceLocation, CelestialDefinition> entry : definitions.entrySet()) {
            if (!entry.getKey().equals(EARTH)) {
                assertTrue(entry.getValue().surfaceDimension().isEmpty(),
                        "non-Earth built-in surface must stay unbound: " + entry.getKey());
            }
        }

        CelestialBindingValidator.Report report = CelestialBindingValidator.validate(
                definitions, Set.of(overworld), resource -> false);
        assertFalse(report.hasErrors(), "built-in celestial validation");
        assertTrue(report.get(EARTH).orElseThrow().landingAvailable(), "Earth landing binding");
        assertEquals(CelestialBindingValidator.VisualSource.PROCEDURAL,
                report.get(EARTH).orElseThrow().cloudSource(), "Earth procedural orbital clouds");
        for (Map.Entry<ResourceLocation, CelestialBindingValidator.ResolvedDefinition> entry
                : report.resolved().entrySet()) {
            if (!entry.getKey().equals(EARTH)) {
                assertFalse(entry.getValue().landingAvailable(),
                        "non-Earth built-in landing must stay disabled: " + entry.getKey());
            }
            assertTrue(entry.getValue().routeAvailable(),
                    "built-in body route availability: " + entry.getKey());
        }

        Path dataRoot = Path.of("src", "main", "resources", "data", "wildfires");
        assertEquals(List.of("orbit.json"), fileNames(dataRoot.resolve("dimension")),
                "P4 has exactly one Wildfires dimension");
        assertEquals(List.of("orbit.json"), fileNames(dataRoot.resolve("dimension_type")),
                "P4 has exactly one Wildfires dimension type");
    }

    private static List<String> fileNames(Path directory) {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException exception) {
            throw new AssertionError("failed to inspect dimension resource directory: " + directory, exception);
        }
    }

    private static void bindingValidationNeverCreatesOrGuessesDimensions() {
        Map<ResourceLocation, CelestialDefinition> definitions = loadBuiltInCelestials();
        CelestialBindingValidator.Report withoutOverworld = CelestialBindingValidator.validate(
                definitions, Set.of(), resource -> false);
        CelestialBindingValidator.ResolvedDefinition earth = withoutOverworld.get(EARTH).orElseThrow();
        assertFalse(earth.landingAvailable(), "missing overworld disables Earth landing");
        assertTrue(earth.surfaceDimension().isEmpty(), "missing dimension cannot be guessed");
        assertTrue(earth.routeAvailable(), "missing surface dimension keeps Earth as a visual route target");
        assertTrue(earth.issues().stream().anyMatch(issue -> issue.code()
                        == CelestialBindingValidator.IssueCode.UNAVAILABLE_SURFACE_DIMENSION),
                "missing dimension diagnostic");
    }

    private static void parentAndResourceFailuresAreExplicit() {
        Map<ResourceLocation, CelestialDefinition> definitions = new HashMap<>(loadBuiltInCelestials());
        CelestialDefinition earth = definitions.get(EARTH);
        ResourceLocation moon = id("moon");
        definitions.put(EARTH, new CelestialDefinition(earth.schemaVersion(), earth.kind(),
                java.util.Optional.of(moon), earth.surfaceDimension(), earth.landable(),
                earth.environment(), earth.visual()));
        CelestialDefinition moonDefinition = definitions.get(moon);
        definitions.put(moon, new CelestialDefinition(moonDefinition.schemaVersion(), moonDefinition.kind(),
                java.util.Optional.of(EARTH), moonDefinition.surfaceDimension(), moonDefinition.landable(),
                moonDefinition.environment(), moonDefinition.visual()));
        CelestialBindingValidator.Report cycle = CelestialBindingValidator.validate(
                definitions, Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
                resource -> false);
        assertTrue(cycle.get(EARTH).orElseThrow().issues().stream().anyMatch(issue -> issue.code()
                        == CelestialBindingValidator.IssueCode.PARENT_CYCLE),
                "Earth/Moon parent cycle diagnostic");
        assertFalse(cycle.get(EARTH).orElseThrow().routeAvailable(), "cycle isolates route target");

        CelestialVisualDefinition.CloudLayer fallbackCloud = new CelestialVisualDefinition.CloudLayer(
                true, CelestialVisualDefinition.CloudMapping.EQUIRECTANGULAR,
                java.util.Optional.of(id("textures/space/clouds/missing.png")), true,
                1.012D, 0.6D, new CelestialVisualDefinition.Color(1.0D, 1.0D, 1.0D),
                1.0D, CelestialVisualDefinition.Axis.UP, 0.0D, 0.5D);
        CelestialVisualDefinition visualWithFallback = new CelestialVisualDefinition(
                java.util.Optional.of(id("textures/space/bodies/missing.png")), true,
                id("three_by_two_v1"), id("cube"), earth.visual().atmosphere(), fallbackCloud);
        CelestialDefinition fallbackDefinition = new CelestialDefinition(earth.schemaVersion(), earth.kind(),
                earth.parent(), earth.surfaceDimension(), earth.landable(), earth.environment(), visualWithFallback);
        CelestialBindingValidator.Report fallback = CelestialBindingValidator.validate(
                Map.of(EARTH, fallbackDefinition),
                Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")),
                resource -> false);
        CelestialBindingValidator.ResolvedDefinition resolved = fallback.get(EARTH).orElseThrow();
        assertEquals(CelestialBindingValidator.VisualSource.PROCEDURAL, resolved.surfaceSource(),
                "missing surface texture uses procedural fallback");
        assertEquals(CelestialBindingValidator.VisualSource.PROCEDURAL, resolved.cloudSource(),
                "missing cloud texture uses visible procedural fallback");
    }

    private static void registryReloadInvalidatesOldGenerationsAndReportsRemovedBodies() {
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        Map<ResourceLocation, CelestialDefinition> initialDefinitions = loadBuiltInCelestials();
        CelestialRegistrySnapshot initial = CelestialRegistrySnapshot.reload(
                CelestialRegistrySnapshot.empty(), 1L, initialDefinitions, Set.of(overworld),
                resource -> false);
        assertEquals(CelestialRegistrySnapshot.LookupStatus.PRESENT,
                initial.lookup(1L, EARTH).status(), "current generation lookup");

        Map<ResourceLocation, CelestialDefinition> reloadedDefinitions = new HashMap<>(initialDefinitions);
        reloadedDefinitions.remove(MARS);
        CelestialRegistrySnapshot reloaded = CelestialRegistrySnapshot.reload(
                initial, 2L, reloadedDefinitions, Set.of(overworld), resource -> false);
        assertEquals(Set.of(MARS), reloaded.removedDefinitions(), "reload removed definition set");
        assertEquals(CelestialRegistrySnapshot.LookupStatus.REMOVED,
                reloaded.lookup(2L, MARS).status(), "removed definition lookup");
        assertEquals(CelestialRegistrySnapshot.LookupStatus.STALE_GENERATION,
                reloaded.lookup(1L, EARTH).status(), "old generation invalidation");
        assertEquals(CelestialRegistrySnapshot.LookupStatus.MISSING,
                reloaded.lookup(2L, id("never_defined")).status(), "never-defined lookup");
        assertTrue(reloaded.lookup(2L, EARTH).definition().orElseThrow().landingAvailable(),
                "Earth remains the only live overworld landing binding after reload");
        assertThrows(IllegalArgumentException.class,
                () -> CelestialRegistrySnapshot.reload(reloaded, 2L, reloadedDefinitions,
                        Set.of(overworld), resource -> false),
                "registry generation must advance");
    }

    private static Map<ResourceLocation, CelestialDefinition> loadBuiltInCelestials() {
        Path folder = Path.of("src", "main", "resources", "data", "wildfires", "wildfires", "celestials");
        Map<ResourceLocation, CelestialDefinition> definitions = new HashMap<>();
        try (var paths = Files.list(folder)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        String bodyPath = filename.substring(0, filename.length() - ".json".length());
                        try {
                            CelestialDefinition previous = definitions.put(
                                    ResourceLocation.fromNamespaceAndPath("wildfires", bodyPath),
                                    parseDefinition(Files.readString(path)));
                            if (previous != null) {
                                throw new AssertionError("duplicate built-in celestial id: " + bodyPath);
                            }
                        } catch (IOException exception) {
                            throw new AssertionError("failed to read built-in celestial " + path, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new AssertionError("failed to list built-in celestial resources", exception);
        }
        return Map.copyOf(definitions);
    }

    private static StationJourneyService.TransitionResult advanceWithUnrelatedCalendarValue(
            StationJourneyService.State state, StationRouteDefinition route,
            long serverGameTime, long ignoredCalendarTicks) {
        if (ignoredCalendarTicks < 0L) {
            throw new AssertionError("test calendar value must be non-negative");
        }
        return StationJourneyService.advance(state, route, serverGameTime);
    }

    private static StationRouteDefinition route(long departure, long cruise, long arrival, boolean enabled) {
        return new StationRouteDefinition(ROUTE_ID, EARTH, MARS, departure, cruise, arrival, enabled);
    }

    private static StationJourney journey(ResourceLocation from, ResourceLocation to,
                                           StationJourneyPhase phase, long started, long duration) {
        return new StationJourney(JOURNEY_ID, ROUTE_ID, from, to, phase,
                started, duration, REQUESTER_ID);
    }

    private static StationJourneyPhase phase(StationJourneyService.TransitionResult result) {
        return result.state().journey().orElseThrow().phase();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("wildfires", path);
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError(name + ": expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError(name + ": expected " + expected.getSimpleName()
                        + ", got " + actual.getClass().getSimpleName(), actual);
            }
        }
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) {
            throw new AssertionError(name);
        }
    }

    private static void assertFalse(boolean value, String name) {
        assertTrue(!value, name);
    }

    private static void assertClose(double expected, double actual, String name) {
        if (Math.abs(expected - actual) > 1.0E-12D) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
