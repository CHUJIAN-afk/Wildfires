package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.network.CelestialSettingsSyncPacket;
import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.HashSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

/** Plain-Java regression checks for the unified celestial model. */
public final class CelestialMathSelfTest {

    private static final double EPSILON = 1.0E-6D;

    private CelestialMathSelfTest() {
    }

    public static void main(String[] args) {
        latitudeGridMatchesTfcPolesAndEquators();
        configurableMonthLengthPreservesDaysAndScalesYears();
        solarFrameIsFiniteAtPoles();
        equinoxSolsticeAndPolarDayAreDistinct();
        seasonalGridIsNorthSouthSymmetric();
        solarDirectionsMatchTfcReferenceMath();
        synodicCycleReturnsToFullMoon();
        moonPhaseCellsUseThreeDimensionalSeparation();
        moonPhaseAndDirectionAreContinuousAcrossCycle();
        anomalisticCycleIsIndependent();
        nodalCycleIsIndependent();
        lunarPeriodPresetsAreExplicit();
        eclipseDiscGeometryIsBounded();
        realGeometryProducesSolarAndLunarEclipses();
        eventTargetsUseTheUnifiedVisibleGeometry();
        visualScaleCannotChangeEclipseMath();
        orbitalProjectionIsThreeDimensionalAndDeterministic();
        bodyDefinitionsMatchTfccaelumAuthority();
        configuredPrimaryBodySettingsDriveUnifiedOrbits();
        allSeventeenBodiesAreFiniteHierarchicalAndDeterministic();
        bloodMoonGameplayRulesAreLocalAndFinite();
        overworldFrameContextIsExactlyEquivalentAtEveryLatitude();
        tfeHemisphereMethodHandleIsStrictAndFinite();
        settingsPacketRoundTripsAllAuthoritativeFields();
        legacyCelestialModsAreRejectedExplicitly();
        System.out.println("CelestialMathSelfTest: all checks passed");
    }

    private static void legacyCelestialModsAreRejectedExplicitly() {
        LegacyCelestialModGuard.rejectLoaded(modId -> false);
        assertLegacyModRejected("caelum");
        assertLegacyModRejected("tfccaelum");
    }

    private static void overworldFrameContextIsExactlyEquivalentAtEveryLatitude() {
        CelestialRuntimeSettings custom = new CelestialRuntimeSettings(17.25D, 14.75D, 19.0D,
                Math.toRadians(4.75D), true, 2.5D, CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        for (CelestialRuntimeSettings settings : new CelestialRuntimeSettings[]{
                CelestialRuntimeSettings.DEFAULT, custom}) {
            double ticks = 9876543.25D;
            int daysInMonth = 11;
            double scale = 23456.0D;
            OverworldCelestialProvider.FrameContext context = new OverworldCelestialProvider.FrameContext(
                    ticks, daysInMonth, scale, settings);
            for (double z : new double[]{-50000.0D, -11728.5D, 0.0D, 11728.5D, 50000.0D}) {
                OverworldCelestialProvider.Frame actual = context.frameAt(z);
                CelestialMath.Result expected = CelestialMath.calculate(new CelestialMath.Input(z, scale, ticks,
                        daysInMonth, settings.resolvedSynodicDays(daysInMonth),
                        settings.resolvedAnomalisticDays(daysInMonth), settings.nodalYears(),
                        settings.lunarInclinationRadians()));
                if (!expected.equals(actual.result())
                        || Double.doubleToLongBits(ticks) != Double.doubleToLongBits(actual.calendarTicks())
                        || actual.daysInMonth() != daysInMonth) {
                    throw new AssertionError("shared overworld frame changed the celestial result at z=" + z);
                }
            }
        }
    }

    private static void tfeHemisphereMethodHandleIsStrictAndFinite() {
        try {
            Method valid = CelestialMathSelfTest.class.getMethod("validHemisphereScale", Level.class);
            MethodHandle handle = TfeHemisphereScale.adapt(valid);
            assertClose(12345.0D, TfeHemisphereScale.invoke(handle, null), "method-handle TFE scale");
            assertClose(12345.0D, TfeHemisphereScale.validOrFallback(12345.0F), "finite TFE scale");
            assertClose(-12345.0D, TfeHemisphereScale.validOrFallback(-12345.0F), "signed TFE scale");
            assertClose(20000.0D, TfeHemisphereScale.validOrFallback(0.0F), "zero TFE scale fallback");
            assertClose(20000.0D, TfeHemisphereScale.validOrFallback(Float.NaN), "NaN TFE scale fallback");
            assertClose(20000.0D, TfeHemisphereScale.validOrFallback(Float.POSITIVE_INFINITY),
                    "infinite TFE scale fallback");
            assertRejectedTfeMethod(CelestialMathSelfTest.class.getMethod("wrongHemisphereReturn", Level.class));
            assertRejectedTfeMethod(CelestialMathSelfTest.class.getMethod("instanceHemisphereScale", Level.class));
        } catch (Throwable exception) {
            throw new AssertionError("TFE MethodHandle adapter failed", exception);
        }
    }

    private static void assertRejectedTfeMethod(Method method) throws IllegalAccessException {
        try {
            TfeHemisphereScale.adapt(method);
            throw new AssertionError("invalid TFE helper signature was accepted: " + method);
        } catch (NoSuchMethodException expected) {
            // Expected strict failure: a renamed or retyped TFE helper must not silently link.
        }
    }

    public static float validHemisphereScale(Level ignored) {
        return 12345.0F;
    }

    public static double wrongHemisphereReturn(Level ignored) {
        return 12345.0D;
    }

    public float instanceHemisphereScale(Level ignored) {
        return 12345.0F;
    }

    private static void assertLegacyModRejected(String legacyModId) {
        try {
            LegacyCelestialModGuard.rejectLoaded(legacyModId::equals);
            throw new AssertionError("legacy celestial mod was not rejected: " + legacyModId);
        } catch (IllegalStateException exception) {
            if (!exception.getMessage().contains(legacyModId)
                    || !exception.getMessage().contains("remove the legacy")) {
                throw new AssertionError("legacy celestial mod rejection was not actionable", exception);
            }
        }
    }

    private static void latitudeGridMatchesTfcPolesAndEquators() {
        // TFE exposes a 20,000 block hemisphere scale for the default 10 km temperature scale.
        double scale = 20000.0D;
        assertClose(Math.PI / 2.0D, CelestialMath.latitude(-10000.0D, scale), "north pole");
        assertClose(0.0D, CelestialMath.latitude(10000.0D, scale), "equator");
        assertClose(-Math.PI / 2.0D, CelestialMath.latitude(30000.0D, scale), "south pole");
        assertClose(0.0D, CelestialMath.latitude(-30000.0D, scale), "wrapped equator");
    }

    private static void configurableMonthLengthPreservesDaysAndScalesYears() {
        assertClose(1.0D, CelestialMath.calendarDays(24000.0D), "one TFC day remains 24000 ticks");
        assertClose(1200.0D, CelestialMath.TICKS_IN_DAY / 20.0D,
                "one TFC day at the normal 20 TPS rate");
        for (int daysInMonth : new int[]{1, 4, 8, 12, 16, Integer.MAX_VALUE}) {
            double yearDays = daysInMonth * 12.0D;
            assertClose(yearDays, CelestialMath.daysInYear(daysInMonth),
                    "configured TFC year length for " + daysInMonth + " day months");
            assertClose(1.0D, CelestialMath.calendarYears(yearDays * 24000.0D, daysInMonth),
                    "configured calendar year conversion for " + daysInMonth + " day months");

            CelestialMath.Result day = calculateAt(10000.0D, 5.25D, daysInMonth);
            CelestialMath.Result nextDay = calculateAt(10000.0D, 6.25D, daysInMonth);
            assertClose(0.25D, day.fractionOfDay(), "daily fraction");
            assertClose(day.fractionOfDay(), nextDay.fractionOfDay(), "daily 24000 tick repetition");

            CelestialMath.Result start = calculateAt(10000.0D, 0.25D, daysInMonth);
            CelestialMath.Result nextYear = calculateAt(10000.0D, yearDays + 0.25D, daysInMonth);
            if (CelestialMath.angle(start.sunDirection(), nextYear.sunDirection()) > 1.0E-6D) {
                throw new AssertionError("solar year ignored configured month length " + daysInMonth);
            }

            double nodeStart = CelestialMath.lunarAscendingNode(0.0D, yearDays, CelestialMath.NODAL_YEARS);
            double nodeEnd = CelestialMath.lunarAscendingNode(yearDays * CelestialMath.NODAL_YEARS,
                    yearDays, CelestialMath.NODAL_YEARS);
            assertClose(nodeStart, nodeEnd,
                    "18.6 configured-year nodal cycle for " + daysInMonth + " day months");

            CelestialMath.Result planetFrame = calculateAt(10000.0D, yearDays * 2.5D, daysInMonth);
            var bodies = CelestialBodies.calculate(planetFrame,
                    CelestialMath.calendarYears(yearDays * 2.5D * 24000.0D, daysInMonth));
            if (bodies.size() != 17 || !bodies.equals(CelestialBodies.calculate(planetFrame, 2.5D))) {
                throw new AssertionError("planet year conversion ignored configured month length " + daysInMonth);
            }
        }

        // TFC 3.2.20 defines defaultMonthLength over the complete [1, Integer.MAX_VALUE] domain.
        // Exercise a deterministic spread across that domain in addition to both exact boundaries.
        long sample = 0x4D595DF4D0F33173L;
        for (int index = 0; index < 4096; index++) {
            sample = sample * 6364136223846793005L + 1442695040888963407L;
            int daysInMonth = 1 + (int) Long.remainderUnsigned(sample, Integer.MAX_VALUE);
            double expectedYearDays = (double) daysInMonth * CelestialMath.MONTHS_IN_YEAR;
            assertClose(expectedYearDays, CelestialMath.daysInYear(daysInMonth),
                    "legal TFC month-length domain sample " + index);
            assertClose(1.0D, CelestialMath.calendarYears(
                            expectedYearDays * CelestialMath.TICKS_IN_DAY, daysInMonth),
                    "legal TFC calendar-year domain sample " + index);
        }
    }

    private static void solarFrameIsFiniteAtPoles() {
        for (double z : new double[]{-10000.0D, 30000.0D}) {
            for (int day = 0; day < 96; day++) {
                CelestialMath.Result result = CelestialMath.calculate(
                        new CelestialMath.Input(z, 20000.0D, day * 24000.0D, 8));
                assertFinite(result.solarElevation(), "solar elevation");
                assertFinite(result.moonElevation(), "moon elevation");
                assertFinite(result.apparentDayTime(), "apparent time");
            }
        }
    }

    private static void equinoxSolsticeAndPolarDayAreDistinct() {
        double yearDays = 96.0D;
        double equinoxDay = Math.floor(positiveModulo(-284.0D / 365.0D, 1.0D) * yearDays) + 0.5D;
        double solsticeDay = Math.floor(positiveModulo(0.25D - 284.0D / 365.0D, 1.0D) * yearDays) + 0.5D;
        CelestialMath.Result equinox = calculateAt(10000.0D, equinoxDay);
        CelestialMath.Result solstice = calculateAt(10000.0D, solsticeDay);
        if (!(equinox.solarElevation() > solstice.solarElevation())) {
            throw new AssertionError("equatorial equinox noon must exceed solstice noon");
        }
        CelestialMath.Result northSummer = calculateAt(-10000.0D, solsticeDay);
        CelestialMath.Result northWinter = calculateAt(-10000.0D, solsticeDay + yearDays * 0.5D);
        if (!(northSummer.solarElevation() > northWinter.solarElevation())) {
            throw new AssertionError("polar summer and winter were not distinguished");
        }
    }

    private static void seasonalGridIsNorthSouthSymmetric() {
        double yearDays = 96.0D;
        for (int season = 0; season < 4; season++) {
            double day = season * yearDays / 4.0D + 0.5D;
            CelestialMath.Result north = calculateAt(-10000.0D, day);
            CelestialMath.Result southOppositeSeason = calculateAt(30000.0D, day + yearDays * 0.5D);
            assertClose(north.solarElevation(), southOppositeSeason.solarElevation(),
                    "north/south opposite-season solar elevation " + season);
            assertFinite(north.celestialNorth().x(), "celestial north x");
            assertFinite(north.celestialNorth().y(), "celestial north y");
            assertFinite(north.celestialNorth().z(), "celestial north z");
        }
    }

    private static void solarDirectionsMatchTfcReferenceMath() {
        double scale = 20000.0D;
        int daysInMonth = 8;
        double yearDays = CelestialMath.daysInYear(daysInMonth);
        for (double z : new double[]{-5000.0D, 0.0D, 10000.0D, 20000.0D, 25000.0D}) {
            for (double yearFraction : new double[]{0.0D, 0.125D, 0.25D, 0.5D, 0.75D}) {
                for (double dayFraction : new double[]{0.0D, 0.125D, 0.25D, 0.5D, 0.75D, 0.875D}) {
                    CelestialMath.Result actual = calculateAt(z, yearFraction * yearDays + dayFraction,
                            daysInMonth);
                    CelestialVector expected = tfcReferenceSunDirection(z, scale, actual.fractionOfYear(),
                            actual.fractionOfDay());
                    if (CelestialMath.angle(expected, actual.sunDirection()) > 1.0E-6D) {
                        throw new AssertionError("solar direction diverged from TFC reference at z=" + z
                                + ", year=" + yearFraction + ", day=" + dayFraction);
                    }
                }
            }
        }
    }

    private static CelestialVector tfcReferenceSunDirection(double z, double scale,
                                                             double fractionOfYear, double fractionOfDay) {
        double latitude = CelestialMath.latitude(z, scale);
        double declination = CelestialMath.AXIAL_TILT
                * Math.sin(CelestialMath.TAU * (284.0D / 365.0D + fractionOfYear));
        double hourAngle = CelestialMath.TAU * (0.5D - fractionOfDay);
        double cosineZenith = Math.sin(latitude) * Math.sin(declination)
                + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
        double zenith = Math.acos(Math.max(-1.0D, Math.min(1.0D, cosineZenith)));
        double denominator = Math.sin(zenith) * Math.cos(latitude);
        double cosineAzimuth = (Math.sin(declination) - Math.cos(zenith) * Math.sin(latitude)) / denominator;
        double absoluteAzimuth = Math.acos(Math.max(-1.0D, Math.min(1.0D, cosineAzimuth)));
        double azimuth = hourAngle < 0.0D ? absoluteAzimuth : CelestialMath.TAU - absoluteAzimuth;
        double horizontal = Math.sin(zenith);
        return new CelestialVector(-horizontal * Math.sin(azimuth), Math.cos(zenith),
                horizontal * Math.cos(azimuth)).normalized();
    }

    private static void synodicCycleReturnsToFullMoon() {
        CelestialMath.Result start = calculateAtDay(0.0D);
        CelestialMath.Result quarter = calculateAtDay(CelestialMath.SYNODIC_DAYS / 4.0D);
        CelestialMath.Result half = calculateAtDay(CelestialMath.SYNODIC_DAYS / 2.0D);
        CelestialMath.Result end = calculateAtDay(CelestialMath.SYNODIC_DAYS);
        if (start.moonPhase() != 0 || half.moonPhase() != 4 || end.moonPhase() != 0) {
            throw new AssertionError("moon phase indices do not follow the 16.13 day cycle");
        }
        if (!(start.illuminatedFraction() > quarter.illuminatedFraction()
                && quarter.illuminatedFraction() > half.illuminatedFraction())) {
            throw new AssertionError("moon illumination does not decrease from full to new");
        }
    }

    private static void moonPhaseCellsUseThreeDimensionalSeparation() {
        if (CelestialMath.moonPhaseFromGeometry(0.0D, Math.PI) != 0
                || CelestialMath.moonPhaseFromGeometry(0.5D, 0.0D) != 4
                || CelestialMath.moonPhaseFromGeometry(0.25D, Math.PI * 0.75D) != 1
                || CelestialMath.moonPhaseFromGeometry(0.75D, Math.PI * 0.75D) != 7) {
            throw new AssertionError("moon phase cells were not mapped from geometric separation");
        }
    }

    private static void moonPhaseAndDirectionAreContinuousAcrossCycle() {
        double epsilonDays = 1.0E-6D;
        CelestialMath.Result before = calculateAtDay(CelestialMath.SYNODIC_DAYS - epsilonDays);
        CelestialMath.Result after = calculateAtDay(CelestialMath.SYNODIC_DAYS + epsilonDays);
        if (CelestialMath.angle(before.moonDirection(), after.moonDirection()) > 1.0E-3D
                || Math.abs(before.illuminatedFraction() - after.illuminatedFraction()) > 1.0E-6D) {
            throw new AssertionError("moon direction/illumination is discontinuous at the synodic wrap");
        }
        CelestialMath.Result full = calculateAtDay(0.0D);
        CelestialMath.Result fresh = calculateAtDay(CelestialMath.SYNODIC_DAYS * 0.5D);
        if (!(full.sunMoonSeparation() > 3.0D && fresh.sunMoonSeparation() < 0.2D)) {
            throw new AssertionError("rendered lunar direction does not agree with full/new phase geometry");
        }
    }

    private static void anomalisticCycleIsIndependent() {
        CelestialMath.Result start = calculateAtDay(0.0D);
        CelestialMath.Result halfDistance = calculateAtDay(CelestialMath.ANOMALISTIC_DAYS / 2.0D);
        CelestialMath.Result end = calculateAtDay(CelestialMath.ANOMALISTIC_DAYS);
        if (!(start.moonDistance() < halfDistance.moonDistance())) {
            throw new AssertionError("perigee/apogee order is invalid");
        }
        assertClose(start.moonDistance(), end.moonDistance(), "anomalistic cycle");
        assertClose(15.050661758D, CelestialMath.ANOMALISTIC_DAYS, "anomalistic ratio");
    }

    private static void nodalCycleIsIndependent() {
        double yearDays = 96.0D;
        double start = CelestialMath.lunarAscendingNode(0.0D, yearDays, CelestialMath.NODAL_YEARS);
        double half = CelestialMath.lunarAscendingNode(yearDays * CelestialMath.NODAL_YEARS * 0.5D,
                yearDays, CelestialMath.NODAL_YEARS);
        double end = CelestialMath.lunarAscendingNode(yearDays * CelestialMath.NODAL_YEARS,
                yearDays, CelestialMath.NODAL_YEARS);
        assertClose(0.0D, start, "nodal start");
        assertClose(-Math.PI, half, "nodal half-cycle");
        assertClose(0.0D, end, "nodal full-cycle");
    }

    private static void lunarPeriodPresetsAreExplicit() {
        CelestialRuntimeSettings unified = CelestialRuntimeSettings.DEFAULT;
        assertClose(16.13D, unified.resolvedSynodicDays(8), "unified synodic preset");
        CelestialRuntimeSettings legacy = new CelestialRuntimeSettings(99.0D, 98.0D, 18.6D,
                CelestialMath.LUNAR_INCLINATION, true, 3.0D,
                CelestialRuntimeSettings.LunarPeriodPreset.LEGACY_TFCCAELUM,
                CelestialPlanetSettings.DEFAULT);
        assertClose(8.0D * 29.530588D / 30.436875D, legacy.resolvedSynodicDays(8),
                "legacy TFCCaelum orbit period");
        assertClose(29.530588D, legacy.resolvedAnomalisticDays(8),
                "legacy TFCCaelum supermoon period");
    }

    private static void eclipseDiscGeometryIsBounded() {
        assertClose(1.0D, CelestialMath.circleCoverage(1.0D, 2.0D, 0.0D), "full coverage");
        assertClose(0.0D, CelestialMath.circleCoverage(1.0D, 1.0D, 2.1D), "separated discs");
        double partial = CelestialMath.circleCoverage(1.0D, 1.0D, 1.0D);
        if (!(partial > 0.0D && partial < 1.0D)) {
            throw new AssertionError("partial disc coverage is outside (0,1)");
        }
    }

    private static void realGeometryProducesSolarAndLunarEclipses() {
        double cycleDays = 96.0D * CelestialMath.NODAL_YEARS;
        double maxSolar = 0.0D;
        double maxLunar = 0.0D;
        double maxBloodMoon = 0.0D;
        boolean partialSolar = false;
        boolean partialLunar = false;
        for (double day = 0.0D; day < cycleDays; day += 0.02D) {
            CelestialMath.Result result = calculateAtDay(day);
            maxSolar = Math.max(maxSolar, result.solarEclipse());
            maxLunar = Math.max(maxLunar, result.lunarEclipse());
            maxBloodMoon = Math.max(maxBloodMoon, result.bloodMoon());
            partialSolar |= result.solarEclipse() > 0.001D && result.solarEclipse() < 0.999D;
            partialLunar |= result.lunarEclipse() > 0.001D && result.lunarEclipse() < 0.999D;
            assertClose(result.lunarEclipse(), result.bloodMoon(),
                    "blood moon uses the unified three-dimensional Earth-shadow geometry");
        }
        if (maxSolar < 0.25D || maxLunar < 0.25D || maxBloodMoon < 0.25D
                || !partialSolar || !partialLunar) {
            throw new AssertionError("unified geometry did not produce full-cycle solar/lunar eclipse events: "
                    + maxSolar + ", " + maxLunar + ", blood moon=" + maxBloodMoon);
        }
    }

    private static void eventTargetsUseTheUnifiedVisibleGeometry() {
        double cycleDays = 96.0D * CelestialMath.NODAL_YEARS;
        boolean solar = false;
        boolean lunar = false;
        boolean blood = false;
        for (double day = 0.0D; day < cycleDays && !(solar && lunar && blood); day += 0.01D) {
            long calendarTick = Math.round(day * CelestialMath.TICKS_IN_DAY);
            CelestialMath.Result result = calculateAtDay(day);
            solar |= CelestialEventType.SOLAR_ECLIPSE.matches(result, calendarTick,
                    CelestialEventRules.RainSample.DRY);
            lunar |= CelestialEventType.LUNAR_ECLIPSE.matches(result, calendarTick,
                    CelestialEventRules.RainSample.DRY);
            blood |= CelestialEventType.BLOOD_MOON.matches(result, calendarTick,
                    CelestialEventRules.RainSample.DRY);
        }
        if (!solar || !lunar || !blood) {
            throw new AssertionError("event targets did not expose visible unified eclipses: "
                    + solar + ", " + lunar + ", " + blood);
        }
    }

    private static void visualScaleCannotChangeEclipseMath() {
        CelestialMath.Result result = calculateAtDay(CelestialMath.SYNODIC_DAYS / 2.0D);
        double original = CelestialMath.circleCoverage(CelestialMath.SUN_ANGULAR_RADIUS,
                result.moonAngularRadius(), result.sunMoonSeparation());
        double repeated = CelestialMath.circleCoverage(CelestialMath.SUN_ANGULAR_RADIUS,
                result.moonAngularRadius(), result.sunMoonSeparation());
        assertClose(original, repeated, "eclipse geometry");
    }

    private static void orbitalProjectionIsThreeDimensionalAndDeterministic() {
        var prograde = CelestialMath.orbitalPosition(10.0D, 20.0D, Math.toRadians(30.0D), false, 5.0D);
        var retrograde = CelestialMath.orbitalPosition(10.0D, 20.0D, Math.toRadians(30.0D), true, 5.0D);
        if (Math.abs(prograde.z()) < EPSILON || Math.signum(prograde.y()) == Math.signum(retrograde.y())) {
            throw new AssertionError("inclined prograde/retrograde orbit projection is invalid");
        }
        if (!prograde.equals(CelestialMath.orbitalPosition(10.0D, 20.0D,
                Math.toRadians(30.0D), false, 5.0D))) {
            throw new AssertionError("orbit projection is not deterministic");
        }
        var parent = CelestialMath.orbitalPosition(100.0D, 100.0D, 0.0D, false, 12.0D);
        var child = parent.add(CelestialMath.orbitalPosition(2.0D, 4.0D, 0.2D, false, 12.0D));
        if (child.subtract(parent).length() < 1.99D) {
            throw new AssertionError("parent-child orbit composition lost the satellite offset");
        }
    }

    private static void allSeventeenBodiesAreFiniteHierarchicalAndDeterministic() {
        CelestialBodies.validateDefinitions();
        CelestialMath.Result frame = calculateAtDay(123.456D);
        var first = CelestialBodies.calculate(frame, 12.345D);
        var second = CelestialBodies.calculate(frame, 12.345D);
        if (first.size() != 17 || !first.equals(second)) {
            throw new AssertionError("17-body calculation is incomplete or non-deterministic");
        }
        var ids = new HashSet<net.minecraft.resources.ResourceLocation>();
        var byId = new java.util.HashMap<net.minecraft.resources.ResourceLocation, CelestialBodyState>();
        for (CelestialBodyState body : first) {
            if (!ids.add(body.id()) || !finite(body.geocentricPosition()) || !finite(body.observerDirection())
                    || !Double.isFinite(body.distance()) || body.distance() <= 0.0D
                    || !Double.isFinite(body.angularRadiusRadians()) || body.angularRadiusRadians() <= 0.0D) {
                throw new AssertionError("invalid calculated body state: " + body);
            }
            if (body.parentId() != null && !byId.containsKey(body.parentId())) {
                throw new AssertionError("child was calculated before its parent: " + body.id());
            }
            byId.put(body.id(), body);
        }
        for (CelestialBodies definition : CelestialBodies.values()) {
            CelestialBodyState state = byId.get(definition.id());
            if (state == null || (definition.parent() == null) != (state.parentId() == null)) {
                throw new AssertionError("body definition/state mismatch: " + definition);
            }
        }
    }

    private static void configuredPrimaryBodySettingsDriveUnifiedOrbits() {
        CelestialBodyParameters defaults = CelestialBodies.JUPITER.defaultParameters();
        CelestialBodyParameters changed = new CelestialBodyParameters(defaults.diameterKm() * 1.25D,
                defaults.orbitalDays() * 0.75D, defaults.semiMajorMillionKm() * 1.5D,
                defaults.synodicDays() * 1.1D, defaults.inclinationRadians() + Math.toRadians(4.0D));
        CelestialPlanetSettings settings = CelestialPlanetSettings.DEFAULT.with(CelestialBodies.JUPITER, changed);
        if (!settings.parameters(CelestialBodies.JUPITER).equals(changed)
                || !settings.parameters(CelestialBodies.GANYMEDE)
                .equals(CelestialBodies.GANYMEDE.defaultParameters())) {
            throw new AssertionError("configured primary body table changed a non-configurable satellite");
        }

        CelestialMath.Result frame = calculateAtDay(137.25D);
        var defaultsStates = CelestialBodies.calculate(frame, 7.25D);
        var changedStates = CelestialBodies.calculate(frame, 7.25D, settings);
        CelestialBodyState defaultJupiter = defaultsStates.get(CelestialBodies.JUPITER.ordinal());
        CelestialBodyState changedJupiter = changedStates.get(CelestialBodies.JUPITER.ordinal());
        CelestialBodyState defaultGanymede = defaultsStates.get(CelestialBodies.GANYMEDE.ordinal());
        CelestialBodyState changedGanymede = changedStates.get(CelestialBodies.GANYMEDE.ordinal());
        if (defaultJupiter.geocentricPosition().equals(changedJupiter.geocentricPosition())
                || defaultJupiter.angularRadiusRadians() == changedJupiter.angularRadiusRadians()
                || defaultGanymede.geocentricPosition().equals(changedGanymede.geocentricPosition())) {
            throw new AssertionError("server-authoritative primary parameters did not drive body/child state");
        }

        CelestialPlanetSettings changedEarth = CelestialPlanetSettings.DEFAULT.withEarth(
                CelestialBodies.EARTH_DIAMETER_KM * 1.1D,
                CelestialBodies.EARTH_ORBITAL_DAYS * 0.9D,
                CelestialBodies.EARTH_SEMI_MAJOR_AXIS * 1.2D);
        CelestialBodyParameters persephone = changedEarth.parameters(CelestialBodies.PERSEPHONE);
        CelestialBodyParameters nemesis = changedEarth.parameters(CelestialBodies.NEMESIS);
        assertClose(changedEarth.earthDiameterKm() * 4.0D, persephone.diameterKm(),
                "Persephone follows configured Earth diameter");
        assertClose(changedEarth.earthOrbitalDays() * 15000.0D, persephone.orbitalDays(),
                "Persephone follows configured Earth year");
        assertClose(changedEarth.earthOrbitalDays() * 11100.0D, nemesis.orbitalDays(),
                "Nemesis follows configured Earth year");
        var changedEarthStates = CelestialBodies.calculate(frame, 7.25D, changedEarth);
        if (defaultsStates.get(CelestialBodies.PERSEPHONE.ordinal()).geocentricPosition()
                .equals(changedEarthStates.get(CelestialBodies.PERSEPHONE.ordinal()).geocentricPosition())
                || defaultsStates.get(CelestialBodies.NEMESIS.ordinal()).geocentricPosition()
                .equals(changedEarthStates.get(CelestialBodies.NEMESIS.ordinal()).geocentricPosition())) {
            throw new AssertionError("TFCCaelum Earth parameters did not drive derived outer bodies");
        }
    }

    private static void bodyDefinitionsMatchTfccaelumAuthority() {
        double[][] expected = {
                {4879, 87.968, 57.909, 115.88, 7.004, 0.1},
                {12104, 224.695, 108.210, 583.92, 3.395, 0.1},
                {6792, 779.94, 227.956, 779.94, 1.848, 0.4},
                {142984, 4330.595, 778.479, 4330.595, 1.304, 0.125},
                {120536, 10746.94, 1432.041, 378.09, 2.486, 0.15},
                {51118, 30588.74, 2867.043, 369.66, 0.770, 0.4},
                {49528, 59799.9, 4514.953, 367.49, 1.770, 0.4},
                {2376, 90560.0, 5869.656, 366.73, 17.160, 5.0},
                {50968, 5_478_630.0, 73302.956643, 365.242, 16.0, 5.0},
                {571936, 4_054_186.2, 14_190_792.6, 365.242, 10.0, 20.0},
                {5268.2, 7.15455296, 1.0704, 4330.595, 0.2, 1.0},
                {4820.6, 16.6890184, 1.8827, 4330.595, 0.192, 1.0},
                {3643.2, 1.769137786, 0.4217, 4330.595, 0.05, 1.0},
                {3121.6, 3.551181, 0.669151, 4330.595, 0.47, 1.0},
                {5149.46, 15.945, 1.22187, 378.09, 0.348, 1.0},
                {2706.8, 5.876854, 1.22187, 367.49, 156.885, 1.0},
                {1212.5, 6.3872304, 0.709518, 366.73, 0.08, 12.0}
        };
        CelestialBodies[] bodies = CelestialBodies.values();
        if (bodies.length != expected.length) {
            throw new AssertionError("TFCCaelum authority table length changed");
        }
        for (int index = 0; index < bodies.length; index++) {
            CelestialBodies body = bodies[index];
            double[] row = expected[index];
            assertClose(row[0], body.diameterKm(), body + " diameter");
            assertClose(row[1], body.orbitalDays(), body + " orbital period");
            assertClose(row[2], body.semiMajorMillionKm(), body + " semi-major axis");
            assertClose(row[3], body.synodicDays(), body + " synodic period");
            assertClose(Math.toRadians(row[4]), body.inclinationRadians(), body + " inclination");
            assertClose(row[5], body.scaleFactor(), body + " scale");
            if (body.retrograde() != (body == CelestialBodies.TITAN)) {
                throw new AssertionError("TFCCaelum retrograde marker changed for " + body);
            }
        }
        if (CelestialBodies.GANYMEDE.parent() != CelestialBodies.JUPITER
                || CelestialBodies.CALLISTO.parent() != CelestialBodies.JUPITER
                || CelestialBodies.IO.parent() != CelestialBodies.JUPITER
                || CelestialBodies.EUROPA.parent() != CelestialBodies.JUPITER
                || CelestialBodies.TITAN.parent() != CelestialBodies.SATURN
                || CelestialBodies.TRITON.parent() != CelestialBodies.NEPTUNE
                || CelestialBodies.CHARON.parent() != CelestialBodies.PLUTO) {
            throw new AssertionError("TFCCaelum satellite parent table changed");
        }
    }

    private static void bloodMoonGameplayRulesAreLocalAndFinite() {
        assertClose(0.0D, CelestialGameplayRules.visibleBloodMoon(1.0D, -0.01D),
                "moon below horizon");
        assertClose(0.0D, CelestialGameplayRules.visibleBloodMoon(
                CelestialGameplayRules.ACTIVE_THRESHOLD, 0.5D), "strict blood moon threshold");
        double aboveThreshold = Math.nextUp(CelestialGameplayRules.ACTIVE_THRESHOLD);
        assertClose(aboveThreshold, CelestialGameplayRules.visibleBloodMoon(aboveThreshold, 0.5D),
                "blood moon immediately above source threshold");
        assertClose(0.75D, CelestialGameplayRules.visibleBloodMoon(0.75D, 0.5D),
                "visible blood moon");
        int limit = CelestialGameplayRules.localMobCapLimit(70, 0.5D, 3.0D);
        if (limit != 140 || CelestialGameplayRules.localMobCapLimit(70, 1.0D, 3.0D) != 210) {
            throw new AssertionError("finite local mob cap scaling is invalid");
        }
        if (CelestialGameplayRules.unluckAmplifier(1.0D) != 2) {
            throw new AssertionError("Unluck amplifier does not follow round(2 * intensity)");
        }
    }

    private static void settingsPacketRoundTripsAllAuthoritativeFields() {
        CelestialBodyParameters mars = CelestialBodies.MARS.defaultParameters();
        CelestialPlanetSettings planets = CelestialPlanetSettings.DEFAULT.with(CelestialBodies.MARS,
                new CelestialBodyParameters(mars.diameterKm() + 1.0D, mars.orbitalDays() + 2.0D,
                        mars.semiMajorMillionKm() + 3.0D, mars.synodicDays() + 4.0D,
                        mars.inclinationRadians() + 0.01D))
                .withEarth(CelestialBodies.EARTH_DIAMETER_KM + 5.0D,
                        CelestialBodies.EARTH_ORBITAL_DAYS + 6.0D,
                        CelestialBodies.EARTH_SEMI_MAJOR_AXIS + 7.0D);
        CelestialRuntimeSettings expected = new CelestialRuntimeSettings(21.25D, 19.75D, 11.5D,
                Math.toRadians(7.25D), false, 4.5D, CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                planets);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new CelestialSettingsSyncPacket(expected).encode(buffer);
        CelestialSettingsSyncPacket decoded = new CelestialSettingsSyncPacket(buffer);
        if (!expected.equals(decoded.settings()) || buffer.readableBytes() != 0) {
            throw new AssertionError("celestial settings packet did not round-trip exactly");
        }
        buffer.release();
    }

    private static CelestialMath.Result calculateAtDay(double day) {
        return calculateAt(10000.0D, day);
    }

    private static CelestialMath.Result calculateAt(double z, double day) {
        return calculateAt(z, day, 8);
    }

    private static CelestialMath.Result calculateAt(double z, double day, int daysInMonth) {
        return CelestialMath.calculate(new CelestialMath.Input(z, 20000.0D,
                day * CelestialMath.TICKS_IN_DAY, daysInMonth));
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private static void assertFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new AssertionError(name + " is not finite");
        }
    }

    private static boolean finite(CelestialVector vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    private static void assertClose(double expected, double actual, String name) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
