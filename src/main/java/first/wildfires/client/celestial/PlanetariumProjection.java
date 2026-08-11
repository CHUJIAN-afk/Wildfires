package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialEventType;
import first.wildfires.celestial.EclipsePredictionService.SolarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Timeline;
import first.wildfires.celestial.EclipsePredictionService.LunarPrediction;
import first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind;
import first.wildfires.celestial.EclipsePredictionService.LunarPhasePrediction;
import first.wildfires.celestial.SolarEclipseRegion;
import java.util.ArrayList;
import java.util.List;

/** Horizon polar projection and eclipse helpers used by the in-game planetarium screen. */
final class PlanetariumProjection {

    static final int TIMELINE_ICON_SIZE = 8;
    static final int TIMELINE_DISC_SOURCE_SIZE = 8;
    static final int TIMELINE_DISC_SOURCE_U = 12;
    static final int TIMELINE_DISC_SOURCE_V = 12;
    static final int TIMELINE_NEW_MOON_SOURCE_V = 44;
    static final int TIMELINE_POINTER_LENGTH = 16;
    static final int TIMELINE_POINTER_WIDTH = 1;
    static final int TIMELINE_POINTER_COLOR = 0xFF2D8FB8;
    static final int TIMELINE_TRACK_GAP = 2;
    static final int TIMELINE_LABEL_Y = 97;
    static final int POINTER_TEXTURE_SIZE = 128;
    static final int MAP_SELECTION_CURSOR_RADIUS = 6;
    static final int MAP_SELECTION_HOVER_RADIUS = 7;
    static final int MAP_PLAYER_CURSOR_RADIUS = 3;
    static final int MAP_SELECTION_COLOR = 0xFFFFD85A;
    static final int MAP_PLAYER_CURSOR_COLOR = 0xFF62E7FF;
    static final String TIMELINE_SUN_TEXTURE = "minecraft:textures/environment/sun.png";
    static final String TIMELINE_FULL_MOON_TEXTURE =
            "minecraft:textures/environment/moon_phases.png";

    private PlanetariumProjection() {
    }

    static Point project(CelestialVector direction, double centerX, double centerY, double radius) {
        if (direction == null || !Double.isFinite(radius) || radius <= 0.0D) {
            return Point.HIDDEN;
        }
        CelestialVector unit = direction.normalized();
        if (unit.lengthSquared() < 1.0E-12D || !Double.isFinite(unit.x())
                || !Double.isFinite(unit.y()) || !Double.isFinite(unit.z())) {
            return Point.HIDDEN;
        }
        double altitude = Math.asin(clamp(unit.y(), -1.0D, 1.0D));
        if (altitude < 0.0D) {
            return Point.HIDDEN;
        }
        double horizontal = Math.hypot(unit.x(), unit.z());
        double radial = (Math.PI * 0.5D - altitude) / (Math.PI * 0.5D) * radius;
        double east = horizontal > 1.0E-12D ? unit.x() / horizontal : 0.0D;
        double north = horizontal > 1.0E-12D ? unit.z() / horizontal : 1.0D;
        return new Point(centerX + east * radial, centerY - north * radial, true);
    }

    static CelestialVector starDirection(StarTableLoader.Star star, CelestialState state) {
        double cos = Math.cos(star.declination());
        CelestialVector equatorial = new CelestialVector(cos * Math.cos(star.ascension()),
                cos * Math.sin(star.ascension()), Math.sin(star.declination()));
        return CelestialMath.equatorialToHorizon(equatorial, state.latitudeRadians(),
                localSiderealAngle(state));
    }

    static double localSiderealAngle(CelestialState state) {
        CelestialVector sun = state.sun().geocentricPosition().normalized();
        double rightAscension = Math.atan2(sun.y(), sun.x());
        return rightAscension + CelestialMath.TAU * (state.fractionOfDay() - 0.5D);
    }

    static double eclipseCoverage(SolarPrediction prediction, double timeProgress,
                                  double latitudeRadians) {
        if (prediction == null || !prediction.present() || !Double.isFinite(timeProgress)
                || !Double.isFinite(latitudeRadians)) {
            return 0.0D;
        }
        double progress = clamp(timeProgress, 0.0D, 1.0D);
        double calendarTicks = prediction.startCalendarTicks()
                + (prediction.endCalendarTicks() - prediction.startCalendarTicks()) * progress;
        if (CelestialMath.solarElevationAt(latitudeRadians,
                calendarTicks / CelestialMath.TICKS_IN_DAY, prediction.daysInYear()) <= 0.0D) {
            return 0.0D;
        }
        return SolarEclipseRegion.coverageAt(prediction.event(),
                calendarTicks / CelestialMath.TICKS_IN_DAY, latitudeRadians,
                prediction.sunHalfTangent(), prediction.moonHalfTangent(), prediction.synodicDays());
    }

    static double eclipseLatitudeHalfWidth(SolarPrediction prediction, double timeProgress,
                                           double coverageThreshold) {
        if (prediction == null || !prediction.present() || !Double.isFinite(timeProgress)) {
            return 0.0D;
        }
        double progress = clamp(timeProgress, 0.0D, 1.0D);
        double calendarTicks = prediction.startCalendarTicks()
                + (prediction.endCalendarTicks() - prediction.startCalendarTicks()) * progress;
        return SolarEclipseRegion.latitudeHalfWidthAt(prediction.event(),
                calendarTicks / CelestialMath.TICKS_IN_DAY, prediction.sunHalfTangent(),
                prediction.moonHalfTangent(), prediction.synodicDays(), coverageThreshold);
    }

    /** Maps positive eclipse coverage into the five paper-chart bands requested by the UI. */
    static int eclipseCoverageBand(double coverage) {
        if (!Double.isFinite(coverage) || coverage <= 0.0D) {
            return -1;
        }
        return Math.min(4, (int) Math.floor(clamp(coverage, 0.0D, 1.0D) * 5.0D));
    }

    static double timelineProgress(Timeline timeline, double calendarTicks) {
        if (timeline == null || !Double.isFinite(calendarTicks)) {
            return 0.0D;
        }
        double span = timeline.endCalendarTicks() - timeline.startCalendarTicks();
        if (!(span > 0.0D) || !Double.isFinite(span)) {
            return 0.0D;
        }
        return clamp((calendarTicks - timeline.startCalendarTicks()) / span, 0.0D, 1.0D);
    }

    static SolarPrediction selectSolar(Timeline timeline, long conjunctionIndex) {
        if (timeline == null) {
            return SolarPrediction.NONE;
        }
        return timeline.solar().stream()
                .filter(candidate -> candidate.conjunctionIndex() == conjunctionIndex)
                .findFirst().orElse(SolarPrediction.NONE);
    }

    static LunarPrediction selectLunar(Timeline timeline, long fullMoonIndex) {
        if (timeline == null) {
            return LunarPrediction.NONE;
        }
        return timeline.lunar().stream()
                .filter(candidate -> candidate.fullMoonIndex() == fullMoonIndex)
                .findFirst().orElse(LunarPrediction.NONE);
    }

    static LunarPhasePrediction selectPhase(Timeline timeline, long phaseIndex,
                                             LunarPhaseKind kind) {
        if (timeline == null || kind == null) {
            return LunarPhasePrediction.NONE;
        }
        return timeline.phases().stream()
                .filter(candidate -> candidate.phaseIndex() == phaseIndex
                        && candidate.kind() == kind)
                .findFirst().orElse(LunarPhasePrediction.NONE);
    }

    static double maximumGlobalSolarCoverage(SolarPrediction prediction, double latitudeRadians) {
        return first.wildfires.celestial.EclipsePredictionService
                .maximumGlobalSolarCoverageAtLatitude(prediction, latitudeRadians);
    }

    /** Converts a click inside the paper map into its display-only longitude and TFE latitude. */
    static MapSelection mapSelection(double mouseX, double mouseY, int chartX, int chartY,
                                     int chartWidth, int chartHeight) {
        if (!validMapPoint(mouseX, mouseY, chartWidth, chartHeight)
                || mouseX < chartX || mouseX > chartX + chartWidth
                || mouseY < chartY || mouseY > chartY + chartHeight) {
            return MapSelection.NONE;
        }
        return clampedMapSelection(mouseX, mouseY, chartX, chartY, chartWidth, chartHeight);
    }

    /** Keeps an active drag on the paper map even when the mouse passes beyond one of its edges. */
    static MapSelection clampedMapSelection(double mouseX, double mouseY, int chartX, int chartY,
                                            int chartWidth, int chartHeight) {
        if (!validMapPoint(mouseX, mouseY, chartWidth, chartHeight)) {
            return MapSelection.NONE;
        }
        double horizontal = clamp((mouseX - chartX) / chartWidth, 0.0D, 1.0D);
        double vertical = clamp((mouseY - chartY) / chartHeight, 0.0D, 1.0D);
        return new MapSelection(horizontal * CelestialMath.TAU - Math.PI,
                Math.PI * 0.5D - vertical * Math.PI, true);
    }

    private static boolean validMapPoint(double mouseX, double mouseY,
                                         int chartWidth, int chartHeight) {
        return Double.isFinite(mouseX) && Double.isFinite(mouseY)
                && chartWidth > 0 && chartHeight > 0;
    }

    static int mapX(double longitudeRadians, int chartX, int chartWidth) {
        return chartX + (int) Math.round(clamp((longitudeRadians + Math.PI)
                / CelestialMath.TAU, 0.0D, 1.0D) * chartWidth);
    }

    static int mapY(double latitudeRadians, int chartY, int chartHeight) {
        return chartY + (int) Math.round(clamp((Math.PI * 0.5D - latitudeRadians)
                / Math.PI, 0.0D, 1.0D) * chartHeight);
    }

    static boolean crosshairContains(MapSelection selection, double mouseX, double mouseY,
                                     int chartX, int chartY, int chartWidth, int chartHeight,
                                     int hoverRadius) {
        if (selection == null || !selection.present() || hoverRadius < 0) {
            return false;
        }
        int centerX = mapX(selection.longitudeRadians(), chartX, chartWidth);
        int centerY = mapY(selection.latitudeRadians(), chartY, chartHeight);
        return Math.abs(mouseX - centerX) <= hoverRadius
                && Math.abs(mouseY - centerY) <= hoverRadius;
    }

    static List<TimelineLunarMarkerKind> timelineLunarMarkerKinds(LunarPrediction lunar) {
        if (lunar == null || !lunar.present()) {
            return List.of();
        }
        List<TimelineLunarMarkerKind> kinds = new ArrayList<>(2);
        if (lunar.eclipse()) {
            kinds.add(TimelineLunarMarkerKind.ECLIPSE);
        }
        if (lunar.supermoon()) {
            kinds.add(TimelineLunarMarkerKind.SUPERMOON);
        }
        return List.copyOf(kinds);
    }

    /** Maps the public API snapshot to UI rows; blood moon replaces the lunar-eclipse row. */
    static List<CelestialEventType> currentEventTypes(CelestialEventState events) {
        if (events == null) {
            return List.of();
        }
        List<CelestialEventType> active = new ArrayList<>(5);
        if (events.solarEclipseVisible()) {
            active.add(CelestialEventType.SOLAR_ECLIPSE);
        }
        if (events.newMoon()) {
            active.add(CelestialEventType.NEW_MOON);
        }
        if (events.fullMoon()) {
            active.add(CelestialEventType.FULL_MOON);
        }
        if (events.bloodMoonVisible()) {
            active.add(CelestialEventType.BLOOD_MOON);
        } else if (events.lunarEclipseVisible()) {
            active.add(CelestialEventType.LUNAR_ECLIPSE);
        }
        if (events.supermoonVisible()) {
            active.add(CelestialEventType.SUPERMOON);
        }
        return List.copyOf(active);
    }

    /**
     * Expands each full-Moon prediction into its independently selectable event markers before
     * assigning horizontal tracks. A simultaneous eclipse and supermoon therefore cannot collapse
     * into one icon merely because both share the same prediction and timeline pixel.
     */
    static List<TimelineLunarMarker> timelineLunarMarkers(List<LunarPrediction> predictions) {
        if (predictions == null) {
            throw new IllegalArgumentException("Lunar predictions cannot be null");
        }
        List<LunarPrediction> expandedPredictions = new ArrayList<>();
        List<TimelineLunarMarkerKind> expandedKinds = new ArrayList<>();
        for (LunarPrediction prediction : predictions) {
            for (TimelineLunarMarkerKind kind : timelineLunarMarkerKinds(prediction)) {
                expandedPredictions.add(prediction);
                expandedKinds.add(kind);
            }
        }
        List<TimelineLunarMarker> markers = new ArrayList<>(expandedPredictions.size());
        for (int index = 0; index < expandedPredictions.size(); index++) {
            markers.add(new TimelineLunarMarker(expandedPredictions.get(index),
                    expandedKinds.get(index)));
        }
        return List.copyOf(markers);
    }

    static TimelineMoonTint timelineMoonTint(LunarPrediction lunar,
                                             TimelineLunarMarkerKind markerKind) {
        if (lunar == null || !lunar.present()) {
            return new TimelineMoonTint(1.0F, 1.0F, 1.0F);
        }
        if (markerKind == TimelineLunarMarkerKind.SUPERMOON) {
            return new TimelineMoonTint(0.43F, 0.70F, 1.0F);
        }
        if (markerKind == TimelineLunarMarkerKind.ECLIPSE) {
            return lunar.bloodMoon()
                    ? new TimelineMoonTint(0.56F, 0.08F, 0.045F)
                    : new TimelineMoonTint(0.88F, 0.47F, 0.43F);
        }
        return new TimelineMoonTint(1.0F, 1.0F, 1.0F);
    }

    /**
     * Packs exceptional-event TFC-day groups while keeping one shared pointer for every such day.
     * Icons within each upper/lower row are centered around that pointer; neighboring day groups
     * are shifted as little as possible to keep their widest rows from overlapping. Ordinary full
     * and new Moon phase markers do not enter these groups; they use {@link #timelineAxisMarker}.
     */
    static List<TimelineDayLayout> timelineDayLayouts(List<TimelineDaySeed> seeds,
                                                       int markerSize, int markerGap,
                                                       int minimumLeft, int maximumRight) {
        if (seeds == null || seeds.isEmpty()) {
            return List.of();
        }
        if (markerSize <= 0 || markerGap < 0 || maximumRight - minimumLeft < markerSize) {
            throw new IllegalArgumentException("Invalid timeline day-group bounds");
        }
        int count = seeds.size();
        int[] halfExtents = new int[count];
        int[] offsets = new int[count];
        double minimumBase = Double.NEGATIVE_INFINITY;
        double maximumBase = Double.POSITIVE_INFINITY;
        long previousDay = Long.MIN_VALUE;
        int previousCenter = Integer.MIN_VALUE;
        for (int index = 0; index < count; index++) {
            TimelineDaySeed seed = seeds.get(index);
            if (seed == null || seed.upperCount() < 0 || seed.lowerCount() < 0
                    || seed.upperCount() + seed.lowerCount() == 0) {
                throw new IllegalArgumentException("Timeline day group must contain an event");
            }
            if (seed.day() <= previousDay || seed.desiredCenter() < previousCenter) {
                throw new IllegalArgumentException("Timeline day groups must be time ordered");
            }
            int width = Math.max(rowWidth(seed.upperCount(), markerSize, markerGap),
                    rowWidth(seed.lowerCount(), markerSize, markerGap));
            halfExtents[index] = width / 2;
            if (index > 0) {
                offsets[index] = offsets[index - 1] + halfExtents[index - 1]
                        + markerGap + halfExtents[index];
            }
            minimumBase = Math.max(minimumBase,
                    minimumLeft + halfExtents[index] - offsets[index]);
            maximumBase = Math.min(maximumBase,
                    maximumRight - halfExtents[index] - offsets[index]);
            previousDay = seed.day();
            previousCenter = seed.desiredCenter();
        }
        if (minimumBase > maximumBase) {
            throw new IllegalArgumentException("Timeline is too narrow for distinct day groups");
        }

        List<IsotonicBlock> blocks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            blocks.add(new IsotonicBlock(index, index,
                    seeds.get(index).desiredCenter() - offsets[index], 1));
            while (blocks.size() >= 2) {
                IsotonicBlock right = blocks.get(blocks.size() - 1);
                IsotonicBlock left = blocks.get(blocks.size() - 2);
                if (left.mean() <= right.mean()) {
                    break;
                }
                blocks.remove(blocks.size() - 1);
                blocks.set(blocks.size() - 1, left.merge(right));
            }
        }
        double[] fitted = new double[count];
        for (IsotonicBlock block : blocks) {
            for (int index = block.firstIndex(); index <= block.lastIndex(); index++) {
                fitted[index] = block.mean();
            }
        }

        List<TimelineDayLayout> layouts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            TimelineDaySeed seed = seeds.get(index);
            double base = clamp(fitted[index], minimumBase, maximumBase);
            int pointerX = (int) Math.round(base + offsets[index]);
            layouts.add(new TimelineDayLayout(seed.day(), pointerX,
                    rowLefts(pointerX, seed.upperCount(), markerSize, markerGap),
                    rowLefts(pointerX, seed.lowerCount(), markerSize, markerGap)));
        }
        return List.copyOf(layouts);
    }

    /** Centers an ordinary phase marker directly on the timeline axis without a pointer. */
    static TimelineAxisMarker timelineAxisMarker(int centerX, int axisY, int markerSize) {
        if (markerSize <= 0) {
            throw new IllegalArgumentException("Timeline axis marker size must be positive");
        }
        return new TimelineAxisMarker(centerX - markerSize / 2, axisY - markerSize / 2);
    }

    private static int rowWidth(int count, int markerSize, int markerGap) {
        return count <= 0 ? 0 : markerSize * count + markerGap * (count - 1);
    }

    private static List<Integer> rowLefts(int center, int count, int markerSize, int markerGap) {
        if (count <= 0) {
            return List.of();
        }
        int width = rowWidth(count, markerSize, markerGap);
        int left = center - width / 2;
        List<Integer> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(left + index * (markerSize + markerGap));
        }
        return List.copyOf(result);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record Point(double x, double y, boolean visible) {
        static final Point HIDDEN = new Point(0.0D, 0.0D, false);
    }

    enum TimelineLunarMarkerKind {
        ECLIPSE,
        SUPERMOON
    }

    record TimelineLunarMarker(LunarPrediction prediction, TimelineLunarMarkerKind kind) {}

    record TimelineMoonTint(float red, float green, float blue) {}

    record TimelineDaySeed(long day, int desiredCenter, int upperCount, int lowerCount) {}

    record TimelineDayLayout(long day, int pointerX, List<Integer> upperLefts,
                             List<Integer> lowerLefts) {}

    record TimelineAxisMarker(int left, int top) {}

    record MapSelection(double longitudeRadians, double latitudeRadians, boolean present) {
        static final MapSelection NONE = new MapSelection(0.0D, 0.0D, false);
    }

    /** Pure click/drag lifecycle used by the scaled planetarium screen and client tests. */
    record MapDragState(MapSelection selection, boolean dragging) {
        static final MapDragState NONE = new MapDragState(MapSelection.NONE, false);

        MapDragState {
            selection = selection == null ? MapSelection.NONE : selection;
        }

        MapDragState begin(double mouseX, double mouseY, int chartX, int chartY,
                           int chartWidth, int chartHeight) {
            MapSelection clicked = mapSelection(mouseX, mouseY, chartX, chartY,
                    chartWidth, chartHeight);
            return clicked.present() ? new MapDragState(clicked, true)
                    : new MapDragState(selection, false);
        }

        MapDragState drag(double mouseX, double mouseY, int chartX, int chartY,
                          int chartWidth, int chartHeight) {
            if (!dragging) {
                return this;
            }
            MapSelection dragged = clampedMapSelection(mouseX, mouseY, chartX, chartY,
                    chartWidth, chartHeight);
            return dragged.present() ? new MapDragState(dragged, true) : this;
        }

        MapDragState release() {
            return dragging ? new MapDragState(selection, false) : this;
        }
    }

    private record IsotonicBlock(int firstIndex, int lastIndex, double total, int weight) {
        double mean() {
            return total / weight;
        }

        IsotonicBlock merge(IsotonicBlock other) {
            return new IsotonicBlock(firstIndex, other.lastIndex, total + other.total,
                    weight + other.weight);
        }
    }
}
