package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialProvider;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.List;
import java.util.Optional;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * TFC-calendar/TFE-grid provider for the overworld. It never changes climate or global time.
 * Apparent sky directions retain the equatorial/horizon contract, while body positions exposed
 * to the shared space ephemeris use ecliptic coordinates.
 */
public final class OverworldCelestialProvider implements CelestialProvider {

    public static final OverworldCelestialProvider INSTANCE = new OverworldCelestialProvider();
    private static final ResourceLocation SUN_ID = ResourceLocation.fromNamespaceAndPath(
            "wildfires", "sun");
    private static final ResourceLocation EARTH_ID = ResourceLocation.fromNamespaceAndPath(
            "wildfires", "earth");
    private static final ResourceLocation MOON_ID = ResourceLocation.fromNamespaceAndPath(
            "wildfires", "moon");
    private static final ThreadLocal<CelestialMath.HorizonProducts> HORIZON_PRODUCTS =
            ThreadLocal.withInitial(CelestialMath.HorizonProducts::new);

    private OverworldCelestialProvider() {
    }

    @Override
    public CelestialState state(Level level, Vec3 observer, float partialTick) {
        CelestialRuntimeSettings settings = settings(level);
        Frame frame = frame(level, observer, partialTick, settings);
        double ticks = frame.calendarTicks();
        int daysInMonth = frame.daysInMonth();
        CelestialMath.Result result = frame.result();
        double weatherVisibility = Math.max(0.0D, Math.min(1.0D,
                1.0D - level.getRainLevel(partialTick)));
        double calendarYears = CelestialMath.calendarYears(ticks, daysInMonth);

        CelestialVector sunPosition = solarEclipticPosition(result,
                settings.planetSettings().earthSemiMajorMillionKm());
        CelestialBodyState sun = new CelestialBodyState(SUN_ID, null,
                sunPosition, result.sunDirection(),
                settings.planetSettings().earthSemiMajorMillionKm(), CelestialMath.SUN_ANGULAR_RADIUS,
                result.solarElevation(), result.daylightFactor(), 1.0D, result.solarEclipse());
        double moonDistance = CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM * result.moonDistance();
        CelestialVector moonPosition = equatorialToEclipticScaled(result.moonGeocentric(), moonDistance);
        CelestialBodyState moon = new CelestialBodyState(MOON_ID, EARTH_ID,
                moonPosition, result.moonDirection(), moonDistance,
                result.moonAngularRadius(), result.moonElevation(), result.illuminatedFraction(),
                result.illuminatedFraction(), result.lunarEclipse());
        List<CelestialBodyState> planets = CelestialBodies.calculate(result, calendarYears,
                settings.planetSettings(), settings.orbitalPhases(),
                frame.sineLatitude(), frame.cosineLatitude(),
                frame.sineSidereal(), frame.cosineSidereal());
        DaylightState daylight = daylightFromResult(result);
        return new CelestialState(result.latitude(), result.fractionOfDay(), result.fractionOfYear(),
                (long) ticks, sun, moon, result.celestialNorth(), planets, result.moonPhase(),
                result.solarEclipse(), result.physicalSolarEclipse(), result.solarEclipseRegion(),
                result.lunarEclipse(), result.lunarEclipseRegion(),
                result.supermoon(), result.bloodMoon(), settings.sunScale(), settings.moonScale(),
                weatherVisibility, daylight);
    }

    @Override
    public Optional<DaylightState> daylightOptional(Level level, Vec3 observer,
                                                    float partialTick) {
        settings(level);
        CelestialMath.DaylightSample sample = daylightContext(level, partialTick)
                .daylightSampleAt(observer.z);
        return Optional.of(daylightFromSample(sample));
    }

    @Override
    public Optional<DaylightState> daylightOptional(Level level, BlockPos observer,
                                                    float partialTick) {
        settings(level);
        CelestialMath.DaylightSample sample = daylightContext(level, partialTick)
                .daylightSampleAt(observer.getZ() + 0.5D);
        return Optional.of(daylightFromSample(sample));
    }

    @Override
    public Optional<CelestialEventState> eventsOptional(Level level, Vec3 observer,
                                                        float partialTick) {
        CelestialMath.EventSample sample = context(level, partialTick, settings(level))
                .eventSampleAt(observer.z);
        return Optional.of(eventsFromSample(sample));
    }

    @Override
    public Optional<CelestialEventState> eventsOptional(Level level, BlockPos observer,
                                                        float partialTick) {
        CelestialMath.EventSample sample = context(level, partialTick, settings(level))
                .eventSampleAt(observer.getZ() + 0.5D);
        return Optional.of(eventsFromSample(sample));
    }

    private static CelestialRuntimeSettings settings(Level level) {
        return level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialEphemerisSavedData.get(level.getServer())
                .settings(CelestialConfig.serverSettings());
    }

    static DaylightState daylightFromResult(CelestialMath.Result result) {
        return new DaylightState(result.solarElevation(), result.solarElevation() > 0.0D,
                result.apparentDayTime(), result.daylightFactor());
    }

    static DaylightState daylightFromSample(CelestialMath.DaylightSample sample) {
        return new DaylightState(sample.solarElevation(), sample.solarElevation() > 0.0D,
                sample.apparentDayTime(), sample.daylightFactor());
    }

    static CelestialEventState eventsFromResult(CelestialMath.Result result) {
        double illumination = result.illuminatedFraction();
        boolean full = illumination >= 0.995D;
        boolean fresh = illumination <= 0.005D;
        boolean first = result.moonPhase() == 2 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean last = result.moonPhase() == 6 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean localDay = result.solarElevation() > 0.0D;
        boolean localNight = result.solarElevation() <= 0.0D;
        boolean moonAboveHorizon = result.moonElevation() > 0.0D;
        boolean moonVisibleAtNight = moonAboveHorizon && localNight;
        return new CelestialEventState(localDay, localNight, localDay, moonAboveHorizon,
                full && moonVisibleAtNight,
                fresh && moonAboveHorizon && localDay,
                first && moonVisibleAtNight, last && moonVisibleAtNight,
                result.solarEclipse(), result.solarEclipse() > 0.0D && localDay,
                result.lunarEclipse(), result.lunarEclipseRegion().penumbraCoverage(),
                result.lunarEclipseRegion().active() && moonVisibleAtNight,
                result.supermoon(), result.supermoon()
                >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD && moonVisibleAtNight,
                result.bloodMoon(), result.bloodMoon()
                > CelestialState.BLOOD_MOON_COVERAGE_THRESHOLD && moonVisibleAtNight);
    }

    static CelestialEventState eventsFromSample(CelestialMath.EventSample result) {
        double illumination = result.illuminatedFraction();
        boolean full = illumination >= 0.995D;
        boolean fresh = illumination <= 0.005D;
        boolean first = result.moonPhase() == 2 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean last = result.moonPhase() == 6 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean localDay = result.solarElevation() > 0.0D;
        boolean localNight = result.solarElevation() <= 0.0D;
        boolean moonAboveHorizon = result.moonElevation() > 0.0D;
        boolean moonVisibleAtNight = moonAboveHorizon && localNight;
        return new CelestialEventState(localDay, localNight, localDay, moonAboveHorizon,
                full && moonVisibleAtNight,
                fresh && moonAboveHorizon && localDay,
                first && moonVisibleAtNight, last && moonVisibleAtNight,
                result.solarEclipse(), result.solarEclipse() > 0.0D && localDay,
                result.lunarEclipseRegion().umbraCoverage(),
                result.lunarEclipseRegion().penumbraCoverage(),
                result.lunarEclipseRegion().active() && moonVisibleAtNight,
                result.supermoon(), result.supermoon()
                >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD && moonVisibleAtNight,
                result.bloodMoon(), result.bloodMoon()
                > CelestialState.BLOOD_MOON_COVERAGE_THRESHOLD && moonVisibleAtNight);
    }

    static CelestialVector equatorialToEclipticScaled(CelestialVector vector, double scale) {
        double cosine = CelestialMath.AXIAL_TILT_COS;
        double sine = CelestialMath.AXIAL_TILT_SIN;
        double eclipticY = vector.y() * cosine + vector.z() * sine;
        double eclipticZ = -vector.y() * sine + vector.z() * cosine;
        return new CelestialVector(vector.x() * scale, eclipticY * scale,
                eclipticZ * scale);
    }

    static CelestialVector solarEclipticPosition(CelestialMath.Result result, double scale) {
        // sunGeocentric retains the TFC-reference apparent-declination approximation used by the
        // surface sky.  The shared space ephemeris instead needs the exact ecliptic longitude that
        // also drives Earth's orbit, otherwise converting the apparent vector back would leave a
        // small artificial latitude even after removing the 23.44 degree axial tilt.
        double longitude = result.solarLongitude();
        return new CelestialVector(Math.cos(longitude) * scale,
                Math.sin(longitude) * scale, 0.0D);
    }

    static Frame frame(Level level, Vec3 observer, float partialTick, CelestialRuntimeSettings settings) {
        return frameAtZ(level, observer.z, partialTick, settings);
    }

    private static Frame frameAtZ(Level level, double observerZ, float partialTick,
                                  CelestialRuntimeSettings settings) {
        return context(level, partialTick, settings).frameAt(observerZ);
    }

    /** Captures the inputs shared by every position query during the same authoritative tick. */
    static FrameContext context(Level level, float partialTick, CelestialRuntimeSettings settings) {
        ICalendar calendar = Calendars.get(level);
        double interpolatedTicks = level.isClientSide()
                ? TfcCalendarRateController.clientPartialCalendarTicks(partialTick)
                : partialTick;
        double ticks = calendar.getCalendarTicks() + interpolatedTicks;
        int daysInMonth = calendar.getCalendarDaysInMonth();
        return new FrameContext(ticks, daysInMonth, TfeHemisphereScale.get(level), settings);
    }

    /** Captures only the authoritative inputs observable through a daylight-only query. */
    static DaylightContext daylightContext(Level level, float partialTick) {
        ICalendar calendar = Calendars.get(level);
        double interpolatedTicks = level.isClientSide()
                ? TfcCalendarRateController.clientPartialCalendarTicks(partialTick)
                : partialTick;
        double ticks = calendar.getCalendarTicks() + interpolatedTicks;
        int daysInMonth = calendar.getCalendarDaysInMonth();
        return new DaylightContext(ticks, daysInMonth, TfeHemisphereScale.get(level));
    }

    record DaylightContext(double calendarTicks, int daysInMonth, double hemisphereScale) {

        CelestialMath.DaylightSample daylightSampleAt(double observerZ) {
            return CelestialMath.daylightSampleAt(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth);
        }
    }

    record FrameContext(double calendarTicks, int daysInMonth, double hemisphereScale,
                         CelestialRuntimeSettings settings, double synodicDays,
                         double anomalisticDays, double sineLunarInclination) {

        FrameContext(double calendarTicks, int daysInMonth, double hemisphereScale,
                     CelestialRuntimeSettings settings) {
            this(calendarTicks, daysInMonth, hemisphereScale, settings,
                    settings.preparedPeriods(daysInMonth));
        }

        private FrameContext(double calendarTicks, int daysInMonth, double hemisphereScale,
                             CelestialRuntimeSettings settings,
                             CelestialRuntimeSettings.PreparedPeriods prepared) {
            this(calendarTicks, daysInMonth, hemisphereScale, settings,
                    prepared.synodicDays(), prepared.anomalisticDays(),
                    prepared.sineLunarInclination());
        }

        Frame frameAt(double observerZ) {
            CelestialMath.HorizonProducts horizonProducts = HORIZON_PRODUCTS.get();
            CelestialMath.Result result = CelestialMath.calculate(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination, horizonProducts);
            return new Frame(result, calendarTicks, daysInMonth,
                    horizonProducts.sineLatitude(), horizonProducts.cosineLatitude(),
                    horizonProducts.sineSidereal(), horizonProducts.cosineSidereal());
        }

        CelestialMath.EventSample eventSampleAt(double observerZ) {
            return CelestialMath.eventSampleAt(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination);
        }

        CelestialMath.DaylightSample daylightSampleAt(double observerZ) {
            return CelestialMath.daylightSampleAt(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth);
        }

        CelestialMath.DisplayEventSample displayEventSampleAt(double observerZ) {
            return CelestialMath.displayEventSampleAt(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination);
        }

        double visibleBloodMoonAt(double observerZ) {
            return CelestialMath.visibleBloodMoonAt(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination);
        }
    }

    record Frame(CelestialMath.Result result, double calendarTicks, int daysInMonth,
                 double sineLatitude, double cosineLatitude,
                 double sineSidereal, double cosineSidereal) {
    }
}
