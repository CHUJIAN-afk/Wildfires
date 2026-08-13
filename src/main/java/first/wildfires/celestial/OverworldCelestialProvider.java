package first.wildfires.celestial;

import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialProvider;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.DaylightState;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.List;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** TFC-calendar/TFE-grid provider for the overworld. It never changes climate or global time. */
public final class OverworldCelestialProvider implements CelestialProvider {

    public static final OverworldCelestialProvider INSTANCE = new OverworldCelestialProvider();

    private OverworldCelestialProvider() {
    }

    @Override
    public CelestialState state(Level level, Vec3 observer, float partialTick) {
        CelestialRuntimeSettings settings = level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialConfig.serverSettings().withOrbitalPhases(
                        CelestialEphemerisSavedData.get(level.getServer()).phases());
        Frame frame = frame(level, observer, partialTick, settings);
        double ticks = frame.calendarTicks();
        int daysInMonth = frame.daysInMonth();
        CelestialMath.Result result = frame.result();
        double weatherVisibility = Math.max(0.0D, Math.min(1.0D,
                1.0D - level.getRainLevel(partialTick)));
        double calendarYears = CelestialMath.calendarYears(ticks, daysInMonth);

        CelestialVector sunPosition = result.sunGeocentric()
                .scale(settings.planetSettings().earthSemiMajorMillionKm());
        CelestialBodyState sun = new CelestialBodyState(Wildfires.rl("sun"), null,
                sunPosition, result.sunDirection(),
                settings.planetSettings().earthSemiMajorMillionKm(), CelestialMath.SUN_ANGULAR_RADIUS,
                result.solarElevation(), result.daylightFactor(), 1.0D, result.solarEclipse());
        double moonDistance = CelestialMath.MOON_MEAN_DISTANCE_MILLION_KM * result.moonDistance();
        CelestialVector moonPosition = equatorialToEcliptic(result.moonGeocentric()).scale(moonDistance);
        CelestialBodyState moon = new CelestialBodyState(Wildfires.rl("moon"), Wildfires.rl("earth"),
                moonPosition, result.moonDirection(), moonDistance,
                result.moonAngularRadius(), result.moonElevation(), result.illuminatedFraction(),
                result.illuminatedFraction(), result.lunarEclipse());
        List<CelestialBodyState> planets = CelestialBodies.calculate(result, calendarYears,
                settings.planetSettings(), settings.orbitalPhases());
        DaylightState daylight = new DaylightState(result.solarElevation(), result.solarElevation() > 0.0D,
                result.apparentDayTime(), result.daylightFactor());
        return new CelestialState(result.latitude(), result.fractionOfDay(), result.fractionOfYear(),
                (long) ticks, sun, moon, result.celestialNorth(), planets, result.moonPhase(),
                result.solarEclipse(), result.physicalSolarEclipse(), result.solarEclipseRegion(),
                result.lunarEclipse(), result.lunarEclipseRegion(),
                result.supermoon(), result.bloodMoon(), settings.sunScale(), settings.moonScale(),
                weatherVisibility, daylight);
    }

    private static CelestialVector equatorialToEcliptic(CelestialVector vector) {
        double cosine = Math.cos(CelestialMath.AXIAL_TILT);
        double sine = Math.sin(CelestialMath.AXIAL_TILT);
        return new CelestialVector(vector.x(), vector.y() * cosine + vector.z() * sine,
                -vector.y() * sine + vector.z() * cosine);
    }

    static Frame frame(Level level, Vec3 observer, float partialTick, CelestialRuntimeSettings settings) {
        return context(level, partialTick, settings).frameAt(observer.z);
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

    record FrameContext(double calendarTicks, int daysInMonth, double hemisphereScale,
                        CelestialRuntimeSettings settings) {

        Frame frameAt(double observerZ) {
            CelestialMath.Input input = new CelestialMath.Input(observerZ, hemisphereScale, calendarTicks,
                    daysInMonth, settings.resolvedSynodicDays(daysInMonth),
                    settings.resolvedAnomalisticDays(daysInMonth), settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale());
            return new Frame(CelestialMath.calculate(input), calendarTicks, daysInMonth);
        }
    }

    record Frame(CelestialMath.Result result, double calendarTicks, int daysInMonth) {
    }
}
