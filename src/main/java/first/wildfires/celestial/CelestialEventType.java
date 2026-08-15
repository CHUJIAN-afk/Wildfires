package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialEventRules.RainSample;

/** Natural local sky events that the TFC calendar debug command can wait for. */
public enum CelestialEventType {
    SUNRISE("sunrise"),
    SUNSET("sunset"),
    MOONRISE("moonrise"),
    MOONSET("moonset"),
    NOON("noon"),
    MIDNIGHT("midnight"),
    FULL_MOON("full_moon"),
    NEW_MOON("new_moon"),
    FIRST_QUARTER("first_quarter"),
    LAST_QUARTER("last_quarter"),
    SUPERMOON("supermoon"),
    SOLAR_ECLIPSE("solar_eclipse"),
    LUNAR_ECLIPSE("lunar_eclipse"),
    BLOOD_MOON("blood_moon"),
    AURORA("aurora"),
    RAINBOW("rainbow");

    private static final double CLOCK_WINDOW = 120.0D / CelestialMath.TICKS_IN_DAY;
    private final String commandName;

    CelestialEventType(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public String translationKey() {
        return "commands.wildfires.tfctime.event." + commandName;
    }

    public boolean matches(CelestialMath.Result result, long calendarTicks, RainSample rain) {
        return matches((CelestialMath.EventView) result, calendarTicks, rain);
    }

    boolean matches(CelestialMath.EventSample result, long calendarTicks, RainSample rain) {
        return matches((CelestialMath.EventView) result, calendarTicks, rain);
    }

    /** Exact predicate subset for events that read only the local Sun and optional rain sample. */
    boolean matches(CelestialMath.DaylightSample result, double latitude,
                    long calendarTicks, RainSample rain) {
        return switch (this) {
            case SUNRISE -> result.solarElevation() > 0.0D;
            case SUNSET -> result.solarElevation() <= 0.0D;
            case AURORA -> {
                long eventKey = CelestialEventRules.auroraEventKey(calendarTicks, latitude);
                yield CelestialEventRules.auroraVisible(false, false, 1, latitude,
                        result.solarElevation(), CelestialEventRules.auroraRoll(eventKey));
            }
            case RAINBOW -> rain != null && CelestialEventRules.startsRainbow(
                    rain.before(), rain.current(), rain.after(), result.apparentDayTime(),
                    result.solarElevation());
            default -> throw new IllegalArgumentException(
                    "Event does not use a daylight-only sample: " + this);
        };
    }

    /** Exact predicate subset for events that do not read phase cells or apparent solar time. */
    boolean matches(CelestialMath.DisplayEventSample result) {
        return switch (this) {
            case MOONRISE -> result.moonElevation() > 0.0D;
            case MOONSET -> result.moonElevation() <= 0.0D;
            case FULL_MOON -> result.illuminatedFraction() >= 0.995D
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            case NEW_MOON -> result.illuminatedFraction() <= 0.005D
                    && result.moonElevation() > 0.0D && result.solarElevation() > 0.0D;
            case SUPERMOON -> result.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            case SOLAR_ECLIPSE -> visibleEclipseContact(
                    result.solarEclipse(), result.solarElevation());
            case LUNAR_ECLIPSE -> result.lunarPenumbraCoverage() > 0.0D
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            case BLOOD_MOON -> result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            default -> throw new IllegalArgumentException(
                    "Event does not use a display-event sample: " + this);
        };
    }

    /** Exact predicate subset for the two quarter-phase events. */
    boolean matches(CelestialMath.QuarterEventSample result) {
        return switch (this) {
            case FIRST_QUARTER -> result.moonPhase() == 2
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            case LAST_QUARTER -> result.moonPhase() == 6
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && localLunarNight(result.moonElevation(), result.solarElevation());
            default -> throw new IllegalArgumentException(
                    "Event does not use a quarter-phase sample: " + this);
        };
    }

    /** Exact clock-window predicate without constructing any celestial state. */
    boolean matchesFractionOfDay(double fractionOfDay) {
        return switch (this) {
            case NOON -> circularDistance(fractionOfDay, 0.5D) <= CLOCK_WINDOW;
            case MIDNIGHT -> circularDistance(fractionOfDay, 0.0D) <= CLOCK_WINDOW;
            default -> throw new IllegalArgumentException(
                    "Event does not use only fraction-of-day: " + this);
        };
    }

    boolean matches(CelestialMath.EventView result, long calendarTicks, RainSample rain) {
        return switch (this) {
            case SUNRISE -> result.solarElevation() > 0.0D;
            case SUNSET -> result.solarElevation() <= 0.0D;
            case MOONRISE -> result.moonElevation() > 0.0D;
            case MOONSET -> result.moonElevation() <= 0.0D;
            case NOON -> circularDistance(result.fractionOfDay(), 0.5D) <= CLOCK_WINDOW;
            case MIDNIGHT -> circularDistance(result.fractionOfDay(), 0.0D) <= CLOCK_WINDOW;
            case FULL_MOON -> result.illuminatedFraction() >= 0.995D && localLunarNight(result);
            case NEW_MOON -> result.illuminatedFraction() <= 0.005D
                    && result.moonElevation() > 0.0D && result.solarElevation() > 0.0D;
            case FIRST_QUARTER -> result.moonPhase() == 2
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && localLunarNight(result);
            case LAST_QUARTER -> result.moonPhase() == 6
                    && Math.abs(result.illuminatedFraction() - 0.5D) <= 0.03D
                    && localLunarNight(result);
            case SUPERMOON -> result.supermoon() >= CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                    && localLunarNight(result);
            case SOLAR_ECLIPSE -> visibleEclipseContact(result.solarEclipse(), result.solarElevation());
            case LUNAR_ECLIPSE -> result.lunarEclipseRegion().active() && localLunarNight(result);
            case BLOOD_MOON -> result.bloodMoon() > CelestialGameplayRules.ACTIVE_THRESHOLD
                    && localLunarNight(result);
            case AURORA -> {
                long eventKey = CelestialEventRules.auroraEventKey(calendarTicks, result.latitude());
                yield CelestialEventRules.auroraVisible(false, false, 1, result.latitude(),
                        result.solarElevation(), CelestialEventRules.auroraRoll(eventKey));
            }
            case RAINBOW -> rain != null && CelestialEventRules.startsRainbow(
                    rain.before(), rain.current(), rain.after(), result.apparentDayTime(),
                    result.solarElevation());
        };
    }

    private static double circularDistance(double first, double second) {
        double distance = Math.abs(first - second) % 1.0D;
        return Math.min(distance, 1.0D - distance);
    }

    /** Exact geometric contact window: no artistic or gameplay-strength threshold is applied. */
    static boolean visibleEclipseContact(double coverage, double elevation) {
        return Double.isFinite(coverage) && coverage > 0.0D
                && Double.isFinite(elevation) && elevation > 0.0D;
    }

    private static boolean localLunarNight(CelestialMath.EventView result) {
        return localLunarNight(result.moonElevation(), result.solarElevation());
    }

    private static boolean localLunarNight(double moonElevation, double solarElevation) {
        return Double.isFinite(moonElevation) && moonElevation > 0.0D
                && Double.isFinite(solarElevation) && solarElevation <= 0.0D;
    }
}
