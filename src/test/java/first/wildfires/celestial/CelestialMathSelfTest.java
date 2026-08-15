package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialProvider;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.network.CelestialSettingsSyncPacket;
import first.wildfires.tfc.calendar.CalendarEventWindowScanner;
import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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
        anomalousTimelineMatchesFullExceptionalEvents();
        configurableMonthLengthPreservesDaysAndScalesYears();
        solarFrameIsFiniteAtPoles();
        equinoxSolsticeAndPolarDayAreDistinct();
        apparentSolarTimeIsContinuousAtPolarCircleTransitions();
        optimizedSolarTimeMatchesLegacyBits();
        preparedSolarElevationMatchesLegacyBits();
        allocationFreeMathEntriesMatchFullCalculationBits();
        preparedEclipseEventCacheMatchesLegacyBits();
        cachedCoordinateTransformsMatchLegacyBits();
        optimizedAngleMatchesLegacyBits();
        seasonalGridIsNorthSouthSymmetric();
        directEventJumpSearchHorizonsAreBounded();
        directEventJumpFindsEveryDeterministicEvent();
        eventSpecificAccelerationSamplesMatchFullEventView();
        solarDirectionsMatchTfcReferenceMath();
        synodicCycleReturnsToFullMoon();
        supermoonRequiresFullMoonPerigeeAndLocalNight();
        optimizedSupermoonCacheMatchesLegacyBits();
        quarterPhaseDebugEventsRequireLocalNight();
        moonPhaseCellsUseThreeDimensionalSeparation();
        moonPhaseAndDirectionAreContinuousAcrossCycle();
        anomalisticCycleIsIndependent();
        nodalCycleIsIndependent();
        lunarPeriodPresetsAreExplicit();
        eclipseDiscGeometryIsBounded();
        squarePixelDiscProjectionMatchesRenderedGeometry();
        optimizedStableBasisMatchesLegacyBits();
        optimizedSquareCoverageMatchesLegacyBits();
        optimizedLunarProjectionMatchesLegacyBits();
        lunarUmbraUsesEqualSquarePixelGeometry();
        lunarEclipseRegionUsesNonlinearTerrestrialShadowLatitude();
        optimizedLunarOpportunityScaleMatchesLegacyBits();
        realGeometryProducesSolarAndLunarEclipses();
        eclipseEventWindowsStartAtFirstGeometricContact();
        renderedPixelEclipseWindowOutlastsPhysicalDiscWindow();
        eclipseWindowsRemainContiguousAndTickContinuous();
        optimizedSolarSearchMatchesLegacyBits();
        renderedSolarOverlapAlwaysMatchesAuthority();
        eventTargetsUseTheUnifiedVisibleGeometry();
        authoritativeVisualScaleDrivesPixelCoverageOnly();
        orbitalProjectionIsThreeDimensionalAndDeterministic();
        bodyDefinitionsMatchTfccaelumAuthority();
        satelliteReferencePlanesMatchJplElements();
        configuredPrimaryBodySettingsDriveUnifiedOrbits();
        allSeventeenBodiesAreFiniteHierarchicalAndDeterministic();
        optimizedOrbitsAndPhasesMatchLegacyBits();
        creationEphemerisIsRandomPersistentAndNonAligned();
        combinedServerSettingsCacheIsExactAndCoherent();
        preparedRuntimePeriodsAreExactAndThreadIsolated();
        bloodMoonGameplayRulesAreLocalAndFinite();
        visibleBloodMoonSubsetMatchesFullDisplaySample();
        surfaceMonsterIdFilterMatchesLegacyTextComparison();
        overworldFrameContextIsExactlyEquivalentAtEveryLatitude();
        scaledMoonPositionMatchesLegacyBits();
        sunPositionUsesTheSharedEclipticOrbitFrame();
        fastProviderQueriesMatchFullStateExactly();
        defaultProviderFastQueriesPreserveOptionalSemantics();
        displayEventMaskMatchesLegacyStates();
        currentEventScannerMatchesLegacyExactly();
        tfeHemisphereMethodHandleIsStrictAndFinite();
        settingsPacketRoundTripsAllAuthoritativeFields();
        legacyCelestialModsAreRejectedExplicitly();
        System.out.println("CelestialMathSelfTest: all checks passed");
    }

    private static void preparedSolarElevationMatchesLegacyBits() {
        Random random = new Random(0x51A7E1E5L);
        double[] fixedLatitudes = {-Math.PI, -Math.PI * 0.5D, -Math.toRadians(66.56D),
                -1.0E-12D, 0.0D, 1.0E-12D, Math.toRadians(66.56D),
                Math.PI * 0.5D, Math.PI};
        double[] fixedDays = {-1.0E12D, -365.25D, -1.0E-12D, 0.0D, 0.5D,
                95.999999999D, 96.0D, 1.0E12D};
        double[] fixedYears = {1.0E-9D, 12.0D, 96.0D, 180.0D, 1.0E9D};
        for (double latitude : fixedLatitudes) {
            CelestialMath.SolarLatitudeContext preparedLatitude =
                    CelestialMath.prepareSolarLatitude(latitude);
            for (double day : fixedDays) {
                for (double year : fixedYears) {
                    assertPreparedSolarElevation(latitude, day, year, preparedLatitude,
                            "fixed prepared solar elevation");
                }
            }
        }
        for (int sample = 0; sample < 512; sample++) {
            double latitude = (random.nextDouble() * 4.0D - 2.0D) * Math.PI;
            double day = (random.nextDouble() * 2.0D - 1.0D) * 1.0E8D
                    + random.nextDouble();
            double year = Math.scalb(0.5D + random.nextDouble(), random.nextInt(40) - 15);
            assertPreparedSolarElevation(latitude, day, year,
                    CelestialMath.prepareSolarLatitude(latitude),
                    "random prepared solar elevation " + sample);
        }
        double[] invalidLatitudes = {Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY};
        double[] invalidDays = {Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY};
        double[] invalidYears = {Double.NaN, Double.NEGATIVE_INFINITY, -1.0D, 0.0D,
                Double.POSITIVE_INFINITY};
        for (double latitude : invalidLatitudes) {
            assertPreparedSolarElevation(latitude, 10.25D, 96.0D,
                    CelestialMath.prepareSolarLatitude(latitude), "invalid prepared latitude");
        }
        for (double day : invalidDays) {
            assertPreparedSolarElevation(0.25D, day, 96.0D,
                    CelestialMath.prepareSolarLatitude(0.25D), "invalid prepared day");
        }
        for (double year : invalidYears) {
            assertPreparedSolarElevation(0.25D, 10.25D, year,
                    CelestialMath.prepareSolarLatitude(0.25D), "invalid prepared year");
        }
    }

    private static void assertPreparedSolarElevation(
            double latitude, double calendarDays, double daysInYear,
            CelestialMath.SolarLatitudeContext preparedLatitude, String label) {
        double expected = legacySolarElevationAt(latitude, calendarDays, daysInYear);
        assertRawDouble(expected, CelestialMath.solarElevationAt(
                latitude, calendarDays, daysInYear), label + " public");
        assertRawDouble(expected, CelestialMath.solarElevationAt(
                preparedLatitude, calendarDays, daysInYear), label + " latitude");
        assertRawDouble(expected, CelestialMath.solarElevationAt(latitude,
                CelestialMath.prepareSolarTime(calendarDays, daysInYear)), label + " time");
    }

    private static double legacySolarElevationAt(double latitudeRadians, double calendarDays,
                                                 double daysInYear) {
        if (!Double.isFinite(latitudeRadians) || !Double.isFinite(calendarDays)
                || !Double.isFinite(daysInYear) || daysInYear <= 0.0D) {
            return Double.NaN;
        }
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double fractionOfDay = positiveModulo(calendarDays, 1.0D);
        double longitude = CelestialMath.TAU
                * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = CelestialMath.AXIAL_TILT * Math.sin(longitude);
        double hourAngle = CelestialMath.TAU * (0.5D - fractionOfDay);
        double cosineZenith = Math.sin(latitudeRadians) * Math.sin(declination)
                + Math.cos(latitudeRadians) * Math.cos(declination) * Math.cos(hourAngle);
        return Math.asin(testClamp(cosineZenith, -1.0D, 1.0D));
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
                assertRawDouble(Math.sin(expected.latitude()), actual.sineLatitude(),
                        "shared frame sine latitude at z=" + z);
                assertRawDouble(Math.cos(expected.latitude()), actual.cosineLatitude(),
                        "shared frame cosine latitude at z=" + z);
                assertRawDouble(Math.sin(expected.localSiderealAngle()), actual.sineSidereal(),
                        "shared frame sine sidereal at z=" + z);
                assertRawDouble(Math.cos(expected.localSiderealAngle()), actual.cosineSidereal(),
                        "shared frame cosine sidereal at z=" + z);
                CelestialMath.DisplayEventSample display = context.displayEventSampleAt(z);
                double expectedBloodMoon = CelestialGameplayRules.visibleBloodMoon(
                        display.bloodMoon(), display.moonElevation(), display.solarElevation());
                assertRawDouble(expectedBloodMoon, context.visibleBloodMoonAt(z),
                        "shared frame visible blood Moon at z=" + z);
            }
        }
    }

    private static void scaledMoonPositionMatchesLegacyBits() {
        double[][] fixed = {
                {0.0D, 0.0D, 0.0D, 0.0D},
                {-0.0D, 0.0D, -0.0D, 1.0D},
                {1.0D, -2.0D, 3.0D, 0.3844D},
                {-1.0E-300D, 1.0E-200D, -1.0E-100D, -2.0D},
                {Double.MAX_VALUE / 8.0D, -Double.MAX_VALUE / 16.0D,
                        Double.MAX_VALUE / 32.0D, 0.25D}
        };
        for (int index = 0; index < fixed.length; index++) {
            assertScaledMoonPosition(fixed[index][0], fixed[index][1], fixed[index][2],
                    fixed[index][3], "fixed scaled Moon position " + index);
        }
        Random random = new Random(0x4D00C00DL);
        for (int sample = 0; sample < 512; sample++) {
            assertScaledMoonPosition((random.nextDouble() - 0.5D) * 1.0E9D,
                    (random.nextDouble() - 0.5D) * 1.0E9D,
                    (random.nextDouble() - 0.5D) * 1.0E9D,
                    (random.nextDouble() - 0.5D) * 1.0E4D,
                    "random scaled Moon position " + sample);
        }
    }

    private static void assertScaledMoonPosition(double x, double y, double z, double scale,
                                                  String label) {
        CelestialVector source = new CelestialVector(x, y, z);
        CelestialVector ecliptic = new CelestialVector(source.x(),
                source.y() * CelestialMath.AXIAL_TILT_COS
                        + source.z() * CelestialMath.AXIAL_TILT_SIN,
                -source.y() * CelestialMath.AXIAL_TILT_SIN
                        + source.z() * CelestialMath.AXIAL_TILT_COS);
        CelestialVector expected = ecliptic.scale(scale);
        CelestialVector actual = OverworldCelestialProvider.equatorialToEclipticScaled(source, scale);
        assertVectorRaw(expected, actual, label);
    }

    private static void sunPositionUsesTheSharedEclipticOrbitFrame() {
        CelestialPlanetSettings planetSettings = CelestialRuntimeSettings.DEFAULT.planetSettings();
        double distance = planetSettings.earthSemiMajorMillionKm();
        double maximumRawEquatorialZ = 0.0D;
        for (int sample = 0; sample < 16; sample++) {
            double day = 96.0D * sample / 16.0D;
            CelestialMath.Result frame = calculateAtDay(day);
            CelestialVector position = OverworldCelestialProvider.solarEclipticPosition(
                    frame, distance);
            double longitude = frame.solarLongitude();
            assertClose(Math.cos(longitude) * distance, position.x(), 1.0E-12D,
                    "solar ecliptic x at sample " + sample);
            assertClose(Math.sin(longitude) * distance, position.y(), 1.0E-12D,
                    "solar ecliptic y at sample " + sample);
            assertClose(0.0D, position.z(), 0.0D,
                    "solar ecliptic latitude at sample " + sample);
            assertClose(distance, position.length(), 1.0E-12D,
                    "solar ephemeris distance at sample " + sample);
            double astronomicalDays = CelestialMath.calendarYears(
                    day * CelestialMath.TICKS_IN_DAY, 8) * planetSettings.earthOrbitalDays()
                    + (284.0D / 365.0D + 0.5D) * planetSettings.earthOrbitalDays();
            CelestialVector earth = CelestialMath.orbitalPosition(distance,
                    planetSettings.earthOrbitalDays(), 0.0D, false, astronomicalDays);
            assertClose(-earth.x(), position.x(), 1.0E-12D,
                    "Earth-to-Sun opposes heliocentric Earth x at sample " + sample);
            assertClose(-earth.y(), position.y(), 1.0E-12D,
                    "Earth-to-Sun opposes heliocentric Earth y at sample " + sample);
            assertClose(-earth.z(), position.z(), 1.0E-12D,
                    "Earth-to-Sun opposes heliocentric Earth z at sample " + sample);
            maximumRawEquatorialZ = Math.max(maximumRawEquatorialZ,
                    Math.abs(frame.sunGeocentric().z()));
        }
        if (!(maximumRawEquatorialZ > 0.1D)) {
            throw new AssertionError("solar regression samples did not exercise axial tilt");
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

    private static void anomalousTimelineMatchesFullExceptionalEvents() {
        CelestialRuntimeSettings rapidCustomCycle = new CelestialRuntimeSettings(
                0.2D, 0.19D, CelestialMath.NODAL_YEARS, 0.0D,
                true, 3.0D, CelestialDiscGeometry.DEFAULT_SUN_SCALE,
                CelestialDiscGeometry.DEFAULT_MOON_SCALE,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        double[][] inputs = {
                {0.0D, 8.0D, 20_000.0D, 0.0D, 10_000.0D, 64.0D},
                {652_097_136.0D, 8.0D, 20_000.0D, -12_345.5D, 27_500.0D, 48.0D},
                {-987_654_321.25D, 15.0D, -30_000.0D, 40_000.0D, -15_000.0D, 32.0D},
                {123_456.75D, 4.0D, 20_000.0D, 250.0D, 10_000.0D, 2.0D}
        };
        for (int index = 0; index < inputs.length; index++) {
            double[] input = inputs[index];
            CelestialRuntimeSettings settings = index == inputs.length - 1
                    ? rapidCustomCycle : CelestialRuntimeSettings.DEFAULT;
            EclipsePredictionService.Timeline full = EclipsePredictionService.predictTimeline(
                    input[0], (int) input[1], input[2], input[3], input[4], settings, input[5]);
            EclipsePredictionService.Timeline anomalous =
                    EclipsePredictionService.predictAnomalousTimeline(
                            input[0], (int) input[1], input[2], input[3], input[4], settings,
                            input[5]);
            assertRawDouble(full.startCalendarTicks(), anomalous.startCalendarTicks(),
                    "anomalous timeline start " + index);
            assertRawDouble(full.endCalendarTicks(), anomalous.endCalendarTicks(),
                    "anomalous timeline end " + index);
            assertRawDouble(full.observerLongitudeRadians(), anomalous.observerLongitudeRadians(),
                    "anomalous timeline longitude " + index);
            assertRawDouble(full.observerLatitudeRadians(), anomalous.observerLatitudeRadians(),
                    "anomalous timeline latitude " + index);
            assertSolarPredictionListsRaw(full.solar(), anomalous.solar(),
                    "anomalous timeline solar " + index);
            assertLunarPredictionListsRaw(full.lunar(), anomalous.lunar(),
                    "anomalous timeline lunar " + index);
            if (anomalous.phases().isEmpty() == false) {
                throw new AssertionError("anomalous timeline retained ordinary phases " + index);
            }
            try {
                anomalous.phases().add(EclipsePredictionService.LunarPhasePrediction.NONE);
                throw new AssertionError("anomalous phase list became mutable " + index);
            } catch (UnsupportedOperationException expected) {
                // Timeline keeps the same immutable-list contract as the complete entry point.
            }
            EclipsePredictionService.Timeline fullAfter = EclipsePredictionService.predictTimeline(
                    input[0], (int) input[1], input[2], input[3], input[4], settings, input[5]);
            if (!full.phases().equals(fullAfter.phases())) {
                throw new AssertionError("anomalous scan changed later complete phase output " + index);
            }
        }
    }

    private static void eventSpecificAccelerationSamplesMatchFullEventView() {
        CelestialRuntimeSettings custom = new CelestialRuntimeSettings(
                5.75D, 4.875D, 3.25D, Math.toRadians(7.5D), true, 2.25D,
                0.8D, 1.2D, CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        Object[][] contexts = {
                {10_000.0D, 20_000.0D, 8, CelestialRuntimeSettings.DEFAULT},
                {-15_000.0D, -30_000.0D, 15, CelestialRuntimeSettings.DEFAULT},
                {27_500.25D, 20_000.0D, 4, custom}
        };
        long[] ticks = {Long.MIN_VALUE, -24_001L, -1L, 0L, 5_999L, 6_000L,
                12_000L, Long.MAX_VALUE};
        CelestialEventRules.RainSample[] rainbowSamples = {
                null,
                new CelestialEventRules.RainSample(0.49F, 0.48F, 0.47F),
                new CelestialEventRules.RainSample(Float.NaN, 0.0F, -0.1F)
        };
        for (int contextIndex = 0; contextIndex < contexts.length; contextIndex++) {
            double observerZ = (double) contexts[contextIndex][0];
            double hemisphereScale = (double) contexts[contextIndex][1];
            int daysInMonth = (int) contexts[contextIndex][2];
            CelestialRuntimeSettings settings =
                    (CelestialRuntimeSettings) contexts[contextIndex][3];
            CelestialRuntimeSettings.PreparedPeriods prepared =
                    settings.preparedPeriods(daysInMonth);
            CelestialMath.ObserverLatitudeContext observerLatitude =
                    CelestialMath.prepareObserverLatitude(observerZ, hemisphereScale);
            for (long tick : ticks) {
                CelestialMath.DaylightSample legacyDaylight = CelestialMath.daylightSampleAt(
                        observerZ, hemisphereScale, tick, daysInMonth);
                CelestialMath.DaylightSample preparedDaylight = CelestialMath.daylightSampleAt(
                        observerLatitude, tick, daysInMonth);
                assertRawDouble(legacyDaylight.solarElevation(), preparedDaylight.solarElevation(),
                        "prepared acceleration daylight elevation " + contextIndex + "/" + tick);
                assertRawDouble(legacyDaylight.apparentDayTime(), preparedDaylight.apparentDayTime(),
                        "prepared acceleration apparent time " + contextIndex + "/" + tick);
                assertRawDouble(legacyDaylight.daylightFactor(), preparedDaylight.daylightFactor(),
                        "prepared acceleration daylight factor " + contextIndex + "/" + tick);

                CelestialMath.EventSample full = CelestialMath.eventSampleAt(observerLatitude,
                        tick, daysInMonth, prepared.synodicDays(), prepared.anomalisticDays(),
                        settings.nodalYears(), settings.lunarInclinationRadians(),
                        settings.sunScale(), settings.moonScale(),
                        prepared.sineLunarInclination());
                CelestialMath.QuarterEventSample quarter = CelestialMath.quarterEventSampleAt(
                        observerLatitude, tick, daysInMonth, prepared.synodicDays(),
                        prepared.anomalisticDays(), settings.nodalYears(),
                        settings.lunarInclinationRadians(), settings.sunScale(),
                        settings.moonScale(), prepared.sineLunarInclination());
                assertRawDouble(full.illuminatedFraction(), quarter.illuminatedFraction(),
                        "quarter acceleration illumination " + contextIndex + "/" + tick);
                if (full.moonPhase() != quarter.moonPhase()) {
                    throw new AssertionError("quarter acceleration phase changed at context "
                            + contextIndex + ", tick " + tick);
                }
                assertRawDouble(full.solarElevation(), quarter.solarElevation(),
                        "quarter acceleration solar elevation " + contextIndex + "/" + tick);
                assertRawDouble(full.moonElevation(), quarter.moonElevation(),
                        "quarter acceleration Moon elevation " + contextIndex + "/" + tick);
                for (CelestialEventType quarterType : new CelestialEventType[]{
                        CelestialEventType.FIRST_QUARTER, CelestialEventType.LAST_QUARTER}) {
                    if (quarterType.matches(full, tick, null) != quarterType.matches(quarter)) {
                        throw new AssertionError("quarter acceleration predicate changed for "
                                + quarterType + " at context " + contextIndex + ", tick " + tick);
                    }
                }
                for (CelestialEventType event : CelestialEventType.values()) {
                    CelestialEventRules.RainSample[] rains = event == CelestialEventType.RAINBOW
                            ? rainbowSamples : new CelestialEventRules.RainSample[]{null};
                    for (CelestialEventRules.RainSample rain : rains) {
                        boolean expected = event.matches(full, tick, rain);
                        boolean actual = TfcCalendarEventAcceleration.matchesAt(event, tick,
                                observerZ, hemisphereScale, daysInMonth, settings, rain);
                        if (expected != actual) {
                            throw new AssertionError("event-specific acceleration diverged for "
                                    + event + " at context " + contextIndex + ", tick " + tick
                                    + ", rain " + rain + ": " + expected + " != " + actual);
                        }
                    }
                }
            }
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

    private static void optimizedSupermoonCacheMatchesLegacyBits() {
        double synodic = CelestialMath.SYNODIC_DAYS;
        double[] calendarEdges = {Double.NaN, Double.NEGATIVE_INFINITY,
                -Double.MAX_VALUE, -synodic - 0.5D,
                Math.nextAfter(-0.5D, Double.NEGATIVE_INFINITY), -0.5D,
                Math.nextAfter(-0.5D, Double.POSITIVE_INFINITY), -0.0D, 0.0D,
                Math.nextAfter(0.5D, Double.NEGATIVE_INFINITY), 0.5D,
                Math.nextAfter(0.5D, Double.POSITIVE_INFINITY), synodic + 0.5D,
                Double.MAX_VALUE, Double.POSITIVE_INFINITY};
        double[] periodEdges = {Double.longBitsToDouble(0x7ff8000000000042L),
                Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -0.0D, 0.0D,
                Double.MIN_VALUE, Double.MIN_NORMAL, 1.0D, synodic,
                Double.MAX_VALUE, Double.POSITIVE_INFINITY};
        for (double calendarDays : calendarEdges) {
            for (double synodicDays : periodEdges) {
                for (double anomalisticDays : periodEdges) {
                    assertRawDouble(legacySupermoonAtFullMoon(calendarDays, synodicDays,
                                    anomalisticDays),
                            CelestialMath.supermoonAtFullMoon(calendarDays, synodicDays,
                                    anomalisticDays), "cached supermoon edge");
                }
            }
        }
        Random random = new Random(0x5A9E4D00L);
        for (int sample = 0; sample < 8_192; sample++) {
            double calendarDays = Double.longBitsToDouble(random.nextLong());
            double synodicDays = Double.longBitsToDouble(random.nextLong());
            double anomalisticDays = Double.longBitsToDouble(random.nextLong());
            assertRawDouble(legacySupermoonAtFullMoon(calendarDays, synodicDays,
                            anomalisticDays),
                    CelestialMath.supermoonAtFullMoon(calendarDays, synodicDays,
                            anomalisticDays), "cached supermoon random " + sample);
            double eventDay = (sample % 97 - 48) * CelestialMath.SYNODIC_DAYS
                    + (sample % 3 - 1) * 0.49D;
            double eventAnomalistic = CelestialMath.ANOMALISTIC_DAYS + (sample & 7) * 0.125D;
            assertRawDouble(legacySupermoonAtFullMoon(eventDay, CelestialMath.SYNODIC_DAYS,
                            eventAnomalistic),
                    CelestialMath.supermoonAtFullMoon(eventDay, CelestialMath.SYNODIC_DAYS,
                            eventAnomalistic), "cached supermoon alternate " + sample);
            assertRawDouble(legacySupermoonAtFullMoon(calendarDays, synodicDays,
                            anomalisticDays),
                    CelestialMath.supermoonAtFullMoon(calendarDays, synodicDays,
                            anomalisticDays), "cached supermoon restored " + sample);
        }

        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = supermoonCacheThread("supermoon-cache-a", 0, failure);
        Thread second = supermoonCacheThread("supermoon-cache-b", 1, failure);
        first.start();
        second.start();
        try {
            first.join();
            second.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("supermoon cache thread test interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("supermoon cache was not thread isolated", failure.get());
        }
    }

    private static Thread supermoonCacheThread(
            String name, int lane,
            java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                for (int sample = 0; sample < 4_096; sample++) {
                    double synodicDays = 8.0D + lane * 3.0D + sample % 7 * 0.125D;
                    double calendarDays = (sample % 251 - 125) * synodicDays
                            + (sample % 3 - 1) * 0.49D;
                    double anomalisticDays = 6.0D + lane * 5.0D + sample % 11 * 0.25D;
                    assertRawDouble(legacySupermoonAtFullMoon(calendarDays, synodicDays,
                                    anomalisticDays),
                            CelestialMath.supermoonAtFullMoon(calendarDays, synodicDays,
                                    anomalisticDays), name + " sample " + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static double legacySupermoonAtFullMoon(double calendarDays, double synodicDays,
                                                     double anomalisticDays) {
        if (!Double.isFinite(calendarDays) || !Double.isFinite(synodicDays)
                || synodicDays <= 0.0D || !Double.isFinite(anomalisticDays)
                || anomalisticDays <= 0.0D) {
            return 0.0D;
        }
        double fullMoonDay = Math.rint(calendarDays / synodicDays) * synodicDays;
        if (Math.abs(calendarDays - fullMoonDay)
                > CelestialMath.SUPERMOON_FULL_MOON_HALF_WINDOW_DAYS) {
            return 0.0D;
        }
        double anomalisticProgress = positiveModulo(fullMoonDay / anomalisticDays, 1.0D);
        double distanceAtFullMoon = 1.0D - 0.07D
                * Math.cos(CelestialMath.TAU * anomalisticProgress);
        double strength = testClamp((1.0D - distanceAtFullMoon) / 0.07D, 0.0D, 1.0D);
        return strength >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD ? strength : 0.0D;
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

    private static void visibleBloodMoonSubsetMatchesFullDisplaySample() {
        CelestialRuntimeSettings custom = new CelestialRuntimeSettings(
                17.25D, 14.75D, 19.0D, Math.toRadians(4.75D), true, 2.5D,
                0.8D, 1.15D, CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        int active = 0;
        for (CelestialRuntimeSettings settings : new CelestialRuntimeSettings[]{
                CelestialRuntimeSettings.DEFAULT, custom}) {
            int daysInMonth = settings == custom ? 11 : 8;
            double hemisphereScale = settings == custom ? -23_456.0D : 20_000.0D;
            CelestialRuntimeSettings.PreparedPeriods prepared =
                    settings.preparedPeriods(daysInMonth);
            double daysInYear = CelestialMath.daysInYear(daysInMonth);
            long fullMoonCount = (long) Math.ceil(settings.nodalYears() * daysInYear
                    / prepared.synodicDays()) + 2L;
            for (long fullMoonIndex = -2L; fullMoonIndex <= fullMoonCount; fullMoonIndex++) {
                for (double offset : new double[]{-0.25D, -0.08D, 0.0D, 0.08D, 0.25D}) {
                    double calendarTicks = (fullMoonIndex * prepared.synodicDays() + offset)
                            * CelestialMath.TICKS_IN_DAY;
                    for (double z : new double[]{-hemisphereScale, -hemisphereScale * 0.5D,
                            0.0D, hemisphereScale * 0.5D, hemisphereScale}) {
                        CelestialMath.DisplayEventSample display =
                                CelestialMath.displayEventSampleAt(z, hemisphereScale,
                                        calendarTicks, daysInMonth, prepared.synodicDays(),
                                        prepared.anomalisticDays(), settings.nodalYears(),
                                        settings.lunarInclinationRadians(), settings.sunScale(),
                                        settings.moonScale(), prepared.sineLunarInclination());
                        double expected = CelestialGameplayRules.visibleBloodMoon(
                                display.bloodMoon(), display.moonElevation(),
                                display.solarElevation());
                        double actual = CelestialMath.visibleBloodMoonAt(z, hemisphereScale,
                                calendarTicks, daysInMonth, prepared.synodicDays(),
                                prepared.anomalisticDays(), settings.nodalYears(),
                                settings.lunarInclinationRadians(), settings.sunScale(),
                                settings.moonScale(), prepared.sineLunarInclination());
                        assertRawDouble(expected, actual,
                                "visible blood Moon subset " + fullMoonIndex + "/" + offset
                                        + "/" + z);
                        if (expected > 0.0D) {
                            active++;
                        }
                    }
                }
            }
        }
        if (active < 8) {
            throw new AssertionError("visible blood Moon subset did not exercise active states: "
                    + active);
        }
    }

    private static void surfaceMonsterIdFilterMatchesLegacyTextComparison() {
        ResourceLocation zombie = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");
        ResourceLocation custom = ResourceLocation.fromNamespaceAndPath("wildfires", "test_monster");
        java.util.List<java.util.List<String>> configuredLists = java.util.List.of(
                java.util.List.of(),
                java.util.List.of("minecraft:zombie"),
                java.util.List.of("zombie"),
                java.util.List.of("minecraft:zombie", "minecraft:zombie"),
                java.util.List.of("wildfires:test_monster", "minecraft:zombie"),
                java.util.List.of("not valid", "minecraft:"),
                java.util.List.of(":zombie", "wildfires:test_monster"),
                java.util.Arrays.asList(null, "minecraft:zombie"),
                java.util.Arrays.asList("wildfires:test_monster", null));
        for (java.util.List<String> configured : configuredLists) {
            CelestialConfig.SurfaceMonsterIdFilter optimized =
                    CelestialConfig.SurfaceMonsterIdFilter.from(configured);
            for (ResourceLocation id : new ResourceLocation[]{zombie, custom,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "skeleton")}) {
                boolean legacy = legacySurfaceMonsterAllowed(configured, id);
                if (optimized.allows(id) != legacy) {
                    throw new AssertionError("surface monster filter changed for " + configured
                            + " and " + id);
                }
            }
        }

        java.util.Random random = new java.util.Random(0xB100D1D5L);
        for (int sample = 0; sample < 4096; sample++) {
            String namespace = "n" + Integer.toUnsignedString(random.nextInt(), 36);
            String path = "p" + Integer.toUnsignedString(random.nextInt(), 36);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
            java.util.List<String> configured = switch (sample & 3) {
                case 0 -> java.util.List.of(id.toString());
                case 1 -> java.util.List.of(path);
                case 2 -> java.util.List.of("minecraft:zombie", id.toString());
                default -> java.util.List.of("invalid value", path, "wildfires:test_monster");
            };
            boolean legacy = legacySurfaceMonsterAllowed(configured, id);
            boolean optimized = CelestialConfig.SurfaceMonsterIdFilter.from(configured).allows(id);
            if (legacy != optimized) {
                throw new AssertionError("random surface monster filter changed for " + configured
                        + " and " + id);
            }
        }

        for (java.util.List<String> configured : java.util.List.of(
                java.util.List.<String>of(), java.util.List.of("minecraft:zombie"))) {
            Throwable legacy = surfaceMonsterNullFailure(configured, true);
            Throwable optimized = surfaceMonsterNullFailure(configured, false);
            if (legacy == null ? optimized != null
                    : optimized == null || legacy.getClass() != optimized.getClass()) {
                throw new AssertionError("surface monster null semantics changed for " + configured
                        + ": legacy=" + legacy + ", optimized=" + optimized);
            }
        }
    }

    private static boolean legacySurfaceMonsterAllowed(java.util.List<String> configured,
                                                        ResourceLocation id) {
        if (configured.isEmpty()) {
            return true;
        }
        String text = id.toString();
        for (String configuredId : configured) {
            if (text.equals(configuredId)) {
                return true;
            }
        }
        return false;
    }

    private static Throwable surfaceMonsterNullFailure(java.util.List<String> configured,
                                                       boolean legacy) {
        try {
            if (legacy) {
                legacySurfaceMonsterAllowed(configured, null);
            } else {
                CelestialConfig.SurfaceMonsterIdFilter.from(configured).allows(null);
            }
            return null;
        } catch (Throwable failure) {
            return failure;
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

    private static void combinedServerSettingsCacheIsExactAndCoherent() {
        CelestialOrbitalPhases phases = CelestialOrbitalPhases.random(
                new Random(0x5E771A65L), CelestialPlanetSettings.DEFAULT);
        CelestialEphemerisSavedData data = CelestialEphemerisSavedData.load(
                new CelestialEphemerisSavedDataForTest(phases).save());
        CelestialOrbitalPhases persistedPhases = data.phases();
        CelestialRuntimeSettings firstBase = CelestialRuntimeSettings.DEFAULT
                .withOrbitalPhases(CelestialOrbitalPhases.ZERO);
        CelestialRuntimeSettings expected = firstBase.withOrbitalPhases(persistedPhases);
        CelestialRuntimeSettings first = data.settings(firstBase);
        CelestialRuntimeSettings repeated = data.settings(firstBase);
        assertRuntimeSettingsRaw(expected, first, "cached server settings");
        if (first != repeated) {
            throw new AssertionError("same base/ephemeris identity did not reuse combined settings");
        }

        CelestialRuntimeSettings equalButDistinctBase = CelestialRuntimeSettings.DEFAULT
                .withOrbitalPhases(CelestialOrbitalPhases.ZERO);
        if (equalButDistinctBase == firstBase || !equalButDistinctBase.equals(firstBase)) {
            throw new AssertionError("settings-cache identity test did not build equal distinct bases");
        }
        CelestialRuntimeSettings refreshed = data.settings(equalButDistinctBase);
        assertRuntimeSettingsRaw(equalButDistinctBase.withOrbitalPhases(persistedPhases), refreshed,
                "identity-invalidated server settings");
        if (refreshed == first) {
            throw new AssertionError("equal but identity-distinct base failed to invalidate settings cache");
        }

        CelestialRuntimeSettings alternateBase = new CelestialRuntimeSettings(
                Math.nextUp(firstBase.synodicDays()), firstBase.anomalisticDays(),
                firstBase.nodalYears(), firstBase.lunarInclinationRadians(),
                firstBase.bloodMoonSurfaceMonsters(), firstBase.bloodMoonSpawnMultiplier(),
                firstBase.sunScale(), firstBase.moonScale(), firstBase.lunarPeriodPreset(),
                firstBase.planetSettings(), firstBase.orbitalPhases());
        CelestialEphemerisSavedData concurrentData = CelestialEphemerisSavedData.load(
                new CelestialEphemerisSavedDataForTest(phases).save());
        CelestialOrbitalPhases concurrentPhases = concurrentData.phases();
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicReference<CelestialRuntimeSettings> sharedIdentity =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] workers = new Thread[2];
        for (int lane = 0; lane < workers.length; lane++) {
            final int worker = lane;
            workers[lane] = new Thread(() -> {
                try {
                    start.await();
                    for (int sample = 0; sample < 512; sample++) {
                        if (sample == 128) {
                            start.await();
                        }
                        CelestialRuntimeSettings base = sample < 128
                                ? firstBase : ((sample + worker) & 1) == 0
                                ? firstBase : alternateBase;
                        CelestialRuntimeSettings actual = concurrentData.settings(base);
                        assertRuntimeSettingsRaw(base.withOrbitalPhases(concurrentPhases), actual,
                                "concurrent settings holder " + worker + "/" + sample);
                        if (sample < 128) {
                            CelestialRuntimeSettings observed = sharedIdentity.get();
                            if (observed == null) {
                                sharedIdentity.compareAndSet(null, actual);
                                observed = sharedIdentity.get();
                            }
                            if (observed != actual) {
                                throw new AssertionError("concurrent first cache miss published two values");
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "wildfires-celestial-settings-cache-" + lane);
            workers[lane].start();
        }
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("settings-cache concurrency test was interrupted", exception);
            }
        }
        if (failure.get() != null) {
            throw new AssertionError("atomic settings holder exposed a mixed snapshot", failure.get());
        }
    }

    private static void preparedRuntimePeriodsAreExactAndThreadIsolated() {
        CelestialRuntimeSettings legacy = new CelestialRuntimeSettings(
                31.25D, 29.75D, 17.5D, -0.1375D,
                true, 2.0D, 0.75D, 1.25D,
                CelestialRuntimeSettings.LunarPeriodPreset.LEGACY_TFCCAELUM,
                CelestialPlanetSettings.DEFAULT);
        CelestialRuntimeSettings custom = new CelestialRuntimeSettings(
                19.125D, 13.875D, 21.0D, 0.24375D,
                false, 4.0D, 1.5D, 0.5D,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        CelestialRuntimeSettings[] settingsSamples = {
                CelestialRuntimeSettings.DEFAULT, legacy, custom
        };
        int[] monthLengths = {
                Integer.MIN_VALUE, -1, 0, 1, 8, 15, 31, Integer.MAX_VALUE
        };
        for (CelestialRuntimeSettings settings : settingsSamples) {
            for (int daysInMonth : monthLengths) {
                CelestialRuntimeSettings.PreparedPeriods first =
                        settings.preparedPeriods(daysInMonth);
                assertPreparedPeriods(settings, daysInMonth, first,
                        "prepared runtime periods");
                if (first != settings.preparedPeriods(daysInMonth)) {
                    throw new AssertionError("prepared runtime periods did not hit one identity");
                }
                CelestialRuntimeSettings equalButDistinct = new CelestialRuntimeSettings(
                        settings.synodicDays(), settings.anomalisticDays(), settings.nodalYears(),
                        settings.lunarInclinationRadians(), settings.bloodMoonSurfaceMonsters(),
                        settings.bloodMoonSpawnMultiplier(), settings.sunScale(), settings.moonScale(),
                        settings.lunarPeriodPreset(), settings.planetSettings(),
                        settings.orbitalPhases());
                CelestialRuntimeSettings.PreparedPeriods refreshed =
                        equalButDistinct.preparedPeriods(daysInMonth);
                assertPreparedPeriods(equalButDistinct, daysInMonth, refreshed,
                        "identity-invalidated runtime periods");
                if (refreshed == first) {
                    throw new AssertionError("equal runtime settings identity did not invalidate");
                }
            }
        }

        java.util.concurrent.atomic.AtomicReference<CelestialRuntimeSettings.PreparedPeriods> first =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<CelestialRuntimeSettings.PreparedPeriods> second =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread threadA = preparedPeriodsThread("prepared-periods-a", custom, first, failure);
        Thread threadB = preparedPeriodsThread("prepared-periods-b", custom, second, failure);
        threadA.start();
        threadB.start();
        try {
            threadA.join();
            threadB.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("prepared-period cache thread test interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("prepared-period cache thread test failed", failure.get());
        }
        if (first.get() == null || second.get() == null || first.get() == second.get()) {
            throw new AssertionError("prepared-period cache was not thread isolated");
        }
    }

    private static Thread preparedPeriodsThread(String name, CelestialRuntimeSettings settings,
            java.util.concurrent.atomic.AtomicReference<CelestialRuntimeSettings.PreparedPeriods> output,
            java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                CelestialRuntimeSettings.PreparedPeriods first = settings.preparedPeriods(15);
                for (int sample = 0; sample < 256; sample++) {
                    int daysInMonth = (sample & 1) == 0 ? 15 : 8;
                    CelestialRuntimeSettings.PreparedPeriods actual =
                            settings.preparedPeriods(daysInMonth);
                    assertPreparedPeriods(settings, daysInMonth, actual,
                            name + " sample " + sample);
                }
                CelestialRuntimeSettings.PreparedPeriods stable = settings.preparedPeriods(15);
                if (stable != settings.preparedPeriods(15)) {
                    throw new AssertionError(name + " failed a stable cache hit");
                }
                output.set(stable);
                assertPreparedPeriods(settings, 15, first, name + " initial value");
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static void assertPreparedPeriods(CelestialRuntimeSettings settings, int daysInMonth,
                                              CelestialRuntimeSettings.PreparedPeriods actual,
                                              String label) {
        assertRawDouble(settings.resolvedSynodicDays(daysInMonth), actual.synodicDays(),
                label + " synodic");
        assertRawDouble(settings.resolvedAnomalisticDays(daysInMonth), actual.anomalisticDays(),
                label + " anomalistic");
        assertRawDouble(Math.sin(settings.lunarInclinationRadians()),
                actual.sineLunarInclination(), label + " inclination sine");
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

    private static void optimizedSolarTimeMatchesLegacyBits() {
        double[] scales = {20_000.0D, -20_000.0D, 1.0E-12D, Double.NaN};
        double[] positions = {-1.0E9D, -50_000.0D, -10_000.0D, 0.0D, 10_000.0D,
                30_000.0D, 50_000.0D, 1.0E9D};
        double[] years = {-2.0D, -0.25D, 0.0D, 0.249999999D, 0.5D, 0.999999999D, 2.25D};
        double[] days = {-1.0D, 0.0D, 0.125D, 0.25D, 0.499999999D, 0.5D,
                0.75D, 0.999999999D, 1.25D};
        for (double scale : scales) {
            for (double z : positions) {
                double latitude = CelestialMath.latitude(z, scale);
                for (double fractionOfYear : years) {
                    double longitude = CelestialMath.TAU * positiveModulo(
                            284.0D / 365.0D + fractionOfYear, 1.0D);
                    double declination = CelestialMath.AXIAL_TILT * Math.sin(longitude);
                    for (double fractionOfDay : days) {
                        double expected = legacySunBasedDayTime(z, scale, fractionOfYear,
                                fractionOfDay);
                        double actual = CelestialMath.sunBasedDayTime(latitude, declination,
                                fractionOfDay);
                        assertRawDouble(expected, actual, "optimized apparent solar time");
                    }
                }
            }
        }
        assertRawDouble(Math.sin(CelestialMath.AXIAL_TILT), CelestialMath.AXIAL_TILT_SIN,
                "cached axial tilt sine");
        assertRawDouble(Math.cos(CelestialMath.AXIAL_TILT), CelestialMath.AXIAL_TILT_COS,
                "cached axial tilt cosine");
        for (double z : positions) {
            for (double calendarTicks : new double[]{-9_876_543.25D, -1.0D, -0.0D, 0.0D,
                    1.0D, 12_345.75D, 987_654_321.125D}) {
                int daysInMonth = 11;
                CelestialMath.Result actual = CelestialMath.calculate(new CelestialMath.Input(
                        z, 20_000.0D, calendarTicks, daysInMonth));
                double calendarDays = CelestialMath.calendarDays(calendarTicks);
                double fractionOfYear = positiveModulo(calendarDays,
                        CelestialMath.daysInYear(daysInMonth))
                        / CelestialMath.daysInYear(daysInMonth);
                double fractionOfDay = positiveModulo(calendarTicks,
                        CelestialMath.TICKS_IN_DAY) / CelestialMath.TICKS_IN_DAY;
                double longitude = CelestialMath.TAU * positiveModulo(
                        284.0D / 365.0D + fractionOfYear, 1.0D);
                CelestialVector rawSun = CelestialMath.eclipticToEquatorialFixedTilt(
                        longitude, 0.0D);
                double declination = CelestialMath.AXIAL_TILT * Math.sin(longitude);
                double rightAscension = Math.atan2(rawSun.y(), rawSun.x());
                double cosineDeclination = Math.cos(declination);
                CelestialVector expectedSun = new CelestialVector(
                        cosineDeclination * Math.cos(rightAscension),
                        cosineDeclination * Math.sin(rightAscension), Math.sin(declination));
                assertVectorRaw(expectedSun, actual.sunGeocentric(),
                        "shared declination trig Sun vector");
                assertRawDouble(CelestialMath.sunBasedDayTime(
                                CelestialMath.latitude(z, 20_000.0D), declination,
                                fractionOfDay) / CelestialMath.TICKS_IN_DAY,
                        actual.apparentDayTime(), "shared declination trig apparent time");
            }
        }
    }

    private static void allocationFreeMathEntriesMatchFullCalculationBits() {
        Random random = new Random(0x4D4154485F464153L);
        CelestialMath.HorizonProducts horizonProducts = new CelestialMath.HorizonProducts();
        for (int sample = 0; sample < 256; sample++) {
            double z = sample < 8
                    ? new double[]{-100_000.0D, -40_000.0D, -10_000.0D, -0.0D,
                    0.0D, 10_000.0D, 40_000.0D, 100_000.0D}[sample]
                    : -100_000.0D + random.nextDouble() * 200_000.0D;
            double hemisphereScale = sample < 4
                    ? new double[]{20_000.0D, -20_000.0D, 1.0E-5D, 75_000.0D}[sample]
                    : 1.0D + random.nextDouble() * 75_000.0D;
            double calendarTicks = sample < 8
                    ? new double[]{-1.0E12D, -9_876_543.25D, -1.0D, -0.0D,
                    0.0D, 1.0D, 12_345.75D, 1.0E12D}[sample]
                    : -1.0E10D + random.nextDouble() * 2.0E10D;
            int daysInMonth = sample < 4 ? new int[]{1, 8, 15, 31}[sample]
                    : 1 + random.nextInt(64);
            double synodicDays = 5.0D + random.nextDouble() * 35.0D;
            double anomalisticDays = 5.0D + random.nextDouble() * 35.0D;
            double nodalYears = 1.0D + random.nextDouble() * 30.0D;
            double lunarInclination = -0.3D + random.nextDouble() * 0.6D;
            double sineInclination = Math.sin(lunarInclination);
            double sunScale = 0.1D + random.nextDouble() * 3.0D;
            double moonScale = 0.1D + random.nextDouble() * 3.0D;
            CelestialMath.Input input = new CelestialMath.Input(z, hemisphereScale,
                    calendarTicks, daysInMonth, synodicDays, anomalisticDays, nodalYears,
                    lunarInclination, sunScale, moonScale);
            CelestialMath.Result full = CelestialMath.calculate(input);
            CelestialMath.Result scalar = CelestialMath.calculate(z, hemisphereScale,
                    calendarTicks, daysInMonth, synodicDays, anomalisticDays, nodalYears,
                    lunarInclination, sunScale, moonScale);
            assertResultRaw(full, scalar, "allocation-free full frame " + sample);
            CelestialMath.Result prepared = CelestialMath.calculate(z, hemisphereScale,
                    calendarTicks, daysInMonth, synodicDays, anomalisticDays, nodalYears,
                    lunarInclination, sunScale, moonScale, sineInclination);
            assertResultRaw(full, prepared, "prepared-inclination full frame " + sample);
            CelestialMath.Result preparedHorizon = CelestialMath.calculate(z, hemisphereScale,
                    calendarTicks, daysInMonth, synodicDays, anomalisticDays, nodalYears,
                    lunarInclination, sunScale, moonScale, sineInclination, horizonProducts);
            assertResultRaw(full, preparedHorizon,
                    "prepared-horizon full frame " + sample);
            assertRawDouble(Math.sin(full.latitude()), horizonProducts.sineLatitude(),
                    "prepared horizon sine latitude " + sample);
            assertRawDouble(Math.cos(full.latitude()), horizonProducts.cosineLatitude(),
                    "prepared horizon cosine latitude " + sample);
            assertRawDouble(Math.sin(full.localSiderealAngle()), horizonProducts.sineSidereal(),
                    "prepared horizon sine sidereal " + sample);
            assertRawDouble(Math.cos(full.localSiderealAngle()), horizonProducts.cosineSidereal(),
                    "prepared horizon cosine sidereal " + sample);
            assertRawDouble(full.moonDistance(),
                    CelestialMath.moonDistanceAtCalendarTicks(calendarTicks, anomalisticDays),
                    "moon-distance subset " + sample);
            CelestialMath.DaylightSample daylightSample = CelestialMath.daylightSampleAt(z,
                    hemisphereScale, calendarTicks, daysInMonth);
            CelestialMath.DaylightSample contextDaylight =
                    new OverworldCelestialProvider.DaylightContext(calendarTicks,
                            daysInMonth, hemisphereScale).daylightSampleAt(z);
            assertRawDouble(full.solarElevation(), daylightSample.solarElevation(),
                    "daylight subset solar elevation " + sample);
            assertRawDouble(full.apparentDayTime(), daylightSample.apparentDayTime(),
                    "daylight subset apparent time " + sample);
            assertRawDouble(full.daylightFactor(), daylightSample.daylightFactor(),
                    "daylight subset factor " + sample);
            assertRawDouble(daylightSample.solarElevation(), contextDaylight.solarElevation(),
                    "daylight context solar elevation " + sample);
            assertRawDouble(daylightSample.apparentDayTime(), contextDaylight.apparentDayTime(),
                    "daylight context apparent time " + sample);
            assertRawDouble(daylightSample.daylightFactor(), contextDaylight.daylightFactor(),
                    "daylight context factor " + sample);
            assertRawDouble(full.illuminatedFraction(),
                    CelestialMath.illuminatedFractionAt(z, hemisphereScale, calendarTicks,
                            daysInMonth, synodicDays, nodalYears, lunarInclination),
                    "phase-illumination subset " + sample);
            assertRawDouble(full.illuminatedFraction(),
                    CelestialMath.illuminatedFractionAt(z, hemisphereScale, calendarTicks,
                            daysInMonth, synodicDays, nodalYears, lunarInclination,
                            sineInclination),
                    "prepared phase-illumination subset " + sample);
            CelestialMath.SolarLatitudeContext observerLatitude =
                    CelestialMath.prepareSolarLatitude(CelestialMath.latitude(z, hemisphereScale));
            CelestialMath.ObserverLatitudeContext fixedObserver =
                    CelestialMath.prepareObserverLatitude(z, hemisphereScale);
            double expectedObserverLatitude = CelestialMath.latitude(z, hemisphereScale);
            assertRawDouble(expectedObserverLatitude, fixedObserver.latitude(),
                    "fixed-observer latitude " + sample);
            assertRawDouble(Math.sin(expectedObserverLatitude), fixedObserver.sine(),
                    "fixed-observer latitude sine " + sample);
            assertRawDouble(Math.cos(expectedObserverLatitude), fixedObserver.cosine(),
                    "fixed-observer latitude cosine " + sample);
            assertRawDouble(full.illuminatedFraction(),
                    CelestialMath.illuminatedFractionAt(observerLatitude, calendarTicks,
                            daysInMonth, synodicDays, nodalYears, lunarInclination,
                            sineInclination),
                    "fixed-observer phase-illumination subset " + sample);
            CelestialMath.LunarPredictionSample lunarSample =
                    CelestialMath.lunarPredictionSampleAt(z, hemisphereScale, calendarTicks,
                            daysInMonth, synodicDays, anomalisticDays, nodalYears,
                            lunarInclination, moonScale);
            assertLunarEclipseStateRaw(full.lunarEclipseRegion(),
                    lunarSample.lunarEclipseRegion(), "lunar prediction subset " + sample);
            assertRawDouble(full.supermoon(), lunarSample.supermoon(),
                    "lunar prediction supermoon " + sample);
            CelestialMath.LunarPredictionSample preparedLunarSample =
                    CelestialMath.lunarPredictionSampleAt(z, hemisphereScale, calendarTicks,
                            daysInMonth, synodicDays, anomalisticDays, nodalYears,
                            lunarInclination, moonScale, sineInclination);
            assertLunarEclipseStateRaw(full.lunarEclipseRegion(),
                    preparedLunarSample.lunarEclipseRegion(),
                    "prepared lunar prediction subset " + sample);
            assertRawDouble(full.supermoon(), preparedLunarSample.supermoon(),
                    "prepared lunar prediction supermoon " + sample);
            CelestialMath.LunarPredictionSample observerLunarSample =
                    CelestialMath.lunarPredictionSampleAt(observerLatitude, calendarTicks,
                            daysInMonth, synodicDays, anomalisticDays, nodalYears,
                            lunarInclination, moonScale, sineInclination);
            assertLunarEclipseStateRaw(full.lunarEclipseRegion(),
                    observerLunarSample.lunarEclipseRegion(),
                    "fixed-observer lunar prediction subset " + sample);
            assertRawDouble(full.supermoon(), observerLunarSample.supermoon(),
                    "fixed-observer lunar prediction supermoon " + sample);
            CelestialMath.EventSample eventSample = CelestialMath.eventSampleAt(z,
                    hemisphereScale, calendarTicks, daysInMonth, synodicDays,
                    anomalisticDays, nodalYears, lunarInclination, sunScale, moonScale,
                    sineInclination);
            assertRawDouble(full.latitude(), eventSample.latitude(),
                    "event subset latitude " + sample);
            assertRawDouble(full.fractionOfDay(), eventSample.fractionOfDay(),
                    "event subset fraction " + sample);
            assertRawDouble(full.illuminatedFraction(), eventSample.illuminatedFraction(),
                    "event subset illumination " + sample);
            if (full.moonPhase() != eventSample.moonPhase()) {
                throw new AssertionError("event subset moon phase " + sample);
            }
            assertRawDouble(full.solarEclipse(), eventSample.solarEclipse(),
                    "event subset solar eclipse " + sample);
            assertLunarEclipseStateRaw(full.lunarEclipseRegion(),
                    eventSample.lunarEclipseRegion(), "event subset lunar eclipse " + sample);
            assertRawDouble(full.supermoon(), eventSample.supermoon(),
                    "event subset supermoon " + sample);
            assertRawDouble(full.bloodMoon(), eventSample.bloodMoon(),
                    "event subset blood moon " + sample);
            assertRawDouble(full.solarElevation(), eventSample.solarElevation(),
                    "event subset solar elevation " + sample);
            assertRawDouble(full.moonElevation(), eventSample.moonElevation(),
                    "event subset moon elevation " + sample);
            assertRawDouble(full.apparentDayTime(), eventSample.apparentDayTime(),
                    "event subset apparent time " + sample);
            CelestialMath.EventSample fixedObserverEvent = CelestialMath.eventSampleAt(
                    fixedObserver, calendarTicks, daysInMonth, synodicDays, anomalisticDays,
                    nodalYears, lunarInclination, sunScale, moonScale, sineInclination);
            assertEventSampleRaw(full, fixedObserverEvent,
                    "fixed-observer event subset " + sample);
            DaylightState fullDaylight = OverworldCelestialProvider.daylightFromResult(full);
            DaylightState sampledDaylight = OverworldCelestialProvider.daylightFromSample(
                    daylightSample);
            assertRawDouble(fullDaylight.solarElevationRadians(),
                    sampledDaylight.solarElevationRadians(),
                    "provider daylight elevation " + sample);
            if (fullDaylight.sunAboveHorizon() != sampledDaylight.sunAboveHorizon()) {
                throw new AssertionError("provider daylight horizon " + sample);
            }
            assertRawDouble(fullDaylight.apparentDayTime(), sampledDaylight.apparentDayTime(),
                    "provider daylight apparent time " + sample);
            assertRawDouble(fullDaylight.daylightFactor(), sampledDaylight.daylightFactor(),
                    "provider daylight factor " + sample);
            CelestialEventState fullEvents = OverworldCelestialProvider.eventsFromResult(full);
            CelestialEventState sampledEvents = OverworldCelestialProvider.eventsFromSample(
                    eventSample);
            if (!fullEvents.equals(sampledEvents)) {
                throw new AssertionError("provider event subset changed at sample " + sample
                        + "\nfull=" + fullEvents + "\nsampled=" + sampledEvents);
            }
            CelestialMath.DisplayEventSample displaySample = CelestialMath.displayEventSampleAt(z,
                    hemisphereScale, calendarTicks, daysInMonth, synodicDays,
                    anomalisticDays, nodalYears, lunarInclination, sunScale, moonScale,
                    sineInclination);
            assertRawDouble(full.illuminatedFraction(), displaySample.illuminatedFraction(),
                    "display-event illumination " + sample);
            assertRawDouble(full.solarEclipse(), displaySample.solarEclipse(),
                    "display-event solar eclipse " + sample);
            assertRawDouble(full.lunarEclipseRegion().penumbraCoverage(),
                    displaySample.lunarPenumbraCoverage(),
                    "display-event lunar penumbra " + sample);
            assertRawDouble(full.supermoon(), displaySample.supermoon(),
                    "display-event supermoon " + sample);
            assertRawDouble(full.bloodMoon(), displaySample.bloodMoon(),
                    "display-event blood moon " + sample);
            assertRawDouble(full.solarElevation(), displaySample.solarElevation(),
                    "display-event solar elevation " + sample);
            assertRawDouble(full.moonElevation(), displaySample.moonElevation(),
                    "display-event Moon elevation " + sample);
            long displayTick = (long) Math.floor(calendarTicks);
            if (EclipsePredictionService.displayEventMask(full, displayTick)
                    != EclipsePredictionService.displayEventMask(displaySample, displayTick)) {
                throw new AssertionError("display-event sample mask changed at sample " + sample);
            }
            CelestialMath.DisplayEventSample fixedObserverDisplay =
                    CelestialMath.displayEventSampleAt(fixedObserver, calendarTicks,
                            daysInMonth, synodicDays, anomalisticDays, nodalYears,
                            lunarInclination, sunScale, moonScale, sineInclination);
            assertDisplayEventSampleRaw(full, fixedObserverDisplay,
                    "fixed-observer display subset " + sample);
            if (EclipsePredictionService.displayEventMask(full, displayTick)
                    != EclipsePredictionService.displayEventMask(fixedObserverDisplay,
                    displayTick)) {
                throw new AssertionError("fixed-observer display mask changed at sample "
                        + sample);
            }
            CelestialMath.QuarterEventSample quarterSample =
                    CelestialMath.quarterEventSampleAt(fixedObserver, calendarTicks,
                            daysInMonth, synodicDays, anomalisticDays, nodalYears,
                            lunarInclination, sunScale, moonScale, sineInclination);
            assertRawDouble(full.illuminatedFraction(), quarterSample.illuminatedFraction(),
                    "quarter-event illumination " + sample);
            if (full.moonPhase() != quarterSample.moonPhase()) {
                throw new AssertionError("quarter-event phase changed at sample " + sample);
            }
            assertRawDouble(full.solarElevation(), quarterSample.solarElevation(),
                    "quarter-event solar elevation " + sample);
            assertRawDouble(full.moonElevation(), quarterSample.moonElevation(),
                    "quarter-event Moon elevation " + sample);
            for (CelestialEventType quarterType : new CelestialEventType[]{
                    CelestialEventType.FIRST_QUARTER, CelestialEventType.LAST_QUARTER}) {
                if (quarterType.matches(full, (long) Math.floor(calendarTicks), null)
                        != quarterType.matches(quarterSample)) {
                    throw new AssertionError("quarter-event predicate changed for "
                            + quarterType + " at sample " + sample);
                }
            }
            CelestialEventRules.RainSample rain = new CelestialEventRules.RainSample(
                    sample % 3 * 0.25F, sample % 5 * 0.1F, sample % 7 * 0.05F);
            long eventTick = (long) Math.floor(calendarTicks);
            for (CelestialEventType type : CelestialEventType.values()) {
                if (type.matches(full, eventTick, rain)
                        != type.matches(eventSample, eventTick, rain)
                        || type.matches(full, eventTick, null)
                        != type.matches(eventSample, eventTick, null)) {
                    throw new AssertionError("event subset predicate " + type + " " + sample);
                }
            }
            double calendarDays = CelestialMath.calendarDays(calendarTicks);
            double yearDays = CelestialMath.daysInYear(daysInMonth);
            assertSolarEventRaw(SolarEclipseRegion.eventFor(calendarDays, yearDays,
                            synodicDays, nodalYears, lunarInclination),
                    SolarEclipseRegion.eventFor(calendarDays, yearDays, synodicDays,
                            nodalYears, lunarInclination, sineInclination),
                    "prepared solar inclination " + sample);
            assertSolarEventRaw(SolarEclipseRegion.eventFor(calendarDays, yearDays,
                            synodicDays, nodalYears, lunarInclination),
                    SolarEclipseRegion.eventForPrepared(calendarDays, yearDays, synodicDays,
                            nodalYears, lunarInclination, sineInclination),
                    "cached prepared solar event " + sample);
            assertLunarEventRaw(LunarEclipseRegion.eventFor(calendarDays, yearDays,
                            synodicDays, nodalYears, lunarInclination),
                    LunarEclipseRegion.eventFor(calendarDays, yearDays, synodicDays,
                            nodalYears, lunarInclination, sineInclination),
                    "prepared lunar inclination " + sample);
            assertLunarEventRaw(LunarEclipseRegion.eventFor(calendarDays, yearDays,
                            synodicDays, nodalYears, lunarInclination),
                    LunarEclipseRegion.eventForPrepared(calendarDays, yearDays, synodicDays,
                            nodalYears, lunarInclination, sineInclination),
                    "cached prepared lunar event " + sample);
        }

        double[] exceptionalCoordinates = {Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, -0.0D, 0.0D};
        double[] exceptionalScales = {Double.NaN, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, -0.0D, 0.0D, 1.0E-7D, 20_000.0D};
        for (double z : exceptionalCoordinates) {
            for (double hemisphereScale : exceptionalScales) {
                double latitude = CelestialMath.latitude(z, hemisphereScale);
                CelestialMath.ObserverLatitudeContext observer =
                        CelestialMath.prepareObserverLatitude(z, hemisphereScale);
                assertRawDouble(latitude, observer.latitude(),
                        "exceptional fixed-observer latitude");
                assertRawDouble(Math.sin(latitude), observer.sine(),
                        "exceptional fixed-observer sine");
                assertRawDouble(Math.cos(latitude), observer.cosine(),
                        "exceptional fixed-observer cosine");
            }
        }
    }

    private static void preparedEclipseEventCacheMatchesLegacyBits() {
        SolarEclipseRegion.Event publicSolarFirst = SolarEclipseRegion.eventFor(19.0D,
                96.0D, CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION);
        SolarEclipseRegion.Event publicSolarSecond = SolarEclipseRegion.eventFor(19.0D,
                96.0D, CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION);
        LunarEclipseRegion.Event publicLunarFirst = LunarEclipseRegion.eventFor(19.0D,
                96.0D, CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION);
        LunarEclipseRegion.Event publicLunarSecond = LunarEclipseRegion.eventFor(19.0D,
                96.0D, CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                CelestialMath.LUNAR_INCLINATION);
        if (publicSolarFirst == publicSolarSecond || publicLunarFirst == publicLunarSecond) {
            throw new AssertionError("public eclipse event factories lost per-call record identity");
        }

        double[] exceptional = {Double.longBitsToDouble(0x7ff8000000000042L),
                Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -0.0D, 0.0D,
                Double.MIN_VALUE, Double.MIN_NORMAL, 1.0D, Double.MAX_VALUE,
                Double.POSITIVE_INFINITY};
        for (double calendarDays : exceptional) {
            for (double synodicDays : exceptional) {
                double sine = Math.sin(CelestialMath.LUNAR_INCLINATION);
                assertSolarEventRaw(SolarEclipseRegion.eventFor(calendarDays, 96.0D,
                                synodicDays, CelestialMath.NODAL_YEARS,
                                CelestialMath.LUNAR_INCLINATION, sine),
                        SolarEclipseRegion.eventForPrepared(calendarDays, 96.0D,
                                synodicDays, CelestialMath.NODAL_YEARS,
                                CelestialMath.LUNAR_INCLINATION, sine),
                        "prepared solar event edge");
                assertLunarEventRaw(LunarEclipseRegion.eventFor(calendarDays, 96.0D,
                                synodicDays, CelestialMath.NODAL_YEARS,
                                CelestialMath.LUNAR_INCLINATION, sine),
                        LunarEclipseRegion.eventForPrepared(calendarDays, 96.0D,
                                synodicDays, CelestialMath.NODAL_YEARS,
                                CelestialMath.LUNAR_INCLINATION, sine),
                        "prepared lunar event edge");
            }
        }

        Random random = new Random(0xECE17CA5L);
        for (int sample = 0; sample < 4_096; sample++) {
            double calendarDays = (random.nextDouble() * 2.0D - 1.0D) * 1.0E9D;
            double yearDays = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(30) - 5);
            double synodicDays = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(20) - 5);
            double nodalYears = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(20) - 5);
            double inclination = (random.nextDouble() * 2.0D - 1.0D) * Math.PI;
            double sine = Math.sin(inclination);
            assertPreparedEclipseEvents(calendarDays, yearDays, synodicDays, nodalYears,
                    inclination, sine, "prepared eclipse event random " + sample);
            assertPreparedEclipseEvents(calendarDays + synodicDays * 3.0D,
                    yearDays + 1.0D, synodicDays + 0.125D, nodalYears + 0.25D,
                    inclination * 0.5D, Math.sin(inclination * 0.5D),
                    "prepared eclipse event alternate " + sample);
            assertPreparedEclipseEvents(calendarDays, yearDays, synodicDays, nodalYears,
                    inclination, sine, "prepared eclipse event restored " + sample);
        }

        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = preparedEclipseEventThread("prepared-eclipse-event-a", 0, failure);
        Thread second = preparedEclipseEventThread("prepared-eclipse-event-b", 1, failure);
        first.start();
        second.start();
        try {
            first.join();
            second.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("prepared eclipse event thread test interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("prepared eclipse event cache was not thread isolated",
                    failure.get());
        }
    }

    private static Thread preparedEclipseEventThread(
            String name, int lane,
            java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                for (int sample = 0; sample < 4_096; sample++) {
                    double calendarDays = lane * 0.375D + sample * 0.015625D;
                    double yearDays = 72.0D + lane * 19.0D + sample % 7;
                    double synodicDays = 8.0D + lane * 3.0D + sample % 5 * 0.125D;
                    double nodalYears = 12.0D + lane * 5.0D + sample % 11 * 0.25D;
                    double inclination = Math.toRadians(2.0D + lane + sample % 13 * 0.1D);
                    assertPreparedEclipseEvents(calendarDays, yearDays, synodicDays,
                            nodalYears, inclination, Math.sin(inclination),
                            name + " sample " + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static void assertPreparedEclipseEvents(
            double calendarDays, double daysInYear, double synodicDays,
            double nodalYears, double lunarInclination, double sineLunarInclination,
            String name) {
        assertSolarEventRaw(SolarEclipseRegion.eventFor(calendarDays, daysInYear,
                        synodicDays, nodalYears, lunarInclination, sineLunarInclination),
                SolarEclipseRegion.eventForPrepared(calendarDays, daysInYear, synodicDays,
                        nodalYears, lunarInclination, sineLunarInclination), name + " solar");
        assertLunarEventRaw(LunarEclipseRegion.eventFor(calendarDays, daysInYear,
                        synodicDays, nodalYears, lunarInclination, sineLunarInclination),
                LunarEclipseRegion.eventForPrepared(calendarDays, daysInYear, synodicDays,
                        nodalYears, lunarInclination, sineLunarInclination), name + " lunar");
    }

    private static void cachedCoordinateTransformsMatchLegacyBits() {
        double[] longitudes = {Double.NEGATIVE_INFINITY, -CelestialMath.TAU * 3.0D,
                -Math.PI, -0.0D, 0.0D, 0.125D, Math.PI,
                CelestialMath.TAU * 4.25D, Double.NaN};
        double[] latitudes = {-Math.PI * 0.5D, -1.0D, -0.0D, 0.0D, 0.75D,
                Math.PI * 0.5D, Double.POSITIVE_INFINITY, Double.NaN};
        double[] siderealAngles = {-CelestialMath.TAU * 2.0D, -Math.PI, -0.0D,
                0.0D, 0.3D, Math.PI, CelestialMath.TAU * 3.5D,
                Double.POSITIVE_INFINITY, Double.NaN};
        for (double longitude : longitudes) {
            for (double eclipticLatitude : latitudes) {
                CelestialVector legacy = legacyEclipticToEquatorial(longitude,
                        eclipticLatitude, CelestialMath.AXIAL_TILT);
                assertVectorRaw(legacy, CelestialMath.eclipticToEquatorial(longitude,
                                eclipticLatitude, CelestialMath.AXIAL_TILT),
                        "scalar generic ecliptic transform");
                CelestialVector cached = CelestialMath.eclipticToEquatorialFixedTilt(
                        longitude, eclipticLatitude);
                assertVectorRaw(legacy, cached, "cached fixed-tilt transform");
                for (double observerLatitude : latitudes) {
                    for (double sidereal : siderealAngles) {
                        CelestialVector expected = legacyEquatorialToHorizon(legacy,
                                observerLatitude, sidereal);
                        CelestialVector actual = CelestialMath.equatorialToHorizon(legacy,
                                observerLatitude, sidereal);
                        assertVectorRaw(expected, actual, "cached public horizon transform");
                        CelestialVector prepared = CelestialMath.equatorialToHorizon(legacy,
                                Math.sin(observerLatitude), Math.cos(observerLatitude),
                                Math.sin(sidereal), Math.cos(sidereal));
                        assertVectorRaw(expected, prepared, "prepared horizon transform");
                        CelestialVector scalar = CelestialMath.equatorialToHorizon(
                                legacy.x(), legacy.y(), legacy.z(),
                                Math.sin(observerLatitude), Math.cos(observerLatitude),
                                Math.sin(sidereal), Math.cos(sidereal));
                        assertVectorRaw(expected, scalar, "scalar horizon transform");
                    }
                }
            }
        }
    }

    private static CelestialVector legacyEclipticToEquatorial(double longitude, double latitude,
                                                               double obliquity) {
        double cosLatitude = Math.cos(latitude);
        double x = cosLatitude * Math.cos(longitude);
        double y = cosLatitude * Math.sin(longitude);
        double z = Math.sin(latitude);
        double cosObliquity = Math.cos(obliquity);
        double sinObliquity = Math.sin(obliquity);
        return new CelestialVector(x,
                y * cosObliquity - z * sinObliquity,
                y * sinObliquity + z * cosObliquity).normalized();
    }

    private static CelestialVector legacyEquatorialToHorizon(CelestialVector equatorial,
                                                               double latitude,
                                                               double localSiderealAngle) {
        double meridian = equatorial.x() * Math.cos(localSiderealAngle)
                + equatorial.y() * Math.sin(localSiderealAngle);
        double east = -equatorial.x() * Math.sin(localSiderealAngle)
                + equatorial.y() * Math.cos(localSiderealAngle);
        double north = -Math.sin(latitude) * meridian + Math.cos(latitude) * equatorial.z();
        double up = Math.cos(latitude) * meridian + Math.sin(latitude) * equatorial.z();
        return new CelestialVector(east, up, north).normalized();
    }

    private static double legacySunBasedDayTime(double z, double scale,
                                                double fractionOfYear,
                                                double fractionOfDay) {
        double current = legacySunElevation(z, scale, fractionOfYear, fractionOfDay);
        double midnight = legacySunElevation(z, scale, fractionOfYear, 0.0D);
        double noon = legacySunElevation(z, scale, fractionOfYear, 0.5D);
        return CelestialMath.sunBasedDayTimeFromElevations(fractionOfDay, current, midnight, noon);
    }

    private static double legacySunElevation(double z, double scale, double fractionOfYear,
                                             double fractionOfDay) {
        double latitude = CelestialMath.latitude(z, scale);
        double longitude = CelestialMath.TAU * positiveModulo(
                284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = CelestialMath.AXIAL_TILT * Math.sin(longitude);
        double hourAngle = CelestialMath.TAU * (0.5D - fractionOfDay);
        double cosineZenith = Math.sin(latitude) * Math.sin(declination)
                + Math.cos(latitude) * Math.cos(declination) * Math.cos(hourAngle);
        return Math.PI * 0.5D - Math.acos(testClamp(cosineZenith, -1.0D, 1.0D));
    }

    private static void optimizedAngleMatchesLegacyBits() {
        CelestialVector[] edges = {CelestialVector.ZERO,
                new CelestialVector(-0.0D, 0.0D, -0.0D),
                new CelestialVector(Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_VALUE),
                new CelestialVector(Double.MIN_NORMAL, 0.0D, -Double.MIN_NORMAL),
                new CelestialVector(Double.MAX_VALUE, 1.0D, -1.0D),
                new CelestialVector(Double.NaN, 0.0D, 1.0D),
                new CelestialVector(Double.POSITIVE_INFINITY, 0.0D, 1.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        for (int first = 0; first < edges.length; first++) {
            for (int second = 0; second < edges.length; second++) {
                assertRawDouble(legacyAngle(edges[first], edges[second]),
                        CelestialMath.angle(edges[first], edges[second]),
                        "scalar angle edge " + first + "/" + second);
            }
        }
        Random random = new Random(0xA1161EL);
        for (int sample = 0; sample < 4_096; sample++) {
            double firstScale = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(1200) - 600);
            double secondScale = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(1200) - 600);
            CelestialVector first = new CelestialVector(
                    (random.nextDouble() * 2.0D - 1.0D) * firstScale,
                    (random.nextDouble() * 2.0D - 1.0D) * firstScale,
                    (random.nextDouble() * 2.0D - 1.0D) * firstScale);
            CelestialVector second = new CelestialVector(
                    (random.nextDouble() * 2.0D - 1.0D) * secondScale,
                    (random.nextDouble() * 2.0D - 1.0D) * secondScale,
                    (random.nextDouble() * 2.0D - 1.0D) * secondScale);
            assertRawDouble(legacyAngle(first, second), CelestialMath.angle(first, second),
                    "scalar angle random " + sample);
        }
    }

    private static double legacyAngle(CelestialVector first, CelestialVector second) {
        return Math.acos(testClamp(first.normalized().dot(second.normalized()), -1.0D, 1.0D));
    }

    private static void optimizedSquareCoverageMatchesLegacyBits() {
        Random random = new Random(0x51A7E5L);
        double[] halfTangents = {1.0E-6D, 0.001D, 0.01D, 0.075D, 0.25D};
        for (int sample = 0; sample < 180; sample++) {
            CelestialVector first = randomUnitVector(random).scale(Math.scalb(
                    0.5D + random.nextDouble(), random.nextInt(40) - 20));
            CelestialVector second = randomUnitVector(random).scale(Math.scalb(
                    0.5D + random.nextDouble(), random.nextInt(40) - 20));
            CelestialVector north = sample % 9 == 0 ? first : randomUnitVector(random);
            double firstHalf = halfTangents[sample % halfTangents.length];
            double secondHalf = halfTangents[(sample * 3 + 1) % halfTangents.length];
            double expected = legacySquareCoverage(first, second, north, firstHalf, secondHalf);
            double actual = CelestialDiscGeometry.squareCoverage(first, second, north,
                    firstHalf, secondHalf);
            assertRawDouble(expected, actual, "optimized square coverage sample " + sample);
            CelestialDiscGeometry.PreparedSquare prepared =
                    CelestialDiscGeometry.prepareFirstSquare(first, north);
            assertRawDouble(expected, CelestialDiscGeometry.squareCoverage(prepared, second,
                            firstHalf, secondHalf),
                    "prepared first square coverage sample " + sample);
            assertRawDouble(expected, CelestialDiscGeometry.squareCoveragePrepared(prepared, second,
                            firstHalf, secondHalf),
                    "trusted prepared square coverage sample " + sample);
            assertAlignedSquareRaw(legacyAlignedSquareProjection(first, second, north,
                            firstHalf, secondHalf),
                    CelestialDiscGeometry.alignedSquareProjection(first, second, north,
                            firstHalf, secondHalf),
                    "optimized aligned square sample " + sample);
        }
        CelestialVector[] edgeVectors = {null, CelestialVector.ZERO,
                new CelestialVector(-0.0D, 0.0D, -0.0D),
                new CelestialVector(Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_VALUE),
                new CelestialVector(Double.MIN_NORMAL, 0.0D, -Double.MIN_NORMAL),
                new CelestialVector(Double.MAX_VALUE, 1.0D, -1.0D),
                new CelestialVector(Double.NaN, 0.0D, 1.0D),
                new CelestialVector(Double.POSITIVE_INFINITY, 0.0D, 1.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        CelestialVector[] edgeNorths = {null, CelestialVector.ZERO,
                new CelestialVector(-0.0D, 1.0D, 0.0D),
                new CelestialVector(Double.NaN, 0.0D, 1.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        for (int firstIndex = 0; firstIndex < edgeVectors.length; firstIndex++) {
            for (int secondIndex = 0; secondIndex < edgeVectors.length; secondIndex++) {
                for (int northIndex = 0; northIndex < edgeNorths.length; northIndex++) {
                    CelestialVector first = edgeVectors[firstIndex];
                    CelestialVector second = edgeVectors[secondIndex];
                    CelestialVector north = edgeNorths[northIndex];
                    assertRawDouble(legacySquareCoverage(first, second, north, 0.075D, 0.01D),
                            CelestialDiscGeometry.squareCoverage(first, second, north,
                                    0.075D, 0.01D),
                            "scalar square coverage edge " + firstIndex + "/" + secondIndex
                                    + "/" + northIndex);
                    assertAlignedSquareRaw(legacyAlignedSquareProjection(first, second, north,
                                    0.075D, 0.01D),
                            CelestialDiscGeometry.alignedSquareProjection(first, second, north,
                                    0.075D, 0.01D),
                            "scalar aligned square edge " + firstIndex + "/" + secondIndex
                                    + "/" + northIndex);
                }
            }
        }
        assertRawDouble(legacySquareCoverage(new CelestialVector(0.0D, 0.0D, 1.0D),
                        new CelestialVector(0.0D, 0.0D, 1.0D), CelestialVector.ZERO,
                        0.05D, 0.05D),
                CelestialDiscGeometry.squareCoverage(new CelestialVector(0.0D, 0.0D, 1.0D),
                        new CelestialVector(0.0D, 0.0D, 1.0D), CelestialVector.ZERO,
                        0.05D, 0.05D), "square coverage fallback basis");
        assertAlignedSquareRaw(legacyAlignedSquareProjection(CelestialVector.ZERO,
                        new CelestialVector(0.0D, 0.0D, 1.0D), CelestialVector.ZERO,
                        0.05D, 0.05D),
                CelestialDiscGeometry.alignedSquareProjection(CelestialVector.ZERO,
                        new CelestialVector(0.0D, 0.0D, 1.0D), CelestialVector.ZERO,
                        0.05D, 0.05D), "aligned square invalid direction");
        assertRawDouble(0.0D, CelestialDiscGeometry.squareCoverage(
                        CelestialDiscGeometry.prepareFirstSquare(CelestialVector.ZERO,
                                CelestialVector.ZERO),
                        new CelestialVector(0.0D, 0.0D, 1.0D), 0.05D, 0.05D),
                "prepared square invalid first direction");
        preparedRawDirectionMatchesObjectPathBits();
        preparedSolarCoverageMatchesLegacyBits();
    }

    private static void optimizedLunarProjectionMatchesLegacyBits() {
        LunarEclipseRegion.Event event = new LunarEclipseRegion.Event(17L, 273.25D,
                Math.toRadians(0.37D), Math.toRadians(0.19D), true);
        CelestialVector[] edges = {null, CelestialVector.ZERO,
                new CelestialVector(-0.0D, 0.0D, -0.0D),
                new CelestialVector(Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_VALUE),
                new CelestialVector(Double.MIN_NORMAL, 0.0D, -Double.MIN_NORMAL),
                new CelestialVector(Double.MAX_VALUE, 1.0D, -1.0D),
                new CelestialVector(Double.NaN, 0.0D, 1.0D),
                new CelestialVector(Double.POSITIVE_INFINITY, 0.0D, 1.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        CelestialVector[] norths = {null, CelestialVector.ZERO,
                new CelestialVector(-0.0D, 1.0D, 0.0D),
                new CelestialVector(Double.NaN, 0.0D, 1.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        for (int moonIndex = 0; moonIndex < edges.length; moonIndex++) {
            for (int sunIndex = 0; sunIndex < edges.length; sunIndex++) {
                for (int northIndex = 0; northIndex < norths.length; northIndex++) {
                    assertLunarProjectionOptimization(event, edges[moonIndex], edges[sunIndex],
                            norths[northIndex], 0.075D, 0.075D,
                            "lunar projection edge " + moonIndex + "/" + sunIndex + "/"
                                    + northIndex);
                }
            }
        }
        double[] invalidTangents = {Double.NaN, Double.NEGATIVE_INFINITY, -1.0D, -0.0D,
                0.0D, Double.MIN_VALUE, Double.POSITIVE_INFINITY};
        CelestialVector moon = new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector sun = new CelestialVector(0.0D, 0.0D, -1.0D);
        for (double tangent : invalidTangents) {
            assertLunarProjectionOptimization(event, moon, sun, CelestialVector.ZERO,
                    tangent, 0.075D, "lunar projection first tangent " + tangent);
            assertLunarProjectionOptimization(event, moon, sun, CelestialVector.ZERO,
                    0.075D, tangent, "lunar projection second tangent " + tangent);
        }
        assertLunarProjectionOptimization(null, moon, sun, CelestialVector.ZERO,
                0.075D, 0.075D, "lunar projection null event");
        assertLunarProjectionOptimization(LunarEclipseRegion.Event.NONE, moon, sun,
                CelestialVector.ZERO, 0.075D, 0.075D, "lunar projection invalid event");

        Random random = new Random(0x1A4E5EEDL);
        for (int sample = 0; sample < 4_096; sample++) {
            double moonScale = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(1200) - 600);
            double sunScale = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(1200) - 600);
            CelestialVector randomMoon = randomUnitVector(random).scale(moonScale);
            CelestialVector randomSun = randomUnitVector(random).scale(sunScale);
            CelestialVector randomNorth = sample % 17 == 0 ? randomMoon
                    : sample % 19 == 0 ? randomMoon.negated() : randomUnitVector(random);
            LunarEclipseRegion.Event randomEvent = new LunarEclipseRegion.Event(
                    random.nextLong(), (random.nextDouble() * 2.0D - 1.0D) * 1.0E8D,
                    (random.nextDouble() * 2.0D - 1.0D) * 1.4D,
                    (random.nextDouble() * 2.0D - 1.0D) * 1.4D, true);
            double moonHalf = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(40) - 20);
            double shadowHalf = Math.scalb(0.5D + random.nextDouble(),
                    random.nextInt(40) - 20);
            assertLunarProjectionOptimization(randomEvent, randomMoon, randomSun, randomNorth,
                    moonHalf, shadowHalf, "lunar projection random " + sample);
        }
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = lunarProjectionThread(1, failure);
        Thread second = lunarProjectionThread(2, failure);
        first.start();
        second.start();
        try {
            first.join();
            second.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lunar projection thread test interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("thread-confined lunar projection output changed",
                    failure.get());
        }
    }

    private static Thread lunarProjectionThread(int lane,
                                                 java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                Random random = new Random(0x1A4E5EEDL + lane * 0x10_001L);
                for (int sample = 0; sample < 1_024; sample++) {
                    CelestialVector moon = randomUnitVector(random).scale(0.5D + lane);
                    CelestialVector sun = randomUnitVector(random).scale(1.5D + sample % 7);
                    CelestialVector north = sample % 29 == 0 ? moon : randomUnitVector(random);
                    LunarEclipseRegion.Event event = new LunarEclipseRegion.Event(
                            lane * 10_000L + sample, lane * 100.0D + sample * 0.125D,
                            Math.toRadians((sample % 37 - 18) * 0.05D),
                            Math.toRadians((sample % 41 - 20) * 0.04D), true);
                    double moonHalf = 0.01D + (sample % 17) * 0.0025D;
                    double shadowHalf = 0.015D + (sample % 23) * 0.002D;
                    assertLunarProjectionOptimization(event, moon, sun, north,
                            moonHalf, shadowHalf,
                            "lunar projection thread " + lane + "/" + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, "lunar-projection-" + lane);
    }

    private static void assertLunarProjectionOptimization(
            LunarEclipseRegion.Event event, CelestialVector moonDirection,
            CelestialVector sunDirection, CelestialVector celestialNorth,
            double moonHalfTangent, double shadowHalfTangent, String name) {
        LunarEclipseRegion.Projection expected = legacyLunarProjection(event, moonDirection,
                sunDirection, celestialNorth, moonHalfTangent, shadowHalfTangent);
        if (event != null && event.valid()) {
            assertRawDouble(Math.tan(event.effectiveLatitudeRadians())
                            - Math.tan(event.lunarLatitudeRadians()),
                    LunarEclipseRegion.projectionDelta(event), name + " projection delta");
        }
        LunarEclipseRegion.Projection actual = LunarEclipseRegion.project(event, moonDirection,
                sunDirection, celestialNorth, moonHalfTangent, shadowHalfTangent);
        if (expected.shadow().valid() != actual.shadow().valid()) {
            throw new AssertionError(name + " shadow validity changed");
        }
        assertAlignedSquareRaw(expected.shadow(), actual.shadow(), name + " public shadow");
        assertLunarEclipseStateRaw(expected.state(), actual.state(), name + " public state");
        assertLunarEclipseStateRaw(expected.state(), LunarEclipseRegion.projectState(event,
                moonDirection, sunDirection, celestialNorth, moonHalfTangent, shadowHalfTangent),
                name + " state-only");
        LunarEclipseRegion.CoverageOutput coverages = LunarEclipseRegion.projectCoverages(event,
                moonDirection, sunDirection, celestialNorth, moonHalfTangent, shadowHalfTangent);
        assertRawDouble(expected.state().umbraCoverage(), coverages.umbraCoverage(),
                name + " scalar umbra");
        assertRawDouble(expected.state().penumbraCoverage(), coverages.penumbraCoverage(),
                name + " scalar penumbra");
        assertRawDouble(expected.state().umbraCoverage(),
                LunarEclipseRegion.projectUmbraCoverage(event, moonDirection, sunDirection,
                        celestialNorth, moonHalfTangent, shadowHalfTangent),
                name + " umbra-only");
        CelestialVector legacyShadow = sunDirection == null ? null : sunDirection.negated();
        assertAlignedSquareRaw(legacyAlignedSquareProjection(moonDirection, legacyShadow,
                        celestialNorth, moonHalfTangent, shadowHalfTangent),
                CelestialDiscGeometry.alignedSquareProjectionNegatedShadow(moonDirection,
                        sunDirection, celestialNorth, moonHalfTangent, shadowHalfTangent),
                name + " negated-shadow geometry");
    }

    private static LunarEclipseRegion.Projection legacyLunarProjection(
            LunarEclipseRegion.Event event, CelestialVector moonDirection,
            CelestialVector sunDirection, CelestialVector celestialNorth,
            double moonHalfTangent, double shadowHalfTangent) {
        if (event == null || !event.valid() || !positiveFinite(moonHalfTangent)
                || !positiveFinite(shadowHalfTangent)) {
            return LunarEclipseRegion.Projection.NONE;
        }
        CelestialDiscGeometry.AlignedSquare raw = legacyAlignedSquareProjection(moonDirection,
                sunDirection == null ? null : sunDirection.negated(), celestialNorth,
                moonHalfTangent, shadowHalfTangent);
        if (!raw.valid()) {
            return LunarEclipseRegion.Projection.NONE;
        }
        double additionalProjection = Math.tan(event.effectiveLatitudeRadians())
                - Math.tan(event.lunarLatitudeRadians());
        double centerY = raw.centerY() - additionalProjection / moonHalfTangent;
        if (!Double.isFinite(centerY)) {
            return LunarEclipseRegion.Projection.NONE;
        }
        CelestialDiscGeometry.AlignedSquare shadow = new CelestialDiscGeometry.AlignedSquare(
                raw.centerX(), centerY, raw.radius(), true);
        double umbra = legacyAlignedSquareCoverage(shadow);
        CelestialDiscGeometry.AlignedSquare expanded = new CelestialDiscGeometry.AlignedSquare(
                shadow.centerX(), shadow.centerY(),
                shadow.radius() + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH, true);
        double penumbra = legacyAlignedSquareCoverage(expanded);
        LunarEclipseState state = new LunarEclipseState(event.fullMoonIndex(),
                event.fullMoonDay() * CelestialMath.TICKS_IN_DAY,
                event.lunarLatitudeRadians(), event.effectiveLatitudeRadians(),
                shadow.centerX(), shadow.centerY(), shadow.radius(), umbra, penumbra);
        return new LunarEclipseRegion.Projection(shadow, state);
    }

    private static double legacyAlignedSquareCoverage(
            CelestialDiscGeometry.AlignedSquare square) {
        if (!square.valid()) {
            return 0.0D;
        }
        double xOverlap = Math.max(0.0D, Math.min(1.0D, square.centerX() + square.radius())
                - Math.max(-1.0D, square.centerX() - square.radius()));
        double yOverlap = Math.max(0.0D, Math.min(1.0D, square.centerY() + square.radius())
                - Math.max(-1.0D, square.centerY() - square.radius()));
        return Math.max(0.0D, Math.min(1.0D, xOverlap * yOverlap / 4.0D));
    }

    private static void optimizedLunarOpportunityScaleMatchesLegacyBits() {
        double[] latitudes = {Double.NaN, -Math.PI * 0.5D, -1.0E-300D, -0.0D,
                0.0D, 1.0E-300D, Math.toRadians(0.37D), Math.PI * 0.5D};
        double[] years = {Double.longBitsToDouble(0x7ff8000000000042L),
                Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -0.0D, 0.0D,
                Double.MIN_VALUE, Double.MIN_NORMAL, 96.0D, Double.MAX_VALUE,
                Double.POSITIVE_INFINITY};
        double[] synodic = {Double.longBitsToDouble(0x7ff8000000000084L),
                Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -0.0D, 0.0D,
                Double.MIN_VALUE, Double.MIN_NORMAL, CelestialMath.SYNODIC_DAYS,
                Double.MAX_VALUE, Double.POSITIVE_INFINITY};
        for (double latitude : latitudes) {
            for (double year : years) {
                for (double period : synodic) {
                    assertRawDouble(legacyEffectiveLunarLatitude(latitude, year, period),
                            LunarEclipseRegion.effectiveLatitudeRadians(latitude, year, period),
                            "cached lunar opportunity edge");
                }
            }
        }
        Random random = new Random(0x0A77027EL);
        for (int sample = 0; sample < 8_192; sample++) {
            double latitude = (random.nextDouble() * 2.0D - 1.0D) * Math.PI * 0.5D;
            double year = Double.longBitsToDouble(random.nextLong());
            double period = Double.longBitsToDouble(random.nextLong());
            assertRawDouble(legacyEffectiveLunarLatitude(latitude, year, period),
                    LunarEclipseRegion.effectiveLatitudeRadians(latitude, year, period),
                    "cached lunar opportunity random " + sample);
            double alternateYear = 96.0D + (sample & 7);
            double alternatePeriod = CelestialMath.SYNODIC_DAYS + (sample & 3) * 0.25D;
            assertRawDouble(legacyEffectiveLunarLatitude(latitude, alternateYear, alternatePeriod),
                    LunarEclipseRegion.effectiveLatitudeRadians(latitude,
                            alternateYear, alternatePeriod),
                    "cached lunar opportunity alternate " + sample);
            assertRawDouble(legacyEffectiveLunarLatitude(latitude, year, period),
                    LunarEclipseRegion.effectiveLatitudeRadians(latitude, year, period),
                    "cached lunar opportunity restored " + sample);
        }

        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = lunarOpportunityThread("lunar-opportunity-a", 0, failure);
        Thread second = lunarOpportunityThread("lunar-opportunity-b", 1, failure);
        first.start();
        second.start();
        try {
            first.join();
            second.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("lunar opportunity cache thread test interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("lunar opportunity cache was not thread isolated",
                    failure.get());
        }
    }

    private static Thread lunarOpportunityThread(
            String name, int lane,
            java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                for (int sample = 0; sample < 2_048; sample++) {
                    double latitude = Math.toRadians((sample % 281) * 0.5D - 70.0D);
                    double year = 48.0D + lane * 37.0D + sample % 19;
                    double period = 4.0D + lane * 3.0D + sample % 13 * 0.125D;
                    assertRawDouble(legacyEffectiveLunarLatitude(latitude, year, period),
                            LunarEclipseRegion.effectiveLatitudeRadians(latitude, year, period),
                            name + " sample " + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static double legacyEffectiveLunarLatitude(double lunarLatitude,
                                                        double daysInYear,
                                                        double synodicDays) {
        if (!Double.isFinite(lunarLatitude)) {
            return Double.NaN;
        }
        double absoluteLatitude = Math.abs(lunarLatitude);
        double x = Math.max(0.0D, Math.min(1.0D,
                (absoluteLatitude - LunarEclipseRegion.INNER_TRANSITION_LATITUDE)
                        / (LunarEclipseRegion.OUTER_TRANSITION_LATITUDE
                        - LunarEclipseRegion.INNER_TRANSITION_LATITUDE)));
        double blend = x * x * (3.0D - 2.0D * x);
        double multiplier = LunarEclipseRegion.CENTER_LATITUDE_MULTIPLIER
                + (LunarEclipseRegion.OUTER_LATITUDE_MULTIPLIER
                - LunarEclipseRegion.CENTER_LATITUDE_MULTIPLIER) * blend;
        double referenceOpportunities = LunarEclipseRegion.REFERENCE_YEAR_DAYS
                / CelestialMath.SYNODIC_DAYS;
        double annualOpportunities = positiveFinite(daysInYear) && positiveFinite(synodicDays)
                ? daysInYear / synodicDays : referenceOpportunities;
        double opportunityScale = Math.pow(annualOpportunities / referenceOpportunities,
                LunarEclipseRegion.ANNUAL_OPPORTUNITY_EXPONENT);
        double maximumProjectionLatitude = Math.PI * 0.5D - 1.0E-6D;
        return Math.max(-maximumProjectionLatitude,
                Math.min(maximumProjectionLatitude,
                        lunarLatitude * multiplier * opportunityScale));
    }

    private static void preparedRawDirectionMatchesObjectPathBits() {
        CelestialDiscGeometry.PreparedSquare[] preparedSquares = {
                CelestialDiscGeometry.prepareFirstSquare(
                        new CelestialVector(0.0D, 0.0D, 1.0D),
                        new CelestialVector(0.0D, 0.6D, 0.8D)),
                CelestialDiscGeometry.prepareFirstSquare(
                        new CelestialVector(0.5D, -0.25D, 2.0D),
                        new CelestialVector(0.5D, -0.25D, 2.0D)),
                CelestialDiscGeometry.prepareFirstSquare(
                        new CelestialVector(-2.0D, 0.75D, 0.125D), CelestialVector.ZERO),
                CelestialDiscGeometry.prepareFirstSquare(
                        new CelestialVector(0.25D, 1.0D, -0.5D),
                        new CelestialVector(Double.NaN, 0.0D, 1.0D))
        };
        double[][] edgeDirections = {
                {0.0D, 0.0D, 1.0D}, {-0.0D, 0.0D, 1.0D},
                {0.0D, -0.0D, 1.0D}, {-0.0D, -0.0D, -0.0D},
                {Double.MIN_VALUE, -Double.MIN_VALUE, Double.MIN_VALUE},
                {Double.MIN_NORMAL, -Double.MIN_NORMAL, Double.MIN_NORMAL},
                {Double.MAX_VALUE, 1.0D, 1.0D},
                {Double.NaN, 0.0D, 1.0D},
                {Double.POSITIVE_INFINITY, 0.0D, 1.0D},
                {0.0D, Double.NEGATIVE_INFINITY, 1.0D}
        };
        double[] halfTangents = {1.0E-6D, 0.001D, 0.075D, 0.25D};
        for (int preparedIndex = 0; preparedIndex < preparedSquares.length; preparedIndex++) {
            CelestialDiscGeometry.PreparedSquare prepared = preparedSquares[preparedIndex];
            for (int index = 0; index < edgeDirections.length; index++) {
                double[] direction = edgeDirections[index];
                double firstHalf = halfTangents[index % halfTangents.length];
                double secondHalf = halfTangents[(index + 1) % halfTangents.length];
                CelestialVector normalized = new CelestialVector(direction[0], direction[1],
                        direction[2]).normalized();
                double expected = CelestialDiscGeometry.squareCoveragePrepared(prepared, normalized,
                        firstHalf, secondHalf);
                double actual = CelestialDiscGeometry.squareCoveragePreparedRaw(prepared,
                        direction[0], direction[1], direction[2], firstHalf, secondHalf);
                assertRawDouble(expected, actual, "prepared raw direction edge "
                        + preparedIndex + "/" + index);
            }
        }
        Random random = new Random(0x5CA1A2L);
        for (int preparedIndex = 0; preparedIndex < preparedSquares.length; preparedIndex++) {
            CelestialDiscGeometry.PreparedSquare prepared = preparedSquares[preparedIndex];
            for (int sample = 0; sample < 4_096; sample++) {
                double scale = Math.scalb(0.5D + random.nextDouble(), random.nextInt(1200) - 600);
                double x = (random.nextDouble() * 2.0D - 1.0D) * scale;
                double y = (random.nextDouble() * 2.0D - 1.0D) * scale;
                double z = (random.nextDouble() * 2.0D - 1.0D) * scale;
                double firstHalf = halfTangents[sample % halfTangents.length];
                double secondHalf = halfTangents[(sample * 3 + 1) % halfTangents.length];
                CelestialVector normalized = new CelestialVector(x, y, z).normalized();
                double expected = CelestialDiscGeometry.squareCoveragePrepared(prepared, normalized,
                        firstHalf, secondHalf);
                double actual = CelestialDiscGeometry.squareCoveragePreparedRaw(prepared,
                        x, y, z, firstHalf, secondHalf);
                assertRawDouble(expected, actual, "prepared raw direction random "
                        + preparedIndex + "/" + sample);
            }
        }
    }

    private static void preparedSolarCoverageMatchesLegacyBits() {
        double daysInYear = 96.0D;
        double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(0.725D));
        double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.moonBodyHalfSize(1.0D, 0.93D),
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        int compared = 0;
        for (long index = -96; index <= 192; index++) {
            SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, daysInYear,
                    CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                    CelestialMath.LUNAR_INCLINATION);
            if (!event.intersectsWorld()) {
                continue;
            }
            double contact = sunHalf + moonHalf;
            double halfDuration = Math.atan(contact) * CelestialMath.SYNODIC_DAYS
                    / CelestialMath.TAU;
            for (int time = 0; time <= 16; time++) {
                double day = event.conjunctionDay()
                        + (time / 16.0D * 2.0D - 1.0D) * halfDuration;
                SolarEclipseRegion.PreparedCoverage prepared =
                        SolarEclipseRegion.prepareCoverageAtTime(event, day, sunHalf,
                                moonHalf, CelestialMath.SYNODIC_DAYS);
                if (prepared == null) {
                    throw new AssertionError("valid solar contact failed to prepare");
                }
                for (int latitude = 0; latitude <= 32; latitude++) {
                    double radians = -Math.PI * 0.5D + Math.PI * latitude / 32.0D;
                    double expected = SolarEclipseRegion.coverageAt(event, day, radians,
                            sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS);
                    assertRawDouble(expected, prepared.coverageAt(radians),
                            "prepared solar coverage " + index + "/" + time + "/" + latitude);
                    assertRawDouble(expected, prepared.coverageAtPrepared(radians),
                            "trusted prepared solar coverage " + index + "/" + time + "/" + latitude);
                    compared++;
                }
            }
        }
        if (compared < 1_000) {
            throw new AssertionError("prepared solar coverage proof sampled too few contacts: "
                    + compared);
        }
    }

    private static CelestialDiscGeometry.AlignedSquare legacyAlignedSquareProjection(
            CelestialVector firstDirection, CelestialVector shadowDirection,
            CelestialVector celestialNorth, double firstHalfTangent,
            double shadowHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(shadowHalfTangent)
                || !finiteDirection(firstDirection) || !finiteDirection(shadowDirection)) {
            return CelestialDiscGeometry.AlignedSquare.NONE;
        }
        CelestialVector first = firstDirection.normalized();
        CelestialVector shadow = shadowDirection.normalized();
        double forward = shadow.dot(first);
        if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
            return CelestialDiscGeometry.AlignedSquare.NONE;
        }
        CelestialDiscGeometry.Basis basis = CelestialDiscGeometry.stableBasis(first,
                celestialNorth);
        double centerScale = forward * firstHalfTangent;
        double centerX = shadow.dot(basis.right()) / centerScale;
        double centerY = shadow.dot(basis.up()) / centerScale;
        double radius = shadowHalfTangent / firstHalfTangent;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !positiveFinite(radius)) {
            return CelestialDiscGeometry.AlignedSquare.NONE;
        }
        return new CelestialDiscGeometry.AlignedSquare(centerX, centerY, radius, true);
    }

    private static void assertAlignedSquareRaw(CelestialDiscGeometry.AlignedSquare expected,
                                               CelestialDiscGeometry.AlignedSquare actual,
                                               String name) {
        if (expected.valid() != actual.valid()) {
            throw new AssertionError(name + " validity changed");
        }
        assertRawDouble(expected.centerX(), actual.centerX(), name + " center x");
        assertRawDouble(expected.centerY(), actual.centerY(), name + " center y");
        assertRawDouble(expected.radius(), actual.radius(), name + " radius");
    }

    private static void optimizedStableBasisMatchesLegacyBits() {
        CelestialVector[] directions = {null, CelestialVector.ZERO,
                new CelestialVector(-0.0D, 1.0D, 0.0D),
                new CelestialVector(0.0D, -0.0D, -1.0D),
                new CelestialVector(Double.NaN, 0.0D, 0.0D),
                new CelestialVector(Double.POSITIVE_INFINITY, 1.0D, 0.0D),
                new CelestialVector(1.0D, 1.0D, 1.0D)};
        CelestialVector[] norths = {null, CelestialVector.ZERO,
                new CelestialVector(0.0D, -0.0D, 1.0D),
                new CelestialVector(-0.0D, 1.0D, 0.0D),
                new CelestialVector(0.0D, Double.NEGATIVE_INFINITY, 0.0D),
                new CelestialVector(1.0D, -2.0D, 3.0D)};
        for (CelestialVector direction : directions) {
            for (CelestialVector north : norths) {
                assertBasisRaw(legacyStableBasis(direction, north),
                        CelestialDiscGeometry.stableBasis(direction, north),
                        "scalar stable basis edge case");
            }
        }
        Random random = new Random(0xBA515L);
        for (int sample = 0; sample < 1_000; sample++) {
            CelestialVector direction = randomUnitVector(random);
            CelestialVector north = sample % 17 == 0 ? direction
                    : sample % 19 == 0 ? direction.negated() : randomUnitVector(random);
            assertBasisRaw(legacyStableBasis(direction, north),
                    CelestialDiscGeometry.stableBasis(direction, north),
                    "scalar stable basis random sample " + sample);
        }
    }

    private static CelestialDiscGeometry.Basis legacyStableBasis(
            CelestialVector bodyDirection, CelestialVector celestialNorth) {
        CelestialVector xAxis = new CelestialVector(1.0D, 0.0D, 0.0D);
        CelestialVector yAxis = new CelestialVector(0.0D, 1.0D, 0.0D);
        CelestialVector zAxis = new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector direction = legacyFiniteUnit(bodyDirection, yAxis);
        CelestialVector north = legacyFiniteUnit(celestialNorth, zAxis);
        CelestialVector up = north.subtract(direction.scale(direction.dot(north)));
        if (up.lengthSquared() < 1.0E-12D) {
            CelestialVector fallback = legacyLeastAlignedAxis(direction, xAxis, yAxis, zAxis);
            up = fallback.subtract(direction.scale(direction.dot(fallback)));
        }
        up = legacyFiniteUnit(up, zAxis);
        CelestialVector right = legacyFiniteUnit(testCross(up, direction), xAxis);
        up = legacyFiniteUnit(testCross(direction, right), up);
        return new CelestialDiscGeometry.Basis(right, up);
    }

    private static CelestialVector legacyFiniteUnit(CelestialVector vector,
                                                     CelestialVector fallback) {
        return finiteDirection(vector) ? vector.normalized() : fallback;
    }

    private static CelestialVector legacyLeastAlignedAxis(CelestialVector direction,
                                                           CelestialVector xAxis,
                                                           CelestialVector yAxis,
                                                           CelestialVector zAxis) {
        double x = Math.abs(direction.x());
        double y = Math.abs(direction.y());
        double z = Math.abs(direction.z());
        if (x <= y && x <= z) return xAxis;
        return y <= z ? yAxis : zAxis;
    }

    private static void assertBasisRaw(CelestialDiscGeometry.Basis expected,
                                       CelestialDiscGeometry.Basis actual, String name) {
        assertVectorRaw(expected.right(), actual.right(), name + " right");
        assertVectorRaw(expected.up(), actual.up(), name + " up");
    }

    private static CelestialVector randomUnitVector(Random random) {
        CelestialVector value;
        do {
            value = new CelestialVector(random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 2.0D - 1.0D);
        } while (value.lengthSquared() <= 1.0E-12D);
        return value.normalized();
    }

    private static double legacySquareCoverage(CelestialVector firstDirection,
                                               CelestialVector secondDirection,
                                               CelestialVector celestialNorth,
                                               double firstHalfTangent,
                                               double secondHalfTangent) {
        if (!positiveFinite(firstHalfTangent) || !positiveFinite(secondHalfTangent)
                || !finiteDirection(firstDirection) || !finiteDirection(secondDirection)) {
            return 0.0D;
        }
        CelestialVector first = firstDirection.normalized();
        CelestialVector second = secondDirection.normalized();
        CelestialDiscGeometry.Basis firstBasis = CelestialDiscGeometry.stableBasis(first,
                celestialNorth);
        CelestialDiscGeometry.Basis secondBasis = CelestialDiscGeometry.stableBasis(second,
                celestialNorth);
        double[] x1 = new double[12];
        double[] y1 = new double[12];
        double[] x2 = new double[12];
        double[] y2 = new double[12];
        double[][] corners = {{-1.0D, -1.0D}, {1.0D, -1.0D},
                {1.0D, 1.0D}, {-1.0D, 1.0D}};
        for (int index = 0; index < corners.length; index++) {
            double[] corner = corners[index];
            CelestialVector ray = second.add(secondBasis.right().scale(
                            corner[0] * secondHalfTangent))
                    .add(secondBasis.up().scale(corner[1] * secondHalfTangent));
            double forward = ray.dot(first);
            if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
                return 0.0D;
            }
            double x = ray.dot(firstBasis.right()) / forward;
            double y = ray.dot(firstBasis.up()) / forward;
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return 0.0D;
            }
            x1[index] = x;
            y1[index] = y;
        }
        int count = legacyClip(x1, y1, 4, x2, y2, true,
                -firstHalfTangent, true);
        count = legacyClip(x2, y2, count, x1, y1, true,
                firstHalfTangent, false);
        count = legacyClip(x1, y1, count, x2, y2, false,
                -firstHalfTangent, true);
        count = legacyClip(x2, y2, count, x1, y1, false,
                firstHalfTangent, false);
        double area = legacyPolygonArea(x1, y1, count);
        double firstArea = 4.0D * firstHalfTangent * firstHalfTangent;
        return testClamp(area / firstArea, 0.0D, 1.0D);
    }

    private static int legacyClip(double[] inputX, double[] inputY, int count,
                                  double[] outputX, double[] outputY, boolean xAxis,
                                  double boundary, boolean keepGreater) {
        if (count == 0) return 0;
        int outputCount = 0;
        double previousX = inputX[count - 1];
        double previousY = inputY[count - 1];
        boolean previousInside = legacyInside(previousX, previousY, xAxis, boundary, keepGreater);
        for (int index = 0; index < count; index++) {
            double currentX = inputX[index];
            double currentY = inputY[index];
            boolean currentInside = legacyInside(currentX, currentY, xAxis, boundary, keepGreater);
            if (currentInside != previousInside) {
                double previousValue = xAxis ? previousX : previousY;
                double currentValue = xAxis ? currentX : currentY;
                double denominator = currentValue - previousValue;
                double fraction = Math.abs(denominator) < 1.0E-15D ? 0.0D
                        : testClamp((boundary - previousValue) / denominator, 0.0D, 1.0D);
                outputX[outputCount] = previousX + (currentX - previousX) * fraction;
                outputY[outputCount] = previousY + (currentY - previousY) * fraction;
                outputCount++;
            }
            if (currentInside) {
                outputX[outputCount] = currentX;
                outputY[outputCount] = currentY;
                outputCount++;
            }
            previousX = currentX;
            previousY = currentY;
            previousInside = currentInside;
        }
        return outputCount;
    }

    private static boolean legacyInside(double x, double y, boolean xAxis,
                                        double boundary, boolean keepGreater) {
        double value = xAxis ? x : y;
        return keepGreater ? value >= boundary : value <= boundary;
    }

    private static double legacyPolygonArea(double[] x, double[] y, int count) {
        if (count < 3) return 0.0D;
        double twiceArea = 0.0D;
        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;
            twiceArea += x[index] * y[next] - y[index] * x[next];
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    private static void optimizedSolarSearchMatchesLegacyBits() {
        double daysInYear = CelestialMath.daysInYear(8);
        double sunHalf = 0.054375D;
        double moonHalf = 0.050125D;
        for (long index = -24L; index <= 24L; index += 6L) {
            SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, daysInYear,
                    CelestialMath.SYNODIC_DAYS, CelestialMath.NODAL_YEARS,
                    CelestialMath.LUNAR_INCLINATION);
            for (double offset : new double[]{-0.12D, -0.03D, 0.0D, 0.04D, 0.13D}) {
                double day = event.conjunctionDay() + offset;
                assertRawDouble(legacyMaximumCoverageAtTime(event, day, sunHalf, moonHalf,
                                CelestialMath.SYNODIC_DAYS),
                        SolarEclipseRegion.maximumCoverageAtTime(event, day, sunHalf, moonHalf,
                                CelestialMath.SYNODIC_DAYS),
                        "prepared global eclipse search " + index + "/" + offset);
                for (double threshold : new double[]{0.0D, Double.MIN_VALUE, 0.01D,
                        0.2D, 0.5D, 0.8D, 0.999D, 1.0D}) {
                    assertRawDouble(legacyLatitudeHalfWidthAt(event, day, sunHalf, moonHalf,
                                    CelestialMath.SYNODIC_DAYS, threshold),
                            SolarEclipseRegion.latitudeHalfWidthAt(event, day, sunHalf, moonHalf,
                                    CelestialMath.SYNODIC_DAYS, threshold),
                            "prepared latitude edge " + index + "/" + offset + "/" + threshold);
                }
            }
            for (double latitude : new double[]{-Math.PI * 0.5D, -1.0D, -0.25D, 0.0D,
                    0.25D, 1.0D, Math.PI * 0.5D}) {
                assertRawDouble(legacyMaximumCoverageAtLatitude(event, latitude, sunHalf,
                                moonHalf, CelestialMath.SYNODIC_DAYS),
                        SolarEclipseRegion.maximumCoverageAtLatitude(event, latitude, sunHalf,
                                moonHalf, CelestialMath.SYNODIC_DAYS),
                        "prepared latitude eclipse search " + index + "/" + latitude);
            }
            double[] batched = new double[721];
            double[] latitudes = new double[721];
            for (int latitudeIndex = 0; latitudeIndex <= 720; latitudeIndex++) {
                double latitude = -Math.PI * 0.5D + Math.PI * latitudeIndex / 720;
                latitudes[latitudeIndex] = latitude;
                assertRawDouble(latitude,
                        EclipsePredictionService.solarLatitudeSample(latitudeIndex),
                        "prepared latitude grid " + latitudeIndex);
            }
            SolarEclipseRegion.maximumCoverageAtLatitudeSamples(event, sunHalf, moonHalf,
                    CelestialMath.SYNODIC_DAYS, latitudes, batched);
            for (int latitudeIndex = 0; latitudeIndex <= 720; latitudeIndex++) {
                double latitude = latitudes[latitudeIndex];
                assertRawDouble(legacyMaximumCoverageAtLatitude(event, latitude, sunHalf,
                                moonHalf, CelestialMath.SYNODIC_DAYS), batched[latitudeIndex],
                        "batched latitude eclipse search " + index + "/" + latitudeIndex);
            }
        }
        for (double threshold : new double[]{-1.0D, Double.NaN, Double.POSITIVE_INFINITY,
                1.0000000000000002D}) {
            assertRawDouble(legacyLatitudeHalfWidthAt(SolarEclipseRegion.Event.NONE, 0.0D,
                            sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS, threshold),
                    SolarEclipseRegion.latitudeHalfWidthAt(SolarEclipseRegion.Event.NONE, 0.0D,
                            sunHalf, moonHalf, CelestialMath.SYNODIC_DAYS, threshold),
                    "prepared latitude edge invalid threshold " + threshold);
        }
        assertRawDouble(legacyLatitudeHalfWidthAt(null, Double.NaN, sunHalf, moonHalf,
                        CelestialMath.SYNODIC_DAYS, 0.0D),
                SolarEclipseRegion.latitudeHalfWidthAt(null, Double.NaN, sunHalf, moonHalf,
                        CelestialMath.SYNODIC_DAYS, 0.0D),
                "prepared latitude edge null event");
    }

    /** Independent copy of the pre-prepared latitude-edge implementation. */
    private static double legacyLatitudeHalfWidthAt(SolarEclipseRegion.Event event,
                                                     double calendarDays,
                                                     double sunHalf, double moonHalf,
                                                     double synodicDays,
                                                     double coverageThreshold) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(calendarDays) || !positiveFinite(sunHalf)
                || !positiveFinite(moonHalf) || !positiveFinite(synodicDays)
                || !Double.isFinite(coverageThreshold) || coverageThreshold < 0.0D
                || coverageThreshold > 1.0D) {
            return 0.0D;
        }
        double phaseOffset = CelestialMath.TAU * (calendarDays - event.conjunctionDay())
                / synodicDays;
        if (Math.abs(phaseOffset) >= Math.PI * 0.5D) return 0.0D;
        double contact = sunHalf + moonHalf;
        double centerX = -Math.tan(phaseOffset);
        double northDot = legacySolarNorthDot(calendarDays, event.daysInYear());
        double centerCoverage = legacyProjectedSolarSquareCoverage(sunHalf, moonHalf,
                centerX, 0.0D, northDot);
        if ((coverageThreshold <= 0.0D && !(centerCoverage > 0.0D))
                || (coverageThreshold > 0.0D && centerCoverage < coverageThreshold)) {
            return 0.0D;
        }
        double low = 0.0D;
        double high = contact * 1.5D;
        for (int iteration = 0; iteration < 48; iteration++) {
            double middle = (low + high) * 0.5D;
            double coverage = legacyProjectedSolarSquareCoverage(sunHalf, moonHalf,
                    centerX, middle, northDot);
            boolean inside = coverageThreshold <= 0.0D
                    ? coverage > 0.0D : coverage >= coverageThreshold;
            if (inside) low = middle;
            else high = middle;
        }
        return testClamp(low / contact * SolarEclipseRegion.PARTIAL_HALF_WIDTH,
                0.0D, SolarEclipseRegion.PARTIAL_HALF_WIDTH);
    }

    private static double legacyProjectedSolarSquareCoverage(double firstHalf, double secondHalf,
                                                              double centerX, double centerY,
                                                              double northDot) {
        if (!positiveFinite(firstHalf) || !positiveFinite(secondHalf)
                || !Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(northDot)) {
            return 0.0D;
        }
        CelestialVector sun = new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector moon = new CelestialVector(centerX, centerY, 1.0D).normalized();
        double clampedNorthDot = testClamp(northDot, -1.0D, 1.0D);
        CelestialVector north = new CelestialVector(0.0D,
                Math.sqrt(Math.max(0.0D,
                        1.0D - clampedNorthDot * clampedNorthDot)), clampedNorthDot);
        return legacySquareCoverage(sun, moon, north, firstHalf, secondHalf);
    }

    private static double legacyMaximumCoverageAtTime(SolarEclipseRegion.Event event,
                                                       double calendarDays,
                                                       double sunHalf, double moonHalf,
                                                       double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()) return 0.0D;
        double track = event.trackLatitude(calendarDays);
        double searchRadius = SolarEclipseRegion.PARTIAL_HALF_WIDTH * 1.5D;
        double low = testClamp(track - searchRadius, -Math.PI * 0.5D, Math.PI * 0.5D);
        double high = testClamp(track + searchRadius, -Math.PI * 0.5D, Math.PI * 0.5D);
        if (!(high >= low)) return 0.0D;
        double maximum = Math.max(
                legacySolarCoverageAt(event, calendarDays, low, sunHalf, moonHalf, synodicDays),
                legacySolarCoverageAt(event, calendarDays, high, sunHalf, moonHalf, synodicDays));
        for (int iteration = 0; iteration < 32; iteration++) {
            double firstThird = (2.0D * low + high) / 3.0D;
            double secondThird = (low + 2.0D * high) / 3.0D;
            double firstCoverage = legacySolarCoverageAt(event, calendarDays, firstThird,
                    sunHalf, moonHalf, synodicDays);
            double secondCoverage = legacySolarCoverageAt(event, calendarDays, secondThird,
                    sunHalf, moonHalf, synodicDays);
            maximum = Math.max(maximum, Math.max(firstCoverage, secondCoverage));
            if (firstCoverage < secondCoverage) low = firstThird;
            else high = secondThird;
        }
        double middle = (low + high) * 0.5D;
        return Math.max(maximum, legacySolarCoverageAt(event, calendarDays, middle,
                sunHalf, moonHalf, synodicDays));
    }

    private static double legacyMaximumCoverageAtLatitude(SolarEclipseRegion.Event event,
                                                           double observerLatitude,
                                                           double sunHalf, double moonHalf,
                                                           double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(observerLatitude) || !positiveFinite(sunHalf)
                || !positiveFinite(moonHalf) || !positiveFinite(synodicDays)) return 0.0D;
        double contact = sunHalf + moonHalf;
        double halfDuration = Math.atan(contact) * synodicDays / CelestialMath.TAU;
        double maximum = 0.0D;
        for (int sample = 0; sample <= 64; sample++) {
            double offset = (sample / 64.0D * 2.0D - 1.0D) * halfDuration;
            maximum = Math.max(maximum, legacySolarCoverageAt(event,
                    event.conjunctionDay() + offset, observerLatitude,
                    sunHalf, moonHalf, synodicDays));
        }
        return testClamp(maximum, 0.0D, 1.0D);
    }

    private static double legacySolarCoverageAt(SolarEclipseRegion.Event event,
                                                double calendarDays,
                                                double observerLatitude,
                                                double sunHalf, double moonHalf,
                                                double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(calendarDays) || !Double.isFinite(observerLatitude)
                || !positiveFinite(sunHalf) || !positiveFinite(moonHalf)
                || !positiveFinite(synodicDays)) return 0.0D;
        double phaseOffset = CelestialMath.TAU * (calendarDays - event.conjunctionDay())
                / synodicDays;
        if (Math.abs(phaseOffset) >= Math.PI * 0.5D) return 0.0D;
        double contact = sunHalf + moonHalf;
        double alongTrack = Math.tan(phaseOffset);
        double trackLatitude = event.trackLatitude(calendarDays);
        double crossTrack = (observerLatitude - trackLatitude) * contact
                / SolarEclipseRegion.PARTIAL_HALF_WIDTH;
        return legacyProjectedSolarSquareCoverage(sunHalf, moonHalf,
                -alongTrack, crossTrack, legacySolarNorthDot(calendarDays, event.daysInYear()));
    }

    private static double legacySolarNorthDot(double calendarDays, double daysInYear) {
        if (!Double.isFinite(calendarDays) || !positiveFinite(daysInYear)) return 0.0D;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double longitude = CelestialMath.TAU * positiveModulo(
                284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = CelestialMath.AXIAL_TILT * Math.sin(longitude);
        return Math.sin(declination);
    }

    private static void optimizedOrbitsAndPhasesMatchLegacyBits() {
        Random random = new Random(0x0B17B17L);
        for (int sample = 0; sample < 160; sample++) {
            double radius = 0.001D + random.nextDouble() * 10_000.0D;
            double period = 0.01D + random.nextDouble() * 100_000.0D;
            double inclination = (random.nextDouble() - 0.5D) * Math.PI;
            double node = (random.nextDouble() - 0.5D) * CelestialMath.TAU;
            boolean retrograde = random.nextBoolean();
            double days = (random.nextDouble() - 0.5D) * 1.0E7D;
            double phase = random.nextDouble();
            assertVectorRaw(legacyOrbitalPosition(radius, period, inclination, node,
                            retrograde, days, phase),
                    CelestialMath.orbitalPosition(radius, period, inclination, node,
                            retrograde, days, phase), "optimized primary orbit " + sample);
        }
        for (CelestialBodies body : CelestialBodies.values()) {
            if (body.parent() == null) continue;
            for (double days : new double[]{-12345.75D, 0.0D, 1.25D, 98765.5D}) {
                for (double phase : new double[]{0.0D, 0.125D, 0.75D}) {
                    assertVectorRaw(legacySatelliteOrbitalPosition(body.semiMajorMillionKm(),
                                    body.orbitalDays(), body.orbitalReferenceNormalEcliptic(),
                                    body.inclinationRadians(), body.ascendingNodeRadians(),
                                    body.retrograde(), days, phase),
                            CelestialMath.satelliteOrbitalPosition(body.semiMajorMillionKm(),
                                    body.orbitalDays(), body.orbitalReferenceNormalEcliptic(),
                                    body.inclinationRadians(), body.ascendingNodeRadians(),
                                    body.retrograde(), days, phase),
                            "optimized satellite orbit " + body + "/" + days + "/" + phase);
                }
            }
        }
        CelestialPlanetSettings settings = CelestialPlanetSettings.DEFAULT.withEarth(
                CelestialBodies.EARTH_DIAMETER_KM * 1.01D,
                CelestialBodies.EARTH_ORBITAL_DAYS * 0.99D,
                CelestialBodies.EARTH_SEMI_MAJOR_AXIS * 1.02D);
        CelestialBodyParameters mercury = settings.parameters(CelestialBodies.MERCURY);
        CelestialPlanetSettings tiltedSettings = settings.with(CelestialBodies.MERCURY,
                new CelestialBodyParameters(mercury.diameterKm(), mercury.orbitalDays(),
                        mercury.semiMajorMillionKm(), mercury.synodicDays(),
                        mercury.inclinationRadians() + 0.0375D));
        CelestialPlanetSettings equalSettings = new CelestialPlanetSettings(
                settings.configurableBodies(), settings.earthDiameterKm(),
                settings.earthOrbitalDays(), settings.earthSemiMajorMillionKm());
        CelestialOrbitalPhases phases = CelestialOrbitalPhases.random(new Random(77L), settings);
        CelestialOrbitalPhases equalPhases = new CelestialOrbitalPhases(phases.asMap());
        CelestialOrbitalPhases alternatePhases = CelestialOrbitalPhases.random(
                new Random(0x50484153454CL), tiltedSettings);
        assertRawDouble(phases.turns(CelestialOrbitalPhases.EARTH), phases.earthTurns(),
                "fast Earth phase");
        for (CelestialBodies body : CelestialBodies.values()) {
            assertRawDouble(phases.turns(body.id()), phases.turns(body),
                    "fast body phase " + body);
        }
        for (double years : new double[]{-2.5D, 0.0D, 1.2345D, 42.0D}) {
            CelestialMath.Result frame = calculateAt(12_345.0D, years * 96.0D + 1.25D);
            java.util.List<CelestialBodyState> expected = legacyBodiesCalculate(frame, years,
                    settings, phases);
            java.util.List<CelestialBodyState> actual = CelestialBodies.calculate(frame, years,
                    settings, phases);
            assertBodyStatesRaw(expected, actual,
                    "optimized 17-body assembly at years=" + years);
        }
        for (int sample = 0; sample < 64; sample++) {
            double years = (sample - 31.5D) * 0.731D;
            CelestialMath.Result frame = calculateAt((sample % 11 - 5) * 9_173.25D,
                    years * 96.0D + sample / 64.0D);
            java.util.List<CelestialBodyState> expected = legacyBodiesCalculate(
                    frame, years, settings, phases);
            java.util.List<CelestialBodyState> actual = CelestialBodies.calculate(
                    frame, years, settings, phases);
            assertBodyStatesRaw(expected, actual,
                    "thread-local 17-body scratch sequential call " + sample);
        }
        CelestialPlanetSettings[] alternatingSettings = {
                settings, tiltedSettings, equalSettings, CelestialPlanetSettings.DEFAULT,
                tiltedSettings, settings
        };
        CelestialOrbitalPhases[] alternatingPhases = {
                phases, alternatePhases, equalPhases, CelestialOrbitalPhases.ZERO,
                alternatePhases, phases
        };
        for (int sample = 0; sample < 192; sample++) {
            int selection = sample % alternatingSettings.length;
            CelestialPlanetSettings selected = alternatingSettings[selection];
            CelestialOrbitalPhases selectedPhases = alternatingPhases[selection];
            double years = (sample - 95.5D) * 0.19375D;
            CelestialMath.Result frame = calculateAt((sample % 17 - 8) * 5_137.75D,
                    years * 96.0D + sample / 192.0D);
            assertBodyStatesRaw(legacyBodiesCalculate(frame, years, selected, selectedPhases),
                    CelestialBodies.calculate(frame, years, selected, selectedPhases),
                    "prepared body settings alternating identity " + sample);
        }
        java.util.concurrent.atomic.AtomicReference<Throwable> threadFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread firstThread = bodyScratchThread("celestial-body-scratch-a", 0, settings,
                tiltedSettings, phases, alternatePhases, threadFailure);
        Thread secondThread = bodyScratchThread("celestial-body-scratch-b", 1, settings,
                equalSettings, phases, equalPhases, threadFailure);
        firstThread.start();
        secondThread.start();
        try {
            firstThread.join();
            secondThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("17-body scratch cross-thread test was interrupted", exception);
        }
        if (threadFailure.get() != null) {
            throw new AssertionError("17-body scratch was not thread isolated", threadFailure.get());
        }
    }

    private static Thread bodyScratchThread(String name, int lane,
                                             CelestialPlanetSettings settings,
                                             CelestialPlanetSettings alternateSettings,
                                             CelestialOrbitalPhases phases,
                                             CelestialOrbitalPhases alternatePhases,
                                             java.util.concurrent.atomic.AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                for (int sample = 0; sample < 96; sample++) {
                    double years = (lane == 0 ? 1.0D : -1.0D)
                            * (sample + 0.375D) * 0.417D;
                    double z = (lane == 0 ? -23_456.75D : 34_567.25D) + sample * 113.0D;
                    CelestialMath.Result frame = calculateAt(z,
                            years * 96.0D + (lane + 1) * 0.125D);
                    CelestialPlanetSettings selected = (sample & 1) == 0
                            ? settings : alternateSettings;
                    CelestialOrbitalPhases selectedPhases = (sample & 1) == 0
                            ? phases : alternatePhases;
                    java.util.List<CelestialBodyState> expected = legacyBodiesCalculate(
                            frame, years, selected, selectedPhases);
                    java.util.List<CelestialBodyState> actual = CelestialBodies.calculate(
                            frame, years, selected, selectedPhases);
                    assertBodyStatesRaw(expected, actual, name + " sample " + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, name);
    }

    private static CelestialVector legacyOrbitalPosition(double radius, double orbitalDays,
                                                          double inclination,
                                                          double ascendingNode,
                                                          boolean retrograde,
                                                          double calendarDays,
                                                          double phaseTurns) {
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * CelestialMath.TAU * calendarDays / orbitalDays
                + CelestialMath.TAU * phaseTurns;
        double nodeCos = Math.cos(ascendingNode);
        double nodeSin = Math.sin(ascendingNode);
        CelestialVector node = new CelestialVector(nodeCos, nodeSin, 0.0D);
        CelestialVector transverse = new CelestialVector(-nodeSin * Math.cos(inclination),
                nodeCos * Math.cos(inclination), Math.sin(inclination));
        return node.scale(radius * Math.cos(angle))
                .add(transverse.scale(radius * Math.sin(angle)));
    }

    private static CelestialVector legacySatelliteOrbitalPosition(double radius,
                                                                   double orbitalDays,
                                                                   CelestialVector referenceNormal,
                                                                   double inclination,
                                                                   double ascendingNode,
                                                                   boolean retrograde,
                                                                   double calendarDays,
                                                                   double phaseTurns) {
        CelestialVector normal = referenceNormal.normalized();
        if (normal.lengthSquared() < 1.0E-12D) {
            throw new IllegalArgumentException("Orbit reference-plane normal must be non-zero");
        }
        CelestialVector equatorialNorth = new CelestialVector(0.0D,
                Math.sin(CelestialMath.AXIAL_TILT), Math.cos(CelestialMath.AXIAL_TILT));
        CelestialVector reference = Math.abs(normal.dot(equatorialNorth)) < 0.95D
                ? equatorialNorth : new CelestialVector(1.0D, 0.0D, 0.0D);
        CelestialVector equatorX = testCross(reference, normal).normalized();
        CelestialVector equatorY = testCross(normal, equatorX).normalized();
        CelestialVector node = equatorX.scale(Math.cos(ascendingNode))
                .add(equatorY.scale(Math.sin(ascendingNode))).normalized();
        CelestialVector tiltedNormal = legacyRotateAroundAxis(normal, node, inclination).normalized();
        CelestialVector transverse = testCross(tiltedNormal, node).normalized();
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * CelestialMath.TAU * calendarDays / orbitalDays
                + CelestialMath.TAU * phaseTurns;
        return node.scale(radius * Math.cos(angle))
                .add(transverse.scale(radius * Math.sin(angle)));
    }

    private static CelestialVector legacyRotateAroundAxis(CelestialVector vector,
                                                           CelestialVector axis,
                                                           double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return vector.scale(cosine)
                .add(testCross(axis, vector).scale(sine))
                .add(axis.scale(axis.dot(vector) * (1.0D - cosine)));
    }

    private static java.util.List<CelestialBodyState> legacyBodiesCalculate(
            CelestialMath.Result frame, double calendarYears, CelestialPlanetSettings settings,
            CelestialOrbitalPhases phases) {
        CelestialBodies[] ordered = CelestialBodies.values();
        CelestialVector[] positions = new CelestialVector[ordered.length];
        double earthOrbitalDays = settings.earthOrbitalDays();
        double astronomicalDays = calendarYears * earthOrbitalDays
                + (284.0D / 365.0D + 0.5D) * earthOrbitalDays;
        double referenceTurns = phases.turns(CelestialOrbitalPhases.EARTH);
        CelestialVector earth = legacyRotateEcliptic(legacyOrbitalPosition(
                settings.earthSemiMajorMillionKm(), earthOrbitalDays, 0.0D,
                0.0D, false, astronomicalDays, 0.0D), referenceTurns);
        java.util.List<CelestialBodyState> states = new java.util.ArrayList<>(ordered.length);
        for (CelestialBodies body : ordered) {
            CelestialBodyParameters parameters = settings.parameters(body);
            CelestialVector origin = body.parent() == null ? CelestialVector.ZERO
                    : positions[body.parent().ordinal()];
            CelestialVector unrotated = body.parent() == null
                    ? legacyOrbitalPosition(parameters.semiMajorMillionKm(),
                    parameters.orbitalDays(), parameters.inclinationRadians(),
                    body.ascendingNodeRadians(), body.retrograde(), astronomicalDays,
                    phases.turns(body.id()))
                    : legacySatelliteOrbitalPosition(parameters.semiMajorMillionKm(),
                    parameters.orbitalDays(), body.orbitalReferenceNormalEcliptic(),
                    parameters.inclinationRadians(), body.ascendingNodeRadians(),
                    body.retrograde(), astronomicalDays, phases.turns(body.id()));
            CelestialVector relative = legacyRotateEcliptic(unrotated, referenceTurns);
            CelestialVector heliocentric = origin == null ? relative : origin.add(relative);
            positions[body.ordinal()] = heliocentric;
            CelestialVector geocentric = legacyRotateEcliptic(heliocentric.subtract(earth),
                    -referenceTurns);
            CelestialVector normalized = geocentric.normalized();
            double cosine = Math.cos(CelestialMath.AXIAL_TILT);
            double sine = Math.sin(CelestialMath.AXIAL_TILT);
            CelestialVector equatorial = new CelestialVector(normalized.x(),
                    normalized.y() * cosine - normalized.z() * sine,
                    normalized.y() * sine + normalized.z() * cosine).normalized();
            CelestialVector direction = CelestialMath.equatorialToHorizon(equatorial,
                    frame.latitude(), frame.localSiderealAngle());
            double distance = Math.max(1.0E-6D, geocentric.length());
            double angularRadius = Math.atan2(parameters.diameterKm() * 0.5D,
                    distance * 1_000_000.0D);
            double altitude = Math.asin(Math.max(-1.0D, Math.min(1.0D, direction.y())));
            states.add(new CelestialBodyState(body.id(), body.parent() == null ? null
                    : body.parent().id(), geocentric, direction, distance, angularRadius,
                    altitude, Math.min(1.0D, 1.0D / Math.sqrt(distance)), 1.0D, 0.0D));
        }
        return states;
    }

    private static CelestialVector legacyRotateEcliptic(CelestialVector vector, double turns) {
        double angle = CelestialMath.TAU * turns;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return new CelestialVector(vector.x() * cosine - vector.y() * sine,
                vector.x() * sine + vector.y() * cosine, vector.z());
    }

    private static void fastProviderQueriesMatchFullStateExactly() {
        for (double z : new double[]{-50_000.0D, -10_000.0D, 0.0D, 10_000.0D, 30_000.0D}) {
            for (double day : new double[]{0.0D, 3.25D, 8.065D, 16.13D, 77.75D}) {
                CelestialMath.Result result = calculateAt(z, day);
                DaylightState expectedDaylight = new DaylightState(result.solarElevation(),
                        result.solarElevation() > 0.0D, result.apparentDayTime(),
                        result.daylightFactor());
                DaylightState actualDaylight = OverworldCelestialProvider.daylightFromResult(result);
                if (!expectedDaylight.equals(actualDaylight)) {
                    throw new AssertionError("fast daylight query changed at z/day=" + z + "/" + day);
                }
                CelestialState full = fullStateForEventReference(result);
                CelestialEventState expectedEvents = CelestialEventState.from(full);
                CelestialEventState actualEvents = OverworldCelestialProvider.eventsFromResult(result);
                if (!expectedEvents.equals(actualEvents)) {
                    throw new AssertionError("fast event query changed at z/day=" + z + "/" + day
                            + "\nexpected=" + expectedEvents + "\nactual=" + actualEvents);
                }
            }
        }
    }

    private static void defaultProviderFastQueriesPreserveOptionalSemantics() {
        CelestialState full = fullStateForEventReference(calculateAt(12_345.0D, 19.75D));
        Vec3[] observedCenter = {null};
        CelestialProvider provider = (level, observer, partialTick) -> {
            observedCenter[0] = observer;
            return full;
        };
        Optional<DaylightState> daylight = provider.daylightOptional(null, Vec3.ZERO, 0.0F);
        Optional<CelestialEventState> events = provider.eventsOptional(null, Vec3.ZERO, 0.0F);
        if (!daylight.equals(Optional.of(full.daylight()))
                || !events.equals(Optional.of(CelestialEventState.from(full)))) {
            throw new AssertionError("default provider fast hooks no longer derive the full state");
        }
        BlockPos block = new BlockPos(-3, 7, -11);
        daylight = provider.daylightOptional(null, block, 0.0F);
        if (!daylight.equals(Optional.of(full.daylight()))
                || !new Vec3(-2.5D, 7.5D, -10.5D).equals(observedCenter[0])) {
            throw new AssertionError("default BlockPos daylight hook changed centered Vec3 semantics");
        }
        events = provider.eventsOptional(null, block, 0.0F);
        if (!events.equals(Optional.of(CelestialEventState.from(full)))
                || !new Vec3(-2.5D, 7.5D, -10.5D).equals(observedCenter[0])) {
            throw new AssertionError("default BlockPos event hook changed centered Vec3 semantics");
        }

        CelestialProvider absent = new CelestialProvider() {
            @Override
            public CelestialState state(Level level, Vec3 observer, float partialTick) {
                throw new AssertionError("empty provider unexpectedly requested a mandatory state");
            }

            @Override
            public Optional<CelestialState> stateOptional(Level level, Vec3 observer,
                                                           float partialTick) {
                return Optional.empty();
            }
        };
        if (absent.daylightOptional(null, Vec3.ZERO, 0.0F).isPresent()
                || absent.eventsOptional(null, Vec3.ZERO, 0.0F).isPresent()
                || absent.daylightOptional(null, block, 0.0F).isPresent()
                || absent.eventsOptional(null, block, 0.0F).isPresent()) {
            throw new AssertionError("default provider fast hooks changed empty optional semantics");
        }
    }

    private static CelestialState fullStateForEventReference(CelestialMath.Result result) {
        net.minecraft.resources.ResourceLocation sunId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("wildfires", "sun");
        net.minecraft.resources.ResourceLocation earthId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("wildfires", "earth");
        net.minecraft.resources.ResourceLocation moonId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("wildfires", "moon");
        CelestialBodyState sun = new CelestialBodyState(sunId, null, result.sunGeocentric(),
                result.sunDirection(), 1.0D, CelestialMath.SUN_ANGULAR_RADIUS,
                result.solarElevation(), result.daylightFactor(), 1.0D, result.solarEclipse());
        CelestialBodyState moon = new CelestialBodyState(moonId, earthId, result.moonGeocentric(),
                result.moonDirection(), result.moonDistance(), result.moonAngularRadius(),
                result.moonElevation(), result.illuminatedFraction(),
                result.illuminatedFraction(), result.lunarEclipse());
        DaylightState daylight = new DaylightState(result.solarElevation(),
                result.solarElevation() > 0.0D, result.apparentDayTime(), result.daylightFactor());
        return new CelestialState(result.latitude(), result.fractionOfDay(), result.fractionOfYear(),
                0L, sun, moon, result.celestialNorth(), java.util.List.of(), result.moonPhase(),
                result.solarEclipse(), result.physicalSolarEclipse(), result.solarEclipseRegion(),
                result.lunarEclipse(), result.lunarEclipseRegion(), result.supermoon(),
                result.bloodMoon(), CelestialDiscGeometry.DEFAULT_SUN_SCALE,
                CelestialDiscGeometry.DEFAULT_MOON_SCALE, 1.0D, daylight);
    }

    private static void displayEventMaskMatchesLegacyStates() {
        CelestialEventType[] order = {CelestialEventType.SOLAR_ECLIPSE,
                CelestialEventType.NEW_MOON, CelestialEventType.FULL_MOON,
                CelestialEventType.LUNAR_ECLIPSE, CelestialEventType.BLOOD_MOON,
                CelestialEventType.SUPERMOON};
        for (int index = -64; index <= 256; index++) {
            long ticks = index * 1_337L;
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(
                    index * 173.0D, 20_000.0D, ticks, 8));
            boolean bloodMoon = CelestialEventType.BLOOD_MOON.matches(result, ticks, null);
            boolean lunarEclipse = CelestialEventType.LUNAR_ECLIPSE.matches(result, ticks, null);
            int expected = 0;
            for (int eventIndex = 0; eventIndex < order.length; eventIndex++) {
                CelestialEventType type = order[eventIndex];
                boolean active = switch (type) {
                    case LUNAR_ECLIPSE -> lunarEclipse && !bloodMoon;
                    case BLOOD_MOON -> bloodMoon;
                    default -> type.matches(result, ticks, null);
                };
                if (active) expected |= 1 << eventIndex;
            }
            int actual = EclipsePredictionService.displayEventMask(result, ticks);
            if (expected != actual) {
                throw new AssertionError("event mask changed at tick " + ticks + ": expected "
                        + expected + ", got " + actual);
            }
        }

        LunarEclipseState activeLunar = new LunarEclipseState(1L, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.25D, 0.5D);
        double[] illuminations = {Double.NaN, -0.0D, 0.005D, 0.5D, 0.995D,
                1.0D, Double.POSITIVE_INFINITY};
        double[] solarCoverages = {Double.NaN, -0.0D, Double.MIN_VALUE, 0.5D,
                Double.POSITIVE_INFINITY};
        double[] strengths = {Double.NaN, -0.0D, 0.8D,
                Math.nextUp(0.8D), 0.98D, 1.0D, Double.POSITIVE_INFINITY};
        double[] elevations = {Double.NEGATIVE_INFINITY, Double.NaN, -1.0D, -0.0D,
                0.0D, Double.MIN_VALUE, 1.0D, Double.POSITIVE_INFINITY};
        long ticks = 0x5A17C0DEL;
        int compared = 0;
        for (double illumination : illuminations) {
            for (double solarCoverage : solarCoverages) {
                for (LunarEclipseState lunar : new LunarEclipseState[]{
                        LunarEclipseState.NONE, activeLunar}) {
                    for (double supermoon : strengths) {
                        for (double bloodMoon : strengths) {
                            for (double solarElevation : elevations) {
                                for (double moonElevation : elevations) {
                                    CelestialMath.DisplayEventSample sample =
                                            new CelestialMath.DisplayEventSample(illumination,
                                                    solarCoverage, lunar.penumbraCoverage(),
                                                    supermoon, bloodMoon, solarElevation,
                                                    moonElevation);
                                    int expected = legacyDisplayEventMask(sample);
                                    int actual = EclipsePredictionService.displayEventMask(sample,
                                            ticks);
                                    if (expected != actual) {
                                        throw new AssertionError("scalar display mask changed for "
                                                + sample + ": expected " + expected + ", got "
                                                + actual);
                                    }
                                    for (int eventIndex = 0; eventIndex < 6; eventIndex++) {
                                        boolean expectedState = (actual & 1 << eventIndex) != 0;
                                        boolean actualState = EclipsePredictionService
                                                .displayEventState(eventIndex, sample);
                                        if (expectedState != actualState) {
                                            throw new AssertionError("single display-event state "
                                                    + eventIndex + " changed for " + sample);
                                        }
                                    }
                                    compared++;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (compared < 10_000) {
            throw new AssertionError("display mask boundary proof sampled too few states: "
                    + compared);
        }
    }

    private static int legacyDisplayEventMask(CelestialMath.EventView result,
                                              long calendarTicks) {
        CelestialEventType[] order = {CelestialEventType.SOLAR_ECLIPSE,
                CelestialEventType.NEW_MOON, CelestialEventType.FULL_MOON,
                CelestialEventType.LUNAR_ECLIPSE, CelestialEventType.BLOOD_MOON,
                CelestialEventType.SUPERMOON};
        boolean bloodMoon = CelestialEventType.BLOOD_MOON.matches(result,
                calendarTicks, null);
        boolean lunarEclipse = CelestialEventType.LUNAR_ECLIPSE.matches(result,
                calendarTicks, null);
        int mask = 0;
        for (int index = 0; index < order.length; index++) {
            CelestialEventType type = order[index];
            boolean active = switch (type) {
                case LUNAR_ECLIPSE -> lunarEclipse && !bloodMoon;
                case BLOOD_MOON -> bloodMoon;
                default -> type.matches(result, calendarTicks, null);
            };
            if (active) {
                mask |= 1 << index;
            }
        }
        return mask;
    }

    /** Independent copy of the pre-scalar display-sample mask expression. */
    private static int legacyDisplayEventMask(CelestialMath.DisplayEventSample result) {
        double solarElevation = result.solarElevation();
        double moonElevation = result.moonElevation();
        boolean localLunarNight = Double.isFinite(moonElevation) && moonElevation > 0.0D
                && Double.isFinite(solarElevation) && solarElevation <= 0.0D;
        boolean bloodMoon = result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                && localLunarNight;
        boolean lunarEclipse = result.lunarPenumbraCoverage() > 0.0D && localLunarNight;
        int mask = 0;
        if (Double.isFinite(result.solarEclipse()) && result.solarEclipse() > 0.0D
                && Double.isFinite(solarElevation) && solarElevation > 0.0D) {
            mask |= 1 << 0;
        }
        if (result.illuminatedFraction() <= 0.005D
                && moonElevation > 0.0D && solarElevation > 0.0D) {
            mask |= 1 << 1;
        }
        if (result.illuminatedFraction() >= 0.995D && localLunarNight) {
            mask |= 1 << 2;
        }
        if (lunarEclipse && !bloodMoon) {
            mask |= 1 << 3;
        }
        if (bloodMoon) {
            mask |= 1 << 4;
        }
        if (result.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                && localLunarNight) {
            mask |= 1 << 5;
        }
        return mask;
    }

    private static void currentEventScannerMatchesLegacyExactly() {
        CelestialRuntimeSettings custom = new CelestialRuntimeSettings(17.25D, 14.75D,
                19.0D, Math.toRadians(4.75D), true, 2.5D, 0.8D, 1.15D,
                CelestialRuntimeSettings.LunarPeriodPreset.CUSTOM,
                CelestialPlanetSettings.DEFAULT);
        int comparedActive = 0;
        int comparedInactive = 0;
        for (CelestialRuntimeSettings settings : new CelestialRuntimeSettings[]{
                CelestialRuntimeSettings.DEFAULT, custom}) {
            for (int sample = -32; sample <= 256; sample++) {
                double ticks = sample * CelestialMath.TICKS_IN_DAY / 8.0D + 0.25D;
                double z = (sample % 9 - 4) * 7_531.5D;
                int daysInMonth = settings == custom ? 11 : 8;
                CelestialMath.Result result = eventResult(z, 20_000.0D,
                        (long) Math.floor(ticks), daysInMonth, settings);
                boolean active = EclipsePredictionService.displayEventMask(result,
                        (long) Math.floor(ticks)) != 0;
                if (active && comparedActive >= 8 || !active && comparedInactive >= 8) {
                    continue;
                }
                EclipsePredictionService.CurrentEvents expected = legacyCurrentEvents(
                        ticks, daysInMonth, 20_000.0D, z, settings);
                EclipsePredictionService.CurrentEvents actual = EclipsePredictionService.currentEvents(
                        ticks, daysInMonth, 20_000.0D, z, settings);
                if (!expected.equals(actual)) {
                    throw new AssertionError("unresolved-mask current-event scan changed at sample "
                            + sample + ":\nexpected=" + expected + "\nactual=" + actual);
                }
                if (active) comparedActive++; else comparedInactive++;
                if (comparedActive >= 8 && comparedInactive >= 8) break;
            }
        }
        if (comparedActive < 8 || comparedInactive < 8) {
            throw new AssertionError("current-event scan equivalence did not cover active/inactive states: "
                    + comparedActive + "/" + comparedInactive);
        }

        double[] edgeTicks = {
                Math.nextUp((double) Long.MIN_VALUE), -1.25D, -0.25D, 0.0D, 0.25D,
                Math.nextDown((double) Long.MAX_VALUE)
        };
        for (int index = 0; index < edgeTicks.length; index++) {
            CelestialRuntimeSettings settings = (index & 1) == 0
                    ? CelestialRuntimeSettings.DEFAULT : custom;
            assertCurrentEventsMatchLegacy(edgeTicks[index], settings == custom ? 11 : 8,
                    20_000.0D, (index - 2.5D) * 6_113.75D, settings,
                    "edge current-event scan " + index);
        }

        for (int sample = 0; sample < 32; sample++) {
            CelestialRuntimeSettings settings = (sample & 1) == 0
                    ? CelestialRuntimeSettings.DEFAULT : custom;
            double ticks = (sample - 16.0D) * CelestialMath.TICKS_IN_DAY * 0.375D
                    + (sample % 5) * 0.25D;
            assertCurrentEventsMatchLegacy(ticks, settings == custom ? 11 : 8,
                    20_000.0D, (sample % 11 - 5) * 4_271.25D, settings,
                    "sequential scratch reuse " + sample);
        }

        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = currentEventWorker(0, start, failure, custom);
        Thread second = currentEventWorker(1, start, failure, custom);
        first.start();
        second.start();
        try {
            first.join();
            second.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("current-event cross-thread test was interrupted", exception);
        }
        if (failure.get() != null) {
            throw new AssertionError("thread-local current-event scratch changed results",
                    failure.get());
        }
    }

    private static Thread currentEventWorker(
            int worker, java.util.concurrent.CyclicBarrier start,
            java.util.concurrent.atomic.AtomicReference<Throwable> failure,
            CelestialRuntimeSettings custom) {
        return new Thread(() -> {
            try {
                start.await();
                for (int sample = 0; sample < 24; sample++) {
                    CelestialRuntimeSettings settings = ((sample + worker) & 1) == 0
                            ? CelestialRuntimeSettings.DEFAULT : custom;
                    double ticks = (sample - 12.0D) * CelestialMath.TICKS_IN_DAY * 0.625D
                            + worker * 0.5D;
                    assertCurrentEventsMatchLegacy(ticks, settings == custom ? 11 : 8,
                            20_000.0D, (sample % 13 - 6) * 3_187.5D + worker * 41.0D,
                            settings, "worker " + worker + " sample " + sample);
                }
            } catch (Throwable throwable) {
                failure.compareAndSet(null, throwable);
            }
        }, "wildfires-current-events-" + worker);
    }

    private static void assertCurrentEventsMatchLegacy(
            double calendarTicks, int daysInMonth, double hemisphereScale, double observerZ,
            CelestialRuntimeSettings settings, String label) {
        EclipsePredictionService.CurrentEvents expected = legacyCurrentEvents(calendarTicks,
                daysInMonth, hemisphereScale, observerZ, settings);
        EclipsePredictionService.CurrentEvents actual = EclipsePredictionService.currentEvents(
                calendarTicks, daysInMonth, hemisphereScale, observerZ, settings);
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " changed:\nexpected=" + expected
                    + "\nactual=" + actual);
        }
        try {
            actual.events().add(new EclipsePredictionService.CurrentEvent(
                    CelestialEventType.SOLAR_ECLIPSE, 0L));
            throw new AssertionError(label + " returned a mutable current-event list");
        } catch (UnsupportedOperationException expectedException) {
            // The public record has always exposed an immutable defensive copy.
        }
    }

    private static EclipsePredictionService.CurrentEvents legacyCurrentEvents(
            double calendarTicks, int daysInMonth, double hemisphereScale, double observerZ,
            CelestialRuntimeSettings settings) {
        CelestialEventType[] types = {CelestialEventType.SOLAR_ECLIPSE,
                CelestialEventType.NEW_MOON, CelestialEventType.FULL_MOON,
                CelestialEventType.LUNAR_ECLIPSE, CelestialEventType.BLOOD_MOON,
                CelestialEventType.SUPERMOON};
        long now = (long) Math.floor(calendarTicks);
        int initial = EclipsePredictionService.displayEventMask(eventResult(observerZ,
                hemisphereScale, now, daysInMonth, settings), now);
        long[] firstChanges = new long[types.length];
        java.util.Arrays.fill(firstChanges, Long.MAX_VALUE);
        long horizon = testSaturatingAdd(now, (long) (2.0D * CelestialMath.TICKS_IN_DAY));
        long previous = now;
        for (long sample = testSaturatingAdd(now, 80L);
             sample <= horizon; sample = testSaturatingAdd(sample, 80L)) {
            int states = EclipsePredictionService.displayEventMask(eventResult(observerZ,
                    hemisphereScale, sample, daysInMonth, settings), sample);
            for (int index = 0; index < types.length; index++) {
                boolean initialState = (initial & 1 << index) != 0;
                boolean sampledState = (states & 1 << index) != 0;
                if (firstChanges[index] == Long.MAX_VALUE && sampledState != initialState) {
                    firstChanges[index] = legacyRefineDisplayEventChange(index, initialState,
                            previous, sample, observerZ, hemisphereScale, daysInMonth, settings);
                }
            }
            if (sample == horizon || sample > horizon - 80L) break;
            previous = sample;
        }
        java.util.List<EclipsePredictionService.CurrentEvent> active = new java.util.ArrayList<>();
        long nextChange = horizon;
        for (int index = 0; index < types.length; index++) {
            if (firstChanges[index] != Long.MAX_VALUE) {
                nextChange = Math.min(nextChange, firstChanges[index]);
            }
            if ((initial & 1 << index) != 0) {
                active.add(new EclipsePredictionService.CurrentEvent(types[index],
                        firstChanges[index] != Long.MAX_VALUE ? firstChanges[index] : horizon));
            }
        }
        return new EclipsePredictionService.CurrentEvents(active, nextChange);
    }

    private static long legacyRefineDisplayEventChange(int eventIndex, boolean initial,
                                                        long low, long high, double observerZ,
                                                        double hemisphereScale, int daysInMonth,
                                                        CelestialRuntimeSettings settings) {
        while (high - low > 1L) {
            long middle = low + (high - low) / 2L;
            boolean state = (EclipsePredictionService.displayEventMask(eventResult(observerZ,
                    hemisphereScale, middle, daysInMonth, settings), middle)
                    & 1 << eventIndex) != 0;
            if (state == initial) low = middle; else high = middle;
        }
        return high;
    }

    private static long testSaturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static CelestialVector testCross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static boolean finiteDirection(CelestialVector vector) {
        return vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z()) && vector.lengthSquared() > 1.0E-12D;
    }

    private static double testClamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void assertSolarPredictionListsRaw(
            List<EclipsePredictionService.SolarPrediction> expected,
            List<EclipsePredictionService.SolarPrediction> actual, String name) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(name + " size " + expected.size() + " != " + actual.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            EclipsePredictionService.SolarPrediction first = expected.get(index);
            EclipsePredictionService.SolarPrediction second = actual.get(index);
            if (first.present() != second.present()
                    || first.conjunctionIndex() != second.conjunctionIndex()) {
                throw new AssertionError(name + " identity " + index);
            }
            assertRawDouble(first.greatestCalendarTicks(), second.greatestCalendarTicks(),
                    name + " greatest " + index);
            assertRawDouble(first.startCalendarTicks(), second.startCalendarTicks(),
                    name + " start " + index);
            assertRawDouble(first.endCalendarTicks(), second.endCalendarTicks(),
                    name + " end " + index);
            assertRawDouble(first.daysInYear(), second.daysInYear(), name + " year " + index);
            assertRawDouble(first.lunarLatitudeRadians(), second.lunarLatitudeRadians(),
                    name + " lunar latitude " + index);
            assertRawDouble(first.greatestTrackLatitudeRadians(),
                    second.greatestTrackLatitudeRadians(), name + " track latitude " + index);
            assertRawDouble(first.startTrackLatitudeRadians(), second.startTrackLatitudeRadians(),
                    name + " start track " + index);
            assertRawDouble(first.endTrackLatitudeRadians(), second.endTrackLatitudeRadians(),
                    name + " end track " + index);
            assertRawDouble(first.globalMaximumCoverage(), second.globalMaximumCoverage(),
                    name + " global coverage " + index);
            assertRawDouble(first.observerMaximumCoverage(), second.observerMaximumCoverage(),
                    name + " observer coverage " + index);
            assertLatitudeBandRaw(first.partialBand(), second.partialBand(),
                    name + " partial " + index);
            assertLatitudeBandRaw(first.penumbraBand(), second.penumbraBand(),
                    name + " penumbra " + index);
            assertLatitudeBandRaw(first.umbraBand(), second.umbraBand(),
                    name + " umbra " + index);
            assertRawDouble(first.synodicDays(), second.synodicDays(),
                    name + " synodic " + index);
            assertRawDouble(first.sunHalfTangent(), second.sunHalfTangent(),
                    name + " Sun half " + index);
            assertRawDouble(first.moonHalfTangent(), second.moonHalfTangent(),
                    name + " Moon half " + index);
        }
    }

    private static void assertLunarPredictionListsRaw(
            List<EclipsePredictionService.LunarPrediction> expected,
            List<EclipsePredictionService.LunarPrediction> actual, String name) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(name + " size " + expected.size() + " != " + actual.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            EclipsePredictionService.LunarPrediction first = expected.get(index);
            EclipsePredictionService.LunarPrediction second = actual.get(index);
            if (first.present() != second.present() || first.fullMoonIndex() != second.fullMoonIndex()
                    || first.kind() != second.kind() || first.eclipse() != second.eclipse()) {
                throw new AssertionError(name + " identity " + index);
            }
            assertRawDouble(first.greatestCalendarTicks(), second.greatestCalendarTicks(),
                    name + " greatest " + index);
            assertRawDouble(first.startCalendarTicks(), second.startCalendarTicks(),
                    name + " start " + index);
            assertRawDouble(first.endCalendarTicks(), second.endCalendarTicks(),
                    name + " end " + index);
            assertRawDouble(first.maximumCoverage(), second.maximumCoverage(),
                    name + " umbra " + index);
            assertRawDouble(first.maximumPenumbraCoverage(), second.maximumPenumbraCoverage(),
                    name + " penumbra " + index);
            assertRawDouble(first.supermoonIntensity(), second.supermoonIntensity(),
                    name + " supermoon " + index);
            assertRawDouble(first.shadowCenterX(), second.shadowCenterX(),
                    name + " shadow x " + index);
            assertRawDouble(first.shadowCenterY(), second.shadowCenterY(),
                    name + " shadow y " + index);
            assertRawDouble(first.shadowRadius(), second.shadowRadius(),
                    name + " shadow radius " + index);
        }
    }

    private static void assertLatitudeBandRaw(EclipsePredictionService.LatitudeBand expected,
                                              EclipsePredictionService.LatitudeBand actual,
                                              String name) {
        if (expected.present() != actual.present()) {
            throw new AssertionError(name + " presence");
        }
        assertRawDouble(expected.southRadians(), actual.southRadians(), name + " south");
        assertRawDouble(expected.northRadians(), actual.northRadians(), name + " north");
    }

    private static void assertRawDouble(double expected, double actual, String name) {
        long expectedBits = Double.doubleToRawLongBits(expected);
        long actualBits = Double.doubleToRawLongBits(actual);
        if (expectedBits != actualBits) {
            throw new AssertionError(name + ": expected " + expected + " (0x"
                    + Long.toHexString(expectedBits) + "), got " + actual + " (0x"
                    + Long.toHexString(actualBits) + ")");
        }
    }

    private static void assertVectorRaw(CelestialVector expected, CelestialVector actual,
                                        String name) {
        assertRawDouble(expected.x(), actual.x(), name + " x");
        assertRawDouble(expected.y(), actual.y(), name + " y");
        assertRawDouble(expected.z(), actual.z(), name + " z");
    }

    private static void assertBodyStatesRaw(java.util.List<CelestialBodyState> expected,
                                            java.util.List<CelestialBodyState> actual,
                                            String name) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(name + " body count changed: " + expected.size()
                    + " != " + actual.size());
        }
        for (int index = 0; index < expected.size(); index++) {
            CelestialBodyState oldState = expected.get(index);
            CelestialBodyState newState = actual.get(index);
            String body = name + "[" + index + "]";
            if (!java.util.Objects.equals(oldState.id(), newState.id())
                    || !java.util.Objects.equals(oldState.parentId(), newState.parentId())) {
                throw new AssertionError(body + " identity or order changed");
            }
            assertVectorRaw(oldState.geocentricPosition(), newState.geocentricPosition(),
                    body + " geocentric");
            assertVectorRaw(oldState.observerDirection(), newState.observerDirection(),
                    body + " direction");
            assertRawDouble(oldState.distance(), newState.distance(), body + " distance");
            assertRawDouble(oldState.angularRadiusRadians(), newState.angularRadiusRadians(),
                    body + " angular radius");
            assertRawDouble(oldState.altitudeRadians(), newState.altitudeRadians(),
                    body + " altitude");
            assertRawDouble(oldState.brightness(), newState.brightness(), body + " brightness");
            assertRawDouble(oldState.illuminatedFraction(), newState.illuminatedFraction(),
                    body + " illumination");
            assertRawDouble(oldState.occultation(), newState.occultation(), body + " occultation");
        }
    }

    private static void assertRuntimeSettingsRaw(CelestialRuntimeSettings expected,
                                                 CelestialRuntimeSettings actual,
                                                 String name) {
        assertRawDouble(expected.synodicDays(), actual.synodicDays(), name + " synodic days");
        assertRawDouble(expected.anomalisticDays(), actual.anomalisticDays(),
                name + " anomalistic days");
        assertRawDouble(expected.nodalYears(), actual.nodalYears(), name + " nodal years");
        assertRawDouble(expected.lunarInclinationRadians(), actual.lunarInclinationRadians(),
                name + " lunar inclination");
        if (expected.bloodMoonSurfaceMonsters() != actual.bloodMoonSurfaceMonsters()) {
            throw new AssertionError(name + " blood-Moon monster flag changed");
        }
        assertRawDouble(expected.bloodMoonSpawnMultiplier(), actual.bloodMoonSpawnMultiplier(),
                name + " blood-Moon multiplier");
        assertRawDouble(expected.sunScale(), actual.sunScale(), name + " Sun scale");
        assertRawDouble(expected.moonScale(), actual.moonScale(), name + " Moon scale");
        if (expected.lunarPeriodPreset() != actual.lunarPeriodPreset()
                || !expected.planetSettings().equals(actual.planetSettings())
                || expected.orbitalPhases() != actual.orbitalPhases()) {
            throw new AssertionError(name + " object fields changed");
        }
    }

    private static void assertEventSampleRaw(CelestialMath.Result expected,
                                             CelestialMath.EventSample actual,
                                             String name) {
        assertRawDouble(expected.latitude(), actual.latitude(), name + " latitude");
        assertRawDouble(expected.fractionOfDay(), actual.fractionOfDay(), name + " day");
        assertRawDouble(expected.illuminatedFraction(), actual.illuminatedFraction(),
                name + " illumination");
        if (expected.moonPhase() != actual.moonPhase()) {
            throw new AssertionError(name + " Moon phase changed");
        }
        assertRawDouble(expected.solarEclipse(), actual.solarEclipse(),
                name + " solar eclipse");
        assertLunarEclipseStateRaw(expected.lunarEclipseRegion(),
                actual.lunarEclipseRegion(), name + " lunar eclipse");
        assertRawDouble(expected.supermoon(), actual.supermoon(), name + " supermoon");
        assertRawDouble(expected.bloodMoon(), actual.bloodMoon(), name + " blood Moon");
        assertRawDouble(expected.solarElevation(), actual.solarElevation(),
                name + " solar elevation");
        assertRawDouble(expected.moonElevation(), actual.moonElevation(),
                name + " Moon elevation");
        assertRawDouble(expected.apparentDayTime(), actual.apparentDayTime(),
                name + " apparent time");
    }

    private static void assertDisplayEventSampleRaw(CelestialMath.Result expected,
                                                    CelestialMath.DisplayEventSample actual,
                                                    String name) {
        assertRawDouble(expected.illuminatedFraction(), actual.illuminatedFraction(),
                name + " illumination");
        assertRawDouble(expected.solarEclipse(), actual.solarEclipse(),
                name + " solar eclipse");
        assertRawDouble(expected.lunarEclipseRegion().penumbraCoverage(),
                actual.lunarPenumbraCoverage(), name + " lunar penumbra");
        assertRawDouble(expected.supermoon(), actual.supermoon(), name + " supermoon");
        assertRawDouble(expected.bloodMoon(), actual.bloodMoon(), name + " blood Moon");
        assertRawDouble(expected.solarElevation(), actual.solarElevation(),
                name + " solar elevation");
        assertRawDouble(expected.moonElevation(), actual.moonElevation(),
                name + " Moon elevation");
    }

    private static void assertResultRaw(CelestialMath.Result expected,
                                        CelestialMath.Result actual, String name) {
        assertRawDouble(expected.latitude(), actual.latitude(), name + " latitude");
        assertRawDouble(expected.fractionOfDay(), actual.fractionOfDay(), name + " day");
        assertRawDouble(expected.fractionOfYear(), actual.fractionOfYear(), name + " year");
        assertVectorRaw(expected.sunGeocentric(), actual.sunGeocentric(), name + " Sun geocentric");
        assertVectorRaw(expected.moonGeocentric(), actual.moonGeocentric(), name + " Moon geocentric");
        assertVectorRaw(expected.sunDirection(), actual.sunDirection(), name + " Sun direction");
        assertVectorRaw(expected.moonDirection(), actual.moonDirection(), name + " Moon direction");
        assertVectorRaw(expected.celestialNorth(), actual.celestialNorth(), name + " north");
        assertRawDouble(expected.moonDistance(), actual.moonDistance(), name + " Moon distance");
        assertRawDouble(expected.moonAngularRadius(), actual.moonAngularRadius(), name + " Moon radius");
        assertRawDouble(expected.sunMoonSeparation(), actual.sunMoonSeparation(), name + " separation");
        assertRawDouble(expected.illuminatedFraction(), actual.illuminatedFraction(), name + " illumination");
        if (expected.moonPhase() != actual.moonPhase()) {
            throw new AssertionError(name + " Moon phase changed");
        }
        assertRawDouble(expected.solarEclipse(), actual.solarEclipse(), name + " solar eclipse");
        assertRawDouble(expected.physicalSolarEclipse(), actual.physicalSolarEclipse(),
                name + " physical eclipse");
        assertRawDouble(expected.lunarEclipse(), actual.lunarEclipse(), name + " lunar eclipse");
        assertLunarEclipseStateRaw(expected.lunarEclipseRegion(), actual.lunarEclipseRegion(), name);
        assertRawDouble(expected.supermoon(), actual.supermoon(), name + " supermoon");
        assertRawDouble(expected.bloodMoon(), actual.bloodMoon(), name + " blood Moon");
        assertSolarEclipseStateRaw(expected.solarEclipseRegion(), actual.solarEclipseRegion(), name);
        assertRawDouble(expected.solarElevation(), actual.solarElevation(), name + " solar elevation");
        assertRawDouble(expected.moonElevation(), actual.moonElevation(), name + " Moon elevation");
        assertRawDouble(expected.apparentDayTime(), actual.apparentDayTime(), name + " apparent time");
        assertRawDouble(expected.daylightFactor(), actual.daylightFactor(), name + " daylight");
        assertRawDouble(expected.solarLongitude(), actual.solarLongitude(), name + " longitude");
        assertRawDouble(expected.localSiderealAngle(), actual.localSiderealAngle(), name + " sidereal");
    }

    private static void assertLunarEclipseStateRaw(
            first.wildfires.api.celestial.LunarEclipseState expected,
            first.wildfires.api.celestial.LunarEclipseState actual, String name) {
        if (expected.fullMoonIndex() != actual.fullMoonIndex()) {
            throw new AssertionError(name + " lunar-eclipse index changed");
        }
        assertRawDouble(expected.fullMoonCalendarTicks(), actual.fullMoonCalendarTicks(),
                name + " lunar-eclipse ticks");
        assertRawDouble(expected.lunarLatitudeRadians(), actual.lunarLatitudeRadians(),
                name + " lunar-eclipse latitude");
        assertRawDouble(expected.effectiveLatitudeRadians(), actual.effectiveLatitudeRadians(),
                name + " lunar-eclipse effective latitude");
        assertRawDouble(expected.shadowCenterX(), actual.shadowCenterX(), name + " shadow x");
        assertRawDouble(expected.shadowCenterY(), actual.shadowCenterY(), name + " shadow y");
        assertRawDouble(expected.shadowRadius(), actual.shadowRadius(), name + " shadow radius");
        assertRawDouble(expected.umbraCoverage(), actual.umbraCoverage(), name + " umbra");
        assertRawDouble(expected.penumbraCoverage(), actual.penumbraCoverage(), name + " penumbra");
    }

    private static void assertSolarEclipseStateRaw(
            first.wildfires.api.celestial.SolarEclipseState expected,
            first.wildfires.api.celestial.SolarEclipseState actual, String name) {
        if (expected.activeSomewhere() != actual.activeSomewhere()
                || expected.conjunctionIndex() != actual.conjunctionIndex()
                || expected.zone() != actual.zone()) {
            throw new AssertionError(name + " solar-eclipse discrete state changed");
        }
        assertRawDouble(expected.conjunctionCalendarTicks(), actual.conjunctionCalendarTicks(),
                name + " conjunction ticks");
        assertRawDouble(expected.trackLatitudeRadians(), actual.trackLatitudeRadians(),
                name + " track latitude");
        assertRawDouble(expected.greatestTrackLatitudeRadians(),
                actual.greatestTrackLatitudeRadians(), name + " greatest latitude");
        assertRawDouble(expected.globalCoverage(), actual.globalCoverage(), name + " global coverage");
        assertRawDouble(expected.localMaximumCoverage(), actual.localMaximumCoverage(),
                name + " local maximum");
    }

    private static void assertSolarEventRaw(SolarEclipseRegion.Event expected,
                                            SolarEclipseRegion.Event actual, String name) {
        if (expected.conjunctionIndex() != actual.conjunctionIndex()
                || expected.intersectsWorld() != actual.intersectsWorld()
                || expected.valid() != actual.valid()) {
            throw new AssertionError(name + " discrete state changed");
        }
        assertRawDouble(expected.conjunctionDay(), actual.conjunctionDay(), name + " day");
        assertRawDouble(expected.daysInYear(), actual.daysInYear(), name + " year");
        assertRawDouble(expected.lunarLatitude(), actual.lunarLatitude(), name + " latitude");
        assertRawDouble(expected.greatestLatitude(), actual.greatestLatitude(),
                name + " greatest latitude");
    }

    private static void assertLunarEventRaw(LunarEclipseRegion.Event expected,
                                            LunarEclipseRegion.Event actual, String name) {
        if (expected.fullMoonIndex() != actual.fullMoonIndex()
                || expected.valid() != actual.valid()) {
            throw new AssertionError(name + " discrete state changed");
        }
        assertRawDouble(expected.fullMoonDay(), actual.fullMoonDay(), name + " day");
        assertRawDouble(expected.lunarLatitudeRadians(), actual.lunarLatitudeRadians(),
                name + " latitude");
        assertRawDouble(expected.effectiveLatitudeRadians(), actual.effectiveLatitudeRadians(),
                name + " effective latitude");
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
