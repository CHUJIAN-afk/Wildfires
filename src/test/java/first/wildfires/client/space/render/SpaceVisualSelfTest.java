package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.api.celestial.SolarEclipseState;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialPlanetSettings;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import first.wildfires.client.space.NtmObjFastRenderer;
import first.wildfires.client.space.ObjComponentVisibility;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.CelestialTransferProfile;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import first.wildfires.thirdparty.genesisadapt.GenesisCubeAtlasLayout;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
        ntmAscentAtmosphereUsesExactAltitudeCurve();
        genesisDirectionsAndFallbackSurfaceAreSeamContinuous();
        ntmNightAtlasUsesItsOwnExactFaceContract();
        ntmRelativisticSamplingCrossesAllTwelveEdgesContinuously();
        surfaceTexturePolicyUsesOnlyGenesisCubemapsOrFallback();
        stationObserverUsesUnifiedRealEphemeris();
        sunEphemerisStaysOnEclipticThroughOrbitTransforms();
        ntmJourneyPhasesKeepTheirOrbitAndTransferSemantics();
        transferArcClearsAnOriginThatBlocksTheTarget();
        localParentMoonAndSiblingMoonTransfersClearEveryCube();
        earthMoonTransferUsesAuthoritativeLunarEphemeris();
        acceleratedEarthMoonTransfersClearBothMovingCubes();
        acceleratedCalendarLocalTransferLocksDepartureAndInterceptsMovingMoon();
        acceleratedLocalTransferClearsEveryMovingMoonAndDelayedPhasePacket();
        interSystemIngressLocksTargetAndSweepsEverySatelliteSystemCube();
        everySatelliteIngressClearsAllMovingBodiesAtAdverseArrivalPhases();
        everySatelliteDepartureClearsItsMovingParentAtAdversePhases();
        everyBuiltInLocalTransferTracksTheAuthoritativeEphemeris();
        ntmPointAndCubeLodUsesRecordedThresholds();
        compressedDepthPreservesOcclusionAndAngularSize();
        orbitFrameAndPlanetRotationAreTimeDriven();
        cachedBodyRotationsMatchLegacyBits();
        knownLengthNormalizationMatchesLegacyBits();
        optimizedOrbitPolygonAreaMatchesLegacyBits();
        optimizedProjectedCubeCoverageMatchesLegacyBits();
        ntmSunSizeUsesBodyEndpointsInsteadOfTransferChordDistance();
        ntmIlluminationUsesRotatedCubeOcclusion();
        satelliteShadowsUseBoundedThreeDimensionalCubeCasters();
        sunIsASeparateFlatNtmLayerAndLightsPlanetsFromSystemCenter();
        orbitalCloudMaterialMovesWithoutRotatingShell();
        vacuumBackgroundDepthAndBlockLightRemainIndependent();
        developmentClockDrivesSkyAndLightmapTogether();
        developmentClientSuppressesCitadelDevFollower();
        copiedNtmTexturesMatchRecordedHashes();
        adaptedShadersDeclareAlphaAndValidMatrices();
        translatedWaterfallDaedalusRetainsSourceContracts();
        relativisticJumpMathIsFiniteAndDirectional();
        jumpArrivalPathAndSkyOrientationStayContinuous();
        orbitVisualFrameCacheIsExactAndLossless();
        ntmObjFastRendererIsBitExactAndStrictlyBounded();
        reusableCapsuleTransitionUsesOnlyTheCapturedGenesisFrame();
        reusableCapsuleInputUsesGameplayEdgesAndTransitionPolling();
        reusableCapsuleRenderAndTransferSourceContractsStayAligned();
        reusableCapsuleParticlesKeepTheCompleteNtmContract();
        System.out.println("SpaceVisualSelfTest passed");
    }

    private static void translatedWaterfallDaedalusRetainsSourceContracts() throws Exception {
        String shader = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/WaterfallTranslatedEngineShader.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/WaterfallTestEngineRenderer.java"));
        String block = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/WaterfallTestEngineBlock.java"));
        String blockEntity = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/WaterfallTestEngineBlockEntity.java"));
        String register = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/SpaceContentRegister.java"));
        String clientRegister = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/SpaceContentClientEvents.java"));
        String directionalVertex = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/waterfall_billboard_directional.vsh"));
        String directionalFragment = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/waterfall_billboard_directional.fsh"));
        String dynamicJson = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/waterfall_test_engine_dynamic.json"));
        String dynamicVertex = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/waterfall_test_engine_dynamic.vsh"));
        String dynamicFragment = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/shaders/core/waterfall_test_engine_dynamic.fsh"));
        Path daedalusV1Config = Path.of("third_party/waterfall/0.10.3-6be4f897/upstream/"
                + "GameData/WarpPlugin/Parts/Engines/Daedalus/Deadalus.cfg");
        String daedalusV1Source = Files.readString(daedalusV1Config);

        assertTrue(block.contains("dimension() == SpaceDimensions.ORBIT")
                        && block.contains("setValue(FACING, Direction.SOUTH)")
                        && blockEntity.contains("implements StationPropulsion")
                        && blockEntity.contains("StationDriveIndex.register(engine)")
                        && blockEntity.contains("STARTUP_TICKS = 60")
                        && blockEntity.contains("SHUTDOWN_TICKS = 20")
                        && blockEntity.contains("int radius = v1 ? 76 : 6")
                        && blockEntity.contains("v1 ? 350 : 140"),
                "the translated Daedalus test block is an orbit-only fixed-south ordinary NTM drive");
        assertTrue(register.contains("DAEDALUS_V1_TEST_ENGINE_BLOCK_ENTITY")
                        && register.contains("DAEDALUS_V2_TEST_ENGINE_BLOCK_ENTITY")
                        && register.contains("ANTIMATTER_TEST_ENGINE_BLOCK_ENTITY")
                        && !register.contains("ANTIMATTER_CATALYZED")
                        && clientRegister.contains("WaterfallTestEngineRenderer::new")
                        && clientRegister.contains("WaterfallTranslatedEngineShader.register(event)")
                        && clientRegister.contains("WaterfallTranslatedEngineShader.reset()"),
                "Daedalus and the ordinary antimatter engine remain registered without catalyzed remnants");
        assertTrue(renderer.contains("Stage.AFTER_PARTICLES")
                        && renderer.contains("shouldRenderOffScreen")
                        && renderer.contains("return true;")
                        && renderer.contains("return 512;"),
                "the translated plume defers behind station depth and survives origin culling");

        assertTrue(shader.contains("DAEDALUS_RADIAL_UNIT = 2.5F / 6.0F")
                        && shader.contains("DAEDALUS_LENGTH_UNIT = 128.0F / 658.0F")
                        && shader.contains("DAEDALUS_V1_UNIT = 2.5F / 8.0F")
                        && !shader.contains("CATALYZED"),
                "both Daedalus variants retain their requested scaling without catalyzed data");
        assertNear(2.5D, 2.0D * 3.0D * 2.5D / 6.0D, 1.0E-12D,
                "Daedalus widest core diameter");
        assertNear(125.0D, 300.0D * 2.5D / 6.0D, 1.0E-12D,
                "Daedalus strict-scale core length");
        assertNear(128.0D, 658.0D * 128.0D / 658.0D, 1.0E-12D,
                "Daedalus compressed plume endpoint");
        double daedalusTailMidExpansion = 10.0D * 0.5D + 0.707776666D * 0.25D
                - 1.41555345D * (1.0D - Math.exp(-1.5D));
        double daedalusTailEndExpansion = 10.0D + 0.707776666D
                - 1.41555345D * (1.0D - Math.exp(-3.0D));
        assertTrue(2.0D * (1.0D + daedalusTailMidExpansion) * 2.20000005D
                        * 2.5D / 6.0D > 8.0D
                        && 2.0D * (1.0D + daedalusTailEndExpansion) * 2.20000005D
                        * 2.5D / 6.0D > 18.0D,
                "Daedalus source tail is expanding rather than a long converging cone");
        assertTrue(shader.contains("DAEDALUS_PLUME_BRIGHTNESS_BOOST = 2.5F")
                        && shader.contains("ZERO_TO_POINT_SIX, DAEDALUS_LENGTH)")
                        && shader.contains("brightnessScale = v1 ? 1.0F")
                        && shader.contains(": DAEDALUS_PLUME_BRIGHTNESS_BOOST"),
                "only Daedalus v2 receives its accepted presentation brightness multiplier");
        String daedalusLayers = sourceSection(shader, "DynamicLayer[] DAEDALUS_LAYERS",
                "BillboardLayer[] DAEDALUS_BILLBOARDS");
        String daedalusBillboards = sourceSection(shader, "BillboardLayer[] DAEDALUS_BILLBOARDS",
                "DynamicMaterial DAEDALUS_V1_DETONATION");
        assertEquals(11, countOccurrences(daedalusLayers, "layer("),
                "Daedalus dynamic Waterfall layer count");
        assertEquals(2, countOccurrences(daedalusBillboards, "billboard("),
                "Daedalus non-ignition billboard count");
        assertTrue(!shader.contains("IgnitionaBeam")
                        && shader.contains("fx-noise-4.png")
                        && shader.contains("fx-ion-noise.png")
                        && !shader.contains("fx-noise-6.png")
                        && !shader.contains("fx-noise-2.png")
                        && shader.contains("key(0.1F, 0.5F, 0, 1)"),
                "runtime retains Daedalus source materials and excludes ignition and catalyzed assets");
        assertTrue(shader.contains("active.time.set((float) (gameTime / 400.0D))"),
                "Daedalus keeps Unity _Time.x texture motion");

        assertTrue(dynamicJson.contains("\"vertex\": \"wildfires:waterfall_test_engine_dynamic\"")
                        && dynamicJson.contains(
                        "\"fragment\": \"wildfires:waterfall_test_engine_dynamic\"")
                        && dynamicJson.contains("\"dstrgb\": \"one_minus_src_color\"")
                        && dynamicVertex.contains("safeNormalize")
                        && dynamicFragment.contains("safeNormalize"),
                "Daedalus isolates numerically stable Waterfall dynamic GLSL");
        assertTrue(shader.contains("DestFactor.ONE_MINUS_SRC_COLOR")
                        && !dynamicJson.contains("\"dstrgb\": \"1-src-color\"")
                        && shader.contains("useUnityFiltering(material.texture)")
                        && shader.contains("useUnityFiltering(layer.texture)")
                        && shader.contains("layer.sourcePosition - sourceOrigin"),
                "Daedalus models retain Waterfall blend, filtering and shared position origin");
        assertTrue(directionalVertex.contains("uniform vec3 CameraPosition")
                        && directionalVertex.contains("objectCamera = CameraPosition / Scale")
                        && directionalVertex.contains("dot(Direction, viewDirection)")
                        && directionalVertex.contains("DirectionScale == 0.0 ? 1.0")
                        && directionalFragment.contains("col.a <= 0.01")
                        && directionalFragment.contains("directionalFactor * Brightness")
                        && directionalFragment.contains("0.0, 50.0"),
                "directional billboard keeps Waterfall camera-facing and directional equations");

        ByteBuffer cylinder = ByteBuffer.wrap(Files.readAllBytes(Path.of(
                "src/main/resources/assets/wildfires/models/effect/fx-cylinder.mesh")));
        int cylinderVertices = cylinder.getInt();
        float minCylinderX = Float.POSITIVE_INFINITY;
        float maxCylinderX = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < cylinderVertices; index++) {
            float x = cylinder.getFloat();
            minCylinderX = Math.min(minCylinderX, x);
            maxCylinderX = Math.max(maxCylinderX, x);
            cylinder.position(cylinder.position() + 28);
        }
        assertNear(2.5D, (maxCylinderX - minCylinderX) * 3.0D * 2.5D / 6.0D,
                1.0E-6D, "Daedalus visible widest-core diameter");

        double v1Unit = 2.5D / 8.0D;
        double v1CoreDiameter = 2.0D * 4.0D * v1Unit;
        double v1OuterStartDiameter = 2.0D * (1.0D + 2.02221918D) * 2.5D * v1Unit;
        double v1OuterEndExpansion = 2.02221918D + 130.0D - 0.707776666D
                - 40.0D * (1.0D - Math.exp(-3.0D));
        double v1OuterEndDiameter = 2.0D * (1.0D + v1OuterEndExpansion) * 2.5D * v1Unit;
        assertNear(2.5D, v1CoreDiameter, 1.0E-12D,
                "Daedalus v1 maximum core diameter");
        assertNear(4.72221746875D, v1OuterStartDiameter, 1.0E-12D,
                "Daedalus v1 outer starting diameter");
        assertNear(147.353008201117D, v1OuterEndDiameter, 1.0E-12D,
                "Daedalus v1 geometric endpoint diameter");
        assertNear(334.375D, 1070.0D * v1Unit, 1.0E-12D,
                "Daedalus v1 exact endpoint length");
        String v1Layers = sourceSection(shader, "DynamicLayer[] DAEDALUS_V1_LAYERS",
                "BillboardLayer[] DAEDALUS_V1_BILLBOARDS");
        String v1Billboards = sourceSection(shader, "BillboardLayer[] DAEDALUS_V1_BILLBOARDS",
                "private static DynamicBindings dynamic");
        assertEquals(9, countOccurrences(v1Layers, "layer("),
                "Daedalus v1 non-ignition dynamic layer count");
        assertEquals(4, countOccurrences(v1Billboards, "billboard("),
                "Daedalus v1 directional flare count");
        assertEquals(40, countOccurrences(daedalusV1Source, "name = IgnitionBeam"),
                "Daedalus v1 excluded source ignition-beam count");
        assertTrue(shader.contains("fade(0.00455631875F, 0.671389401F)")
                        && shader.contains("2.02221918F, 130.0F, -40.0F, 200.0F")
                        && shader.contains("3.33666158F, 100.0F, -25.0F, 83.9109879F")
                        && shader.contains("fx_flarelens02.png")
                        && !shader.contains("name = IgnitionBeam"),
                "Daedalus v1 keeps source fade, expansion, motion and flare fields only");
        byte[] v1ConfigDigest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(daedalusV1Config));
        StringBuilder v1ConfigHash = new StringBuilder(64);
        for (byte value : v1ConfigDigest) v1ConfigHash.append(String.format("%02x", value & 0xFF));
        assertEquals("715050892fe1c8f91a7a70c3bb1bedb3729372f5e4945714717a1de27c8ba5ae",
                v1ConfigHash.toString(), "Daedalus v1 preserved source config SHA-256");

        assertTrue(Files.isRegularFile(Path.of(
                        "src/main/resources/assets/wildfires/blockstates/daedalus_v1_test_engine.json"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/assets/wildfires/models/item/daedalus_v1_test_engine.json"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/data/wildfires/loot_tables/blocks/daedalus_v1_test_engine.json"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/assets/wildfires/textures/effect/fx_flarelens02.png"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/assets/wildfires/blockstates/daedalus_v2_test_engine.json"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/assets/wildfires/models/item/daedalus_v2_test_engine.json"))
                        && Files.isRegularFile(Path.of(
                        "src/main/resources/data/wildfires/loot_tables/blocks/daedalus_v2_test_engine.json")),
                "Daedalus has blockstate, item model and loot resources");
        for (String path : List.of(
                "src/main/resources/assets/wildfires/blockstates/antimatter_catalyzed_test_engine.json",
                "src/main/resources/assets/wildfires/models/block/antimatter_catalyzed_test_engine_off.json",
                "src/main/resources/assets/wildfires/models/block/antimatter_catalyzed_test_engine_on.json",
                "src/main/resources/assets/wildfires/models/item/antimatter_catalyzed_test_engine.json",
                "src/main/resources/data/wildfires/loot_tables/blocks/antimatter_catalyzed_test_engine.json",
                "src/main/resources/assets/wildfires/textures/effect/fx-noise-2.png",
                "src/main/resources/assets/wildfires/textures/effect/fx-noise-6.png")) {
            assertTrue(!Files.exists(Path.of(path)), "removed catalyzed resource is absent: " + path);
        }
    }

    private static void ntmObjFastRendererIsBitExactAndStrictlyBounded() throws Exception {
        NtmObjFastRenderer.clear();
        Set<String> outerPrevious = ObjComponentVisibility.enter(Set.of("Port"));
        assertTrue(ObjComponentVisibility.visible("Port")
                        && !ObjComponentVisibility.visible("ArmZP")
                        && ObjComponentVisibility.fastPathActive(),
                "explicit outer component scope selects only its NTM mesh");
        Set<String> innerPrevious = ObjComponentVisibility.enter(Set.of("ArmZP"));
        assertTrue(ObjComponentVisibility.visible("ArmZP")
                        && !ObjComponentVisibility.visible("Port"),
                "nested component scope temporarily replaces the outer selection");
        ObjComponentVisibility.exit(innerPrevious);
        assertTrue(ObjComponentVisibility.visible("Port")
                        && !ObjComponentVisibility.visible("ArmZP"),
                "nested component scope restores the exact outer selection");
        ObjComponentVisibility.exit(outerPrevious);
        assertTrue(ObjComponentVisibility.visible("Port")
                        && ObjComponentVisibility.visible("ArmZP")
                        && !ObjComponentVisibility.fastPathActive(),
                "leaving all scopes restores ordinary Forge rendering");
        Random random = new Random(0x4E544D4F424AL);
        List<BakedQuad> lastQuads = null;
        PoseStack lastPoses = null;
        int lastLight = 0;
        int lastOverlay = 0;
        for (int sample = 0; sample < 160; sample++) {
            List<BakedQuad> quads = new ArrayList<>();
            int quadCount = 1 + random.nextInt(5);
            for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
                int[] vertices = new int[32];
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * 8;
                    vertices[offset] = Float.floatToRawIntBits(random.nextFloat(-8.0F, 8.0F));
                    vertices[offset + 1] = Float.floatToRawIntBits(random.nextFloat(-8.0F, 8.0F));
                    vertices[offset + 2] = Float.floatToRawIntBits(random.nextFloat(-8.0F, 8.0F));
                    vertices[offset + 3] = random.nextInt();
                    vertices[offset + 4] = Float.floatToRawIntBits(random.nextFloat(-2.0F, 3.0F));
                    vertices[offset + 5] = Float.floatToRawIntBits(random.nextFloat(-2.0F, 3.0F));
                    vertices[offset + 6] = random.nextInt();
                    if ((sample + vertex) % 7 == 0) {
                        vertices[offset + 7] = 0;
                    } else {
                        int normalX = random.nextInt(255) - 127;
                        int normalY = random.nextInt(255) - 127;
                        int normalZ = random.nextInt(255) - 127;
                        vertices[offset + 7] = (normalX & 0xFF)
                                | (normalY & 0xFF) << 8 | (normalZ & 0xFF) << 16;
                    }
                }
                quads.add(new BakedQuad(vertices, -1,
                        Direction.values()[random.nextInt(Direction.values().length)],
                        null, true));
            }

            PoseStack poses = new PoseStack();
            poses.translate(random.nextDouble(-4.0D, 4.0D), random.nextDouble(-4.0D, 4.0D),
                    random.nextDouble(-4.0D, 4.0D));
            poses.mulPose(com.mojang.math.Axis.XP.rotation(random.nextFloat(-3.0F, 3.0F)));
            poses.mulPose(com.mojang.math.Axis.YP.rotation(random.nextFloat(-3.0F, 3.0F)));
            poses.mulPose(com.mojang.math.Axis.ZP.rotation(random.nextFloat(-3.0F, 3.0F)));
            poses.scale(nonZeroScale(random), nonZeroScale(random), nonZeroScale(random));
            int light = random.nextInt();
            int overlay = random.nextInt();

            byte[] reference = forgeObjVertexBytes(poses, quads, light, overlay, false);
            byte[] optimized = forgeObjVertexBytes(poses, quads, light, overlay, true);
            assertTrue(java.util.Arrays.equals(reference, optimized),
                    "NTM fast OBJ output is byte-for-byte Forge-equivalent for sample " + sample);
            lastQuads = quads;
            lastPoses = poses;
            lastLight = light;
            lastOverlay = overlay;
        }

        Method cacheSize = NtmObjFastRenderer.class.getDeclaredMethod("cachedMeshCountForTesting");
        cacheSize.setAccessible(true);
        int beforeReuse = (Integer) cacheSize.invoke(null);
        forgeObjVertexBytes(lastPoses, lastQuads, lastLight, lastOverlay, true);
        assertEquals(beforeReuse, ((Integer) cacheSize.invoke(null)).intValue(),
                "same mesh identity reuses one decoded primitive cache entry");

        com.sun.management.ThreadMXBean allocationBean =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        if (allocationBean.isThreadAllocatedMemorySupported()) {
            allocationBean.setThreadAllocatedMemoryEnabled(true);
            int iterations = 2_000;
            long threadId = Thread.currentThread().getId();
            BufferBuilder optimizedBuilder = new BufferBuilder(300_000);
            optimizedBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            long optimizedBefore = allocationBean.getThreadAllocatedBytes(threadId);
            for (int iteration = 0; iteration < iterations; iteration++) {
                assertTrue(NtmObjFastRenderer.render(lastPoses.last(), optimizedBuilder,
                                lastQuads, lastLight, lastOverlay),
                        "steady-state exact buffer keeps using the primitive path");
            }
            long optimizedAllocated = allocationBean.getThreadAllocatedBytes(threadId) - optimizedBefore;
            optimizedBuilder.discard();

            BufferBuilder forgeBuilder = new BufferBuilder(300_000);
            forgeBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            long forgeBefore = allocationBean.getThreadAllocatedBytes(threadId);
            for (int iteration = 0; iteration < iterations; iteration++) {
                for (BakedQuad quad : lastQuads) {
                    forgeBuilder.putBulkData(lastPoses.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F,
                            lastLight, lastOverlay, true);
                }
            }
            long forgeAllocated = allocationBean.getThreadAllocatedBytes(threadId) - forgeBefore;
            forgeBuilder.discard();
            System.out.println("NtmObjFastRenderer allocations over " + iterations
                    + " submissions: optimized=" + optimizedAllocated + "B, forge="
                    + forgeAllocated + "B");
            assertTrue(optimizedAllocated <= 1_024L,
                    "cached NTM primitive submission has no repeating per-frame allocation: "
                            + optimizedAllocated + " bytes");
            assertTrue(forgeAllocated > optimizedAllocated + 1_000_000L,
                    "Forge bulk submission retains measurable per-quad allocation pressure: "
                            + forgeAllocated + " bytes");
        }

        BufferBuilder first = new BufferBuilder(32);
        BufferBuilder second = new BufferBuilder(32);
        assertTrue(!NtmObjFastRenderer.render(lastPoses.last(),
                        VertexMultiConsumer.create(first, second), lastQuads, lastLight, lastOverlay),
                "wrapped or custom vertex consumers always fall back to Forge");
        List<BakedQuad> malformed = List.of(new BakedQuad(new int[31], -1, Direction.UP,
                null, true));
        assertTrue(!NtmObjFastRenderer.render(lastPoses.last(), new BufferBuilder(32),
                        malformed, lastLight, lastOverlay),
                "nonstandard quad layouts fall back before writing any vertex");

        NtmObjFastRenderer.clear();
        assertEquals(0, ((Integer) cacheSize.invoke(null)).intValue(),
                "resource reload clears all decoded NTM mesh identities");
        String mixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/CompositeRenderableMeshMixin.java"));
        String visibility = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ObjComponentVisibility.java"));
        String models = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/NtmSpaceObjModels.java"));
        String fast = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/NtmObjFastRenderer.java"));
        String mixinConfig = Files.readString(Path.of("src/main/resources/wildfires.mixins.json"));
        String capsuleRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReusableReturnCapsuleRenderer.java"));
        assertTrue(mixin.contains("fastPathActive()")
                        && mixin.contains("NtmObjFastRenderer.render")
                        && mixinConfig.contains("minecraft.CompositeRenderableMeshMixin")
                        && fast.contains("consumer.getClass() != BufferBuilder.class")
                        && fast.contains("ThreadLocal.withInitial(Scratch::new)"),
                "fast submission is restricted to explicit NTM scopes and exact BufferBuilder");
        assertTrue(visibility.contains("ThreadLocal<Set<String>>")
                        && !visibility.contains("Predicate") && !visibility.contains("Runnable")
                        && models.contains("NtmObjFastRenderer.clear()")
                        && models.contains("volatile CompositeRenderable"),
                "component scopes allocate no lambdas and model/cache reload ownership stays aligned");
        assertTrue(capsuleRenderer.indexOf("if (visual.orbitRenderAll())")
                        < capsuleRenderer.indexOf("ObjComponentVisibility.enter"),
                "complete orbit capsule remains on untouched Forge submission without a selected scope");
    }

    private static float nonZeroScale(Random random) {
        float magnitude = random.nextFloat(0.20F, 2.50F);
        return random.nextBoolean() ? magnitude : -magnitude;
    }

    private static byte[] forgeObjVertexBytes(PoseStack poses, List<BakedQuad> quads,
                                              int light, int overlay, boolean optimized) {
        BufferBuilder builder = new BufferBuilder(Math.max(256, quads.size() * 128));
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        if (optimized) {
            assertTrue(NtmObjFastRenderer.render(poses.last(), builder, quads, light, overlay),
                    "exact BufferBuilder accepts the NTM primitive fast path");
        } else {
            for (BakedQuad quad : quads) {
                builder.putBulkData(poses.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F,
                        light, overlay, true);
            }
        }
        BufferBuilder.RenderedBuffer rendered = builder.end();
        try {
            ByteBuffer view = rendered.vertexBuffer();
            byte[] bytes = new byte[view.remaining()];
            view.get(bytes);
            return bytes;
        } finally {
            rendered.release();
        }
    }

    private static void ntmAscentAtmosphereUsesExactAltitudeCurve() {
        assertTrue(NtmAscentAtmosphereVisuals.ascentBody(null, new Camera()).isEmpty(),
                "an unbound login-frame camera cannot activate capsule ascent visuals");
        assertClose(1.0D, NtmAscentAtmosphereVisuals.curvature(299.0D),
                "NTM ascent atmosphere remains complete below Y=300");
        assertClose(1.0D, NtmAscentAtmosphereVisuals.curvature(300.0D),
                "NTM ascent atmosphere starts at full strength at Y=300");
        assertClose(0.5D, NtmAscentAtmosphereVisuals.curvature(550.0D),
                "NTM ascent atmosphere is half strength at Y=550");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.curvature(800.0D),
                "NTM ascent atmosphere reaches vacuum black at Y=800");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.curvature(901.0D),
                "NTM ascent atmosphere stays black through the Y>900 transfer boundary");
        assertClose(1.0D, NtmAscentAtmosphereVisuals.curvature(Double.NaN),
                "non-finite diagnostic altitude does not black out an unrelated scene");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.planetAlpha(199.0D),
                "NTM underfoot body remains hidden below Y=200");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.planetAlpha(200.0D),
                "NTM underfoot body starts transparent at Y=200");
        assertClose(0.5D, NtmAscentAtmosphereVisuals.planetAlpha(350.0D),
                "NTM underfoot body is half visible at Y=350");
        assertClose(1.0D, NtmAscentAtmosphereVisuals.planetAlpha(500.0D),
                "NTM underfoot body is fully visible at Y=500");
        assertClose(1.0D, NtmAscentAtmosphereVisuals.planetAlpha(901.0D),
                "NTM underfoot body remains complete through the transfer boundary");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.planetAlpha(Double.NaN),
                "non-finite diagnostic altitude cannot reveal a body");
        Quaternionf rotation = new Quaternionf().rotateXYZ(0.62F, 0.37F, -0.21F);
        double outerExit = NtmAscentAtmosphereVisuals.outerShellExitDistance(rotation, 1.025D);
        double lowDistance = NtmAscentAtmosphereVisuals.planetCenterDistance(200.0D, outerExit);
        double middleDistance = NtmAscentAtmosphereVisuals.planetCenterDistance(350.0D, outerExit);
        double highDistance = NtmAscentAtmosphereVisuals.planetCenterDistance(900.0D, outerExit);
        assertTrue(Double.isFinite(lowDistance) && Double.isFinite(highDistance)
                        && outerExit > NtmAscentAtmosphereVisuals.PLANET_HALF_SIZE
                        && Double.doubleToLongBits(lowDistance) == Double.doubleToLongBits(outerExit)
                        && middleDistance > outerExit
                        && highDistance > lowDistance,
                "rotated Genesis cube starts on its outer shell and moves monotonically outside");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.planetAlpha(
                        350.0D, outerExit - 1.0D, outerExit),
                "camera inside a rotated atmosphere shell cannot reveal the square planet");
        assertClose(0.0D, NtmAscentAtmosphereVisuals.planetAlpha(
                        200.0D, lowDistance, outerExit),
                "surface-exit boundary begins from zero opacity");
        assertClose(0.5D, NtmAscentAtmosphereVisuals.planetAlpha(
                        350.0D, middleDistance, outerExit),
                "planet reveal resumes the NTM altitude curve only after geometric exit");
        assertTrue(!NtmAscentAtmosphereVisuals.hasLeftRenderedSurface(200.0D)
                        && NtmAscentAtmosphereVisuals.hasLeftRenderedSurface(200.01D),
                "local cloud deck is disabled at the same strict surface-exit edge");

        CelestialTransferProfile earth = CelestialTransferProfile.resolve(EARTH,
                visual(Optional.of(id("earth_test")), id("three_by_two_v1")),
                CelestialPlanetSettings.DEFAULT);
        CelestialTransferProfile jupiter = CelestialTransferProfile.resolve(JUPITER,
                visual(Optional.of(id("jupiter_test")), id("three_by_two_v1")),
                CelestialPlanetSettings.DEFAULT);
        CelestialVisualDefinition.Atmosphere denseAtmosphere = new CelestialVisualDefinition.Atmosphere(
                true, 1.06D, 12.0D, new CelestialVisualDefinition.Color(0.8D, 0.6D, 0.3D),
                Optional.empty(), Optional.empty(), 1.0D, 1.0D, 1.0D,
                3.0D, 4.0D, 0.6D, 2.0D, 0.72D, 1.0D);
        CelestialVisualDefinition denseVisual = new CelestialVisualDefinition(
                Optional.of(id("dense_test")), true, id("three_by_two_v1"), id("cube"),
                denseAtmosphere, CelestialVisualDefinition.CloudLayer.NONE);
        CelestialTransferProfile denseEarth = CelestialTransferProfile.resolve(EARTH, denseVisual,
                CelestialPlanetSettings.DEFAULT);
        assertTrue(jupiter.planetHalfSize() > earth.planetHalfSize()
                        && jupiter.revealEndAltitude() > earth.revealEndAltitude(),
                "larger bound bodies receive a longer square-body reveal corridor");
        assertTrue(NtmAscentAtmosphereVisuals.hasLeftRenderedSurface(
                        earth.revealStartAltitude() + 0.01D, earth)
                        && !NtmAscentAtmosphereVisuals.hasLeftRenderedSurface(
                        earth.revealStartAltitude(), earth)
                        && !NtmAscentAtmosphereVisuals.hasLeftRenderedSurface(
                        earth.revealStartAltitude() + 0.01D, jupiter),
                "terrain, cloud and weather gates share the strict size-aware reveal boundary");
        assertTrue(denseEarth.atmosphereFadeEndAltitude() > earth.atmosphereFadeEndAltitude()
                        && denseEarth.transferAltitude() > earth.transferAltitude(),
                "thicker denser atmospheres receive a longer fade and transfer corridor");
    }

    private static void reusableCapsuleInputUsesGameplayEdgesAndTransitionPolling() throws Exception {
        String client = Files.readString(Path.of(
                "src/main/java/first/wildfires/event/forgeEvent/ClientForgeEvent.java"));
        String packet = Files.readString(Path.of(
                "src/main/java/first/wildfires/network/PlayerInputPacket.java"));
        assertTrue(client.contains("MovementInputUpdateEvent")
                        && client.contains("syncJumpState(event.getInput().jumping)")
                        && client.contains("if (ReturnCapsuleClientTransition.armed()) syncPhysicalJumpKey()")
                        && client.contains("syncJumpState(minecraft.options.keyJump.isDown())"),
                "normal gameplay uses Forge jump edges while the receiving screen retains physical polling");
        assertTrue(packet.contains("handlePrimaryActionInput(player, capsule, pressed)")
                        && !packet.contains("recordPrimaryActionInput(player, pressed);\n"
                        + "                Entity vehicle"),
                "one return-capsule input packet is recorded and executed through one service entry");
    }

    private static void reusableCapsuleParticlesKeepTheCompleteNtmContract() throws Exception {
        String shock = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsuleShockSmokeParticle.java"));
        String flame = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsuleGasFlameParticle.java"));
        String visuals = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/capsule/ReturnCapsuleVisuals.java"));
        String shockJson = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/particles/return_capsule_shock_smoke.json"));
        String flameJson = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/particles/return_capsule_gas_flame.json"));

        assertTrue(shock.contains("QUAD_COUNT = 6")
                        && shock.contains("new Random(renderId)")
                        && shock.contains("geometry.nextGaussian() - 1.0D")
                        && shock.contains("xd *= DAMPING")
                        && shock.contains("move(xd, yd, zd)")
                        && shock.indexOf("xd *= DAMPING") < shock.indexOf("move(xd, yd, zd)")
                        && !shock.contains("one deterministic grey smoke sprite"),
                "NTM shock smoke retains six stable quads, Gaussian offsets and pre-move damping");
        assertTrue(visuals.contains("int count = 1 + capsule.level().random.nextInt(3)")
                        && visuals.contains("if (!capsule.hasPlayerPassenger()) return;")
                        && visuals.contains("double strength = 1.0D + capsule.level().random.nextGaussian()")
                        && visuals.contains("Mth.TWO_PI / count")
                        && visuals.contains("capsule.flightVelocity()")
                        && visuals.contains("capsule.getY() - groundY < 10.0D")
                        && visuals.contains("groundY + 1.0D")
                        && visuals.contains("addAlwaysVisibleParticle")
                        && visuals.contains("RETURN_CAPSULE_SHOCK_SMOKE.get(), true")
                        && visuals.contains("RETURN_CAPSULE_GAS_FLAME.get(), true")
                        && !visuals.contains("count * 6"),
                "NTM terrain shock uses one to three equal-angle forced particle states at full strength");
        assertTrue(shockJson.contains("wildfires:third_party/ntm_space/particle_base")
                        && Files.exists(Path.of(
                        "src/main/resources/assets/wildfires/textures/particle/third_party/ntm_space/particle_base.png")),
                "NTM particle_base is the sole shock-smoke sprite");
        assertTrue(flame.contains("this.xd = this.xd * 0.1D + speedX")
                        && flame.contains("this.friction = 0.96F")
                        && flame.contains("yd += 0.004D")
                        && flame.contains("xd *= 0.75D")
                        && flame.contains("yd += 0.005D")
                        && flame.contains("this.quadSize = 0.65F")
                        && flame.contains("7 - age * 8 / lifetime")
                        && flame.contains("return 0xF000F0"),
                "NTM gas flame retains legacy smoke seed, damping, lift, scale and fullbright");
        assertTrue(flameJson.indexOf("minecraft:generic_0")
                        < flameJson.indexOf("minecraft:generic_7"),
                "NTM gas flame keeps the canonical smoke frame index table");

        Path particle = Path.of(
                "src/main/resources/assets/wildfires/textures/particle/third_party/ntm_space/particle_base.png");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(particle));
        StringBuilder actual = new StringBuilder(64);
        for (byte value : digest) actual.append(String.format("%02x", value & 0xFF));
        assertEquals("ed0a1b6efd067b3ea7803e91904fbc9ebc71b3245dbcb8cc521ff6115f0ae0eb",
                actual.toString(), "NTM particle_base SHA-256");
    }

    private static void reusableCapsuleRenderAndTransferSourceContractsStayAligned() throws Exception {
        String mixins = Files.readString(Path.of("src/main/resources/wildfires.mixins.json"));
        String capsuleRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReusableReturnCapsuleRenderer.java"));
        String passengerPose = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsulePassengerPose.java"));
        String passengerMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/ReturnCapsuleLivingEntityRendererMixin.java"));
        String playerRendererMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/ReturnCapsulePlayerRendererMixin.java"));
        String entityRendererMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/ReturnCapsuleEntityRendererMixin.java"));
        String cameraMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/ReturnCapsuleCameraMixin.java"));
        String coreRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/StationCoreRenderer.java"));
        String objModels = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/NtmSpaceObjModels.java"));
        String engineRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/AntimatterTestEngineRenderer.java"));
        String radiantShader = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/AntimatterRadiantDriveShader.java"));
        String engineEntity = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/AntimatterTestEngineBlockEntity.java"));
        String coreItemModel = Files.readString(Path.of(
                "src/main/resources/assets/wildfires/models/item/station_core.json"));
        String coreEntity = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/StationCoreBlockEntity.java"));
        String coreBlock = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/StationCoreBlock.java"));
        String capsuleRegistration = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/SpaceContentRegister.java"));
        String flightSound = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsuleFlightSound.java"));
        String receiving = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsuleReceivingScreen.java"));
        String transition = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/ReturnCapsuleClientTransition.java"));
        String orbitSky = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        String ascentAtmosphere = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/NtmAscentAtmosphereVisuals.java"));
        String ascentFogMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/NtmAscentFogRendererMixin.java"));
        String ascentLevelMixin = Files.readString(Path.of(
                "src/main/java/first/wildfires/mixin/minecraft/NtmAscentLevelRendererMixin.java"));
        String ascentPlanet = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/NtmAscentPlanetRenderer.java"));
        String overworldSky = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/celestial/CelestialRenderer.java"));
        String clientForgeEvents = Files.readString(Path.of(
                "src/main/java/first/wildfires/event/forgeEvent/ClientForgeEvent.java"));
        String celestialStateCache = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/celestial/CelestialClientStateCache.java"));
        String service = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/capsule/ReturnCapsuleService.java"));
        String capsuleEntity = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/capsule/ReusableReturnCapsuleEntity.java"));
        String stationServerEvents = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/station/StationServerEvents.java"));
        String stationCoreService = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/StationCoreService.java"));
        String stationStructure = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/StationStructureBlock.java"));
        String stationFluidPort = Files.readString(Path.of(
                "src/main/java/first/wildfires/space/content/StationFluidPortBlockEntity.java"));
        String stationOverlay = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/StationCoreOverlay.java"));
        String armedPacket = Files.readString(Path.of(
                "src/main/java/first/wildfires/network/ReturnCapsuleTransitionArmedPacket.java"));
        String abortPacket = Files.readString(Path.of(
                "src/main/java/first/wildfires/network/ReturnCapsuleTransitionAbortPacket.java"));
        String network = Files.readString(Path.of(
                "src/main/java/first/wildfires/register/NetworkPacketRegister.java"));

        assertTrue(mixins.contains("minecraft.CompositeRenderableComponentMixin"),
                "Forge OBJ component visibility mixin is registered on the client");
        assertTrue(capsuleRenderer.contains(
                        "Set.of(\"DropPod\", \"Door\", \"Legs\")")
                        && capsuleRenderer.contains("Set.of(\"Airbrake0\")")
                        && !capsuleRenderer.contains("Set.of(\"HeatShield\")")
                        && capsuleRenderer.contains("index < 4")
                        && capsuleRenderer.contains("visual.orbitRenderAll()"),
                "surface pod excludes HeatShield, repeats Airbrake0 four times, and orbit renders all");
        int firstYaw = passengerPose.indexOf("rotationDegrees(yawAroundPitch)");
        int pitch = passengerPose.indexOf("rotationDegrees(pitch)", firstYaw);
        int inverseYaw = passengerPose.indexOf("rotationDegrees(-yawAroundPitch)", pitch);
        assertTrue(capsuleRenderer.contains("ReturnCapsulePassengerPose.applyAttitude(")
                        && firstYaw >= 0 && pitch > firstYaw && inverseYaw > pitch,
                "pod and passenger share the exact NTM yaw-conjugated pitch order");
        assertTrue(mixins.contains("minecraft.ReturnCapsuleLivingEntityRendererMixin")
                        && mixins.contains("minecraft.ReturnCapsuleCameraMixin")
                        && mixins.contains("minecraft.ReturnCapsuleEntityRendererMixin")
                        && mixins.contains("minecraft.ReturnCapsulePlayerRendererMixin")
                        && passengerMixin.contains("@Mixin(LivingEntityRenderer.class)")
                        && passengerMixin.contains("shift = At.Shift.BEFORE")
                        && passengerMixin.contains("entity.getVehicle() instanceof ReusableReturnCapsuleEntity")
                        && passengerMixin.contains("ReturnCapsulePassengerPose.applyAttitudeToPassenger(")
                        && passengerPose.contains("Mth.rotLerp(partialTick, capsule.yRotO, capsule.getYRot())")
                        && passengerPose.contains("capsule.phaseTicks() + partialTick")
                        && passengerPose.contains("interpolatedSeatPosition")
                        && passengerPose.contains("interpolatedRenderSeatPosition")
                        && passengerPose.contains("entity.xOld")
                        && passengerPose.contains("entity.xo")
                        && passengerPose.contains("passengerRenderOffset")
                        && passengerPose.contains("ReusableReturnCapsuleEntity.CAPSULE_SEAT_OFFSET")
                        && playerRendererMixin.contains("@Mixin(PlayerRenderer.class)")
                        && playerRendererMixin.contains("@Inject(method = \"getRenderOffset\"")
                        && playerRendererMixin.contains("passengerRenderOffset(")
                        && playerRendererMixin.contains("callback.getReturnValue().add(")
                        && entityRendererMixin.contains("@Mixin(EntityRenderer.class)")
                        && entityRendererMixin.contains("@Inject(method = \"shouldRender\"")
                         && entityRendererMixin.contains("capsule.hasPassenger(entity)")
                         && entityRendererMixin.contains("callback.setReturnValue(true)")
                         && cameraMixin.contains("@Mixin(Camera.class)")
                         && cameraMixin.contains("target = \"Lnet/minecraft/client/Camera;getMaxZoom(D)D\"")
                         && cameraMixin.contains("if (!detached ||")
                         && cameraMixin.contains("interpolatedRenderSeatPosition(")
                         && cameraMixin.contains("capsuleRenderSeat.subtract(playerCameraAnchor)")
                         && cameraMixin.contains("setPosition(current.add(")
                         && cameraMixin.contains("if (detached) return;")
                         && cameraMixin.contains("interpolatedSeatPosition(capsule, partialTick)")
                         && cameraMixin.contains("attitude.transform(offset)")
                         && cameraMixin.contains("setPosition(seat.add(offset.x, offset.y, offset.z))")
                        && cameraMixin.contains("rotation.premul(attitude).normalize()")
                         && cameraMixin.contains("forwards.set(0.0F, 0.0F, 1.0F).rotate(rotation)")
                         && capsuleEntity.contains("CAPSULE_HEIGHT - 3.0D"),
                 "seat, third-person camera/body and first-person attitude share the exact pod domains");
        assertTrue(coreRenderer.contains("Set.of(\"Port\")")
                        && coreRenderer.contains("Set.of(\"ArmZP\")")
                        && coreRenderer.contains("index < 4")
                        && coreRenderer.contains("index * 90.0F")
                        && coreRenderer.contains("return 256;"),
                "station port repeats the single ArmZP mesh four times and remains visible for 256 blocks");
        assertTrue(coreRenderer.contains("poses.translate(0.5D, 1.0D, 0.5D)")
                        && !coreRenderer.contains("poses.scale(")
                        && coreItemModel.contains("\"scale\": [0.125, 0.125, 0.125]")
                        && coreItemModel.contains("\"translation\": [0, 2, 0]"),
                "station core keeps NTM 1:1 world scale and its ten-pixel inventory presentation");
        assertTrue(objModels.contains("docking_port.obj\"), false, true, true,")
                        && objModels.contains("false, \"wildfires:models/third_party/ntm_space/docking_port.mtl\"")
                        && coreRenderer.contains("LevelRenderer.getLightColor")
                        && coreRenderer.contains("core.getBlockPos().above(2)")
                        && coreRenderer.contains("RenderType::entityCutoutNoCull, environmentLight")
                        && engineRenderer.contains("Stage.AFTER_PARTICLES")
                        && engineRenderer.contains("DEFERRED.add")
                        && engineRenderer.contains("return 320;")
                        && engineEntity.contains("worldPosition.offset(5, 5, 130)")
                        && radiantShader.contains("RandomnessController uses Random.Range(-1, 1)")
                        && radiantShader.contains("0.2D * KSP_TO_MC * throttle"),
                "station core uses environment light while the complete plume is deferred and never origin-culled");
        assertTrue(engineEntity.contains("STARTUP_TICKS = 60")
                        && engineEntity.contains("SHUTDOWN_TICKS = 20")
                        && engineEntity.contains("ramp_ticks")
                        && engineEntity.contains("return STARTUP_TICKS")
                        && engineEntity.contains("return SHUTDOWN_TICKS")
                        && radiantShader.contains("OUTER_BRIGHTNESS_MULTIPLIER = 1.35F")
                        && radiantShader.contains("brightness *= OUTER_BRIGHTNESS_MULTIPLIER"),
                "antimatter output reaches full power in sixty ticks, shuts down in twenty, and only OuterBeam receives the requested brightness lift");
        ByteBuffer billboard = ByteBuffer.wrap(Files.readAllBytes(Path.of(
                "src/main/resources/assets/wildfires/models/effect/fx-billboard-generic-1.mesh")));
        int billboardVertices = billboard.getInt();
        float minBillboardY = Float.POSITIVE_INFINITY;
        float maxBillboardY = Float.NEGATIVE_INFINITY;
        float maxBillboardZ = 0.0F;
        for (int index = 0; index < billboardVertices; index++) {
            billboard.getFloat();
            float y = billboard.getFloat();
            float z = billboard.getFloat();
            minBillboardY = Math.min(minBillboardY, y);
            maxBillboardY = Math.max(maxBillboardY, y);
            maxBillboardZ = Math.max(maxBillboardZ, Math.abs(z));
            billboard.position(billboard.position() + 20);
        }
        assertTrue(billboardVertices == 4 && maxBillboardY - minBillboardY > 0.99F
                        && maxBillboardZ < 0.0001F,
                "Waterfall billboard occupies camera-facing XY instead of collapsing into plume-axis XZ");
        assertTrue(coreEntity.contains("clientPreviousArmRotation = core.clientArmRotation")
                        && coreEntity.contains("clientArmRotation + 2.25F")
                        && coreEntity.contains("clientArmRotation - 2.25F")
                        && coreEntity.contains("reservedCapsuleId")
                        && coreEntity.contains("public boolean reserveDock")
                        && coreEntity.contains("public boolean completeDock")
                        && coreBlock.contains("StationCoreBlockEntity::clientTick")
                        && coreRenderer.contains("core.clientArmRotation(partialTick)"),
                "station reservation is separate while true docking alone drives NTM clamp animation");
        assertTrue(capsuleRegistration.contains(".sized(2.0F, 4.0F)")
                        && service.contains("CAPSULE_HEIGHT = 4.0D")
                        && service.contains("CAPSULE_HALF_WIDTH = 1.0D")
                        && service.contains("capsule.setFlightVelocity(0.1D)")
                        && service.contains("capsule.getY() + 0.1D * NTM_MOTION_MULTIPLIER")
                        && service.contains("capsule.setFlightVelocity(-0.1D)")
                        && service.contains("capsule.getY() - 0.1D * NTM_MOTION_MULTIPLIER")
                        && service.contains("motion.scale(NTM_MOTION_MULTIPLIER)")
                        && service.contains("stationUndockEnd(ticket.sourcePosition()).y")
                        && service.contains("capsule.getY() - targetHeight <= 0.25D")
                        && !service.contains("NTM_MANEUVER_SUBSTEPS")
                        && capsuleEntity.contains("clientLerpSteps = Math.max(1, steps)")
                        && !capsuleEntity.contains("clientLerpSteps = 1;")
                        && service.contains("landed.y + altitude")
                        && service.contains("CelestialTransferProfile.resolve")
                        && service.contains("getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES")
                        && flightSound.contains("this.volume = 1.0F"),
                "pod bounds, fourfold NTM motion, client convergence, body-relative reentry and sound match NTM");
        assertTrue(ascentPlanet.contains("RenderSystem.enableDepthTest()")
                        && ascentPlanet.contains("RenderSystem.depthMask(true)")
                        && ascentPlanet.contains("RenderSystem.enableCull()")
                        && !ascentPlanet.contains("RenderSystem.depthMask(false);\n            RenderSystem.disableCull();\n            RenderSystem.disableBlend();"),
                "AFTER_SKY square-planet rendering restores depth writes and culling before pod and passenger geometry");
        assertTrue(!receiving.contains("super.render(")
                        && receiving.contains("graphics.fill(0, 0, width, height, 0xFF000000)")
                        && receiving.contains("targetSceneReadyForPreview()")
                        && transition.contains("return toStation ? orbitSceneReady()")
                        && transition.contains("!armed() || !targetGraphReady()"),
                "capsule receiving screen hides vanilla loading but reveals only a proven live target frame");
        assertTrue(capsuleEntity.contains("public boolean hasPlayerPassenger()")
                        && capsuleEntity.contains("passenger instanceof Player && passenger.getVehicle() == this")
                        && capsuleEntity.contains("protected boolean canAddPassenger(Entity passenger)")
                        && flightSound.contains("return capsule.hasPlayerPassenger()")
                        && ascentAtmosphere.contains("if (!(observer instanceof Player)")
                        && ascentAtmosphere.contains("capsule.getFirstPassenger() != observer"),
                "only a directly seated player can activate capsule ascent, exhaust and flight audio");
        assertTrue(mixins.contains("minecraft.NtmAscentFogRendererMixin")
                        && mixins.contains("minecraft.NtmAscentLevelRendererMixin")
                        && ascentAtmosphere.contains("frame.profile().curvature(frame.altitude())")
                        && ascentAtmosphere.contains("state != ReturnCapsuleState.SURFACE_LAUNCHING")
                        && ascentAtmosphere.contains("state != ReturnCapsuleState.ASCENT_TRANSITION")
                        && ascentAtmosphere.contains("capsule.activeBodyId()")
                        && ascentAtmosphere.contains("definition.surfaceDimension()")
                        && ascentAtmosphere.contains("level.dimension().location()::equals")
                        && ascentFogMixin.contains("@Inject(method = \"setupColor\", at = @At(\"TAIL\"))")
                        && ascentFogMixin.contains("RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F)")
                        && ascentLevelMixin.contains("@Inject(method = \"renderClouds\"")
                        && ascentLevelMixin.contains("NtmAscentAtmosphereVisuals.hideLocalClouds")
                        && ascentLevelMixin.contains("@Inject(method = \"renderSnowAndRain\"")
                        && ascentLevelMixin.contains("@Inject(method = \"tickRain\"")
                        && ascentLevelMixin.contains("NtmAscentAtmosphereVisuals.hideLocalWeather")
                        && ascentLevelMixin.contains("@Inject(method = \"renderChunkLayer\"")
                        && ascentLevelMixin.contains("NtmAscentAtmosphereVisuals.hideLocalTerrain")
                        && overworldSky.contains("NtmAscentAtmosphereVisuals.fadeSky")
                        && overworldSky.contains("state.weatherVisibility()) * atmosphere"),
                "body-relative NTM atmosphere curve affects only the capsule's bound surface sky, weather, twilight and fog");
        assertTrue(clientForgeEvents.contains("RenderLevelStageEvent.Stage.AFTER_SKY")
                        && clientForgeEvents.contains("NtmAscentPlanetRenderer.render(")
                        && clientForgeEvents.contains(".ascentBody(minecraft.level, event.getCamera())")
                        && clientForgeEvents.contains("CelestialClientStateCache.stateForBoundSurfaceAscent")
                        && celestialStateCache.contains("ExistingCelestialEphemeris.INSTANCE.state")
                        && celestialStateCache.contains("if (registered != null) return registered")
                        && ascentPlanet.contains("GenesisPlanetMesh.ensure()")
                        && ascentPlanet.contains("GenesisPlanetMesh.drawSurface")
                        && ascentPlanet.contains("GenesisPlanetMesh.drawAtmosphere")
                        && ascentPlanet.contains("OrbitBodyTextureManager.surface(")
                        && ascentPlanet.contains("OrbitBodyTextureManager.clouds(")
                        && ascentPlanet.contains("bodyRotation(bodyId, state.calendarTicks())")
                        && ascentPlanet.contains("incomingLightDirection(bodyId, state)")
                        && ascentPlanet.contains("outerShellExitDistance(rotation")
                        && ascentPlanet.contains("outerShellRadiusMultiplier(definition.visual())")
                        && ascentAtmosphere.contains("centerDistance <= outerShellExitDistance")
                        && !ascentPlanet.contains("state.sun().geocentricPosition()")
                        && !ascentPlanet.contains("earthToSun")
                        && ascentPlanet.indexOf("poseStack.mulPose(rotation)")
                        < ascentPlanet.indexOf("drawClouds(")
                        && !ascentPlanet.contains("planet.png")
                        && !ascentPlanet.contains("new GenesisPlanetMesh")
                        && !ascentPlanet.contains("new OrbitBodyTextureManager")
                        && capsuleEntity.contains("EntityDataSerializers.STRING")
                        && capsuleEntity.contains("ticket.bodyId().toString()")
                        && capsuleEntity.contains("activeBodyId()"),
                "NTM high-altitude reveal is body/data driven and uses cached Genesis cube, rigid clouds, atmosphere and physical incoming light");
        assertTrue(stationCoreService.contains("new ArrayList<>(49)")
                        && stationCoreService.contains("for (int x = -2; x <= 2; x++)")
                        && stationCoreService.contains("for (int z = -2; z <= 2; z++)")
                        && stationCoreService.contains("coreForTopCenterBlock")
                        && stationCoreService.contains("interactTopCenter")
                        && stationStructure.contains("StationCoreService.interactTopCenter")
                        && capsuleEntity.contains("getDismountLocationForPassenger")
                        && capsuleEntity.contains("stationDismountPosition")
                        && coreEntity.contains("ForgeCapabilities.FLUID_HANDLER")
                        && coreEntity.contains("value.fuelTank().fill(resource, action)")
                        && stationCoreService.contains("new ArrayList<>(12)")
                        && stationCoreService.contains("fluidPortOffsets()")
                        && stationStructure.contains("BooleanProperty.create(\"fluid_port\")")
                        && stationFluidPort.contains("target.fill(resource, action)")
                        && stationFluidPort.contains("return FluidStack.EMPTY")
                        && !stationFluidPort.contains("static void tick")
                        && stationOverlay.contains("RenderGuiOverlayEvent.Post")
                        && stationOverlay.contains("coreForTopCenterBlock")
                        && !stationServerEvents.contains("StationServerEvents::onServerTick")
                        && !stationServerEvents.contains("CORE_REPAIR_QUEUE"),
                "NTM 5x5x2 top-centre interaction, twelve no-tick Forge-water ports and event-only proxy ownership remain complete");
        assertTrue(transition.contains("REQUIRED_STABLE_TICKS = 2")
                        && transition.contains("REQUIRED_RENDERED_FRAMES = 2")
                        && transition.contains("renderedTargetFrames >= REQUIRED_RENDERED_FRAMES")
                        && transition.contains("targetGraphReady()")
                        && transition.contains("holdAndRepairTargetPassengerGraph()")
                        && transition.contains("minecraft.level.entitiesForRendering()")
                        && transition.contains("capsule.getUUID().equals(capsuleId)")
                        && transition.contains("minecraft.player.getVehicle() == tracked")
                        && transition.contains("tracked.hasPassenger(minecraft.player)")
                        && transition.contains("if (!tracked.getPassengers().isEmpty()) tracked.ejectPassengers()")
                        && transition.contains("minecraft.player.setDeltaMovement(Vec3.ZERO)")
                        && transition.contains("minecraft.player.fallDistance = 0.0F")
                        && capsuleEntity.contains("if (!level().isClientSide() && !transferDismountInProgress")
                        && capsuleEntity.contains("return level().isClientSide() || capsuleState().interactive()")
                        && stationServerEvents.contains("if (!event.getEntity().level().isClientSide()")
                        && transition.contains("ReturnCapsuleTransitionArmedPacket")
                        && transition.indexOf("sendArmedAcknowledgementIfDue(false)")
                        < transition.indexOf("holdAndRepairTargetPassengerGraph()")
                        && transition.indexOf("holdAndRepairTargetPassengerGraph()")
                        < transition.indexOf("if (!targetGraphReady())"),
                "target transfer lets vanilla rebuild and accept the client graph, removes only exact stale capsule links, then waits for rendered frames");
        assertTrue(transition.contains("if (!toStation && serverConfirmed && targetGraphReady())")
                        && transition.contains("markOrbitSceneRendered(boolean contextReady")
                        && transition.contains("orbitContextReady && orbitCelestialReady && orbitBodyRendered")
                        && orbitSky.contains("body.body().equals(context.currentBody())")
                        && orbitSky.contains("markOrbitSceneRendered(true, true, currentBodyRendered)")
                        && orbitSky.contains("return depthWritten;"),
                "orbit release requires the current body's Genesis cube, while surface release uses ordinary frames");
        int targetVehicleAdded = service.indexOf("destination.addDuringTeleport(moved)");
        int targetPlayerTeleported = service.indexOf("player.teleportTo(destination", targetVehicleAdded);
        int targetPassengerMounted = service.indexOf("player.startRiding(moved, true)", targetPlayerTeleported);
        assertTrue(targetVehicleAdded >= 0
                        && targetPlayerTeleported > targetVehicleAdded
                        && targetPassengerMounted > targetPlayerTeleported
                        && !service.contains("capsule.changeDimension(destination"),
                "Forge 1.20.1 transfer tracks the Genesis-style target vehicle before Respawn and remount");
        assertTrue(service.contains("TicketType<UUID> TRANSFER_TICKET")
                        && service.contains("addRegionTicket(TRANSFER_TICKET, currentPos")
                        && service.contains("removeRegionTicket(TRANSFER_TICKET, new ChunkPos(previous)")
                        && service.contains("ticket.ticketId()")
                        && capsuleEntity.contains("flightTicketChunk")
                        && !service.contains("setChunkForced(")
                        && !service.contains("unloadChunk("),
                "each capsule releases only its ticket UUID and cannot unload a co-located player's chunk");
        assertTrue(service.contains("Stage.CLIENT_ARMED")
                        && service.indexOf("new StationContextPacket(ObservationContext.from(station")
                        < service.indexOf("new ReturnCapsuleTransitionPacket(",
                                service.indexOf("private static void sendTransferHandshake"))
                        && service.contains("ClientboundSetPassengersPacket")
                        && service.contains("PASSENGER_GRAPH_RETRY_TICKS = 5")
                        && service.contains("ticket.stage() != ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED")
                        && armedPacket.contains("confirmClientArmed")
                        && network.contains("Version = \"17\"")
                        && network.contains("ReturnCapsuleTransitionArmedPacket.class")
                        && network.indexOf("ReturnCapsuleTransitionCompletePacket.class")
                        < network.indexOf("ReturnCapsuleTransitionAbortPacket.class")
                        && abortPacket.contains("ReturnCapsuleClientTransition.abort(ticketId, capsuleId)")
                        && transition.contains("if (!ticket.equals(ticketId) || !capsule.equals(capsuleId)) return;")
                        && transition.indexOf("complete();", transition.indexOf("public static void abort"))
                        < transition.indexOf("releaseCapturedFrame();", transition.indexOf("public static void abort"))
                        && transition.indexOf("releaseCapturedFrame();", transition.indexOf("public static void abort"))
                        < transition.indexOf("minecraft.setScreen(null)", transition.indexOf("public static void abort"))
                        && transition.contains("RenderTarget frame = captured;")
                        && transition.contains("captured = null;")
                        && transition.contains("RenderSystem.recordRenderCall(frame::destroyBuffers)")
                        && clientForgeEvents.contains("ReturnCapsuleClientTransition.shutdown()"),
                "source pre-arm, target passenger replay, exact recovery abort and GPU capture cleanup remain registered");
    }

    private static void reusableCapsuleTransitionUsesOnlyTheCapturedGenesisFrame() throws Exception {
        boolean removed;
        try {
            Class.forName("first.wildfires.client.space.ReturnCapsuleTransitionOverlay");
            removed = false;
        } catch (ClassNotFoundException expected) {
            removed = true;
        }
        assertTrue(removed, "synthetic capsule transition overlay was restored");
        Class<?> receiving = Class.forName(
                "first.wildfires.client.space.ReturnCapsuleReceivingScreen");
        assertTrue(net.minecraft.client.gui.screens.ReceivingLevelScreen.class
                        .isAssignableFrom(receiving)
                        && receiving.getDeclaredMethod("render", net.minecraft.client.gui.GuiGraphics.class,
                        int.class, int.class, float.class) != null
                        && receiving.getDeclaredMethod("tick") != null,
                "captured-frame receiving screen contract is missing");
    }

    private static void orbitVisualFrameCacheIsExactAndLossless() {
        Object context = new Object();
        Object state = new Object();
        java.util.concurrent.atomic.AtomicInteger builds = new java.util.concurrent.atomic.AtomicInteger();
        OrbitVisualFrameCache.SingleEntryCache<Object> cache =
                new OrbitVisualFrameCache.SingleEntryCache<>((ignoredContext, ignoredState,
                                                               ignoredGameTime, ignoredCalendarTicks,
                                                               ignoredCalendarRate, ignoredMonthLength) -> {
            builds.incrementAndGet();
            return new Object();
        });
        Object first = cache.get(context, state, 12.25D, 34.5D, 1.0D, 8);
        Object repeated = cache.get(context, state, 12.25D, 34.5D, 1.0D, 8);
        assertTrue(first == repeated && builds.get() == 1,
                "identical frame inputs reuse the exact immutable result");

        java.util.concurrent.atomic.AtomicBoolean failBuild = new java.util.concurrent.atomic.AtomicBoolean();
        OrbitVisualFrameCache.SingleEntryCache<Object> transactionalCache =
                new OrbitVisualFrameCache.SingleEntryCache<>((ignoredContext, ignoredState,
                                                               ignoredGameTime, ignoredCalendarTicks,
                                                               ignoredCalendarRate, ignoredMonthLength) -> {
                    if (failBuild.get()) throw new IllegalStateException("expected factory failure");
                    return new Object();
                });
        Object retained = transactionalCache.get(context, state, 1.0D, 2.0D, 3.0D, 8);
        failBuild.set(true);
        try {
            transactionalCache.get(context, state, 2.0D, 2.0D, 3.0D, 8);
            throw new AssertionError("failing frame construction must propagate");
        } catch (IllegalStateException expected) {
            assertTrue("expected factory failure".equals(expected.getMessage()),
                    "frame factory failure remains visible");
        }
        failBuild.set(false);
        assertTrue(transactionalCache.get(context, state, 1.0D, 2.0D, 3.0D, 8) == retained,
                "failed frame construction does not poison the prior exact cache entry");

        Object[] contexts = {context, new Object()};
        Object[] states = {state, new Object()};
        double[] gameTimes = {12.25D, Math.nextUp(12.25D)};
        double[] calendarTicks = {34.5D, Math.nextUp(34.5D)};
        double[] calendarRates = {1.0D, Math.nextUp(1.0D)};
        int[] monthLengths = {8, 9};
        cache.clear();
        Object previous = cache.get(contexts[0], states[0], gameTimes[0], calendarTicks[0],
                calendarRates[0], monthLengths[0]);
        for (int changed = 0; changed < 6; changed++) {
            Object next = cache.get(contexts[changed == 0 ? 1 : 0], states[changed == 1 ? 1 : 0],
                    gameTimes[changed == 2 ? 1 : 0], calendarTicks[changed == 3 ? 1 : 0],
                    calendarRates[changed == 4 ? 1 : 0], monthLengths[changed == 5 ? 1 : 0]);
            assertTrue(next != previous,
                    "every frame cache identity or raw-bit field independently invalidates");
            cache.clear();
            previous = cache.get(contexts[0], states[0], gameTimes[0], calendarTicks[0],
                    calendarRates[0], monthLengths[0]);
        }

        ObservationJourney cruise = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        ObservationContext visualContext = context(EARTH, Optional.of(cruise));
        CelestialState visualState = state();
        OrbitVisualFrameCache.reset();
        OrbitVisualRules.Frame direct = OrbitVisualRules.frame(
                visualContext, visualState, 100.25D, 0.0D, 1.0D, 8);
        OrbitVisualRules.Frame cached = OrbitVisualFrameCache.frame(
                visualContext, visualState, 100.25D, 0.0D, 1.0D, 8);
        assertTrue(direct.equals(cached),
                "frame cache preserves every orbit and relativistic visual value");
        assertTrue(cached == OrbitVisualFrameCache.frame(
                        visualContext, visualState, 100.25D, 0.0D, 1.0D, 8),
                "sky and lightmap can share one exact frame object");
        OrbitVisualFrameCache.reset();
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

    private static void interSystemIngressLocksTargetAndSweepsEverySatelliteSystemCube() {
        long duration = 1_000L;
        CelestialState state = stateWithJovianMoonsAcrossIngressLine(
                new CelestialVector(20.0D, 1.0704D, 0.0D));
        ObservationJourney cruise = new ObservationJourney(EARTH, EUROPA,
                StationJourneyPhase.CRUISE, 0L, duration);
        ObservationContext active = context(EARTH, Optional.of(cruise));
        CelestialVector stableStart = OrbitVisualRules.frame(context(EARTH, Optional.empty()),
                state, 0.0D, 0.0D, 0.0D).observerPosition();
        CelestialVector stableEnd = OrbitVisualRules.frame(context(EUROPA, Optional.empty()),
                state, duration, 0.0D, 0.0D).observerPosition();
        Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> ephemeris = testEphemeris(state);
        OrbitVisualRules.BodyEphemeris earth = ephemeris.get(EARTH);
        OrbitVisualRules.BodyEphemeris europa = ephemeris.get(EUROPA);
        OrbitVisualRules.BodyEphemeris jupiter = ephemeris.get(JUPITER);
        double legacyMinimum = Double.POSITIVE_INFINITY;
        CelestialVector legacyClosest = stableStart;
        for (int step = 0; step <= 2_000; step++) {
            double progress = step / 2_000.0D;
            CelestialVector legacy = OrbitVisualRules.safeTransferPosition(stableStart, stableEnd,
                    earth, europa, OrbitVisualRules.circularTransfer(progress));
            double distance = legacy.subtract(jupiter.position()).length();
            if (distance < legacyMinimum) {
                legacyMinimum = distance;
                legacyClosest = legacy;
            }
        }
        // A moon can occupy the otherwise untested lane around the parent. This is exactly the
        // missing target-system obstacle that the old endpoint-only curve could not see.
        state = stateWithJovianMoonsAcrossIngressLine(legacyClosest);
        ephemeris = testEphemeris(state);
        double ganymedeGuard = ephemeris.get(GANYMEDE).radius() * Math.sqrt(3.0D) * 1.05D;
        assertTrue(segmentDistanceToPoint(legacyClosest, legacyClosest,
                        ephemeris.get(GANYMEDE).position()) <= ganymedeGuard,
                "regression geometry places a target-system moon directly in the old Bezier lane");

        CelestialVector previous = OrbitVisualRules.frame(active, state, 0.0D, 0.0D, 0.0D)
                .observerPosition();
        assertVector(stableStart, previous, "inter-system ingress locks the departure orbit");
        for (int step = 1; step < 4_000; step++) {
            double tick = duration * step / 4_000.0D;
            CelestialVector current = OrbitVisualRules.frame(active, state, tick, 0.0D, 0.0D)
                    .observerPosition();
            for (ResourceLocation body : List.of(EARTH, MOON, JUPITER, IO, EUROPA, GANYMEDE)) {
                OrbitVisualRules.BodyEphemeris obstacle = ephemeris.get(body);
                double sweptDistance = segmentDistanceToPoint(previous, current,
                        obstacle.position());
                double guard = obstacle.radius() * Math.sqrt(3.0D) * 1.05D;
                assertTrue(sweptDistance > guard,
                        "Earth -> Europa swept ingress entered " + body + " at tick " + tick
                                + ": clearance=" + (sweptDistance - guard));
            }
            previous = current;
        }
        CelestialVector nearEnd = OrbitVisualRules.frame(active, state,
                duration - 1.0E-7D, 0.0D, 0.0D).observerPosition();
        assertTrue(nearEnd.subtract(stableEnd).length() < 1.0E-5D,
                "inter-system ingress ends at the locked Europa stable orbit");

        double startCalendar = 1_234_567.0D;
        double rate = 1_200.0D;
        long movingDuration = 600L;
        int routeIndex = 0;
        for (ResourceLocation movingTargetId : List.of(JUPITER, EUROPA)) {
            long gameStart = 10_000L + routeIndex++ * 1_000L;
            ObservationJourney movingCruise = new ObservationJourney(EARTH, movingTargetId,
                    StationJourneyPhase.CRUISE, gameStart, movingDuration);
            ObservationContext movingActive = context(EARTH, Optional.of(movingCruise));
            CelestialState previousState = authoritativeStateAt(startCalendar);
            OrbitVisualRules.Frame movingStartFrame = OrbitVisualRules.frame(movingActive,
                    previousState, gameStart, startCalendar, rate, 8);
            CelestialVector previousStation = movingStartFrame.observerPosition();
            double lockedHeading = movingStartFrame.viewRotationRadians();
            for (int elapsed = 1; elapsed < movingDuration; elapsed++) {
                double calendar = startCalendar + rate * elapsed;
                CelestialState currentState = authoritativeStateAt(calendar);
                OrbitVisualRules.Frame currentFrame = OrbitVisualRules.frame(movingActive,
                        currentState, gameStart + elapsed, calendar, rate, 8);
                CelestialVector currentStation = currentFrame.observerPosition();
                assertNear(lockedHeading, currentFrame.viewRotationRadians(), 1.0E-12D,
                        "cross-system cruise heading remains locked while target system moves");
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> previousBodies =
                        testEphemeris(previousState);
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> currentBodies =
                        testEphemeris(currentState);
                for (CelestialBodyState body : localSystemBodies(currentState, JUPITER)) {
                    double swept = movingSegmentDistance(previousStation, currentStation,
                            previousBodies.get(body.id()).position(),
                            currentBodies.get(body.id()).position());
                    double radius = currentBodies.get(body.id()).radius();
                    assertTrue(swept > radius * Math.sqrt(3.0D) * 1.05D,
                            "moving Earth -> " + movingTargetId + " ingress swept through "
                                    + body.id() + " between cruise ticks " + (elapsed - 1)
                                    + " and " + elapsed + ": swept=" + swept + ", guard="
                                    + (radius * Math.sqrt(3.0D) * 1.05D));
                }
                previousState = currentState;
                previousStation = currentStation;
            }
            double endCalendar = startCalendar + rate * movingDuration;
            CelestialState endState = authoritativeStateAt(endCalendar);
            CelestialVector movingNearEnd = OrbitVisualRules.frame(movingActive, endState,
                    gameStart + movingDuration - 1.0E-7D, endCalendar - rate * 1.0E-7D,
                    rate, 8).observerPosition();
            CelestialVector movingTarget = OrbitVisualRules.frame(
                    context(movingTargetId, Optional.empty()), endState,
                    gameStart + movingDuration, endCalendar, rate, 8).observerPosition();
            assertTrue(movingNearEnd.subtract(movingTarget).length() < 1.0E-5D,
                    "cross-system plan intercepts " + movingTargetId
                            + " authoritative end-of-cruise ephemeris: "
                            + movingNearEnd.subtract(movingTarget).length());
        }
    }

    private static void everySatelliteIngressClearsAllMovingBodiesAtAdverseArrivalPhases() {
        double startCalendar = 4_321_000.0D;
        double rate = 1.0D;
        long duration = 600L;
        List<ResourceLocation> satelliteTargets = new ArrayList<>();
        satelliteTargets.add(MOON);
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() != null) satelliteTargets.add(body.id());
        }
        for (ResourceLocation targetId : satelliteTargets) {
            CelestialState arrivalState = authoritativeStateAt(startCalendar + rate * duration);
            Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> arrivalBodies =
                    testEphemeris(arrivalState);
            OrbitVisualRules.BodyEphemeris target = arrivalBodies.get(targetId);
            OrbitVisualRules.BodyEphemeris primary = arrivalBodies.get(target.parent());
            CelestialVector targetFromPrimary = target.position().subtract(primary.position());
            double inwardAngle = Math.atan2(-targetFromPrimary.y(), -targetFromPrimary.x());
            if (inwardAngle < 0.0D) inwardAngle += Math.PI * 2.0D;
            long gameEnd = 8_000L + Math.round(inwardAngle / (Math.PI * 2.0D) * 8_000.0D);
            long gameStart = gameEnd - duration;
            ObservationJourney cruise = new ObservationJourney(EARTH, targetId,
                    StationJourneyPhase.CRUISE, gameStart, duration);
            ObservationContext active = context(EARTH, Optional.of(cruise));
            CelestialState previousState = authoritativeStateAt(startCalendar);
            CelestialVector previousStation = OrbitVisualRules.frame(active, previousState,
                    gameStart, startCalendar, rate, 8).observerPosition();
            for (int elapsed = 1; elapsed <= duration; elapsed++) {
                double calendar = startCalendar + rate * elapsed;
                CelestialState currentState = authoritativeStateAt(calendar);
                CelestialVector currentStation = OrbitVisualRules.frame(active, currentState,
                        gameStart + elapsed, calendar, rate, 8).observerPosition();
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> previousBodies =
                        testEphemeris(previousState);
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> currentBodies =
                        testEphemeris(currentState);
                for (Map.Entry<ResourceLocation, OrbitVisualRules.BodyEphemeris> entry
                        : currentBodies.entrySet()) {
                    OrbitVisualRules.BodyEphemeris currentBody = entry.getValue();
                    OrbitVisualRules.BodyEphemeris previousBody = previousBodies.get(entry.getKey());
                    double swept = movingSegmentDistance(previousStation, currentStation,
                            previousBody.position(), currentBody.position());
                    double guard = Math.max(previousBody.radius(), currentBody.radius())
                            * Math.sqrt(3.0D) * 1.05D;
                    assertTrue(swept > guard,
                            "adverse Earth -> " + targetId + " ingress swept through "
                                    + entry.getKey() + " between ticks " + (elapsed - 1)
                                    + " and " + elapsed + ": clearance=" + (swept - guard));
                }
                previousState = currentState;
                previousStation = currentStation;
            }
        }
    }

    private static void everySatelliteDepartureClearsItsMovingParentAtAdversePhases() {
        double startCalendar = 7_654_000.0D;
        double rate = 1.0D;
        long duration = 600L;
        List<ResourceLocation> satelliteSources = new ArrayList<>();
        satelliteSources.add(MOON);
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() != null) satelliteSources.add(body.id());
        }
        CelestialState departureState = authoritativeStateAt(startCalendar);
        Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> departureBodies =
                testEphemeris(departureState);
        for (ResourceLocation sourceId : satelliteSources) {
            OrbitVisualRules.BodyEphemeris source = departureBodies.get(sourceId);
            OrbitVisualRules.BodyEphemeris primary = departureBodies.get(source.parent());
            CelestialVector sourceFromPrimary = source.position().subtract(primary.position());
            double inwardAngle = Math.atan2(-sourceFromPrimary.y(), -sourceFromPrimary.x());
            if (inwardAngle < 0.0D) inwardAngle += Math.PI * 2.0D;
            long gameStart = 8_000L
                    + Math.round(inwardAngle / (Math.PI * 2.0D) * 8_000.0D);
            ResourceLocation targetId = sourceId.equals(MOON) ? MARS : EARTH;
            ObservationJourney cruise = new ObservationJourney(sourceId, targetId,
                    StationJourneyPhase.CRUISE, gameStart, duration);
            ObservationContext active = context(sourceId, Optional.of(cruise));
            CelestialState previousState = departureState;
            CelestialVector previousStation = OrbitVisualRules.frame(active, previousState,
                    gameStart, startCalendar, rate, 8).observerPosition();
            for (int elapsed = 1; elapsed <= duration; elapsed++) {
                double calendar = startCalendar + rate * elapsed;
                CelestialState currentState = authoritativeStateAt(calendar);
                CelestialVector currentStation = OrbitVisualRules.frame(active, currentState,
                        gameStart + elapsed, calendar, rate, 8).observerPosition();
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> previousBodies =
                        testEphemeris(previousState);
                Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> currentBodies =
                        testEphemeris(currentState);
                for (Map.Entry<ResourceLocation, OrbitVisualRules.BodyEphemeris> entry
                        : currentBodies.entrySet()) {
                    OrbitVisualRules.BodyEphemeris currentBody = entry.getValue();
                    OrbitVisualRules.BodyEphemeris previousBody = previousBodies.get(entry.getKey());
                    double swept = movingSegmentDistance(previousStation, currentStation,
                            previousBody.position(), currentBody.position());
                    double guard = Math.max(previousBody.radius(), currentBody.radius())
                            * Math.sqrt(3.0D) * 1.05D;
                    assertTrue(swept > guard,
                            "adverse " + sourceId + " -> " + targetId
                                    + " departure swept through " + entry.getKey()
                                    + " between ticks " + (elapsed - 1) + " and " + elapsed
                                    + ": clearance=" + (swept - guard));
                }
                previousState = currentState;
                previousStation = currentStation;
            }
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
        CelestialBodyState sun = body(SUN, null, testSunEclipticPosition(frame,
                settings.planetSettings().earthSemiMajorMillionKm()), 0.69634D);
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

    private static void sunEphemerisStaysOnEclipticThroughOrbitTransforms() {
        double distance = first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT
                .planetSettings().earthSemiMajorMillionKm();
        CelestialVector eclipticNorth = OrbitVisualRules.ntmFrameVector(
                new CelestialVector(0.0D, 0.0D, 1.0D));
        for (int sample = 0; sample < 16; sample++) {
            double calendarTicks = sample * 6.0D * CelestialMath.TICKS_IN_DAY;
            CelestialState state = authoritativeStateAt(calendarTicks);
            CelestialVector sun = state.sun().geocentricPosition();
            assertNear(0.0D, sun.z(), 1.0E-12D,
                    "orbit Sun has zero ecliptic latitude at sample " + sample);
            assertNear(distance, sun.length(), 1.0E-12D,
                    "orbit Sun keeps Earth semi-major distance at sample " + sample);

            CelestialVector ntmSun = OrbitVisualRules.ntmFrameVector(sun.normalized());
            assertNear(0.0D, ntmSun.dot(eclipticNorth), 1.0E-12D,
                    "NTM frame keeps Sun on ecliptic at sample " + sample);
            OrbitVisualRules.Frame visualFrame = OrbitVisualRules.frame(
                    context(EARTH, Optional.empty()), state, sample * 173.0D,
                    calendarTicks, 1.0D, 8);
            org.joml.Vector3f viewedSun = new org.joml.Vector3f(
                    (float) ntmSun.x(), (float) ntmSun.y(), (float) ntmSun.z());
            org.joml.Vector3f viewedNorth = new org.joml.Vector3f(
                    (float) eclipticNorth.x(), (float) eclipticNorth.y(),
                    (float) eclipticNorth.z());
            org.joml.Quaternionf view = OrbitVisualRules.frameViewOrientation(visualFrame);
            view.transform(viewedSun);
            view.transform(viewedNorth);
            assertNear(0.0D, viewedSun.dot(viewedNorth), 2.0E-6D,
                    "shared orbit camera keeps Sun on rotated ecliptic at sample " + sample);
        }
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

    private static void cachedBodyRotationsMatchLegacyBits() {
        List<ResourceLocation> bodies = new ArrayList<>();
        bodies.add(EARTH);
        bodies.add(MOON);
        for (CelestialBodies body : CelestialBodies.values()) {
            bodies.add(body.id());
        }
        bodies.add(id("unknown_rotation_body"));
        double[] ticks = {-1.0E12D, -24_000.25D, -0.0D, 0.0D, 1.0D,
                12_345.75D, 24_000.0D, 1.0E12D};
        for (ResourceLocation body : bodies) {
            for (double calendarTicks : ticks) {
                org.joml.Quaternionf expected = legacyBodyRotation(body, calendarTicks);
                org.joml.Quaternionf actual = OrbitVisualRules.bodyRotation(body, calendarTicks);
                assertRawFloat(expected.x(), actual.x(), "cached body rotation x " + body);
                assertRawFloat(expected.y(), actual.y(), "cached body rotation y " + body);
                assertRawFloat(expected.z(), actual.z(), "cached body rotation z " + body);
                assertRawFloat(expected.w(), actual.w(), "cached body rotation w " + body);
                if (expected == actual) {
                    throw new AssertionError("body rotation unexpectedly reused a mutable quaternion "
                            + body);
                }
            }
        }
    }

    private static org.joml.Quaternionf legacyBodyRotation(ResourceLocation body,
                                                             double calendarTicks) {
        CelestialVector renderAxis = OrbitVisualRules.ntmFrameVector(
                OrbitVisualRules.spinAxisEcliptic(body)).normalized();
        org.joml.Vector3f axis = new org.joml.Vector3f((float) renderAxis.x(),
                (float) renderAxis.y(), (float) renderAxis.z());
        return new org.joml.Quaternionf()
                .rotationTo(new org.joml.Vector3f(0.0F, 1.0F, 0.0F), axis)
                .rotateY((float) OrbitVisualRules.surfaceRotationRadians(body, calendarTicks));
    }

    private static void knownLengthNormalizationMatchesLegacyBits() {
        List<CelestialVector> fixed = List.of(
                CelestialVector.ZERO,
                new CelestialVector(-0.0D, 0.0D, -0.0D),
                new CelestialVector(1.0E-14D, -1.0E-14D, 1.0E-14D),
                new CelestialVector(1.0D, 2.0D, 3.0D),
                new CelestialVector(Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_VALUE),
                new CelestialVector(Double.MAX_VALUE, 1.0D, -1.0D),
                new CelestialVector(Double.NaN, 1.0D, 2.0D),
                new CelestialVector(Double.POSITIVE_INFINITY, 1.0D, 2.0D));
        for (CelestialVector vector : fixed) {
            assertKnownLengthNormalization(vector, "fixed known-length normalization");
        }
        java.util.Random random = new java.util.Random(0x4E4F524D4C454E47L);
        for (int sample = 0; sample < 2048; sample++) {
            CelestialVector vector = new CelestialVector(
                    (random.nextDouble() * 2.0D - 1.0D) * 1.0E12D,
                    (random.nextDouble() * 2.0D - 1.0D) * 1.0E12D,
                    (random.nextDouble() * 2.0D - 1.0D) * 1.0E12D);
            assertKnownLengthNormalization(vector,
                    "random known-length normalization " + sample);
        }
    }

    private static void assertKnownLengthNormalization(CelestialVector vector, String name) {
        double length = vector.length();
        CelestialVector expected = vector.normalized();
        CelestialVector actual = OrbitVisualRules.normalizedAtLength(vector, length);
        assertRawDouble(expected.x(), actual.x(), name + " x");
        assertRawDouble(expected.y(), actual.y(), name + " y");
        assertRawDouble(expected.z(), actual.z(), name + " z");
        if ((expected == CelestialVector.ZERO) != (actual == CelestialVector.ZERO)) {
            throw new AssertionError(name + " changed shared ZERO identity");
        }
    }

    private static void optimizedOrbitPolygonAreaMatchesLegacyBits() {
        List<List<OrbitVisualRules.ProjectedPoint>> fixed = List.of(
                List.of(),
                List.of(new OrbitVisualRules.ProjectedPoint(-0.0D, 0.0D)),
                List.of(new OrbitVisualRules.ProjectedPoint(0.0D, 0.0D),
                        new OrbitVisualRules.ProjectedPoint(1.0D, 0.0D),
                        new OrbitVisualRules.ProjectedPoint(0.0D, 1.0D)),
                List.of(new OrbitVisualRules.ProjectedPoint(Double.NaN, 1.0D),
                        new OrbitVisualRules.ProjectedPoint(2.0D, Double.POSITIVE_INFINITY)),
                List.of(new OrbitVisualRules.ProjectedPoint(Double.MAX_VALUE, 1.0D),
                        new OrbitVisualRules.ProjectedPoint(-Double.MAX_VALUE, 2.0D),
                        new OrbitVisualRules.ProjectedPoint(0.0D, -Double.MAX_VALUE)));
        for (List<OrbitVisualRules.ProjectedPoint> polygon : fixed) {
            assertRawDouble(legacyOrbitPolygonArea(polygon),
                    OrbitVisualRules.polygonArea(polygon), "fixed orbit polygon area");
        }
        java.util.Random random = new java.util.Random(0x504F4C59474F4E4CL);
        for (int sample = 0; sample < 1024; sample++) {
            int count = random.nextInt(17);
            List<OrbitVisualRules.ProjectedPoint> polygon = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                polygon.add(new OrbitVisualRules.ProjectedPoint(
                        (random.nextDouble() * 2.0D - 1.0D) * 1.0E8D,
                        (random.nextDouble() * 2.0D - 1.0D) * 1.0E8D));
            }
            assertRawDouble(legacyOrbitPolygonArea(polygon),
                    OrbitVisualRules.polygonArea(polygon),
                    "random orbit polygon area " + sample);
        }
    }

    private static double legacyOrbitPolygonArea(
            List<OrbitVisualRules.ProjectedPoint> polygon) {
        double twiceArea = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            OrbitVisualRules.ProjectedPoint first = polygon.get(index);
            OrbitVisualRules.ProjectedPoint second = polygon.get((index + 1) % polygon.size());
            twiceArea += first.x() * second.y() - first.y() * second.x();
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    private static void optimizedProjectedCubeCoverageMatchesLegacyBits() {
        CelestialVector north = new CelestialVector(0.0D, 1.0D, 0.0D);
        List<ProjectedCubeCase> fixed = List.of(
                new ProjectedCubeCase(new CelestialVector(0.0D, 0.0D, 1.0D), 0.1D,
                        new CelestialVector(0.0D, 0.0D, 2.0D), 0.25D,
                        new org.joml.Quaternionf(), north),
                new ProjectedCubeCase(new CelestialVector(1.0D, 2.0D, 3.0D), 0.2D,
                        new CelestialVector(3.0D, 6.0D, 9.0D), 0.5D,
                        new org.joml.Quaternionf().rotationXYZ(0.25F, -0.5F, 1.0F), north),
                new ProjectedCubeCase(CelestialVector.ZERO, 0.1D,
                        new CelestialVector(0.0D, 0.0D, 1.0D), 0.25D,
                        new org.joml.Quaternionf(), north),
                new ProjectedCubeCase(new CelestialVector(Double.NaN, 0.0D, 1.0D), 0.1D,
                        new CelestialVector(0.0D, 0.0D, 1.0D), 0.25D,
                        new org.joml.Quaternionf(), north));
        for (ProjectedCubeCase sample : fixed) {
            assertProjectedCubeCoverageRaw(sample, "fixed projected cube coverage");
        }
        java.util.Random random = new java.util.Random(0x43554245434F5645L);
        for (int sample = 0; sample < 1024; sample++) {
            CelestialVector sun = new CelestialVector(
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D);
            if (sun.length() <= 0.01D) {
                sun = new CelestialVector(0.0D, 0.0D, 1.0D);
            }
            CelestialVector unitSun = sun.normalized();
            double distance = 2.0D + random.nextDouble() * 200.0D;
            CelestialVector center = unitSun.scale(distance).add(new CelestialVector(
                    (random.nextDouble() * 2.0D - 1.0D) * distance * 0.2D,
                    (random.nextDouble() * 2.0D - 1.0D) * distance * 0.2D,
                    (random.nextDouble() * 2.0D - 1.0D) * distance * 0.2D));
            org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationXYZ(
                    random.nextFloat() * 6.0F - 3.0F,
                    random.nextFloat() * 6.0F - 3.0F,
                    random.nextFloat() * 6.0F - 3.0F);
            ProjectedCubeCase value = new ProjectedCubeCase(sun,
                    0.001D + random.nextDouble(), center,
                    0.001D + random.nextDouble(), rotation,
                    new CelestialVector(random.nextDouble() * 2.0D - 1.0D,
                            random.nextDouble() * 2.0D - 1.0D,
                            random.nextDouble() * 2.0D - 1.0D));
            assertProjectedCubeCoverageRaw(value,
                    "random projected cube coverage " + sample);
        }
    }

    private static void assertProjectedCubeCoverageRaw(ProjectedCubeCase sample, String name) {
        double expected = legacyProjectedCubeCoverage(sample.sunDirection(), sample.sunHalfTangent(),
                sample.cubeCenter(), sample.cubeHalfSize(), sample.rotation(),
                sample.celestialNorth());
        double actual = OrbitVisualRules.projectedCubeCoverage(sample.sunDirection(),
                sample.sunHalfTangent(), sample.cubeCenter(), sample.cubeHalfSize(),
                sample.rotation(), sample.celestialNorth());
        assertRawDouble(expected, actual, name);
    }

    private static double legacyProjectedCubeCoverage(
            CelestialVector sunDirection, double sunHalfTangent,
            CelestialVector cubeCenter, double cubeHalfSize,
            org.joml.Quaternionf cubeRotation, CelestialVector celestialNorth) {
        if (!legacyFinite(sunDirection) || !legacyFinite(cubeCenter)
                || !(sunHalfTangent > 0.0D) || !(cubeHalfSize > 0.0D)
                || !Double.isFinite(sunHalfTangent) || !Double.isFinite(cubeHalfSize)
                || cubeRotation == null) {
            return 0.0D;
        }
        CelestialVector sun = sunDirection.normalized();
        first.wildfires.celestial.CelestialDiscGeometry.Basis basis =
                first.wildfires.celestial.CelestialDiscGeometry.stableBasis(sun, celestialNorth);
        List<LegacyProjectedPoint> points = new ArrayList<>(8);
        for (int xIndex = 0; xIndex < 2; xIndex++) {
            int x = xIndex == 0 ? -1 : 1;
            for (int yIndex = 0; yIndex < 2; yIndex++) {
                int y = yIndex == 0 ? -1 : 1;
                for (int zIndex = 0; zIndex < 2; zIndex++) {
                    int z = zIndex == 0 ? -1 : 1;
                    org.joml.Vector3f local = new org.joml.Vector3f(x, y, z)
                            .mul((float) cubeHalfSize);
                    cubeRotation.transform(local);
                    CelestialVector corner = cubeCenter.add(
                            new CelestialVector(local.x, local.y, local.z));
                    double forward = corner.dot(sun);
                    if (!(forward > 1.0E-12D) || !Double.isFinite(forward)) {
                        return 0.0D;
                    }
                    points.add(new LegacyProjectedPoint(corner.dot(basis.right()) / forward,
                            corner.dot(basis.up()) / forward));
                }
            }
        }
        List<LegacyProjectedPoint> polygon = legacyConvexHull(points);
        polygon = legacyClip(polygon, true, -sunHalfTangent, true);
        polygon = legacyClip(polygon, true, sunHalfTangent, false);
        polygon = legacyClip(polygon, false, -sunHalfTangent, true);
        polygon = legacyClip(polygon, false, sunHalfTangent, false);
        double coverage = legacyProjectedPolygonArea(polygon)
                / (4.0D * sunHalfTangent * sunHalfTangent);
        return Math.max(0.0D, Math.min(1.0D, coverage));
    }

    private static List<LegacyProjectedPoint> legacyConvexHull(
            List<LegacyProjectedPoint> points) {
        List<LegacyProjectedPoint> sorted = new ArrayList<>(points);
        sorted.sort(java.util.Comparator.comparingDouble(LegacyProjectedPoint::x)
                .thenComparingDouble(LegacyProjectedPoint::y));
        List<LegacyProjectedPoint> hull = new ArrayList<>(16);
        for (LegacyProjectedPoint point : sorted) {
            while (hull.size() >= 2 && legacyProjectedCross(hull.get(hull.size() - 2),
                    hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        int lower = hull.size();
        for (int index = sorted.size() - 2; index >= 0; index--) {
            LegacyProjectedPoint point = sorted.get(index);
            while (hull.size() > lower && legacyProjectedCross(hull.get(hull.size() - 2),
                    hull.get(hull.size() - 1), point) <= 0.0D) {
                hull.remove(hull.size() - 1);
            }
            hull.add(point);
        }
        if (hull.size() > 1) {
            hull.remove(hull.size() - 1);
        }
        return hull;
    }

    private static List<LegacyProjectedPoint> legacyClip(
            List<LegacyProjectedPoint> input, boolean xAxis,
            double boundary, boolean keepGreater) {
        if (input.isEmpty()) {
            return input;
        }
        List<LegacyProjectedPoint> output = new ArrayList<>(input.size() + 4);
        LegacyProjectedPoint previous = input.get(input.size() - 1);
        boolean previousInside = legacyProjectedInside(previous, xAxis, boundary, keepGreater);
        for (LegacyProjectedPoint current : input) {
            boolean currentInside = legacyProjectedInside(current, xAxis, boundary, keepGreater);
            if (currentInside != previousInside) {
                double from = xAxis ? previous.x() : previous.y();
                double to = xAxis ? current.x() : current.y();
                double fraction = Math.max(0.0D,
                        Math.min(1.0D, (boundary - from) / (to - from)));
                output.add(new LegacyProjectedPoint(
                        previous.x() + (current.x() - previous.x()) * fraction,
                        previous.y() + (current.y() - previous.y()) * fraction));
            }
            if (currentInside) {
                output.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean legacyProjectedInside(LegacyProjectedPoint point, boolean xAxis,
                                                  double boundary, boolean keepGreater) {
        double value = xAxis ? point.x() : point.y();
        return keepGreater ? value >= boundary : value <= boundary;
    }

    private static double legacyProjectedCross(LegacyProjectedPoint first,
                                               LegacyProjectedPoint second,
                                               LegacyProjectedPoint third) {
        return (second.x() - first.x()) * (third.y() - first.y())
                - (second.y() - first.y()) * (third.x() - first.x());
    }

    private static double legacyProjectedPolygonArea(List<LegacyProjectedPoint> polygon) {
        double twiceArea = 0.0D;
        for (int index = 0; index < polygon.size(); index++) {
            LegacyProjectedPoint first = polygon.get(index);
            LegacyProjectedPoint second = polygon.get((index + 1) % polygon.size());
            twiceArea += first.x() * second.y() - first.y() * second.x();
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    private static boolean legacyFinite(CelestialVector vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private record ProjectedCubeCase(CelestialVector sunDirection, double sunHalfTangent,
                                     CelestialVector cubeCenter, double cubeHalfSize,
                                     org.joml.Quaternionf rotation,
                                     CelestialVector celestialNorth) {
    }

    private record LegacyProjectedPoint(double x, double y) {
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
        CelestialState state = state();
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context(EARTH, Optional.empty()), state, 0.0D);
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
        assertVector(body(frame, EARTH).incomingLightDirection(),
                OrbitVisualRules.incomingLightDirection(EARTH, state),
                "surface ascent derives Earth light from the selected body ephemeris");
        assertVector(body(frame, MARS).incomingLightDirection(),
                OrbitVisualRules.incomingLightDirection(MARS, state),
                "surface ascent derives non-Earth light from the selected body ephemeris");
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

    private static void developmentClientSuppressesCitadelDevFollower() throws Exception {
        String clientEvents = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/SpaceContentClientEvents.java"));
        assertTrue(clientEvents.contains("if (!FMLEnvironment.production)")
                        && clientEvents.contains("ClientProxy.hideFollower = true"),
                "development client suppresses Citadel's false-positive Dev Patreon follower only outside production");
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
        String orbitRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        assertTrue(orbitRenderer.contains("boolean depthWritten = false")
                        && orbitRenderer.contains("if (depthWritten)")
                        && orbitRenderer.contains("depthWritten = drawCubeBody")
                        && !orbitRenderer.substring(orbitRenderer.indexOf("private static void drawSunLayer"),
                        orbitRenderer.indexOf("private static boolean drawBodyLayer"))
                        .contains("clearLayerDepth"),
                "point-only and depth-disabled Sun passes skip clears without changing cube isolation");
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
        ObservationJourney contractionCruise = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.JUMP_CRUISING, 60L, 160L);
        assertClose(0.0D, RelativisticVisualRules.state(accelerating, 0.0D).beta(), "jump starts at rest");
        assertTrue(RelativisticVisualRules.state(accelerating, 60.0D).beta() > 0.98D,
                "acceleration reaches near-light beta in three seconds");
        assertClose(0.0D, RelativisticVisualRules.state(decelerating, 280.0D).beta(),
                "deceleration ends at rest");
        double accelerationTrailMiddle = RelativisticVisualRules.state(
                accelerating, 30.0D).starTrailStrength();
        double accelerationTrailNearEnd = RelativisticVisualRules.state(
                accelerating, 58.0D).starTrailStrength();
        assertClose(0.0D, RelativisticVisualRules.state(
                accelerating, 0.0D).starTrailStrength(), "acceleration trail starts from zero");
        assertTrue(accelerationTrailMiddle > 0.90D
                        && accelerationTrailNearEnd > 0.0D
                        && accelerationTrailNearEnd < accelerationTrailMiddle * 0.35D,
                "three-second acceleration has an obvious speed-driven trail that retracts near its end");
        double accelerationTrailEnd = RelativisticVisualRules.state(
                accelerating, 60.0D).starTrailStrength();
        double cruiseTrailStart = RelativisticVisualRules.state(
                contractionCruise, 60.0D).starTrailStrength();
        double cruiseTrailMiddle = RelativisticVisualRules.state(
                contractionCruise, 70.0D).starTrailStrength();
        assertTrue(accelerationTrailEnd > 0.0D
                        && accelerationTrailEnd < accelerationTrailMiddle * 0.15D,
                "acceleration keeps only a short residual trail at its three-second boundary");
        assertClose(accelerationTrailEnd, cruiseTrailStart,
                "acceleration trail hands the first cruise second one continuous residual length");
        assertTrue(cruiseTrailMiddle > 0.0D && cruiseTrailMiddle < cruiseTrailStart,
                "the added cruise second keeps smoothly retracting the acceleration trail");
        assertClose(0.0D, RelativisticVisualRules.state(
                contractionCruise, 80.0D).starTrailStrength(),
                "acceleration trail finishes retracting exactly one second into cruise");
        double cruiseTrailEndSlope = (RelativisticVisualRules.state(
                contractionCruise, 80.0D).starTrailStrength() - RelativisticVisualRules.state(
                contractionCruise, 79.999D).starTrailStrength()) / 0.001D;
        assertNear(0.0D, cruiseTrailEndSlope, 1.0E-4D,
                "extended acceleration trail reaches zero with a smooth end slope");
        double previousAccelerationTrail = Math.abs(RelativisticVisualRules.state(
                accelerating, 48.0D).starTrailStrength());
        for (double time = 48.5D; time < 60.0D; time += 0.5D) {
            double currentTrail = Math.abs(RelativisticVisualRules.state(
                    accelerating, time).starTrailStrength());
            assertTrue(currentTrail > 0.0D && currentTrail < previousAccelerationTrail,
                    "acceleration trail continuously contracts toward its retained star point");
            previousAccelerationTrail = currentTrail;
        }
        double previousCruiseTrail = cruiseTrailStart;
        for (double time = 60.5D; time < 80.0D; time += 0.5D) {
            double currentTrail = RelativisticVisualRules.state(
                    contractionCruise, time).starTrailStrength();
            assertTrue(currentTrail > 0.0D && currentTrail < previousCruiseTrail,
                    "first cruise second monotonically retracts the acceleration trail");
            previousCruiseTrail = currentTrail;
        }
        double decelerationTrailMiddle = RelativisticVisualRules.state(
                decelerating, 250.0D).starTrailStrength();
        double decelerationTrailNearEnd = RelativisticVisualRules.state(
                decelerating, 278.0D).starTrailStrength();
        assertClose(0.0D, RelativisticVisualRules.state(
                decelerating, 220.0D).starTrailStrength(), "release trail fades in from zero");
        assertTrue(decelerationTrailMiddle < -0.90D
                        && decelerationTrailNearEnd < 0.0D
                        && Math.abs(decelerationTrailNearEnd) < Math.abs(decelerationTrailMiddle) * 0.10D,
                "three-second release reverses the obvious trail and retracts it near its end");
        assertClose(0.0D, RelativisticVisualRules.state(
                decelerating, 280.0D).starTrailStrength(),
                "release trail reaches zero smoothly at the arrival boundary");
        double decelerationTrailEndSlope = (RelativisticVisualRules.state(
                decelerating, 280.0D).starTrailStrength() - RelativisticVisualRules.state(
                decelerating, 279.999D).starTrailStrength()) / 0.001D;
        assertNear(0.0D, decelerationTrailEndSlope, 1.0E-4D,
                "release trail retracts with zero end slope instead of disappearing abruptly");
        double previousDecelerationTrail = Math.abs(RelativisticVisualRules.state(
                decelerating, 268.0D).starTrailStrength());
        for (double time = 268.5D; time < 280.0D; time += 0.5D) {
            double currentTrail = Math.abs(RelativisticVisualRules.state(
                    decelerating, time).starTrailStrength());
            assertTrue(currentTrail > 0.0D && currentTrail < previousDecelerationTrail,
                    "release trail continuously contracts toward its retained star point");
            previousDecelerationTrail = currentTrail;
        }
        double contractionStart = RelativisticVisualRules.state(contractionCruise, 60.0D).aberrationBeta();
        double contractionTwoSeconds = RelativisticVisualRules.state(contractionCruise, 100.0D).aberrationBeta();
        double contractionFourSeconds = RelativisticVisualRules.state(contractionCruise, 140.0D).aberrationBeta();
        double contractionSixSeconds = RelativisticVisualRules.state(contractionCruise, 180.0D).aberrationBeta();
        double contractionEnd = RelativisticVisualRules.state(contractionCruise, 220.0D).aberrationBeta();
        assertClose(RelativisticVisualRules.ACCELERATION_END_ABERRATION_BETA, contractionStart,
                "cruise starts from acceleration contraction without a jump");
        assertTrue(contractionStart < contractionTwoSeconds
                        && contractionTwoSeconds < contractionFourSeconds
                        && contractionFourSeconds < contractionSixSeconds
                        && contractionSixSeconds < contractionEnd,
                "all eight cruise seconds continue contracting the star field");
        assertClose(RelativisticVisualRules.CRUISE_END_ABERRATION_BETA, contractionEnd,
                "cruise reaches its tighter but finite dramatic endpoint");
        assertClose(0.0D, RelativisticVisualRules.state(
                contractionCruise, 180.0D).starTrailStrength(),
                "the remaining seven cruise seconds stay completely free of star trails");
        assertTrue(contractionEnd > 0.90D
                        && contractionEnd < RelativisticVisualRules.VISUAL_ABERRATION_MAX_BETA,
                "new cruise endpoint is tighter than the old curve with safety headroom remaining");
        double firstTwoSecondGain = contractionTwoSeconds - contractionStart;
        double secondTwoSecondGain = contractionFourSeconds - contractionTwoSeconds;
        double thirdTwoSecondGain = contractionSixSeconds - contractionFourSeconds;
        double fourthTwoSecondGain = contractionEnd - contractionSixSeconds;
        assertTrue(firstTwoSecondGain > secondTwoSecondGain
                        && secondTwoSecondGain > thirdTwoSecondGain * 0.99D,
                "first four cruise seconds smoothly reduce contraction rate");
        assertClose(thirdTwoSecondGain, fourthTwoSecondGain,
                "last four cruise seconds retain a constant slow contraction rate");
        double sampleStep = 0.0001D;
        double accelerationJoinRate = (RelativisticVisualRules.state(accelerating, 60.0D).aberrationBeta()
                - RelativisticVisualRules.state(accelerating, 60.0D - sampleStep).aberrationBeta()) / sampleStep;
        double cruiseStartRate = (RelativisticVisualRules.state(contractionCruise, 60.0D + sampleStep)
                .aberrationBeta() - contractionStart) / sampleStep;
        assertNear(accelerationJoinRate, cruiseStartRate, 1.0E-6D,
                "acceleration and cruise contraction rates join continuously");
        double transitionLeftRate = (contractionFourSeconds
                - RelativisticVisualRules.state(contractionCruise, 140.0D - sampleStep).aberrationBeta())
                / sampleStep;
        double transitionRightRate = (RelativisticVisualRules.state(
                contractionCruise, 140.0D + sampleStep).aberrationBeta() - contractionFourSeconds) / sampleStep;
        assertNear(transitionLeftRate, transitionRightRate, 1.0E-7D,
                "four-second cruise rate transition has no visual kink");
        assertClose(contractionEnd,
                RelativisticVisualRules.state(decelerating, 220.0D).aberrationBeta(),
                "deceleration begins from the exact cruise contraction state");
        double cruiseEndRate = (contractionEnd - RelativisticVisualRules.state(
                contractionCruise, 220.0D - sampleStep).aberrationBeta()) / sampleStep;
        double decelerationStartRate = (RelativisticVisualRules.state(
                decelerating, 220.0D + sampleStep).aberrationBeta() - contractionEnd) / sampleStep;
        assertNear(cruiseEndRate, decelerationStartRate, 1.0E-6D,
                "cruise and deceleration contraction rates join continuously");
        assertTrue(RelativisticVisualRules.state(decelerating, 240.0D).aberrationBeta()
                        < contractionEnd,
                "three-second deceleration rapidly releases contraction while still moving");
        assertClose(0.0D, RelativisticVisualRules.state(decelerating, 280.0D).aberrationBeta(),
                "three-second deceleration fully releases sky contraction");
        double tightestContraction = 0.0D;
        for (int sample = 0; sample <= 600; sample++) {
            tightestContraction = Math.max(tightestContraction,
                    RelativisticVisualRules.state(accelerating, sample * 0.1D).visualAberrationBeta());
        }
        for (int sample = 0; sample <= 1600; sample++) {
            tightestContraction = Math.max(tightestContraction,
                    RelativisticVisualRules.state(contractionCruise, 60.0D + sample * 0.1D)
                            .visualAberrationBeta());
        }
        for (int sample = 0; sample <= 600; sample++) {
            tightestContraction = Math.max(tightestContraction,
                    RelativisticVisualRules.state(decelerating, 220.0D + sample * 0.1D)
                            .visualAberrationBeta());
        }
        assertTrue(Double.isFinite(tightestContraction)
                        && tightestContraction < RelativisticVisualRules.VISUAL_ABERRATION_MAX_BETA,
                "complete jump contraction curve remains finite and below its point-collapse guard");
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
        OrbitVisualRules.Frame predictedArrivalMiddle = OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState, 285.0D);
        OrbitVisualRules.Frame predictedArrivalEnd = OrbitVisualRules.frame(
                context(EARTH, Optional.of(decelerating)), visualState, 290.0D);
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
        assertClose(body(arrivalMiddle, MARS).cubeAlpha(),
                body(predictedArrivalMiddle, MARS).cubeAlpha(),
                "a late ARRIVING packet cannot hold the target point through the reveal midpoint");
        assertClose(body(arrivalRevealEnd, MARS).renderHalfSize(),
                body(predictedArrivalEnd, MARS).renderHalfSize(),
                "stale deceleration extrapolates the same continuous ten-tick target enlargement");
        assertVector(arrivalMiddle.observerPosition(), predictedArrivalMiddle.observerPosition(),
                "late phase delivery keeps ship and background on the authoritative reveal path");
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
        java.util.Random lineRandom = new java.util.Random(0x57494C4446495245L);
        for (int sample = 0; sample < 2_000; sample++) {
            CelestialVector observer = new CelestialVector(lineRandom.nextDouble(-30.0D, 30.0D),
                    lineRandom.nextDouble(-30.0D, 30.0D), lineRandom.nextDouble(-30.0D, 30.0D));
            CelestialVector targetPosition = new CelestialVector(lineRandom.nextDouble(-30.0D, 30.0D),
                    lineRandom.nextDouble(-30.0D, 30.0D), lineRandom.nextDouble(-30.0D, 30.0D));
            java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> obstacles =
                    new java.util.LinkedHashMap<>();
            obstacles.put(blockedTarget, new OrbitVisualRules.BodyEphemeris(
                    targetPosition, lineRandom.nextDouble(0.01D, 2.0D), OrbitVisualRules.SUN));
            for (int index = 0; index < 20; index++) {
                ResourceLocation obstacleId = ResourceLocation.fromNamespaceAndPath(
                        "wildfires", "line_obstacle_" + index);
                obstacles.put(obstacleId, new OrbitVisualRules.BodyEphemeris(new CelestialVector(
                        lineRandom.nextDouble(-30.0D, 30.0D),
                        lineRandom.nextDouble(-30.0D, 30.0D),
                        lineRandom.nextDouble(-30.0D, 30.0D)),
                        lineRandom.nextDouble(0.01D, 2.0D), OrbitVisualRules.SUN));
            }
            assertTrue(OrbitVisualRules.hasClearTargetLine(observer, blockedTarget,
                            targetPosition, obstacles)
                            == referenceHasClearTargetLine(observer, blockedTarget,
                            targetPosition, obstacles),
                    "allocation-free jump obstruction math is exactly decision-equivalent");
        }
        try {
            String sky = Files.readString(Path.of(
                    "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.vsh"));
            String renderer = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/NtmOrbitSkyRenderer.java"));
            String shaderBindings = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/RelativisticSkyShader.java"));
            String fragment = Files.readString(Path.of(
                    "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.fsh"));
            String shaderJson = Files.readString(Path.of(
                    "src/main/resources/assets/wildfires/shaders/core/relativistic_sky.json"));
            assertTrue(sky.contains("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0)")
                            && !sky.contains("shifted") && !sky.contains("Beta"),
                    "skybox geometry remains rigid instead of folding toward jump velocity");
            assertTrue(fragment.contains("ntmAtlasUv") && fragment.contains("inverseAberrationSource")
                            && fragment.contains("AberrationBeta") && fragment.contains("doppler")
                            && fragment.contains("gamma * (1.0 - beta * observedCosine)"),
                    "fragment shader slides stars across all atlas faces and preserves Doppler colour");
            assertTrue(fragment.contains("clamp(AberrationBeta, 0.0, 0.94)")
                            && fragment.contains("visualBeta / 0.94")
                            && !fragment.contains("clamp(AberrationBeta, 0.0, 0.90)")
                            && !fragment.contains("visualBeta / 0.90"),
                    "GPU aberration limit matches the Java 0.94 guard throughout all eight cruise seconds");
            assertTrue(fragment.contains("vec2(1.0, 1.0)") && fragment.contains("vec2(2.0, 0.0)")
                            && fragment.contains("vec2(1.0, 0.0)") && fragment.contains("vec2(0.0, 0.0)")
                            && fragment.contains("vec2(2.0, 1.0)") && fragment.contains("vec2(0.0, 1.0)"),
                    "shader inverse mapping covers all six exact NTM atlas cells");
            assertTrue(renderer.contains("RelativisticSkyShader")
                            && renderer.contains("aberrationBeta().set")
                            && renderer.contains("starTrailStrength().set")
                            && renderer.contains("starVisibility(starVisibility, relativity)")
                            && !renderer.contains("safeGetUniform")
                            && shaderBindings.contains("safeGetUniform(\"AberrationBeta\")")
                            && shaderBindings.contains("safeGetUniform(\"StarTrailStrength\")")
                            && shaderJson.contains("\"name\":\"StarTrailStrength\""),
                    "NTM star cubemap receives bounded sliding and boosted jump exposure");
            assertTrue(fragment.contains("pow(clamp(doppler, 0.0, 1.0), 1.65)")
                            && fragment.contains("forwardCone")
                            && fragment.contains("sampleNtmAtlasSeamless")
                            && fragment.contains("observedCosine")
                            && !fragment.contains("1.0 + 0.82 * exposure"),
                    "shader gives a seamless radial forward brightness independent of cubemap faces");
            assertTrue(fragment.contains("if (trailMagnitude > 0.0)")
                            && !fragment.contains("if (trailMagnitude > 0.01)")
                            && !fragment.contains("smoothstep(0.04")
                            && !fragment.contains("nearTrail") && !fragment.contains("farTrail")
                            && fragment.contains("const int STAR_TRAIL_SAMPLES = 12")
                            && fragment.contains("trailIndex <= STAR_TRAIL_SAMPLES")
                            && fragment.contains("float(trailIndex) / float(STAR_TRAIL_SAMPLES)")
                            && fragment.contains("normalize(mix(source, tailSource, trailFraction))")
                            && fragment.contains("1.0 - smoothstep(0.0, 1.0, trailFraction)")
                            && fragment.contains("trailSample - sampled")
                            && fragment.contains("trailResidual = max(trailResidual, residual)")
                            && fragment.contains("0.165 * StarTrailStrength")
                            && !fragment.contains("0.055 * StarTrailStrength")
                            && fragment.contains("sampled.rgb += trailResidual.rgb * 0.68"),
                    "fixed seamless integration keeps one continuous long band with a fading remote tail");
            String orbitRenderer = Files.readString(Path.of(
                    "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
            assertTrue(orbitRenderer.contains("prewarmJumpTarget(level, context)")
                            && orbitRenderer.contains("OrbitBodyTextureManager.surface(journey.toBody()")
                            && orbitRenderer.contains("journey.phase().isJumpPhase()")
                            && orbitRenderer.contains("OrbitBodyTextureManager.clouds(journey.toBody()"),
                    "jump target surface and clouds are cached before the half-second reveal");
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
        java.util.Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> immutableEphemeris =
                java.util.Map.copyOf(ephemeris);
        List<OrbitVisualRules.SatelliteShadow> expected = legacySatelliteShadows(
                parentId, parent, immutableEphemeris, sun);
        List<OrbitVisualRules.SatelliteShadow> selected = OrbitVisualRules.satelliteShadows(
                parentId, parent, immutableEphemeris, sun);
        assertSatelliteShadowsRaw(expected, selected, "optimized satellite-shadow candidates");
        CelestialVector parentFromSun = parent.position().subtract(sun.position());
        double parentSunDistance = parentFromSun.length();
        CelestialVector incomingLight = parentFromSun.normalized();
        double sunHalfTangent = sun.radius() / Math.max(1.0E-12D, parentSunDistance);
        List<OrbitVisualRules.SatelliteShadow> prepared = OrbitVisualRules.satelliteShadows(
                parentId, parent, immutableEphemeris, incomingLight, sunHalfTangent);
        assertSatelliteShadowsRaw(expected, prepared,
                "frame-prepared satellite-shadow candidates");
        assertEquals(OrbitVisualRules.MAX_SATELLITE_SHADOWS, selected.size(),
                "fixed maximum satellite shadow budget");
        assertEquals(childA, selected.get(0).satellite(), "largest angular caster has priority");
        assertTrue(selected.stream().noneMatch(shadow -> shadow.satellite().equals(offAxis)
                        || shadow.satellite().equals(behind) || shadow.satellite().equals(childE)),
                "CPU culling rejects off-cone, behind-parent and fifth-priority casters");
        assertClose(0.40D, selected.get(0).halfSize(), "caster normalized to parent half-size");

        String renderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/OrbitSkyRenderer.java"));
        String ascentRenderer = Files.readString(Path.of(
                "src/main/java/first/wildfires/client/space/render/NtmAscentPlanetRenderer.java"));
        String bindings = Files.readString(Path.of(
                "src/main/java/first/wildfires/thirdparty/genesisadapt/GenesisPlanetShader.java"));
        assertTrue(renderer.contains("shadowCount()") && renderer.contains("axisX(index)")
                        && !renderer.contains("safeGetUniform")
                        && !renderer.contains("MAX_SATELLITE_SHADOWS")
                        && bindings.contains("safeGetUniform(\"ShadowCount\")")
                        && bindings.contains("uniforms(shader, \"ShadowAxisX\")"),
                "renderer uploads cached OBB axes and uses the already bounded caster list");
        assertTrue(ascentRenderer.contains("satelliteShadowFrame(bodyId, state)")
                        && ascentRenderer.contains("OrbitSkyRenderer.configureSatelliteShadows")
                        && !ascentRenderer.contains("clearSatelliteShadows")
                        && !ascentRenderer.contains("shadowCount().set(0)"),
                "bound-surface ascent reuses the same geometric satellite umbra/penumbra frame");
    }

    private static List<OrbitVisualRules.SatelliteShadow> legacySatelliteShadows(
            ResourceLocation parentId, OrbitVisualRules.BodyEphemeris parent,
            Map<ResourceLocation, OrbitVisualRules.BodyEphemeris> ephemeris,
            OrbitVisualRules.BodyEphemeris sun) {
        CelestialVector incomingLight = parent.position().subtract(sun.position()).normalized();
        double sunHalfTangent = sun.radius() / Math.max(1.0E-12D,
                parent.position().subtract(sun.position()).length());
        List<java.util.Map.Entry<OrbitVisualRules.SatelliteShadow, Double>> candidates =
                new ArrayList<>();
        for (Map.Entry<ResourceLocation, OrbitVisualRules.BodyEphemeris> entry
                : ephemeris.entrySet()) {
            OrbitVisualRules.BodyEphemeris satellite = entry.getValue();
            if (!parentId.equals(satellite.parent())) {
                continue;
            }
            CelestialVector relative = satellite.position().subtract(parent.position());
            double signedLightDistance = relative.dot(incomingLight);
            double satelliteToParent = -signedLightDistance;
            if (!(satelliteToParent > satellite.radius())
                    || !Double.isFinite(satelliteToParent)) {
                continue;
            }
            CelestialVector lateralVector = relative.subtract(
                    incomingLight.scale(signedLightDistance));
            double lateral = lateralVector.length();
            double parentCorner = Math.sqrt(3.0D) * parent.radius();
            double satelliteCorner = Math.sqrt(3.0D) * satellite.radius();
            double squareStarSpread = Math.sqrt(2.0D) * sunHalfTangent * satelliteToParent;
            if (lateral > parentCorner + satelliteCorner + squareStarSpread) {
                continue;
            }
            double priority = satellite.radius() / satelliteToParent;
            OrbitVisualRules.SatelliteShadow shadow = new OrbitVisualRules.SatelliteShadow(
                    entry.getKey(), OrbitVisualRules.ntmFrameVector(relative),
                    satellite.radius() / parent.radius());
            candidates.add(new java.util.AbstractMap.SimpleImmutableEntry<>(shadow, priority));
        }
        candidates.sort(java.util.Comparator
                .<java.util.Map.Entry<OrbitVisualRules.SatelliteShadow, Double>>comparingDouble(
                        java.util.Map.Entry::getValue).reversed()
                .thenComparing(candidate -> candidate.getKey().satellite().toString()));
        int selectedCount = Math.min(OrbitVisualRules.MAX_SATELLITE_SHADOWS,
                candidates.size());
        List<OrbitVisualRules.SatelliteShadow> selected = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selected.add(candidates.get(index).getKey());
        }
        return List.copyOf(selected);
    }

    private static void assertSatelliteShadowsRaw(
            List<OrbitVisualRules.SatelliteShadow> expected,
            List<OrbitVisualRules.SatelliteShadow> actual, String name) {
        assertEquals(expected.size(), actual.size(), name + " size");
        for (int index = 0; index < expected.size(); index++) {
            OrbitVisualRules.SatelliteShadow oldShadow = expected.get(index);
            OrbitVisualRules.SatelliteShadow newShadow = actual.get(index);
            assertEquals(oldShadow.satellite(), newShadow.satellite(), name + " id " + index);
            assertRawDouble(oldShadow.relativePosition().x(), newShadow.relativePosition().x(),
                    name + " x " + index);
            assertRawDouble(oldShadow.relativePosition().y(), newShadow.relativePosition().y(),
                    name + " y " + index);
            assertRawDouble(oldShadow.relativePosition().z(), newShadow.relativePosition().z(),
                    name + " z " + index);
            assertRawDouble(oldShadow.halfSize(), newShadow.halfSize(),
                    name + " half-size " + index);
        }
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

    private static CelestialState stateWithJovianMoonsAcrossIngressLine(
            CelestialVector ganymedePosition) {
        CelestialBodyState sun = body(SUN, null, new CelestialVector(100.0D, 0.0D, 0.0D), 1.0D);
        CelestialBodyState moon = body(MOON, EARTH, new CelestialVector(0.0D, 0.3844D, 0.0D),
                0.001737D);
        CelestialVector jupiterPosition = new CelestialVector(20.0D, 0.0D, 0.0D);
        CelestialBodyState jupiter = body(JUPITER, SUN, jupiterPosition, 0.071492D);
        CelestialBodyState io = body(IO, JUPITER,
                new CelestialVector(19.5783D, 0.0D, 0.0D), 0.0018216D);
        CelestialBodyState europa = body(EUROPA, JUPITER,
                new CelestialVector(20.669151D, 0.0D, 0.0D), 0.0015608D);
        CelestialBodyState ganymede = body(GANYMEDE, JUPITER, ganymedePosition, 0.0026341D);
        return new CelestialState(0.0D, 0.0D, 0.0D, 0L, sun, moon,
                new CelestialVector(0.0D, 1.0D, 0.0D),
                List.of(jupiter, io, europa, ganymede), 0, 0.0D, 0.0D,
                SolarEclipseState.NONE, 0.0D, LunarEclipseState.NONE, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D, new DaylightState(0.0D, true, 0.0D, 1.0D));
    }

    private static CelestialState stateWithMoonAt(double calendarTicks) {
        first.wildfires.celestial.CelestialRuntimeSettings settings =
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT;
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(
                0.0D, 1.0D, calendarTicks, 8,
                settings.resolvedSynodicDays(8), settings.resolvedAnomalisticDays(8),
                settings.nodalYears(), settings.lunarInclinationRadians()));
        CelestialVector sunPosition = testSunEclipticPosition(frame,
                settings.planetSettings().earthSemiMajorMillionKm());
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

    private static CelestialVector testSunEclipticPosition(CelestialMath.Result frame,
                                                            double distance) {
        return new CelestialVector(Math.cos(frame.solarLongitude()) * distance,
                Math.sin(frame.solarLongitude()) * distance, 0.0D);
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

    private static void jumpArrivalPathAndSkyOrientationStayContinuous() {
        CelestialState visualState = state();
        ObservationJourney arrival = new ObservationJourney(EARTH, MARS, StationTravelMode.JUMP,
                StationJourneyPhase.ARRIVING, 280L, 200L);
        ObservationContext arrivingContext = context(MARS, Optional.of(arrival));
        OrbitVisualRules.Frame previous = OrbitVisualRules.frame(arrivingContext, visualState, 280.0D);
        Quaternionf previousOrientation = OrbitVisualRules.frameViewOrientation(previous);
        CelestialVector previousViewedTarget = viewedDirection(previous, MARS);
        double maximumPositionStep = 0.0D;
        double maximumOrientationStep = 0.0D;
        double maximumTargetStep = 0.0D;
        double maximumPositionTime = 280.0D;
        double maximumOrientationTime = 280.0D;
        double maximumTargetTime = 280.0D;
        for (int sample = 1; sample <= 4_000; sample++) {
            double time = 280.0D + sample * 0.05D;
            OrbitVisualRules.Frame current = OrbitVisualRules.frame(arrivingContext, visualState, time);
            Quaternionf orientation = OrbitVisualRules.frameViewOrientation(current);
            CelestialVector viewedTarget = viewedDirection(current, MARS);
            double positionStep = current.observerPosition().subtract(previous.observerPosition()).length();
            double orientationStep = 2.0D * Math.acos(Math.min(1.0D,
                    Math.abs(previousOrientation.dot(orientation))));
            double targetStep = Math.acos(Math.max(-1.0D, Math.min(1.0D,
                    previousViewedTarget.dot(viewedTarget))));
            if (positionStep > maximumPositionStep) {
                maximumPositionStep = positionStep;
                maximumPositionTime = time;
            }
            if (orientationStep > maximumOrientationStep) {
                maximumOrientationStep = orientationStep;
                maximumOrientationTime = time;
            }
            if (targetStep > maximumTargetStep) {
                maximumTargetStep = targetStep;
                maximumTargetTime = time;
            }
            previous = current;
            previousOrientation = orientation;
            previousViewedTarget = viewedTarget;
        }
        OrbitVisualRules.Frame ordinary = OrbitVisualRules.frame(
                context(MARS, Optional.empty()), visualState, 480.0D);
        assertVector(ordinary.observerPosition(), previous.observerPosition(),
                "jump ARRIVING path ends at the live ordinary target orbit");
        assertTrue(Math.abs(OrbitVisualRules.frameViewOrientation(ordinary)
                        .dot(previousOrientation)) > 0.999999F,
                "jump ARRIVING sky ends at the ordinary inertial attitude");
        assertTrue(maximumPositionStep < 0.02D,
                "jump ARRIVING observer path has no teleport; maximum 0.05-tick step="
                        + maximumPositionStep + " at " + maximumPositionTime);
        assertTrue(maximumOrientationStep < Math.toRadians(1.0D),
                "jump ARRIVING sky has no large adjacent rotation; maximum 0.05-tick step="
                        + Math.toDegrees(maximumOrientationStep) + " degrees at "
                        + maximumOrientationTime);
        assertTrue(maximumTargetStep < Math.toRadians(1.0D),
                "jump ARRIVING target screen motion is continuous; maximum 0.05-tick step="
                        + Math.toDegrees(maximumTargetStep) + " degrees at " + maximumTargetTime);
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

    private static boolean referenceHasClearTargetLine(CelestialVector observer,
                                                        ResourceLocation targetId,
                                                        CelestialVector targetPosition,
                                                        java.util.Map<ResourceLocation,
                                                                OrbitVisualRules.BodyEphemeris> ephemeris) {
        CelestialVector segment = targetPosition.subtract(observer);
        double segmentLengthSquared = segment.dot(segment);
        if (!(segmentLengthSquared > 1.0E-18D)) return false;
        for (java.util.Map.Entry<ResourceLocation, OrbitVisualRules.BodyEphemeris> entry
                : ephemeris.entrySet()) {
            if (entry.getKey().equals(targetId)) continue;
            OrbitVisualRules.BodyEphemeris obstacle = entry.getValue();
            double along = Math.max(0.0D, Math.min(1.0D,
                    obstacle.position().subtract(observer).dot(segment) / segmentLengthSquared));
            CelestialVector closest = observer.add(segment.scale(along));
            double conservativeCubeRadius = obstacle.radius() * Math.sqrt(3.0D) * 1.05D;
            if (closest.subtract(obstacle.position()).length() <= conservativeCubeRadius) {
                return false;
            }
        }
        return true;
    }

    private static double segmentDistanceToPoint(CelestialVector start, CelestialVector end,
                                                 CelestialVector point) {
        CelestialVector segment = end.subtract(start);
        double lengthSquared = segment.dot(segment);
        double along = lengthSquared > 1.0E-24D
                ? Math.max(0.0D, Math.min(1.0D,
                point.subtract(start).dot(segment) / lengthSquared)) : 0.0D;
        return start.add(segment.scale(along)).subtract(point).length();
    }

    private static double movingSegmentDistance(CelestialVector stationStart,
                                                CelestialVector stationEnd,
                                                CelestialVector bodyStart,
                                                CelestialVector bodyEnd) {
        CelestialVector relativeStart = stationStart.subtract(bodyStart);
        CelestialVector relativeEnd = stationEnd.subtract(bodyEnd);
        return segmentDistanceToPoint(relativeStart, relativeEnd, CelestialVector.ZERO);
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

    private static String sourceSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start,
                "source section exists: " + startMarker + " to " + endMarker);
        return source.substring(start, end);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
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

    private static void assertRawDouble(double expected, double actual, String name) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError(name + ": expected raw 0x"
                    + Long.toHexString(Double.doubleToRawLongBits(expected)) + " but was 0x"
                    + Long.toHexString(Double.doubleToRawLongBits(actual)));
        }
    }

    private static void assertRawFloat(float expected, float actual, String name) {
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
            throw new AssertionError(name + ": expected raw 0x"
                    + Integer.toHexString(Float.floatToRawIntBits(expected)) + " but was 0x"
                    + Integer.toHexString(Float.floatToRawIntBits(actual)));
        }
    }

    private static void assertNear(double expected, double actual, double tolerance, String name) {
        if (!Double.isFinite(expected) || !Double.isFinite(actual)
                || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(name + ": expected " + expected + " +/- " + tolerance
                    + " but was " + actual);
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
