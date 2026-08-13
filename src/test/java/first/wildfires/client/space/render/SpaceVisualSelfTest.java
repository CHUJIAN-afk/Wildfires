package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.api.celestial.SolarEclipseState;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.capsule.ReturnCapsuleState;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import first.wildfires.thirdparty.genesisadapt.GenesisCubeAtlasLayout;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Plain-Java P6 contract checks; no render thread or OpenGL context is required. */
public final class SpaceVisualSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation SUN = id("sun");
    private static final ResourceLocation MOON = id("moon");
    private static final ResourceLocation MARS = id("mars");
    private static final ResourceLocation JUPITER = id("jupiter");
    private static final ResourceLocation IO = id("io");
    private static final ResourceLocation EUROPA = id("europa");
    private static final ResourceLocation GANYMEDE = id("ganymede");

    private SpaceVisualSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        genesisAtlasUsesExactFaceContract();
        genesisDirectionsAndFallbackSurfaceAreSeamContinuous();
        ntmNightAtlasUsesItsOwnExactFaceContract();
        ntmRelativisticSamplingCrossesAllTwelveEdgesContinuously();
        surfaceTexturePolicyUsesOnlyGenesisCubemapsOrFallback();
        stationObserverUsesUnifiedRealEphemeris();
        ntmJourneyPhasesKeepTheirOrbitAndTransferSemantics();
        transferArcClearsAnOriginThatBlocksTheTarget();
        localParentMoonAndSiblingMoonTransfersClearEveryCube();
        earthMoonTransferUsesAuthoritativeLunarEphemeris();
        acceleratedEarthMoonTransfersClearBothMovingCubes();
        acceleratedCalendarLocalTransferLocksDepartureAndInterceptsMovingMoon();
        acceleratedLocalTransferClearsEveryMovingMoonAndDelayedPhasePacket();
        everyBuiltInLocalTransferTracksTheAuthoritativeEphemeris();
        ntmPointAndCubeLodUsesRecordedThresholds();
        compressedDepthPreservesOcclusionAndAngularSize();
        orbitFrameAndPlanetRotationAreTimeDriven();
        ntmSunSizeUsesBodyEndpointsInsteadOfTransferChordDistance();
        ntmIlluminationUsesRotatedCubeOcclusion();
        satelliteShadowsUseBoundedThreeDimensionalCubeCasters();
        sunIsASeparateFlatNtmLayerAndLightsPlanetsFromSystemCenter();
        orbitalCloudMaterialMovesWithoutRotatingShell();
        vacuumBackgroundDepthAndBlockLightRemainIndependent();
        developmentClockDrivesSkyAndLightmapTogether();
        copiedNtmTexturesMatchRecordedHashes();
        adaptedShadersDeclareAlphaAndValidMatrices();
        relativisticJumpMathIsFiniteAndDirectional();
        reusableCapsuleTransitionIsAContinuousVacuumFade();
        System.out.println("SpaceVisualSelfTest passed");
    }

    private static void reusableCapsuleTransitionIsAContinuousVacuumFade() throws Exception {
        Class<?> overlay = Class.forName("first.wildfires.client.space.ReturnCapsuleTransitionOverlay");
        Method opacity = overlay.getDeclaredMethod("opacity",
                ReturnCapsuleState.class, int.class, float.class);
        opacity.setAccessible(true);

        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LANDED, 0),
                "landed capsule has no transition cover");
        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LAUNCHING, 84),
                "surface launch fade starts transparent");
        assertClose(0.5D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LAUNCHING, 92),
                "surface launch midpoint is continuous");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LAUNCHING, 100),
                "surface launch reaches black vacuum before dimension transfer");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.ASCENT_TRANSITION, 0),
                "ascent dimension handoff remains fully hidden");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.ORBIT_INSERTION, 0),
                "orbit insertion begins under the same full cover");
        assertClose(0.5D, overlayOpacity(opacity, ReturnCapsuleState.ORBIT_INSERTION, 10),
                "orbit insertion reveals space continuously");
        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.ORBIT_INSERTION, 20),
                "orbit insertion finishes fully visible");

        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.STATION_UNDOCKING, 24),
                "undocking deorbit fade starts transparent");
        assertClose(0.5D, overlayOpacity(opacity, ReturnCapsuleState.STATION_UNDOCKING, 32),
                "undocking deorbit midpoint is continuous");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.STATION_UNDOCKING, 40),
                "undocking reaches full cover");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.DEORBIT, 0),
                "surface dimension handoff remains fully hidden");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.REENTRY, 0),
                "reentry starts under the same full cover");
        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.REENTRY, 50),
                "mid-reentry is fully visible");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.REENTRY, 100),
                "reentry hides the handoff into terminal landing presentation");
        assertClose(1.0D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LANDING, 0),
                "terminal landing starts under full cover");
        assertClose(0.0D, overlayOpacity(opacity, ReturnCapsuleState.SURFACE_LANDING, 20),
                "terminal landing reveals the surface continuously");
    }

    private static double overlayOpacity(Method method, ReturnCapsuleState state, int ticks)
            throws ReflectiveOperationException {
        return ((Float) method.invoke(null, state, ticks, 0.0F)).doubleValue();
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

    private static void ntmRelativisticSamplingCrossesAllTwelveEdgesContinuously() throws Exception {
        int edgeCount = 0;
        double epsilon = 1.0E-6D;
        for (int firstAxis = 0; firstAxis < 3; firstAxis++) {
            for (int secondAxis = firstAxis + 1; secondAxis < 3; secondAxis++) {
                int freeAxis = 3 - firstAxis - secondAxis;
                for (int firstSign : new int[]{-1, 1}) {
                    for (int secondSign : new int[]{-1, 1}) {
                        edgeCount++;
                        for (int step = 1; step < 8; step++) {
                            double along = -1.0D + step * 0.25D;
                            double[] firstSide = new double[3];
                            firstSide[firstAxis] = firstSign * (1.0D + epsilon);
                            firstSide[secondAxis] = secondSign;
                            firstSide[freeAxis] = along;
                            double[] secondSide = firstSide.clone();
                            secondSide[firstAxis] = firstSign;
                            secondSide[secondAxis] = secondSign * (1.0D + epsilon);

                            NtmAtlasSample first = ntmAtlasSample(firstSide);
                            NtmAtlasSample second = ntmAtlasSample(secondSide);
                            assertTrue(first.face() != second.face(),
                                    "edge probes select the two neighboring cube faces");
                            assertDirection(firstSide, ntmDirection(first),
                                    "first face preserves edge ray orientation");
                            assertDirection(secondSide, ntmDirection(second),
                                    "second face preserves edge ray orientation");
                            assertDirection(firstSide, secondSide,
                                    3.0E-6D, "ray direction stays continuous across cube edge");
                        }
                    }
                }
            }
        }
        assertEquals(12, edgeCount, "all twelve cube edges are covered");

        BufferedImage atlas = ImageIO.read(Path.of(
                "src/main/resources/assets/wildfires/textures/third_party/ntm_space/night.png").toFile());
        assertEquals(2304, atlas.getWidth(), "NTM night atlas width");
        assertEquals(1536, atlas.getHeight(), "NTM night atlas height");
        assertEquals(768, atlas.getWidth() / 3, "NTM night face pixel width");
        assertEquals(768, atlas.getHeight() / 2, "NTM night face pixel height");
        String fragment = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.fsh"));
        assertTrue(fragment.contains("textureSize(Sampler0, 0)")
                        && fragment.contains("0.5 / cellSize")
                        && !fragment.contains("vec2(0.001)"),
                "relativistic atlas sampling uses a real half-texel face inset");
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

    private static void transferArcClearsAnOriginThatBlocksTheTarget() {
        CelestialState blocked = stateWithMars(new CelestialVector(-200.0D, 0.0D, 0.0D));
        ObservationJourney cruise = journey(StationJourneyPhase.CRUISE, 0L, 1_000L);
        OrbitVisualRules.Frame midpoint = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), blocked, 500.0D);
        assertTrue(midpoint.observerPosition().length() > 0.006371D * 4.0D,
                "transfer arc clears the origin cube instead of passing through its silhouette");
        assertTrue(Math.abs(midpoint.observerPosition().y()) > 0.005D,
                "a target behind the origin receives a visible lateral transfer detour");
        for (int tick = 0; tick <= 1_000; tick += 5) {
            CelestialVector observer = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)),
                    blocked, tick).observerPosition();
            assertTrue(outsideCube(observer, 0.006371D),
                    "transfer arc entered the origin cube at cruise tick " + tick + ": " + observer);
            assertTrue(outsideCube(observer.subtract(new CelestialVector(-200.0D, 0.0D, 0.0D)), 0.003396D),
                    "transfer arc entered the target cube at cruise tick " + tick + ": " + observer);
        }
    }

    private static void localParentMoonAndSiblingMoonTransfersClearEveryCube() {
        CelestialState jovian = stateWithJovianMoons();
        assertLocalTransferClear(jovian, JUPITER, IO, new CelestialVector(20.0D, 0.0D, 0.0D),
                0.071492D, new CelestialVector(20.4217D, 0.0D, 0.0D), 0.0018216D,
                "Jupiter to Io");
        assertLocalTransferClear(jovian, IO, JUPITER, new CelestialVector(20.4217D, 0.0D, 0.0D),
                0.0018216D, new CelestialVector(20.0D, 0.0D, 0.0D), 0.071492D,
                "Io to Jupiter");
        assertLocalTransferClear(jovian, IO, EUROPA, new CelestialVector(20.4217D, 0.0D, 0.0D),
                0.0018216D, new CelestialVector(20.0D, 0.669151D, 0.0D), 0.0015608D,
                "Io to Europa");
        assertLocalTransferClear(jovian, EUROPA, IO, new CelestialVector(20.0D, 0.669151D, 0.0D),
                0.0015608D, new CelestialVector(20.4217D, 0.0D, 0.0D), 0.0018216D,
                "Europa to Io");

        // The old missing mechanism escaped above the complete moon system. A local transfer must
        // remain a bounded common-primary orbit whose normal displacement is set by the route, not
        // by the outermost satellite's orbital radius.
        ObservationJourney ioToEuropa = new ObservationJourney(IO, EUROPA,
                StationJourneyPhase.CRUISE, 0L, 1_000L);
        double maximumNormal = 0.0D;
        for (int tick = 0; tick <= 1_000; tick++) {
            CelestialVector observer = OrbitVisualRules.frame(
                    context(IO, Optional.of(ioToEuropa)), jovian, tick).observerPosition();
            maximumNormal = Math.max(maximumNormal, Math.abs(observer.z()));
        }
        assertTrue(maximumNormal < 0.35D,
                "sibling-moon transfer remains in the local common-primary orbital neighbourhood");
    }

    private static void assertLocalTransferClear(CelestialState state,
                                                 ResourceLocation fromId,
                                                 ResourceLocation toId,
                                                 CelestialVector fromCenter,
                                                 double fromRadius,
                                                 CelestialVector toCenter,
                                                 double toRadius,
                                                 String name) {
        ObservationJourney cruise = new ObservationJourney(fromId, toId,
                StationJourneyPhase.CRUISE, 0L, 1_000L);
        OrbitVisualRules.Frame start = OrbitVisualRules.frame(
                context(fromId, Optional.of(cruise)), state, 0.0D);
        OrbitVisualRules.Frame end = OrbitVisualRules.frame(
                context(fromId, Optional.of(cruise)), state, 1_000.0D);
        assertVector(OrbitVisualRules.frame(context(fromId, Optional.empty()), state, 0.0D)
                .observerPosition(), start.observerPosition(), name + " start continuity");
        assertVector(OrbitVisualRules.frame(context(toId, Optional.empty()), state, 1_000.0D)
                .observerPosition(), end.observerPosition(), name + " end continuity");
        for (int tick = 1; tick < 1_000; tick++) {
            CelestialVector observer = OrbitVisualRules.frame(
                    context(fromId, Optional.of(cruise)), state, tick).observerPosition();
            assertTrue(outsideCube(observer.subtract(new CelestialVector(20.0D, 0.0D, 0.0D)),
                            0.071492D),
                    name + " entered the Jupiter cube at cruise tick " + tick);
            assertTrue(outsideCube(observer.subtract(fromCenter), fromRadius),
                    name + " entered the origin cube at cruise tick " + tick);
            assertTrue(outsideCube(observer.subtract(toCenter), toRadius),
                    name + " entered the target cube at cruise tick " + tick + ": " + observer);
            assertTrue(outsideCube(observer.subtract(new CelestialVector(20.4217D, 0.0D, 0.05D)),
                            0.0026341D),
                    name + " entered the non-target Ganymede cube at cruise tick " + tick
                            + ": " + observer);
        }
    }

    private static boolean outsideCube(CelestialVector point, double halfSize) {
        return Math.abs(point.x()) > halfSize || Math.abs(point.y()) > halfSize
                || Math.abs(point.z()) > halfSize;
    }

    private static void acceleratedCalendarLocalTransferLocksDepartureAndInterceptsMovingMoon() {
        double rate = 1_200.0D;
        long duration = 600L;
        ObservationJourney cruise = new ObservationJourney(IO, EUROPA,
                StationJourneyPhase.CRUISE, 0L, duration);
        CelestialState startState = stateWithOrbitingJovianMoons(0.0D);
        CelestialState oneTickState = stateWithOrbitingJovianMoons(rate);
        CelestialState endState = stateWithOrbitingJovianMoons(rate * duration);
        ObservationContext active = context(IO, Optional.of(cruise));

        CelestialVector start = OrbitVisualRules.frame(active, startState, 0.0D, 0.0D, rate)
                .observerPosition();
        CelestialVector oneTick = OrbitVisualRules.frame(active, oneTickState, 1.0D, rate, rate)
                .observerPosition();
        CelestialVector end = OrbitVisualRules.frame(active, endState, duration,
                rate * duration, rate).observerPosition();
        CelestialVector expectedStart = OrbitVisualRules.frame(context(IO, Optional.empty()),
                startState, 0.0D, 0.0D, rate).observerPosition();
        CelestialVector expectedEnd = OrbitVisualRules.frame(context(EUROPA, Optional.empty()),
                endState, duration, rate * duration, rate).observerPosition();

        assertVector(expectedStart, start, "accelerated local transfer starts on the departure orbit");
        assertVector(expectedEnd, end, "accelerated local transfer intercepts the moving target orbit");
        assertTrue(oneTick.subtract(start).length() < 0.01D,
                "departure gate is locked instead of being dragged by the source moon each frame");
        for (int tick = 1; tick < duration; tick += 3) {
            double calendar = rate * tick;
            CelestialState state = stateWithOrbitingJovianMoons(calendar);
            CelestialVector observer = OrbitVisualRules.frame(active, state, tick, calendar, rate)
                    .observerPosition();
            assertTrue(Double.isFinite(observer.x()) && Double.isFinite(observer.y())
                            && Double.isFinite(observer.z()),
                    "accelerated local transfer remains finite at tick " + tick);
            assertTrue(outsideCube(observer.subtract(new CelestialVector(20.0D, 0.0D, 0.0D)),
                            0.071492D),
                    "accelerated local transfer entered Jupiter at tick " + tick);
        }
    }

    private static void earthMoonTransferUsesAuthoritativeLunarEphemeris() {
        double rate = 1_200.0D;
        long duration = 600L;
        CelestialState startState = stateWithMoonAt(0.0D);
        CelestialState endState = stateWithMoonAt(rate * duration);
        ObservationJourney cruise = new ObservationJourney(EARTH, MOON,
                StationJourneyPhase.CRUISE, 0L, duration);
        CelestialVector end = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)),
                endState, duration, rate * duration, rate, 8).observerPosition();
        CelestialVector expected = OrbitVisualRules.frame(context(MOON, Optional.empty()),
                endState, duration, rate * duration, rate, 8).observerPosition();
        assertVector(expected, end,
                "Earth-Moon transfer intercepts the unified inclined lunar ephemeris");

        CelestialVector start = OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)),
                startState, 0.0D, 0.0D, rate, 8).observerPosition();
        CelestialVector expectedStart = OrbitVisualRules.frame(context(EARTH, Optional.empty()),
                startState, 0.0D, 0.0D, rate, 8).observerPosition();
        assertVector(expectedStart, start,
                "Earth-Moon transfer locks the departure orbit before lunar motion");
    }

    private static void acceleratedLocalTransferClearsEveryMovingMoonAndDelayedPhasePacket() {
        double rate = 1_200.0D;
        long duration = 600L;
        for (ResourceLocation[] route : new ResourceLocation[][]{
                {JUPITER, IO}, {IO, JUPITER}, {IO, EUROPA}, {EUROPA, IO}
        }) {
            ObservationJourney cruise = new ObservationJourney(route[0], route[1],
                    StationJourneyPhase.CRUISE, 0L, duration);
            ObservationContext active = context(route[0], Optional.of(cruise));
            for (int tick = 1; tick < duration; tick++) {
                double calendar = rate * tick;
                CelestialState state = stateWithOrbitingJovianMoons(calendar);
                CelestialVector observer = OrbitVisualRules.frame(active, state, tick,
                        calendar, rate).observerPosition();
                assertOutsideMovingBody(observer, state, JUPITER, 0.071492D,
                        route[0] + " -> " + route[1] + " Jupiter", tick);
                assertOutsideMovingBody(observer, state, IO, 0.0018216D,
                        route[0] + " -> " + route[1] + " Io", tick);
                assertOutsideMovingBody(observer, state, EUROPA, 0.0015608D,
                        route[0] + " -> " + route[1] + " Europa", tick);
                assertOutsideMovingBody(observer, state, GANYMEDE, 0.0026341D,
                        route[0] + " -> " + route[1] + " Ganymede", tick);
            }

            // Simulate a one-tick-late ARRIVING packet. A stale CRUISE snapshot must already
            // resolve to the same live target orbit that the new phase will use.
            double delayedCalendar = rate * (duration + 1L);
            CelestialState delayedState = stateWithOrbitingJovianMoons(delayedCalendar);
            CelestialVector staleCruise = OrbitVisualRules.frame(active, delayedState,
                    duration + 1.0D, delayedCalendar, rate).observerPosition();
            CelestialVector liveTarget = OrbitVisualRules.frame(context(route[1], Optional.empty()),
                    delayedState, duration + 1.0D, delayedCalendar, rate).observerPosition();
            assertVector(liveTarget, staleCruise,
                    route[0] + " -> " + route[1] + " delayed CRUISE packet handoff");
        }
    }

    private static void everyBuiltInLocalTransferTracksTheAuthoritativeEphemeris() {
        double startCalendar = 1_234_567.0D;
        double rate = 1_200.0D;
        long duration = 600L;
        List<ResourceLocation[]> routes = allBuiltInLocalTransferPairs();
        assertEquals(28, routes.size(), "complete built-in directed local-transfer set");
        for (ResourceLocation[] route : routes) {
            ObservationJourney cruise = new ObservationJourney(route[0], route[1],
                    StationJourneyPhase.CRUISE, 0L, duration);
            ObservationContext active = context(route[0], Optional.of(cruise));
            CelestialState start = authoritativeStateAt(startCalendar);
            CelestialVector departure = OrbitVisualRules.frame(active, start, 0.0D,
                    startCalendar, rate, 8).observerPosition();
            CelestialVector stableDeparture = OrbitVisualRules.frame(
                    context(route[0], Optional.empty()), start, 0.0D,
                    startCalendar, rate, 8).observerPosition();
            assertVector(stableDeparture, departure,
                    route[0] + " -> " + route[1] + " authoritative departure anchor");

            // Do not sample exactly at the end: the intentional stale-CRUISE packet guard would
            // return the live target orbit directly and bypass the planned intercept endpoint.
            double nearEndTick = duration - 1.0E-7D;
            double nearEndCalendar = startCalendar + rate * nearEndTick;
            CelestialState nearEnd = authoritativeStateAt(nearEndCalendar);
            CelestialVector planned = OrbitVisualRules.frame(active, nearEnd, nearEndTick,
                    nearEndCalendar, rate, 8).observerPosition();
            CelestialVector liveTarget = OrbitVisualRules.frame(
                    context(route[1], Optional.empty()), nearEnd, nearEndTick,
                    nearEndCalendar, rate, 8).observerPosition();
            assertTrue(planned.subtract(liveTarget).length() < 1.0E-5D,
                    route[0] + " -> " + route[1]
                            + " planned endpoint missed the authoritative target ephemeris: "
                            + planned.subtract(liveTarget).length());

            ResourceLocation primary = commonPrimary(route[0], route[1]);
            for (int step = 1; step < 48; step++) {
                double gameTick = duration * step / 48.0D;
                double calendar = startCalendar + rate * gameTick;
                CelestialState state = authoritativeStateAt(calendar);
                CelestialVector observer = OrbitVisualRules.frame(active, state, gameTick,
                        calendar, rate, 8).observerPosition();
                for (CelestialBodyState body : localSystemBodies(state, primary)) {
                    double radius = Math.tan(body.angularRadiusRadians()) * body.distance();
                    assertTrue(outsideCube(observer.subtract(body.geocentricPosition()), radius),
                            route[0] + " -> " + route[1] + " entered " + body.id()
                                    + " at sampled cruise tick " + gameTick);
                }
                if (primary.equals(EARTH)) {
                    assertTrue(outsideCube(observer, 0.006371D),
                            route[0] + " -> " + route[1]
                                    + " entered Earth at sampled cruise tick " + gameTick);
                }
            }
        }
    }

    private static List<ResourceLocation[]> allBuiltInLocalTransferPairs() {
        Map<ResourceLocation, List<ResourceLocation>> children = new HashMap<>();
        children.computeIfAbsent(EARTH, ignored -> new ArrayList<>()).add(MOON);
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() != null) {
                children.computeIfAbsent(body.parent().id(), ignored -> new ArrayList<>())
                        .add(body.id());
            }
        }
        List<ResourceLocation[]> pairs = new ArrayList<>();
        children.forEach((parent, moons) -> {
            for (ResourceLocation moon : moons) {
                pairs.add(new ResourceLocation[]{parent, moon});
                pairs.add(new ResourceLocation[]{moon, parent});
            }
            for (ResourceLocation origin : moons) {
                for (ResourceLocation target : moons) {
                    if (!origin.equals(target)) {
                        pairs.add(new ResourceLocation[]{origin, target});
                    }
                }
            }
        });
        return List.copyOf(pairs);
    }

    private static ResourceLocation commonPrimary(ResourceLocation from, ResourceLocation to) {
        ResourceLocation fromParent = parentOf(from);
        ResourceLocation toParent = parentOf(to);
        if (toParent != null && from.equals(toParent)) return from;
        if (fromParent != null && to.equals(fromParent)) return to;
        if (fromParent != null && fromParent.equals(toParent)) return fromParent;
        throw new AssertionError("not a local transfer: " + from + " -> " + to);
    }

    private static ResourceLocation parentOf(ResourceLocation body) {
        if (body.equals(MOON)) return EARTH;
        CelestialBodies definition = CelestialBodies.byId(body);
        return definition == null || definition.parent() == null ? null : definition.parent().id();
    }

    private static List<CelestialBodyState> localSystemBodies(CelestialState state,
                                                              ResourceLocation primary) {
        List<CelestialBodyState> bodies = new ArrayList<>();
        if (primary.equals(EARTH)) {
            bodies.add(state.moon());
        }
        state.orbitingBodies().stream()
                .filter(body -> body.id().equals(primary) || primary.equals(body.parentId()))
                .forEach(bodies::add);
        return List.copyOf(bodies);
    }

    private static CelestialState authoritativeStateAt(double calendarTicks) {
        first.wildfires.celestial.CelestialRuntimeSettings settings =
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT;
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(
                0.0D, 1.0D, calendarTicks, 8,
                settings.resolvedSynodicDays(8), settings.resolvedAnomalisticDays(8),
                settings.nodalYears(), settings.lunarInclinationRadians()));
        CelestialBodyState sun = body(SUN, null, frame.sunGeocentric()
                .scale(settings.planetSettings().earthSemiMajorMillionKm()), 0.69634D);
        CelestialBodyState moon = body(MOON, EARTH, testEquatorialToEcliptic(frame.moonGeocentric())
                .scale(CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM * frame.moonDistance()), 0.001737D);
        List<CelestialBodyState> orbiting = CelestialBodies.calculate(frame,
                CelestialMath.calendarYears(calendarTicks, 8), settings.planetSettings(),
                settings.orbitalPhases());
        return new CelestialState(0.0D, frame.fractionOfDay(), frame.fractionOfYear(),
                (long) calendarTicks, sun, moon, new CelestialVector(0.0D, 1.0D, 0.0D),
                orbiting, 0, 0.0D, 0.0D, SolarEclipseState.NONE, 0.0D,
                LunarEclipseState.NONE, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static void acceleratedEarthMoonTransfersClearBothMovingCubes() {
        double rate = 1_200.0D;
        long duration = 600L;
        for (ResourceLocation[] route : new ResourceLocation[][]{
                {EARTH, MOON}, {MOON, EARTH}
        }) {
            ObservationJourney cruise = new ObservationJourney(route[0], route[1],
                    StationJourneyPhase.CRUISE, 0L, duration);
            ObservationContext active = context(route[0], Optional.of(cruise));
            for (int tick = 1; tick < duration; tick++) {
                double calendar = rate * tick;
                CelestialState state = stateWithMoonAt(calendar);
                CelestialVector observer = OrbitVisualRules.frame(active, state, tick,
                        calendar, rate, 8).observerPosition();
                assertTrue(outsideCube(observer, 0.006371D),
                        route[0] + " -> " + route[1]
                                + " entered the moving Earth cube at tick " + tick);
                assertTrue(outsideCube(observer.subtract(state.moon().geocentricPosition()),
                                0.001737D),
                        route[0] + " -> " + route[1]
                                + " entered the moving Moon cube at tick " + tick);
            }
        }
    }

    private static void assertOutsideMovingBody(CelestialVector observer, CelestialState state,
                                                ResourceLocation id, double halfSize,
                                                String name, int tick) {
        CelestialVector center = state.orbitingBodies().stream()
                .filter(body -> body.id().equals(id)).findFirst().orElseThrow()
                .geocentricPosition();
        assertTrue(outsideCube(observer.subtract(center), halfSize),
                name + " dynamic cube intersection at cruise tick " + tick + ": " + observer);
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
        double previousDistance = Double.POSITIVE_INFINITY;
        for (OrbitVisualRules.BodyLayer layer : frame.bodies()) {
            assertTrue(layer.distance() <= previousDistance,
                    "far-to-near body ordering for " + layer.body());
            previousDistance = layer.distance();
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

    private static void ntmSunSizeUsesBodyEndpointsInsteadOfTransferChordDistance() {
        CelestialState separated = stateWithMars(new CelestialVector(300.0D, 0.0D, 0.0D));
        double source = OrbitVisualRules.frame(context(EARTH, Optional.empty()), separated, 0.0D)
                .sun().apparentSize();
        double target = OrbitVisualRules.frame(context(MARS, Optional.empty()), separated, 0.0D)
                .sun().apparentSize();
        assertTrue(source > target, "the farther target body receives a smaller physical NTM Sun");

        ObservationJourney departing = journey(StationJourneyPhase.DEPARTING, 1_000L, 400L);
        assertClose(source, OrbitVisualRules.frame(context(EARTH, Optional.of(departing)), separated,
                1_200.0D).sun().apparentSize(),
                "DEPARTING keeps the source-body Sun size instead of orbit-camera distance jitter");
        ObservationJourney cruise = journey(StationJourneyPhase.CRUISE, 2_000L, 1_000L);
        assertClose(source, OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), separated,
                2_000.0D).sun().apparentSize(), "CRUISE starts at source-body Sun size");
        assertClose((source + target) * 0.5D,
                OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), separated,
                        2_500.0D).sun().apparentSize(),
                "CRUISE interpolates NTM body endpoint sizes without following the transfer chord");
        assertClose(target, OrbitVisualRules.frame(context(EARTH, Optional.of(cruise)), separated,
                3_000.0D).sun().apparentSize(), "CRUISE ends at target-body Sun size");
        ObservationJourney arriving = journey(StationJourneyPhase.ARRIVING, 3_000L, 400L);
        assertClose(target, OrbitVisualRules.frame(context(MARS, Optional.of(arriving)), separated,
                3_200.0D).sun().apparentSize(),
                "ARRIVING keeps target-body Sun size instead of orbit-camera distance jitter");
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

    private static void vacuumBackgroundDepthAndBlockLightRemainIndependent() throws Exception {
        String effects = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/OrbitDimensionEffects.java"));
        String ntmRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/NtmOrbitSkyRenderer.java"));
        String orbitRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        String lightmapMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/OrbitLightTextureMixin.java"));
        assertTrue(effects.contains("SkyType.NONE")
                        && effects.contains("return Vec3.ZERO"),
                "orbit disables vanilla sky and returns exact NTM black fog/sky colour");
        assertTrue(ntmRenderer.contains("drawVacuumBackdrop(poseStack, projectionMatrix);")
                        && ntmRenderer.contains("blackSkybox.drawWithShader")
                        && ntmRenderer.indexOf("drawVacuumBackdrop(poseStack, projectionMatrix);")
                        < ntmRenderer.indexOf("nightSkybox.drawWithShader"),
                "opaque black vacuum pass is invoked before the additive NTM star atlas");
        assertTrue(ntmRenderer.contains("drawVacuumBackdrop")
                        && orbitRenderer.contains("context == null || celestial == null")
                        && orbitRenderer.contains("NtmOrbitSkyRenderer.drawVacuumBackdrop"),
                "missing orbit context still draws a truthful black vacuum instead of blue clear colour");
        assertTrue(!effects.contains("sunrise") && !effects.contains("sunset")
                        && !ntmRenderer.contains("sunrise") && !ntmRenderer.contains("sunset"),
                "orbit background has no vanilla or invented global sunrise/sunset composite");
        assertTrue(orbitRenderer.contains("frame.bodies()")
                        && orbitRenderer.contains("frame.sun().distance() > body.distance()")
                        && orbitRenderer.contains("clearLayerDepth()"),
                "far-to-near bodies and the Sun use one physical painter/depth ordering");
        assertTrue(lightmapMixin.contains("index = 4")
                        && lightmapMixin.contains("illumination.sunlight()")
                        && !lightmapMixin.contains("LightLayer.BLOCK")
                        && !lightmapMixin.contains("blockLight"),
                "station sunlight replaces only the sky multiplier and never persisted BLOCK light");
    }

    private static void developmentClockDrivesSkyAndLightmapTogether() throws Exception {
        String sky = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        String lightmap = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/OrbitClientIllumination.java"));
        String command = Files.readString(Path.of(
                "src/main/java/first/wildfires/event/forgeEvent/ClientForgeEvent.java"));
        assertTrue(sky.contains("OrbitVisualDebugClock.gameTime()")
                        && lightmap.contains("OrbitVisualDebugClock.gameTime()"),
                "development visual clock drives orbit sky and lightmap from one game time");
        assertTrue(sky.contains("OrbitVisualDebugClock.calendarTicks()")
                        && lightmap.contains("OrbitVisualDebugClock.calendarTicks()")
                        && sky.contains("frame(context, celestial, gameTime, calendarTicks,")
                        && lightmap.contains("frame(context, celestial, gameTime, calendarTicks,"),
                "development visual clock freezes one physical planet/occlusion time for sky and lightmap");
        assertTrue(command.contains("if (!FMLEnvironment.production)")
                        && command.contains("orbitvisualtime"),
                "visual clock command is registered only outside production");
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
        assertTrue(surface.contains("surfaceDirection * ReceiverRadius")
                        && surface.contains("for (int y = -1; y <= 1; ++y)")
                        && surface.contains("for (int x = -1; x <= 1; ++x)")
                        && surface.contains("rayCube(point, direction")
                        && !surface.contains("distance(surfaceDirection.xy"),
                "surface shadow is a 3D rotating-cube projection with fixed 3x3 square-star samples");
        assertTrue(atmosphere.contains("atmosphereDirectVisibility")
                        && atmosphere.contains("shadowInfluence")
                        && atmosphere.contains("* 0.75"),
                "atmosphere shares the geometric shadow but preserves ambient scattering");
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
        String vertex = Files.readString(root.resolve("genesis_planet_textured.vsh"));
        assertTrue(vertex.contains("surfaceDirection = Position")
                        && !vertex.contains("surfaceDirection = normalize(Position)"),
                "shadow receiver uses actual planar cube coordinates across faces, edges and corners");
    }

    private static void relativisticJumpMathIsFiniteAndDirectional() {
        RelativisticVisualRules.State rest = new RelativisticVisualRules.State(0.0D);
        RelativisticVisualRules.State cruise = new RelativisticVisualRules.State(RelativisticVisualRules.CRUISE_BETA);
        CelestialVector forward = new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector side = new CelestialVector(1.0D, 0.0D, 0.0D);
        assertVector(forward, RelativisticVisualRules.aberrate(forward, forward, rest), "rest ray unchanged");
        assertTrue(RelativisticVisualRules.dopplerFactor(forward, forward, cruise) > 1.0D,
                "forward jump ray blue-shifts");
        assertTrue(RelativisticVisualRules.dopplerFactor(forward.scale(-1.0D), forward, cruise) < 1.0D,
                "rear jump ray red-shifts");
        assertClose(cruise.gamma(), RelativisticVisualRules.dopplerFactor(side, forward, cruise),
                "attachment transverse Doppler factor uses the post-aberration angle convention");
        assertTrue(RelativisticVisualRules.dopplerFactor(side, forward, cruise) > 1.0D,
                "transverse source blue-shifts and brightens like the attachment renderer");
        assertTrue(RelativisticVisualRules.aberrate(side, forward, cruise).z() > 0.70D,
                "side ray visibly slides toward jump direction");
        assertClose(RelativisticVisualRules.VISUAL_ABERRATION_MAX_BETA, cruise.aberrationBeta(),
                "artistic aberration is capped before the whole sky collapses");
        assertTrue(RelativisticVisualRules.aberrate(side, forward, cruise).z() >= 0.89D
                        && RelativisticVisualRules.aberrate(side, forward, cruise).z() < 0.95D,
                "stronger capped sky sliding forms a tight but finite forward field");
        assertTrue(RelativisticVisualRules.angularScale(cruise) > 0.15D
                        && RelativisticVisualRules.angularScale(cruise) < 0.20D,
                "0.985c cruise remains strongly compressed without collapsing bodies to light points");
        assertTrue(RelativisticVisualRules.tint(forward, forward, cruise).brightness()
                        > RelativisticVisualRules.tint(forward.scale(-1.0D), forward, cruise).brightness(),
                "forward visual Doppler approximation is brighter than the rear sky");
        RelativisticVisualRules.Tint forwardTint = RelativisticVisualRules.tint(forward, forward, cruise);
        RelativisticVisualRules.Tint sideTint = RelativisticVisualRules.tint(side, forward, cruise);
        RelativisticVisualRules.Tint rearShoulderTint = RelativisticVisualRules.tint(
                new CelestialVector(Math.sqrt(0.19D), 0.0D, -0.9D), forward, cruise);
        RelativisticVisualRules.Tint rearTint = RelativisticVisualRules.tint(forward.scale(-1.0D), forward, cruise);
        assertTrue(forwardTint.blue() > forwardTint.red() * 2.0D,
                "forward jump stars retain an unmistakable blue shift");
        assertTrue(forwardTint.brightness() > sideTint.brightness()
                        && sideTint.brightness() > rearShoulderTint.brightness()
                        && rearShoulderTint.brightness() > rearTint.brightness(),
                "Doppler brightness remains directional from forward through the exact rear");
        assertTrue(rearShoulderTint.red() > rearShoulderTint.blue() * 1.5D
                        && rearShoulderTint.brightness() > 0.10D,
                "rear shoulder retains dim red star texture");
        assertTrue(rearTint.brightness() < 0.02D,
                "exact rear may disappear under extreme red shift without an artificial floor");
        assertTrue(RelativisticVisualRules.starVisibility(0.0D, cruise) >= 0.87D,
                "jump supplies its own bright star exposure even when ordinary stars are hidden");
        ObservationJourney accelerating = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_ACCELERATING, 0L, 60L);
        ObservationJourney decelerating = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_DECELERATING, 220L, 60L);
        assertClose(0.0D, RelativisticVisualRules.state(accelerating, 0.0D).beta(), "jump starts at rest");
        assertTrue(RelativisticVisualRules.state(accelerating, 60.0D).beta() > 0.98D,
                "acceleration reaches near-light beta in three seconds");
        assertClose(0.0D, RelativisticVisualRules.state(decelerating, 280.0D).beta(),
                "deceleration ends at rest");
        ObservationJourney contractionCruise = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        double contractionStart = RelativisticVisualRules.state(contractionCruise, 60.0D).aberrationBeta();
        double contractionMiddle = RelativisticVisualRules.state(contractionCruise, 135.0D).aberrationBeta();
        double contractionAtSevenPointFive = RelativisticVisualRules.state(
                contractionCruise, 210.0D).aberrationBeta();
        assertClose(RelativisticVisualRules.ACCELERATION_END_ABERRATION_BETA, contractionStart,
                "cruise starts from acceleration contraction without a jump");
        assertTrue(contractionStart < contractionMiddle
                        && contractionMiddle < contractionAtSevenPointFive,
                "first 7.5 cruise seconds continue slowly contracting the star field");
        assertClose(RelativisticVisualRules.VISUAL_ABERRATION_MAX_BETA, contractionAtSevenPointFive,
                "cruise reaches its tightest finite contraction at 7.5 seconds");
        assertClose(contractionAtSevenPointFive,
                RelativisticVisualRules.state(contractionCruise, 220.0D).aberrationBeta(),
                "last half cruise second holds the tight point-like star field");
        assertClose(contractionAtSevenPointFive,
                RelativisticVisualRules.state(decelerating, 220.0D).aberrationBeta(),
                "deceleration begins from the exact cruise contraction state");
        assertTrue(RelativisticVisualRules.state(decelerating, 240.0D).aberrationBeta()
                        < contractionAtSevenPointFive,
                "three-second deceleration rapidly releases contraction while still moving");
        CelestialState visualState = state();
        ObservationJourney jumpCruise = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        CelestialVector cruiseEarly = OrbitVisualRules.frame(
                context(EARTH, Optional.of(jumpCruise)), visualState, 80.0D).observerPosition();
        CelestialVector cruiseLate = OrbitVisualRules.frame(
                context(EARTH, Optional.of(jumpCruise)), visualState, 200.0D).observerPosition();
        assertTrue(cruiseLate.subtract(cruiseEarly).length() > 1.0D,
                "eight-second cruise moves the observer and makes every scene body stream past");
        ObservationJourney jumpAcceleration = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_ACCELERATING, 0L, 60L);
        ObservationJourney anchoredCruise = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> speedEphemeris =
                testEphemeris(visualState);
        double joinSpeedFraction = OrbitVisualRules.jumpJoinSpeedFraction(
                anchoredCruise, speedEphemeris);
        assertVector(OrbitVisualRules.frame(context(EARTH, Optional.of(jumpAcceleration)), visualState, 60.0D)
                        .observerPosition(),
                OrbitVisualRules.frame(context(EARTH, Optional.of(anchoredCruise)), visualState, 60.0D)
                        .observerPosition(),
                "acceleration-to-cruise observer position is continuous");
        assertClose(0.0D, OrbitVisualRules.jumpSpeedFraction(
                        jumpAcceleration, 0.0D, joinSpeedFraction),
                "jump acceleration starts at zero visual speed");
        assertClose(1.0D, OrbitVisualRules.jumpSpeedFraction(
                        jumpAcceleration, 60.0D, joinSpeedFraction),
                "jump acceleration reaches the route cruise speed");
        double earlyAccelerationStep = OrbitVisualRules.jumpTravelProgress(
                jumpAcceleration, 10.0D, joinSpeedFraction)
                - OrbitVisualRules.jumpTravelProgress(jumpAcceleration, 0.0D, joinSpeedFraction);
        double lateAccelerationStep = OrbitVisualRules.jumpTravelProgress(
                jumpAcceleration, 60.0D, joinSpeedFraction)
                - OrbitVisualRules.jumpTravelProgress(jumpAcceleration, 50.0D, joinSpeedFraction);
        assertTrue(lateAccelerationStep > earlyAccelerationStep * 4.0D,
                "acceleration scenery recedes from slow to fast rather than jumping to cruise speed");
        double firstCruiseStep = OrbitVisualRules.jumpTravelProgress(
                anchoredCruise, 80.0D, joinSpeedFraction)
                - OrbitVisualRules.jumpTravelProgress(anchoredCruise, 60.0D, joinSpeedFraction);
        double lastCruiseStep = OrbitVisualRules.jumpTravelProgress(
                anchoredCruise, 220.0D, joinSpeedFraction)
                - OrbitVisualRules.jumpTravelProgress(anchoredCruise, 200.0D, joinSpeedFraction);
        assertClose(firstCruiseStep, lastCruiseStep,
                "all eight cruise seconds use one distance-derived constant scene speed");
        ObservationJourney alternateCruise = new ObservationJourney(MOON, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        double earthMarsStep = OrbitVisualRules.frame(
                context(EARTH, Optional.of(anchoredCruise)), visualState, 81.0D).observerPosition()
                .subtract(OrbitVisualRules.frame(context(EARTH, Optional.of(anchoredCruise)),
                        visualState, 80.0D).observerPosition()).length();
        double moonMarsStep = OrbitVisualRules.frame(
                context(MOON, Optional.of(alternateCruise)), visualState, 81.0D).observerPosition()
                .subtract(OrbitVisualRules.frame(context(MOON, Optional.of(alternateCruise)),
                        visualState, 80.0D).observerPosition()).length();
        assertClose(OrbitVisualRules.jumpCruiseDistancePerTick(anchoredCruise, speedEphemeris),
                earthMarsStep, "Earth-Mars cruise scene speed equals route distance over fixed time");
        assertClose(OrbitVisualRules.jumpCruiseDistancePerTick(alternateCruise, speedEphemeris),
                moonMarsStep, "Moon-Mars cruise scene speed equals route distance over fixed time");
        assertTrue((OrbitVisualRules.jumpCruiseDistancePerTick(anchoredCruise, speedEphemeris)
                        > OrbitVisualRules.jumpCruiseDistancePerTick(alternateCruise, speedEphemeris))
                        == (earthMarsStep > moonMarsStep),
                "the longer fixed-time route produces the larger cruise scenery speed");
        assertVector(OrbitVisualRules.frame(context(EARTH, Optional.of(anchoredCruise)), visualState, 220.0D)
                        .observerPosition(),
                OrbitVisualRules.frame(context(EARTH, Optional.of(decelerating)), visualState, 220.0D)
                        .observerPosition(),
                "cruise-to-deceleration observer position is continuous");
        CelestialVector decelerationStart = OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState, 220.0D).observerPosition();
        CelestialVector decelerationMiddle = OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState, 250.0D).observerPosition();
        CelestialVector decelerationEnd = OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState, 280.0D).observerPosition();
        assertTrue(decelerationMiddle.subtract(decelerationStart).length() > 1.0D
                        && decelerationEnd.subtract(decelerationMiddle).length() > 0.1D,
                "the ship and every scene object continue moving throughout all three deceleration seconds");
        assertTrue(decelerationMiddle.subtract(decelerationStart).length()
                        > decelerationEnd.subtract(decelerationMiddle).length(),
                "deceleration scene speed continuously falls toward the reveal entry speed");
        assertTrue(joinSpeedFraction > 0.0D && joinSpeedFraction < 1.0D,
                "deceleration solves a finite non-zero target-reveal join speed");
        assertClose(joinSpeedFraction, OrbitVisualRules.jumpSpeedFraction(
                        decelerating, 280.0D, joinSpeedFraction),
                "deceleration ends at the target-reveal entry speed instead of zero");
        ObservationJourney jumpArrival = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.ARRIVING, 280L, 200L);
        OrbitVisualRules.Frame arrivalStart = OrbitVisualRules.frame(
                context(MARS, Optional.of(jumpArrival)), visualState, 280.0D);
        OrbitVisualRules.Frame arrivalMiddle = OrbitVisualRules.frame(
                context(MARS, Optional.of(jumpArrival)), visualState, 285.0D);
        OrbitVisualRules.Frame arrivalRevealEnd = OrbitVisualRules.frame(
                context(MARS, Optional.of(jumpArrival)), visualState, 290.0D);
        assertVector(decelerationEnd, arrivalStart.observerPosition(),
                "deceleration ends at the moving half-second reveal path start");
        double derivativeStep = 1.0E-4D;
        double decelerationJoinStep = decelerationEnd.subtract(OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState,
                280.0D - derivativeStep).observerPosition()).length();
        double arrivalJoinStep = OrbitVisualRules.frame(
                context(MARS, Optional.of(jumpArrival)), visualState,
                280.0D + derivativeStep).observerPosition()
                .subtract(arrivalStart.observerPosition()).length();
        assertTrue(decelerationJoinStep > 0.0D && arrivalJoinStep > 0.0D
                        && Math.abs(decelerationJoinStep - arrivalJoinStep)
                        / Math.max(decelerationJoinStep, arrivalJoinStep) < 1.0E-4D,
                "deceleration and half-second reveal share one continuous world-distance speed");
        assertTrue(arrivalMiddle.observerPosition().subtract(arrivalStart.observerPosition()).length() > 0.01D
                        && arrivalRevealEnd.observerPosition().subtract(arrivalMiddle.observerPosition()).length()
                        > 0.01D,
                "the ship and background continue moving during the sudden half-second target enlargement");
        assertTrue(body(arrivalStart, MARS).cubeAlpha() == 0.0D
                        && body(arrivalMiddle, MARS).cubeAlpha() > 0.0D
                        && body(arrivalRevealEnd, MARS).cubeAlpha() > body(arrivalMiddle, MARS).cubeAlpha(),
                "target remains a star point at deceleration end then enlarges continuously for 0.5 seconds");
        assertTrue(arrivalStart.targetLockStrength() == 1.0D
                        && arrivalRevealEnd.targetLockStrength() == 1.0D,
                "target stays centered throughout the moving half-second enlargement");
        OrbitVisualRules.Frame lockedCruise = OrbitVisualRules.frame(
                context(EARTH, Optional.of(anchoredCruise)), visualState, 100.0D);
        assertTrue(lockedCruise.targetLockStrength() == 1.0D,
                "jump acceleration and cruise hold a complete target lock");
        CelestialVector centeredTarget = viewedDirection(lockedCruise, MARS);
        assertTrue(Math.abs(centeredTarget.x()) < 1.0E-6D
                        && Math.abs(centeredTarget.y()) < 1.0E-6D
                        && centeredTarget.z() < -0.999999D,
                "jump target is centered directly ahead");
        ObservationJourney jumpDeparture = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.DEPARTING, 0L, 60L);
        ObservationJourney boundaryAcceleration = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_ACCELERATING, 60L, 60L);
        CelestialState departureVisualState = stateWithMars(new CelestialVector(-200.0D, 0.0D, 0.0D));
        OrbitVisualRules.Frame departureStart = OrbitVisualRules.frame(
                context(EARTH, Optional.of(jumpDeparture)), departureVisualState, 0.0D);
        OrbitVisualRules.Frame departureEnd = OrbitVisualRules.frame(
                context(EARTH, Optional.of(jumpDeparture)), departureVisualState, 60.0D);
        OrbitVisualRules.Frame accelerationStart = OrbitVisualRules.frame(
                context(EARTH, Optional.of(boundaryAcceleration)), departureVisualState, 60.0D);
        org.joml.Quaternionf ordinaryStart = OrbitVisualRules.stationViewOrientation(0.0D);
        org.joml.Quaternionf departureStartOrientation = OrbitVisualRules.frameViewOrientation(departureStart);
        assertTrue(Math.abs(ordinaryStart.dot(departureStartOrientation)) > 0.999999F,
                "jump direction search begins at the ordinary inertial station attitude");
        assertTrue(Math.abs(OrbitVisualRules.frameViewOrientation(departureEnd)
                        .dot(OrbitVisualRules.frameViewOrientation(accelerationStart))) > 0.999999F,
                "jump departure ends at exactly the acceleration target-lock attitude");
        assertVector(departureEnd.observerPosition(), accelerationStart.observerPosition(),
                "jump direction search position hands off continuously to acceleration");
        CelestialVector fixedDepartureDirection = departureStart.velocityDirection();
        org.joml.Quaternionf priorDepartureOrientation = departureStartOrientation;
        double previousRemainingAngle = 2.0D * Math.acos(Math.min(1.0D, Math.abs(
                priorDepartureOrientation.dot(OrbitVisualRules.frameViewOrientation(departureEnd)))));
        for (int step = 1; step <= 600; step++) {
            double sampleTime = step / 10.0D;
            OrbitVisualRules.Frame sample = OrbitVisualRules.frame(
                    context(EARTH, Optional.of(jumpDeparture)), departureVisualState, sampleTime);
            org.joml.Quaternionf orientation = OrbitVisualRules.frameViewOrientation(sample);
            assertClose(0.0D, sample.viewRotationRadians(),
                    "jump direction search does not add a second planar route yaw");
            assertVector(fixedDepartureDirection, sample.velocityDirection(),
                    "jump direction search keeps one fixed acceleration heading");
            assertTrue(Math.abs(priorDepartureOrientation.dot(orientation)) > 0.9999F,
                    "complete jump-departure sky orientation has no adjacent rotational jump");
            double remainingAngle = 2.0D * Math.acos(Math.min(1.0D, Math.abs(
                    orientation.dot(OrbitVisualRules.frameViewOrientation(departureEnd)))));
            assertTrue(remainingAngle <= previousRemainingAngle + 1.0E-5D,
                    "complete jump-departure turn advances monotonically toward target lock");
            previousRemainingAngle = remainingAngle;
            priorDepartureOrientation = orientation;
        }
        CelestialVector departureCenteredTarget = viewedDirection(departureEnd, MARS);
        assertTrue(Math.abs(departureCenteredTarget.x()) < 1.0E-6D
                        && Math.abs(departureCenteredTarget.y()) < 1.0E-6D
                        && departureCenteredTarget.z() < -0.999999D,
                "direction search finishes with the target directly ahead before acceleration");
        assertTrue(departureEnd.observerPosition().subtract(departureStart.observerPosition()).length()
                        > 0.001D,
                "blocked direct line performs visible orbital phasing while the sky turn stays continuous");
        org.joml.Quaternionf priorTurn = null;
        for (int step = 0; step <= 720; step++) {
            double angle = Math.PI * 2.0D * step / 720.0D;
            CelestialVector direction = new CelestialVector(
                    0.35D, Math.sin(angle), Math.cos(angle)).normalized();
            org.joml.Quaternionf turn = OrbitVisualRules.targetLockOrientation(direction);
            org.joml.Vector3f centered = new org.joml.Vector3f(
                    (float) direction.x(), (float) direction.y(), (float) direction.z());
            turn.transform(centered);
            assertTrue(Math.abs(centered.x) < 1.0E-5F && Math.abs(centered.y) < 1.0E-5F
                            && centered.z < -0.99999F,
                    "stable target-lock orientation keeps every sampled route centered");
            if (priorTurn != null) {
                assertTrue(Math.abs(priorTurn.dot(turn)) > 0.9999F,
                        "adjacent target-lock frames never flip to a different rotation branch");
            }
            priorTurn = turn;
        }
        assertTrue(OrbitVisualRules.hasClearTargetLine(lockedCruise.observerPosition(), MARS,
                        body(lockedCruise, MARS).worldPosition(), testEphemeris(visualState)),
                "jump acceleration begins only from a direct unobstructed target line");
        java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> blocked = new java.util.LinkedHashMap<>();
        ResourceLocation blockedOrigin = id("blocked_jump_origin");
        ResourceLocation blockedTarget = id("blocked_jump_target");
        blocked.put(OrbitVisualRules.SUN, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(0.0D, 100.0D, 0.0D), 0.1D, null));
        blocked.put(blockedOrigin, new OrbitVisualRules.BodyEphemeris(
                CelestialVector.ZERO, 1.0D, OrbitVisualRules.SUN));
        blocked.put(blockedTarget, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(-20.0D, 0.0D, 0.0D), 0.5D, OrbitVisualRules.SUN));
        CelestialVector initiallyBlocked = new CelestialVector(
                1.0D / Math.tan(Math.toRadians(OrbitVisualRules.NEAR_PHYSICAL_DIAMETER_DEGREES * 0.5D)),
                0.0D, 0.0D);
        assertTrue(!OrbitVisualRules.hasClearTargetLine(initiallyBlocked, blockedTarget,
                        blocked.get(blockedTarget).position(), blocked),
                "source cube blocks the initial straight jump line");
        CelestialVector phasedLock = OrbitVisualRules.jumpLockPosition(
                blockedOrigin, blockedTarget, blocked, 0.0D);
        assertTrue(OrbitVisualRules.hasClearTargetLine(phasedLock, blockedTarget,
                        blocked.get(blockedTarget).position(), blocked),
                "ordinary orbital phasing finds a clear straight jump lock before acceleration");
        assertTrue(phasedLock.subtract(initiallyBlocked).length() > 0.1D,
                "blocked jump visibly changes orbit before locking the target");
        try {
            String sky = Files.readString(Path.of(
                    "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.vsh"));
            String renderer = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/NtmOrbitSkyRenderer.java"));
            String fragment = Files.readString(Path.of(
                    "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.fsh"));
            assertTrue(sky.contains("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)")
                            && !sky.contains("shifted") && !sky.contains("Beta"),
                    "skybox geometry remains rigid instead of folding toward jump velocity");
            assertTrue(fragment.contains("ntmAtlasUv") && fragment.contains("source = observed")
                            && fragment.contains("AberrationBeta") && fragment.contains("doppler")
                            && fragment.contains("gamma * (1.0 - beta * observedCosine)"),
                    "fragment shader slides stars across all atlas faces and preserves Doppler colour");
            assertTrue(fragment.contains("vec2(1.0, 1.0)") && fragment.contains("vec2(2.0, 0.0)")
                            && fragment.contains("vec2(1.0, 0.0)") && fragment.contains("vec2(0.0, 0.0)")
                            && fragment.contains("vec2(2.0, 1.0)") && fragment.contains("vec2(0.0, 1.0)"),
                    "shader inverse mapping covers all six exact NTM atlas cells");
            assertTrue(renderer.contains("RelativisticSkyShader") && renderer.contains("AberrationBeta")
                            && renderer.contains("starVisibility(starVisibility, relativity)"),
                    "NTM star cubemap receives bounded sliding and boosted jump exposure");
            assertTrue(fragment.contains("pow(clamp(doppler, 0.0, 1.0), 1.65)")
                            && fragment.contains("forwardCone")
                            && fragment.contains("sampleNtmAtlasSeamless")
                            && fragment.contains("observedCosine")
                            && !fragment.contains("1.0 + 0.82 * exposure"),
                    "shader gives a seamless radial forward brightness independent of cubemap faces");
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot inspect relativistic sky shader", exception);
        }
    }

    private static void satelliteShadowsUseBoundedThreeDimensionalCubeCasters() throws Exception {
        ResourceLocation parentId = id("test_parent");
        ResourceLocation childA = id("test_child_a");
        ResourceLocation childB = id("test_child_b");
        ResourceLocation offAxis = id("test_off_axis");
        ResourceLocation behind = id("test_behind");
        ResourceLocation childC = id("test_child_c");
        ResourceLocation childD = id("test_child_d");
        ResourceLocation childE = id("test_child_e");
        OrbitVisualRules.BodyEphemeris sun = new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D, null);
        OrbitVisualRules.BodyEphemeris parent = new OrbitVisualRules.BodyEphemeris(
                CelestialVector.ZERO, 1.0D, SUN);
        java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> ephemeris = new java.util.LinkedHashMap<>();
        ephemeris.put(SUN, sun);
        ephemeris.put(parentId, parent);
        // Sun is +X, so casters between Sun and parent have positive X and cast toward the parent.
        ephemeris.put(childA, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(4.0D, 0.0D, 0.0D), 0.40D, parentId));
        ephemeris.put(childB, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(5.0D, 0.7D, 0.7D), 0.30D, parentId));
        ephemeris.put(childC, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(6.0D, -0.2D, 0.1D), 0.20D, parentId));
        ephemeris.put(childD, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(7.0D, 0.1D, -0.2D), 0.18D, parentId));
        ephemeris.put(childE, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(8.0D, 0.2D, 0.2D), 0.05D, parentId));
        ephemeris.put(offAxis, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(4.0D, 20.0D, 0.0D), 0.40D, parentId));
        ephemeris.put(behind, new OrbitVisualRules.BodyEphemeris(
                new CelestialVector(-4.0D, 0.0D, 0.0D), 0.40D, parentId));
        List<OrbitVisualRules.SatelliteShadow> selected = OrbitVisualRules.satelliteShadows(
                parentId, parent, java.util.Map.copyOf(ephemeris), sun);
        assertEquals(OrbitVisualRules.MAX_SATELLITE_SHADOWS, selected.size(),
                "fixed maximum satellite shadow budget");
        assertEquals(childA, selected.get(0).satellite(), "largest angular caster has priority");
        assertTrue(selected.stream().noneMatch(shadow -> shadow.satellite().equals(offAxis)
                        || shadow.satellite().equals(behind) || shadow.satellite().equals(childE)),
                "CPU culling rejects off-cone, behind-parent and fifth-priority casters");
        assertClose(0.40D, selected.get(0).halfSize(), "caster normalized to parent half-size");

        String renderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        assertTrue(renderer.contains("ShadowCount") && renderer.contains("ShadowAxisX")
                        && renderer.contains("MAX_SATELLITE_SHADOWS") == false,
                "renderer uploads OBB axes and uses the already bounded caster list");
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

    /** Java mirror of relativistic_sky.fsh::ntmAtlasUv, used to lock face orientation at seams. */
    private static NtmAtlasSample ntmAtlasSample(double[] direction) {
        double length = Math.sqrt(direction[0] * direction[0] + direction[1] * direction[1]
                + direction[2] * direction[2]);
        double x = direction[0] / length;
        double y = direction[1] / length;
        double z = direction[2] / length;
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        if (ax >= ay && ax >= az) {
            return x < 0.0D
                    ? new NtmAtlasSample(0, (-z / ax + 1.0D) * 0.5D,
                    (1.0D - y / ax) * 0.5D)
                    : new NtmAtlasSample(4, (z / ax + 1.0D) * 0.5D,
                    (1.0D - y / ax) * 0.5D);
        }
        if (ay >= az) {
            return new NtmAtlasSample(y > 0.0D ? 1 : 2,
                    (1.0D - z / ay) * 0.5D,
                    ((y > 0.0D ? -x : x) / ay + 1.0D) * 0.5D);
        }
        return new NtmAtlasSample(z < 0.0D ? 3 : 5,
                ((z < 0.0D ? x : -x) / az + 1.0D) * 0.5D,
                (1.0D - y / az) * 0.5D);
    }

    private static double[] ntmDirection(NtmAtlasSample sample) {
        double u = sample.u() * 2.0D - 1.0D;
        double v = 1.0D - sample.v() * 2.0D;
        return switch (sample.face()) {
            case 0 -> normalized(-1.0D, v, -u); // west, cell 4
            case 1 -> normalized(v, 1.0D, -u); // up, cell 1
            case 2 -> normalized(-v, -1.0D, -u); // down, cell 0
            case 3 -> normalized(u, v, -1.0D); // north, cell 5
            case 4 -> normalized(1.0D, v, u); // east, cell 2
            case 5 -> normalized(-u, v, 1.0D); // south, cell 3
            default -> throw new IllegalArgumentException("face");
        };
    }

    private static double[] normalized(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / length, y / length, z / length};
    }

    private static void assertDirection(double[] expected, double[] actual, String name) {
        assertDirection(expected, actual, 1.0E-9D, name);
    }

    private static void assertDirection(double[] expected, double[] actual,
                                        double tolerance, String name) {
        double expectedLength = Math.sqrt(expected[0] * expected[0] + expected[1] * expected[1]
                + expected[2] * expected[2]);
        double actualLength = Math.sqrt(actual[0] * actual[0] + actual[1] * actual[1]
                + actual[2] * actual[2]);
        double dot = (expected[0] * actual[0] + expected[1] * actual[1]
                + expected[2] * actual[2]) / (expectedLength * actualLength);
        if (!Double.isFinite(dot) || 1.0D - dot > tolerance) {
            throw new AssertionError(name + ": direction dot was " + dot);
        }
    }

    private record NtmAtlasSample(int face, double u, double v) {
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
        return stateWithMars(new CelestialVector(200.0D, 0.0D, 0.0D));
    }

    private static CelestialState stateWithMars(CelestialVector marsPosition) {
        CelestialBodyState sun = body(SUN, null, new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D);
        CelestialBodyState moon = body(MOON, EARTH, new CelestialVector(0.3844D, 0.0D, 0.0D),
                0.001737D);
        CelestialBodyState mars = body(MARS, SUN, marsPosition,
                0.003396D);
        return new CelestialState(0.0D, 0.0D, 0.0D, 0L, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D), List.of(mars), 0,
                0.0D, 0.0D, SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE,
                0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialState stateWithJovianMoons() {
        CelestialBodyState sun = body(SUN, null, new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D);
        CelestialBodyState moon = body(MOON, EARTH, new CelestialVector(0.3844D, 0.0D, 0.0D),
                0.001737D);
        CelestialBodyState jupiter = body(JUPITER, SUN,
                new CelestialVector(20.0D, 0.0D, 0.0D), 0.071492D);
        CelestialBodyState io = body(IO, JUPITER,
                new CelestialVector(20.4217D, 0.0D, 0.0D), 0.0018216D);
        CelestialBodyState europa = body(EUROPA, JUPITER,
                new CelestialVector(20.0D, 0.669151D, 0.0D), 0.0015608D);
        CelestialBodyState ganymede = body(GANYMEDE, JUPITER,
                new CelestialVector(20.4217D, 0.0D, 0.05D), 0.0026341D);
        return new CelestialState(0.0D, 0.0D, 0.0D, 0L, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D), List.of(jupiter, io, europa, ganymede), 0,
                0.0D, 0.0D, SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE,
                0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialState stateWithMoonAt(double calendarTicks) {
        first.wildfires.celestial.CelestialRuntimeSettings settings =
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT;
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(
                0.0D, 1.0D, calendarTicks, 8,
                settings.resolvedSynodicDays(8), settings.resolvedAnomalisticDays(8),
                settings.nodalYears(), settings.lunarInclinationRadians()));
        CelestialVector sunPosition = frame.sunGeocentric()
                .scale(settings.planetSettings().earthSemiMajorMillionKm());
        CelestialVector moonPosition = testEquatorialToEcliptic(frame.moonGeocentric())
                .scale(CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM * frame.moonDistance());
        CelestialBodyState sun = body(SUN, null, sunPosition, 0.69634D);
        CelestialBodyState moon = body(MOON, EARTH, moonPosition, 0.001737D);
        return new CelestialState(0.0D, 0.0D, 0.0D, (long) calendarTicks, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D), List.of(), 0,
                0.0D, 0.0D, SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE,
                0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialVector testEquatorialToEcliptic(CelestialVector vector) {
        double cosine = Math.cos(CelestialMath.AXIAL_TILT);
        double sine = Math.sin(CelestialMath.AXIAL_TILT);
        return new CelestialVector(vector.x(), vector.y() * cosine + vector.z() * sine,
                -vector.y() * sine + vector.z() * cosine);
    }

    private static CelestialState stateWithOrbitingJovianMoons(double calendarTicks) {
        CelestialVector jupiterPosition = new CelestialVector(20.0D, 0.0D, 0.0D);
        CelestialBodyState sun = body(SUN, null, new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D);
        CelestialBodyState moon = body(MOON, EARTH, new CelestialVector(0.3844D, 0.0D, 0.0D),
                0.001737D);
        CelestialBodyState jupiter = body(JUPITER, SUN, jupiterPosition, 0.071492D);
        CelestialBodyState io = orbitingMoon(CelestialBodies.IO, jupiterPosition, calendarTicks,
                0.0018216D);
        CelestialBodyState europa = orbitingMoon(CelestialBodies.EUROPA, jupiterPosition, calendarTicks,
                0.0015608D);
        CelestialBodyState ganymede = orbitingMoon(CelestialBodies.GANYMEDE, jupiterPosition,
                calendarTicks, 0.0026341D);
        return new CelestialState(0.0D, 0.0D, 0.0D, (long) calendarTicks, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D), List.of(jupiter, io, europa, ganymede), 0,
                0.0D, 0.0D, SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE,
                0.0D, 0.0D, 1.0D, 1.0D, 1.0D,
                new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialBodyState orbitingMoon(CelestialBodies definition,
                                                    CelestialVector parentPosition,
                                                    double calendarTicks, double radius) {
        first.wildfires.celestial.CelestialPlanetSettings planets =
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT.planetSettings();
        CelestialVector normal = definition.orbitalPlaneNormalEcliptic(planets);
        CelestialVector reference = Math.abs(normal.z()) < 0.9D
                ? new CelestialVector(-normal.y(), normal.x(), 0.0D).normalized()
                : new CelestialVector(0.0D, -normal.z(), normal.y()).normalized();
        double astronomicalDays = CelestialMath.calendarYears(calendarTicks, 8)
                * planets.earthOrbitalDays();
        double angle = Math.PI * 2.0D * astronomicalDays
                / planets.parameters(definition).orbitalDays();
        CelestialVector transverse = cross(normal, reference);
        CelestialVector relative = reference.scale(Math.cos(angle))
                .add(transverse.scale(Math.sin(angle))).scale(definition.semiMajorMillionKm());
        return body(definition.id(), definition.parent().id(), parentPosition.add(relative), radius);
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
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
        OrbitVisualRules.frameViewOrientation(frame).transform(viewed);
        return new CelestialVector(viewed.x, viewed.y, viewed.z).normalized();
    }

    private static java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> testEphemeris(
            CelestialState state) {
        java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> ephemeris = new java.util.LinkedHashMap<>();
        ephemeris.put(EARTH, new OrbitVisualRules.BodyEphemeris(CelestialVector.ZERO, 0.006371D,
                OrbitVisualRules.SUN));
        java.util.List<CelestialBodyState> bodies = new java.util.ArrayList<>();
        bodies.add(state.sun());
        bodies.add(state.moon());
        bodies.addAll(state.orbitingBodies());
        for (CelestialBodyState body : bodies) {
            ephemeris.put(body.id(), new OrbitVisualRules.BodyEphemeris(body.geocentricPosition(),
                    Math.tan(body.angularRadiusRadians()) * body.distance(), body.parentId()));
        }
        return java.util.Map.copyOf(ephemeris);
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
