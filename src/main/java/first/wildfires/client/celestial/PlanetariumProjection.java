package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialEventType;
import first.wildfires.celestial.EclipsePredictionService.SolarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Timeline;
import first.wildfires.celestial.EclipsePredictionService.LunarPrediction;
import first.wildfires.celestial.SolarEclipseRegion;
import java.util.ArrayList;
import java.util.List;

/** Horizon polar projection and eclipse helpers used by the in-game planetarium screen. */
final class PlanetariumProjection {

    private static final int CURRENT_SOLAR_ECLIPSE_BIT = 1 << 0;
    private static final int CURRENT_NEW_MOON_BIT = 1 << 1;
    private static final int CURRENT_FULL_MOON_BIT = 1 << 2;
    private static final int CURRENT_LUNAR_ECLIPSE_BIT = 1 << 3;
    private static final int CURRENT_BLOOD_MOON_BIT = 1 << 4;
    private static final int CURRENT_SUPERMOON_BIT = 1 << 5;

    static final int TIMELINE_ICON_SIZE = 4;
    static final int TIMELINE_DISC_SOURCE_SIZE = 8;
    static final int TIMELINE_DISC_SOURCE_U = 12;
    static final int TIMELINE_DISC_SOURCE_V = 12;
    static final int TIMELINE_NEW_MOON_SOURCE_V = 44;
    static final int TIMELINE_POINTER_LENGTH = 5;
    static final int TIMELINE_POINTER_WIDTH = 1;
    static final int TIMELINE_POINTER_COLOR = 0xFF2D8FB8;
    static final int TIMELINE_SELECTED_COLOR = 0xFF62E7FF;
    static final int TIMELINE_TRACK_GAP = 1;
    static final int TIMELINE_LABEL_Y = 37;
    static final int TIMELINE_LABEL_COLOR = 0xFFFFFF55;
    static final int INFO_TITLE_COLOR = 0xFFFFFFFF;
    static final int INFO_PRIMARY_COLOR = 0xFFE6F4FF;
    static final int INFO_ACCENT_COLOR = 0xFF62E7FF;
    static final int INFO_SECONDARY_COLOR = 0xFFB8C7E8;
    static final int INFO_SOLAR_COLOR = 0xFFFF8FA8;
    static final int INFO_LUNAR_COLOR = 0xFFE2B5FF;
    static final int INFO_SUPERMOON_COLOR = 0xFF8FC7FF;
    static final int INFO_BOX_WIDTH = 140;
    static final int INFO_BOX_HEIGHT = 180;
    static final int INFO_LINE_HEIGHT = 9;
    static final int INFO_GROUP_GAP = 1;
    static final int CLOCK_TEXTURE_X = 148;
    static final int CLOCK_TEXTURE_Y = 39;
    static final int CLOCK_TEXTURE_WIDTH = 96;
    static final int CLOCK_TEXTURE_HEIGHT = 86;
    static final int POINTER_TEXTURE_WIDTH = 96;
    static final int POINTER_TEXTURE_HEIGHT = 86;
    static final double CLOCK_CENTER_SOURCE_X = 47.5D;
    static final double CLOCK_CENTER_SOURCE_Y = 42.0D;
    static final double CLOCK_RADIUS_X = 38.0D;
    static final double CLOCK_RADIUS_Y = 30.0D;
    static final double POINTER_PIVOT_X = 47.5D;
    static final double POINTER_PIVOT_Y = 44.0D;
    static final double POINTER_SHADOW_PIVOT_X = 47.5D;
    static final double POINTER_SHADOW_PIVOT_Y = 46.0D;
    static final double POINTER_SHADOW_OFFSET_Y = 1.0D;
    static final int MAP_SELECTION_CURSOR_RADIUS = 2;
    static final int MAP_SELECTION_HOVER_RADIUS = 3;
    static final int MAP_PLAYER_CURSOR_RADIUS = 1;
    static final int MAP_SELECTION_COLOR = 0xFFFFD85A;
    static final int MAP_PLAYER_CURSOR_COLOR = 0xFF62E7FF;
    static final String TIMELINE_SUN_TEXTURE = "minecraft:textures/environment/sun.png";
    static final String TIMELINE_FULL_MOON_TEXTURE =
            "minecraft:textures/environment/moon_phases.png";

    private PlanetariumProjection() {
    }

    /** Returns a fresh authored layout for every newly opened planetarium screen. */
    static FloatingComponentLayout initialFloatingComponentLayout() {
        return new FloatingComponentLayout(0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** Exact logical-pixel rectangle shared by timeline rendering, selection and hit testing. */
    static PixelRect timelineIconBounds(int centerX, int centerY) {
        int left = centerX - TIMELINE_ICON_SIZE / 2;
        int top = centerY - TIMELINE_ICON_SIZE / 2;
        return new PixelRect(left, top, left + TIMELINE_ICON_SIZE,
                top + TIMELINE_ICON_SIZE);
    }

    static int timelineGroupCenter(List<Integer> upperLefts, List<Integer> lowerLefts,
                                   int markerSize) {
        List<Integer> row = upperLefts != null && !upperLefts.isEmpty()
                ? upperLefts : lowerLefts;
        if (row == null || row.isEmpty() || markerSize <= 0) {
            throw new IllegalArgumentException("Timeline group needs at least one marker");
        }
        int firstCenterTwice = row.get(0) * 2 + markerSize;
        int lastCenterTwice = row.get(row.size() - 1) * 2 + markerSize;
        return Math.floorDiv(firstCenterTwice + lastCenterTwice, 4);
    }

    static InputLayer topInputLayer(boolean eventMarker, boolean timelineBody,
                                    boolean clockBody, boolean mapBody) {
        if (eventMarker) {
            return InputLayer.EVENT_MARKER;
        }
        if (timelineBody) {
            return InputLayer.TIMELINE;
        }
        if (clockBody) {
            return InputLayer.CLOCK;
        }
        return mapBody ? InputLayer.MAP : InputLayer.NONE;
    }

    /** Maps a unit-clock fraction onto the actual elliptical dial used by the pixel asset. */
    static Point ellipsePoint(double fraction, double centerX, double centerY,
                              double radiusX, double radiusY) {
        if (!Double.isFinite(fraction) || !Double.isFinite(centerX)
                || !Double.isFinite(centerY) || !Double.isFinite(radiusX)
                || !Double.isFinite(radiusY) || radiusX <= 0.0D || radiusY <= 0.0D) {
            return Point.HIDDEN;
        }
        double angle = fraction * CelestialMath.TAU - Math.PI * 0.5D;
        return new Point(centerX + Math.cos(angle) * radiusX,
                centerY + Math.sin(angle) * radiusY, true);
    }

    /** Applies the same anisotropic ellipse mapping to every vertex of the pointer texture. */
    static Point ellipsePointerVertex(double sourceX, double sourceY, double fraction,
                                      double centerX, double centerY) {
        return ellipsePointerVertex(sourceX, sourceY, fraction, centerX, centerY,
                POINTER_PIVOT_X, POINTER_PIVOT_Y, 0.0D);
    }

    /** Normalizes the authored two-pixel shadow offset, then places it one logical pixel lower. */
    static Point ellipsePointerShadowVertex(double sourceX, double sourceY, double fraction,
                                            double centerX, double centerY) {
        return ellipsePointerVertex(sourceX, sourceY, fraction, centerX, centerY,
                POINTER_SHADOW_PIVOT_X, POINTER_SHADOW_PIVOT_Y, POINTER_SHADOW_OFFSET_Y);
    }

    private static Point ellipsePointerVertex(double sourceX, double sourceY, double fraction,
                                               double centerX, double centerY,
                                               double pivotX, double pivotY,
                                               double screenOffsetY) {
        if (!Double.isFinite(sourceX) || !Double.isFinite(sourceY)) {
            return Point.HIDDEN;
        }
        double localX = sourceX - pivotX;
        double localY = sourceY - pivotY;
        double angle = fraction * CelestialMath.TAU - Math.PI * 0.5D;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double rotatedX = cosine * localX - sine * localY;
        double rotatedY = sine * localX + cosine * localY;
        return new Point(centerX + rotatedX,
                centerY + rotatedY * CLOCK_RADIUS_Y / CLOCK_RADIUS_X + screenOffsetY, true);
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
        return currentEventTypes(currentEventMask(events));
    }

    /** Allocation-free snapshot used by the screen's mandatory per-tick public-API check. */
    static int currentEventMask(CelestialEventState events) {
        if (events == null) {
            return 0;
        }
        int mask = 0;
        if (events.solarEclipseVisible()) {
            mask |= CURRENT_SOLAR_ECLIPSE_BIT;
        }
        if (events.newMoon()) {
            mask |= CURRENT_NEW_MOON_BIT;
        }
        if (events.fullMoon()) {
            mask |= CURRENT_FULL_MOON_BIT;
        }
        if (events.bloodMoonVisible()) {
            mask |= CURRENT_BLOOD_MOON_BIT;
        } else if (events.lunarEclipseVisible()) {
            mask |= CURRENT_LUNAR_ECLIPSE_BIT;
        }
        if (events.supermoonVisible()) {
            mask |= CURRENT_SUPERMOON_BIT;
        }
        return mask;
    }

    static List<CelestialEventType> currentEventTypes(int mask) {
        if (mask == 0) {
            return List.of();
        }
        List<CelestialEventType> active = new ArrayList<>(5);
        if ((mask & CURRENT_SOLAR_ECLIPSE_BIT) != 0) {
            active.add(CelestialEventType.SOLAR_ECLIPSE);
        }
        if ((mask & CURRENT_NEW_MOON_BIT) != 0) {
            active.add(CelestialEventType.NEW_MOON);
        }
        if ((mask & CURRENT_FULL_MOON_BIT) != 0) {
            active.add(CelestialEventType.FULL_MOON);
        }
        if ((mask & CURRENT_BLOOD_MOON_BIT) != 0) {
            active.add(CelestialEventType.BLOOD_MOON);
        } else if ((mask & CURRENT_LUNAR_ECLIPSE_BIT) != 0) {
            active.add(CelestialEventType.LUNAR_ECLIPSE);
        }
        if ((mask & CURRENT_SUPERMOON_BIT) != 0) {
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
     * The pointer remains at the real timeline coordinate. Icons for one day are centered around
     * it. The caller deliberately excludes ordinary full/new Moon predictions, so the complete
     * 400-day exceptional-event axis remains sparse enough for one finite brass-slot lane.
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
        long previousDay = Long.MIN_VALUE;
        int previousCenter = Integer.MIN_VALUE;
        int[] widths = new int[count];
        int[] lefts = new int[count];
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
            int halfExtent = width / 2;
            if (width > maximumRight - minimumLeft) {
                throw new IllegalArgumentException("Timeline day group is wider than its bounds");
            }
            int pointerX = (int) Math.round(clamp(seed.desiredCenter(),
                    minimumLeft + halfExtent, maximumRight - halfExtent));
            int left = pointerX - halfExtent;
            if (index > 0) {
                left = Math.max(left, lefts[index - 1] + widths[index - 1] + markerGap);
            }
            widths[index] = width;
            lefts[index] = left;
            previousDay = seed.day();
            previousCenter = seed.desiredCenter();
        }

        if (lefts[count - 1] + widths[count - 1] > maximumRight) {
            lefts[count - 1] = maximumRight - widths[count - 1];
            for (int index = count - 2; index >= 0; index--) {
                lefts[index] = Math.min(lefts[index],
                        lefts[index + 1] - markerGap - widths[index]);
            }
            if (lefts[0] < minimumLeft) {
                lefts[0] = minimumLeft;
                for (int index = 1; index < count; index++) {
                    lefts[index] = Math.max(lefts[index],
                            lefts[index - 1] + widths[index - 1] + markerGap);
                }
            }
        }

        List<TimelineDayLayout> layouts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            TimelineDaySeed seed = seeds.get(index);
            int layoutCenter = lefts[index] + widths[index] / 2;
            List<Integer> upperLefts = rowLefts(layoutCenter, seed.upperCount(),
                    markerSize, markerGap);
            List<Integer> lowerLefts = rowLefts(layoutCenter, seed.lowerCount(),
                    markerSize, markerGap);
            int pointerX = timelineGroupCenter(upperLefts, lowerLefts, markerSize);
            layouts.add(new TimelineDayLayout(seed.day(), pointerX, 0,
                    upperLefts, lowerLefts));
        }
        return List.copyOf(layouts);
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

    record PixelRect(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    enum TimelineLunarMarkerKind {
        ECLIPSE,
        SUPERMOON
    }

    enum InputLayer {
        EVENT_MARKER,
        TIMELINE,
        CLOCK,
        MAP,
        NONE
    }

    record TimelineLunarMarker(LunarPrediction prediction, TimelineLunarMarkerKind kind) {}

    record TimelineMoonTint(float red, float green, float blue) {}

    record TimelineDaySeed(long day, int desiredCenter, int upperCount, int lowerCount) {}

    record TimelineDayLayout(long day, int pointerX, int lane, List<Integer> upperLefts,
                             List<Integer> lowerLefts) {}

    record FloatingComponentLayout(double timelineOffsetX, double timelineOffsetY,
                                   double clockOffsetX, double clockOffsetY) {}

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

    /** Non-persistent screen-space drag lifecycle for one floating planetarium component. */
    record ComponentDragState(boolean dragging, double pressX, double pressY,
                              double originOffsetX, double originOffsetY) {
        static final ComponentDragState NONE = new ComponentDragState(false,
                0.0D, 0.0D, 0.0D, 0.0D);

        ComponentDragState begin(double mouseX, double mouseY,
                                 double componentX, double componentY,
                                 double componentWidth, double componentHeight,
                                 double offsetX, double offsetY) {
            boolean inside = Double.isFinite(mouseX) && Double.isFinite(mouseY)
                    && Double.isFinite(componentX) && Double.isFinite(componentY)
                    && componentWidth > 0.0D && componentHeight > 0.0D
                    && mouseX >= componentX && mouseX <= componentX + componentWidth
                    && mouseY >= componentY && mouseY <= componentY + componentHeight;
            return inside ? new ComponentDragState(true, mouseX, mouseY, offsetX, offsetY) : NONE;
        }

        Point drag(double mouseX, double mouseY, double scale,
                   double baseScreenX, double baseScreenY,
                   double componentWidth, double componentHeight,
                   int viewportWidth, int viewportHeight) {
            if (!dragging || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                    || !Double.isFinite(scale) || !(scale > 0.0D)) {
                return Point.HIDDEN;
            }
            double wantedOffsetX = originOffsetX + (mouseX - pressX) / scale;
            double wantedOffsetY = originOffsetY + (mouseY - pressY) / scale;
            double minimumOffsetX = -baseScreenX / scale;
            double minimumOffsetY = -baseScreenY / scale;
            double maximumOffsetX = (viewportWidth - componentWidth - baseScreenX) / scale;
            double maximumOffsetY = (viewportHeight - componentHeight - baseScreenY) / scale;
            return new Point(clamp(wantedOffsetX, minimumOffsetX, maximumOffsetX),
                    clamp(wantedOffsetY, minimumOffsetY, maximumOffsetY), true);
        }

        ComponentDragState release() {
            return NONE;
        }
    }

}
