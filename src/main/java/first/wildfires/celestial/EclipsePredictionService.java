package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Deterministic next-event and bounded-window prediction shared by the planetarium and tests. */
public final class EclipsePredictionService {

    private static final int SEARCH_CONJUNCTIONS = 256;
    private static final int LATITUDE_SAMPLES = 720;
    private static final int SOLAR_TIME_SAMPLES = 512;
    private static final int SOLAR_REFINEMENT_ITERATIONS = 48;
    private static final int LUNAR_TIME_SAMPLES = 320;
    private static final long CURRENT_EVENT_SCAN_STEP_TICKS = 80L;
    private static final long CURRENT_EVENT_SCAN_HORIZON_TICKS =
            (long) (2.0D * CelestialMath.TICKS_IN_DAY);
    private static final List<CelestialEventType> DISPLAY_EVENT_TYPES = List.of(
            CelestialEventType.SOLAR_ECLIPSE,
            CelestialEventType.NEW_MOON,
            CelestialEventType.FULL_MOON,
            CelestialEventType.LUNAR_ECLIPSE,
            CelestialEventType.BLOOD_MOON,
            CelestialEventType.SUPERMOON);

    private EclipsePredictionService() {
    }

    public static Predictions predict(Level level, Vec3 observer) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(observer, "observer");
        CelestialRuntimeSettings settings = level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialConfig.serverSettings();
        return predict(Calendars.get(level).getCalendarTicks(),
                Calendars.get(level).getCalendarDaysInMonth(), TfeHemisphereScale.get(level),
                observer.z, settings);
    }

    /** Returns every solar and lunar eclipse overlapping the requested future TFC-day window. */
    public static Timeline predictTimeline(Level level, Vec3 observer, double horizonDays) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(observer, "observer");
        CelestialRuntimeSettings settings = level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialConfig.serverSettings();
        return predictTimeline(Calendars.get(level).getCalendarTicks(),
                Calendars.get(level).getCalendarDaysInMonth(), TfeHemisphereScale.get(level),
                observer.x, observer.z, settings, horizonDays);
    }

    /**
     * Returns the locally visible long-duration events at this instant, their independent first
     * exit times, and the next tick at which the presentation can change. Blood moon replaces the
     * synonymous lunar-eclipse row while its strict coverage threshold is active.
     */
    public static CurrentEvents currentEvents(Level level, Vec3 observer) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(observer, "observer");
        CelestialRuntimeSettings settings = level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialConfig.serverSettings();
        return currentEvents(Calendars.get(level).getCalendarTicks(),
                Calendars.get(level).getCalendarDaysInMonth(), TfeHemisphereScale.get(level),
                observer.z, settings);
    }

    public static CurrentEvents currentEvents(double calendarTicks, int daysInMonth,
                                              double hemisphereScale, double observerZ,
                                              CelestialRuntimeSettings settings) {
        if (!Double.isFinite(calendarTicks)) {
            throw new IllegalArgumentException("Calendar ticks must be finite");
        }
        Objects.requireNonNull(settings, "settings");
        long now = (long) Math.floor(calendarTicks);
        double synodicDays = settings.resolvedSynodicDays(daysInMonth);
        double anomalisticDays = settings.resolvedAnomalisticDays(daysInMonth);
        boolean[] initial = displayEventStates(calculate(observerZ, hemisphereScale, now,
                daysInMonth, synodicDays, anomalisticDays, settings), now);
        long[] firstChanges = new long[DISPLAY_EVENT_TYPES.size()];
        java.util.Arrays.fill(firstChanges, Long.MAX_VALUE);
        long horizon = saturatingAdd(now, CURRENT_EVENT_SCAN_HORIZON_TICKS);
        long previous = now;
        for (long sample = saturatingAdd(now, CURRENT_EVENT_SCAN_STEP_TICKS);
             sample <= horizon; sample = saturatingAdd(sample, CURRENT_EVENT_SCAN_STEP_TICKS)) {
            boolean[] states = displayEventStates(calculate(observerZ, hemisphereScale, sample,
                    daysInMonth, synodicDays, anomalisticDays, settings), sample);
            for (int index = 0; index < initial.length; index++) {
                if (firstChanges[index] == Long.MAX_VALUE && states[index] != initial[index]) {
                    firstChanges[index] = refineDisplayEventChange(index, initial[index], previous,
                            sample, observerZ, hemisphereScale, daysInMonth, synodicDays,
                            anomalisticDays, settings);
                }
            }
            if (sample == horizon || sample > horizon - CURRENT_EVENT_SCAN_STEP_TICKS) {
                break;
            }
            previous = sample;
        }

        List<CurrentEvent> active = new ArrayList<>();
        long nextChange = horizon;
        for (int index = 0; index < initial.length; index++) {
            if (firstChanges[index] != Long.MAX_VALUE) {
                nextChange = Math.min(nextChange, firstChanges[index]);
            }
            if (initial[index]) {
                long end = firstChanges[index] != Long.MAX_VALUE
                        ? firstChanges[index] : horizon;
                active.add(new CurrentEvent(DISPLAY_EVENT_TYPES.get(index), end));
            }
        }
        return new CurrentEvents(active, nextChange);
    }

    private static CelestialMath.Result calculate(double observerZ, double hemisphereScale,
                                                  long calendarTicks, int daysInMonth,
                                                  double synodicDays, double anomalisticDays,
                                                  CelestialRuntimeSettings settings) {
        return CelestialMath.calculate(new CelestialMath.Input(observerZ, hemisphereScale,
                calendarTicks, daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
    }

    private static boolean[] displayEventStates(CelestialMath.Result result, long calendarTicks) {
        boolean bloodMoon = CelestialEventType.BLOOD_MOON.matches(result, calendarTicks, null);
        boolean lunarEclipse = CelestialEventType.LUNAR_ECLIPSE.matches(result, calendarTicks, null);
        boolean[] states = new boolean[DISPLAY_EVENT_TYPES.size()];
        for (int index = 0; index < DISPLAY_EVENT_TYPES.size(); index++) {
            CelestialEventType type = DISPLAY_EVENT_TYPES.get(index);
            states[index] = switch (type) {
                case LUNAR_ECLIPSE -> lunarEclipse && !bloodMoon;
                case BLOOD_MOON -> bloodMoon;
                default -> type.matches(result, calendarTicks, null);
            };
        }
        return states;
    }

    private static long refineDisplayEventChange(int eventIndex, boolean initial,
                                                 long low, long high,
                                                 double observerZ, double hemisphereScale,
                                                 int daysInMonth, double synodicDays,
                                                 double anomalisticDays,
                                                 CelestialRuntimeSettings settings) {
        while (high - low > 1L) {
            long middle = low + (high - low) / 2L;
            boolean state = displayEventStates(calculate(observerZ, hemisphereScale, middle,
                    daysInMonth, synodicDays, anomalisticDays, settings), middle)[eventIndex];
            if (state == initial) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    /** Display-only periodic longitude; X still has no time or eclipse-coverage effect. */
    public static double displayLongitude(Level level, double x) {
        Objects.requireNonNull(level, "level");
        return displayLongitude(x, TfeHemisphereScale.get(level));
    }

    public static double displayLongitude(double x, double hemisphereScale) {
        if (!Double.isFinite(x) || !Double.isFinite(hemisphereScale)
                || Math.abs(hemisphereScale) < 1.0E-9D) {
            return 0.0D;
        }
        double period = Math.abs(hemisphereScale) * 4.0D;
        double wrapped = x - Math.floor((x + period * 0.5D) / period) * period;
        return wrapped / period * CelestialMath.TAU;
    }

    public static Predictions predict(double calendarTicks, int daysInMonth, double hemisphereScale,
                                      double observerZ, CelestialRuntimeSettings settings) {
        double nowDay = CelestialMath.calendarDays(calendarTicks);
        double daysInYear = CelestialMath.daysInYear(daysInMonth);
        double synodicDays = settings.resolvedSynodicDays(daysInMonth);
        double anomalisticDays = settings.resolvedAnomalisticDays(daysInMonth);
        double observerLatitude = CelestialMath.latitude(observerZ, hemisphereScale);
        SolarPrediction solar = nextSolar(nowDay, daysInMonth, daysInYear, hemisphereScale,
                observerLatitude, synodicDays, anomalisticDays, settings);
        LunarPrediction lunar = nextLunar(nowDay, daysInMonth, hemisphereScale, observerZ, synodicDays,
                anomalisticDays, settings);
        return new Predictions(solar, lunar);
    }

    public static Timeline predictTimeline(double calendarTicks, int daysInMonth,
                                           double hemisphereScale, double observerX,
                                           double observerZ, CelestialRuntimeSettings settings,
                                           double horizonDays) {
        if (!Double.isFinite(horizonDays) || horizonDays <= 0.0D) {
            throw new IllegalArgumentException("Prediction horizon must be finite and positive");
        }
        double nowDay = CelestialMath.calendarDays(calendarTicks);
        double endDay = nowDay + horizonDays;
        double daysInYear = CelestialMath.daysInYear(daysInMonth);
        double synodicDays = settings.resolvedSynodicDays(daysInMonth);
        double anomalisticDays = settings.resolvedAnomalisticDays(daysInMonth);
        double observerLatitude = CelestialMath.latitude(observerZ, hemisphereScale);
        List<SolarPrediction> solar = new ArrayList<>();
        List<LunarPrediction> lunar = new ArrayList<>();
        List<LunarPhasePrediction> phases = new ArrayList<>();

        long firstSolar = (long) Math.floor(nowDay / synodicDays - 0.5D) - 1L;
        long lastSolar = (long) Math.ceil(endDay / synodicDays + 0.5D) + 1L;
        for (long index = firstSolar; index <= lastSolar; index++) {
            SolarPrediction candidate = solarAt(index, daysInMonth, daysInYear, hemisphereScale,
                    observerLatitude, synodicDays, anomalisticDays, settings);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= calendarTicks
                    && candidate.startCalendarTicks() <= endDay * CelestialMath.TICKS_IN_DAY + 1.0E-9D) {
                solar.add(candidate);
            }
        }

        long firstLunar = (long) Math.floor(nowDay / synodicDays) - 1L;
        long lastLunar = (long) Math.ceil(endDay / synodicDays) + 1L;
        for (long index = firstLunar; index <= lastLunar; index++) {
            LunarPrediction candidate = lunarAt(index, daysInMonth, hemisphereScale, observerZ,
                    synodicDays, anomalisticDays, settings);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= calendarTicks
                    && candidate.startCalendarTicks() <= endDay * CelestialMath.TICKS_IN_DAY + 1.0E-9D) {
                lunar.add(candidate);
            }
            addVisiblePhase(phases, phaseAt(index, LunarPhaseKind.FULL_MOON, daysInMonth,
                    hemisphereScale, observerZ, synodicDays, anomalisticDays, settings),
                    calendarTicks, endDay);
            addVisiblePhase(phases, phaseAt(index, LunarPhaseKind.NEW_MOON, daysInMonth,
                    hemisphereScale, observerZ, synodicDays, anomalisticDays, settings),
                    calendarTicks, endDay);
        }
        solar.sort(Comparator.comparingDouble(SolarPrediction::greatestCalendarTicks));
        lunar.sort(Comparator.comparingDouble(LunarPrediction::greatestCalendarTicks));
        phases.sort(Comparator.comparingDouble(LunarPhasePrediction::calendarTicks));
        return new Timeline(calendarTicks, endDay * CelestialMath.TICKS_IN_DAY,
                displayLongitude(observerX, hemisphereScale), observerLatitude, solar, lunar,
                phases);
    }

    private static void addVisiblePhase(List<LunarPhasePrediction> phases,
                                        LunarPhasePrediction candidate,
                                        double startCalendarTicks, double endDay) {
        if (candidate.present() && candidate.calendarTicks() + 1.0E-9D >= startCalendarTicks
                && candidate.calendarTicks() <= endDay * CelestialMath.TICKS_IN_DAY + 1.0E-9D) {
            phases.add(candidate);
        }
    }

    private static SolarPrediction nextSolar(double nowDay, int daysInMonth, double daysInYear,
                                             double hemisphereScale, double observerLatitude,
                                             double synodicDays, double anomalisticDays,
                                             CelestialRuntimeSettings settings) {
        long firstIndex = (long) Math.floor(nowDay / synodicDays - 0.5D);
        for (long index = firstIndex; index < firstIndex + SEARCH_CONJUNCTIONS; index++) {
            SolarPrediction candidate = solarAt(index, daysInMonth, daysInYear, hemisphereScale,
                    observerLatitude, synodicDays, anomalisticDays, settings);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= nowDay
                    * CelestialMath.TICKS_IN_DAY) {
                return candidate;
            }
        }
        return SolarPrediction.NONE;
    }

    private static SolarPrediction solarAt(long index, int daysInMonth, double daysInYear,
                                           double hemisphereScale, double observerLatitude,
                                           double synodicDays, double anomalisticDays,
                                           CelestialRuntimeSettings settings) {
        SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, daysInYear,
                synodicDays, settings.nodalYears(), settings.lunarInclinationRadians());
        if (!event.intersectsWorld()) {
            return SolarPrediction.NONE;
        }
        double centerZ = zForLatitude(event.greatestLatitude(), hemisphereScale);
        CelestialMath.Result center = CelestialMath.calculate(new CelestialMath.Input(centerZ,
                hemisphereScale, event.conjunctionDay() * CelestialMath.TICKS_IN_DAY, daysInMonth,
                synodicDays, anomalisticDays, settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
        double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(settings.sunScale()));
        double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.moonBodyHalfSize(settings.moonScale(), center.moonDistance()),
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarWindow window = scanSolarWindow(event, sunHalf, moonHalf, synodicDays, daysInYear);
        if (!window.present()) {
            return SolarPrediction.NONE;
        }
        double startDay = window.startDay();
        double endDay = window.endDay();
        BandAccumulator partial = new BandAccumulator();
        BandAccumulator penumbra = new BandAccumulator();
        BandAccumulator umbra = new BandAccumulator();
        double strongest = window.maximumCoverage();
        for (int sample = 0; sample <= LATITUDE_SAMPLES; sample++) {
            double latitude = -Math.PI * 0.5D + Math.PI * sample / LATITUDE_SAMPLES;
            // These are global latitude bands. X has no eclipse authority, so every
            // latitude represents all longitudes; whether the current observer's
            // meridian is in daylight must not erase a globally real contact.
            double maximum = SolarEclipseRegion.maximumCoverageAtLatitude(event, latitude,
                    sunHalf, moonHalf, synodicDays);
            strongest = Math.max(strongest, maximum);
            partial.accept(latitude, maximum > 0.0D);
            penumbra.accept(latitude, maximum >= 0.5D);
            umbra.accept(latitude, maximum >= 0.8D);
        }
        if (strongest <= 0.0D) {
            return SolarPrediction.NONE;
        }
        double localMaximum = maximumVisibleSolarCoverageAtLatitude(event, observerLatitude,
                sunHalf, moonHalf, synodicDays, daysInYear);
        return new SolarPrediction(true, event.conjunctionIndex(),
                window.greatestDay() * CelestialMath.TICKS_IN_DAY,
                startDay * CelestialMath.TICKS_IN_DAY, endDay * CelestialMath.TICKS_IN_DAY,
                daysInYear, event.lunarLatitude(), event.greatestLatitude(),
                event.trackLatitude(startDay), event.trackLatitude(endDay), strongest,
                localMaximum, partial.band(), penumbra.band(), umbra.band(),
                synodicDays, sunHalf, moonHalf);
    }

    /** Maximum square-disc coverage that is actually above this latitude's daytime horizon. */
    public static double maximumVisibleSolarCoverageAtLatitude(SolarPrediction prediction,
                                                                double latitudeRadians) {
        if (prediction == null || !prediction.present()) {
            return 0.0D;
        }
        return maximumVisibleSolarCoverageAtLatitude(prediction.event(), latitudeRadians,
                prediction.sunHalfTangent(), prediction.moonHalfTangent(), prediction.synodicDays(),
                prediction.daysInYear());
    }

    /** Maximum geometric coverage at a latitude across the global event, independent of local daylight. */
    public static double maximumGlobalSolarCoverageAtLatitude(SolarPrediction prediction,
                                                               double latitudeRadians) {
        if (prediction == null || !prediction.present() || !Double.isFinite(latitudeRadians)) {
            return 0.0D;
        }
        return SolarEclipseRegion.maximumCoverageAtLatitude(prediction.event(), latitudeRadians,
                prediction.sunHalfTangent(), prediction.moonHalfTangent(), prediction.synodicDays());
    }

    static double maximumVisibleSolarCoverageAtTime(SolarPrediction prediction,
                                                     double calendarDays) {
        if (prediction == null || !prediction.present()) {
            return 0.0D;
        }
        return maximumVisibleSolarCoverageAtTime(prediction.event(), calendarDays,
                prediction.sunHalfTangent(), prediction.moonHalfTangent(), prediction.synodicDays(),
                prediction.daysInYear());
    }

    private static double maximumVisibleSolarCoverageAtLatitude(SolarEclipseRegion.Event event,
                                                                 double latitudeRadians,
                                                                 double sunHalf, double moonHalf,
                                                                 double synodicDays,
                                                                 double daysInYear) {
        if (event == null || !event.valid() || !Double.isFinite(latitudeRadians)
                || !Double.isFinite(daysInYear) || daysInYear <= 0.0D) {
            return 0.0D;
        }
        double contact = sunHalf + moonHalf;
        double halfDuration = Math.atan(contact) * synodicDays / CelestialMath.TAU;
        double maximum = 0.0D;
        for (int sample = 0; sample <= 128; sample++) {
            double day = event.conjunctionDay()
                    + (sample / 128.0D * 2.0D - 1.0D) * halfDuration;
            if (CelestialMath.solarElevationAt(latitudeRadians, day, daysInYear) > 0.0D) {
                maximum = Math.max(maximum, SolarEclipseRegion.coverageAt(event, day,
                        latitudeRadians, sunHalf, moonHalf, synodicDays));
            }
        }
        return maximum;
    }

    private static SolarWindow scanSolarWindow(SolarEclipseRegion.Event event,
                                               double sunHalf, double moonHalf,
                                               double synodicDays, double daysInYear) {
        double scanHalfDays = Math.atan((sunHalf + moonHalf) * 1.5D)
                * synodicDays / CelestialMath.TAU;
        double firstDay = event.conjunctionDay() - scanHalfDays;
        double step = 2.0D * scanHalfDays / SOLAR_TIME_SAMPLES;
        double firstContactLow = Double.NaN;
        double firstContactHigh = Double.NaN;
        double lastContactLow = Double.NaN;
        double lastContactHigh = Double.NaN;
        double bestDay = event.conjunctionDay();
        double bestCoverage = 0.0D;
        double previousDay = firstDay;
        double previousCoverage = SolarEclipseRegion.maximumCoverageAtTime(event, previousDay,
                sunHalf, moonHalf, synodicDays);
        boolean inside = previousCoverage > 0.0D;
        if (inside) {
            firstContactLow = firstDay - step;
            firstContactHigh = firstDay;
        }
        for (int sample = 1; sample <= SOLAR_TIME_SAMPLES; sample++) {
            double day = firstDay + step * sample;
            double coverage = SolarEclipseRegion.maximumCoverageAtTime(event, day,
                    sunHalf, moonHalf, synodicDays);
            if (coverage > bestCoverage) {
                bestCoverage = coverage;
                bestDay = day;
            }
            boolean active = coverage > 0.0D;
            if (active && !inside && !Double.isFinite(firstContactHigh)) {
                firstContactLow = previousDay;
                firstContactHigh = day;
            } else if (!active && inside) {
                lastContactLow = previousDay;
                lastContactHigh = day;
            }
            inside = active;
            previousDay = day;
        }
        if (inside) {
            lastContactLow = previousDay;
            lastContactHigh = previousDay + step;
        }
        if (!(bestCoverage > 0.0D) || !Double.isFinite(firstContactLow)
                || !Double.isFinite(firstContactHigh) || !Double.isFinite(lastContactLow)
                || !Double.isFinite(lastContactHigh)) {
            return SolarWindow.NONE;
        }
        double startDay = refineSolarContact(event, firstContactLow, firstContactHigh,
                sunHalf, moonHalf, synodicDays, daysInYear, true);
        double endDay = refineSolarContact(event, lastContactLow, lastContactHigh,
                sunHalf, moonHalf, synodicDays, daysInYear, false);
        double maximumLow = Math.max(startDay, bestDay - step);
        double maximumHigh = Math.min(endDay, bestDay + step);
        for (int iteration = 0; iteration < SOLAR_REFINEMENT_ITERATIONS; iteration++) {
            double firstThird = (2.0D * maximumLow + maximumHigh) / 3.0D;
            double secondThird = (maximumLow + 2.0D * maximumHigh) / 3.0D;
            double firstCoverage = SolarEclipseRegion.maximumCoverageAtTime(event, firstThird,
                    sunHalf, moonHalf, synodicDays);
            double secondCoverage = SolarEclipseRegion.maximumCoverageAtTime(event, secondThird,
                    sunHalf, moonHalf, synodicDays);
            if (firstCoverage < secondCoverage) {
                maximumLow = firstThird;
            } else {
                maximumHigh = secondThird;
            }
        }
        double greatestDay = (maximumLow + maximumHigh) * 0.5D;
        double maximumCoverage = SolarEclipseRegion.maximumCoverageAtTime(event, greatestDay,
                sunHalf, moonHalf, synodicDays);
        return new SolarWindow(true, startDay, greatestDay, endDay, maximumCoverage);
    }

    private static double refineSolarContact(SolarEclipseRegion.Event event,
                                             double low, double high,
                                             double sunHalf, double moonHalf,
                                             double synodicDays, double daysInYear,
                                             boolean entering) {
        for (int iteration = 0; iteration < SOLAR_REFINEMENT_ITERATIONS; iteration++) {
            double middle = (low + high) * 0.5D;
            boolean active = SolarEclipseRegion.maximumCoverageAtTime(event, middle,
                    sunHalf, moonHalf, synodicDays) > 0.0D;
            if (entering == active) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return entering ? high : low;
    }

    private static double maximumVisibleSolarCoverageAtTime(SolarEclipseRegion.Event event,
                                                             double calendarDays,
                                                             double sunHalf, double moonHalf,
                                                             double synodicDays,
                                                             double daysInYear) {
        double maximum = 0.0D;
        for (int sample = 0; sample <= LATITUDE_SAMPLES; sample++) {
            double latitude = -Math.PI * 0.5D + Math.PI * sample / LATITUDE_SAMPLES;
            if (CelestialMath.solarElevationAt(latitude, calendarDays, daysInYear) > 0.0D) {
                maximum = Math.max(maximum, SolarEclipseRegion.coverageAt(event, calendarDays,
                        latitude, sunHalf, moonHalf, synodicDays));
            }
        }
        return maximum;
    }

    private static LunarPrediction nextLunar(double nowDay, int daysInMonth, double hemisphereScale,
                                             double observerZ,
                                             double synodicDays, double anomalisticDays,
                                             CelestialRuntimeSettings settings) {
        long firstIndex = (long) Math.floor(nowDay / synodicDays);
        for (long index = firstIndex; index < firstIndex + SEARCH_CONJUNCTIONS; index++) {
            LunarPrediction candidate = lunarAt(index, daysInMonth, hemisphereScale, observerZ,
                    synodicDays, anomalisticDays, settings);
            if (candidate.present() && candidate.eclipse()
                    && candidate.endCalendarTicks() + 1.0E-9D >= nowDay
                    * CelestialMath.TICKS_IN_DAY) {
                return candidate;
            }
        }
        return LunarPrediction.NONE;
    }

    private static LunarPrediction lunarAt(long index, int daysInMonth, double hemisphereScale,
                                           double observerZ,
                                           double synodicDays, double anomalisticDays,
                                           CelestialRuntimeSettings settings) {
        double fullMoonDay = index * synodicDays;
        double scanHalfDays = Math.min(0.75D, synodicDays * 0.08D);
        double maximumUmbra = 0.0D;
        double maximumPenumbra = 0.0D;
        double maximumSupermoon = 0.0D;
        double maximumUmbraDay = fullMoonDay;
        double maximumPenumbraDay = fullMoonDay;
        double supermoonDay = fullMoonDay;
        double bestFullMoonDistance = Double.POSITIVE_INFINITY;
        double firstEclipseContact = Double.NaN;
        double lastEclipseContact = Double.NaN;
        double firstSupermoonContact = Double.NaN;
        double lastSupermoonContact = Double.NaN;
        CelestialMath.Result maximumUmbraResult = null;
        CelestialMath.Result maximumPenumbraResult = null;
        for (int sample = 0; sample <= LUNAR_TIME_SAMPLES; sample++) {
            double day = fullMoonDay - scanHalfDays
                    + 2.0D * scanHalfDays * sample / LUNAR_TIME_SAMPLES;
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(observerZ,
                    hemisphereScale, day * CelestialMath.TICKS_IN_DAY, daysInMonth,
                    synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
            double penumbraCoverage = result.lunarEclipseRegion().penumbraCoverage();
            boolean eclipseContact = penumbraCoverage > 0.0D;
            boolean supermoonContact = result.supermoon()
                    >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
            if (eclipseContact) {
                if (!Double.isFinite(firstEclipseContact)) {
                    firstEclipseContact = day;
                }
                lastEclipseContact = day;
            }
            if (supermoonContact) {
                if (!Double.isFinite(firstSupermoonContact)) {
                    firstSupermoonContact = day;
                }
                lastSupermoonContact = day;
            }
            if (eclipseContact && result.lunarEclipse() > maximumUmbra) {
                maximumUmbra = result.lunarEclipse();
                maximumUmbraDay = day;
                maximumUmbraResult = result;
            }
            if (eclipseContact && penumbraCoverage > maximumPenumbra) {
                maximumPenumbra = penumbraCoverage;
                maximumPenumbraDay = day;
                maximumPenumbraResult = result;
            }
            if (supermoonContact) {
                maximumSupermoon = Math.max(maximumSupermoon, result.supermoon());
                double fullMoonDistance = Math.abs(day - fullMoonDay);
                if (fullMoonDistance < bestFullMoonDistance) {
                    bestFullMoonDistance = fullMoonDistance;
                    supermoonDay = day;
                }
            }
        }
        boolean eclipse = maximumPenumbra > 0.0D && maximumPenumbraResult != null;
        boolean supermoon = maximumSupermoon >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
        double firstContact = eclipse ? firstEclipseContact : firstSupermoonContact;
        double lastContact = eclipse ? lastEclipseContact : lastSupermoonContact;
        if ((!eclipse && !supermoon) || !Double.isFinite(firstContact)
                || !Double.isFinite(lastContact)) {
            return LunarPrediction.NONE;
        }
        double scanStep = 2.0D * scanHalfDays / LUNAR_TIME_SAMPLES;
        firstContact = refineLunarPredictionContact(firstContact - scanStep, firstContact, true, eclipse,
                daysInMonth, hemisphereScale, observerZ, synodicDays, anomalisticDays, settings);
        lastContact = refineLunarPredictionContact(lastContact, lastContact + scanStep, false, eclipse,
                daysInMonth, hemisphereScale, observerZ, synodicDays, anomalisticDays, settings);
        CelestialMath.Result greatestEclipseResult = maximumUmbraResult != null
                ? maximumUmbraResult : maximumPenumbraResult;
        double greatestEclipseDay = maximumUmbraResult != null
                ? maximumUmbraDay : maximumPenumbraDay;
        CelestialDiscGeometry.AlignedSquare shadow = CelestialDiscGeometry.AlignedSquare.NONE;
        if (eclipse) {
            var projection = greatestEclipseResult.lunarEclipseRegion();
            shadow = new CelestialDiscGeometry.AlignedSquare(projection.shadowCenterX(),
                    projection.shadowCenterY(), projection.shadowRadius(), true);
        }
        LunarEclipseKind kind = lunarKind(maximumUmbra, maximumPenumbra);
        double greatestDay = eclipse ? greatestEclipseDay : supermoonDay;
        return new LunarPrediction(true, index,
                greatestDay * CelestialMath.TICKS_IN_DAY,
                firstContact * CelestialMath.TICKS_IN_DAY,
                lastContact * CelestialMath.TICKS_IN_DAY,
                maximumUmbra, maximumPenumbra, kind, eclipse, maximumSupermoon,
                shadow.centerX(), shadow.centerY(), shadow.radius());
    }

    private static double refineLunarPredictionContact(double low, double high, boolean entering,
                                                       boolean eclipse,
                                                       int daysInMonth, double hemisphereScale,
                                                       double observerZ, double synodicDays,
                                                       double anomalisticDays,
                                                       CelestialRuntimeSettings settings) {
        for (int iteration = 0; iteration < SOLAR_REFINEMENT_ITERATIONS; iteration++) {
            double middle = (low + high) * 0.5D;
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(observerZ,
                    hemisphereScale, middle * CelestialMath.TICKS_IN_DAY, daysInMonth,
                    synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
            boolean active = eclipse ? result.lunarEclipseRegion().penumbraCoverage() > 0.0D
                    : result.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
            if (entering == active) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return entering ? high : low;
    }

    static LunarEclipseKind lunarKind(double maximumUmbra, double maximumPenumbra) {
        if (!Double.isFinite(maximumUmbra) || !Double.isFinite(maximumPenumbra)
                || maximumPenumbra <= 0.0D) {
            return LunarEclipseKind.NONE;
        }
        if (maximumUmbra <= 0.0D) {
            return LunarEclipseKind.PENUMBRAL;
        }
        return maximumUmbra >= 0.9D ? LunarEclipseKind.TOTAL : LunarEclipseKind.PARTIAL;
    }

    private static LunarPhasePrediction phaseAt(long index, LunarPhaseKind kind,
                                                int daysInMonth, double hemisphereScale,
                                                double observerZ, double synodicDays,
                                                double anomalisticDays,
                                                CelestialRuntimeSettings settings) {
        double centerDay = (index + (kind == LunarPhaseKind.NEW_MOON ? 0.5D : 0.0D))
                * synodicDays;
        double halfWindow = Math.min(CelestialMath.SUPERMOON_FULL_MOON_HALF_WINDOW_DAYS,
                synodicDays * Math.acos(0.99D) / CelestialMath.TAU);
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestCenterDistance = Double.POSITIVE_INFINITY;
        double bestDay = Double.NaN;
        double bestIllumination = kind == LunarPhaseKind.FULL_MOON ? 0.0D : 1.0D;
        for (int sample = 0; sample <= LUNAR_TIME_SAMPLES; sample++) {
            double day = centerDay - halfWindow
                    + 2.0D * halfWindow * sample / LUNAR_TIME_SAMPLES;
            CelestialMath.Result result = CelestialMath.calculate(new CelestialMath.Input(observerZ,
                    hemisphereScale, day * CelestialMath.TICKS_IN_DAY, daysInMonth,
                    synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale()));
            double illumination = result.illuminatedFraction();
            boolean phaseReached = kind == LunarPhaseKind.FULL_MOON
                    ? illumination >= 0.995D : illumination <= 0.005D;
            if (!phaseReached) {
                continue;
            }
            double score = kind == LunarPhaseKind.FULL_MOON ? illumination : 1.0D - illumination;
            double centerDistance = Math.abs(day - centerDay);
            if (score > bestScore + 1.0E-12D
                    || Math.abs(score - bestScore) <= 1.0E-12D
                    && centerDistance < bestCenterDistance) {
                bestScore = score;
                bestCenterDistance = centerDistance;
                bestDay = day;
                bestIllumination = illumination;
            }
        }
        return Double.isFinite(bestDay)
                ? new LunarPhasePrediction(true, index, kind,
                bestDay * CelestialMath.TICKS_IN_DAY, bestIllumination)
                : LunarPhasePrediction.NONE;
    }

    /** One stable inverse branch of the repeating TFC latitude triangle. */
    public static double zForLatitude(double latitudeRadians, double hemisphereScale) {
        if (!Double.isFinite(latitudeRadians) || !Double.isFinite(hemisphereScale)
                || Math.abs(hemisphereScale) < 1.0E-9D) {
            return 0.0D;
        }
        double latitude = Math.max(-Math.PI * 0.5D, Math.min(Math.PI * 0.5D, latitudeRadians));
        return hemisphereScale * (0.5D - latitude / (Math.PI * 0.5D));
    }

    public enum LunarEclipseKind {
        NONE,
        PENUMBRAL,
        PARTIAL,
        TOTAL
    }

    private record SolarWindow(boolean present, double startDay, double greatestDay,
                               double endDay, double maximumCoverage) {
        private static final SolarWindow NONE = new SolarWindow(false, 0.0D,
                0.0D, 0.0D, 0.0D);
    }

    public record Predictions(SolarPrediction solar, LunarPrediction lunar) {
    }

    public record CurrentEvent(CelestialEventType type, long endCalendarTicks) {
        public CurrentEvent {
            Objects.requireNonNull(type, "type");
        }
    }

    public record CurrentEvents(List<CurrentEvent> events, long nextChangeCalendarTicks) {
        public CurrentEvents {
            events = List.copyOf(events);
        }
    }

    public record Timeline(double startCalendarTicks, double endCalendarTicks,
                           double observerLongitudeRadians, double observerLatitudeRadians,
                           List<SolarPrediction> solar, List<LunarPrediction> lunar,
                           List<LunarPhasePrediction> phases) {
        public Timeline {
            solar = List.copyOf(solar);
            lunar = List.copyOf(lunar);
            phases = List.copyOf(phases);
        }
    }

    public record LatitudeBand(boolean present, double southRadians, double northRadians) {
        public static final LatitudeBand NONE = new LatitudeBand(false, 0.0D, 0.0D);
    }

    public record SolarPrediction(boolean present, long conjunctionIndex, double greatestCalendarTicks,
                                  double startCalendarTicks, double endCalendarTicks,
                                  double daysInYear, double lunarLatitudeRadians,
                                  double greatestTrackLatitudeRadians,
                                  double startTrackLatitudeRadians, double endTrackLatitudeRadians,
                                  double globalMaximumCoverage, double observerMaximumCoverage,
                                  LatitudeBand partialBand, LatitudeBand penumbraBand,
                                  LatitudeBand umbraBand, double synodicDays,
                                  double sunHalfTangent, double moonHalfTangent) {
        public static final SolarPrediction NONE = new SolarPrediction(false, 0L, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                LatitudeBand.NONE, LatitudeBand.NONE, LatitudeBand.NONE,
                0.0D, 0.0D, 0.0D);

        public SolarEclipseRegion.Event event() {
            if (!present) {
                return SolarEclipseRegion.Event.NONE;
            }
            return new SolarEclipseRegion.Event(conjunctionIndex,
                    (conjunctionIndex + 0.5D) * synodicDays, daysInYear,
                    lunarLatitudeRadians, greatestTrackLatitudeRadians, true, true);
        }
    }

    public record LunarPrediction(boolean present, long fullMoonIndex, double greatestCalendarTicks,
                                  double startCalendarTicks, double endCalendarTicks,
                                  double maximumCoverage, double maximumPenumbraCoverage,
                                  LunarEclipseKind kind,
                                  boolean eclipse, double supermoonIntensity,
                                  double shadowCenterX, double shadowCenterY, double shadowRadius) {
        public static final LunarPrediction NONE = new LunarPrediction(false, 0L, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, LunarEclipseKind.NONE, false, 0.0D,
                0.0D, 0.0D, 0.0D);

        public double displayMaximumCoverage() {
            return maximumCoverage > 0.0D ? maximumCoverage : maximumPenumbraCoverage;
        }

        public boolean supermoon() {
            return supermoonIntensity >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
        }

        public boolean bloodMoon() {
            return eclipse && maximumCoverage > CelestialState.BLOOD_MOON_COVERAGE_THRESHOLD;
        }
    }

    public enum LunarPhaseKind {
        FULL_MOON,
        NEW_MOON
    }

    public record LunarPhasePrediction(boolean present, long phaseIndex, LunarPhaseKind kind,
                                       double calendarTicks, double illuminatedFraction) {
        public static final LunarPhasePrediction NONE = new LunarPhasePrediction(false, 0L,
                LunarPhaseKind.FULL_MOON, 0.0D, 0.0D);
    }

    private static final class BandAccumulator {
        private double south = Double.POSITIVE_INFINITY;
        private double north = Double.NEGATIVE_INFINITY;

        void accept(double latitude, boolean inside) {
            if (inside) {
                south = Math.min(south, latitude);
                north = Math.max(north, latitude);
            }
        }

        LatitudeBand band() {
            return south <= north ? new LatitudeBand(true, south, north) : LatitudeBand.NONE;
        }
    }
}
