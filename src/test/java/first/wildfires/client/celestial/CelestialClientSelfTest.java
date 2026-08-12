package first.wildfires.client.celestial;

import com.google.gson.JsonParser;
import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialDiscGeometry;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.api.celestial.CelestialVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Deterministic checks for star-table merging and client-only visual eligibility rules. */
public final class CelestialClientSelfTest {

    private static final double EPSILON = 1.0E-9D;

    private CelestialClientSelfTest() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "planetarium".equals(args[0])) {
            supermoonBlueTintTracksLocalNightWeatherAndEclipse();
            publicPhaseEventApiRequiresCorrectLocalLight();
            planetariumPixelAssetsClockAndTimelineAreComplete();
            System.out.println("CelestialClientSelfTest: planetarium checks passed");
            return;
        }
        starTablesMergeInStableOrderAndIsolateErrors();
        auroraRulesCoverBothPolesAndLegacyMode();
        auroraAppearancePresetsAreCompleteAndDeterministic();
        auroraCachedGeometryRetainsTheOriginalWave();
        auroraAnimationUsesClientTicks();
        rainbowRulesCoverWeatherAndSolarBounds();
        rainbowDirectionIsFiniteAtSolarZenith();
        eclipseAppearanceIsContinuousAndBounded();
        solarOccultorTextureMatchesOrdinaryNewMoonAtZeroCoverage();
        eclipseBodiesUsePhysicalScaleAndMovingSquareShadow();
        lunarPenumbraStartsRedAtFirstContact();
        lunarShadowProjectionMatchesAuthoritativeCoverage();
        managedEclipseAndAuroraShadersPreserveTheSkyBehindThem();
        starAndMoonVisualScalesMatchTheirSources();
        moonAtlasAndSkyCoverRulesAvoidDaytimeArtifacts();
        supermoonBlueTintTracksLocalNightWeatherAndEclipse();
        publicPhaseEventApiRequiresCorrectLocalLight();
        moonHaloAttenuatesNearbyStarsByPhaseAndDistance();
        planetRenderingRestoresTfccaelumArcSecondScale();
        satelliteRenderingRetainsVisibleThreeDimensionalSeparation();
        actualJupiterAndSaturnSatellitesOrbitTheirParents();
        celestialDiscOrientationIsContinuousAcrossTheZenith();
        horizonTwilightAndSolarAppearanceAreContinuous();
        allOrbitingBodiesHaveFiniteVisualSizes();
        clientStateCacheIsExactAndInvalidatesEverySemanticKey();
        localApparentTimeUsesVanillaCelestialAngle();
        solarEclipseVisualTimeDimsEverySharedConsumer();
        polarVisualLightingFollowsTheLocalSun();
        planetariumProjectionAndEclipsePlotAreExact();
        planetariumPixelAssetsClockAndTimelineAreComplete();
        localVisualSceneMatrixIsFiniteAndComplete();
        System.out.println("CelestialClientSelfTest: all checks passed");
    }

    private static void starTablesMergeInStableOrderAndIsolateErrors() {
        Map<ResourceLocation, com.google.gson.JsonElement> resources = new LinkedHashMap<>();
        resources.put(id("d", "invalid"), json("""
                {"replace":false,"stars":[
                  {"name":"bad","ascension":0,"declination":0,"magnitude":1,"color":"nope"}
                ]}
                """));
        resources.put(id("e", "non_finite"), json("""
                {"replace":false,"stars":[
                  {"name":"infinite","ascension":0,"declination":0,"magnitude":1e309,"color":"ffffff"}
                ]}
                """));
        resources.put(id("c", "append"), json("""
                {"replace":false,"stars":[
                  {"name":"hash","ascension":3,"declination":0.3,"magnitude":3,"color":"#A1b2C3"}
                ]}
                """));
        resources.put(id("a", "base"), json("""
                {"replace":false,"stars":[
                  {"name":"discarded","ascension":1,"declination":0.1,"magnitude":1,"color":"112233"}
                ]}
                """));
        resources.put(id("b", "replace"), json("""
                {"replace":true,"stars":[
                  {"name":"kept","ascension":2,"declination":0.2,"magnitude":2,"color":"abcdef"}
                ]}
                """));

        StarTableLoader.Result first = StarTableLoader.load(resources);
        Map<ResourceLocation, com.google.gson.JsonElement> reversed = new LinkedHashMap<>();
        var entries = new ArrayList<>(resources.entrySet());
        Collections.reverse(entries);
        entries.forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
        StarTableLoader.Result second = StarTableLoader.load(reversed);

        if (!first.stars().equals(second.stars()) || first.stars().size() != 2) {
            throw new AssertionError("star-table merge order is not deterministic");
        }
        if (!first.stars().get(0).name().equals("kept") || !first.stars().get(1).name().equals("hash")) {
            throw new AssertionError("replace/append semantics are incorrect: " + first.stars());
        }
        if (first.stars().get(1).rgb() != 0xA1B2C3 || first.errors().size() != 2
                || !first.errors().get(0).resource().equals(id("d", "invalid"))
                || !first.errors().get(1).resource().equals(id("e", "non_finite"))) {
            throw new AssertionError("RGB compatibility or per-file error isolation is incorrect");
        }
    }

    private static void clientStateCacheIsExactAndInvalidatesEverySemanticKey() {
        CelestialClientStateCache.SingleEntryCache<String> cache =
                new CelestialClientStateCache.SingleEntryCache<>();
        Object level = new Object();
        Object settings = new Object();
        Vec3 observer = new Vec3(1.25D, 64.0D, -987.5D);
        int[] computations = {0};
        java.util.function.Supplier<String> factory = () -> "state-" + ++computations[0];
        String first = cache.get(level, 1234L, 55L, 0.375F, 0.25F, 1200.0D,
                observer, settings, factory);
        String repeated = cache.get(level, 1234L, 55L, 0.375F, 0.25F, 1200.0D,
                observer, settings, factory);
        if (first != repeated || computations[0] != 1) {
            throw new AssertionError("identical client visual queries did not share one state");
        }
        cache.get(level, 1234L, 55L, 0.375F, 0.25F, 1200.0D,
                observer.add(0.0D, 0.0D, 1.0D), settings, factory);
        cache.get(level, 1235L, 55L, 0.375F, 0.25F, 1200.0D, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.375F, 0.25F, 1200.0D, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.25F, 1200.0D, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 1200.0D, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D, observer, settings, factory);
        cache.get(new Object(), 1235L, 56L, 0.5F, 0.5F, 600.0D, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D, observer, new Object(), factory);
        cache.clear();
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D, observer, settings, factory);
        Object stationContext = new Object();
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D,
                observer, settings, stationContext, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D,
                observer, settings, stationContext, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, 600.0D,
                observer, settings, new Object(), factory);
        if (computations[0] != 12) {
            throw new AssertionError("client cache failed to invalidate a position/tick/frame/level/settings key: "
                    + computations[0]);
        }
    }

    private static void auroraCachedGeometryRetainsTheOriginalWave() {
        AuroraRenderer.AuroraStyle style = AuroraRenderer.styleFor(0x5EEDBEEFL, 3);
        for (double animationTicks : new double[]{-12345.5D, 0.0D, 1.25D, 512.0D, 9876543.75D}) {
            double phase = AuroraRenderer.wavePhaseDegrees(animationTicks);
            for (int index : new int[]{0, 1, 7, 31, style.pathX().length - 1}) {
                double oldWave = Math.cos(Math.toRadians((index << 3) + animationTicks * 0.75D));
                double cachedWave = AuroraRenderer.waveOffset(index, phase);
                assertClose(oldWave, cachedWave, "cached aurora wave");
                for (double pole : new double[]{-1.0D, 1.0D}) {
                    int band = Math.min(1, style.bandCount() - 1);
                    double oldZ = pole * 82.0D + style.pathZ()[index]
                            + pole * style.offset() * band * 0.35D + pole * oldWave * 6.0D;
                    double cachedZ = pole * 82.0D + style.pathZ()[index]
                            + pole * style.offset() * band * 0.35D + pole * cachedWave * 6.0D;
                    assertClose(oldZ, cachedZ, "cached aurora vertex displacement");
                }
            }
        }
    }

    private static void auroraAnimationUsesClientTicks() {
        assertClose(0.75D, AuroraRenderer.wavePhaseDegrees(1.0D), "one-client-tick aurora wave");
        assertClose(512.0D, AuroraRenderer.advanceFadeAge(0.0D, 100.0D, 612.0D),
                "512-client-tick aurora fade");
        assertClose(12.5D, AuroraRenderer.advanceFadeAge(10.0D, 200.0D, 202.5D),
                "partial-client-tick aurora fade");
        assertClose(0.0D, AuroraRenderer.advanceFadeAge(400.0D, 300.0D, 299.0D),
                "world-time rollback resets aurora fade");
        assertClose(0.0D, AuroraRenderer.advanceFadeAge(Double.NaN, 0.0D, 1.0D),
                "invalid aurora age reset");
    }

    private static void auroraRulesCoverBothPolesAndLegacyMode() {
        double night = Math.toRadians(-12.0D);
        if (CelestialVisualRules.auroraVisible(false, false, 3, Math.toRadians(49.99D), night, 0.0D)) {
            throw new AssertionError("aurora appeared below 50 degrees");
        }
        if (!CelestialVisualRules.auroraVisible(false, false, 3, Math.toRadians(55.0D), night, 0.0D)
                || !CelestialVisualRules.auroraVisible(false, false, 3, Math.toRadians(-55.0D), night, 0.0D)) {
            throw new AssertionError("aurora latitude rules are not north/south symmetric");
        }
        assertClose(0.02D, CelestialVisualRules.auroraProbability(50.0D), "50 degree probability");
        assertClose(0.42D, CelestialVisualRules.auroraProbability(65.0D), "65 degree probability");
        if (CelestialVisualRules.auroraVisible(false, false, 3, Math.toRadians(70.0D),
                Math.toRadians(-6.0D), 0.0D)) {
            throw new AssertionError("aurora appeared before the -6 degree night boundary");
        }
        if (!CelestialVisualRules.auroraVisible(true, false, 1, 0.0D, Math.toRadians(30.0D), 0.99D)
                || CelestialVisualRules.auroraVisible(true, true, 1, 0.0D, Math.toRadians(30.0D), 0.0D)
                || CelestialVisualRules.auroraVisible(true, false, 0, 0.0D, Math.toRadians(30.0D), 0.0D)) {
            throw new AssertionError("legacy/disabled aurora modes are incorrect");
        }
        assertClose(0.0D, CelestialVisualRules.auroraNightFactor(Math.toRadians(-6.0D)), "aurora dusk alpha");
        assertClose(1.0D, CelestialVisualRules.auroraNightFactor(Math.toRadians(-18.0D)), "aurora night alpha");
    }

    private static void auroraAppearancePresetsAreCompleteAndDeterministic() {
        if (AuroraRenderer.paletteCount() != 24 || AuroraRenderer.geometryCount() != 12) {
            throw new AssertionError("TFCCaelum aurora appearance presets are incomplete");
        }
        AuroraRenderer.AuroraStyle first = AuroraRenderer.styleFor(0x5EEDL, 3);
        AuroraRenderer.AuroraStyle second = AuroraRenderer.styleFor(0x5EEDL, 3);
        if (!first.colors().equals(second.colors()) || !first.geometry().equals(second.geometry())
                || first.bandCount() != second.bandCount() || first.offset() != second.offset()
                || !java.util.Arrays.equals(first.pathX(), second.pathX())
                || !java.util.Arrays.equals(first.pathZ(), second.pathZ())) {
            throw new AssertionError("aurora appearance is not deterministic for a TFC night seed");
        }
        if (first.bandCount() < 1 || first.bandCount() > 3
                || first.pathX().length != first.geometry().nodes()
                || first.pathZ().length != first.geometry().nodes()) {
            throw new AssertionError("aurora geometry or band count is outside the TFCCaelum range");
        }
        for (int maxBands = 1; maxBands <= 3; maxBands++) {
            AuroraRenderer.AuroraStyle style = AuroraRenderer.styleFor(maxBands * 31L, maxBands);
            if (style.bandCount() < 1 || style.bandCount() > maxBands) {
                throw new AssertionError("aurora maxBands was not respected");
            }
        }
    }

    private static void rainbowRulesCoverWeatherAndSolarBounds() {
        if (!CelestialVisualRules.startsRainbow(0.491F, 0.49F, 0.489F, 0.25D, 0.5D)
                || CelestialVisualRules.startsRainbow(0.501F, 0.5F, 0.499F, 0.25D, 0.5D)
                || CelestialVisualRules.startsRainbow(0.489F, 0.49F, 0.488F, 0.25D, 0.5D)
                || CelestialVisualRules.startsRainbow(0.491F, 0.49F, 0.491F, 0.25D, 0.5D)
                || CelestialVisualRules.startsRainbow(1.0F, 0.0F, -0.1F, 0.5D, 0.5D)
                || CelestialVisualRules.startsRainbow(1.0F, 0.0F, -0.1F, 0.25D, -0.1D)
                || CelestialVisualRules.startsRainbow(Float.NaN, 0.0F, -0.1F, 0.25D, 0.5D)) {
            throw new AssertionError("rainbow start rules are incorrect");
        }
        if (!CelestialVisualRules.rainbowVisible(1L, 0.25D, 0.5D)
                || CelestialVisualRules.rainbowVisible(0L, 0.25D, 0.5D)
                || CelestialVisualRules.rainbowVisible(1L, 13000.0D / 24000.0D, 0.5D)
                || CelestialVisualRules.rainbowVisible(1L, 0.25D, 0.0D)) {
            throw new AssertionError("rainbow lifetime/solar bounds are incorrect");
        }
        double start = CelestialVisualRules.rainbowAlpha(CelestialVisualRules.RAINBOW_DURATION_TICKS, 0.0D);
        double peak = CelestialVisualRules.rainbowAlpha(3536L, 0.0D);
        double end = CelestialVisualRules.rainbowAlpha(0L, 0.0D);
        if (!(start < 0.001D) || !(peak > 0.999D) || end != 0.0D
                || CelestialVisualRules.rainbowAlpha(3536L, 1.0D) != 0.0D
                || CelestialVisualRules.rainbowAlpha(3536L, 0.2D) != peak
                || Math.abs(CelestialVisualRules.rainbowAlpha(3536L, 0.6D) - peak * 0.5D) > EPSILON
                || CelestialVisualRules.rainbowAlpha(-1L, 0.0D) != 0.0D
                || CelestialVisualRules.rainbowAlpha(3536L, Double.NaN) != peak) {
            throw new AssertionError("TFCCaelum rainbow curve or unified eclipse masking is incorrect");
        }
        if (CelestialVisualRules.advanceRainbowTimer(5000L, 100L, true) != 4900L
                || CelestialVisualRules.advanceRainbowTimer(5000L, 100L, false) != 5000L
                || CelestialVisualRules.advanceRainbowTimer(100L, Long.MAX_VALUE, true) != 0L
                || CelestialVisualRules.advanceRainbowTimer(100L, -1L, true) != 100L
                || CelestialVisualRules.advanceRainbowTimer(-1L, 1L, true) != 0L) {
            throw new AssertionError("rainbow timer did not remain finite across pauses or time changes");
        }
    }

    private static void rainbowDirectionIsFiniteAtSolarZenith() {
        for (double[] sun : new double[][]{{0.0D, 0.0D}, {1.0E-15D, -1.0E-15D},
                {1.0D, 0.0D}, {0.0D, -1.0D}}) {
            CelestialVisualRules.RainbowDirection direction = CelestialVisualRules.rainbowDirection(sun[0], sun[1]);
            double length = Math.sqrt(direction.x() * direction.x() + direction.y() * direction.y()
                    + direction.z() * direction.z());
            if (!Double.isFinite(direction.x()) || !Double.isFinite(direction.y())
                    || !Double.isFinite(direction.z()) || Math.abs(length - 1.0D) > EPSILON) {
                throw new AssertionError("rainbow direction became invalid at a vertical sun: " + direction);
            }
        }
    }

    private static void eclipseAppearanceIsContinuousAndBounded() {
        assertClose(1.0D, CelestialVisualRules.solarOccultorTextureAlpha(-1.0D),
                "negative-coverage new-moon texture alpha");
        assertClose(0.5D, CelestialVisualRules.solarOccultorTextureAlpha(0.5D),
                "half-coverage new-moon texture alpha");
        assertClose(0.0D, CelestialVisualRules.solarOccultorTextureAlpha(2.0D),
                "over-coverage new-moon texture alpha");
        assertClose(1.0D, CelestialVisualRules.solarOccultorTextureAlpha(Double.NaN),
                "non-finite new-moon texture alpha");
        assertClose(0.2D, CelestialVisualRules.solarOccultorMoonAlpha(0.25D, 1.0D, 0.0D),
                "zero-coverage Moon keeps its ordinary daytime fade");
        assertClose(0.1D, CelestialVisualRules.solarOccultorMoonAlpha(0.25D, 1.0D, 0.5D),
                "partial eclipse continuously fades the ordinary Moon");
        assertClose(0.0D, CelestialVisualRules.solarOccultorMoonAlpha(0.25D, 1.0D, 1.0D),
                "total eclipse removes the ordinary additive Moon texture");
        CelestialVisualRules.MoonTint daytimeMoon = CelestialVisualRules.moonSkyTint(
                0.6D, 0.7D, 0.8D, 0.25D);
        CelestialVisualRules.MoonTint midnightMoon = CelestialVisualRules.moonSkyTint(
                0.6D, 0.7D, 0.8D, 0.75D);
        assertClose(0.7D, daytimeMoon.red(), "daytime Moon inherits sky red");
        assertClose(0.775D, daytimeMoon.green(), "daytime Moon inherits sky green");
        assertClose(0.85D, daytimeMoon.blue(), "daytime Moon inherits sky blue");
        if (!midnightMoon.equals(new CelestialVisualRules.MoonTint(1.0D, 1.0D, 1.0D))
                || !(daytimeMoon.red() < midnightMoon.red())
                || !(daytimeMoon.green() < midnightMoon.green())) {
            throw new AssertionError("Moon tint no longer stays below the sky by day and restores at night");
        }
        CelestialVisualRules.SunTint clear = CelestialVisualRules.solarEclipseSunTint(0.0D);
        CelestialVisualRules.SunTint threshold = CelestialVisualRules.solarEclipseSunTint(0.2D);
        CelestialVisualRules.SunTint partial = CelestialVisualRules.solarEclipseSunTint(0.6D);
        CelestialVisualRules.SunTint total = CelestialVisualRules.solarEclipseSunTint(1.0D);
        if (!clear.equals(new CelestialVisualRules.SunTint(1.0D, 1.0D, 1.0D))
                || !threshold.equals(clear)
                || !CelestialVisualRules.solarEclipseSunTint(Double.NaN).equals(clear)
                || !CelestialVisualRules.solarEclipseSunTint(-1.0D).equals(clear)
                || !CelestialVisualRules.solarEclipseSunTint(2.0D).equals(total)) {
            throw new AssertionError("solar eclipse Sun tint stopped clamping invalid coverage");
        }
        assertClose(0.86D, partial.green(), "partial eclipse warm corona green");
        assertClose(0.59D, partial.blue(), "partial eclipse warm corona blue");
        assertClose(0.72D, total.green(), "total eclipse warm corona green");
        assertClose(0.18D, total.blue(), "total eclipse warm corona blue");
        if (!(clear.blue() > partial.blue() && partial.blue() > total.blue())
                || !(clear.green() > partial.green() && partial.green() > total.green())) {
            throw new AssertionError("solar corona warmth is not monotonic with eclipse coverage");
        }
    }

    private static void solarOccultorTextureMatchesOrdinaryNewMoonAtZeroCoverage() {
        try (InputStream occultorStream = CelestialClientSelfTest.class.getResourceAsStream(
                "/assets/wildfires/textures/sky/eclipse/new_moon_0.png");
             InputStream atlasStream = CelestialClientSelfTest.class.getResourceAsStream(
                     "/assets/minecraft/textures/environment/moon_phases.png")) {
            if (occultorStream == null || atlasStream == null) {
                throw new AssertionError("missing solar occultor or Minecraft moon atlas test resource");
            }
            BufferedImage occultor = ImageIO.read(occultorStream);
            BufferedImage atlas = ImageIO.read(atlasStream);
            if (occultor == null || atlas == null || occultor.getWidth() != 32 || occultor.getHeight() != 32
                    || atlas.getWidth() != 128 || atlas.getHeight() != 64) {
                throw new AssertionError("unexpected eclipse/new-moon texture dimensions");
            }
            int opaqueBodyPixels = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int occultorArgb = occultor.getRGB(x, y);
                    int alpha = occultorArgb >>> 24;
                    boolean bodyPixel = x >= 12 && x <= 19 && y >= 12 && y <= 19;
                    if (bodyPixel) {
                        int ordinaryNewMoonArgb = atlas.getRGB(x, y + 32);
                        if (occultorArgb != ordinaryNewMoonArgb || alpha != 255) {
                            throw new AssertionError("zero-coverage occultor differs from ordinary phase-4 new moon at "
                                    + x + "," + y + ": " + Integer.toHexString(occultorArgb)
                                    + " != " + Integer.toHexString(ordinaryNewMoonArgb));
                        }
                        opaqueBodyPixels++;
                    } else if (alpha != 0) {
                        throw new AssertionError("solar occultor contains pixels outside the centered 8x8 body at "
                                + x + "," + y);
                    }
                }
            }
            if (opaqueBodyPixels != 64) {
                throw new AssertionError("solar occultor lost its 8x8 Minecraft body");
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to compare solar occultor with the ordinary new moon", exception);
        }
    }

    private static void eclipseBodiesUsePhysicalScaleAndMovingSquareShadow() {
        double sunBodyHalfSize = 30.0D * 0.725D * 4.0D / 16.0D;
        double moonHalfSize = CelestialVisualRules.moonAtlasBodyHalfSize(20.0D);
        assertClose(5.0D, moonHalfSize, "rendered solar occultor half-size");
        if (!(sunBodyHalfSize > moonHalfSize)
                || CelestialVisualRules.moonAtlasBodyHalfSize(-1.0D) != 0.0D
                || CelestialVisualRules.moonAtlasBodyHalfSize(Double.NaN) != 0.0D) {
            throw new AssertionError("solar occultor no longer matches the ordinary rendered lunar pixel body");
        }
        CelestialVisualRules.SolarOccultorTint clear = CelestialVisualRules.solarOccultorTint(
                0.6D, 0.7D, 0.8D, 0.0D);
        CelestialVisualRules.SolarOccultorTint partial = CelestialVisualRules.solarOccultorTint(
                0.6D, 0.7D, 0.8D, 0.5D);
        CelestialVisualRules.SolarOccultorTint total = CelestialVisualRules.solarOccultorTint(
                0.6D, 0.7D, 0.8D, 1.0D);
        assertClose(0.6D, clear.red(), "first-contact occultor matches local sky");
        assertClose((0.6D + 0.008D) * 0.5D, partial.red(), "partial occultor darkness");
        assertClose(0.008D, total.red(), "maximum occultor darkness");
        if (!(clear.red() > partial.red() && partial.red() > total.red())
                || !CelestialVisualRules.solarOccultorTint(0.6D, 0.7D, 0.8D, Double.NaN)
                .equals(clear)) {
            throw new AssertionError("solar occultor darkness no longer follows pixel coverage continuously");
        }

        CelestialVisualRules.LunarShadow centered = CelestialVisualRules.lunarShadow(
                new LunarEclipseState(0L, 0.0D, 0.0D, 0.0D,
                        0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        if (!centered.visible()) {
            throw new AssertionError("centered terrestrial shadow was rejected");
        }
        assertClose(0.0D, centered.centerX(), "centered lunar shadow x");
        assertClose(0.0D, centered.centerY(), "centered lunar shadow y");
        assertClose(1.0D, centered.radius(), "equal-size lunar umbra radius");
        assertClose(1.0D, CelestialVisualRules.lunarEclipseMoonlight(1.0D, 0.0D),
                "uneclipsed full-moon veil");
        assertClose(0.56D, CelestialVisualRules.lunarEclipseMoonlight(1.0D, 0.5D),
                "partial-eclipse moon veil");
        assertClose(0.12D, CelestialVisualRules.lunarEclipseMoonlight(1.0D, 1.0D),
                "total-eclipse moon veil");

        CelestialVisualRules.LunarShadow shifted = CelestialVisualRules.lunarShadow(
                new LunarEclipseState(0L, 0.0D, 0.0D, 0.0D,
                        0.5D, 0.0D, 1.0D, 0.75D, 0.875D));
        assertClose(0.5D, shifted.centerX(), "moving lunar shadow x");
        assertClose(0.0D, shifted.centerY(), "moving lunar shadow y");
        if (!shifted.visible() || CelestialVisualRules.lunarShadow(LunarEclipseState.NONE).visible()
                || CelestialVisualRules.lunarShadow(new LunarEclipseState(0L, 0.0D,
                0.0D, 0.0D, 2.5D, 0.0D, 1.0D, 0.0D, 0.0D)).visible()) {
            throw new AssertionError("client lunar shadow did not follow the authoritative projection state");
        }
    }

    private static void lunarShadowProjectionMatchesAuthoritativeCoverage() {
        double cycleDays = CelestialMath.daysInYear(8) * CelestialMath.NODAL_YEARS;
        double maximumDifference = 0.0D;
        int samples = 0;
        for (double day = 0.0D; day < cycleDays; day += 0.02D) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    10_000.0D, 20_000.0D, day * CelestialMath.TICKS_IN_DAY, 8));
            if (!result.lunarEclipseRegion().active()) {
                continue;
            }
            CelestialVisualRules.LunarShadow shadow = CelestialVisualRules.lunarShadow(
                    result.lunarEclipseRegion());
            if (!shadow.visible()) {
                throw new AssertionError("authoritative lunar eclipse has no client shadow at day " + day);
            }
            CelestialDiscGeometry.AlignedSquare square = new CelestialDiscGeometry.AlignedSquare(
                    shadow.centerX(), shadow.centerY(), shadow.radius(), true);
            double renderedCoverage = CelestialDiscGeometry.alignedSquareCoverage(square);
            double renderedPenumbra = CelestialVisualRules.lunarPenumbraCoverage(shadow);
            maximumDifference = Math.max(maximumDifference,
                    Math.abs(renderedCoverage - result.lunarEclipse()));
            maximumDifference = Math.max(maximumDifference, Math.abs(renderedPenumbra
                    - result.lunarEclipseRegion().penumbraCoverage()));
            samples++;
        }
        if (samples == 0 || maximumDifference > 1.0E-4D) {
            throw new AssertionError("client square lunar shadow diverges from authoritative coverage: samples="
                    + samples + ", max difference=" + maximumDifference);
        }
    }

    private static void lunarPenumbraStartsRedAtFirstContact() {
        double penumbraWidth = CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH;
        CelestialVisualRules.LunarShadow centered = new CelestialVisualRules.LunarShadow(
                0.0D, 0.0D, 1.0D, true);
        assertClose(1.0D, CelestialVisualRules.lunarPenumbraCoverage(centered),
                "centered expanded lunar penumbra coverage");

        double firstContactCenter = 1.0D + centered.radius() + penumbraWidth;
        CelestialVisualRules.LunarShadow exactContact = new CelestialVisualRules.LunarShadow(
                firstContactCenter, 0.0D, centered.radius(), true);
        CelestialVisualRules.LunarShadow justInside = new CelestialVisualRules.LunarShadow(
                firstContactCenter - 1.0E-4D, 0.0D, centered.radius(), true);
        CelestialVisualRules.LunarShadow justOutside = new CelestialVisualRules.LunarShadow(
                firstContactCenter + 1.0E-4D, 0.0D, centered.radius(), false);
        assertClose(0.0D, CelestialVisualRules.lunarPenumbraCoverage(exactContact),
                "zero-area lunar penumbra contact");
        if (!(CelestialVisualRules.lunarPenumbraCoverage(justInside) > 0.0D)
                || CelestialVisualRules.lunarPenumbraCoverage(justOutside) != 0.0D) {
            throw new AssertionError("lunar face red did not begin at the first positive penumbra contact");
        }

        CelestialVisualRules.LunarShadow umbraSeparated = new CelestialVisualRules.LunarShadow(
                2.1D, 0.0D, centered.radius(), true);
        double xUmbraOverlap = Math.max(0.0D,
                Math.min(1.0D, umbraSeparated.centerX() + umbraSeparated.radius())
                        - Math.max(-1.0D, umbraSeparated.centerX() - umbraSeparated.radius()));
        if (xUmbraOverlap != 0.0D
                || !(CelestialVisualRules.lunarPenumbraCoverage(umbraSeparated) > 0.0D)) {
            throw new AssertionError("one-pixel penumbra did not redden the Moon before umbra contact");
        }
    }

    private static void managedEclipseAndAuroraShadersPreserveTheSkyBehindThem() {
        String auroraJson = resourceText("/assets/wildfires/shaders/core/aurora.json");
        String auroraFragment = resourceText("/assets/wildfires/shaders/core/aurora.fsh");
        String lunarJson = resourceText("/assets/wildfires/shaders/core/lunar_eclipse.json");
        String lunarFragment = resourceText("/assets/wildfires/shaders/core/lunar_eclipse.fsh");
        if (!auroraJson.contains("\"dstrgb\": \"one\"")
                || !auroraFragment.contains("discard;")
                || !auroraFragment.contains("Alpha * smoothstep")
                || !lunarJson.contains("\"ShadowRadius\", \"type\": \"float\", \"count\": 1, \"values\": [1]")
                || !lunarJson.contains("\"PenumbraIntensity\", \"type\": \"float\", \"count\": 1, \"values\": [0]")
                || !lunarJson.contains("\"SkyColor\", \"type\": \"float\", \"count\": 3")
                || !lunarJson.contains("\"MoonTint\", \"type\": \"float\", \"count\": 3")
                || !lunarJson.contains("\"MoonAlpha\", \"type\": \"float\", \"count\": 1")
                || !lunarFragment.contains("max(shadowOffset.x, shadowOffset.y)")
                || !lunarFragment.contains("PENUMBRA_WIDTH = 0.25")
                || !lunarFragment.contains("inheritedSky = clamp(SkyColor")
                || !lunarFragment.contains("moonVisibility = clamp(MoonAlpha, 0.0, 1.0)")
                || !lunarFragment.contains("max(PenumbraIntensity, umbraIntensity)")
                || !lunarFragment.contains("baseRedAlpha = 0.80")
                || !lunarFragment.contains("PENUMBRA_EXTRA_RED_ALPHA = 0.16")
                || !lunarFragment.contains("float umbraAlpha = umbra;")
                || !lunarFragment.contains("vec3 bloodRed = mix(inheritedSky")
                || !lunarFragment.contains("vec3 penumbraDeepRed = mix(inheritedSky")
                || !lunarFragment.contains("vec3 umbraColor = mix(inheritedSky")
                || lunarFragment.contains("opacity = clamp(baseRedAlpha + penumbraExtraAlpha + umbraAlpha, 0.0, 1.0)\n            * clamp(MoonAlpha")
                || lunarFragment.contains("totalityRed")) {
            throw new AssertionError("managed eclipse/aurora shaders lost additive transparency or square shadow");
        }
        double baseRed = 0.48D;
        double penumbraRed = 0.18D;
        double softenedRingRed = (baseRed * 0.80D + penumbraRed * 0.16D) / (0.80D + 0.16D);
        if (!(softenedRingRed < baseRed) || !(softenedRingRed > penumbraRed)) {
            throw new AssertionError("softened lunar penumbra no longer remains visibly deeper than the base red Moon");
        }
    }

    private static String resourceText(String path) {
        try (InputStream stream = CelestialClientSelfTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("missing test resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("failed to read test resource " + path, exception);
        }
    }

    private static void moonAtlasAndSkyCoverRulesAvoidDaytimeArtifacts() {
        for (int phase = 0; phase < 8; phase++) {
            if (!CelestialVisualRules.moonTextureVisible(phase)) {
                throw new AssertionError("unexpected ordinary moon texture visibility for phase " + phase);
            }
        }
        if (CelestialVisualRules.moonTextureVisible(-1) || CelestialVisualRules.moonTextureVisible(8)) {
            throw new AssertionError("out-of-range moon atlas cells were accepted");
        }

        double midnight = 0.75D;
        if (CelestialVisualRules.moonSkyCoverVisible(-1, midnight, 1.0D)
                || CelestialVisualRules.moonSkyCoverVisible(8, midnight, 1.0D)) {
            throw new AssertionError("moon foreground cover accepted an invalid atlas phase");
        }
        for (int phase = 0; phase < 8; phase++) {
            if (!CelestialVisualRules.moonSkyCoverVisible(phase, midnight, 1.0D)
                    || !CelestialVisualRules.moonSkyCoverVisible(phase, 0.25D, 1.0D)
                    || !CelestialVisualRules.moonSkyCoverVisible(phase, midnight, 0.0D)) {
                throw new AssertionError("moon phase lost its global foreground layer: " + phase);
            }
        }
        assertClose(5.0D, CelestialVisualRules.moonAtlasBodyHalfSize(20.0D),
                "vanilla moon atlas pixel-body half size");
        assertClose(7.5D, CelestialVisualRules.sunAtlasBodyHalfSize(30.0D),
                "TFC solar atlas pixel-body half size");
        assertClose(15.0D, CelestialVisualRules.sunAtlasBodyHalfSize(60.0D),
                "configured full Sun and body cover scale together");
        assertClose(13.75D, CelestialVisualRules.moonAtlasGlowRadius(20.0D),
                "vanilla moon atlas gradient radius");
        if (CelestialVisualRules.moonAtlasBodyHalfSize(0.0D) != 0.0D
                || CelestialVisualRules.moonAtlasBodyHalfSize(-1.0D) != 0.0D
                || CelestialVisualRules.moonAtlasBodyHalfSize(Double.NaN) != 0.0D
                || CelestialVisualRules.sunAtlasBodyHalfSize(Double.POSITIVE_INFINITY) != 0.0D
                || CelestialVisualRules.moonAtlasGlowRadius(Double.POSITIVE_INFINITY) != 0.0D) {
            throw new AssertionError("invalid moon atlas radii were accepted");
        }
        if (CelestialVisualRules.sunSkyCoverVisible(0.25D, 1.0D)
                || CelestialVisualRules.sunSkyCoverVisible(midnight, 0.0D)
                || !CelestialVisualRules.sunSkyCoverVisible(midnight, 1.0D)) {
            throw new AssertionError("solar body cover did not follow visible background stars");
        }
    }

    private static void supermoonBlueTintTracksLocalNightWeatherAndEclipse() {
        double midnight = CelestialVisualRules.supermoonBlueIntensity(0.75D, 1.0D,
                1.0D, 0.0D, -0.5D, 0.5D);
        double sunset = CelestialVisualRules.supermoonBlueIntensity(0.5D, 1.0D,
                1.0D, 0.0D, -0.01D, 0.5D);
        double dawn = CelestialVisualRules.supermoonBlueIntensity(0.0D, 1.0D,
                1.0D, 0.0D, -0.01D, 0.5D);
        assertClose(1.0D, midnight, "midnight supermoon blue peak");
        assertClose(0.0D, sunset, "sunset supermoon remains white");
        assertClose(0.0D, dawn, "dawn supermoon returns white");
        assertClose(0.5D, CelestialVisualRules.supermoonBlueIntensity(0.75D, 0.5D,
                1.0D, 0.0D, -0.5D, 0.5D), "weather attenuates blue moon");
        LunarEclipseState penumbralContact = new LunarEclipseState(0L, 0.0D,
                0.0D, 0.0D, 1.75D, 0.0D, 1.0D, 0.0D, 0.25D);
        assertClose(0.25D, CelestialVisualRules.lunarEclipseTintCoverage(penumbralContact),
                "penumbral contact drives blue-to-red transition");
        assertClose(0.75D, CelestialVisualRules.supermoonBlueIntensity(0.75D, 1.0D,
                1.0D, CelestialVisualRules.lunarEclipseTintCoverage(penumbralContact),
                -0.5D, 0.5D), "pure penumbra begins removing blue moon");
        assertClose(0.0D, CelestialVisualRules.lunarEclipseTintCoverage(LunarEclipseState.NONE),
                "no lunar eclipse preserves blue moon");
        assertClose(0.4D, CelestialVisualRules.supermoonBlueIntensity(0.75D, 1.0D,
                1.0D, 0.6D, -0.5D, 0.5D), "lunar eclipse removes blue moon");
        assertClose(0.0D, CelestialVisualRules.supermoonBlueIntensity(0.75D, 1.0D,
                1.0D, 0.0D, 0.1D, 0.5D), "daylight rejects blue moon");
        assertClose(0.0D, CelestialVisualRules.supermoonBlueIntensity(0.75D, 1.0D,
                1.0D, 0.0D, -0.5D, -0.1D), "moon below horizon rejects blue moon");
        CelestialVisualRules.MoonTint blue = CelestialVisualRules.supermoonTint(
                new CelestialVisualRules.MoonTint(1.0D, 1.0D, 1.0D), 1.0D);
        assertClose(0.45D, blue.red(), "blue moon red channel");
        assertClose(0.72D, blue.green(), "blue moon green channel");
        assertClose(1.0D, blue.blue(), "blue moon blue channel");
        CelestialState midnightState = planetariumState(0.0D);
        CelestialState noonState = planetariumState(0.5D);
        if (!midnightState.localNight() || !midnightState.moonAboveHorizon()
                || !midnightState.visibleSupermoon() || midnightState.visibleSolarEclipse()
                || noonState.visibleSupermoon()) {
            throw new AssertionError("public local Sun/Moon event API ignored day, night or horizon");
        }
        CelestialEventState events = CelestialEventState.from(midnightState);
        if (!events.localNight() || !events.fullMoon() || !events.supermoonVisible()
                || events.solarEclipseVisible()) {
            throw new AssertionError("public event snapshot diverged from CelestialState");
        }
    }

    private static void publicPhaseEventApiRequiresCorrectLocalLight() {
        boolean sawDayFirstQuarter = false;
        boolean sawNightFirstQuarter = false;
        boolean sawDayLastQuarter = false;
        boolean sawNightLastQuarter = false;
        double endDay = CelestialMath.SYNODIC_DAYS * 9.0D;
        for (double day = 0.0D; day <= endDay; day += 1.0D / 240.0D) {
            CelestialState state = planetariumState(day);
            CelestialEventState events = CelestialEventState.from(state);
            boolean firstGeometry = state.moonPhase() == 2
                    && Math.abs(state.moon().illuminatedFraction() - 0.5D) <= 0.03D
                    && state.moonAboveHorizon();
            boolean lastGeometry = state.moonPhase() == 6
                    && Math.abs(state.moon().illuminatedFraction() - 0.5D) <= 0.03D
                    && state.moonAboveHorizon();
            if (firstGeometry && state.localDay()) {
                sawDayFirstQuarter = true;
                if (events.firstQuarter()) {
                    throw new AssertionError("first-quarter API succeeded during local daylight");
                }
            } else if (firstGeometry && state.localNight()) {
                sawNightFirstQuarter = true;
                if (!events.firstQuarter()) {
                    throw new AssertionError("first-quarter API rejected a visible local night");
                }
            }
            if (lastGeometry && state.localDay()) {
                sawDayLastQuarter = true;
                if (events.lastQuarter()) {
                    throw new AssertionError("last-quarter API succeeded during local daylight");
                }
            } else if (lastGeometry && state.localNight()) {
                sawNightLastQuarter = true;
                if (!events.lastQuarter()) {
                    throw new AssertionError("last-quarter API rejected a visible local night");
                }
            }
            if ((events.fullMoon() || events.lunarEclipseVisible()
                    || events.supermoonVisible() || events.bloodMoonVisible())
                    && !events.localNight()) {
                throw new AssertionError("public lunar event escaped its local-night boundary");
            }
            if ((events.newMoon() || events.solarEclipseVisible()) && !events.localDay()) {
                throw new AssertionError("public solar-aligned event escaped its local-day boundary");
            }
        }
        if (!sawDayFirstQuarter || !sawNightFirstQuarter
                || !sawDayLastQuarter || !sawNightLastQuarter) {
            throw new AssertionError("phase API scan did not cover both local light states: "
                    + sawDayFirstQuarter + "/" + sawNightFirstQuarter + "/"
                    + sawDayLastQuarter + "/" + sawNightLastQuarter);
        }
    }

    private static void moonHaloAttenuatesNearbyStarsByPhaseAndDistance() {
        CelestialVisualRules.MoonHalo none = CelestialVisualRules.moonHalo(0.0D, 1.0D);
        CelestialVisualRules.MoonHalo crescent = CelestialVisualRules.moonHalo(0.25D, 1.0D);
        CelestialVisualRules.MoonHalo gibbous = CelestialVisualRules.moonHalo(0.75D, 1.0D);
        CelestialVisualRules.MoonHalo full = CelestialVisualRules.moonHalo(1.0D, 1.0D);
        if (none.centerAlpha() != 0.0D || none.radiusMultiplier() != 0.0D
                || !(crescent.centerAlpha() < gibbous.centerAlpha()
                && gibbous.centerAlpha() < full.centerAlpha())
                || !(crescent.radiusMultiplier() < gibbous.radiusMultiplier()
                && gibbous.radiusMultiplier() < full.radiusMultiplier())) {
            throw new AssertionError("moon halo did not grow monotonically with illuminated fraction");
        }
        double center = CelestialVisualRules.moonHaloAlpha(full, 0.0D);
        double middle = CelestialVisualRules.moonHaloAlpha(full, 0.5D);
        double edge = CelestialVisualRules.moonHaloAlpha(full, 1.0D);
        if (!(center > middle && middle > edge) || edge != 0.0D
                || CelestialVisualRules.moonHaloAlpha(full, 2.0D) != 0.0D
                || CelestialVisualRules.moonHaloAlpha(full, Double.NaN) != 0.0D
                || CelestialVisualRules.moonHalo(Double.NaN, 1.0D).centerAlpha() != 0.0D
                || CelestialVisualRules.moonHalo(1.0D, 0.0D).centerAlpha() != 0.0D) {
            throw new AssertionError("moon halo radial attenuation or invalid-input bounds changed");
        }
        assertClose(full.centerAlpha() * 0.5D,
                CelestialVisualRules.moonHalo(1.0D, 0.5D).centerAlpha(),
                "weather-dimmed moon halo");
        assertClose(48.125D,
                CelestialVisualRules.moonAtlasGlowRadius(20.0D) * full.radiusMultiplier(),
                "full-moon halo radius around the atlas gradient");
    }

    private static void planetRenderingRestoresTfccaelumArcSecondScale() {
        double jupiterRadius = Math.atan((40.0D / 206265.0D) * 0.5D);
        double saturnRadius = Math.atan((18.0D / 206265.0D) * 0.5D);
        double jupiter = CelestialVisualRules.planetRenderRadius(jupiterRadius,
                CelestialBodies.JUPITER.scaleFactor(), 1.0D);
        double saturn = CelestialVisualRules.planetRenderRadius(saturnRadius,
                CelestialBodies.SATURN.scaleFactor(), 1.0D);
        assertClose(5.0D, jupiter, "TFCCaelum Jupiter visual radius");
        assertClose(2.7D, saturn, "TFCCaelum Saturn visual radius");
        assertClose(jupiter * 0.5D, CelestialVisualRules.planetRenderRadius(jupiterRadius,
                CelestialBodies.JUPITER.scaleFactor(), 0.5D), "global planet scale");
        if (!(jupiter > saturn && saturn > 1.0D)) {
            throw new AssertionError("planet size ordering was lost: Jupiter=" + jupiter + ", Saturn=" + saturn);
        }
        double tinyExpected = 2.0D * Math.tan(1.0E-12D) * 206265.0D;
        assertClose(tinyExpected, CelestialVisualRules.planetRenderRadius(1.0E-12D, 1.0D, 1.0D),
                "unclamped source minimum planet radius");
        double largeExpected = 2.0D * Math.tan(1.0D) * 206265.0D * 100.0D * 100.0D;
        assertClose(largeExpected, CelestialVisualRules.planetRenderRadius(1.0D, 100.0D, 100.0D),
                "unclamped source maximum planet radius");
        if (CelestialVisualRules.planetRenderRadius(Double.NaN, 1.0D, 1.0D) != 0.0D
                || CelestialVisualRules.planetRenderRadius(jupiterRadius, 0.0D, 1.0D) != 0.0D
                || CelestialVisualRules.planetRenderRadius(jupiterRadius, 1.0D, Double.POSITIVE_INFINITY) != 0.0D) {
            throw new AssertionError("invalid planet visual inputs were not rejected");
        }
        double smaller = CelestialVisualRules.planetRenderRadius(Math.toRadians(1.0D / 3600.0D / 2.0D),
                1.0D, 1.0D);
        double larger = CelestialVisualRules.planetRenderRadius(Math.toRadians(4.0D / 3600.0D / 2.0D),
                1.0D, 1.0D);
        if (!(larger > smaller)) {
            throw new AssertionError("source linear scale was not monotonic");
        }
        double representativeRadius = Math.atan((1.0D / 206265.0D) * 0.5D);
        for (CelestialBodies body : CelestialBodies.values()) {
            assertClose(body.scaleFactor(), CelestialVisualRules.planetRenderRadius(
                    representativeRadius, body.scaleFactor(), 1.0D),
                    body + " retained its TFCCaelum scale factor");
        }
    }

    private static void satelliteRenderingRetainsVisibleThreeDimensionalSeparation() {
        double physicalAngle = 0.01D;
        CelestialVector parent = new CelestialVector(1.0D, 0.0D, 0.0D);
        CelestialVector satellite = new CelestialVector(Math.cos(physicalAngle), Math.sin(physicalAngle), 0.0D);
        CelestialVector rendered = CelestialVisualRules.satelliteRenderDirection(parent, satellite);
        double renderedAngle = Math.acos(Math.max(-1.0D, Math.min(1.0D, parent.dot(rendered))));
        assertClose(physicalAngle * CelestialVisualRules.SATELLITE_ORBIT_RENDER_SCALE, renderedAngle,
                "TFCCaelum satellite visual separation");
        if (!Double.isFinite(rendered.x()) || !Double.isFinite(rendered.y()) || !Double.isFinite(rendered.z())
                || Math.abs(rendered.length() - 1.0D) > EPSILON
                || !CelestialVisualRules.satelliteRenderDirection(CelestialVector.ZERO, satellite)
                .equals(satellite)) {
            throw new AssertionError("satellite visual projection was not finite and isolated from physical state");
        }

        for (double sourceAngle : new double[]{1.0E-5D, 1.0E-3D, 0.02D}) {
            CelestialVector source = new CelestialVector(Math.cos(sourceAngle), Math.sin(sourceAngle), 0.0D);
            CelestialVector projected = CelestialVisualRules.satelliteRenderDirection(parent, source);
            double projectedAngle = Math.acos(clampDot(parent.dot(projected)));
            assertClose(sourceAngle * CelestialVisualRules.SATELLITE_ORBIT_RENDER_SCALE, projectedAngle,
                    "TFCCaelum satellite x50 separation at " + sourceAngle);
        }
    }

    private static void actualJupiterAndSaturnSatellitesOrbitTheirParents() {
        for (CelestialBodies satellite : List.of(CelestialBodies.GANYMEDE, CelestialBodies.TITAN)) {
            if (satellite.orbitReferenceFrame()
                    != CelestialBodies.OrbitReferenceFrame.J2000_EQUATORIAL_POLE
                    || Math.abs(satellite.orbitalReferenceNormalEcliptic().length() - 1.0D) > 1.0E-9D) {
                throw new AssertionError(satellite + " escaped its JPL local Laplace-plane contract");
            }
        }
        assertSatelliteQuarterOrbit(CelestialBodies.GANYMEDE, CelestialBodies.JUPITER, 4321.25D);
        assertSatelliteQuarterOrbit(CelestialBodies.TITAN, CelestialBodies.SATURN, 8765.5D);
    }

    private static void celestialDiscOrientationIsContinuousAcrossTheZenith() {
        CelestialVector north = new CelestialVector(0.23D, 0.31D, 0.92D).normalized();
        CelestialVisualRules.DiscBasis previous = null;
        for (int step = -100; step <= 100; step++) {
            double angle = step * 0.0005D;
            CelestialVector direction = new CelestialVector(Math.sin(angle), Math.cos(angle), 0.0D);
            CelestialVisualRules.DiscBasis basis = CelestialVisualRules.stableDiscBasis(direction, north);
            assertDiscBasis(direction, basis, "zenith step " + step);
            if (previous != null && (previous.right().dot(basis.right()) < 0.999D
                    || previous.up().dot(basis.up()) < 0.999D)) {
                throw new AssertionError("celestial disc frame flipped while crossing the zenith at step " + step);
            }
            previous = basis;
        }

        CelestialVector pole = new CelestialVector(0.0D, 1.0D, 0.0D);
        assertDiscBasis(pole, CelestialVisualRules.stableDiscBasis(pole, pole),
                "celestial-pole fallback");
        assertDiscBasis(pole, CelestialVisualRules.stableDiscBasis(
                new CelestialVector(Double.NaN, 0.0D, 0.0D), CelestialVector.ZERO),
                "non-finite fallback");
    }

    private static void horizonTwilightAndSolarAppearanceAreContinuous() {
        CelestialVisualRules.HorizonFrame frame = CelestialVisualRules.horizonFrame(
                new CelestialVector(0.3D, -0.8D, -0.4D));
        assertClose(0.0D, frame.horizon().y(), "twilight center stays on the horizon");
        assertClose(0.0D, frame.right().y(), "twilight long axis stays on the horizon");
        assertClose(1.0D, frame.up().y(), "twilight short axis follows world up");
        assertClose(0.0D, frame.horizon().dot(frame.right()), "twilight horizon/right orthogonality");
        assertClose(0.0D, frame.horizon().dot(frame.up()), "twilight horizon/up orthogonality");
        assertClose(0.0D, frame.right().dot(frame.up()), "twilight right/up orthogonality");
        assertClose(1.0D, frame.horizon().length(), "twilight horizon unit length");
        assertClose(1.0D, frame.right().length(), "twilight right unit length");
        CelestialVisualRules.HorizonFrame zenith = CelestialVisualRules.horizonFrame(
                new CelestialVector(0.0D, 1.0D, 0.0D));
        if (!CelestialVisualRules.celestialDiscRenderable(zenith.horizon())
                || !CelestialVisualRules.celestialDiscRenderable(zenith.right())) {
            throw new AssertionError("twilight horizon fallback was not finite at the solar zenith");
        }

        double previousGreen = -1.0D;
        double previousBlue = -1.0D;
        double previousTwilight = CelestialVisualRules.twilightAlpha(Math.toRadians(-25.0D), 1.0D);
        for (int degrees = -24; degrees <= 30; degrees++) {
            double altitude = Math.toRadians(degrees);
            CelestialVisualRules.SunAppearance appearance = CelestialVisualRules.sunAppearance(altitude);
            if (appearance.green() + EPSILON < previousGreen
                    || appearance.blue() + EPSILON < previousBlue) {
                throw new AssertionError("solar tint was not monotonic at " + degrees + " degrees");
            }
            assertUnitInterval(appearance.red(), "solar red tint");
            assertUnitInterval(appearance.green(), "solar green tint");
            assertUnitInterval(appearance.blue(), "solar blue tint");
            double twilight = CelestialVisualRules.twilightAlpha(altitude, 1.0D);
            if (Math.abs(twilight - previousTwilight) > 0.25D) {
                throw new AssertionError("twilight alpha changed too abruptly at " + degrees + " degrees");
            }
            previousGreen = appearance.green();
            previousBlue = appearance.blue();
            previousTwilight = twilight;
        }
        CelestialVisualRules.SunAppearance high = CelestialVisualRules.sunAppearance(Math.toRadians(20.0D));
        CelestialVisualRules.SunAppearance horizon = CelestialVisualRules.sunAppearance(0.0D);
        CelestialVisualRules.SunAppearance below = CelestialVisualRules.sunAppearance(Math.toRadians(-8.0D));
        if (!(high.blue() > horizon.blue() && horizon.blue() > below.blue())) {
            throw new AssertionError("setting Sun did not warm its complete texture");
        }

        CelestialVector belowHorizon = new CelestialVector(0.6D, -0.8D, 0.0D);
        if (!CelestialVisualRules.celestialDiscRenderable(belowHorizon)
                || CelestialVisualRules.planetVisibility(0.75D, 1.0D) <= 0.0D) {
            throw new AssertionError("a valid below-horizon celestial body was deleted by altitude");
        }
    }

    private static void assertDiscBasis(CelestialVector direction, CelestialVisualRules.DiscBasis basis,
                                        String label) {
        CelestialVector unitDirection = direction.normalized();
        for (CelestialVector vector : new CelestialVector[]{basis.right(), basis.up()}) {
            if (!Double.isFinite(vector.x()) || !Double.isFinite(vector.y()) || !Double.isFinite(vector.z())
                    || Math.abs(vector.length() - 1.0D) > EPSILON) {
                throw new AssertionError(label + " produced a non-finite or non-unit frame: " + basis);
            }
        }
        if (Math.abs(basis.right().dot(basis.up())) > EPSILON
                || Math.abs(basis.right().dot(unitDirection)) > EPSILON
                || Math.abs(basis.up().dot(unitDirection)) > EPSILON) {
            throw new AssertionError(label + " produced a non-orthogonal frame: " + basis);
        }
    }

    private static void assertSatelliteQuarterOrbit(CelestialBodies satellite, CelestialBodies parent,
                                                     double startCalendarDays) {
        double endCalendarDays = startCalendarDays + satellite.orbitalDays() * 0.25D
                * CelestialMath.daysInYear(8) / CelestialBodies.EARTH_ORBITAL_DAYS;
        var startStates = orbitingStatesAt(startCalendarDays);
        var endStates = orbitingStatesAt(endCalendarDays);
        var startParent = startStates.get(parent);
        var startSatellite = startStates.get(satellite);
        var endParent = endStates.get(parent);
        var endSatellite = endStates.get(satellite);

        CelestialVector startRelative = startSatellite.geocentricPosition()
                .subtract(startParent.geocentricPosition()).normalized();
        CelestialVector endRelative = endSatellite.geocentricPosition()
                .subtract(endParent.geocentricPosition()).normalized();
        double physicalTurn = Math.acos(clampDot(startRelative.dot(endRelative)));
        if (Math.abs(physicalTurn - Math.PI * 0.5D) > 1.0E-6D) {
            throw new AssertionError(satellite + " did not complete a physical quarter orbit: " + physicalTurn);
        }

        CelestialVector startRendered = CelestialVisualRules.satelliteRenderDirection(
                startParent.observerDirection(), startSatellite.observerDirection());
        CelestialVector endRendered = CelestialVisualRules.satelliteRenderDirection(
                endParent.observerDirection(), endSatellite.observerDirection());
        double renderedMotion = Math.acos(clampDot(startRendered.dot(endRendered)));
        if (!(renderedMotion > 0.01D) || !Double.isFinite(renderedMotion)) {
            throw new AssertionError(satellite + " lost its visible orbit animation: " + renderedMotion);
        }
    }

    private static java.util.Map<CelestialBodies, first.wildfires.api.celestial.CelestialBodyState>
    orbitingStatesAt(double calendarDays) {
        double calendarTicks = calendarDays * CelestialMath.TICKS_IN_DAY;
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(
                10000.0D, 20000.0D, calendarTicks, 8));
        var states = CelestialBodies.calculate(frame, CelestialMath.calendarYears(calendarTicks, 8));
        java.util.EnumMap<CelestialBodies, first.wildfires.api.celestial.CelestialBodyState> byBody =
                new java.util.EnumMap<>(CelestialBodies.class);
        for (var state : states) {
            byBody.put(CelestialBodies.valueOf(state.id().getPath().toUpperCase(java.util.Locale.ROOT)), state);
        }
        return byBody;
    }

    private static double clampDot(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static void allOrbitingBodiesHaveFiniteVisualSizes() {
        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(
                10000.0D, 20000.0D, 1234.5D * CelestialMath.TICKS_IN_DAY, 8));
        var states = CelestialBodies.calculate(frame, CelestialMath.calendarYears(
                1234.5D * CelestialMath.TICKS_IN_DAY, 8));
        if (states.size() != CelestialBodies.values().length) {
            throw new AssertionError("not all orbiting bodies reached the visual-size audit");
        }
        double jupiter = 0.0D;
        double saturn = 0.0D;
        java.util.Set<Long> distinctSizes = new java.util.HashSet<>();
        for (var state : states) {
            CelestialBodies definition = CelestialBodies.valueOf(state.id().getPath().toUpperCase(java.util.Locale.ROOT));
            double radius = CelestialVisualRules.planetRenderRadius(state.angularRadiusRadians(),
                    definition.scaleFactor(), 1.0D);
            double expected = 2.0D * Math.tan(state.angularRadiusRadians()) * 206265.0D
                    * definition.scaleFactor();
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                throw new AssertionError(definition + " has invalid visual radius " + radius);
            }
            assertClose(expected, radius, definition + " diverged from TFCCaelum linear scale");
            distinctSizes.add(Math.round(radius * 100.0D));
            if (definition == CelestialBodies.JUPITER) jupiter = radius;
            if (definition == CelestialBodies.SATURN) saturn = radius;
        }
        if (jupiter <= saturn || distinctSizes.size() < 8) {
            throw new AssertionError("orbiting bodies lost visible size differences: Jupiter=" + jupiter
                    + ", Saturn=" + saturn + ", distinct=" + distinctSizes.size());
        }
    }

    private static void localApparentTimeUsesVanillaCelestialAngle() {
        float fallback = 0.314159F;
        assertClose(fallback, CelestialClientTime.vanillaCelestialAngle(Double.NaN, fallback),
                "non-finite apparent time fallback");
        assertClose(fallback, CelestialClientTime.vanillaCelestialAngle(Double.POSITIVE_INFINITY, fallback),
                "infinite apparent time fallback");

        double sunrise = CelestialClientTime.vanillaCelestialAngle(0.0D, fallback);
        double noon = CelestialClientTime.vanillaCelestialAngle(0.25D, fallback);
        double sunset = CelestialClientTime.vanillaCelestialAngle(0.5D, fallback);
        double midnight = CelestialClientTime.vanillaCelestialAngle(0.75D, fallback);
        assertClose(0.0D, noon, "noon celestial angle");
        assertClose(0.5D, midnight, "midnight celestial angle");
        assertClose(sunrise, CelestialClientTime.vanillaCelestialAngle(1.0D, fallback),
                "apparent-time wrap");
        if (Math.abs(vanillaSkyBrightness(sunrise) - vanillaSkyBrightness(sunset)) > 1.0E-6D) {
            throw new AssertionError("sunrise and sunset sky brightness were not symmetric");
        }
        assertClose(1.0D, vanillaSkyBrightness(noon), "noon sky brightness");
        assertClose(0.0D, vanillaSkyBrightness(midnight), "midnight sky brightness");

        for (int tick = 0; tick < 24000; tick++) {
            double apparent = tick / 24000.0D;
            float expected = referenceVanillaCelestialAngle(tick);
            float actual = CelestialClientTime.vanillaCelestialAngle(apparent, fallback);
            if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) {
                throw new AssertionError("visual time diverged from DimensionType.timeOfDay at tick " + tick
                        + ": expected " + expected + ", got " + actual);
            }
            assertClose(referenceVanillaStarAlpha(expected), CelestialVisualRules.vanillaStarAlpha(apparent),
                    "vanilla star alpha at tick " + tick);
        }

        // The regression: passing raw apparent midnight (0.75) directly to vanilla leaves the sky half bright.
        if (vanillaSkyBrightness(0.75D) < 0.49D) {
            throw new AssertionError("raw apparent-time regression fixture no longer exposes the unit mismatch");
        }
    }

    private static void solarEclipseVisualTimeDimsEverySharedConsumer() {
        double noon = 0.25D;
        double firstContact = CelestialClientTime.visualApparentDayTime(noon, 1.0E-6D);
        double threshold = CelestialClientTime.visualApparentDayTime(noon, 0.2D);
        double partial = CelestialClientTime.visualApparentDayTime(noon, 0.6D);
        double total = CelestialClientTime.visualApparentDayTime(noon, 1.0D);
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, 0.0D),
                "zero-eclipse visual time");
        assertClose(0.5D, partial, "partial-eclipse visual time");
        assertClose(noon, threshold, "twenty-percent eclipse darkening threshold");
        assertClose(0.75D, total, "total-eclipse visual midnight");
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, -1.0D),
                "negative eclipse coverage");
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, Double.NaN),
                "non-finite eclipse coverage");
        assertClose(0.75D, CelestialClientTime.visualApparentDayTime(noon, 2.0D),
                "clamped eclipse coverage");
        assertClose(0.875D, CelestialClientTime.visualApparentDayTime(0.0D, 0.6D),
                "sunrise eclipse follows the short path to midnight");
        if (Double.doubleToLongBits(firstContact) != Double.doubleToLongBits(noon)) {
            throw new AssertionError("sub-20-percent eclipse unexpectedly darkened the environment");
        }
        assertClose(0.0D, CelestialClientTime.eclipseVisualIntensity(0.2D),
                "eclipse visual intensity threshold");
        assertClose(0.5D, CelestialClientTime.eclipseVisualIntensity(0.6D),
                "eclipse visual intensity midpoint");
        assertClose(1.0D, CelestialClientTime.eclipseVisualIntensity(1.0D),
                "eclipse visual intensity totality");
        if (!Double.isNaN(CelestialClientTime.visualApparentDayTime(Double.NaN, 1.0D))) {
            throw new AssertionError("invalid authoritative apparent time did not remain invalid");
        }
        float fallback = 0.314159F;
        assertClose(fallback, CelestialClientTime.visualCelestialAngle(Double.NaN, 1.0D, fallback),
                "invalid eclipse visual angle fallback");
        assertClose(0.5D, CelestialClientTime.visualCelestialAngle(noon, 1.0D, fallback),
                "total-eclipse vanilla celestial angle");

        double baseStars = CelestialVisualRules.starVisibility(noon, 1.0D);
        double partialStars = CelestialVisualRules.starVisibility(partial, 1.0D);
        double totalStars = CelestialVisualRules.starVisibility(total, 1.0D);
        double baseMoon = CelestialVisualRules.moonVisibility(noon, 1.0D);
        double partialMoon = CelestialVisualRules.moonVisibility(partial, 1.0D);
        double totalMoon = CelestialVisualRules.moonVisibility(total, 1.0D);
        if (!(baseStars < partialStars && partialStars < totalStars)
                || !(baseMoon < partialMoon && partialMoon < totalMoon)
                || CelestialVisualRules.planetVisibility(partial, 1.0D) != partialStars
                || CelestialVisualRules.starShaderBrightness(partial, 1.0D, 2.0D) != partialStars * 2.0D) {
            throw new AssertionError("shared eclipse visual time no longer drives stars, planets and moon together");
        }

        DaylightState authoritative = new DaylightState(0.7D, true, noon, 1.0D);
        CelestialClientTime.visualApparentDayTime(authoritative.apparentDayTime(), 1.0D);
        assertClose(noon, authoritative.apparentDayTime(), "authoritative daylight remains unchanged");
        assertClose(0.7D, authoritative.solarElevationRadians(), "authoritative solar altitude remains unchanged");
        for (int step = 0; step <= 100; step++) {
            double coverage = step / 100.0D;
            double visual = CelestialClientTime.visualApparentDayTime(noon, coverage);
            assertUnitInterval(visual, "eclipse visual time");
            if (step > 0) {
                double previous = CelestialClientTime.visualApparentDayTime(noon, (step - 1) / 100.0D);
                if (CelestialVisualRules.starVisibility(visual, 1.0D) + EPSILON
                        < CelestialVisualRules.starVisibility(previous, 1.0D)) {
                    throw new AssertionError("eclipse darkening was not monotonic at step " + step);
                }
            }
        }
        for (int tick = 0; tick < 24000; tick++) {
            double apparent = tick / 24000.0D;
            double previous = CelestialVisualRules.starVisibility(apparent, 1.0D);
            for (int step = 1; step <= 20; step++) {
                double visual = CelestialClientTime.visualApparentDayTime(apparent, step / 20.0D);
                double current = CelestialVisualRules.starVisibility(visual, 1.0D);
                if (current + EPSILON < previous) {
                    throw new AssertionError("eclipse brightening reversed at day tick " + tick
                            + " and coverage step " + step + ": " + previous + " -> " + current);
                }
                previous = current;
            }
        }
    }

    private static void polarVisualLightingFollowsTheLocalSun() {
        int daysInMonth = 8;
        // TFE exposes 20,000 blocks as one pole-to-pole hemisphere scale: south pole is z=30,000.
        double southPoleZ = 30000.0D;
        double scale = 20000.0D;
        double yearDays = CelestialMath.daysInYear(daysInMonth);
        double summerMinSun = Double.POSITIVE_INFINITY;
        double summerMinLight = Double.POSITIVE_INFINITY;
        double winterMaxSun = Double.NEGATIVE_INFINITY;
        double winterMaxLight = Double.NEGATIVE_INFINITY;
        for (int step = 0; step < 240; step++) {
            double fraction = step / 240.0D;
            CelestialMath.Result summer = CelestialMath.calculate(new CelestialMath.Input(southPoleZ, scale,
                    fraction * CelestialMath.TICKS_IN_DAY, daysInMonth));
            CelestialMath.Result winter = CelestialMath.calculate(new CelestialMath.Input(southPoleZ, scale,
                    (yearDays * 0.5D + fraction) * CelestialMath.TICKS_IN_DAY, daysInMonth));
            summerMinSun = Math.min(summerMinSun, summer.solarElevation());
            summerMinLight = Math.min(summerMinLight, localSkyDarken(summer.apparentDayTime(), 0.0D, 0.0D));
            winterMaxSun = Math.max(winterMaxSun, winter.solarElevation());
            winterMaxLight = Math.max(winterMaxLight, localSkyDarken(winter.apparentDayTime(), 0.0D, 0.0D));
        }
        if (!(summerMinSun > 0.0D) || summerMinLight < 0.99D) {
            throw new AssertionError("polar day darkened while the Sun stayed above the horizon: sun="
                    + summerMinSun + ", light=" + summerMinLight);
        }
        if (!(winterMaxSun < 0.0D) || winterMaxLight > 0.01D) {
            throw new AssertionError("polar night brightened while the Sun stayed below the horizon: sun="
                    + winterMaxSun + ", light=" + winterMaxLight);
        }

        double dry = localSkyDarken(0.25D, 0.0D, 0.0D);
        double rain = localSkyDarken(0.25D, 1.0D, 0.0D);
        double thunder = localSkyDarken(0.25D, 1.0D, 1.0D);
        if (!(dry > rain && rain > thunder) || thunder < 0.0D || dry > 1.0D) {
            throw new AssertionError("local sunlight stopped preserving vanilla rain/thunder attenuation");
        }
    }

    /** Independent copy of ClientLevel.getSkyDarken after its getTimeOfDay result has been localized. */
    private static double localSkyDarken(double apparentDayTime, double rain, double thunder) {
        double angle = CelestialClientTime.vanillaCelestialAngle(apparentDayTime, Float.NaN);
        double light = 1.0D - Math.max(0.0D, Math.min(1.0D,
                1.0D - (Math.cos(angle * Math.PI * 2.0D) * 2.0D + 0.2D)));
        light *= 1.0D - Math.max(0.0D, Math.min(1.0D, rain)) * 5.0D / 16.0D;
        light *= 1.0D - Math.max(0.0D, Math.min(1.0D, thunder)) * 5.0D / 16.0D;
        return light;
    }

    private static double vanillaSkyBrightness(double celestialAngle) {
        return Math.max(0.0D, Math.min(1.0D,
                Math.cos(celestialAngle * Math.PI * 2.0D) * 2.0D + 0.5D));
    }

    /** Independent copy of the Minecraft 1.20.1 DimensionType.timeOfDay contract used as a test oracle. */
    private static float referenceVanillaCelestialAngle(long apparentDayTick) {
        double phase = positiveModulo(apparentDayTick / 24000.0D - 0.25D, 1.0D);
        double eased = 0.5D - Math.cos(phase * Math.PI) * 0.5D;
        return (float) (phase * 2.0D + eased) / 3.0F;
    }

    /** Independent copy of ClientLevel.getStarBrightness after getTimeOfDay has returned its angle. */
    private static double referenceVanillaStarAlpha(double celestialAngle) {
        double alpha = 1.0D - (Math.cos(celestialAngle * Math.PI * 2.0D) * 2.0D + 0.25D);
        alpha = Math.max(0.0D, Math.min(1.0D, alpha));
        return alpha * alpha * 0.5D;
    }

    private static void starAndMoonVisualScalesMatchTheirSources() {
        double catalogMin = -1.46D;
        double catalogMax = 7.96D;
        CelestialVisualRules.StarAppearance brightest = CelestialVisualRules.starAppearance(
                catalogMin, catalogMin, catalogMax, 1.0D);
        CelestialVisualRules.StarAppearance middle = CelestialVisualRules.starAppearance(
                3.0D, catalogMin, catalogMax, 1.0D);
        CelestialVisualRules.StarAppearance faintest = CelestialVisualRules.starAppearance(
                catalogMax, catalogMin, catalogMax, 1.0D);
        assertClose(0.6D, brightest.radius(), "bright Caelum star radius");
        assertClose(1.0D, brightest.alpha(), "bright Caelum star alpha");
        assertClose(0.1D, faintest.radius(), "faint Caelum star radius");
        assertClose(0.0D, faintest.alpha(), "faint Caelum star alpha");
        if (!(brightest.radius() > middle.radius() && middle.radius() > faintest.radius())
                || !(brightest.alpha() > middle.alpha() && middle.alpha() > faintest.alpha())
                || !Double.isFinite(middle.radius()) || !Double.isFinite(middle.alpha())) {
            throw new AssertionError("logarithmic stellar appearance is not finite and monotonic");
        }
        assertClose(1.2D, CelestialVisualRules.starAppearance(catalogMin, catalogMin, catalogMax, 2.0D).radius(),
                "configured Caelum star radius");
        assertClose(0.0D, CelestialVisualRules.vanillaStarAlpha(0.25D),
                "vanilla noon star alpha");
        double horizonStarAlpha = CelestialVisualRules.vanillaStarAlpha(0.0D);
        double sunsetStarAlpha = CelestialVisualRules.vanillaStarAlpha(0.5D);
        if (Math.abs(horizonStarAlpha - sunsetStarAlpha) > 1.0E-6D
                || !(horizonStarAlpha > 0.05D && horizonStarAlpha < 0.052D)
                || !(sunsetStarAlpha > 0.05D && sunsetStarAlpha < 0.052D)) {
            throw new AssertionError("vanilla horizon star alpha changed: sunrise=" + horizonStarAlpha
                    + ", sunset=" + sunsetStarAlpha);
        }
        assertClose(0.5D, CelestialVisualRules.vanillaStarAlpha(0.75D),
                "vanilla midnight star alpha");
        assertClose(1.0D, CelestialVisualRules.starShaderBrightness(0.75D, 1.0D, 2.0D),
                "configured Caelum midnight shader brightness");
        assertClose(0.5D, CelestialVisualRules.starShaderBrightness(0.75D, 0.5D, 2.0D),
                "weather-dimmed Caelum shader brightness");
        assertClose(0.0D, CelestialVisualRules.starShaderBrightness(0.25D, 1.0D, 2.0D),
                "daytime Caelum shader brightness");
        assertClose(0.2D, CelestialVisualRules.moonVisibility(0.25D, 1.0D),
                "TFC daytime moon visibility");
        assertClose(0.6D, CelestialVisualRules.moonVisibility(0.75D, 1.0D),
                "TFC nighttime moon visibility");
        assertClose(0.3D, CelestialVisualRules.moonVisibility(0.75D, 0.5D),
                "weather-dimmed moon visibility");
        if (!CelestialVisualRules.starAppearance(Double.NaN, catalogMin, catalogMax, 1.0D)
                .equals(new CelestialVisualRules.StarAppearance(0.0D, 0.0D))
                || CelestialVisualRules.starShaderBrightness(0.75D, 1.0D, -1.0D) != 0.0D
                || CelestialVisualRules.moonVisibility(Double.NaN, 1.0D) != 0.0D) {
            throw new AssertionError("invalid stellar visual inputs were not rejected");
        }
    }

    private static void localVisualSceneMatrixIsFiniteAndComplete() {
        boolean sawSun = false;
        boolean sawMoon = false;
        boolean sawStars = false;
        boolean sawTwilight = false;
        boolean sawPlanet = false;
        for (int daysInMonth : new int[]{4, 8, 16}) {
            double yearDays = CelestialMath.daysInYear(daysInMonth);
            for (double z : new double[]{-10000.0D, -5000.0D, 10000.0D, 25000.0D, 30000.0D}) {
                for (int season = 0; season < 4; season++) {
                    for (double dayFraction : new double[]{0.0D, 0.25D, 0.5D, 0.75D}) {
                        double day = season * yearDays / 4.0D + dayFraction;
                        CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(z,
                                20000.0D, day * CelestialMath.TICKS_IN_DAY, daysInMonth));
                        double stars = CelestialVisualRules.starVisibility(frame.apparentDayTime(), 1.0D);
                        double twilight = CelestialVisualRules.twilightAlpha(frame.solarElevation(), 1.0D);
                        assertUnitInterval(stars, "scene star visibility");
                        assertUnitInterval(twilight, "scene twilight alpha");
                        sawSun |= CelestialVisualRules.celestialDiscRenderable(frame.sunDirection());
                        sawMoon |= CelestialVisualRules.celestialDiscRenderable(frame.moonDirection());
                        sawStars |= stars > 0.4D;
                        sawTwilight |= twilight > 0.0D;
                        var bodies = CelestialBodies.calculate(frame,
                                CelestialMath.calendarYears(day * CelestialMath.TICKS_IN_DAY, daysInMonth));
                        if (bodies.size() != 17) {
                            throw new AssertionError("client scene lost a TFCCaelum body");
                        }
                        for (var body : bodies) {
                            double visibility = CelestialVisualRules.planetVisibility(
                                    frame.apparentDayTime(), 1.0D);
                            assertUnitInterval(visibility, "scene planet visibility");
                            sawPlanet |= visibility > 0.0D;
                        }
                    }
                }
            }
        }
        if (!sawSun || !sawMoon || !sawStars || !sawTwilight || !sawPlanet) {
            throw new AssertionError("client latitude/season scene matrix missed a render stage");
        }

        double yearDays = CelestialMath.daysInYear(8);
        double solstice = Math.floor(positiveModulo(0.25D - 284.0D / 365.0D, 1.0D) * yearDays);
        CelestialMath.Result polarEvening = CelestialMath.calculate(new CelestialMath.Input(-10000.0D,
                20000.0D, (solstice + 0.75D) * CelestialMath.TICKS_IN_DAY, 8));
        if (!(polarEvening.fractionOfDay() > 9000.0D / 24000.0D)
                || !(polarEvening.apparentDayTime() <= 9000.0D / 24000.0D)
                || !CelestialVisualRules.startsRainbow(1.0F, 0.0F, -0.1F, polarEvening.apparentDayTime(),
                polarEvening.solarElevation())
                || CelestialVisualRules.startsRainbow(1.0F, 0.0F, -0.1F, polarEvening.fractionOfDay(),
                polarEvening.solarElevation())) {
            throw new AssertionError("rainbow eligibility did not follow local apparent solar time");
        }

        boolean sawSolarEclipse = false;
        boolean sawLunarEclipse = false;
        double nodalDays = CelestialMath.daysInYear(8) * CelestialMath.NODAL_YEARS;
        for (double day = 0.0D; day < nodalDays && !(sawSolarEclipse && sawLunarEclipse); day += 0.02D) {
            CelestialMath.Result frame = CelestialMath.calculate(new CelestialMath.Input(10000.0D,
                    20000.0D, day * CelestialMath.TICKS_IN_DAY, 8));
            sawSolarEclipse |= frame.solarEclipse() > 0.01D
                    && CelestialVisualRules.celestialDiscRenderable(frame.sunDirection());
            sawLunarEclipse |= frame.lunarEclipse() > 0.01D
                    && CelestialVisualRules.celestialDiscRenderable(frame.moonDirection());
        }
        if (!sawSolarEclipse || !sawLunarEclipse) {
            throw new AssertionError("client scene sweep did not include visible solar and lunar eclipses");
        }
    }

    private static void planetariumProjectionAndEclipsePlotAreExact() {
        PlanetariumProjection.Point zenith = PlanetariumProjection.project(
                new CelestialVector(0.0D, 1.0D, 0.0D), 100.0D, 80.0D, 60.0D);
        PlanetariumProjection.Point north = PlanetariumProjection.project(
                new CelestialVector(0.0D, 0.0D, 1.0D), 100.0D, 80.0D, 60.0D);
        PlanetariumProjection.Point east = PlanetariumProjection.project(
                new CelestialVector(1.0D, 0.0D, 0.0D), 100.0D, 80.0D, 60.0D);
        PlanetariumProjection.Point below = PlanetariumProjection.project(
                new CelestialVector(0.0D, -0.01D, 1.0D), 100.0D, 80.0D, 60.0D);
        if (!zenith.visible() || !north.visible() || !east.visible() || below.visible()) {
            throw new AssertionError("planetarium horizon visibility changed");
        }
        assertClose(100.0D, zenith.x(), "planetarium zenith x");
        assertClose(80.0D, zenith.y(), "planetarium zenith y");
        assertClose(100.0D, north.x(), "planetarium north x");
        assertClose(20.0D, north.y(), "planetarium north y");
        assertClose(160.0D, east.x(), "planetarium east x");
        assertClose(80.0D, east.y(), "planetarium east y");

        CelestialState state = planetariumState(23.375D);
        for (StarTableLoader.Star star : new StarTableLoader.Star[]{
                new StarTableLoader.Star("equator", 0.0D, 0.0D, 1.0D, 0xFFFFFF),
                new StarTableLoader.Star("north", 1.25D, Math.toRadians(89.0D), 2.0D, 0xAABBCC)}) {
            CelestialVector direction = PlanetariumProjection.starDirection(star, state);
            PlanetariumProjection.Point point = PlanetariumProjection.project(direction,
                    100.0D, 80.0D, 60.0D);
            if (!Double.isFinite(direction.x()) || !Double.isFinite(direction.y())
                    || !Double.isFinite(direction.z()) || (point.visible()
                    && (!Double.isFinite(point.x()) || !Double.isFinite(point.y())))) {
                throw new AssertionError("planetarium star projection became non-finite: " + star);
            }
        }

        first.wildfires.celestial.EclipsePredictionService.SolarPrediction strong = null;
        double searchTick = 0.0D;
        for (int index = 0; index < 64; index++) {
            var candidate = first.wildfires.celestial.EclipsePredictionService.predict(searchTick,
                    8, 20_000.0D, 10_000.0D,
                    first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT).solar();
            if (candidate.present() && candidate.globalMaximumCoverage() >= 0.8D) {
                strong = candidate;
                break;
            }
            searchTick = candidate.present() ? candidate.endCalendarTicks() + 1.0D
                    : searchTick + 32.0D * CelestialMath.TICKS_IN_DAY;
        }
        if (strong == null) {
            throw new AssertionError("planetarium test could not find a strong regional eclipse");
        }
        double greatestProgress = (strong.greatestCalendarTicks() - strong.startCalendarTicks())
                / (strong.endCalendarTicks() - strong.startCalendarTicks());
        double partialWidth = PlanetariumProjection.eclipseLatitudeHalfWidth(
                strong, greatestProgress, 0.0D);
        double penumbraWidth = PlanetariumProjection.eclipseLatitudeHalfWidth(
                strong, greatestProgress, 0.5D);
        double umbraWidth = PlanetariumProjection.eclipseLatitudeHalfWidth(
                strong, greatestProgress, 0.8D);
        if (!(partialWidth > penumbraWidth && penumbraWidth > umbraWidth && umbraWidth > 0.0D)
                || Math.abs(strong.startTrackLatitudeRadians()
                - strong.endTrackLatitudeRadians()) < 1.0E-6D) {
            throw new AssertionError("planetarium eclipse plot lost exact thresholds or track tilt: "
                    + partialWidth + ", " + penumbraWidth + ", " + umbraWidth);
        }
        double centerCoverage = PlanetariumProjection.maximumGlobalSolarCoverage(
                strong, 0.0D);
        double expectedCenterCoverage = first.wildfires.celestial.SolarEclipseRegion
                .maximumCoverageAtLatitude(strong.event(), 0.0D, strong.sunHalfTangent(),
                        strong.moonHalfTangent(), strong.synodicDays());
        if (Math.abs(centerCoverage - expectedCenterCoverage) > 1.0E-12D) {
            throw new AssertionError("planetarium global plot diverged from geometric coverage: "
                    + centerCoverage + " vs " + expectedCenterCoverage);
        }
    }

    private static void planetariumPixelAssetsClockAndTimelineAreComplete() {
        int[] expectedBands = {-1, 0, 0, 1, 1, 2, 3, 4, 4};
        double[] coverages = {0.0D, 0.0001D, 0.1999D, 0.2D, 0.3999D,
                0.4D, 0.6D, 0.8D, 1.0D};
        for (int index = 0; index < coverages.length; index++) {
            int actual = PlanetariumProjection.eclipseCoverageBand(coverages[index]);
            if (actual != expectedBands[index]) {
                throw new AssertionError("five-level eclipse map band changed at "
                        + coverages[index] + ": " + actual);
            }
        }

        PlanetariumClock.Schedule equator = PlanetariumClock.schedule(0.0D, 0.123D);
        assertClose(0.25D, equator.sunriseFraction(), "equatorial sunrise on clock");
        assertClose(0.75D, equator.sunsetFraction(), "equatorial sunset on clock");
        assertClose(0.5D, equator.dayFraction(), "equatorial clock day fraction");
        double northernSummer = positiveModulo(0.25D - 284.0D / 365.0D, 1.0D);
        PlanetariumClock.Schedule polarDay = PlanetariumClock.schedule(
                Math.toRadians(89.0D), northernSummer);
        PlanetariumClock.Schedule polarNight = PlanetariumClock.schedule(
                Math.toRadians(89.0D), positiveModulo(northernSummer + 0.5D, 1.0D));
        if (!polarDay.polarDay() || polarDay.polarNight() || polarDay.dayFraction() != 1.0D
                || polarNight.polarDay() || !polarNight.polarNight()
                || polarNight.dayFraction() != 0.0D) {
            throw new AssertionError("planetarium clock lost polar-day/polar-night sectors");
        }
        assertClose(0.75D, PlanetariumClock.pointerFraction(-0.25D),
                "clock pointer negative wrap");
        assertClose(0.25D, PlanetariumClock.pointerFraction(1.25D),
                "clock pointer positive wrap");
        if (PlanetariumProjection.POINTER_TEXTURE_SIZE != 128
                || PlanetariumProjection.TIMELINE_ICON_SIZE != 8
                || PlanetariumProjection.TIMELINE_DISC_SOURCE_SIZE != 8
                || PlanetariumProjection.TIMELINE_DISC_SOURCE_U != 12
                || PlanetariumProjection.TIMELINE_DISC_SOURCE_V != 12
                || PlanetariumProjection.TIMELINE_NEW_MOON_SOURCE_V != 44
                || PlanetariumProjection.TIMELINE_POINTER_LENGTH != 16
                || PlanetariumProjection.TIMELINE_POINTER_WIDTH != 1
                || PlanetariumProjection.TIMELINE_POINTER_COLOR != 0xFF2D8FB8
                || PlanetariumProjection.TIMELINE_LABEL_Y != 97
                || !PlanetariumProjection.TIMELINE_SUN_TEXTURE
                .equals("minecraft:textures/environment/sun.png")
                || !PlanetariumProjection.TIMELINE_FULL_MOON_TEXTURE
                .equals("minecraft:textures/environment/moon_phases.png")) {
            throw new AssertionError("planetarium pointer pivot or vanilla timeline icon layout changed");
        }
        var blueMoon = new first.wildfires.celestial.EclipsePredictionService.LunarPrediction(
                true, 0L, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D,
                first.wildfires.celestial.EclipsePredictionService.LunarEclipseKind.NONE,
                false, 1.0D, 0.0D, 0.0D, 0.0D);
        var paleEclipse = new first.wildfires.celestial.EclipsePredictionService.LunarPrediction(
                true, 0L, 0.0D, 0.0D, 1.0D, 0.8D, 0.8D,
                first.wildfires.celestial.EclipsePredictionService.LunarEclipseKind.PARTIAL,
                true, 0.0D, 0.0D, 0.0D, 1.0D);
        var bloodMoon = new first.wildfires.celestial.EclipsePredictionService.LunarPrediction(
                true, 0L, 0.0D, 0.0D, 1.0D, Math.nextUp(0.8D), 1.0D,
                first.wildfires.celestial.EclipsePredictionService.LunarEclipseKind.PARTIAL,
                true, 0.0D, 0.0D, 0.0D, 1.0D);
        var dualEventMoon = new first.wildfires.celestial.EclipsePredictionService.LunarPrediction(
                true, 0L, 0.0D, 0.0D, 1.0D, Math.nextUp(0.8D), 1.0D,
                first.wildfires.celestial.EclipsePredictionService.LunarEclipseKind.PARTIAL,
                true, 1.0D, 0.0D, 0.0D, 1.0D);
        PlanetariumProjection.TimelineMoonTint blueTint =
                PlanetariumProjection.timelineMoonTint(blueMoon,
                        PlanetariumProjection.TimelineLunarMarkerKind.SUPERMOON);
        PlanetariumProjection.TimelineMoonTint paleTint =
                PlanetariumProjection.timelineMoonTint(paleEclipse,
                        PlanetariumProjection.TimelineLunarMarkerKind.ECLIPSE);
        PlanetariumProjection.TimelineMoonTint bloodTint =
                PlanetariumProjection.timelineMoonTint(bloodMoon,
                        PlanetariumProjection.TimelineLunarMarkerKind.ECLIPSE);
        if (!(blueTint.blue() > blueTint.green() && blueTint.green() > blueTint.red())
                || !(paleTint.red() > paleTint.green() && paleTint.green() > paleTint.blue())
                || !(bloodTint.red() > bloodTint.green() && bloodTint.green() > bloodTint.blue())
                || !(bloodTint.green() < paleTint.green())
                || !PlanetariumProjection.timelineLunarMarkerKinds(dualEventMoon).equals(
                java.util.List.of(PlanetariumProjection.TimelineLunarMarkerKind.ECLIPSE,
                        PlanetariumProjection.TimelineLunarMarkerKind.SUPERMOON))
                || !PlanetariumProjection.timelineLunarMarkerKinds(blueMoon).equals(
                java.util.List.of(PlanetariumProjection.TimelineLunarMarkerKind.SUPERMOON))) {
            throw new AssertionError("timeline Moon event tint priority changed");
        }
        var expandedMarkers = PlanetariumProjection.timelineLunarMarkers(
                java.util.List.of(dualEventMoon, blueMoon));
        if (expandedMarkers.size() != 3
                || expandedMarkers.get(0).kind()
                != PlanetariumProjection.TimelineLunarMarkerKind.ECLIPSE
                || expandedMarkers.get(1).kind()
                != PlanetariumProjection.TimelineLunarMarkerKind.SUPERMOON
                || expandedMarkers.get(2).kind()
                != PlanetariumProjection.TimelineLunarMarkerKind.SUPERMOON) {
            throw new AssertionError("simultaneous lunar events were not independently expanded: "
                    + expandedMarkers);
        }
        var oneDayLayout = PlanetariumProjection.timelineDayLayouts(java.util.List.of(
                        new PlanetariumProjection.TimelineDaySeed(12L, 92, 0, 3)),
                PlanetariumProjection.TIMELINE_ICON_SIZE,
                PlanetariumProjection.TIMELINE_TRACK_GAP, 0, 200);
        if (oneDayLayout.size() != 1 || oneDayLayout.get(0).pointerX() != 92
                || !oneDayLayout.get(0).upperLefts().isEmpty()
                || !oneDayLayout.get(0).lowerLefts().equals(java.util.List.of(78, 88, 98))) {
            throw new AssertionError("same-day lunar events did not share one centered pointer: "
                    + oneDayLayout);
        }
        var mixedDayLayout = PlanetariumProjection.timelineDayLayouts(java.util.List.of(
                        new PlanetariumProjection.TimelineDaySeed(13L, 120, 1, 2)),
                PlanetariumProjection.TIMELINE_ICON_SIZE,
                PlanetariumProjection.TIMELINE_TRACK_GAP, 0, 200);
        if (mixedDayLayout.size() != 1 || mixedDayLayout.get(0).pointerX() != 120
                || !mixedDayLayout.get(0).upperLefts().equals(java.util.List.of(116))
                || !mixedDayLayout.get(0).lowerLefts().equals(java.util.List.of(111, 121))) {
            throw new AssertionError("same-day solar/lunar events lost their single date pointer: "
                    + mixedDayLayout);
        }
        var axisPhase = PlanetariumProjection.timelineAxisMarker(92, 72,
                PlanetariumProjection.TIMELINE_ICON_SIZE);
        if (axisPhase.left() != 88 || axisPhase.top() != 68
                || axisPhase.left() + PlanetariumProjection.TIMELINE_ICON_SIZE / 2 != 92
                || axisPhase.top() + PlanetariumProjection.TIMELINE_ICON_SIZE / 2 != 72) {
            throw new AssertionError("ordinary phase marker is not centered directly on the axis: "
                    + axisPhase);
        }

        double period = 80_000.0D;
        assertClose(0.0D, first.wildfires.celestial.EclipsePredictionService
                .displayLongitude(0.0D, 20_000.0D), "display longitude origin");
        assertClose(Math.PI * 0.5D, first.wildfires.celestial.EclipsePredictionService
                .displayLongitude(period * 0.25D, 20_000.0D), "display longitude quarter-period");
        assertClose(first.wildfires.celestial.EclipsePredictionService
                        .displayLongitude(12_345.0D, 20_000.0D),
                first.wildfires.celestial.EclipsePredictionService
                        .displayLongitude(12_345.0D + period, 20_000.0D),
                "display longitude full-period repeat");

        var mapCenter = PlanetariumProjection.mapSelection(332.0D, 334.0D,
                100, 200, 464, 268);
        if (!mapCenter.present()) {
            throw new AssertionError("planetarium map center click did not create a cursor");
        }
        assertClose(0.0D, mapCenter.longitudeRadians(), "map cursor center longitude");
        assertClose(0.0D, mapCenter.latitudeRadians(), "map cursor center latitude");
        var mapNorthWest = PlanetariumProjection.mapSelection(100.0D, 200.0D,
                100, 200, 464, 268);
        var mapSouthEast = PlanetariumProjection.mapSelection(564.0D, 468.0D,
                100, 200, 464, 268);
        assertClose(-Math.PI, mapNorthWest.longitudeRadians(), "map cursor west edge");
        assertClose(Math.PI * 0.5D, mapNorthWest.latitudeRadians(), "map cursor north edge");
        assertClose(Math.PI, mapSouthEast.longitudeRadians(), "map cursor east edge");
        assertClose(-Math.PI * 0.5D, mapSouthEast.latitudeRadians(), "map cursor south edge");
        if (PlanetariumProjection.mapSelection(99.999D, 200.0D,
                100, 200, 464, 268).present()
                || PlanetariumProjection.mapX(mapCenter.longitudeRadians(), 100, 464) != 332
                || PlanetariumProjection.mapY(mapCenter.latitudeRadians(), 200, 268) != 334
                || !PlanetariumProjection.crosshairContains(mapCenter, 338.0D, 334.0D,
                100, 200, 464, 268, 7)
                || PlanetariumProjection.crosshairContains(mapCenter, 340.0D, 334.0D,
                100, 200, 464, 268, 7)) {
            throw new AssertionError("single map cursor bounds, inverse projection or hover changed");
        }
        var dragState = PlanetariumProjection.MapDragState.NONE.begin(332.0D, 334.0D,
                100, 200, 464, 268);
        if (!dragState.dragging() || !dragState.selection().present()) {
            throw new AssertionError("map press did not begin a drag inside the chart");
        }
        dragState = dragState.drag(900.0D, 50.0D, 100, 200, 464, 268);
        assertClose(Math.PI, dragState.selection().longitudeRadians(),
                "map drag clamps at east edge");
        assertClose(Math.PI * 0.5D, dragState.selection().latitudeRadians(),
                "map drag clamps at north edge");
        dragState = dragState.drag(-500.0D, 900.0D, 100, 200, 464, 268);
        assertClose(-Math.PI, dragState.selection().longitudeRadians(),
                "map drag clamps at west edge");
        assertClose(-Math.PI * 0.5D, dragState.selection().latitudeRadians(),
                "map drag clamps at south edge");
        dragState = dragState.release();
        var releasedSelection = dragState.selection();
        dragState = dragState.drag(332.0D, 334.0D, 100, 200, 464, 268);
        if (dragState.dragging() || !dragState.selection().equals(releasedSelection)) {
            throw new AssertionError("released map cursor continued moving");
        }
        var outsidePress = dragState.begin(99.0D, 199.0D, 100, 200, 464, 268);
        if (outsidePress.dragging() || !outsidePress.selection().equals(releasedSelection)
                || PlanetariumProjection.clampedMapSelection(Double.NaN, 334.0D,
                100, 200, 464, 268).present()) {
            throw new AssertionError("outside/non-finite map input started or corrupted a drag");
        }
        if (PlanetariumProjection.MAP_PLAYER_CURSOR_RADIUS >=
                PlanetariumProjection.MAP_SELECTION_CURSOR_RADIUS
                || PlanetariumProjection.MAP_PLAYER_CURSOR_COLOR
                == PlanetariumProjection.MAP_SELECTION_COLOR) {
            throw new AssertionError("player and movable map cursors are no longer visually distinct");
        }

        var apiBloodMoon = new first.wildfires.api.celestial.CelestialEventState(
                true, false, true, true, true, true, false, false,
                0.3D, true, 0.9D, 1.0D, true, 1.0D, true, 0.9D, true);
        var apiBloodTypes = PlanetariumProjection.currentEventTypes(apiBloodMoon);
        if (!apiBloodTypes.equals(java.util.List.of(
                first.wildfires.celestial.CelestialEventType.SOLAR_ECLIPSE,
                first.wildfires.celestial.CelestialEventType.NEW_MOON,
                first.wildfires.celestial.CelestialEventType.FULL_MOON,
                first.wildfires.celestial.CelestialEventType.BLOOD_MOON,
                first.wildfires.celestial.CelestialEventType.SUPERMOON))
                || apiBloodTypes.contains(
                first.wildfires.celestial.CelestialEventType.LUNAR_ECLIPSE)) {
            throw new AssertionError("planetarium current-event rows no longer consume API flags "
                    + "or blood moon duplicated the lunar-eclipse row: " + apiBloodTypes);
        }
        var apiLunar = new first.wildfires.api.celestial.CelestialEventState(
                false, true, false, true, false, false, false, false,
                0.0D, false, 0.4D, 0.8D, true, 0.0D, false, 0.4D, false);
        if (!PlanetariumProjection.currentEventTypes(apiLunar).equals(java.util.List.of(
                first.wildfires.celestial.CelestialEventType.LUNAR_ECLIPSE))
                || !PlanetariumProjection.currentEventTypes(null).isEmpty()) {
            throw new AssertionError("planetarium API lunar-event row mapping changed");
        }

        var timeline = first.wildfires.celestial.EclipsePredictionService.predictTimeline(
                0.0D, 8, 20_000.0D, 12_345.0D, 10_000.0D,
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT, 400.0D);
        assertClose(400.0D * CelestialMath.TICKS_IN_DAY,
                timeline.endCalendarTicks() - timeline.startCalendarTicks(),
                "planetarium 400-day horizon");
        if (timeline.solar().isEmpty() || timeline.lunar().isEmpty()) {
            throw new AssertionError("400-day planetarium timeline did not contain both eclipse types");
        }
        if (timeline.lunar().stream().noneMatch(
                first.wildfires.celestial.EclipsePredictionService.LunarPrediction::supermoon)) {
            throw new AssertionError("400-day planetarium timeline omitted full-Moon perigee events");
        }
        if (timeline.phases().stream().noneMatch(phase -> phase.kind()
                == first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind.FULL_MOON)
                || timeline.phases().stream().noneMatch(phase -> phase.kind()
                == first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind.NEW_MOON)) {
            throw new AssertionError("400-day planetarium timeline omitted global full/new moons");
        }
        for (var phase : timeline.phases()) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    10_000.0D, 20_000.0D, phase.calendarTicks(), 8));
            boolean valid = phase.kind()
                    == first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind.FULL_MOON
                    ? result.illuminatedFraction() >= 0.995D
                    : result.illuminatedFraction() <= 0.005D;
            if (!valid) {
                throw new AssertionError("global phase marker violates geometric qualification: "
                        + phase + "; " + result);
            }
            var expected = phase.kind()
                    == first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind.FULL_MOON
                    ? first.wildfires.celestial.CelestialEventType.FULL_MOON
                    : first.wildfires.celestial.CelestialEventType.NEW_MOON;
            boolean locallyVisible = expected.matches(result, (long) phase.calendarTicks(), null);
            var current = first.wildfires.celestial.EclipsePredictionService.currentEvents(
                    phase.calendarTicks(), 8, 20_000.0D, 10_000.0D,
                    first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT);
            var active = current.events().stream().filter(event -> event.type() == expected)
                    .findFirst();
            if (locallyVisible != active.isPresent()) {
                throw new AssertionError("global prediction/local current-event qualification diverged: "
                        + phase + "; local=" + locallyVisible + "; current=" + current);
            }
            if (active.isPresent() && active.get().endCalendarTicks() <= Math.floor(phase.calendarTicks())) {
                throw new AssertionError("current phase event did not expose a future end time: "
                        + active.get());
            }
            if (active.isPresent()) {
                var ended = first.wildfires.celestial.EclipsePredictionService.currentEvents(
                        active.get().endCalendarTicks(), 8, 20_000.0D, 10_000.0D,
                        first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT);
                if (ended.events().stream().anyMatch(event -> event.type() == expected)) {
                    throw new AssertionError("current phase event remained active at its end tick: "
                            + active.get());
                }
            }
        }
        double northPoleZ = first.wildfires.celestial.EclipsePredictionService.zForLatitude(
                Math.PI * 0.5D, 20_000.0D);
        double southPoleZ = first.wildfires.celestial.EclipsePredictionService.zForLatitude(
                -Math.PI * 0.5D, 20_000.0D);
        var northPoleTimeline = first.wildfires.celestial.EclipsePredictionService.predictTimeline(
                0.0D, 8, 20_000.0D, 12_345.0D, northPoleZ,
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT, 400.0D);
        var southPoleTimeline = first.wildfires.celestial.EclipsePredictionService.predictTimeline(
                0.0D, 8, 20_000.0D, 12_345.0D, southPoleZ,
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT, 400.0D);
        boolean sawGlobalOnlyPhase = java.util.stream.Stream.concat(
                        northPoleTimeline.phases().stream().map(phase -> java.util.Map.entry(northPoleZ, phase)),
                        southPoleTimeline.phases().stream().map(phase -> java.util.Map.entry(southPoleZ, phase)))
                .anyMatch(entry -> {
                    var phase = entry.getValue();
                    CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                            entry.getKey(), 20_000.0D, phase.calendarTicks(), 8));
                    var expected = phase.kind()
                            == first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind.FULL_MOON
                            ? first.wildfires.celestial.CelestialEventType.FULL_MOON
                            : first.wildfires.celestial.CelestialEventType.NEW_MOON;
                    return !expected.matches(result, (long) phase.calendarTicks(), null);
                });
        if (!sawGlobalOnlyPhase) {
            throw new AssertionError("polar full/new Moon forecasts were still restricted to local visibility");
        }
        double liveLatitude = Math.toRadians(-46.0D);
        double liveZ = first.wildfires.celestial.EclipsePredictionService.zForLatitude(
                liveLatitude, 20_000.0D);
        var liveTimeline = first.wildfires.celestial.EclipsePredictionService.predictTimeline(
                652_097_136.0D, 8, 20_000.0D, 0.0D, liveZ,
                first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT, 400.0D);
        var globalOnlySolar = liveTimeline.solar().stream()
                .filter(solar -> solar.observerMaximumCoverage() == 0.0D)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "live regression horizon contained no globally real but locally invisible solar eclipse"));
        if (!(PlanetariumProjection.maximumGlobalSolarCoverage(globalOnlySolar,
                globalOnlySolar.greatestTrackLatitudeRadians()) > 0.0D)) {
            throw new AssertionError("global-only solar eclipse still produced a blank paper band: "
                    + globalOnlySolar);
        }
        boolean sawGlobalOnlyLunar = java.util.stream.Stream.concat(
                        northPoleTimeline.lunar().stream().map(lunar -> java.util.Map.entry(northPoleZ, lunar)),
                        southPoleTimeline.lunar().stream().map(lunar -> java.util.Map.entry(southPoleZ, lunar)))
                .filter(entry -> entry.getValue().eclipse())
                .anyMatch(entry -> {
                    var lunar = entry.getValue();
                    CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                            entry.getKey(), 20_000.0D, lunar.greatestCalendarTicks(), 8));
                    return !(result.moonElevation() > 0.0D && result.solarElevation() <= 0.0D);
                });
        if (!sawGlobalOnlyLunar) {
            throw new AssertionError("polar lunar eclipse forecast was still restricted to local night");
        }
        var supermoonPrediction = timeline.lunar().stream()
                .filter(first.wildfires.celestial.EclipsePredictionService.LunarPrediction::supermoon)
                .findFirst().orElseThrow();
        var currentSupermoon = currentEventsAtRepresentableTick(
                first.wildfires.celestial.CelestialEventType.SUPERMOON,
                supermoonPrediction.startCalendarTicks(),
                supermoonPrediction.greatestCalendarTicks(),
                supermoonPrediction.endCalendarTicks(), 10_000.0D);
        if (currentSupermoon.events().stream().noneMatch(event -> event.type()
                == first.wildfires.celestial.CelestialEventType.SUPERMOON)
                || currentSupermoon.events().stream().noneMatch(event -> event.type()
                == first.wildfires.celestial.CelestialEventType.FULL_MOON)) {
            throw new AssertionError("simultaneous full/supermoon API rows were not preserved: "
                    + currentSupermoon);
        }
        java.util.TreeMap<Long, int[]> exceptionalCountsByDay = new java.util.TreeMap<>();
        timeline.solar().forEach(solar -> exceptionalCountsByDay.computeIfAbsent(
                (long) Math.floor(solar.greatestCalendarTicks() / CelestialMath.TICKS_IN_DAY),
                ignored -> new int[2])[0]++);
        timeline.lunar().forEach(lunar -> exceptionalCountsByDay.computeIfAbsent(
                (long) Math.floor(lunar.greatestCalendarTicks() / CelestialMath.TICKS_IN_DAY),
                ignored -> new int[2])[1] +=
                PlanetariumProjection.timelineLunarMarkerKinds(lunar).size());
        var defaultDaySeeds = exceptionalCountsByDay.entrySet().stream().map(entry ->
                new PlanetariumProjection.TimelineDaySeed(entry.getKey(),
                        (int) Math.round(632.0D * PlanetariumProjection.timelineProgress(timeline,
                                (entry.getKey() + 0.5D) * CelestialMath.TICKS_IN_DAY)),
                        entry.getValue()[0], entry.getValue()[1])).toList();
        var defaultDayLayouts = PlanetariumProjection.timelineDayLayouts(defaultDaySeeds,
                PlanetariumProjection.TIMELINE_ICON_SIZE,
                PlanetariumProjection.TIMELINE_TRACK_GAP, -4, 636);
        if (defaultDayLayouts.size() != exceptionalCountsByDay.size()) {
            throw new AssertionError("timeline no longer emits exactly one pointer per exceptional-event day");
        }
        for (int index = 0; index < defaultDayLayouts.size(); index++) {
            var layout = defaultDayLayouts.get(index);
            int[] counts = exceptionalCountsByDay.get(layout.day());
            if (counts == null || layout.upperLefts().size() != counts[0]
                    || layout.lowerLefts().size() != counts[1]) {
                throw new AssertionError("timeline day group lost an event icon: " + layout);
            }
            assertSeparatedTimelineRow(layout.upperLefts(), "upper day " + layout.day());
            assertSeparatedTimelineRow(layout.lowerLefts(), "lower day " + layout.day());
            if (index > 0) {
                var previousLayout = defaultDayLayouts.get(index - 1);
                int previousRight = Math.max(rowRight(previousLayout.upperLefts()),
                        rowRight(previousLayout.lowerLefts()));
                int currentLeft = Math.min(rowLeft(layout.upperLefts()),
                        rowLeft(layout.lowerLefts()));
                if (previousRight + PlanetariumProjection.TIMELINE_TRACK_GAP > currentLeft) {
                    throw new AssertionError("adjacent timeline day groups overlap: "
                            + previousLayout + " / " + layout);
                }
            }
        }
        boolean ordinaryOnlyPhase = false;
        for (var phase : timeline.phases()) {
            long phaseDay = (long) Math.floor(phase.calendarTicks() / CelestialMath.TICKS_IN_DAY);
            int phaseCenter = (int) Math.round(632.0D
                    * PlanetariumProjection.timelineProgress(timeline, phase.calendarTicks()));
            var phaseLayout = PlanetariumProjection.timelineAxisMarker(phaseCenter, 72,
                    PlanetariumProjection.TIMELINE_ICON_SIZE);
            if (phaseLayout.left() + PlanetariumProjection.TIMELINE_ICON_SIZE / 2 != phaseCenter
                    || phaseLayout.top() + PlanetariumProjection.TIMELINE_ICON_SIZE / 2 != 72) {
                throw new AssertionError("full/new Moon marker left the timeline axis: " + phase);
            }
            if (!exceptionalCountsByDay.containsKey(phaseDay)) {
                ordinaryOnlyPhase = true;
                if (defaultDayLayouts.stream().anyMatch(layout -> layout.day() == phaseDay)) {
                    throw new AssertionError("ordinary-only phase day unexpectedly received a pointer: "
                            + phaseDay);
                }
            }
        }
        if (!ordinaryOnlyPhase) {
            throw new AssertionError("400-day timeline did not provide an ordinary-only phase day");
        }
        assertClose(0.0D, PlanetariumProjection.timelineProgress(timeline,
                timeline.startCalendarTicks() - 1.0D), "timeline left clamp");
        assertClose(0.5D, PlanetariumProjection.timelineProgress(timeline,
                (timeline.startCalendarTicks() + timeline.endCalendarTicks()) * 0.5D),
                "timeline midpoint");
        assertClose(1.0D, PlanetariumProjection.timelineProgress(timeline,
                timeline.endCalendarTicks() + 1.0D), "timeline right clamp");
        var selected = PlanetariumProjection.selectSolar(timeline,
                timeline.solar().get(0).conjunctionIndex());
        if (!selected.present() || selected.conjunctionIndex()
                != timeline.solar().get(0).conjunctionIndex()
                || PlanetariumProjection.selectSolar(timeline, Long.MIN_VALUE).present()) {
            throw new AssertionError("clickable solar timeline selection changed");
        }
        var selectedLunar = PlanetariumProjection.selectLunar(timeline,
                timeline.lunar().get(0).fullMoonIndex());
        if (!selectedLunar.present() || selectedLunar.fullMoonIndex()
                != timeline.lunar().get(0).fullMoonIndex()
                || PlanetariumProjection.selectLunar(timeline, Long.MIN_VALUE).present()) {
            throw new AssertionError("clickable lunar timeline selection changed");
        }
        var selectedPhase = PlanetariumProjection.selectPhase(timeline,
                timeline.phases().get(0).phaseIndex(), timeline.phases().get(0).kind());
        if (!selectedPhase.present() || selectedPhase.phaseIndex()
                != timeline.phases().get(0).phaseIndex()
                || selectedPhase.kind() != timeline.phases().get(0).kind()
                || PlanetariumProjection.selectPhase(timeline, Long.MIN_VALUE, null).present()) {
            throw new AssertionError("clickable full/new Moon timeline selection changed");
        }
        var adjacentDays = PlanetariumProjection.timelineDayLayouts(java.util.List.of(
                        new PlanetariumProjection.TimelineDaySeed(20L, 92, 0, 3),
                        new PlanetariumProjection.TimelineDaySeed(21L, 93, 0, 1)),
                PlanetariumProjection.TIMELINE_ICON_SIZE,
                PlanetariumProjection.TIMELINE_TRACK_GAP, 0, 200);
        if (adjacentDays.size() != 2 || adjacentDays.get(0).pointerX() == 92
                || adjacentDays.get(1).pointerX() == 93
                || adjacentDays.get(0).lowerLefts().get(2)
                + PlanetariumProjection.TIMELINE_ICON_SIZE
                + PlanetariumProjection.TIMELINE_TRACK_GAP
                > adjacentDays.get(1).lowerLefts().get(0)) {
            throw new AssertionError("neighboring day groups overlap or retained duplicate pointers: "
                    + adjacentDays);
        }
        double previous = Double.NEGATIVE_INFINITY;
        for (var solar : timeline.solar()) {
            if (!solar.present() || solar.greatestCalendarTicks() < previous
                    || solar.endCalendarTicks() < timeline.startCalendarTicks()
                    || solar.startCalendarTicks() > timeline.endCalendarTicks()) {
                throw new AssertionError("solar timeline order/window changed: " + solar);
            }
            previous = solar.greatestCalendarTicks();
        }
        previous = Double.NEGATIVE_INFINITY;
        for (var lunar : timeline.lunar()) {
            if (!lunar.present() || lunar.greatestCalendarTicks() < previous
                    || lunar.endCalendarTicks() < timeline.startCalendarTicks()
                    || lunar.startCalendarTicks() > timeline.endCalendarTicks()
                    || lunar.startCalendarTicks() > lunar.greatestCalendarTicks()
                    || lunar.greatestCalendarTicks() > lunar.endCalendarTicks()) {
                throw new AssertionError("lunar timeline order/window changed: " + lunar);
            }
            if (lunar.eclipse()) {
                double before = lunarPenumbraCoverage(lunar.startCalendarTicks() - 1.0D, 10_000.0D);
                double entered = lunarPenumbraCoverage(lunar.startCalendarTicks() + 1.0D, 10_000.0D);
                double leaving = lunarPenumbraCoverage(lunar.endCalendarTicks() - 1.0D, 10_000.0D);
                double after = lunarPenumbraCoverage(lunar.endCalendarTicks() + 1.0D, 10_000.0D);
                if (before > 0.0D || !(entered > 0.0D) || !(leaving > 0.0D) || after > 0.0D) {
                    throw new AssertionError("lunar forecast start/end no longer match first/last penumbral contact: "
                            + before + ", " + entered + ", " + leaving + ", " + after + "; " + lunar);
                }
            }
            previous = lunar.greatestCalendarTicks();
        }
        previous = Double.NEGATIVE_INFINITY;
        for (var phase : timeline.phases()) {
            if (!phase.present() || phase.calendarTicks() < previous
                    || phase.calendarTicks() < timeline.startCalendarTicks()
                    || phase.calendarTicks() > timeline.endCalendarTicks()) {
                throw new AssertionError("lunar phase timeline order/window changed: " + phase);
            }
            previous = phase.calendarTicks();
        }

        assertPixelAsset("/assets/wildfires/textures/gui/planetarium/planetarium_background.png",
                1024, 576, false);
        assertPixelAsset("/assets/wildfires/textures/gui/planetarium/planetarium_day_disc.png",
                256, 256, true);
        assertPixelAsset("/assets/wildfires/textures/gui/planetarium/planetarium_night_disc.png",
                256, 256, true);
        assertPixelAsset("/assets/wildfires/textures/gui/planetarium/planetarium_time_pointer.png",
                128, 128, true);
        assertVanillaTimelineTextures();
        String english = resourceText("/assets/wildfires/lang/en_us.json");
        String chinese = resourceText("/assets/wildfires/lang/zh_cn.json");
        if (english.contains("planetarium.export") || chinese.contains("planetarium.export")
                || english.contains("PNG export") || chinese.contains("PNG 导出")) {
            throw new AssertionError("removed planetarium PNG export UI is still present");
        }
        if (!english.contains("\"screen.wildfires.planetarium.timeline.start\": \"Starts: %s\"")
                || !english.contains("\"screen.wildfires.planetarium.timeline.maximum\": \"Maximum: %s\"")
                || !english.contains("\"screen.wildfires.planetarium.timeline.end\": \"Ends: %s\"")
                || !chinese.contains("\"screen.wildfires.planetarium.timeline.start\": \"开始：%s\"")
                || !chinese.contains("\"screen.wildfires.planetarium.timeline.maximum\": \"最大：%s\"")
                || !chinese.contains("\"screen.wildfires.planetarium.timeline.end\": \"结束：%s\"")) {
            throw new AssertionError("eclipse timeline no longer exposes localized start/maximum/end labels");
        }
    }

    private static double lunarPenumbraCoverage(double calendarTicks, double observerZ) {
        return CelestialMath.calculate(new CelestialMath.Input(
                observerZ, 20_000.0D, calendarTicks, 8))
                .lunarEclipseRegion().penumbraCoverage();
    }

    private static void assertVanillaTimelineTextures() {
        try (InputStream sunStream = CelestialClientSelfTest.class.getResourceAsStream(
                "/assets/minecraft/textures/environment/sun.png");
             InputStream moonStream = CelestialClientSelfTest.class.getResourceAsStream(
                     "/assets/minecraft/textures/environment/moon_phases.png")) {
            if (sunStream == null || moonStream == null) {
                throw new AssertionError("missing vanilla Sun or Moon texture for planetarium timeline");
            }
            BufferedImage sun = ImageIO.read(sunStream);
            BufferedImage moon = ImageIO.read(moonStream);
            if (sun == null || sun.getWidth() != 32 || sun.getHeight() != 32
                    || moon == null || moon.getWidth() != 128 || moon.getHeight() != 64) {
                throw new AssertionError("vanilla planetarium timeline texture dimensions changed");
            }
            int opaqueFullMoonPixels = 0;
            int opaqueNewMoonPixels = 0;
            int opaqueSunDiscPixels = 0;
            int nonBlackFullMoonPixels = 0;
            int nonBlackNewMoonPixels = 0;
            int nonBlackSunDiscPixels = 0;
            for (int y = 12; y < 20; y++) {
                for (int x = 12; x < 20; x++) {
                    int moonPixel = moon.getRGB(x, y);
                    int newMoonPixel = moon.getRGB(x, y + 32);
                    int sunPixel = sun.getRGB(x, y);
                    if ((moonPixel >>> 24) != 0) {
                        opaqueFullMoonPixels++;
                    }
                    if ((sunPixel >>> 24) != 0) {
                        opaqueSunDiscPixels++;
                    }
                    if ((newMoonPixel >>> 24) != 0) {
                        opaqueNewMoonPixels++;
                    }
                    if ((moonPixel & 0x00FFFFFF) != 0) {
                        nonBlackFullMoonPixels++;
                    }
                    if ((sunPixel & 0x00FFFFFF) != 0) {
                        nonBlackSunDiscPixels++;
                    }
                    if ((newMoonPixel & 0x00FFFFFF) != 0) {
                        nonBlackNewMoonPixels++;
                    }
                }
            }
            if (opaqueFullMoonPixels != 64 || opaqueNewMoonPixels != 64
                    || opaqueSunDiscPixels != 64 || nonBlackFullMoonPixels != 64
                    || nonBlackNewMoonPixels != 64 || nonBlackSunDiscPixels != 64) {
                throw new AssertionError("vanilla central 8x8 Sun/full/new-Moon discs contain "
                        + "transparent or black background pixels: alpha=" + opaqueSunDiscPixels
                        + "/" + opaqueFullMoonPixels + "/" + opaqueNewMoonPixels + ", color="
                        + nonBlackSunDiscPixels + "/" + nonBlackFullMoonPixels + "/"
                        + nonBlackNewMoonPixels);
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to read vanilla timeline textures", exception);
        }
    }

    private static void assertSeparatedTimelineRow(java.util.List<Integer> lefts, String label) {
        for (int index = 0; index < lefts.size(); index++) {
            int left = lefts.get(index);
            if (left < -4 || left + PlanetariumProjection.TIMELINE_ICON_SIZE > 636
                    || index > 0 && left - lefts.get(index - 1)
                    < PlanetariumProjection.TIMELINE_ICON_SIZE
                    + PlanetariumProjection.TIMELINE_TRACK_GAP) {
                throw new AssertionError("timeline icons overlap in " + label + ": " + lefts);
            }
        }
    }

    private static first.wildfires.celestial.EclipsePredictionService.CurrentEvents
    currentEventsAtRepresentableTick(first.wildfires.celestial.CelestialEventType type,
                                     double start, double greatest, double end, double observerZ) {
        long firstTick = (long) Math.ceil(start);
        long last = (long) Math.floor(end);
        if (last < firstTick) {
            throw new AssertionError("predicted event has no representable calendar tick: " + type
                    + " [" + start + ", " + end + "]");
        }
        var settings = first.wildfires.celestial.CelestialRuntimeSettings.DEFAULT;
        for (long tick = firstTick; tick <= last; tick++) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    observerZ, 20_000.0D, tick, 8,
                    settings.resolvedSynodicDays(8), settings.resolvedAnomalisticDays(8),
                    settings.nodalYears(), settings.lunarInclinationRadians(),
                    settings.sunScale(), settings.moonScale()));
            if (!type.matches(result, tick, null)) {
                continue;
            }
            var current = first.wildfires.celestial.EclipsePredictionService.currentEvents(
                    tick, 8, 20_000.0D, observerZ,
                    settings);
            if (current.events().stream().anyMatch(event -> event.type() == type)) {
                return current;
            }
        }
        throw new AssertionError("predicted event has no matching integer-tick API state: " + type
                + " [" + start + ", " + greatest + ", " + end + "]");
    }

    private static int rowLeft(java.util.List<Integer> lefts) {
        return lefts.isEmpty() ? Integer.MAX_VALUE / 4 : lefts.get(0);
    }

    private static int rowRight(java.util.List<Integer> lefts) {
        return lefts.isEmpty() ? Integer.MIN_VALUE / 4
                : lefts.get(lefts.size() - 1) + PlanetariumProjection.TIMELINE_ICON_SIZE;
    }

    private static void assertPixelAsset(String path, int expectedWidth, int expectedHeight,
                                         boolean transparentCorner) {
        try (InputStream stream = CelestialClientSelfTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("missing planetarium pixel asset " + path);
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != expectedWidth || image.getHeight() != expectedHeight) {
                throw new AssertionError("planetarium pixel asset dimensions changed: " + path);
            }
            int cornerAlpha = image.getRGB(0, 0) >>> 24;
            if ((cornerAlpha == 0) != transparentCorner) {
                throw new AssertionError("planetarium asset corner alpha changed: " + path
                        + " alpha=" + cornerAlpha);
            }
            for (int y = 0; y < image.getHeight(); y += 4) {
                for (int x = 0; x < image.getWidth(); x += 4) {
                    int pixel = image.getRGB(x, y);
                    for (int offsetY = 0; offsetY < 4; offsetY++) {
                        for (int offsetX = 0; offsetX < 4; offsetX++) {
                            if (image.getRGB(x + offsetX, y + offsetY) != pixel) {
                                throw new AssertionError("planetarium asset is no longer nearest-neighbor "
                                        + "pixel art at " + x + "," + y + ": " + path);
                            }
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to read planetarium pixel asset " + path, exception);
        }
    }

    private static CelestialState planetariumState(double day) {
        CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                10_000.0D, 20_000.0D, day * CelestialMath.TICKS_IN_DAY, 8));
        CelestialBodyState sun = new CelestialBodyState(id("wildfires", "sun"), null,
                result.sunGeocentric(), result.sunDirection(), 1.0D,
                CelestialMath.SUN_ANGULAR_RADIUS, result.solarElevation(), result.daylightFactor(),
                1.0D, result.solarEclipse());
        CelestialBodyState moon = new CelestialBodyState(id("wildfires", "moon"), null,
                result.moonGeocentric(), result.moonDirection(), result.moonDistance(),
                result.moonAngularRadius(), result.moonElevation(), result.illuminatedFraction(),
                result.illuminatedFraction(), result.lunarEclipse());
        return new CelestialState(result.latitude(), result.fractionOfDay(), result.fractionOfYear(),
                Math.round(day * CelestialMath.TICKS_IN_DAY), sun, moon, result.celestialNorth(),
                CelestialBodies.calculate(result, day / CelestialMath.daysInYear(8)),
                result.moonPhase(), result.solarEclipse(), result.physicalSolarEclipse(),
                result.solarEclipseRegion(), result.lunarEclipse(), result.lunarEclipseRegion(),
                result.supermoon(),
                result.bloodMoon(), 0.725D, 1.0D, 1.0D,
                new DaylightState(result.solarElevation(), result.solarElevation() > 0.0D,
                        result.apparentDayTime(), result.daylightFactor()));
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private static void assertUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new AssertionError(name + " was outside [0,1]: " + value);
        }
    }

    private static com.google.gson.JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void assertClose(double expected, double actual, String name) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
