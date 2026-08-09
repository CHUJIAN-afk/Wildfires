package first.wildfires.client.celestial;

import com.google.gson.JsonParser;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.api.celestial.CelestialVector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Deterministic checks for star-table merging and client-only visual eligibility rules. */
public final class CelestialClientSelfTest {

    private static final double EPSILON = 1.0E-9D;

    private CelestialClientSelfTest() {
    }

    public static void main(String[] args) {
        starTablesMergeInStableOrderAndIsolateErrors();
        auroraRulesCoverBothPolesAndLegacyMode();
        auroraAppearancePresetsAreCompleteAndDeterministic();
        auroraCachedGeometryRetainsTheOriginalWave();
        auroraAnimationUsesClientTicks();
        rainbowRulesCoverWeatherAndSolarBounds();
        rainbowDirectionIsFiniteAtSolarZenith();
        eclipseFramesAreBounded();
        bloodMoonTintUsesUnifiedIntensity();
        starAndMoonVisualScalesMatchTheirSources();
        moonAtlasAndSkyCoverRulesAvoidDaytimeArtifacts();
        moonHaloAttenuatesNearbyStarsByPhaseAndDistance();
        planetRenderingRestoresTfccaelumArcSecondScale();
        satelliteRenderingRetainsVisibleThreeDimensionalSeparation();
        actualJupiterAndSaturnSatellitesOrbitTheirParents();
        celestialDiscOrientationIsContinuousAcrossTheZenith();
        allOrbitingBodiesHaveFiniteVisualSizes();
        clientStateCacheIsExactAndInvalidatesEverySemanticKey();
        localApparentTimeUsesVanillaCelestialAngle();
        solarEclipseVisualTimeDimsEverySharedConsumer();
        polarVisualLightingFollowsTheLocalSun();
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
        String first = cache.get(level, 1234L, 55L, 0.375F, 0.25F, observer, settings, factory);
        String repeated = cache.get(level, 1234L, 55L, 0.375F, 0.25F, observer, settings, factory);
        if (first != repeated || computations[0] != 1) {
            throw new AssertionError("identical client visual queries did not share one state");
        }
        cache.get(level, 1234L, 55L, 0.375F, 0.25F,
                observer.add(0.0D, 0.0D, 1.0D), settings, factory);
        cache.get(level, 1235L, 55L, 0.375F, 0.25F, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.375F, 0.25F, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.25F, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, observer, settings, factory);
        cache.get(new Object(), 1235L, 56L, 0.5F, 0.5F, observer, settings, factory);
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, observer, new Object(), factory);
        cache.clear();
        cache.get(level, 1235L, 56L, 0.5F, 0.5F, observer, settings, factory);
        if (computations[0] != 9) {
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

    private static void eclipseFramesAreBounded() {
        if (CelestialVisualRules.solarEclipseFrame(-1.0D) != 7
                || CelestialVisualRules.solarEclipseFrame(0.5D) != 4
                || CelestialVisualRules.solarEclipseFrame(2.0D) != 0
                || CelestialVisualRules.solarEclipseFrame(Double.NaN) != 7) {
            throw new AssertionError("solar eclipse frames do not follow TFCCaelum remaining-light order");
        }
        CelestialVisualRules.SunTint clear = CelestialVisualRules.solarEclipseSunTint(0.0D);
        CelestialVisualRules.SunTint partial = CelestialVisualRules.solarEclipseSunTint(0.5D);
        CelestialVisualRules.SunTint total = CelestialVisualRules.solarEclipseSunTint(1.0D);
        if (!clear.equals(new CelestialVisualRules.SunTint(1.0D, 1.0D, 1.0D))
                || !partial.equals(new CelestialVisualRules.SunTint(1.0D, 0.5D, 0.5D))
                || !total.equals(new CelestialVisualRules.SunTint(1.0D, 0.0D, 0.0D))
                || !CelestialVisualRules.solarEclipseSunTint(Double.NaN).equals(clear)
                || !CelestialVisualRules.solarEclipseSunTint(-1.0D).equals(clear)
                || !CelestialVisualRules.solarEclipseSunTint(2.0D).equals(total)) {
            throw new AssertionError("solar eclipse Sun tint stopped matching the bounded TFCCaelum color");
        }
    }

    private static void bloodMoonTintUsesUnifiedIntensity() {
        CelestialVisualRules.MoonTint normal = CelestialVisualRules.bloodMoonTint(0.0D);
        CelestialVisualRules.MoonTint full = CelestialVisualRules.bloodMoonTint(1.0D);
        CelestialVisualRules.MoonTint clamped = CelestialVisualRules.bloodMoonTint(2.0D);
        assertClose(1.0D, normal.red(), "normal moon red");
        assertClose(1.0D, normal.green(), "normal moon green");
        assertClose(1.0D, normal.blue(), "normal moon blue");
        assertClose(1.25D, full.red(), "blood moon red");
        assertClose(0.325D, full.green(), "blood moon green");
        assertClose(0.15D, full.blue(), "blood moon blue");
        if (!full.equals(clamped) || !normal.equals(CelestialVisualRules.bloodMoonTint(Double.NaN))) {
            throw new AssertionError("blood-moon tint did not clamp invalid intensity");
        }
    }

    private static void moonAtlasAndSkyCoverRulesAvoidDaytimeArtifacts() {
        for (int phase = 0; phase < 8; phase++) {
            boolean expected = phase != 4;
            if (CelestialVisualRules.moonTextureVisible(phase) != expected) {
                throw new AssertionError("unexpected ordinary moon texture visibility for phase " + phase);
            }
        }
        if (CelestialVisualRules.moonTextureVisible(-1) || CelestialVisualRules.moonTextureVisible(8)) {
            throw new AssertionError("out-of-range moon atlas cells were accepted");
        }

        double midnight = 0.75D;
        if (CelestialVisualRules.moonSkyCoverVisible(0, 0.25D, 1.0D)
                || CelestialVisualRules.moonSkyCoverVisible(0, midnight, 0.0D)
                || CelestialVisualRules.moonSkyCoverVisible(-1, midnight, 1.0D)
                || CelestialVisualRules.moonSkyCoverVisible(8, midnight, 1.0D)) {
            throw new AssertionError("moon sky cover leaked into daytime, weather or invalid atlas phase");
        }
        for (int phase = 0; phase < 8; phase++) {
            if (!CelestialVisualRules.moonSkyCoverVisible(phase, midnight, 1.0D)) {
                throw new AssertionError("night moon phase did not mask its full disc: " + phase);
            }
        }
        assertClose(5.0D, CelestialVisualRules.moonAtlasBodyHalfSize(20.0D),
                "vanilla moon atlas pixel-body half size");
        assertClose(13.75D, CelestialVisualRules.moonAtlasGlowRadius(20.0D),
                "vanilla moon atlas gradient radius");
        if (CelestialVisualRules.moonAtlasBodyHalfSize(0.0D) != 0.0D
                || CelestialVisualRules.moonAtlasBodyHalfSize(-1.0D) != 0.0D
                || CelestialVisualRules.moonAtlasBodyHalfSize(Double.NaN) != 0.0D
                || CelestialVisualRules.moonAtlasGlowRadius(Double.POSITIVE_INFINITY) != 0.0D) {
            throw new AssertionError("invalid moon atlas radii were accepted");
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
        assertClose(CelestialBodies.JUPITER.inclinationRadians() + CelestialBodies.GANYMEDE.inclinationRadians(),
                CelestialBodies.GANYMEDE.orbitalPlaneInclinationRadians(),
                "Ganymede parent-relative orbital plane");
        assertClose(CelestialBodies.SATURN.inclinationRadians() + CelestialBodies.TITAN.inclinationRadians(),
                CelestialBodies.TITAN.orbitalPlaneInclinationRadians(),
                "Titan parent-relative orbital plane");
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
        double partial = CelestialClientTime.visualApparentDayTime(noon, 0.5D);
        double total = CelestialClientTime.visualApparentDayTime(noon, 1.0D);
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, 0.0D),
                "zero-eclipse visual time");
        assertClose(0.5D, partial, "partial-eclipse visual time");
        assertClose(0.75D, total, "total-eclipse visual midnight");
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, -1.0D),
                "negative eclipse coverage");
        assertClose(noon, CelestialClientTime.visualApparentDayTime(noon, Double.NaN),
                "non-finite eclipse coverage");
        assertClose(0.75D, CelestialClientTime.visualApparentDayTime(noon, 2.0D),
                "clamped eclipse coverage");
        assertClose(0.875D, CelestialClientTime.visualApparentDayTime(0.0D, 0.5D),
                "sunrise eclipse follows the short path to midnight");
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
                || CelestialVisualRules.planetVisibility(1.0D, partial, 1.0D) != partialStars
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
                        sawSun |= CelestialVisualRules.discVisible(frame.solarElevation());
                        sawMoon |= CelestialVisualRules.discVisible(frame.moonElevation());
                        sawStars |= stars > 0.4D;
                        sawTwilight |= twilight > 0.0D;
                        var bodies = CelestialBodies.calculate(frame,
                                CelestialMath.calendarYears(day * CelestialMath.TICKS_IN_DAY, daysInMonth));
                        if (bodies.size() != 17) {
                            throw new AssertionError("client scene lost a TFCCaelum body");
                        }
                        for (var body : bodies) {
                            double visibility = CelestialVisualRules.planetVisibility(body.altitudeRadians(),
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
                    && CelestialVisualRules.discVisible(frame.solarElevation());
            sawLunarEclipse |= frame.lunarEclipse() > 0.01D
                    && CelestialVisualRules.discVisible(frame.moonElevation());
        }
        if (!sawSolarEclipse || !sawLunarEclipse) {
            throw new AssertionError("client scene sweep did not include visible solar and lunar eclipses");
        }
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
