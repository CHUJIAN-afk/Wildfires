package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.LunarEclipseState;
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
    private static final int SOLAR_ECLIPSE_BIT = 1 << 0;
    private static final int NEW_MOON_BIT = 1 << 1;
    private static final int FULL_MOON_BIT = 1 << 2;
    private static final int LUNAR_ECLIPSE_BIT = 1 << 3;
    private static final int BLOOD_MOON_BIT = 1 << 4;
    private static final int SUPERMOON_BIT = 1 << 5;
    private static final ThreadLocal<long[]> CURRENT_EVENT_CHANGE_SCRATCH =
            ThreadLocal.withInitial(() -> new long[DISPLAY_EVENT_TYPES.size()]);
    private static final ThreadLocal<double[]> SOLAR_LATITUDE_MAXIMUM_SCRATCH =
            ThreadLocal.withInitial(() -> new double[LATITUDE_SAMPLES + 1]);
    private static final double[] SOLAR_LATITUDE_RADIANS = createSolarLatitudeSamples();

    private EclipsePredictionService() {
    }

    private static double[] createSolarLatitudeSamples() {
        double[] latitudes = new double[LATITUDE_SAMPLES + 1];
        for (int sample = 0; sample <= LATITUDE_SAMPLES; sample++) {
            latitudes[sample] = -Math.PI * 0.5D + Math.PI * sample / LATITUDE_SAMPLES;
        }
        return latitudes;
    }

    static double solarLatitudeSample(int index) {
        return SOLAR_LATITUDE_RADIANS[index];
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
     * Returns the same anomalous solar/lunar event timeline without scanning ordinary full/new
     * Moon markers. The complete {@link #predictTimeline(Level, Vec3, double)} contract remains
     * unchanged for callers that consume {@link Timeline#phases()}.
     */
    public static Timeline predictAnomalousTimeline(Level level, Vec3 observer,
                                                     double horizonDays) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(observer, "observer");
        CelestialRuntimeSettings settings = level.isClientSide()
                ? CelestialSettingsCache.current()
                : CelestialConfig.serverSettings();
        return predictAnomalousTimeline(Calendars.get(level).getCalendarTicks(),
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
        CelestialRuntimeSettings.PreparedPeriods prepared = settings.preparedPeriods(daysInMonth);
        double synodicDays = prepared.synodicDays();
        double anomalisticDays = prepared.anomalisticDays();
        double sineLunarInclination = prepared.sineLunarInclination();
        CelestialMath.ObserverLatitudeContext observerLatitude =
                CelestialMath.prepareObserverLatitude(observerZ, hemisphereScale);
        int initial = displayEventMask(displayEventSample(observerLatitude, now,
                daysInMonth, synodicDays, anomalisticDays, settings,
                sineLunarInclination), now);
        long[] firstChanges = CURRENT_EVENT_CHANGE_SCRATCH.get();
        java.util.Arrays.fill(firstChanges, Long.MAX_VALUE);
        int unresolvedMask = (1 << DISPLAY_EVENT_TYPES.size()) - 1;
        long horizon = saturatingAdd(now, CURRENT_EVENT_SCAN_HORIZON_TICKS);
        long previous = now;
        for (long sample = saturatingAdd(now, CURRENT_EVENT_SCAN_STEP_TICKS);
             sample <= horizon; sample = saturatingAdd(sample, CURRENT_EVENT_SCAN_STEP_TICKS)) {
            int states = displayEventMask(displayEventSample(observerLatitude, sample,
                    daysInMonth, synodicDays, anomalisticDays, settings,
                    sineLunarInclination), sample);
            int changedMask = (states ^ initial) & unresolvedMask;
            while (changedMask != 0) {
                int index = Integer.numberOfTrailingZeros(changedMask);
                int bit = 1 << index;
                boolean initialState = (initial & bit) != 0;
                firstChanges[index] = refineDisplayEventChange(index, initialState, previous,
                        sample, observerLatitude, daysInMonth, synodicDays,
                        anomalisticDays, settings, sineLunarInclination);
                unresolvedMask &= ~bit;
                changedMask &= changedMask - 1;
            }
            if (unresolvedMask == 0) {
                break;
            }
            if (sample == horizon || sample > horizon - CURRENT_EVENT_SCAN_STEP_TICKS) {
                break;
            }
            previous = sample;
        }

        CurrentEvent[] active = initial == 0
                ? null : new CurrentEvent[Integer.bitCount(initial)];
        int activeIndex = 0;
        long nextChange = horizon;
        for (int index = 0; index < DISPLAY_EVENT_TYPES.size(); index++) {
            if (firstChanges[index] != Long.MAX_VALUE) {
                nextChange = Math.min(nextChange, firstChanges[index]);
            }
            if ((initial & 1 << index) != 0) {
                long end = firstChanges[index] != Long.MAX_VALUE
                        ? firstChanges[index] : horizon;
                active[activeIndex++] = new CurrentEvent(DISPLAY_EVENT_TYPES.get(index), end);
            }
        }
        return new CurrentEvents(active == null ? List.of() : List.of(active), nextChange);
    }

    private static CelestialMath.DisplayEventSample displayEventSample(
            CelestialMath.ObserverLatitudeContext observerLatitude, long calendarTicks,
            int daysInMonth,
            double synodicDays, double anomalisticDays, CelestialRuntimeSettings settings,
            double sineLunarInclination) {
        return CelestialMath.displayEventSampleAt(observerLatitude, calendarTicks,
                daysInMonth,
                synodicDays, anomalisticDays, settings.nodalYears(),
                settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                sineLunarInclination);
    }

    static int displayEventMask(CelestialMath.Result result, long calendarTicks) {
        return displayEventMask(result.illuminatedFraction(), result.solarEclipse(),
                result.lunarEclipseRegion().penumbraCoverage(), result.supermoon(),
                result.bloodMoon(),
                result.solarElevation(), result.moonElevation());
    }

    static int displayEventMask(CelestialMath.EventSample result, long calendarTicks) {
        return displayEventMask(result.illuminatedFraction(), result.solarEclipse(),
                result.lunarEclipseRegion().penumbraCoverage(), result.supermoon(),
                result.bloodMoon(),
                result.solarElevation(), result.moonElevation());
    }

    static int displayEventMask(CelestialMath.DisplayEventSample result, long calendarTicks) {
        return displayEventMask(result.illuminatedFraction(), result.solarEclipse(),
                result.lunarPenumbraCoverage(), result.supermoon(), result.bloodMoon(),
                result.solarElevation(), result.moonElevation());
    }

    private static int displayEventMask(double illuminatedFraction, double solarEclipse,
                                        double lunarPenumbraCoverage,
                                        double supermoon, double bloodMoonCoverage,
                                        double solarElevation, double moonElevation) {
        boolean localLunarNight = Double.isFinite(moonElevation) && moonElevation > 0.0D
                && Double.isFinite(solarElevation) && solarElevation <= 0.0D;
        boolean bloodMoon = bloodMoonCoverage > CelestialGameplayRules.ACTIVE_THRESHOLD
                && localLunarNight;
        boolean lunarEclipse = lunarPenumbraCoverage > 0.0D && localLunarNight;
        int mask = 0;
        if (Double.isFinite(solarEclipse) && solarEclipse > 0.0D
                && Double.isFinite(solarElevation) && solarElevation > 0.0D) {
            mask |= SOLAR_ECLIPSE_BIT;
        }
        if (illuminatedFraction <= 0.005D
                && moonElevation > 0.0D && solarElevation > 0.0D) {
            mask |= NEW_MOON_BIT;
        }
        if (illuminatedFraction >= 0.995D && localLunarNight) {
            mask |= FULL_MOON_BIT;
        }
        if (lunarEclipse && !bloodMoon) {
            mask |= LUNAR_ECLIPSE_BIT;
        }
        if (bloodMoon) {
            mask |= BLOOD_MOON_BIT;
        }
        if (supermoon >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                && localLunarNight) {
            mask |= SUPERMOON_BIT;
        }
        return mask;
    }

    /** Scalar form used when a refinement scan is following exactly one known display row. */
    static boolean displayEventState(int eventIndex, CelestialMath.DisplayEventSample result) {
        double solarElevation = result.solarElevation();
        double moonElevation = result.moonElevation();
        boolean localLunarNight = Double.isFinite(moonElevation) && moonElevation > 0.0D
                && Double.isFinite(solarElevation) && solarElevation <= 0.0D;
        return switch (eventIndex) {
            case 0 -> Double.isFinite(result.solarEclipse())
                    && result.solarEclipse() > 0.0D
                    && Double.isFinite(solarElevation) && solarElevation > 0.0D;
            case 1 -> result.illuminatedFraction() <= 0.005D
                    && moonElevation > 0.0D && solarElevation > 0.0D;
            case 2 -> result.illuminatedFraction() >= 0.995D && localLunarNight;
            case 3 -> result.lunarPenumbraCoverage() > 0.0D && localLunarNight
                    && !(result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                    && localLunarNight);
            case 4 -> result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                    && localLunarNight;
            case 5 -> result.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                    && localLunarNight;
            default -> throw new IllegalArgumentException("Unknown display-event index "
                    + eventIndex);
        };
    }

    private static long refineDisplayEventChange(int eventIndex, boolean initial,
                                                 long low, long high,
                                                 CelestialMath.ObserverLatitudeContext observerLatitude,
                                                  int daysInMonth, double synodicDays,
                                                  double anomalisticDays,
                                                  CelestialRuntimeSettings settings,
                                                  double sineLunarInclination) {
        while (high - low > 1L) {
            long middle = low + (high - low) / 2L;
            boolean state = displayEventState(eventIndex,
                    displayEventSample(observerLatitude, middle,
                    daysInMonth, synodicDays, anomalisticDays, settings,
                    sineLunarInclination));
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
        CelestialRuntimeSettings.PreparedPeriods prepared = settings.preparedPeriods(daysInMonth);
        double synodicDays = prepared.synodicDays();
        double anomalisticDays = prepared.anomalisticDays();
        double observerLatitude = CelestialMath.latitude(observerZ, hemisphereScale);
        CelestialMath.SolarLatitudeContext observerLatitudeContext =
                CelestialMath.prepareSolarLatitude(observerLatitude);
        double sineLunarInclination = prepared.sineLunarInclination();
        SolarPrediction solar = nextSolar(nowDay, daysInMonth, daysInYear, hemisphereScale,
                observerLatitude, synodicDays, anomalisticDays, settings,
                sineLunarInclination);
        LunarPrediction lunar = nextLunar(nowDay, daysInMonth, observerLatitudeContext,
                synodicDays, anomalisticDays, settings, sineLunarInclination);
        return new Predictions(solar, lunar);
    }

    public static Timeline predictTimeline(double calendarTicks, int daysInMonth,
                                           double hemisphereScale, double observerX,
                                           double observerZ, CelestialRuntimeSettings settings,
                                           double horizonDays) {
        return predictTimeline(calendarTicks, daysInMonth, hemisphereScale, observerX,
                observerZ, settings, horizonDays, true);
    }

    public static Timeline predictAnomalousTimeline(double calendarTicks, int daysInMonth,
                                                     double hemisphereScale, double observerX,
                                                     double observerZ,
                                                     CelestialRuntimeSettings settings,
                                                     double horizonDays) {
        return predictTimeline(calendarTicks, daysInMonth, hemisphereScale, observerX,
                observerZ, settings, horizonDays, false);
    }

    private static Timeline predictTimeline(double calendarTicks, int daysInMonth,
                                            double hemisphereScale, double observerX,
                                            double observerZ, CelestialRuntimeSettings settings,
                                            double horizonDays, boolean includeOrdinaryPhases) {
        if (!Double.isFinite(horizonDays) || horizonDays <= 0.0D) {
            throw new IllegalArgumentException("Prediction horizon must be finite and positive");
        }
        double nowDay = CelestialMath.calendarDays(calendarTicks);
        double endDay = nowDay + horizonDays;
        double daysInYear = CelestialMath.daysInYear(daysInMonth);
        CelestialRuntimeSettings.PreparedPeriods prepared = settings.preparedPeriods(daysInMonth);
        double synodicDays = prepared.synodicDays();
        double anomalisticDays = prepared.anomalisticDays();
        double observerLatitude = CelestialMath.latitude(observerZ, hemisphereScale);
        CelestialMath.SolarLatitudeContext observerLatitudeContext =
                CelestialMath.prepareSolarLatitude(observerLatitude);
        double sineLunarInclination = prepared.sineLunarInclination();
        List<SolarPrediction> solar = new ArrayList<>();
        List<LunarPrediction> lunar = new ArrayList<>();
        List<LunarPhasePrediction> phases = includeOrdinaryPhases
                ? new ArrayList<>() : List.of();

        long firstSolar = (long) Math.floor(nowDay / synodicDays - 0.5D) - 1L;
        long lastSolar = (long) Math.ceil(endDay / synodicDays + 0.5D) + 1L;
        for (long index = firstSolar; index <= lastSolar; index++) {
            SolarPrediction candidate = solarAt(index, daysInYear, observerLatitude,
                    synodicDays, anomalisticDays, settings, sineLunarInclination);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= calendarTicks
                    && candidate.startCalendarTicks() <= endDay * CelestialMath.TICKS_IN_DAY + 1.0E-9D) {
                solar.add(candidate);
            }
        }

        long firstLunar = (long) Math.floor(nowDay / synodicDays) - 1L;
        long lastLunar = (long) Math.ceil(endDay / synodicDays) + 1L;
        for (long index = firstLunar; index <= lastLunar; index++) {
            LunarPrediction candidate = lunarAt(index, daysInMonth, observerLatitudeContext,
                    synodicDays, anomalisticDays, settings, sineLunarInclination);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= calendarTicks
                    && candidate.startCalendarTicks() <= endDay * CelestialMath.TICKS_IN_DAY + 1.0E-9D) {
                lunar.add(candidate);
            }
            if (includeOrdinaryPhases) {
                addVisiblePhase(phases, phaseAt(index, LunarPhaseKind.FULL_MOON, daysInMonth,
                        observerLatitudeContext, synodicDays, settings,
                        sineLunarInclination),
                        calendarTicks, endDay);
                addVisiblePhase(phases, phaseAt(index, LunarPhaseKind.NEW_MOON, daysInMonth,
                        observerLatitudeContext, synodicDays, settings,
                        sineLunarInclination),
                        calendarTicks, endDay);
            }
        }
        solar.sort(Comparator.comparingDouble(SolarPrediction::greatestCalendarTicks));
        lunar.sort(Comparator.comparingDouble(LunarPrediction::greatestCalendarTicks));
        if (includeOrdinaryPhases) {
            phases.sort(Comparator.comparingDouble(LunarPhasePrediction::calendarTicks));
        }
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
                                             CelestialRuntimeSettings settings,
                                             double sineLunarInclination) {
        long firstIndex = (long) Math.floor(nowDay / synodicDays - 0.5D);
        for (long index = firstIndex; index < firstIndex + SEARCH_CONJUNCTIONS; index++) {
            SolarPrediction candidate = solarAt(index, daysInYear, observerLatitude,
                    synodicDays, anomalisticDays, settings, sineLunarInclination);
            if (candidate.present() && candidate.endCalendarTicks() + 1.0E-9D >= nowDay
                    * CelestialMath.TICKS_IN_DAY) {
                return candidate;
            }
        }
        return SolarPrediction.NONE;
    }

    private static SolarPrediction solarAt(long index, double daysInYear,
                                           double observerLatitude,
                                           double synodicDays, double anomalisticDays,
                                           CelestialRuntimeSettings settings,
                                           double sineLunarInclination) {
        SolarEclipseRegion.Event event = SolarEclipseRegion.eventAt(index, daysInYear,
                synodicDays, settings.nodalYears(), settings.lunarInclinationRadians(),
                sineLunarInclination);
        if (!event.intersectsWorld()) {
            return SolarPrediction.NONE;
        }
        double centerCalendarTicks = event.conjunctionDay() * CelestialMath.TICKS_IN_DAY;
        double moonDistance = CelestialMath.moonDistanceAtCalendarTicks(centerCalendarTicks,
                anomalisticDays);
        double sunHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(settings.sunScale()));
        double moonHalf = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.moonBodyHalfSize(settings.moonScale(), moonDistance),
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
        double[] latitudeMaximums = SOLAR_LATITUDE_MAXIMUM_SCRATCH.get();
        SolarEclipseRegion.maximumCoverageAtLatitudeSamples(event, sunHalf, moonHalf,
                synodicDays, SOLAR_LATITUDE_RADIANS, latitudeMaximums);
        for (int sample = 0; sample <= LATITUDE_SAMPLES; sample++) {
            double latitude = SOLAR_LATITUDE_RADIANS[sample];
            // These are global latitude bands. X has no eclipse authority, so every
            // latitude represents all longitudes; whether the current observer's
            // meridian is in daylight must not erase a globally real contact.
            double maximum = latitudeMaximums[sample];
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
        CelestialMath.SolarLatitudeContext solarLatitude =
                CelestialMath.prepareSolarLatitude(latitudeRadians);
        for (int sample = 0; sample <= 128; sample++) {
            double day = event.conjunctionDay()
                    + (sample / 128.0D * 2.0D - 1.0D) * halfDuration;
            if (CelestialMath.solarElevationAt(solarLatitude, day, daysInYear) > 0.0D) {
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
        SolarEclipseRegion.PreparedCoverage prepared =
                SolarEclipseRegion.prepareCoverageAtTime(event, calendarDays,
                        sunHalf, moonHalf, synodicDays);
        if (prepared == null) {
            return maximum;
        }
        CelestialMath.SolarTimeContext solarTime =
                CelestialMath.prepareSolarTime(calendarDays, daysInYear);
        for (int sample = 0; sample <= LATITUDE_SAMPLES; sample++) {
            double latitude = SOLAR_LATITUDE_RADIANS[sample];
            if (CelestialMath.solarElevationAt(latitude, solarTime) > 0.0D) {
                maximum = Math.max(maximum, prepared.coverageAtPrepared(latitude));
            }
        }
        return maximum;
    }

    private static LunarPrediction nextLunar(double nowDay, int daysInMonth,
                                             CelestialMath.SolarLatitudeContext observerLatitude,
                                             double synodicDays, double anomalisticDays,
                                             CelestialRuntimeSettings settings,
                                             double sineLunarInclination) {
        long firstIndex = (long) Math.floor(nowDay / synodicDays);
        for (long index = firstIndex; index < firstIndex + SEARCH_CONJUNCTIONS; index++) {
            LunarPrediction candidate = lunarAt(index, daysInMonth, observerLatitude,
                    synodicDays, anomalisticDays, settings, sineLunarInclination);
            if (candidate.present() && candidate.eclipse()
                    && candidate.endCalendarTicks() + 1.0E-9D >= nowDay
                    * CelestialMath.TICKS_IN_DAY) {
                return candidate;
            }
        }
        return LunarPrediction.NONE;
    }

    private static LunarPrediction lunarAt(long index, int daysInMonth,
                                           CelestialMath.SolarLatitudeContext observerLatitude,
                                           double synodicDays, double anomalisticDays,
                                           CelestialRuntimeSettings settings,
                                           double sineLunarInclination) {
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
        LunarEclipseState maximumUmbraState = null;
        LunarEclipseState maximumPenumbraState = null;
        for (int sample = 0; sample <= LUNAR_TIME_SAMPLES; sample++) {
            double day = fullMoonDay - scanHalfDays
                    + 2.0D * scanHalfDays * sample / LUNAR_TIME_SAMPLES;
            CelestialMath.LunarPredictionSample sampleState =
                    CelestialMath.lunarPredictionSampleAt(observerLatitude,
                    day * CelestialMath.TICKS_IN_DAY, daysInMonth, synodicDays,
                    anomalisticDays, settings.nodalYears(), settings.lunarInclinationRadians(),
                    settings.moonScale(), sineLunarInclination);
            LunarEclipseState eclipseState = sampleState.lunarEclipseRegion();
            double penumbraCoverage = eclipseState.penumbraCoverage();
            boolean eclipseContact = penumbraCoverage > 0.0D;
            boolean supermoonContact = sampleState.supermoon()
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
            if (eclipseContact && eclipseState.umbraCoverage() > maximumUmbra) {
                maximumUmbra = eclipseState.umbraCoverage();
                maximumUmbraDay = day;
                maximumUmbraState = eclipseState;
            }
            if (eclipseContact && penumbraCoverage > maximumPenumbra) {
                maximumPenumbra = penumbraCoverage;
                maximumPenumbraDay = day;
                maximumPenumbraState = eclipseState;
            }
            if (supermoonContact) {
                maximumSupermoon = Math.max(maximumSupermoon, sampleState.supermoon());
                double fullMoonDistance = Math.abs(day - fullMoonDay);
                if (fullMoonDistance < bestFullMoonDistance) {
                    bestFullMoonDistance = fullMoonDistance;
                    supermoonDay = day;
                }
            }
        }
        boolean eclipse = maximumPenumbra > 0.0D && maximumPenumbraState != null;
        boolean supermoon = maximumSupermoon >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
        double firstContact = eclipse ? firstEclipseContact : firstSupermoonContact;
        double lastContact = eclipse ? lastEclipseContact : lastSupermoonContact;
        if ((!eclipse && !supermoon) || !Double.isFinite(firstContact)
                || !Double.isFinite(lastContact)) {
            return LunarPrediction.NONE;
        }
        double scanStep = 2.0D * scanHalfDays / LUNAR_TIME_SAMPLES;
        firstContact = refineLunarPredictionContact(firstContact - scanStep, firstContact, true, eclipse,
                daysInMonth, observerLatitude, synodicDays, anomalisticDays, settings,
                sineLunarInclination);
        lastContact = refineLunarPredictionContact(lastContact, lastContact + scanStep, false, eclipse,
                daysInMonth, observerLatitude, synodicDays, anomalisticDays, settings,
                sineLunarInclination);
        LunarEclipseState greatestEclipseState = maximumUmbraState != null
                ? maximumUmbraState : maximumPenumbraState;
        double greatestEclipseDay = maximumUmbraState != null
                ? maximumUmbraDay : maximumPenumbraDay;
        CelestialDiscGeometry.AlignedSquare shadow = CelestialDiscGeometry.AlignedSquare.NONE;
        if (eclipse) {
            shadow = new CelestialDiscGeometry.AlignedSquare(greatestEclipseState.shadowCenterX(),
                    greatestEclipseState.shadowCenterY(), greatestEclipseState.shadowRadius(), true);
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
                                                       int daysInMonth,
                                                       CelestialMath.SolarLatitudeContext observerLatitude,
                                                       double synodicDays,
                                                       double anomalisticDays,
                                                       CelestialRuntimeSettings settings,
                                                       double sineLunarInclination) {
        for (int iteration = 0; iteration < SOLAR_REFINEMENT_ITERATIONS; iteration++) {
            double middle = (low + high) * 0.5D;
            CelestialMath.LunarPredictionSample sample =
                    CelestialMath.lunarPredictionSampleAt(observerLatitude,
                    middle * CelestialMath.TICKS_IN_DAY, daysInMonth, synodicDays,
                    anomalisticDays, settings.nodalYears(), settings.lunarInclinationRadians(),
                    settings.moonScale(), sineLunarInclination);
            boolean active = eclipse
                    ? sample.lunarEclipseRegion().penumbraCoverage() > 0.0D
                    : sample.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD;
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
                                                 int daysInMonth,
                                                 CelestialMath.SolarLatitudeContext observerLatitude,
                                                 double synodicDays,
                                                 CelestialRuntimeSettings settings,
                                                 double sineLunarInclination) {
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
            double illumination = CelestialMath.illuminatedFractionAt(observerLatitude,
                    day * CelestialMath.TICKS_IN_DAY, daysInMonth,
                    synodicDays, settings.nodalYears(), settings.lunarInclinationRadians(),
                    sineLunarInclination);
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
