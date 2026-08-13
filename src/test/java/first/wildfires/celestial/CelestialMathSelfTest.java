package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.network.CelestialSettingsSyncPacket;
import first.wildfires.tfc.calendar.CalendarEventWindowScanner;
import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Random;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

/** Plain-Java regression checks for the unified celestial model. */
public final class CelestialMathSelfTest {

    private static final double EPSILON = 1.0E-6D;

    private CelestialMathSelfTest() {
    }

    public static void main(String[] args) {
        latitudeGridMatchesTfcPolesAndEquators();
        latitudePeriodRepeatsWithoutChangingGeocentricEclipses();
        regionalSolarEclipseBandsAreSeasonalAndLatitudeBound();
        eclipsePredictionsReuseRegionalAndLunarGeometry();
        planetariumTimelinePreservesSameDayDistinctEclipses();
        configurableMonthLengthPreservesDaysAndScalesYears();
        solarFrameIsFiniteAtPoles();
        equinoxSolsticeAndPolarDayAreDistinct();
        apparentSolarTimeIsContinuousAtPolarCircleTransitions();
        seasonalGridIsNorthSouthSymmetric();
        directEventJumpSearchHorizonsAreBounded();
        directEventJumpFindsEveryDeterministicEvent();
        solarDirectionsMatchTfcReferenceMath();
        synodicCycleReturnsToFullMoon();
        supermoonRequiresFullMoonPerigeeAndLocalNight();
        quarterPhaseDebugEventsRequireLocalNight();
        moonPhaseCellsUseThreeDimensionalSeparation();
        moonPhaseAndDirectionAreContinuousAcrossCycle();
        anomalisticCycleIsIndependent();
        nodalCycleIsIndependent();
        lunarPeriodPresetsAreExplicit();
        eclipseDiscGeometryIsBounded();
        squarePixelDiscProjectionMatchesRenderedGeometry();
        lunarUmbraUsesEqualSquarePixelGeometry();
        lunarEclipseRegionUsesNonlinearTerrestrialShadowLatitude();
        realGeometryProducesSolarAndLunarEclipses();
        eclipseEventWindowsStartAtFirstGeometricContact();
        renderedPixelEclipseWindowOutlastsPhysicalDiscWindow();
        eclipseWindowsRemainContiguousAndTickContinuous();
        renderedSolarOverlapAlwaysMatchesAuthority();
        eventTargetsUseTheUnifiedVisibleGeometry();
        authoritativeVisualScaleDrivesPixelCoverageOnly();
        orbitalProjectionIsThreeDimensionalAndDeterministic();
        bodyDefinitionsMatchTfccaelumAuthority();
        satelliteReferencePlanesMatchJplElements();
        configuredPrimaryBodySettingsDriveUnifiedOrbits();
        allSeventeenBodiesAreFiniteHierarchicalAndDeterministic();
        creationEphemerisIsRandomPersistentAndNonAligned();
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
                Math.toRadians(4.75D), true, 2.5D, 0.8D, 1.15D,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
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
                        settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
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

    private static void latitudePeriodRepeatsWithoutChangingGeocentricEclipses() {
        double scale = 20000.0D;
        double period = 4.0D * scale;
        double calendarTicks = 1234567.25D;
        for (double z : new double[]{-50000.0D, -10000.0D, 0.0D, 10000.0D, 30000.0D, 49999.5D}) {
            CelestialMath.Result first = CelestialMath.calculate(new CelestialMath.Input(
                    z, scale, calendarTicks, 8));
            CelestialMath.Result repeated = CelestialMath.calculate(new CelestialMath.Input(
                    z + period, scale, calendarTicks, 8));
            assertClose(first.latitude(), repeated.latitude(), "Z latitude period at " + z);
            if (CelestialMath.angle(first.sunDirection(), repeated.sunDirection()) > EPSILON
                    || CelestialMath.angle(first.moonDirection(), repeated.moonDirection()) > EPSILON) {
                throw new AssertionError("local sky directions changed across one Z period at " + z);
            }
            assertClose(first.solarEclipse(), repeated.solarEclipse(),
                    "geocentric solar eclipse Z period at " + z);
            assertClose(first.lunarEclipse(), repeated.lunarEclipse(),
                    "geocentric lunar eclipse Z period at " + z);
        }

        CelestialMath.Result latitudeA = CelestialMath.calculate(new CelestialMath.Input(
                -10000.0D, scale, calendarTicks, 8));
        CelestialMath.Result latitudeB = CelestialMath.calculate(new CelestialMath.Input(
                10000.0D, scale, calendarTicks, 8));
        assertClose(latitudeA.lunarEclipse(), latitudeB.lunarEclipse(),
                "lunar coverage remains geocentric across latitude");

        double seam = -2.5D * scale;
        double left = CelestialMath.latitude(seam - 0.001D, scale);
        double right = CelestialMath.latitude(seam + 0.001D, scale);
        if (!Double.isFinite(left) || !Double.isFinite(right) || Math.abs(left - right) > 1.0E-6D) {
            throw new AssertionError("Z latitude period seam is discontinuous: " + left + " -> " + right);
        }
        CelestialMath.Result huge = CelestialMath.calculate(new CelestialMath.Input(
                1.0E15D, scale, calendarTicks, 8));
        if (!Double.isFinite(huge.latitude()) || !Double.isFinite(huge.solarEclipse())
                || !Double.isFinite(huge.lunarEclipse())) {
            throw new AssertionError("large Z coordinate produced a non-finite eclipse state");
        }
    }

    private static void regionalSolarEclipseBandsAreSeasonalAndLatitudeBound() {
        double yearDays = CelestialMath.daysInYear(8);
        double cycleDays = yearDays * CelestialMath.NODAL_YEARS;
        long conjunctions = (long) Math.ceil(cycleDays / CelestialMath.SYNODIC_DAYS);
        int eclipseCount = 0;
        boolean foundUmbra = false;
        boolean foundPolarPartialOnly = false;
        boolean foundLatitudeDifference = false;
        SolarEclipseRegion.Event polarPartialEvent = SolarEclipseRegion.Event.NONE;
        double polarPartialLatitude = 0.0D;
        for (long index = 0; index < conjunctions; index++) {
            SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, yearDays,
                    CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                    CelestialMath.LUNAR_INCLINATION);
            if (!event.intersectsWorld()) {
                continue;
            }
            CelestialMath.Result center = calculateAtDay(event.conjunctionDay());
            double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                    CelestialDiscGeometry.sunBodyHalfSize(CelestialDiscGeometry.DEFAULT_SUN_SCALE));
            double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                    CelestialDiscGeometry.moonBodyHalfSize(CelestialDiscGeometry.DEFAULT_MOON_SCALE,
                            center.moonDistance()), CelestialDiscGeometry.PIXEL_COVER_RADIUS);
            double maximum = 0.0D;
            double polarMaximum = 0.0D;
            for (int latitudeIndex = 0; latitudeIndex <= 360; latitudeIndex++) {
                double latitude = -Math.PI * 0.5D + Math.PI * latitudeIndex / 360.0D;
                double coverage = SolarEclipseRegion.maximumCoverageAtLatitude(event, latitude,
                        sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS);
                maximum = Math.max(maximum, coverage);
                if (latitudeIndex == 0 || latitudeIndex == 360) {
                    polarMaximum = Math.max(polarMaximum, coverage);
                }
            }
            if (maximum > 0.0D) {
                eclipseCount++;
                foundUmbra |= maximum >= 0.8D;
                boolean polarOnly = Math.abs(event.greatestLatitude()) > Math.PI * 0.5D
                        && polarMaximum > 0.0D && maximum < 0.5D;
                foundPolarPartialOnly |= polarOnly;
                if (polarOnly && !polarPartialEvent.valid()) {
                    polarPartialEvent = event;
                    polarPartialLatitude = Math.copySign(Math.PI * 0.5D, event.greatestLatitude());
                }
                double equator = SolarEclipseRegion.maximumCoverageAtLatitude(event, 0.0D,
                        sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS);
                double north = SolarEclipseRegion.maximumCoverageAtLatitude(event, Math.toRadians(60.0D),
                        sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS);
                foundLatitudeDifference |= Math.abs(equator - north) > 0.1D;
            }
        }
        double perYear = eclipseCount / CelestialMath.NODAL_YEARS;
        if (!(perYear >= 2.0D && perYear <= 5.0D) || eclipseCount >= conjunctions
                || !foundUmbra || !foundPolarPartialOnly || !foundLatitudeDifference) {
            throw new AssertionError("regional eclipse tuning is invalid: count=" + eclipseCount
                    + "/" + conjunctions + ", perYear=" + perYear + ", umbra=" + foundUmbra
                    + ", polarPartial=" + foundPolarPartialOnly
                    + ", latitudeDifference=" + foundLatitudeDifference);
        }
        int auditedYears = 1_000;
        int[] globalEclipsesPerYear = new int[auditedYears];
        long auditedConjunctions = (long) Math.ceil(auditedYears * yearDays
                / CelestialMath.SYNODIC_DAYS);
        for (long index = 0L; index < auditedConjunctions; index++) {
            SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, yearDays,
                    CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                    CelestialMath.LUNAR_INCLINATION);
            int year = (int) Math.floor(event.conjunctionDay() / yearDays);
            if (event.intersectsWorld() && year >= 0 && year < auditedYears) {
                globalEclipsesPerYear[year]++;
            }
        }
        for (int year = 0; year < auditedYears; year++) {
            int count = globalEclipsesPerYear[year];
            if (count < 2 || count > 5) {
                throw new AssertionError("global solar eclipse count escaped real annual bounds in year "
                        + year + ": " + count);
            }
        }
        SolarEclipseRegion.Event tilted = SolarEclipseRegion.eventAt(0L, yearDays,
                CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION);
        assertClose(Math.toRadians(6.0D), tilted.trackLatitude(tilted.conjunctionDay() + 0.25D)
                - tilted.trackLatitude(tilted.conjunctionDay() - 0.25D), "inclined latitude-time track");

        double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(CelestialDiscGeometry.DEFAULT_SUN_SCALE));
        CelestialMath.Result conjunction = calculateAtDay(polarPartialEvent.conjunctionDay());
        double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.moonBodyHalfSize(CelestialDiscGeometry.DEFAULT_MOON_SCALE,
                        conjunction.moonDistance()), CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        double bestDay = polarPartialEvent.conjunctionDay();
        double bestPolarCoverage = 0.0D;
        for (int sample = 0; sample <= 256; sample++) {
            double day = polarPartialEvent.conjunctionDay() - 0.35D + sample * 0.7D / 256.0D;
            double coverage = SolarEclipseRegion.coverageAt(polarPartialEvent, day,
                    polarPartialLatitude, sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS);
            if (coverage > bestPolarCoverage) {
                bestPolarCoverage = coverage;
                bestDay = day;
            }
        }
        double polarZ = EclipsePredictionService.zForLatitude(polarPartialLatitude, 20_000.0D);
        CelestialMath.Result polarFrame = calculateAt(polarZ, bestDay);
        CelestialMath.Result equatorFrame = calculateAt(10_000.0D, bestDay);
        if (!(bestPolarCoverage > 0.0D) || !(polarFrame.solarEclipse() > 0.0D)
                || polarFrame.solarEclipseRegion().zone()
                != first.wildfires.api.celestial.SolarEclipseZone.PARTIAL
                || !polarFrame.solarEclipseRegion().activeSomewhere()
                || !(polarFrame.solarEclipseRegion().globalCoverage() > 0.0D)
                || equatorFrame.solarEclipse() != 0.0D
                || !equatorFrame.solarEclipseRegion().activeSomewhere()
                || !(equatorFrame.solarEclipseRegion().globalCoverage() > 0.0D)) {
            throw new AssertionError("a polar-only partial contact stopped counting as a global eclipse: polar="
                    + polarFrame + ", equator=" + equatorFrame);
        }

        long polarConjunctionIndex = polarPartialEvent.conjunctionIndex();
        EclipsePredictionService.SolarPrediction polarPrediction = EclipsePredictionService.predictTimeline(
                        (polarPartialEvent.conjunctionDay() - 0.5D) * CelestialMath.TICKS_IN_DAY,
                        8, 20_000.0D, 0.0D, polarZ, CelestialRuntimeSettings.DEFAULT, 1.0D)
                .solar().stream().filter(candidate -> candidate.conjunctionIndex()
                        == polarConjunctionIndex).findFirst()
                .orElse(EclipsePredictionService.SolarPrediction.NONE);
        boolean polarContactIsDaylight = polarFrame.solarElevation() > 0.0D;
        if (!polarPrediction.present()
                || polarPrediction.conjunctionIndex() != polarPartialEvent.conjunctionIndex()
                || !(polarPrediction.globalMaximumCoverage() > 0.0D)
                || !polarPrediction.partialBand().present()
                || polarContactIsDaylight != (polarPrediction.observerMaximumCoverage() > 0.0D)) {
            throw new AssertionError("planetarium global/local polar eclipse qualification diverged: "
                    + polarPrediction + ", daylight=" + polarContactIsDaylight);
        }
        double conjunctionTicks = polarPartialEvent.conjunctionDay() * CelestialMath.TICKS_IN_DAY;
        if (polarPrediction.present()
                && polarPrediction.conjunctionIndex() == polarPartialEvent.conjunctionIndex()
                && Math.abs(polarPrediction.greatestCalendarTicks() - conjunctionTicks) < 1.0D) {
            throw new AssertionError("polar grazing eclipse maximum was still hard-coded to conjunction time: "
                    + polarPrediction);
        }
    }

    private static void eclipsePredictionsReuseRegionalAndLunarGeometry() {
        EclipsePredictionService.Predictions prediction = EclipsePredictionService.predict(
                0.0D, 8, 20_000.0D, 10_000.0D, CelestialRuntimeSettings.DEFAULT);
        if (!prediction.solar().present() || !prediction.lunar().present()
                || prediction.solar().globalMaximumCoverage() <= 0.0D
                || !prediction.solar().partialBand().present()
                || prediction.solar().endCalendarTicks() < 0.0D
                || prediction.lunar().maximumCoverage() <= 0.0D
                || prediction.lunar().kind() == EclipsePredictionService.LunarEclipseKind.NONE
                || !Double.isFinite(prediction.lunar().shadowCenterX())
                || !Double.isFinite(prediction.lunar().shadowCenterY())) {
            throw new AssertionError("planetarium eclipse prediction is incomplete: " + prediction);
        }
        if (EclipsePredictionService.lunarKind(0.0D, 0.1D)
                != EclipsePredictionService.LunarEclipseKind.PENUMBRAL
                || EclipsePredictionService.lunarKind(0.1D, 0.2D)
                != EclipsePredictionService.LunarEclipseKind.PARTIAL
                || EclipsePredictionService.lunarKind(0.9D, 1.0D)
                != EclipsePredictionService.LunarEclipseKind.TOTAL
                || EclipsePredictionService.lunarKind(0.0D, 0.0D)
                != EclipsePredictionService.LunarEclipseKind.NONE) {
            throw new AssertionError("planetarium lunar eclipse classification diverged from coverage");
        }
        EclipsePredictionService.SolarPrediction solar = prediction.solar();
        double greatestDay = solar.greatestCalendarTicks() / CelestialMath.TICKS_IN_DAY;
        assertClose(solar.globalMaximumCoverage(),
                SolarEclipseRegion.maximumCoverageAtTime(solar.event(), greatestDay,
                        solar.sunHalfTangent(), solar.moonHalfTangent(), solar.synodicDays()),
                "planetarium reported greatest-eclipse world time");
        double oneTickDay = 1.0D / CelestialMath.TICKS_IN_DAY;
        double before = SolarEclipseRegion.maximumCoverageAtTime(solar.event(),
                solar.startCalendarTicks() / CelestialMath.TICKS_IN_DAY - oneTickDay,
                solar.sunHalfTangent(), solar.moonHalfTangent(), solar.synodicDays());
        double entered = SolarEclipseRegion.maximumCoverageAtTime(solar.event(),
                solar.startCalendarTicks() / CelestialMath.TICKS_IN_DAY + oneTickDay,
                solar.sunHalfTangent(), solar.moonHalfTangent(), solar.synodicDays());
        double leaving = SolarEclipseRegion.maximumCoverageAtTime(solar.event(),
                solar.endCalendarTicks() / CelestialMath.TICKS_IN_DAY - oneTickDay,
                solar.sunHalfTangent(), solar.moonHalfTangent(), solar.synodicDays());
        double after = SolarEclipseRegion.maximumCoverageAtTime(solar.event(),
                solar.endCalendarTicks() / CelestialMath.TICKS_IN_DAY + oneTickDay,
                solar.sunHalfTangent(), solar.moonHalfTangent(), solar.synodicDays());
        if (before > 0.0D || !(entered > 0.0D) || !(leaving > 0.0D) || after > 0.0D
                || solar.greatestCalendarTicks() < solar.startCalendarTicks()
                || solar.greatestCalendarTicks() > solar.endCalendarTicks()) {
            throw new AssertionError("planetarium solar contact/max times diverged from geometry: "
                    + before + ", " + entered + ", " + leaving + ", " + after + "; " + solar);
        }
        double latitude = Math.toRadians(37.5D);
        double z = EclipsePredictionService.zForLatitude(latitude, 20_000.0D);
        assertClose(latitude, CelestialMath.latitude(z, 20_000.0D), "planetarium latitude inverse");

        // Regression for the live-world 911-day local-visible gap: the 400-day planetarium axis
        // is global and must keep showing regional eclipses even when this observer gets 0%.
        double liveLatitude = Math.toRadians(-46.0D);
        double liveZ = EclipsePredictionService.zForLatitude(liveLatitude, 20_000.0D);
        EclipsePredictionService.Timeline globalTimeline = EclipsePredictionService.predictTimeline(
                652_097_136.0D, 8, 20_000.0D, 0.0D, liveZ,
                CelestialRuntimeSettings.DEFAULT, 400.0D);
        if (globalTimeline.solar().isEmpty()
                || globalTimeline.solar().stream().noneMatch(candidate ->
                candidate.observerMaximumCoverage() == 0.0D)) {
            throw new AssertionError("planetarium still hid global eclipses during a local visibility gap: "
                    + globalTimeline.solar());
        }
        double maximumGlobalGapDays = 0.0D;
        for (int index = 1; index < globalTimeline.solar().size(); index++) {
            maximumGlobalGapDays = Math.max(maximumGlobalGapDays,
                    (globalTimeline.solar().get(index).greatestCalendarTicks()
                            - globalTimeline.solar().get(index - 1).greatestCalendarTicks())
                            / CelestialMath.TICKS_IN_DAY);
        }
        if (globalTimeline.solar().size() < 4
                || maximumGlobalGapDays > CelestialMath.SYNODIC_DAYS * 6.0D) {
            throw new AssertionError("global solar prediction retained an abnormal long gap: count="
                    + globalTimeline.solar().size() + ", maximumGap=" + maximumGlobalGapDays);
        }
    }

    private static void planetariumTimelinePreservesSameDayDistinctEclipses() {
        CelestialRuntimeSettings rapidCustomCycle = new CelestialRuntimeSettings(
                0.2D, 0.19D, CelestialMath.NODAL_YEARS, 0.0D,
                true, 3.0D, CelestialDiscGeometry.DEFAULT_SUN_SCALE,
                CelestialDiscGeometry.DEFAULT_MOON_SCALE,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        EclipsePredictionService.Timeline timeline = EclipsePredictionService.predictTimeline(
                0.0D, 8, 20_000.0D, 0.0D, 10_000.0D,
                rapidCustomCycle, 2.0D);
        EclipsePredictionService.SolarPrediction first = null;
        EclipsePredictionService.SolarPrediction second = null;
        for (int left = 0; left < timeline.solar().size(); left++) {
            long leftDay = (long) Math.floor(timeline.solar().get(left).greatestCalendarTicks()
                    / CelestialMath.TICKS_IN_DAY);
            for (int right = left + 1; right < timeline.solar().size(); right++) {
                long rightDay = (long) Math.floor(timeline.solar().get(right).greatestCalendarTicks()
                        / CelestialMath.TICKS_IN_DAY);
                if (leftDay == rightDay) {
                    first = timeline.solar().get(left);
                    second = timeline.solar().get(right);
                    break;
                }
            }
            if (first != null) {
                break;
            }
        }
        if (first == null || second == null
                || first.conjunctionIndex() == second.conjunctionIndex()
                || first.greatestCalendarTicks() == second.greatestCalendarTicks()
                || !(first.globalMaximumCoverage() > 0.0D)
                || !(second.globalMaximumCoverage() > 0.0D)) {
            throw new AssertionError("planetarium timeline did not preserve two distinct same-day eclipses: "
                    + timeline.solar());
        }
        for (EclipsePredictionService.SolarPrediction prediction : new EclipsePredictionService.SolarPrediction[]{
                first, second}) {
            double greatestDay = prediction.greatestCalendarTicks() / CelestialMath.TICKS_IN_DAY;
            double coverage = SolarEclipseRegion.maximumCoverageAtTime(prediction.event(), greatestDay,
                    prediction.sunHalfTangent(), prediction.moonHalfTangent(), prediction.synodicDays());
            if (!(coverage > 0.0D)) {
                throw new AssertionError("same-day eclipse maximum does not lie inside its geometric window: "
                        + prediction);
            }
        }

        double paddingTicks = 1.0D;
        double singleStart = first.startCalendarTicks() - paddingTicks;
        double singleEnd = first.endCalendarTicks() + paddingTicks;
        EclipsePredictionService.Timeline single = EclipsePredictionService.predictTimeline(
                singleStart, 8, 20_000.0D, 0.0D, 10_000.0D, rapidCustomCycle,
                (singleEnd - singleStart) / CelestialMath.TICKS_IN_DAY);
        if (single.solar().size() != 1
                || single.solar().get(0).conjunctionIndex() != first.conjunctionIndex()
                || single.solar().get(0).greatestCalendarTicks() != first.greatestCalendarTicks()) {
            throw new AssertionError("single eclipse window lost its unique maximum-time marker: "
                    + single.solar());
        }
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

    private static void apparentSolarTimeIsContinuousAtPolarCircleTransitions() {
        assertClose(0.0D, CelestialMath.sunBasedDayTimeFromElevations(
                0.0D, 0.0D, 0.0D, Math.toRadians(30.0D)), "polar-day grazing sunrise");
        assertClose(12000.0D, CelestialMath.sunBasedDayTimeFromElevations(
                0.999999D, 0.0D, 0.0D, Math.toRadians(30.0D)), "polar-day grazing sunset");
        assertClose(24000.0D, CelestialMath.sunBasedDayTimeFromElevations(
                0.499999D, 0.0D, Math.toRadians(-30.0D), 0.0D), "polar-night grazing sunrise");
        assertClose(12000.0D, CelestialMath.sunBasedDayTimeFromElevations(
                0.5D, 0.0D, Math.toRadians(-30.0D), 0.0D), "polar-night grazing sunset");

        for (double minimum : new double[]{Math.toRadians(-30.0D), -1.0E-12D, 0.0D,
                Math.toRadians(5.0D)}) {
            for (double maximum : new double[]{Math.toRadians(5.0D), Math.toRadians(30.0D)}) {
                if (maximum < minimum) continue;
                double previousLight = Double.NaN;
                for (int step = 0; step <= 24000; step++) {
                    double fraction = step / 24000.0D;
                    double elevation = minimum + (maximum - minimum)
                            * (0.5D - 0.5D * Math.cos(fraction * CelestialMath.TAU));
                    double apparent = CelestialMath.sunBasedDayTimeFromElevations(
                            fraction, elevation, minimum, maximum);
                    assertFinite(apparent, "polar-transition apparent time");
                    if (apparent < 0.0D || apparent > 24000.0D) {
                        throw new AssertionError("polar-transition apparent time escaped one day: " + apparent);
                    }
                    double light = visualSkyBrightness(apparent);
                    if (Double.isFinite(previousLight) && Math.abs(light - previousLight) > 0.01D) {
                        throw new AssertionError("polar-transition visual light jumped at minimum=" + minimum
                                + ", maximum=" + maximum + ", step=" + step + ": "
                                + previousLight + " -> " + light);
                    }
                    previousLight = light;
                }
            }
        }
    }

    private static double visualSkyBrightness(double apparentTicks) {
        double phase = positiveModulo(apparentTicks / 24000.0D - 0.25D, 1.0D);
        double eased = 0.5D - Math.cos(phase * Math.PI) * 0.5D;
        double angle = (phase * 2.0D + eased) / 3.0D;
        return Math.max(0.0D, Math.min(1.0D,
                Math.cos(angle * CelestialMath.TAU) * 2.0D + 0.5D));
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

    private static void directEventJumpSearchHorizonsAreBounded() {
        CelestialRuntimeSettings settings = CelestialRuntimeSettings.DEFAULT;
        for (CelestialEventType event : CelestialEventType.values()) {
            long days = TfcCalendarEventAcceleration.skipSearchDays(event, 8, settings);
            if (event == CelestialEventType.RAINBOW) {
                if (days != 0L) {
                    throw new AssertionError("weather-dependent rainbow received a direct search horizon");
                }
            } else if (days < 2L || days > 8192L) {
                throw new AssertionError("direct event search horizon escaped its bound: "
                        + event + " -> " + days);
            }
        }
        if (TfcCalendarEventAcceleration.skipSearchDays(CelestialEventType.NOON, 8, settings) != 2L
                || TfcCalendarEventAcceleration.skipSearchDays(
                CelestialEventType.SUNRISE, 8, settings) != 98L
                || TfcCalendarEventAcceleration.skipSearchDays(
                CelestialEventType.SOLAR_ECLIPSE, 8, settings) != 1820L
                || TfcCalendarEventAcceleration.skipSearchDays(
                CelestialEventType.AURORA, 8, settings) != 8192L
                || TfcCalendarEventAcceleration.skipSearchDays(
                CelestialEventType.LUNAR_ECLIPSE, Integer.MAX_VALUE, settings) != 8192L) {
            throw new AssertionError("direct event search periods no longer follow daily/yearly/nodal bounds");
        }
    }

    private static void directEventJumpFindsEveryDeterministicEvent() {
        int daysInMonth = 8;
        double hemisphereScale = 20_000.0D;
        CelestialRuntimeSettings settings = CelestialRuntimeSettings.DEFAULT;
        for (CelestialEventType event : CelestialEventType.values()) {
            if (event == CelestialEventType.RAINBOW) {
                continue;
            }
            double observerZ = event == CelestialEventType.AURORA ? -6_000.0D : 0.0D;
            long startTick = 0L;
            CelestialMath.Result initial = eventResult(observerZ, hemisphereScale, startTick,
                    daysInMonth, settings);
            long searchDays = TfcCalendarEventAcceleration.skipSearchDays(
                    event, daysInMonth, settings);
            CalendarEventWindowScanner.ScanResult scan = CalendarEventWindowScanner.scan(
                    startTick, searchDays * (long) CelestialMath.TICKS_IN_DAY,
                    event.matches(initial, startTick, null),
                    tick -> event.matches(eventResult(observerZ, hemisphereScale, tick,
                            daysInMonth, settings), tick, null));
            if (!scan.found() || !event.matches(eventResult(observerZ, hemisphereScale,
                    scan.reachedTick(), daysInMonth, settings), scan.reachedTick(), null)) {
                throw new AssertionError("direct jump search did not find " + event
                        + " within " + searchDays + " TFC days");
            }
        }
    }

    private static CelestialMath.Result eventResult(double observerZ, double hemisphereScale,
                                                     long tick, int daysInMonth,
                                                     CelestialRuntimeSettings settings) {
        return CelestialMath.calculate(new CelestialMath.Input(observerZ, hemisphereScale, tick,
                daysInMonth, settings.resolvedSynodicDays(daysInMonth),
                settings.resolvedAnomalisticDays(daysInMonth), settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
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

    private static void supermoonRequiresFullMoonPerigeeAndLocalNight() {
        CelestialMath.Result fullPerigeeMidnight = calculateAtDay(0.0D);
        CelestialMath.Result sameEventNearEdge = calculateAtDay(0.49D);
        CelestialMath.Result outsideFullMoonDay = calculateAtDay(0.51D);
        CelestialMath.Result laterPerigeeWithoutFullMoon = calculateAtDay(CelestialMath.ANOMALISTIC_DAYS);
        if (fullPerigeeMidnight.supermoon() < 0.999D
                || sameEventNearEdge.supermoon() < 0.999D
                || outsideFullMoonDay.supermoon() != 0.0D
                || laterPerigeeWithoutFullMoon.supermoon() != 0.0D
                || !CelestialEventType.SUPERMOON.matches(fullPerigeeMidnight, 0L, null)) {
            throw new AssertionError("supermoon no longer requires the one-day full-Moon/perigee event");
        }
        CelestialMath.Result localNoon = calculateAtDay(0.5D);
        if (CelestialEventType.SUPERMOON.matches(localNoon,
                Math.round(0.5D * CelestialMath.TICKS_IN_DAY), null)) {
            throw new AssertionError("supermoon event succeeded outside the local lunar night");
        }
    }

    private static void quarterPhaseDebugEventsRequireLocalNight() {
        boolean sawDayFirstQuarter = false;
        boolean sawNightFirstQuarter = false;
        boolean sawDayLastQuarter = false;
        boolean sawNightLastQuarter = false;
        double endDay = CelestialMath.SYNODIC_DAYS * 9.0D;
        for (double day = 0.0D; day <= endDay; day += 1.0D / 240.0D) {
            CelestialMath.Result result = calculateAtDay(day);
            boolean firstCandidate = result.moonPhase() == 2
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && result.moonElevation() > 0.0D;
            boolean lastCandidate = result.moonPhase() == 6
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && result.moonElevation() > 0.0D;
            if (firstCandidate) {
                boolean matches = CelestialEventType.FIRST_QUARTER.matches(result,
                        Math.round(day * CelestialMath.TICKS_IN_DAY),
                        CelestialEventRules.RainSample.DRY);
                if (result.solarElevation() > 0.0D) {
                    sawDayFirstQuarter = true;
                    if (matches) {
                        throw new AssertionError("first-quarter debug event succeeded in daylight");
                    }
                } else {
                    sawNightFirstQuarter = true;
                    if (!matches) {
                        throw new AssertionError("first-quarter debug event rejected local night");
                    }
                }
            }
            if (lastCandidate) {
                boolean matches = CelestialEventType.LAST_QUARTER.matches(result,
                        Math.round(day * CelestialMath.TICKS_IN_DAY),
                        CelestialEventRules.RainSample.DRY);
                if (result.solarElevation() > 0.0D) {
                    sawDayLastQuarter = true;
                    if (matches) {
                        throw new AssertionError("last-quarter debug event succeeded in daylight");
                    }
                } else {
                    sawNightLastQuarter = true;
                    if (!matches) {
                        throw new AssertionError("last-quarter debug event rejected local night");
                    }
                }
            }
        }
        if (!sawDayFirstQuarter || !sawNightFirstQuarter
                || !sawDayLastQuarter || !sawNightLastQuarter) {
            throw new AssertionError("quarter-phase debug scan missed a light-state branch: "
                    + sawDayFirstQuarter + "/" + sawNightFirstQuarter + "/"
                    + sawDayLastQuarter + "/" + sawNightLastQuarter);
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
                CelestialDiscGeometry.DEFAULT_SUN_SCALE, CelestialDiscGeometry.DEFAULT_MOON_SCALE,
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

    private static void squarePixelDiscProjectionMatchesRenderedGeometry() {
        CelestialVector sun = new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector north = new CelestialVector(0.0D, 1.0D, 0.0D);
        CelestialDiscGeometry.Basis basis = CelestialDiscGeometry.stableBasis(sun, north);
        double half = 0.05D;
        assertClose(1.0D, CelestialDiscGeometry.squareCoverage(sun, sun, north, half, half),
                "coincident rendered pixel squares");
        CelestialVector halfOffset = sun.add(basis.right().scale(half)).normalized();
        double halfOverlap = CelestialDiscGeometry.squareCoverage(sun, halfOffset, north, half, half);
        if (Math.abs(halfOverlap - 0.5D) > 2.0E-4D) {
            throw new AssertionError("perspective half-width rendered pixel overlap changed: " + halfOverlap);
        }
        CelestialVector separated = sun.add(basis.right().scale(half * 2.2D)).normalized();
        assertClose(0.0D, CelestialDiscGeometry.squareCoverage(sun, separated, north, half, half),
                "separated rendered pixel squares");
        double rotatedPartial = CelestialDiscGeometry.squareCoverage(sun,
                sun.add(basis.right().scale(0.04D)).add(basis.up().scale(0.03D)).normalized(),
                new CelestialVector(0.4D, 1.0D, 0.2D).normalized(), half, half * 1.2D);
        if (!(rotatedPartial > 0.0D && rotatedPartial < 1.0D)) {
            throw new AssertionError("rotated perspective square coverage escaped (0,1): " + rotatedPartial);
        }
        if (CelestialDiscGeometry.squareCoverage(sun, sun, north, Double.NaN, half) != 0.0D
                || CelestialDiscGeometry.squareCoverage(CelestialVector.ZERO, sun, north, half, half) != 0.0D) {
            throw new AssertionError("invalid rendered pixel geometry did not fail closed");
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

    private static void lunarUmbraUsesEqualSquarePixelGeometry() {
        CelestialVector moon = new CelestialVector(1.0D, 0.0D, 0.0D);
        CelestialVector north = new CelestialVector(0.0D, 1.0D, 0.0D);
        CelestialDiscGeometry.Basis basis = CelestialDiscGeometry.stableBasis(moon, north);
        double half = 0.05D;
        assertClose(1.0D, CelestialDiscGeometry.alignedSquareCoverage(moon, moon, north, half, half),
                "centered equal-size square lunar umbra");
        CelestialVector halfOffset = moon.add(basis.right().scale(half)).normalized();
        double halfCoverage = CelestialDiscGeometry.alignedSquareCoverage(moon, halfOffset, north, half, half);
        if (Math.abs(halfCoverage - 0.5D) > 2.0E-4D) {
            throw new AssertionError("equal-size lunar umbra lost half-width coverage: " + halfCoverage);
        }
        CelestialVector penumbraOnly = moon.add(basis.right().scale(half * 2.1D)).normalized();
        assertClose(0.0D, CelestialDiscGeometry.alignedSquareCoverage(moon, penumbraOnly, north, half, half),
                "separated square lunar umbra");
        double expandedPenumbra = CelestialDiscGeometry.alignedSquareCoverage(moon, penumbraOnly, north, half,
                half * (1.0D + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH));
        if (!(expandedPenumbra > 0.0D && expandedPenumbra < 1.0D)) {
            throw new AssertionError("one-pixel lunar penumbra did not bridge the umbra contact: "
                    + expandedPenumbra);
        }
    }

    private static void lunarEclipseRegionUsesNonlinearTerrestrialShadowLatitude() {
        LunarCycleStats reference = scanLunarEclipseCycle(8);
        LunarCycleStats longMonth = scanLunarEclipseCycle(15);
        for (LunarCycleStats stats : new LunarCycleStats[]{reference, longMonth}) {
            double frequency = stats.eclipses() / CelestialMath.NODAL_YEARS;
            double totalAmongUmbral = stats.total() / (double) (stats.partial() + stats.total());
            if (stats.eclipses() != 44 || Math.abs(frequency - 2.38D) > 0.03D
                    || stats.penumbral() < 1 || stats.partial() <= stats.total()
                    || stats.penumbral() + stats.partial() <= stats.total()
                    || Math.abs(totalAmongUmbral - 0.453D) > 0.07D
                    || stats.minimumAnnual() < 0 || stats.maximumAnnual() > 4) {
                throw new AssertionError("calendar-normalized lunar eclipse tuning changed: " + stats
                        + ", frequency=" + frequency + ", totalAmongUmbral=" + totalAmongUmbral);
            }
        }
        assertClose(0.0D, LunarEclipseRegion.effectiveLatitudeRadians(0.0D),
                "zero lunar latitude remains centered");
        assertClose(-LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(0.73D)),
                LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(-0.73D)),
                "nonlinear lunar latitude is odd");
        assertClose(Math.toRadians(0.1D) * LunarEclipseRegion.CENTER_LATITUDE_MULTIPLIER,
                LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(0.1D)),
                "node-adjacent lunar latitude uses center scale");
        assertClose(Math.toRadians(2.0D) * LunarEclipseRegion.OUTER_LATITUDE_MULTIPLIER,
                LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(2.0D)),
                "outer lunar latitude uses rejection scale");
        double opportunityRatio = Math.pow(180.0D / LunarEclipseRegion.REFERENCE_YEAR_DAYS,
                LunarEclipseRegion.ANNUAL_OPPORTUNITY_EXPONENT);
        assertClose(LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(0.1D))
                        * opportunityRatio,
                LunarEclipseRegion.effectiveLatitudeRadians(Math.toRadians(0.1D), 180.0D,
                        CelestialMath.SYNODIC_DAYS),
                "configured month length normalizes annual lunar eclipse opportunities");
        double derivativeStep = 1.0E-7D;
        double innerDerivative = (LunarEclipseRegion.effectiveLatitudeRadians(
                LunarEclipseRegion.INNER_TRANSITION_LATITUDE + derivativeStep)
                - LunarEclipseRegion.effectiveLatitudeRadians(
                LunarEclipseRegion.INNER_TRANSITION_LATITUDE - derivativeStep))
                / (2.0D * derivativeStep);
        double outerDerivative = (LunarEclipseRegion.effectiveLatitudeRadians(
                LunarEclipseRegion.OUTER_TRANSITION_LATITUDE + derivativeStep)
                - LunarEclipseRegion.effectiveLatitudeRadians(
                LunarEclipseRegion.OUTER_TRANSITION_LATITUDE - derivativeStep))
                / (2.0D * derivativeStep);
        if (Math.abs(innerDerivative - LunarEclipseRegion.CENTER_LATITUDE_MULTIPLIER) > 1.0E-3D
                || Math.abs(outerDerivative - LunarEclipseRegion.OUTER_LATITUDE_MULTIPLIER) > 1.0E-3D) {
            throw new AssertionError("nonlinear lunar latitude lost C1 transition slopes: "
                    + innerDerivative + "/" + outerDerivative);
        }
        double previous = 0.0D;
        for (int sample = 1; sample <= 514; sample++) {
            double effective = LunarEclipseRegion.effectiveLatitudeRadians(
                    Math.toRadians(sample / 100.0D));
            if (!(effective > previous)) {
                throw new AssertionError("nonlinear lunar latitude was not strictly monotone at " + sample);
            }
            previous = effective;
        }
        assertClose(Math.toRadians(5.14D), CelestialMath.LUNAR_INCLINATION,
                "physical lunar inclination remains real");
        for (long index = 0L; index < 16L; index++) {
            LunarEclipseRegion.Event extreme = LunarEclipseRegion.eventAt(index,
                    LunarEclipseRegion.REFERENCE_YEAR_DAYS,
                    CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                    Math.PI * 0.5D);
            if (!extreme.valid() || !Double.isFinite(extreme.effectiveLatitudeRadians())
                    || Math.abs(extreme.effectiveLatitudeRadians()) >= Math.PI * 0.5D) {
                throw new AssertionError("extreme configured lunar inclination escaped finite projection");
            }
        }
    }

    private static LunarCycleStats scanLunarEclipseCycle(int daysInMonth) {
        double daysInYear = CelestialMath.daysInYear(daysInMonth);
        long fullMoons = (long) Math.ceil(daysInYear * CelestialMath.NODAL_YEARS
                / CelestialMath.SYNODIC_DAYS);
        int ordinary = 0;
        int penumbral = 0;
        int partial = 0;
        int total = 0;
        int[] eclipsesPerYear = new int[(int) Math.floor(CelestialMath.NODAL_YEARS)];
        for (long index = 0L; index < fullMoons; index++) {
            double day = index * CelestialMath.SYNODIC_DAYS;
            CelestialMath.Result center = calculateAt(10_000.0D, day, daysInMonth);
            var state = center.lunarEclipseRegion();
            if (state.fullMoonIndex() != index) {
                throw new AssertionError("lunar eclipse event was not stable for full Moon " + index);
            }
            assertClose(LunarEclipseRegion.effectiveLatitudeRadians(state.lunarLatitudeRadians(),
                            daysInYear, CelestialMath.SYNODIC_DAYS),
                    state.effectiveLatitudeRadians(), "effective lunar shadow latitude");
            double maximumUmbra = 0.0D;
            double maximumPenumbra = 0.0D;
            for (int sample = 0; sample <= 320; sample++) {
                double sampleDay = day - 0.75D + 1.5D * sample / 320.0D;
                CelestialMath.Result result = calculateAt(10_000.0D, sampleDay, daysInMonth);
                var sampleState = result.lunarEclipseRegion();
                if (sampleState.fullMoonIndex() != index) {
                    throw new AssertionError("lunar eclipse projection changed event inside full-Moon window");
                }
                assertClose(sampleState.umbraCoverage(), result.lunarEclipse(),
                        "lunar eclipse/result umbra authority");
                assertClose(sampleState.umbraCoverage(), result.bloodMoon(),
                        "lunar eclipse/blood-moon umbra authority");
                CelestialDiscGeometry.AlignedSquare shadow = new CelestialDiscGeometry.AlignedSquare(
                        sampleState.shadowCenterX(), sampleState.shadowCenterY(),
                        sampleState.shadowRadius(), true);
                assertClose(CelestialDiscGeometry.alignedSquareCoverage(shadow),
                        sampleState.umbraCoverage(), "projected lunar umbra coverage");
                CelestialDiscGeometry.AlignedSquare expanded = new CelestialDiscGeometry.AlignedSquare(
                        sampleState.shadowCenterX(), sampleState.shadowCenterY(),
                        sampleState.shadowRadius()
                                + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH, true);
                assertClose(CelestialDiscGeometry.alignedSquareCoverage(expanded),
                        sampleState.penumbraCoverage(), "projected lunar penumbra coverage");
                maximumUmbra = Math.max(maximumUmbra, sampleState.umbraCoverage());
                maximumPenumbra = Math.max(maximumPenumbra, sampleState.penumbraCoverage());
            }
            if (Math.abs(state.lunarLatitudeRadians()) >= LunarEclipseRegion.INNER_TRANSITION_LATITUDE
                    && maximumPenumbra > 0.0D && Math.signum(state.lunarLatitudeRadians())
                    == Math.signum(state.shadowCenterY())) {
                throw new AssertionError("north/south lunar shadow grazing sign was lost at " + index);
            }
            if (maximumPenumbra == 0.0D) ordinary++;
            else if (maximumUmbra == 0.0D) penumbral++;
            else if (maximumUmbra < 0.9D) partial++;
            else total++;
            int year = (int) Math.floor(day / daysInYear);
            if (maximumPenumbra > 0.0D && year >= 0 && year < eclipsesPerYear.length) {
                eclipsesPerYear[year]++;
            }
        }
        return new LunarCycleStats(daysInMonth, (int) fullMoons, ordinary, penumbral, partial, total,
                java.util.Arrays.stream(eclipsesPerYear).min().orElseThrow(),
                java.util.Arrays.stream(eclipsesPerYear).max().orElseThrow());
    }

    private record LunarCycleStats(int daysInMonth, int fullMoons, int ordinary,
                                   int penumbral, int partial, int total,
                                   int minimumAnnual, int maximumAnnual) {
        int eclipses() {
            return penumbral + partial + total;
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

    private static void eclipseEventWindowsStartAtFirstGeometricContact() {
        double firstRepresentableCoverage = Math.nextUp(0.0D);
        if (!CelestialEventType.visibleEclipseContact(firstRepresentableCoverage, 0.25D)
                || CelestialEventType.visibleEclipseContact(0.0D, 0.25D)
                || CelestialEventType.visibleEclipseContact(-firstRepresentableCoverage, 0.25D)
                || CelestialEventType.visibleEclipseContact(0.5D, 0.0D)
                || CelestialEventType.visibleEclipseContact(Double.NaN, 0.25D)) {
            throw new AssertionError("eclipse debug window no longer follows exact visible disc contact");
        }
    }

    private static void renderedPixelEclipseWindowOutlastsPhysicalDiscWindow() {
        double centerDay = strongestSolarEclipseDay();
        long centerTick = Math.round(centerDay * CelestialMath.TICKS_IN_DAY);
        long firstPixel = Long.MAX_VALUE;
        long lastPixel = Long.MIN_VALUE;
        long firstPhysical = Long.MAX_VALUE;
        long lastPhysical = Long.MIN_VALUE;
        for (long tick = centerTick - 12_000L; tick <= centerTick + 12_000L; tick++) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    10_000.0D, 20_000.0D, tick, 8));
            if (result.solarEclipse() > 0.0D) {
                firstPixel = Math.min(firstPixel, tick);
                lastPixel = Math.max(lastPixel, tick);
            }
            if (result.physicalSolarEclipse() > 0.0D) {
                firstPhysical = Math.min(firstPhysical, tick);
                lastPhysical = Math.max(lastPhysical, tick);
            }
        }
        long pixelTicks = lastPixel - firstPixel + 1L;
        long physicalTicks = lastPhysical - firstPhysical + 1L;
        if (firstPixel == Long.MAX_VALUE || firstPhysical == Long.MAX_VALUE
                || firstPixel >= firstPhysical || lastPixel <= lastPhysical
                || pixelTicks < physicalTicks * 3L) {
            throw new AssertionError("rendered pixel eclipse window did not contain and substantially outlast "
                    + "the physical diagnostic window: pixel=" + pixelTicks + ", physical=" + physicalTicks);
        }
    }

    private static void eclipseWindowsRemainContiguousAndTickContinuous() {
        assertContinuousEclipseWindow("solar", strongestSolarEclipseDay(), true);
        assertContinuousEclipseWindow("lunar", strongestLunarEclipseDay(), false);
    }

    private static void renderedSolarOverlapAlwaysMatchesAuthority() {
        int daysInMonth = 8;
        double daysInYear = CelestialMath.daysInYear(daysInMonth);
        int conjunctions = (int) Math.ceil(daysInYear * CelestialMath.NODAL_YEARS
                / CelestialMath.SYNODIC_DAYS);
        int visibleWindows = 0;
        for (double moonScale : new double[]{0.75D, 1.0D, 1.5D}) {
            for (long index = 0L; index < conjunctions; index++) {
                SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, daysInYear,
                        CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                        CelestialMath.LUNAR_INCLINATION);
                long centerTick = Math.round(event.conjunctionDay() * CelestialMath.TICKS_IN_DAY);
                for (double latitude : new double[]{-Math.PI * 0.5D, -Math.PI / 3.0D,
                        0.0D, Math.PI / 3.0D, Math.PI * 0.5D}) {
                    double z = EclipsePredictionService.zForLatitude(latitude, 20_000.0D);
                    boolean active = false;
                    int entries = 0;
                    for (long tick = centerTick - 18_000L; tick <= centerTick + 18_000L; tick += 20L) {
                        CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                                z, 20_000.0D, tick, daysInMonth, CelestialMath.SYNODIC_DAYS,
                                CelestialMath.ANOMALISTIC_DAYS, CelestialMath.NODAL_YEARS,
                                CelestialMath.LUNAR_INCLINATION,
                                CelestialDiscGeometry.DEFAULT_SUN_SCALE, moonScale));
                        double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                                CelestialDiscGeometry.sunBodyHalfSize(
                                        CelestialDiscGeometry.DEFAULT_SUN_SCALE));
                        double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                                CelestialDiscGeometry.moonBodyHalfSize(moonScale,
                                        result.moonDistance()), CelestialDiscGeometry.PIXEL_COVER_RADIUS);
                        double renderedCoverage = CelestialDiscGeometry.squareCoverage(
                                result.sunDirection(), result.moonDirection(), result.celestialNorth(),
                                sunHalf, moonHalf);
                        if ((renderedCoverage > 0.0D) != (result.solarEclipse() > 0.0D)
                                || Math.abs(renderedCoverage - result.solarEclipse()) > 1.0E-12D) {
                            throw new AssertionError("rendered/authoritative solar coverage diverged at "
                                    + "conjunction " + index + ": " + renderedCoverage + " != "
                                    + result.solarEclipse());
                        }
                        boolean nowActive = result.solarEclipse() > 0.0D;
                        if (nowActive && !active) {
                            entries++;
                        }
                        if (nowActive && (!result.solarEclipseRegion().activeSomewhere()
                                || !(result.solarEclipseRegion().globalCoverage() > 0.0D))) {
                            throw new AssertionError("local rendered contact lacked global eclipse state at "
                                    + tick + ": " + result);
                        }
                        active = nowActive;
                    }
                    if (!event.intersectsWorld() && entries != 0) {
                        throw new AssertionError("non-eclipse conjunction still crossed the rendered Sun: "
                                + index + " at latitude " + latitude);
                    }
                    if (entries > 1) {
                        throw new AssertionError("one conjunction folded into multiple rendered eclipse windows: "
                                + index + " at latitude " + latitude + " (" + entries + ")");
                    }
                    visibleWindows += entries;
                }
            }
        }
        if (visibleWindows == 0) {
            throw new AssertionError("full nodal-cycle audit found no rendered eclipse windows");
        }
    }

    private static void assertContinuousEclipseWindow(String name, double centerDay, boolean solar) {
        long centerTick = Math.round(centerDay * CelestialMath.TICKS_IN_DAY);
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        double previous = 0.0D;
        double maximum = 0.0D;
        double maximumStep = 0.0D;
        boolean entered = false;
        boolean exited = false;
        for (long tick = centerTick - 15_000L; tick <= centerTick + 15_000L; tick++) {
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    10_000.0D, 20_000.0D, tick, 8));
            double coverage = solar ? result.solarEclipse()
                    : result.lunarEclipseRegion().penumbraCoverage();
            if (!solar) {
                assertClose(result.lunarEclipse(), result.bloodMoon(),
                        "lunar/blood square coverage at " + tick);
            }
            if (!Double.isFinite(coverage) || coverage < 0.0D || coverage > 1.0D) {
                throw new AssertionError(name + " eclipse coverage escaped [0,1] at " + tick + ": " + coverage);
            }
            maximumStep = Math.max(maximumStep, Math.abs(coverage - previous));
            maximum = Math.max(maximum, coverage);
            if (coverage > 0.0D) {
                if (exited) {
                    throw new AssertionError(name + " eclipse window reopened after complete separation at " + tick);
                }
                entered = true;
                first = Math.min(first, tick);
                last = tick;
            } else if (entered) {
                exited = true;
            }
            previous = coverage;
        }
        if (!entered || !exited || first == Long.MAX_VALUE || last == Long.MIN_VALUE || maximum < 0.25D) {
            throw new AssertionError(name + " eclipse scan did not contain a complete strong event");
        }
        if (coverageAtTick(first - 1L, solar) != 0.0D || coverageAtTick(first, solar) <= 0.0D
                || coverageAtTick(last, solar) <= 0.0D || coverageAtTick(last + 1L, solar) != 0.0D) {
            throw new AssertionError(name + " eclipse no longer begins at first positive contact and ends at separation");
        }
        if (maximumStep > 0.01D) {
            throw new AssertionError(name + " eclipse coverage jumped by " + maximumStep
                    + " in one TFC calendar tick");
        }
    }

    private static double coverageAtTick(long tick, boolean solar) {
        CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                10_000.0D, 20_000.0D, tick, 8));
        return solar ? result.solarEclipse() : result.lunarEclipseRegion().penumbraCoverage();
    }

    private static void authoritativeVisualScaleDrivesPixelCoverageOnly() {
        double day = strongestSolarEclipseDay();
        CelestialMath.Input smallPixels = new CelestialMath.Input(10_000.0D, 20_000.0D,
                day * CelestialMath.TICKS_IN_DAY, 8, CelestialMath.SYNODIC_DAYS,
                CelestialMath.ANOMALISTIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION, 0.5D, 0.5D);
        CelestialMath.Input largeMoon = new CelestialMath.Input(10_000.0D, 20_000.0D,
                day * CelestialMath.TICKS_IN_DAY, 8, CelestialMath.SYNODIC_DAYS,
                CelestialMath.ANOMALISTIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION, 0.5D, 2.0D);
        CelestialMath.Result small = CelestialMath.calculate(smallPixels);
        CelestialMath.Result large = CelestialMath.calculate(largeMoon);
        assertClose(small.physicalSolarEclipse(), large.physicalSolarEclipse(),
                "physical solar eclipse diagnostic");
        if (!(large.solarEclipse() > small.solarEclipse())) {
            throw new AssertionError("authoritative rendered Moon scale did not enlarge pixel coverage: "
                    + small.solarEclipse() + " -> " + large.solarEclipse());
        }
    }

    private static double strongestPhysicalSolarEclipseDay() {
        double cycleDays = CelestialMath.daysInYear(8) * CelestialMath.NODAL_YEARS;
        double bestDay = 0.0D;
        double bestCoverage = 0.0D;
        for (double day = 0.0D; day < cycleDays; day += 0.01D) {
            double coverage = calculateAtDay(day).physicalSolarEclipse();
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestDay = day;
            }
        }
        if (bestCoverage <= 0.25D) {
            throw new AssertionError("no strong physical solar eclipse found for window comparison");
        }
        return bestDay;
    }

    private static double strongestSolarEclipseDay() {
        double cycleDays = CelestialMath.daysInYear(8) * CelestialMath.NODAL_YEARS;
        double bestDay = 0.0D;
        double bestCoverage = 0.0D;
        for (double day = 0.0D; day < cycleDays; day += 0.01D) {
            double coverage = calculateAtDay(day).solarEclipse();
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestDay = day;
            }
        }
        if (bestCoverage <= 0.25D) {
            throw new AssertionError("no strong regional solar eclipse found at the reference latitude");
        }
        return bestDay;
    }

    private static double strongestLunarEclipseDay() {
        double cycleDays = CelestialMath.daysInYear(8) * CelestialMath.NODAL_YEARS;
        double bestDay = 0.0D;
        double bestCoverage = 0.0D;
        for (double day = 0.0D; day < cycleDays; day += 0.01D) {
            double coverage = calculateAtDay(day).lunarEclipse();
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestDay = day;
            }
        }
        if (bestCoverage <= 0.25D) {
            throw new AssertionError("no strong square lunar eclipse found for continuity audit");
        }
        return bestDay;
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
                {4879, 87.9691, 57.909, 115.88, 7.005, 0.1},
                {12104, 224.701, 108.210, 583.92, 3.3946, 0.1},
                {6792, 686.980, 227.956, 779.94, 1.850, 0.4},
                {142984, 4332.59, 778.479, 398.88, 1.303, 0.125},
                {120536, 10759.22, 1432.041, 378.09, 2.485, 0.15},
                {51118, 30688.5, 2867.043, 369.66, 0.773, 0.4},
                {49528, 60182.0, 4514.953, 367.49, 1.770, 0.4},
                {2376, 90560.0, 5869.656, 366.73, 17.160, 5.0},
                {50968, 5_478_630.0, 73302.956643, 365.242, 16.0, 5.0},
                {571936, 4_054_186.2, 14_190_792.6, 365.242, 10.0, 20.0},
                {5268.2, 7.15455296, 1.0704, 4330.595, 0.2, 1.0},
                {4820.6, 16.6890184, 1.8827, 4330.595, 0.30, 1.0},
                {3643.2, 1.769137786, 0.4217, 4330.595, 0.0, 1.0},
                {3121.6, 3.551181, 0.669151, 4330.595, 0.50, 1.0},
                {5149.46, 15.945, 1.22187, 378.09, 0.30, 1.0},
                {2706.8, 5.876854, 0.354759, 367.49, 157.3, 1.0},
                {1212.5, 6.3872304, 0.019596, 366.73, 0.0, 12.0}
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
            if (body.retrograde()) {
                throw new AssertionError("retrograde sense must be encoded by the JPL plane normal for " + body);
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
        assertClose(686.980D, CelestialBodies.MARS.orbitalDays(),
                "Mars uses sidereal period instead of the old synodic-period duplication");
        assertClose(0.354759D, CelestialBodies.TRITON.semiMajorMillionKm(),
                "Triton physical semi-major axis");
        assertClose(0.019596D, CelestialBodies.CHARON.semiMajorMillionKm(),
                "Charon physical semi-major axis");
    }

    private static void satelliteReferencePlanesMatchJplElements() {
        if (CelestialBodies.IO.orbitReferenceFrame()
                != CelestialBodies.OrbitReferenceFrame.J2000_EQUATORIAL_POLE
                || CelestialBodies.TITAN.orbitReferenceFrame()
                != CelestialBodies.OrbitReferenceFrame.J2000_EQUATORIAL_POLE
                || CelestialBodies.TRITON.orbitReferenceFrame()
                != CelestialBodies.OrbitReferenceFrame.J2000_EQUATORIAL_POLE
                || CelestialBodies.CHARON.orbitReferenceFrame()
                != CelestialBodies.OrbitReferenceFrame.PARENT_EQUATOR) {
            throw new AssertionError("satellite source reference frames no longer match JPL");
        }
        assertClose(Math.toRadians(2.17D), angleFromEclipticNorth(
                CelestialBodies.IO.orbitalReferenceNormalEcliptic()), 0.03D,
                "Io JPL Laplace plane against ecliptic");
        assertClose(Math.toRadians(27.99D), angleFromEclipticNorth(
                CelestialBodies.TITAN.orbitalReferenceNormalEcliptic()), 0.03D,
                "Titan JPL Laplace plane against ecliptic");
        if (!(angleFromEclipticNorth(CelestialBodies.TRITON.orbitalPlaneNormalEcliptic())
                > Math.PI * 0.5D)) {
            throw new AssertionError("Triton's 157.3 degree JPL orbit lost its retrograde plane");
        }
        assertClose(0.0D, angle(CelestialBodies.CHARON.orbitalPlaneNormalEcliptic(),
                CelestialBodies.PLUTO.spinAxisEcliptic()), 1.0E-6D,
                "Charon orbit follows Pluto's equator");
        CelestialVector moonPole = new CelestialVector(
                Math.sin(Math.toRadians(1.543D)) * Math.cos(Math.toRadians(215.08D)),
                Math.sin(Math.toRadians(1.543D)) * Math.sin(Math.toRadians(215.08D)),
                Math.cos(Math.toRadians(1.543D))).normalized();
        assertClose(Math.toRadians(1.543D), angleFromEclipticNorth(moonPole), 1.0E-9D,
                "lunar Cassini-state spin pole obliquity");
    }

    private static double angleFromEclipticNorth(CelestialVector vector) {
        return angle(vector, new CelestialVector(0.0D, 0.0D, 1.0D));
    }

    private static double angle(CelestialVector first, CelestialVector second) {
        return Math.acos(Math.max(-1.0D, Math.min(1.0D,
                first.normalized().dot(second.normalized()))));
    }

    private static void bloodMoonGameplayRulesAreLocalAndFinite() {
        assertClose(0.0D, CelestialGameplayRules.visibleBloodMoon(1.0D, -0.01D, -0.5D),
                "moon below horizon");
        assertClose(0.0D, CelestialGameplayRules.visibleBloodMoon(
                CelestialGameplayRules.ACTIVE_THRESHOLD, 0.5D, -0.5D), "strict blood moon threshold");
        double aboveThreshold = Math.nextUp(CelestialGameplayRules.ACTIVE_THRESHOLD);
        assertClose(aboveThreshold, CelestialGameplayRules.visibleBloodMoon(
                aboveThreshold, 0.5D, -0.5D),
                "blood moon immediately above source threshold");
        assertClose(0.85D, CelestialGameplayRules.visibleBloodMoon(0.85D, 0.5D, -0.5D),
                "visible blood moon");
        assertClose(0.0D, CelestialGameplayRules.visibleBloodMoon(1.0D, 0.5D, 0.01D),
                "blood moon rejected during local day");
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
        CelestialOrbitalPhases phases = CelestialOrbitalPhases.random(new Random(0x5EEDL), planets);
        CelestialRuntimeSettings expected = new CelestialRuntimeSettings(21.25D, 19.75D, 11.5D,
                Math.toRadians(7.25D), false, 4.5D, 0.81D, 1.17D,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                planets, phases);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new CelestialSettingsSyncPacket(expected).encode(buffer);
        CelestialSettingsSyncPacket decoded = new CelestialSettingsSyncPacket(buffer);
        if (!expected.equals(decoded.settings()) || buffer.readableBytes() != 0) {
            throw new AssertionError("celestial settings packet did not round-trip exactly");
        }
        buffer.release();
    }

    private static void creationEphemerisIsRandomPersistentAndNonAligned() {
        CelestialPlanetSettings settings = CelestialPlanetSettings.DEFAULT;
        CelestialOrbitalPhases first = CelestialOrbitalPhases.random(new Random(123456789L), settings);
        CelestialOrbitalPhases repeated = CelestialOrbitalPhases.random(new Random(123456789L), settings);
        CelestialOrbitalPhases otherWorld = CelestialOrbitalPhases.random(new Random(987654321L), settings);
        if (!first.equals(repeated)) {
            throw new AssertionError("same creation entropy did not reproduce the same test ephemeris");
        }
        if (first.equals(otherWorld)) {
            throw new AssertionError("different creation entropy produced an identical ephemeris");
        }
        int heliocentricCount = 1;
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() == null) {
                heliocentricCount++;
            }
        }
        double guaranteedGap = 0.6D / heliocentricCount;
        if (first.minimumInitialHeliocentricGap(settings) + 1.0E-12D < guaranteedGap) {
            throw new AssertionError("creation ephemeris permits an initial planetary alignment: "
                    + first.minimumInitialHeliocentricGap(settings));
        }

        net.minecraft.nbt.CompoundTag encoded = new CelestialEphemerisSavedDataForTest(first).save();
        CelestialEphemerisSavedData decoded = CelestialEphemerisSavedData.load(encoded);
        if (!first.equals(decoded.phases())) {
            throw new AssertionError("creation ephemeris did not persist exactly");
        }

        double day = 123.25D;
        CelestialMath.Result frame = calculateAtDay(day);
        double years = day / CelestialMath.daysInYear(8);
        var positions = CelestialBodies.calculate(frame, years, settings, first);
        var later = CelestialBodies.calculate(frame, years + 1.0D, settings, first);
        if (positions.equals(later)) {
            throw new AssertionError("persisted creation phases stopped normal orbital motion");
        }
    }

    /** Uses the public NBT contract without exposing a production mutation hook. */
    private record CelestialEphemerisSavedDataForTest(CelestialOrbitalPhases phases) {
        net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("data_version", CelestialEphemerisSavedData.DATA_VERSION);
            net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
            for (var id : CelestialOrbitalPhases.orderedIds()) {
                net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
                entry.putString("body", id.toString());
                entry.putDouble("turns", phases.turns(id));
                entries.add(entry);
            }
            tag.put("phases", entries);
            return tag;
        }
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

    private static void assertClose(double expected, double actual, double tolerance, String name) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
