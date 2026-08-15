package first.wildfires.client.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialEventType;
import first.wildfires.celestial.EclipsePredictionService;
import first.wildfires.celestial.EclipsePredictionService.CurrentEvent;
import first.wildfires.celestial.EclipsePredictionService.CurrentEvents;
import first.wildfires.celestial.EclipsePredictionService.LunarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Predictions;
import first.wildfires.celestial.EclipsePredictionService.SolarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Timeline;
import first.wildfires.client.celestial.PlanetariumProjection.MapDragState;
import first.wildfires.client.celestial.PlanetariumProjection.ComponentDragState;
import first.wildfires.client.celestial.PlanetariumProjection.TimelineLunarMarkerKind;
import first.wildfires.client.celestial.PlanetariumProjection.MapSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Pixel-art eclipse map, local day/night dial and 400-day interactive event timeline. */
public final class PlanetariumScreen extends Screen {

    private static final int CANVAS_WIDTH = 256;
    private static final int CANVAS_HEIGHT = 256;
    private static final float MAX_CANVAS_SCALE = 3.0F;
    private static final int CHART_X = 20;
    private static final int CHART_Y = 60;
    private static final int CHART_WIDTH = 123;
    private static final int CHART_HEIGHT = 76;
    private static final int MAP_CROSSHAIR_RADIUS =
            PlanetariumProjection.MAP_SELECTION_CURSOR_RADIUS;
    private static final int MAP_CROSSHAIR_HOVER_RADIUS =
            PlanetariumProjection.MAP_SELECTION_HOVER_RADIUS;
    private static final int MAP_PLAYER_CURSOR_RADIUS =
            PlanetariumProjection.MAP_PLAYER_CURSOR_RADIUS;
    private static final int MAP_SELECTION_COLOR = PlanetariumProjection.MAP_SELECTION_COLOR;
    private static final int MAP_PLAYER_CURSOR_COLOR =
            PlanetariumProjection.MAP_PLAYER_CURSOR_COLOR;
    private static final int CLOCK_TEXTURE_X = PlanetariumProjection.CLOCK_TEXTURE_X;
    private static final int CLOCK_TEXTURE_Y = PlanetariumProjection.CLOCK_TEXTURE_Y;
    private static final double CLOCK_X = CLOCK_TEXTURE_X
            + PlanetariumProjection.CLOCK_CENTER_SOURCE_X;
    private static final double CLOCK_Y = CLOCK_TEXTURE_Y
            + PlanetariumProjection.CLOCK_CENTER_SOURCE_Y;
    private static final int TIMELINE_TEXTURE_X = 22;
    private static final int TIMELINE_TEXTURE_Y = 16;
    private static final int TIMELINE_X = 24;
    private static final int TIMELINE_Y = 26;
    private static final int TIMELINE_WIDTH = 203;
    private static final int TIMELINE_ICON_SIZE = PlanetariumProjection.TIMELINE_ICON_SIZE;
    private static final int TIMELINE_DISC_SOURCE_SIZE = PlanetariumProjection.TIMELINE_DISC_SOURCE_SIZE;
    private static final int TIMELINE_DISC_SOURCE_U = PlanetariumProjection.TIMELINE_DISC_SOURCE_U;
    private static final int TIMELINE_DISC_SOURCE_V = PlanetariumProjection.TIMELINE_DISC_SOURCE_V;
    private static final int TIMELINE_POINTER_LENGTH = PlanetariumProjection.TIMELINE_POINTER_LENGTH;
    private static final int TIMELINE_POINTER_WIDTH = PlanetariumProjection.TIMELINE_POINTER_WIDTH;
    private static final int TIMELINE_POINTER_COLOR = PlanetariumProjection.TIMELINE_POINTER_COLOR;
    private static final int TIMELINE_LABEL_Y = PlanetariumProjection.TIMELINE_LABEL_Y;
    private static final int POINTER_TEXTURE_WIDTH = PlanetariumProjection.POINTER_TEXTURE_WIDTH;
    private static final int POINTER_TEXTURE_HEIGHT = PlanetariumProjection.POINTER_TEXTURE_HEIGHT;
    private static final int CLOCK_TEXTURE_WIDTH = PlanetariumProjection.CLOCK_TEXTURE_WIDTH;
    private static final int CLOCK_TEXTURE_HEIGHT = PlanetariumProjection.CLOCK_TEXTURE_HEIGHT;
    private static final double TIMELINE_DAYS = 400.0D;
    private static final int[] ECLIPSE_BAND_COLORS = {
            0x553A7180, 0x66566675, 0x77735863, 0x99884A4D, 0xBBA02F39
    };
    private static final ResourceLocation BACKGROUND = Wildfires.rl(
            "textures/gui/planetarium/planetarium_background.png");
    private static final ResourceLocation CLOCK_FRAME = Wildfires.rl(
            "textures/gui/planetarium/planetarium_clock_frame.png");
    private static final ResourceLocation TIMELINE_SLOT = Wildfires.rl(
            "textures/gui/planetarium/planetarium_timeline_slot.png");
    private static final ResourceLocation DAY_DISC = Wildfires.rl(
            "textures/gui/planetarium/planetarium_day_disc.png");
    private static final ResourceLocation NIGHT_DISC = Wildfires.rl(
            "textures/gui/planetarium/planetarium_night_disc.png");
    private static final ResourceLocation TIME_POINTER = Wildfires.rl(
            "textures/gui/planetarium/planetarium_time_pointer.png");
    private static final ResourceLocation TIME_POINTER_SHADOW = Wildfires.rl(
            "textures/gui/planetarium/planetarium_time_pointer_shadow.png");
    private static final ResourceLocation TIMELINE_SUN = ResourceLocation.parse(
            PlanetariumProjection.TIMELINE_SUN_TEXTURE);
    private static final ResourceLocation TIMELINE_FULL_MOON = ResourceLocation.parse(
            PlanetariumProjection.TIMELINE_FULL_MOON_TEXTURE);

    private Predictions predictions;
    private Timeline timeline;
    private SolarPrediction selectedSolar = SolarPrediction.NONE;
    private LunarPrediction selectedLunar = LunarPrediction.NONE;
    private TimelineLunarMarkerKind selectedLunarMarkerKind;
    private MapDragState mapDragState = MapDragState.NONE;
    private CurrentEvents currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
    private List<CurrentEvent> displayedCurrentEvents = List.of();
    private int displayedCurrentEventMask;
    private double[] selectedCoverage = new double[CHART_HEIGHT - 2];
    private long predictionCalendarTick = Long.MIN_VALUE;
    private final List<EventMarker> markers = new ArrayList<>();
    private final List<TimelinePointer> timelinePointers = new ArrayList<>();
    private float canvasScale = 1.0F;
    private int canvasX;
    private int canvasY;
    private double timelineOffsetX;
    private double timelineOffsetY;
    private double clockOffsetX;
    private double clockOffsetY;
    private ComponentDragState timelineDragState = ComponentDragState.NONE;
    private ComponentDragState clockDragState = ComponentDragState.NONE;

    PlanetariumScreen() {
        super(Component.translatable("screen.wildfires.planetarium.title"));
        resetFloatingComponentLayout();
    }

    /**
     * Starts every newly opened screen from the authored component placement. Drag offsets are
     * deliberately session-local and are never restored from an item, config, or static field.
     */
    private void resetFloatingComponentLayout() {
        PlanetariumProjection.FloatingComponentLayout layout =
                PlanetariumProjection.initialFloatingComponentLayout();
        timelineOffsetX = layout.timelineOffsetX();
        timelineOffsetY = layout.timelineOffsetY();
        clockOffsetX = layout.clockOffsetX();
        clockOffsetY = layout.clockOffsetY();
        timelineDragState = ComponentDragState.NONE;
        clockDragState = ComponentDragState.NONE;
    }

    @Override
    protected void init() {
        updateCanvasTransform();
        refreshPredictions();
    }

    private void updateCanvasTransform() {
        canvasScale = Math.min(MAX_CANVAS_SCALE,
                Math.min(width / (float) CANVAS_WIDTH, height / (float) CANVAS_HEIGHT));
        canvasScale = Math.max(0.25F, canvasScale);
        canvasX = Math.round((width - CANVAS_WIDTH * canvasScale) * 0.5F);
        canvasY = Math.round((height - CANVAS_HEIGHT * canvasScale) * 0.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        long current = Calendars.get(minecraft.level).getCalendarTicks();
        boolean nextExpired = predictions != null
                && ((predictions.solar().present()
                && current > predictions.solar().endCalendarTicks())
                || (predictions.lunar().present()
                && current > predictions.lunar().endCalendarTicks()));
        if (timeline == null || current < predictionCalendarTick
                || current - predictionCalendarTick >= CelestialMath.TICKS_IN_DAY || nextExpired) {
            refreshPredictions();
        } else {
            int apiEventMask = currentApiEventMask();
            if (current >= currentEvents.nextChangeCalendarTicks()
                    || displayedCurrentEventMask != apiEventMask) {
                refreshCurrentEvents(apiEventMask);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        CelestialState state = currentState(partialTick);
        double logicalMouseX = (mouseX - canvasX) / canvasScale;
        double logicalMouseY = (mouseY - canvasY) / canvasScale;
        EventMarker hovered = null;
        boolean mapCrosshairHovered = false;

        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0.0F);
        graphics.pose().scale(canvasScale, canvasScale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(BACKGROUND, 0, 0, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT,
                CANVAS_WIDTH, CANVAS_HEIGHT);
        if (state == null || predictions == null || timeline == null) {
            graphics.blit(CLOCK_FRAME, CLOCK_TEXTURE_X, CLOCK_TEXTURE_Y,
                    0, 0, CLOCK_TEXTURE_WIDTH, CLOCK_TEXTURE_HEIGHT,
                    CLOCK_TEXTURE_WIDTH, CLOCK_TEXTURE_HEIGHT);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.wildfires.planetarium.unavailable"),
                    CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2, 0xFFFF7777);
        } else {
            drawEclipseMap(graphics, state);
            drawInformation(graphics, state);
            drawTimeline(graphics);
            drawClock(graphics, state);
            hovered = markerAt(logicalMouseX, logicalMouseY);
            mapCrosshairHovered = PlanetariumProjection.crosshairContains(mapDragState.selection(),
                    logicalMouseX, logicalMouseY, CHART_X, CHART_Y, CHART_WIDTH, CHART_HEIGHT,
                    MAP_CROSSHAIR_HOVER_RADIUS);
        }
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            graphics.renderComponentTooltip(font, markerTooltip(hovered), mouseX, mouseY);
        } else if (mapCrosshairHovered) {
            graphics.renderComponentTooltip(font, mapCrosshairTooltip(), mouseX, mouseY);
        }
    }

    private void drawTimeline(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(timelineOffsetX, timelineOffsetY, 5.0F);
        graphics.blit(TIMELINE_SLOT, TIMELINE_TEXTURE_X, TIMELINE_TEXTURE_Y,
                0, 0, 256, 32, 256, 32);
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        for (int day = 0; day <= 400; day += 100) {
            int x = TIMELINE_X + (int) Math.round(TIMELINE_WIDTH * day / TIMELINE_DAYS);
            graphics.drawCenteredString(font, Integer.toString(day), x * 2,
                    TIMELINE_LABEL_Y * 2, PlanetariumProjection.TIMELINE_LABEL_COLOR);
        }
        graphics.pose().popPose();
        for (TimelinePointer pointer : timelinePointers) {
            drawTimelinePointer(graphics, pointer.x(), pointer.startY(), pointer.endY());
        }
        for (EventMarker marker : markers) {
            int centerX = marker.x() + TIMELINE_ICON_SIZE / 2;
            int centerY = marker.y() + TIMELINE_ICON_SIZE / 2;
            if (marker.solar() != null) {
                boolean selected = selectedSolar.present()
                        && selectedSolar.conjunctionIndex() == marker.solar().conjunctionIndex();
                drawMiniSun(graphics, centerX, centerY, selected);
            } else if (marker.lunar() != null) {
                boolean selected = selectedLunar.present()
                        && selectedLunar.fullMoonIndex() == marker.lunar().fullMoonIndex()
                        && selectedLunarMarkerKind == marker.lunarMarkerKind();
                drawMiniMoon(graphics, centerX, centerY, marker.lunar(),
                        marker.lunarMarkerKind(), selected);
            }
        }
        graphics.pose().popPose();
    }

    /** Rebuilds the prediction-dependent marker layout once instead of once per rendered frame. */
    private void rebuildTimelineLayout() {
        markers.clear();
        timelinePointers.clear();
        if (timeline == null) {
            return;
        }
        List<PlanetariumProjection.TimelineLunarMarker> lunarMarkers =
                PlanetariumProjection.timelineLunarMarkers(timeline.lunar());
        List<PendingEventMarker> pending = new ArrayList<>();
        for (SolarPrediction solar : timeline.solar()) {
            pending.add(PendingEventMarker.solar(solar));
        }
        for (PlanetariumProjection.TimelineLunarMarker marker : lunarMarkers) {
            pending.add(PendingEventMarker.lunar(marker.prediction(), marker.kind()));
        }
        pending.sort(Comparator.comparingDouble(PendingEventMarker::calendarTicks)
                .thenComparingInt(PendingEventMarker::kindOrder));

        List<PendingDayGroup> dayGroups = new ArrayList<>();
        for (PendingEventMarker event : pending) {
            long day = event.calendarDay();
            PendingDayGroup group = dayGroups.isEmpty() ? null : dayGroups.get(dayGroups.size() - 1);
            if (group == null || group.day() != day) {
                group = new PendingDayGroup(day);
                dayGroups.add(group);
            }
            group.add(event);
        }
        List<PlanetariumProjection.TimelineDaySeed> seeds = new ArrayList<>(dayGroups.size());
        for (PendingDayGroup group : dayGroups) {
            seeds.add(new PlanetariumProjection.TimelineDaySeed(group.day(),
                    timelineX((group.day() + 0.5D) * CelestialMath.TICKS_IN_DAY),
                    group.upper().size(), group.lower().size()));
        }
        List<PlanetariumProjection.TimelineDayLayout> layouts =
                PlanetariumProjection.timelineDayLayouts(seeds,
                        TIMELINE_ICON_SIZE, PlanetariumProjection.TIMELINE_TRACK_GAP,
                        TIMELINE_X - TIMELINE_ICON_SIZE / 2,
                        TIMELINE_X + TIMELINE_WIDTH + TIMELINE_ICON_SIZE / 2);
        for (int groupIndex = 0; groupIndex < dayGroups.size(); groupIndex++) {
            PendingDayGroup group = dayGroups.get(groupIndex);
            PlanetariumProjection.TimelineDayLayout layout = layouts.get(groupIndex);
            for (int index = 0; index < group.upper().size(); index++) {
                markers.add(group.upper().get(index).place(layout.upperLefts().get(index), 0));
            }
            for (int index = 0; index < group.lower().size(); index++) {
                markers.add(group.lower().get(index).place(layout.lowerLefts().get(index), 0));
            }
            int pointerStartY = group.upper().isEmpty()
                    ? TIMELINE_Y : TIMELINE_Y - TIMELINE_POINTER_LENGTH;
            int pointerEndY = group.lower().isEmpty()
                    ? TIMELINE_Y : TIMELINE_Y + TIMELINE_POINTER_LENGTH;
            timelinePointers.add(new TimelinePointer(layout.pointerX(), pointerStartY,
                    pointerEndY));
        }
    }

    private int timelineX(double calendarTicks) {
        double progress = PlanetariumProjection.timelineProgress(timeline, calendarTicks);
        return TIMELINE_X + (int) Math.round(progress * TIMELINE_WIDTH);
    }

    private void drawMiniSun(GuiGraphics graphics, int centerX, int centerY, boolean selected) {
        drawVanillaTimelineDisc(graphics, TIMELINE_SUN, centerX, centerY,
                1.0F, 1.0F, 1.0F, 32, 32);
        drawSelectedMarkerBorder(graphics, centerX, centerY, selected);
    }

    private void drawMiniMoon(GuiGraphics graphics, int centerX, int centerY,
                              LunarPrediction lunar, TimelineLunarMarkerKind markerKind,
                              boolean selected) {
        PlanetariumProjection.TimelineMoonTint tint =
                PlanetariumProjection.timelineMoonTint(lunar, markerKind);
        drawVanillaTimelineDisc(graphics, TIMELINE_FULL_MOON, centerX, centerY,
                tint.red(), tint.green(), tint.blue(), 128, 64);
        drawSelectedMarkerBorder(graphics, centerX, centerY, selected);
    }

    private static void drawSelectedMarkerBorder(GuiGraphics graphics, int centerX, int centerY,
                                                 boolean selected) {
        if (!selected) {
            return;
        }
        PlanetariumProjection.PixelRect bounds =
                PlanetariumProjection.timelineIconBounds(centerX, centerY);
        int edge = PlanetariumProjection.TIMELINE_SELECTED_COLOR;
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.top() + 1, edge);
        graphics.fill(bounds.left(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), edge);
        graphics.fill(bounds.left(), bounds.top() + 1, bounds.left() + 1,
                bounds.bottom() - 1, edge);
        graphics.fill(bounds.right() - 1, bounds.top() + 1, bounds.right(),
                bounds.bottom() - 1, edge);
    }

    private static void drawTimelinePointer(GuiGraphics graphics, int centerX,
                                            int startY, int endY) {
        graphics.fill(centerX, Math.min(startY, endY), centerX + TIMELINE_POINTER_WIDTH,
                Math.max(startY, endY), TIMELINE_POINTER_COLOR);
    }

    private void drawVanillaTimelineDisc(GuiGraphics graphics, ResourceLocation texture,
                                         int centerX, int centerY, float red, float green, float blue,
                                         int textureWidth, int textureHeight) {
        drawVanillaTimelineDisc(graphics, texture, centerX, centerY,
                TIMELINE_DISC_SOURCE_V, red, green, blue, textureWidth, textureHeight);
    }

    private void drawVanillaTimelineDisc(GuiGraphics graphics, ResourceLocation texture,
                                         int centerX, int centerY, int sourceV,
                                         float red, float green, float blue,
                                         int textureWidth, int textureHeight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = graphics.pose().last().pose();
        PlanetariumProjection.PixelRect bounds =
                PlanetariumProjection.timelineIconBounds(centerX, centerY);
        float left = bounds.left();
        float top = bounds.top();
        float right = bounds.right();
        float bottom = bounds.bottom();
        float u0 = TIMELINE_DISC_SOURCE_U / (float) textureWidth;
        float v0 = sourceV / (float) textureHeight;
        float u1 = (TIMELINE_DISC_SOURCE_U + TIMELINE_DISC_SOURCE_SIZE)
                / (float) textureWidth;
        float v1 = (sourceV + TIMELINE_DISC_SOURCE_SIZE) / (float) textureHeight;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, left, bottom, 0.0F).uv(u0, v1).endVertex();
        builder.vertex(matrix, right, bottom, 0.0F).uv(u1, v1).endVertex();
        builder.vertex(matrix, right, top, 0.0F).uv(u1, v0).endVertex();
        builder.vertex(matrix, left, top, 0.0F).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawEclipseMap(GuiGraphics graphics, CelestialState state) {
        if (selectedSolar.present()) {
            for (int row = 0; row < selectedCoverage.length; row++) {
                int band = PlanetariumProjection.eclipseCoverageBand(selectedCoverage[row]);
                if (band >= 0) {
                    graphics.fill(CHART_X + 1, CHART_Y + 1 + row,
                            CHART_X + CHART_WIDTH, CHART_Y + 2 + row,
                            ECLIPSE_BAND_COLORS[band]);
                }
            }
        }
        drawChartLabels(graphics);

        double longitude = currentLongitude();
        double latitude = state.latitudeRadians();
        MapSelection mapSelection = mapDragState.selection();
        if (mapSelection.present()) {
            int markerX = PlanetariumProjection.mapX(mapSelection.longitudeRadians(),
                    CHART_X, CHART_WIDTH);
            int markerY = PlanetariumProjection.mapY(mapSelection.latitudeRadians(),
                    CHART_Y, CHART_HEIGHT);
            drawMapCursor(graphics, markerX, markerY, MAP_CROSSHAIR_RADIUS,
                    MAP_SELECTION_COLOR);
        }
        int playerX = PlanetariumProjection.mapX(longitude, CHART_X, CHART_WIDTH);
        int playerY = PlanetariumProjection.mapY(latitude, CHART_Y, CHART_HEIGHT);
        drawMapCursor(graphics, playerX, playerY, MAP_PLAYER_CURSOR_RADIUS,
                MAP_PLAYER_CURSOR_COLOR);

    }

    private static void drawMapCursor(GuiGraphics graphics, int centerX, int centerY,
                                      int radius, int color) {
        graphics.fill(centerX - radius, centerY, centerX + radius + 1, centerY + 1, color);
        graphics.fill(centerX, centerY - radius, centerX + 1, centerY + radius + 1, color);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFFFFFFF);
    }

    private void drawChartLabels(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        int[] longitudeDegrees = {-180, 0, 180};
        for (int i = 0; i < longitudeDegrees.length; i++) {
            int x = CHART_X + (int) Math.round(CHART_WIDTH * i / 2.0D);
            graphics.drawCenteredString(font, Integer.toString(longitudeDegrees[i]), x * 2,
                    (CHART_Y + CHART_HEIGHT + 1) * 2, 0xFF4B321F);
        }
        int[] latitudeDegrees = {90, 0, -90};
        for (int i = 0; i < latitudeDegrees.length; i++) {
            int y = CHART_Y + (int) Math.round(CHART_HEIGHT * i / 2.0D) - 4;
            graphics.drawString(font, Integer.toString(latitudeDegrees[i]), (CHART_X - 14) * 2,
                    y * 2,
                    0xFF4B321F, false);
        }
        graphics.pose().popPose();
    }

    private void rebuildCoverageProfile() {
        selectedCoverage = new double[CHART_HEIGHT - 2];
        if (!selectedSolar.present()) {
            return;
        }
        for (int row = 0; row < selectedCoverage.length; row++) {
            double latitude = Math.PI * 0.5D
                    - (row + 0.5D) / selectedCoverage.length * Math.PI;
            selectedCoverage[row] = PlanetariumProjection.maximumGlobalSolarCoverage(
                    selectedSolar, latitude);
        }
    }

    private void drawClock(GuiGraphics graphics, CelestialState state) {
        graphics.pose().pushPose();
        graphics.pose().translate(clockOffsetX, clockOffsetY, 6.0F);
        PlanetariumClock.Schedule schedule = PlanetariumClock.schedule(
                state.latitudeRadians(), state.fractionOfYear());
        if (schedule.polarDay()) {
            drawTexturedSector(graphics, DAY_DISC, 0.0D, 1.0D);
        } else if (schedule.polarNight()) {
            drawTexturedSector(graphics, NIGHT_DISC, 0.0D, 1.0D);
        } else {
            drawTexturedSector(graphics, NIGHT_DISC, schedule.sunsetFraction(),
                    schedule.sunriseFraction() + 1.0D);
            drawTexturedSector(graphics, DAY_DISC, schedule.sunriseFraction(),
                    schedule.sunsetFraction());
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 2.0F);
        graphics.blit(CLOCK_FRAME, CLOCK_TEXTURE_X, CLOCK_TEXTURE_Y,
                0, 0, CLOCK_TEXTURE_WIDTH, CLOCK_TEXTURE_HEIGHT,
                CLOCK_TEXTURE_WIDTH, CLOCK_TEXTURE_HEIGHT);
        graphics.pose().popPose();
        double pointerFraction = PlanetariumClock.pointerFraction(state.fractionOfDay());
        drawEllipticalPointer(graphics, TIME_POINTER_SHADOW, pointerFraction, true);
        drawEllipticalPointer(graphics, TIME_POINTER, pointerFraction, false);
        graphics.pose().popPose();
    }

    private void drawTexturedSector(GuiGraphics graphics, ResourceLocation texture,
                                    double startFraction, double endFraction) {
        double span = endFraction - startFraction;
        if (!(span > 1.0E-9D)) {
            return;
        }
        int segments = Math.max(1, (int) Math.ceil(Math.min(1.0D, span) * 96.0D));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = graphics.pose().last().pose();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int segment = 0; segment < segments; segment++) {
            double first = startFraction + span * segment / segments;
            double second = startFraction + span * (segment + 1) / segments;
            sectorVertex(builder, matrix, (float) CLOCK_X, (float) CLOCK_Y,
                    (float) (PlanetariumProjection.CLOCK_CENTER_SOURCE_X / CLOCK_TEXTURE_WIDTH),
                    (float) (PlanetariumProjection.CLOCK_CENTER_SOURCE_Y / CLOCK_TEXTURE_HEIGHT),
                    0.0F);
            sectorOuterVertex(builder, matrix, first);
            sectorOuterVertex(builder, matrix, second);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
    }

    private static void sectorOuterVertex(BufferBuilder builder, Matrix4f matrix, double fraction) {
        PlanetariumProjection.Point point = PlanetariumProjection.ellipsePoint(fraction,
                CLOCK_X, CLOCK_Y, PlanetariumProjection.CLOCK_RADIUS_X,
                PlanetariumProjection.CLOCK_RADIUS_Y);
        double sourceX = point.x() - CLOCK_TEXTURE_X;
        double sourceY = point.y() - CLOCK_TEXTURE_Y;
        sectorVertex(builder, matrix, (float) point.x(), (float) point.y(),
                (float) (sourceX / CLOCK_TEXTURE_WIDTH),
                (float) (sourceY / CLOCK_TEXTURE_HEIGHT), 0.0F);
    }

    private static void sectorVertex(BufferBuilder builder, Matrix4f matrix,
                                     float x, float y, float u, float v, float z) {
        builder.vertex(matrix, x, y, z).uv(u, v).endVertex();
    }

    private static void drawEllipticalPointer(GuiGraphics graphics, ResourceLocation texture,
                                              double fraction, boolean shadow) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = graphics.pose().last().pose();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        pointerVertex(builder, matrix, 0.0D, 0.0D, fraction, shadow, 0.0F, 0.0F);
        pointerVertex(builder, matrix, 0.0D, POINTER_TEXTURE_HEIGHT,
                fraction, shadow, 0.0F, 1.0F);
        pointerVertex(builder, matrix, POINTER_TEXTURE_WIDTH, POINTER_TEXTURE_HEIGHT,
                fraction, shadow, 1.0F, 1.0F);
        pointerVertex(builder, matrix, POINTER_TEXTURE_WIDTH, 0.0D,
                fraction, shadow, 1.0F, 0.0F);
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
    }

    private static void pointerVertex(BufferBuilder builder, Matrix4f matrix,
                                      double sourceX, double sourceY, double fraction,
                                      boolean shadow, float u, float v) {
        PlanetariumProjection.Point point = shadow
                ? PlanetariumProjection.ellipsePointerShadowVertex(
                sourceX, sourceY, fraction, CLOCK_X, CLOCK_Y)
                : PlanetariumProjection.ellipsePointerVertex(
                sourceX, sourceY, fraction, CLOCK_X, CLOCK_Y);
        sectorVertex(builder, matrix, (float) point.x(), (float) point.y(), u, v,
                shadow ? 3.0F : 4.0F);
    }

    private void drawInformation(GuiGraphics graphics, CelestialState state) {
        graphics.pose().pushPose();
        graphics.pose().translate(167.0F, 143.0F, 3.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        int x = 0;
        int y = 0;
        List<InformationLine> lines = new ArrayList<>();
        lines.add(InformationLine.title(Component.translatable(
                "screen.wildfires.planetarium.current")));
        lines.add(InformationLine.body(calendarText(state.calendarTicks()),
                PlanetariumProjection.INFO_PRIMARY_COLOR));
        lines.add(InformationLine.body(Component.translatable(
                        "screen.wildfires.planetarium.coordinates",
                        signedDegrees(currentLongitude()), signedDegrees(state.latitudeRadians())),
                PlanetariumProjection.INFO_ACCENT_COLOR));

        PlanetariumClock.Schedule schedule = PlanetariumClock.schedule(
                state.latitudeRadians(), state.fractionOfYear());
        Component clockText;
        if (schedule.polarDay()) {
            clockText = Component.translatable("screen.wildfires.planetarium.clock.polar_day");
        } else if (schedule.polarNight()) {
            clockText = Component.translatable("screen.wildfires.planetarium.clock.polar_night");
        } else {
            clockText = Component.translatable("screen.wildfires.planetarium.clock.transitions",
                    clockTime(schedule.sunriseFraction()), clockTime(schedule.sunsetFraction()));
        }
        lines.add(InformationLine.body(clockText, PlanetariumProjection.INFO_PRIMARY_COLOR));

        if (selectedSolar.present()) {
            lines.add(InformationLine.spacer());
            lines.add(InformationLine.body(Component.translatable(
                            "screen.wildfires.planetarium.coverage.compact",
                            percent(selectedSolar.globalMaximumCoverage()),
                            percent(selectedSolar.observerMaximumCoverage())),
                    PlanetariumProjection.INFO_SOLAR_COLOR));
        }
        if (selectedLunar.present()) {
            LunarPrediction lunar = selectedLunar;
            lines.add(InformationLine.spacer());
            lines.add(InformationLine.body(Component.translatable(
                            "screen.wildfires.planetarium.selected_lunar"),
                    PlanetariumProjection.INFO_LUNAR_COLOR));
            lines.add(InformationLine.body(calendarText((long) lunar.greatestCalendarTicks()),
                    PlanetariumProjection.INFO_PRIMARY_COLOR));
            if (selectedLunarMarkerKind == TimelineLunarMarkerKind.SUPERMOON) {
                lines.add(InformationLine.body(Component.translatable(
                                "screen.wildfires.planetarium.supermoon_strength",
                                percent(lunar.supermoonIntensity())),
                        PlanetariumProjection.INFO_SUPERMOON_COLOR));
            } else if (lunar.eclipse()) {
                lines.add(InformationLine.body(Component.translatable(
                                "screen.wildfires.planetarium.lunar_shape.compact",
                                Component.translatable("screen.wildfires.planetarium.lunar."
                                        + lunar.kind().name().toLowerCase(Locale.ROOT)),
                                percent(lunar.displayMaximumCoverage())),
                        PlanetariumProjection.INFO_SECONDARY_COLOR));
            } else if (lunar.supermoon()) {
                lines.add(InformationLine.body(Component.translatable(
                                "screen.wildfires.planetarium.supermoon_strength",
                                percent(lunar.supermoonIntensity())),
                        PlanetariumProjection.INFO_SUPERMOON_COLOR));
            }
        }

        lines.add(InformationLine.spacer());
        lines.add(InformationLine.body(Component.translatable(
                        "screen.wildfires.planetarium.next_lunar"),
                PlanetariumProjection.INFO_LUNAR_COLOR));
        Component nextLunar = predictions.lunar().present()
                ? calendarText((long) predictions.lunar().greatestCalendarTicks())
                : Component.translatable("screen.wildfires.planetarium.none");
        lines.add(InformationLine.body(nextLunar, PlanetariumProjection.INFO_PRIMARY_COLOR));

        lines.add(InformationLine.spacer());
        lines.add(InformationLine.title(Component.translatable(
                "screen.wildfires.planetarium.current_events")));
        if (displayedCurrentEvents.isEmpty()) {
            lines.add(InformationLine.body(Component.translatable(
                            "screen.wildfires.planetarium.current_events.none"),
                    PlanetariumProjection.INFO_PRIMARY_COLOR));
        } else {
            for (CurrentEvent event : displayedCurrentEvents) {
                lines.add(InformationLine.body(Component.translatable(
                                "screen.wildfires.planetarium.current_events.line",
                                currentEventName(event.type()),
                                calendarText(event.endCalendarTicks())),
                        currentEventColor(event.type())));
            }
        }
        for (InformationLine line : lines) {
            if (line.gap()) {
                y += PlanetariumProjection.INFO_GROUP_GAP;
                continue;
            }
            Component text = line.text();
            String fitted = font.plainSubstrByWidth(text.getString(),
                    PlanetariumProjection.INFO_BOX_WIDTH);
            graphics.drawString(font, fitted, x, y, line.color(), true);
            y += PlanetariumProjection.INFO_LINE_HEIGHT;
            if (y > PlanetariumProjection.INFO_BOX_HEIGHT
                    - PlanetariumProjection.INFO_LINE_HEIGHT) {
                break;
            }
        }
        graphics.pose().popPose();
    }

    private static Component currentEventName(CelestialEventType type) {
        String key = switch (type) {
            case SOLAR_ECLIPSE -> "screen.wildfires.planetarium.timeline.solar";
            case LUNAR_ECLIPSE -> "screen.wildfires.planetarium.timeline.lunar";
            case BLOOD_MOON -> "screen.wildfires.planetarium.timeline.blood_moon";
            case SUPERMOON -> "screen.wildfires.planetarium.timeline.supermoon";
            case FULL_MOON -> "screen.wildfires.planetarium.timeline.full_moon";
            case NEW_MOON -> "screen.wildfires.planetarium.timeline.new_moon";
            default -> type.translationKey();
        };
        return Component.translatable(key);
    }

    private static int currentEventColor(CelestialEventType type) {
        return switch (type) {
            case SOLAR_ECLIPSE -> PlanetariumProjection.INFO_SOLAR_COLOR;
            case LUNAR_ECLIPSE -> PlanetariumProjection.INFO_LUNAR_COLOR;
            case BLOOD_MOON -> 0xFFE15B54;
            case SUPERMOON -> PlanetariumProjection.INFO_SUPERMOON_COLOR;
            default -> PlanetariumProjection.INFO_PRIMARY_COLOR;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            mapDragState = mapDragState.release();
            timelineDragState = timelineDragState.release();
            clockDragState = clockDragState.release();
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        double logicalX = (mouseX - canvasX) / canvasScale;
        double logicalY = (mouseY - canvasY) / canvasScale;
        EventMarker marker = markerAt(logicalX, logicalY);
        if (marker != null && marker.solar() != null) {
            selectedSolar = marker.solar();
            rebuildCoverageProfile();
            return true;
        }
        if (marker != null && marker.lunar() != null) {
            selectedLunar = marker.lunar();
            selectedLunarMarkerKind = marker.lunarMarkerKind();
            return true;
        }
        if (button == 0) {
            timelineDragState = timelineDragState.begin(mouseX, mouseY,
                    canvasX + (TIMELINE_TEXTURE_X + timelineOffsetX) * canvasScale,
                    canvasY + (TIMELINE_TEXTURE_Y + timelineOffsetY) * canvasScale,
                    208.0D * canvasScale, 22.0D * canvasScale,
                    timelineOffsetX, timelineOffsetY);
            if (timelineDragState.dragging()) {
                return true;
            }
            clockDragState = clockDragState.begin(mouseX, mouseY,
                    canvasX + (CLOCK_TEXTURE_X + clockOffsetX) * canvasScale,
                    canvasY + (CLOCK_TEXTURE_Y + clockOffsetY) * canvasScale,
                    CLOCK_TEXTURE_WIDTH * canvasScale, CLOCK_TEXTURE_HEIGHT * canvasScale,
                    clockOffsetX, clockOffsetY);
            if (clockDragState.dragging()) {
                return true;
            }
            MapDragState started = mapDragState.begin(logicalX, logicalY,
                    CHART_X, CHART_Y, CHART_WIDTH, CHART_HEIGHT);
            mapDragState = started;
            if (started.dragging()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && mapDragState.dragging()) {
            double logicalX = (mouseX - canvasX) / canvasScale;
            double logicalY = (mouseY - canvasY) / canvasScale;
            mapDragState = mapDragState.drag(logicalX, logicalY,
                    CHART_X, CHART_Y, CHART_WIDTH, CHART_HEIGHT);
            return true;
        }
        if (button == 0 && timelineDragState.dragging()) {
            PlanetariumProjection.Point offset = timelineDragState.drag(mouseX, mouseY,
                    canvasScale, canvasX + TIMELINE_TEXTURE_X * canvasScale,
                    canvasY + TIMELINE_TEXTURE_Y * canvasScale,
                    208.0D * canvasScale, 22.0D * canvasScale, width, height);
            if (offset.visible()) {
                timelineOffsetX = offset.x();
                timelineOffsetY = offset.y();
            }
            return true;
        }
        if (button == 0 && clockDragState.dragging()) {
            PlanetariumProjection.Point offset = clockDragState.drag(mouseX, mouseY,
                    canvasScale, canvasX + CLOCK_TEXTURE_X * canvasScale,
                    canvasY + CLOCK_TEXTURE_Y * canvasScale,
                    CLOCK_TEXTURE_WIDTH * canvasScale, CLOCK_TEXTURE_HEIGHT * canvasScale,
                    width, height);
            if (offset.visible()) {
                clockOffsetX = offset.x();
                clockOffsetY = offset.y();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = button == 0
                && (mapDragState.dragging() || timelineDragState.dragging()
                || clockDragState.dragging());
        if (button == 0) {
            mapDragState = mapDragState.release();
            timelineDragState = timelineDragState.release();
            clockDragState = clockDragState.release();
        }
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    private EventMarker markerAt(double x, double y) {
        double localX = x - timelineOffsetX;
        double localY = y - timelineOffsetY;
        for (int index = markers.size() - 1; index >= 0; index--) {
            EventMarker marker = markers.get(index);
            if (marker.contains(localX, localY)) {
                return marker;
            }
        }
        return null;
    }

    private List<Component> markerTooltip(EventMarker marker) {
        List<Component> lines = new ArrayList<>();
        if (marker.solar() != null) {
            SolarPrediction solar = marker.solar();
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.solar")
                    .withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.start",
                    calendarText((long) solar.startCalendarTicks())));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.maximum",
                    calendarText((long) solar.greatestCalendarTicks())));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.end",
                    calendarText((long) solar.endCalendarTicks())));
            lines.add(Component.translatable("screen.wildfires.planetarium.coverage",
                    percent(solar.globalMaximumCoverage()),
                    percent(solar.observerMaximumCoverage())));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.click"));
        } else if (marker.lunar() != null) {
            LunarPrediction lunar = marker.lunar();
            boolean supermoonMarker = marker.lunarMarkerKind()
                    == TimelineLunarMarkerKind.SUPERMOON;
            String titleKey = supermoonMarker
                    ? "screen.wildfires.planetarium.timeline.supermoon"
                    : lunar.bloodMoon()
                    ? "screen.wildfires.planetarium.timeline.blood_moon"
                    : "screen.wildfires.planetarium.timeline.lunar";
            lines.add(Component.translatable(titleKey).withStyle(supermoonMarker
                    ? ChatFormatting.AQUA : lunar.bloodMoon()
                    ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.start",
                    calendarText((long) lunar.startCalendarTicks())));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.maximum",
                    calendarText((long) lunar.greatestCalendarTicks())));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.end",
                    calendarText((long) lunar.endCalendarTicks())));
            if (!supermoonMarker && lunar.eclipse()) {
                lines.add(Component.translatable("screen.wildfires.planetarium.lunar_shape",
                        Component.translatable("screen.wildfires.planetarium.lunar."
                                + lunar.kind().name().toLowerCase(Locale.ROOT)),
                        percent(lunar.displayMaximumCoverage())));
            }
            if (supermoonMarker) {
                lines.add(Component.translatable("screen.wildfires.planetarium.supermoon_strength",
                        percent(lunar.supermoonIntensity())));
            }
        }
        return lines;
    }

    private List<Component> mapCrosshairTooltip() {
        MapSelection mapSelection = mapDragState.selection();
        if (!mapSelection.present()) {
            return List.of();
        }
        double coverage = selectedSolar.present()
                ? PlanetariumProjection.maximumGlobalSolarCoverage(selectedSolar,
                mapSelection.latitudeRadians()) : 0.0D;
        return List.of(
                Component.translatable("screen.wildfires.planetarium.map_cursor")
                        .withStyle(ChatFormatting.AQUA),
                Component.translatable("screen.wildfires.planetarium.coordinates",
                        signedDegrees(mapSelection.longitudeRadians()),
                        signedDegrees(mapSelection.latitudeRadians())),
                Component.translatable("screen.wildfires.planetarium.map_eclipse_coverage",
                        percent(coverage)));
    }

    private CelestialState currentState(float partialTick) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        return CelestialClientStateCache.stateOrNull(
                minecraft.level, minecraft.player.position(), partialTick);
    }

    private double currentLongitude() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return 0.0D;
        }
        return EclipsePredictionService.displayLongitude(minecraft.level, minecraft.player.getX());
    }

    private void refreshPredictions() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            predictions = null;
            timeline = null;
            selectedSolar = SolarPrediction.NONE;
            selectedLunar = LunarPrediction.NONE;
            selectedLunarMarkerKind = null;
            currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
            displayedCurrentEvents = List.of();
            displayedCurrentEventMask = 0;
            rebuildTimelineLayout();
            rebuildCoverageProfile();
            return;
        }
        long selectedIndex = selectedSolar.present() ? selectedSolar.conjunctionIndex() : Long.MIN_VALUE;
        long selectedFullMoon = selectedLunar.present() ? selectedLunar.fullMoonIndex() : Long.MIN_VALUE;
        predictionCalendarTick = Calendars.get(minecraft.level).getCalendarTicks();
        Vec3 observer = minecraft.player.position();
        predictions = EclipsePredictionService.predict(minecraft.level, observer);
        timeline = EclipsePredictionService.predictAnomalousTimeline(
                minecraft.level, observer, TIMELINE_DAYS);
        selectedSolar = PlanetariumProjection.selectSolar(timeline, selectedIndex);
        selectedLunar = PlanetariumProjection.selectLunar(timeline, selectedFullMoon);
        if (!selectedLunar.present()) {
            selectedLunarMarkerKind = null;
        } else if (selectedLunarMarkerKind != null) {
            List<TimelineLunarMarkerKind> markerKinds =
                    PlanetariumProjection.timelineLunarMarkerKinds(selectedLunar);
            if (!markerKinds.contains(selectedLunarMarkerKind)) {
                selectedLunarMarkerKind = markerKinds.isEmpty() ? null : markerKinds.get(0);
            }
        }
        if (!selectedSolar.present() && !timeline.solar().isEmpty()) {
            selectedSolar = timeline.solar().get(0);
        }
        rebuildTimelineLayout();
        rebuildCoverageProfile();
        refreshCurrentEvents();
    }

    private void refreshCurrentEvents() {
        refreshCurrentEvents(currentApiEventMask());
    }

    private void refreshCurrentEvents(int apiEventMask) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
            displayedCurrentEvents = List.of();
            displayedCurrentEventMask = 0;
            return;
        }
        var observer = minecraft.player.blockPosition();
        currentEvents = EclipsePredictionService.currentEvents(minecraft.level,
                observer.getCenter());
        displayedCurrentEventMask = apiEventMask;
        List<CelestialEventType> activeTypes = PlanetariumProjection.currentEventTypes(apiEventMask);
        if (activeTypes.isEmpty()) {
            displayedCurrentEvents = List.of();
            return;
        }
        List<CurrentEvent> display = new ArrayList<>(activeTypes.size());
        for (CelestialEventType type : activeTypes) {
            long end = currentEvents.nextChangeCalendarTicks();
            for (CurrentEvent event : currentEvents.events()) {
                if (event.type() == type) {
                    end = event.endCalendarTicks();
                    break;
                }
            }
            display.add(new CurrentEvent(type, end));
        }
        displayedCurrentEvents = List.copyOf(display);
    }

    private int currentApiEventMask() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return 0;
        }
        CelestialEventState apiEvents = CelestialApi.events(minecraft.level,
                minecraft.player.blockPosition()).orElse(null);
        return PlanetariumProjection.currentEventMask(apiEvents);
    }

    private Component calendarText(long calendarTicks) {
        int daysInMonth = minecraft != null && minecraft.level != null
                ? Calendars.get(minecraft.level).getCalendarDaysInMonth() : 8;
        long day = Math.floorDiv(calendarTicks, (long) CelestialMath.TICKS_IN_DAY);
        long ticksOfDay = Math.floorMod(calendarTicks, (long) CelestialMath.TICKS_IN_DAY);
        long daysInYear = Math.max(1L, daysInMonth * 12L);
        long year = Math.floorDiv(day, daysInYear) + 1L;
        long dayOfYear = Math.floorMod(day, daysInYear);
        long month = dayOfYear / Math.max(1, daysInMonth) + 1L;
        long monthDay = dayOfYear % Math.max(1, daysInMonth) + 1L;
        int totalMinutes = (int) Math.floor(ticksOfDay / CelestialMath.TICKS_IN_DAY * 1440.0D);
        return Component.translatable("screen.wildfires.planetarium.date", year, month, monthDay,
                String.format(Locale.ROOT, "%02d", totalMinutes / 60),
                String.format(Locale.ROOT, "%02d", totalMinutes % 60));
    }

    private static String clockTime(double fraction) {
        int minutes = (int) Math.round(clamp(fraction, 0.0D, 1.0D) * 1440.0D) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", clamp(value, 0.0D, 1.0D) * 100.0D);
    }

    private static String signedDegrees(double radians) {
        return String.format(Locale.ROOT, "%+.2f°", Math.toDegrees(radians));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record InformationLine(Component text, int color, boolean gap) {
        static InformationLine title(Component text) {
            return new InformationLine(text, PlanetariumProjection.INFO_TITLE_COLOR, false);
        }

        static InformationLine body(Component text, int color) {
            return new InformationLine(text, color, false);
        }

        static InformationLine spacer() {
            return new InformationLine(Component.empty(), 0, true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TimelinePointer(int x, int startY, int endY) {
    }

    private record EventMarker(int x, int y, int width, int height,
                               SolarPrediction solar, LunarPrediction lunar,
                               TimelineLunarMarkerKind lunarMarkerKind) {
        static EventMarker solar(int x, int y, SolarPrediction prediction) {
            return new EventMarker(x, y, TIMELINE_ICON_SIZE, TIMELINE_ICON_SIZE,
                    prediction, null, null);
        }

        static EventMarker lunar(int x, int y, LunarPrediction prediction,
                                 TimelineLunarMarkerKind markerKind) {
            return new EventMarker(x, y, TIMELINE_ICON_SIZE, TIMELINE_ICON_SIZE,
                    null, prediction, markerKind);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record PendingEventMarker(double calendarTicks, int kindOrder,
                                      SolarPrediction solar, LunarPrediction lunar,
                                      TimelineLunarMarkerKind lunarMarkerKind) {
        static PendingEventMarker solar(SolarPrediction prediction) {
            return new PendingEventMarker(prediction.greatestCalendarTicks(), 0,
                    prediction, null, null);
        }

        static PendingEventMarker lunar(LunarPrediction prediction,
                                        TimelineLunarMarkerKind markerKind) {
            int order = markerKind == TimelineLunarMarkerKind.ECLIPSE ? 2 : 3;
            return new PendingEventMarker(prediction.greatestCalendarTicks(), order,
                    null, prediction, markerKind);
        }

        long calendarDay() {
            return (long) Math.floor(calendarTicks / CelestialMath.TICKS_IN_DAY);
        }

        boolean upper() {
            return solar != null;
        }

        EventMarker place(int left, int lane) {
            int laneOffset = lane * (TIMELINE_ICON_SIZE
                    + PlanetariumProjection.TIMELINE_TRACK_GAP);
            if (solar != null) {
                return EventMarker.solar(left,
                        TIMELINE_Y - TIMELINE_POINTER_LENGTH - laneOffset
                                - TIMELINE_ICON_SIZE / 2, solar);
            }
            int y = TIMELINE_Y + TIMELINE_POINTER_LENGTH + laneOffset
                    - TIMELINE_ICON_SIZE / 2;
            return EventMarker.lunar(left, y, lunar, lunarMarkerKind);
        }
    }

    private static final class PendingDayGroup {
        private final long day;
        private final List<PendingEventMarker> upper = new ArrayList<>();
        private final List<PendingEventMarker> lower = new ArrayList<>();

        private PendingDayGroup(long day) {
            this.day = day;
        }

        void add(PendingEventMarker event) {
            (event.upper() ? upper : lower).add(event);
        }

        long day() {
            return day;
        }

        List<PendingEventMarker> upper() {
            return upper;
        }

        List<PendingEventMarker> lower() {
            return lower;
        }
    }
}
