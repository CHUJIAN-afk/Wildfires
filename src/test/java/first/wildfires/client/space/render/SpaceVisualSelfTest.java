package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.api.celestial.SolarEclipseState;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import first.wildfires.thirdparty.genesisadapt.GenesisCubeAtlasLayout;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Plain-Java P6 contract checks; no render thread or OpenGL context is required. */
public final class SpaceVisualSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation SUN = id("sun");
    private static final ResourceLocation MOON = id("moon");
    private static final ResourceLocation MARS = id("mars");

    private SpaceVisualSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        genesisAtlasUsesExactFaceContract();
        genesisDirectionsAndFallbackSurfaceAreSeamContinuous();
        ntmNightAtlasUsesItsOwnExactFaceContract();
        surfaceTexturePolicyUsesOnlyGenesisCubemapsOrFallback();
        stationObserverUsesUnifiedRealEphemeris();
        ntmJourneyPhasesKeepTheirOrbitAndTransferSemantics();
        ntmPointAndCubeLodUsesRecordedThresholds();
        compressedDepthPreservesOcclusionAndAngularSize();
        orbitFrameAndPlanetRotationAreTimeDriven();
        ntmIlluminationUsesRotatedCubeOcclusion();
        sunIsASeparateFlatNtmLayerAndLightsPlanetsFromSystemCenter();
        orbitalCloudMaterialMovesWithoutRotatingShell();
        copiedNtmTexturesMatchRecordedHashes();
        adaptedShadersDeclareAlphaAndValidMatrices();
        System.out.println("SpaceVisualSelfTest passed");
    }

    private static void genesisAtlasUsesExactFaceContract() {
        assertEquals(3, GenesisCubeAtlasLayout.COLUMNS, "atlas columns");
        assertEquals(2, GenesisCubeAtlasLayout.ROWS, "atlas rows");
        assertEquals(List.of("north", "west", "south", "east", "down", "up"),
                GenesisCubeAtlasLayout.FACES.stream().map(GenesisCubeAtlasLayout.Face::name).toList(),
                "Genesis face order");
        Set<String> cells = new HashSet<>();
        for (GenesisCubeAtlasLayout.Face face : GenesisCubeAtlasLayout.FACES) {
            assertTrue(cells.add(face.column() + ":" + face.row()), "face cell unique");
            GenesisCubeAtlasLayout.Uv minimum = GenesisCubeAtlasLayout.atlasUv(face, 0.0D, 0.0D);
            GenesisCubeAtlasLayout.Uv maximum = GenesisCubeAtlasLayout.atlasUv(face, 1.0D, 1.0D);
            assertClose(1.0D / 3.0D, maximum.u() - minimum.u(), "cell width");
            assertClose(0.5D, maximum.v() - minimum.v(), "cell height");
            assertClose(1.0D, face.normal().length(), "face normal unit");
        }
        assertThrows(IllegalArgumentException.class, () -> GenesisCubeAtlasLayout.face(6),
                "invalid face rejected");
    }

    private static void genesisDirectionsAndFallbackSurfaceAreSeamContinuous() {
        for (GenesisCubeAtlasLayout.Face face : GenesisCubeAtlasLayout.FACES) {
            for (int edge = 0; edge < 4; edge++) {
                for (int step = 0; step <= 16; step++) {
                    double value = step / 16.0D;
                    GenesisCubeAtlasLayout.Direction direction = edge(face, edge, value);
                    GenesisCubeAtlasLayout.Direction match = matchingEdge(face, direction, value);
                    assertClose(direction.x(), match.x(), "seam x");
                    assertClose(direction.y(), match.y(), "seam y");
                    assertClose(direction.z(), match.z(), "seam z");
                    assertEquals(OrbitProceduralTexture.surface(EARTH, direction),
                            OrbitProceduralTexture.surface(EARTH, match), "seam surface sample");
                }
            }
        }
    }

    private static void ntmNightAtlasUsesItsOwnExactFaceContract() throws Exception {
        assertEquals(List.of(4, 1, 0, 5, 2, 3),
                NtmOrbitSkyRenderer.NIGHT_FACES.stream()
                        .map(NtmOrbitSkyRenderer.NightFace::atlasIndex).toList(),
                "NTM source atlas draw order");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(0), 'x', -1, "NTM cell 4 west");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(1), 'y', 1, "NTM cell 1 up");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(2), 'y', -1, "NTM cell 0 down");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(3), 'z', -1, "NTM cell 5 north");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(4), 'x', 1, "NTM cell 2 east");
        assertPlane(NtmOrbitSkyRenderer.NIGHT_FACES.get(5), 'z', 1, "NTM cell 3 south");
        List<List<NtmOrbitSkyRenderer.NightVertex>> expectedCorners = List.of(
                List.of(vertex(-1, 1, 1), vertex(-1, -1, 1), vertex(-1, -1, -1), vertex(-1, 1, -1)),
                List.of(vertex(1, 1, 1), vertex(-1, 1, 1), vertex(-1, 1, -1), vertex(1, 1, -1)),
                List.of(vertex(-1, -1, 1), vertex(1, -1, 1), vertex(1, -1, -1), vertex(-1, -1, -1)),
                List.of(vertex(-1, 1, -1), vertex(-1, -1, -1), vertex(1, -1, -1), vertex(1, 1, -1)),
                List.of(vertex(1, 1, -1), vertex(1, -1, -1), vertex(1, -1, 1), vertex(1, 1, 1)),
                List.of(vertex(1, 1, 1), vertex(1, -1, 1), vertex(-1, -1, 1), vertex(-1, 1, 1)));
        for (int index = 0; index < NtmOrbitSkyRenderer.NIGHT_FACES.size(); index++) {
            NtmOrbitSkyRenderer.NightFace face = NtmOrbitSkyRenderer.NIGHT_FACES.get(index);
            assertEquals(expectedCorners.get(index),
                    List.of(face.first(), face.second(), face.third(), face.fourth()),
                    "NTM transformed night face corners " + index);
            double expectedU = (face.atlasIndex() % 3) / 3.0D;
            double expectedV = (face.atlasIndex() / 3) / 2.0D;
            assertClose(expectedU, face.uMin(), "NTM face u min " + index);
            assertClose(expectedU + 1.0D / 3.0D, face.uMax(), "NTM face u max " + index);
            assertClose(expectedV, face.vMin(), "NTM face v min " + index);
            assertClose(expectedV + 0.5D, face.vMax(), "NTM face v max " + index);
        }
        String renderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/NtmOrbitSkyRenderer.java"));
        assertTrue(!renderer.contains("GenesisCubeAtlasLayout"),
                "NTM night atlas never reuses the incompatible Genesis layout");
    }

    private static void surfaceTexturePolicyUsesOnlyGenesisCubemapsOrFallback() {
        ResourceLocation atlas = id("textures/space/earth.png");
        CelestialVisualDefinition direct = visual(Optional.of(atlas), id("three_by_two_v1"));
        CelestialVisualDefinition wrongLayout = visual(Optional.of(atlas), id("other_layout"));
        CelestialVisualDefinition procedural = visual(Optional.empty(), id("three_by_two_v1"));
        assertEquals(OrbitTextureRules.SourceChoice.DIRECT_CUBE_ATLAS,
                OrbitTextureRules.surface(direct, true), "Genesis atlas direct");
        assertEquals(OrbitTextureRules.SourceChoice.PROCEDURAL,
                OrbitTextureRules.surface(direct, false), "missing atlas fallback");
        assertEquals(OrbitTextureRules.SourceChoice.PROCEDURAL,
                OrbitTextureRules.surface(wrongLayout, true), "foreign layout fallback");
        assertEquals(OrbitTextureRules.SourceChoice.PROCEDURAL,
                OrbitTextureRules.surface(procedural, false), "procedural surface fallback");
    }

    private static void stationObserverUsesUnifiedRealEphemeris() {
        CelestialState state = state();
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state, 0.0D);
        double expectedOrbitRadius = 0.006371D
                / Math.tan(Math.toRadians(OrbitVisualRules.NEAR_PHYSICAL_DIAMETER_DEGREES * 0.5D));
        assertClose(expectedOrbitRadius, frame.observerPosition().length(), "Earth visual orbit radius");
        OrbitVisualRules.BodyLayer earth = body(frame, EARTH);
        assertClose(earth.renderDistance() * Math.tan(Math.toRadians(
                        OrbitVisualRules.NEAR_PHYSICAL_DIAMETER_DEGREES * 0.5D)),
                earth.renderHalfSize(),
                "Genesis near cube preserves the configured physical angular radius");
        assertTrue(earth.renderHalfSize() < earth.renderDistance(),
                "near cube never contains the orbit camera");
        OrbitVisualRules.BodyLayer mars = body(frame, MARS);
        CelestialVector rawDirection = new CelestialVector(200.0D, 0.0D, 0.0D)
                .subtract(frame.observerPosition()).normalized();
        CelestialVector expectedDirection = OrbitVisualRules.ntmFrameVector(rawDirection);
        assertVector(expectedDirection, mars.direction(), "Mars direction from real position");
        assertTrue(mars.distance() > 199.0D && mars.distance() < 201.0D,
                "Mars distance remains based on real ephemeris");
    }

    private static void ntmJourneyPhasesKeepTheirOrbitAndTransferSemantics() {
        CelestialState state = state();
        ObservationJourney departing = journey(StationJourneyPhase.DEPARTING, 1_000L, 400L);
        for (double time : new double[]{1_000.0D, 1_200.0D, 1_400.0D}) {
            CelestialVector expected = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state, time)
                    .observerPosition();
            CelestialVector actual = OrbitVisualRules.frame(context(EARTH, Optional.of(departing)), state, time)
                    .observerPosition();
            assertVector(expected, actual, "DEPARTING remains in source orbit");
        }
        // NTM adds the normalized sky half-turn after -calculateSingleAngle + 90 degrees.
        double travelAngle = -Math.PI * 0.5D;
        assertClose(0.0D, OrbitVisualRules.frame(context(EARTH, Optional.of(departing)), state,
                1_000.0D).viewRotationRadians(), "DEPARTING starts at the inertial orbital attitude");
        assertClose(OrbitVisualRules.circularLerpRadians(0.0D, travelAngle, 0.5D),
                OrbitVisualRules.frame(context(EARTH, Optional.of(departing)), state,
                        1_200.0D).viewRotationRadians(),
                "DEPARTING circularly turns toward the NTM travel angle");
        assertClose(travelAngle,
                OrbitVisualRules.frame(context(EARTH, Optional.of(departing)), state,
                        1_400.0D).viewRotationRadians(),
                "DEPARTING ends aligned to the NTM travel angle");

        ObservationJourney cruise = journey(StationJourneyPhase.CRUISE, 2_000L, 1_000L);
        CelestialVector cruiseStart = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), state,
                2_000.0D).observerPosition();
        CelestialVector cruiseEnd = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), state,
                3_000.0D).observerPosition();
        assertVector(OrbitVisualRules.frame(context(EARTH, Optional.empty()), state, 2_000.0D)
                .observerPosition(), cruiseStart, "CRUISE source endpoint");
        assertVector(OrbitVisualRules.frame(context(MARS, Optional.empty()), state, 3_000.0D)
                .observerPosition(), cruiseEnd, "CRUISE target endpoint");
        assertClose(travelAngle, OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), state,
                2_500.0D).viewRotationRadians(), "CRUISE keeps the NTM travel angle");
        assertClose(0.5D, OrbitVisualRules.circularTransfer(0.5D), "NTM transfer midpoint");
        assertClose((1.0D - Math.sqrt(1.0D - Math.pow(0.5D, 3.0D))) * 0.5D,
                OrbitVisualRules.circularTransfer(0.25D), "NTM cubic circular ease");

        ObservationJourney arriving = journey(StationJourneyPhase.ARRIVING, 3_000L, 400L);
        for (double time : new double[]{3_000.0D, 3_200.0D, 3_400.0D}) {
            assertVector(OrbitVisualRules.frame(context(MARS, Optional.empty()), state, time)
                            .observerPosition(),
                    OrbitVisualRules.frame(context(MARS, Optional.of(arriving)), state, time)
                            .observerPosition(),
                    "ARRIVING remains in target orbit");
        }
        assertClose(travelAngle,
                OrbitVisualRules.frame(context(MARS, Optional.of(arriving)), state,
                        3_000.0D).viewRotationRadians(),
                "ARRIVING starts aligned to the NTM travel angle");
        assertClose(OrbitVisualRules.circularLerpRadians(travelAngle, 0.0D, 0.5D),
                OrbitVisualRules.frame(context(MARS, Optional.of(arriving)), state,
                        3_200.0D).viewRotationRadians(),
                "ARRIVING circularly returns toward the inertial orbital attitude");
        assertClose(0.0D,
                OrbitVisualRules.frame(context(MARS, Optional.of(arriving)), state,
                        3_400.0D).viewRotationRadians(),
                "ARRIVING ends at the inertial orbital attitude");
        assertClose(Math.toRadians(-179.0D), OrbitVisualRules.circularLerpRadians(
                Math.toRadians(179.0D), Math.toRadians(-177.0D), 0.5D),
                "NTM circular interpolation crosses the signed angle seam by the short path");
    }

    private static void ntmPointAndCubeLodUsesRecordedThresholds() {
        assertClose(0.0D, OrbitVisualRules.cubeAlphaFor(0.01D), "cube starts at 0.01");
        assertClose(1.0D, OrbitVisualRules.pointAlphaFor(0.01D), "point opaque at 0.01");
        assertClose(1.0D, OrbitVisualRules.cubeAlphaFor(0.5D), "cube opaque at 0.5");
        assertClose(0.0D, OrbitVisualRules.pointAlphaFor(0.5D), "point ends at 0.5");
        double middle = (0.01D + 0.5D) * 0.5D;
        assertClose(0.5D, OrbitVisualRules.cubeAlphaFor(middle), "cube midpoint alpha");
        assertClose(0.5D, OrbitVisualRules.pointAlphaFor(middle), "point midpoint alpha");
        assertClose(2.0D * Math.atan(2.0D / 20.0D) * OrbitVisualRules.NTM_RENDER_SCALE,
                OrbitVisualRules.apparentSize(2.0D, 20.0D), "NTM apparent render-size formula");
    }

    private static void compressedDepthPreservesOcclusionAndAngularSize() {
        double near = OrbitVisualRules.renderDistance(0.02D);
        double middle = OrbitVisualRules.renderDistance(2.0D);
        double far = OrbitVisualRules.renderDistance(200.0D);
        assertTrue(near < middle && middle < far,
                "compressed render distance is strictly monotonic across orbital scales");
        assertTrue(far <= OrbitVisualRules.MAX_BODY_RENDER_DISTANCE,
                "compressed depth stays inside the sky projection");
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(), 0.0D);
        for (OrbitVisualRules.BodyLayer layer : frame.bodies()) {
            assertClose(Math.max(layer.radius() / layer.distance(), 0.015D / layer.renderDistance()),
                    layer.renderHalfSize() / layer.renderDistance(),
                    "compressed depth preserves angular size for " + layer.body());
        }
    }

    private static void orbitFrameAndPlanetRotationAreTimeDriven() throws Exception {
        OrbitVisualRules.Frame start = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(), 0.0D);
        OrbitVisualRules.Frame quarter = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(),
                OrbitVisualRules.VISUAL_ORBIT_TICKS * 0.25D);
        OrbitVisualRules.Frame half = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(),
                OrbitVisualRules.VISUAL_ORBIT_TICKS * 0.5D);
        OrbitVisualRules.Frame threeQuarter = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(),
                OrbitVisualRules.VISUAL_ORBIT_TICKS * 0.75D);
        OrbitVisualRules.Frame closed = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(),
                OrbitVisualRules.VISUAL_ORBIT_TICKS);
        CelestialVector startOffset = start.observerPosition();
        CelestialVector quarterOffset = quarter.observerPosition();
        assertTrue(Math.abs(startOffset.x()) > Math.abs(startOffset.y()),
                "NTM orbit starts on local X axis");
        assertTrue(Math.abs(quarterOffset.y()) > Math.abs(quarterOffset.x()),
                "NTM orbit visibly advances through the local XY plane");
        assertClose(0.0D, start.viewRotationRadians(),
                "ordinary orbit keeps an inertial station heading");
        assertVector(new CelestialVector(0.0D, 0.0D, -1.0D), viewedDirection(start, EARTH),
                "orbit phase 0 places Earth on the first station-horizon quadrant");
        assertVector(new CelestialVector(-1.0D, 0.0D, 0.0D), viewedDirection(quarter, EARTH),
                "orbit quarter visibly advances Earth by 90 degrees");
        assertVector(new CelestialVector(0.0D, 0.0D, 1.0D), viewedDirection(half, EARTH),
                "orbit half visibly advances Earth by 180 degrees");
        assertVector(new CelestialVector(1.0D, 0.0D, 0.0D), viewedDirection(threeQuarter, EARTH),
                "orbit three-quarter visibly advances Earth by 270 degrees");
        assertVector(viewedDirection(start, EARTH), viewedDirection(closed, EARTH),
                "8000 ticks close the complete visible orbit exactly once");
        assertClose(0.0D, OrbitVisualRules.surfaceRotationRadians(EARTH, 0.0D),
                "Earth rotation starts at zero");
        assertClose(Math.PI, OrbitVisualRules.surfaceRotationRadians(EARTH,
                        OrbitVisualRules.rotationPeriodTfcDays(EARTH) * 12_000.0D),
                "Earth rotates half a turn in half a sidereal period");
        assertTrue(OrbitVisualRules.rotationPeriodTfcDays(id("venus")) < 0.0D,
                "retrograde planetary rotation remains signed");
        assertTrue(OrbitVisualRules.rotationPeriodTfcDays(id("triton")) > 0.0D,
                "Triton synchronously spins around its already-retrograde orbit normal");
        assertTrue(Math.abs(OrbitVisualRules.spinAxisEcliptic(EARTH).z()
                        - Math.cos(Math.toRadians(23.439281D))) < 1.0E-6D,
                "Earth cube uses the physical terrestrial spin pole");
        assertTrue(Math.abs(OrbitVisualRules.spinAxisEcliptic(id("uranus")).z()) < 0.2D,
                "Uranus cube keeps its near-sideways physical spin pole");
        assertTrue(!Files.readString(Path.of(
                        "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"))
                        .contains("stableRotation"),
                "planet cube orientation has no id-seeded random tilt fallback");
        assertClose(7_200.0D, OrbitVisualRules.NTM_ORBITAL_PERIOD_SECONDS,
                "NTM orbit keeps its recorded 7200 second period");
        assertClose(8_000.0D, OrbitVisualRules.VISUAL_ORBIT_TICKS,
                "NTM maps 7200 physical seconds through its KSP-day conversion to 8000 ticks");
        assertClose(80.0D, OrbitVisualRules.NTM_SOURCE_ORBITAL_TILT_DEGREES,
                "NTM source 80 degree visual tilt remains recorded");
        assertClose(90.0D, OrbitVisualRules.STATION_ECLIPTIC_TILT_DEGREES,
                "Wildfires station deck is exactly perpendicular to the ecliptic");
        assertVector(new CelestialVector(0.0D, 1.0D, 0.0D),
                OrbitVisualRules.ntmFrameVector(new CelestialVector(1.0D, 0.0D, 0.0D)),
                "Wildfires ecliptic X maps to NTM orbit Y");
        assertVector(new CelestialVector(0.0D, 0.0D, -1.0D),
                OrbitVisualRules.ntmFrameVector(new CelestialVector(0.0D, 1.0D, 0.0D)),
                "Wildfires ecliptic Y maps to the opposite-handed NTM orbit Z");
        assertVector(new CelestialVector(1.0D, 0.0D, 0.0D),
                OrbitVisualRules.ntmFrameVector(new CelestialVector(0.0D, 0.0D, 1.0D)),
                "Wildfires ecliptic normal maps to NTM orbit normal");
        org.joml.Vector3f transformedNormal = new org.joml.Vector3f(1.0F, 0.0F, 0.0F);
        new org.joml.Quaternionf().rotationX((float) Math.toRadians(
                        OrbitVisualRules.STATION_ECLIPTIC_TILT_DEGREES))
                .rotateY((float) Math.toRadians(-90.0D))
                .rotateX(1.234F).transform(transformedNormal);
        assertClose(1.0D, Math.abs(transformedNormal.y()),
                "ecliptic normal remains exactly parallel to player vertical at every heading");
        assertClose(0.0D, transformedNormal.x(), "ecliptic normal has no station X component");
        assertClose(0.0D, transformedNormal.z(), "ecliptic normal has no station Z component");
        String orbitType = Files.readString(Path.of(
                "src/main/resources/data/wildfires/dimension_type/orbit.json"));
        assertTrue(orbitType.contains("\"has_skylight\": true"),
                "orbit dimension propagates real sky light onto station blocks");
        assertTrue(!orbitType.contains("\"fixed_time\""),
                "orbit is not globally locked to noon; station-local NTM illumination drives its lightmap");
    }

    private static void sunIsASeparateFlatNtmLayerAndLightsPlanetsFromSystemCenter() {
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state(), 0.0D);
        assertTrue(frame.bodies().stream().noneMatch(body -> body.body().equals(SUN)),
                "Sun never enters square-body list");
        assertEquals(id("textures/third_party/ntm_space/kerbol.png"), NtmOrbitSkyRenderer.SUN,
                "flat NTM sun texture");
        assertEquals(id("textures/third_party/ntm_space/sunspike.png"), NtmOrbitSkyRenderer.SUN_SPIKE,
                "NTM corona texture");
        assertVector(new CelestialVector(0.0D, -1.0D, 0.0D),
                body(frame, EARTH).incomingLightDirection(), "Earth incoming center-star light");
        assertVector(new CelestialVector(0.0D, 1.0D, 0.0D),
                body(frame, MARS).incomingLightDirection(), "Mars incoming center-star light");
        assertClose(OrbitVisualRules.NTM_SUN_RENDER_SCALE,
                frame.sun().apparentSize() / OrbitVisualRules.apparentSize(1.0D, 100.0D),
                "NTM Sun size is calculated at the currently orbited body");
        assertClose(OrbitVisualRules.renderDistance(frame.sun().distance()),
                frame.sun().renderDistance(), "Sun participates in compressed physical depth ordering");
        try {
            String renderer = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/NtmOrbitSkyRenderer.java"));
            assertTrue(!renderer.contains("drawBlackMask")
                            && renderer.indexOf("drawBillboardAt(SUN,")
                            < renderer.indexOf("drawBillboardAt(SUN_SPIKE,"),
                    "modern NTM Sun uses one alpha photosphere then an additive flare without a black mask");
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot inspect NTM Sun renderer", exception);
        }
    }

    private static void ntmIlluminationUsesRotatedCubeOcclusion() {
        OrbitVisualRules.Frame sunward = OrbitVisualRules.frame(
                context(EARTH, Optional.empty()), state(), 0.0D);
        OrbitVisualRules.Frame eclipse = OrbitVisualRules.frame(
                context(EARTH, Optional.empty()), state(), OrbitVisualRules.VISUAL_ORBIT_TICKS * 0.5D);
        assertTrue(sunward.illumination().occlusion() > 0.0D
                        && sunward.illumination().occlusion() < 1.0D,
                "the foreground Moon produces a partial eclipse while Earth remains behind the observer");
        assertClose(1.0D, eclipse.illumination().occlusion(),
                "square Earth fully covers the physical square Sun from the night-side orbit");
        assertClose(1.0D - eclipse.illumination().occlusion(),
                eclipse.illumination().sunlight(), "NTM sunlight is one minus eclipse amount");
        assertClose(1.0D, eclipse.illumination().starVisibility(),
                "NTM eclipse amount makes the orbit stars fully visible");
        assertTrue(sunward.illumination().starVisibility() >= sunward.illumination().occlusion()
                        && sunward.illumination().starVisibility() > 0.20D
                        && sunward.illumination().starVisibility() < 0.30D,
                "NTM Kerbin-scaled distance and eclipse produce a small Earth-orbit star floor");

        CelestialVector sun = new CelestialVector(0.0D, 0.0D, 1.0D);
        double faceOn = OrbitVisualRules.projectedCubeCoverage(sun, 0.1D,
                new CelestialVector(0.0D, 0.0D, 10.0D), 0.5D,
                new org.joml.Quaternionf(), new CelestialVector(0.0D, 1.0D, 0.0D));
        double cornerOn = OrbitVisualRules.projectedCubeCoverage(sun, 0.1D,
                new CelestialVector(0.0D, 0.0D, 10.0D), 0.5D,
                new org.joml.Quaternionf().rotateY((float) Math.toRadians(45.0D)),
                new CelestialVector(0.0D, 1.0D, 0.0D));
        double offset = OrbitVisualRules.projectedCubeCoverage(sun, 0.1D,
                new CelestialVector(1.7D, 0.0D, 10.0D), 0.5D,
                new org.joml.Quaternionf(), new CelestialVector(0.0D, 1.0D, 0.0D));
        assertTrue(faceOn > 0.20D && faceOn < 0.30D,
                "face-on cube produces its real square partial coverage");
        assertTrue(cornerOn > faceOn,
                "rotated cube silhouette, not a fixed billboard square, controls eclipse coverage");
        assertClose(0.0D, offset, "separated foreground cube does not eclipse the Sun");
    }

    private static void orbitalCloudMaterialMovesWithoutRotatingShell() {
        CelestialVisualDefinition.CloudLayer clouds = new CelestialVisualDefinition.CloudLayer(
                true, CelestialVisualDefinition.CloudMapping.PROCEDURAL, Optional.empty(), true,
                1.012D, 0.7D, new CelestialVisualDefinition.Color(1.0D, 1.0D, 1.0D),
                1.08D, CelestialVisualDefinition.Axis.UP, 0.0D, 0.45D);
        assertClose(0.0D, OrbitVisualRules.cloudTexturePhase(clouds, 0.0D),
                "cloud material starts at configured phase");
        assertClose(0.25D, OrbitVisualRules.cloudTexturePhase(clouds, 6_480.0D),
                "cloud material phase advances without rotating cubic shell geometry");
        OrbitProceduralTexture.Rgba sample = OrbitProceduralTexture.cloud(EARTH,
                new GenesisCubeAtlasLayout.Direction(1.0D, 0.0D, 0.0D));
        assertTrue(sample.alpha() >= 0.0D && sample.alpha() <= 1.0D,
                "procedural cloud alpha is finite and bounded");
        assertTrue(hasDeclaredMethod(OrbitSkyRenderer.class, "drawClouds"),
                "orbit renderer contains an explicit orbital cloud shell pass");
        try {
            String renderer = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
            int cloudPassStart = renderer.indexOf("private static void drawClouds");
            int cloudPassEnd = renderer.indexOf("private static void drawCloudShell", cloudPassStart);
            assertTrue(cloudPassStart >= 0 && cloudPassEnd > cloudPassStart,
                    "cloud pass source section can be inspected");
            String cloudPass = renderer.substring(cloudPassStart, cloudPassEnd);
            assertTrue(!cloudPass.contains("mulPose("),
                    "cubic cloud shell never receives a relative geometry rotation");
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot inspect cloud shell renderer", exception);
        }
    }

    private static void copiedNtmTexturesMatchRecordedHashes() throws Exception {
        assertHash("night.png", "8a76403ad140614beea2f0d044fcb36f9be8603b3affcc4f265b36fd373217dc");
        assertHash("kerbol.png", "6a59dde88aa746b20df95ae46525825926800c8099b1655942b7f1b23a865431");
        assertHash("sunspike.png", "7c5f485143aac4c4618dc0509ff8b08d470e46509a006d75cf78266c418b1450");
        assertHash("planet.png", "b8f4b7d1966fc7ed7df8f900a8f3b036b190df197c6b9092e17715cd6cd390f5");
    }

    private static void adaptedShadersDeclareAlphaAndValidMatrices() throws Exception {
        Path root = Path.of("src/main/resources/assets/wildfires/shaders/core");
        String surface = Files.readString(root.resolve("genesis_planet_textured.fsh"));
        String atmosphere = Files.readString(root.resolve("genesis_planet_atmosphere.fsh"));
        assertTrue(surface.contains("uniform float Alpha"), "surface transition alpha uniform");
        assertTrue(surface.contains("0.10 + diffuse"),
                "Genesis surface retains its readable 0.1 ambient instead of crushing the night side");
        assertTrue(atmosphere.contains("uniform float Alpha"), "atmosphere transition alpha uniform");
        assertTrue(atmosphere.contains("uniform vec3 DayColor")
                        && atmosphere.contains("uniform vec3 SunsetColor")
                        && atmosphere.contains("uniform vec3 NightColor")
                        && atmosphere.contains("uniform vec2 LimbParameters")
                        && atmosphere.contains("uniform vec2 OpacityExposure"),
                "square atmosphere exposes palette, limb and opacity tuning without changing geometry");
        assertTrue(surface.contains("uniform vec4 LayerColor"), "surface shader supports cloud tint");
        assertTrue(!surface.contains("gl_FragDepth"), "surface does not force far depth");
        assertTrue(!atmosphere.contains("vec3(1.0, 0.34, 0.10)"),
                "orbit atmosphere has no invented global orange sunset composite");
        String identity = "[1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,0.0,1.0]";
        for (String file : List.of("genesis_planet_textured.json", "genesis_planet_atmosphere.json")) {
            String json = Files.readString(root.resolve(file)).replaceAll("\\s+", "");
            assertTrue(json.contains("\"name\":\"ProjMat\",\"type\":\"matrix4x4\",\"count\":16,\"values\":"
                    + identity), file + " has a valid identity fallback projection");
        }
        String mesh = Files.readString(Path.of(
                "src/main/java/first/wildfires/thirdparty/genesisadapt/GenesisPlanetMesh.java"));
        assertTrue(mesh.contains("x * inverseLength") && mesh.contains("surfaceFace(builder"),
                "Genesis surface retains exact winding and normalized cube-corner lighting");
    }

    private static void assertPlane(NtmOrbitSkyRenderer.NightFace face, char axis,
                                    int expected, String name) {
        for (NtmOrbitSkyRenderer.NightVertex vertex : List.of(
                face.first(), face.second(), face.third(), face.fourth())) {
            int actual = switch (axis) {
                case 'x' -> vertex.x();
                case 'y' -> vertex.y();
                case 'z' -> vertex.z();
                default -> throw new IllegalArgumentException("axis");
            };
            assertEquals(expected, actual, name);
        }
    }

    private static NtmOrbitSkyRenderer.NightVertex vertex(int x, int y, int z) {
        return new NtmOrbitSkyRenderer.NightVertex(x, y, z);
    }

    private static boolean hasDeclaredMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static CelestialState state() {
        CelestialBodyState sun = body(SUN, null, new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D);
        CelestialBodyState moon = body(MOON, EARTH, new CelestialVector(0.3844D, 0.0D, 0.0D),
                0.001737D);
        CelestialBodyState mars = body(MARS, SUN, new CelestialVector(200.0D, 0.0D, 0.0D),
                0.003396D);
        return new CelestialState(0.0D, 0.0D, 0.0D, 0L, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D), List.of(mars), 0,
                0.0D, 0.0D, SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE,
                0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialBodyState body(ResourceLocation id, ResourceLocation parent,
                                           CelestialVector position, double radius) {
        double distance = position.length();
        return new CelestialBodyState(id, parent, position, position.normalized(), distance,
                Math.atan(radius / distance), 0.0D, 1.0D, 1.0D, 0.0D);
    }

    private static CelestialVisualDefinition visual(Optional<ResourceLocation> atlas,
                                                    ResourceLocation layout) {
        return new CelestialVisualDefinition(atlas, true, layout, id("cube"),
                CelestialVisualDefinition.Atmosphere.NONE, CelestialVisualDefinition.CloudLayer.NONE);
    }

    private static ObservationJourney journey(StationJourneyPhase phase, long start, long duration) {
        return new ObservationJourney(EARTH, MARS, phase, start, duration);
    }

    private static ObservationContext context(ResourceLocation current,
                                              Optional<ObservationJourney> journey) {
        return new ObservationContext(UUID.fromString("60000000-0000-0000-0000-000000000001"),
                1L, new StationRegion(1, 0), current, StationStatus.ACTIVE, journey, 1L);
    }

    private static OrbitVisualRules.BodyLayer body(OrbitVisualRules.Frame frame, ResourceLocation id) {
        return frame.bodies().stream().filter(body -> body.body().equals(id)).findFirst().orElseThrow();
    }

    private static CelestialVector viewedDirection(OrbitVisualRules.Frame frame, ResourceLocation id) {
        CelestialVector direction = body(frame, id).direction();
        org.joml.Vector3f viewed = new org.joml.Vector3f(
                (float) direction.x(), (float) direction.y(), (float) direction.z());
        OrbitVisualRules.stationViewOrientation(frame.viewRotationRadians()).transform(viewed);
        return new CelestialVector(viewed.x, viewed.y, viewed.z).normalized();
    }

    private static GenesisCubeAtlasLayout.Direction edge(GenesisCubeAtlasLayout.Face face,
                                                         int edge, double value) {
        return switch (edge) {
            case 0 -> GenesisCubeAtlasLayout.direction(face, 0.0D, value);
            case 1 -> GenesisCubeAtlasLayout.direction(face, 1.0D, value);
            case 2 -> GenesisCubeAtlasLayout.direction(face, value, 0.0D);
            case 3 -> GenesisCubeAtlasLayout.direction(face, value, 1.0D);
            default -> throw new IllegalArgumentException("edge");
        };
    }

    private static GenesisCubeAtlasLayout.Direction matchingEdge(GenesisCubeAtlasLayout.Face source,
                                                                 GenesisCubeAtlasLayout.Direction direction,
                                                                 double value) {
        for (GenesisCubeAtlasLayout.Face candidate : GenesisCubeAtlasLayout.FACES) {
            if (candidate.equals(source)) {
                continue;
            }
            for (int edge = 0; edge < 4; edge++) {
                for (double candidateValue : new double[]{value, 1.0D - value}) {
                    GenesisCubeAtlasLayout.Direction sample = edge(candidate, edge, candidateValue);
                    if (distanceSquared(direction, sample) < 1.0E-20D) {
                        return sample;
                    }
                }
            }
        }
        throw new AssertionError("Genesis face edge has no matching neighbor: " + source.name());
    }

    private static double distanceSquared(GenesisCubeAtlasLayout.Direction first,
                                          GenesisCubeAtlasLayout.Direction second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return x * x + y * y + z * z;
    }

    private static void assertHash(String file, String expected) throws Exception {
        Path path = Path.of("src/main/resources/assets/wildfires/textures/third_party/ntm_space")
                .resolve(file);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder actual = new StringBuilder(64);
        for (byte value : digest) {
            actual.append(String.format("%02x", value & 0xFF));
        }
        assertEquals(expected, actual.toString(), file + " SHA-256");
    }

    private static void assertVector(CelestialVector expected, CelestialVector actual, String name) {
        assertClose(expected.x(), actual.x(), name + " x");
        assertClose(expected.y(), actual.y(), name + " y");
        assertClose(expected.z(), actual.z(), name + " z");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String name) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(name + " threw " + throwable, throwable);
        }
        throw new AssertionError(name + " did not throw " + expected.getSimpleName());
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) {
            throw new AssertionError(name);
        }
    }

    private static void assertClose(double expected, double actual, String name) {
        if (!Double.isFinite(expected) || !Double.isFinite(actual)
                || Math.abs(expected - actual) > 1.0E-9D) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + " but was " + actual);
        }
    }
}
