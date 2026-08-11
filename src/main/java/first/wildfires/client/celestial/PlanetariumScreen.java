package first.wildfires.client.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialEventState;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.CelestialEventType;
import first.wildfires.celestial.EclipsePredictionService;
import first.wildfires.celestial.EclipsePredictionService.CurrentEvent;
import first.wildfires.celestial.EclipsePredictionService.CurrentEvents;
import first.wildfires.celestial.EclipsePredictionService.LunarPhaseKind;
import first.wildfires.celestial.EclipsePredictionService.LunarPhasePrediction;
import first.wildfires.celestial.EclipsePredictionService.LunarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Predictions;
import first.wildfires.celestial.EclipsePredictionService.SolarPrediction;
import first.wildfires.celestial.EclipsePredictionService.Timeline;
import first.wildfires.client.celestial.PlanetariumProjection.MapDragState;
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

    private static final int CANVAS_WIDTH = 1024;
    private static final int CANVAS_HEIGHT = 576;
    private static final int CHART_X = 112;
    private static final int CHART_Y = 196;
    private static final int CHART_WIDTH = 464;
    private static final int CHART_HEIGHT = 276;
    private static final int MAP_CROSSHAIR_RADIUS =
            PlanetariumProjection.MAP_SELECTION_CURSOR_RADIUS;
    private static final int MAP_CROSSHAIR_HOVER_RADIUS =
            PlanetariumProjection.MAP_SELECTION_HOVER_RADIUS;
    private static final int MAP_PLAYER_CURSOR_RADIUS =
            PlanetariumProjection.MAP_PLAYER_CURSOR_RADIUS;
    private static final int MAP_SELECTION_COLOR = PlanetariumProjection.MAP_SELECTION_COLOR;
    private static final int MAP_PLAYER_CURSOR_COLOR =
            PlanetariumProjection.MAP_PLAYER_CURSOR_COLOR;
    private static final int CLOCK_X = 828;
    private static final int CLOCK_Y = 212;
    private static final int CLOCK_RADIUS = 88;
    private static final int TIMELINE_X = 128;
    private static final int TIMELINE_Y = 72;
    private static final int TIMELINE_WIDTH = 632;
    private static final int TIMELINE_ICON_SIZE = PlanetariumProjection.TIMELINE_ICON_SIZE;
    private static final int TIMELINE_DISC_SOURCE_SIZE = PlanetariumProjection.TIMELINE_DISC_SOURCE_SIZE;
    private static final int TIMELINE_DISC_SOURCE_U = PlanetariumProjection.TIMELINE_DISC_SOURCE_U;
    private static final int TIMELINE_DISC_SOURCE_V = PlanetariumProjection.TIMELINE_DISC_SOURCE_V;
    private static final int TIMELINE_NEW_MOON_SOURCE_V =
            PlanetariumProjection.TIMELINE_NEW_MOON_SOURCE_V;
    private static final int TIMELINE_POINTER_LENGTH = PlanetariumProjection.TIMELINE_POINTER_LENGTH;
    private static final int TIMELINE_POINTER_WIDTH = PlanetariumProjection.TIMELINE_POINTER_WIDTH;
    private static final int TIMELINE_POINTER_COLOR = PlanetariumProjection.TIMELINE_POINTER_COLOR;
    private static final int TIMELINE_LABEL_Y = PlanetariumProjection.TIMELINE_LABEL_Y;
    private static final int POINTER_TEXTURE_SIZE = PlanetariumProjection.POINTER_TEXTURE_SIZE;
    private static final double TIMELINE_DAYS = 400.0D;
    private static final int[] ECLIPSE_BAND_COLORS = {
            0x553A7180, 0x66566675, 0x77735863, 0x99884A4D, 0xBBA02F39
    };
    private static final ResourceLocation BACKGROUND = Wildfires.rl(
            "textures/gui/planetarium/planetarium_background.png");
    private static final ResourceLocation DAY_DISC = Wildfires.rl(
            "textures/gui/planetarium/planetarium_day_disc.png");
    private static final ResourceLocation NIGHT_DISC = Wildfires.rl(
            "textures/gui/planetarium/planetarium_night_disc.png");
    private static final ResourceLocation TIME_POINTER = Wildfires.rl(
            "textures/gui/planetarium/planetarium_time_pointer.png");
    private static final ResourceLocation TIMELINE_SUN = ResourceLocation.parse(
            PlanetariumProjection.TIMELINE_SUN_TEXTURE);
    private static final ResourceLocation TIMELINE_FULL_MOON = ResourceLocation.parse(
            PlanetariumProjection.TIMELINE_FULL_MOON_TEXTURE);

    private Predictions predictions;
    private Timeline timeline;
    private SolarPrediction selectedSolar = SolarPrediction.NONE;
    private LunarPrediction selectedLunar = LunarPrediction.NONE;
    private LunarPhasePrediction selectedPhase = LunarPhasePrediction.NONE;
    private TimelineLunarMarkerKind selectedLunarMarkerKind;
    private MapDragState mapDragState = MapDragState.NONE;
    private CurrentEvents currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
    private List<CurrentEvent> displayedCurrentEvents = List.of();
    private double[] selectedCoverage = new double[CHART_HEIGHT - 2];
    private long predictionCalendarTick = Long.MIN_VALUE;
    private final List<EventMarker> markers = new ArrayList<>();
    private float canvasScale = 1.0F;
    private int canvasX;
    private int canvasY;

    PlanetariumScreen() {
        super(Component.translatable("screen.wildfires.planetarium.title"));
    }

    @Override
    protected void init() {
        updateCanvasTransform();
        refreshPredictions();
    }

    private void updateCanvasTransform() {
        canvasScale = Math.min(width / (float) CANVAS_WIDTH, height / (float) CANVAS_HEIGHT);
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
        } else if (current >= currentEvents.nextChangeCalendarTicks()
                || !displayedCurrentEvents.stream().map(CurrentEvent::type).toList()
                .equals(currentApiEventTypes())) {
            refreshCurrentEvents();
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
        graphics.drawCenteredString(font, title, CANVAS_WIDTH / 2, 14, 0xFFF4D786);

        if (state == null || predictions == null || timeline == null) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.wildfires.planetarium.unavailable"),
                    CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2, 0xFFFF7777);
        } else {
            drawTimeline(graphics);
            drawEclipseMap(graphics, state);
            drawClock(graphics, state);
            drawInformation(graphics, state);
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
        markers.clear();
        List<PlanetariumProjection.TimelineLunarMarker> lunarMarkers =
                PlanetariumProjection.timelineLunarMarkers(timeline.lunar());
        graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.timeline"),
                105, 27, 0xFFF3D68A, false);
        for (int day = 0; day <= 400; day += 50) {
            int x = TIMELINE_X + (int) Math.round(TIMELINE_WIDTH * day / TIMELINE_DAYS);
            graphics.drawCenteredString(font, Integer.toString(day), x, TIMELINE_LABEL_Y,
                    0xFF4A2B18);
        }

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
        List<PlanetariumProjection.TimelineDaySeed> seeds = dayGroups.stream()
                .map(group -> new PlanetariumProjection.TimelineDaySeed(group.day(),
                        timelineX((group.day() + 0.5D) * CelestialMath.TICKS_IN_DAY),
                        group.upper().size(), group.lower().size()))
                .toList();
        List<PlanetariumProjection.TimelineDayLayout> layouts =
                PlanetariumProjection.timelineDayLayouts(seeds,
                TIMELINE_ICON_SIZE, PlanetariumProjection.TIMELINE_TRACK_GAP,
                TIMELINE_X - TIMELINE_ICON_SIZE / 2,
                TIMELINE_X + TIMELINE_WIDTH + TIMELINE_ICON_SIZE / 2);
        List<EventMarker> ordered = new ArrayList<>(pending.size() + timeline.phases().size());
        for (LunarPhasePrediction phase : timeline.phases()) {
            PlanetariumProjection.TimelineAxisMarker axisMarker =
                    PlanetariumProjection.timelineAxisMarker(timelineX(phase.calendarTicks()),
                            TIMELINE_Y, TIMELINE_ICON_SIZE);
            ordered.add(EventMarker.phase(axisMarker.left(), axisMarker.top(), phase));
        }
        for (int groupIndex = 0; groupIndex < dayGroups.size(); groupIndex++) {
            PendingDayGroup group = dayGroups.get(groupIndex);
            PlanetariumProjection.TimelineDayLayout layout = layouts.get(groupIndex);
            for (int index = 0; index < group.upper().size(); index++) {
                ordered.add(group.upper().get(index).place(layout.upperLefts().get(index)));
            }
            for (int index = 0; index < group.lower().size(); index++) {
                ordered.add(group.lower().get(index).place(layout.lowerLefts().get(index)));
            }
            int pointerStartY = group.upper().isEmpty()
                    ? TIMELINE_Y : TIMELINE_Y - TIMELINE_POINTER_LENGTH;
            int pointerEndY = group.lower().isEmpty()
                    ? TIMELINE_Y : TIMELINE_Y + TIMELINE_POINTER_LENGTH;
            drawTimelinePointer(graphics, layout.pointerX(), pointerStartY, pointerEndY);
        }
        for (EventMarker marker : ordered) {
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
            } else {
                boolean selected = selectedPhase.present()
                        && selectedPhase.phaseIndex() == marker.phase().phaseIndex()
                        && selectedPhase.kind() == marker.phase().kind();
                drawMiniPhaseMoon(graphics, centerX, centerY, marker.phase().kind(), selected);
            }
            markers.add(marker);
        }
    }

    private int timelineX(double calendarTicks) {
        double progress = PlanetariumProjection.timelineProgress(timeline, calendarTicks);
        return TIMELINE_X + (int) Math.round(progress * TIMELINE_WIDTH);
    }

    private void drawMiniSun(GuiGraphics graphics, int centerX, int centerY, boolean selected) {
        drawSelectedMarkerBorder(graphics, centerX, centerY, selected);
        drawVanillaTimelineDisc(graphics, TIMELINE_SUN, centerX, centerY,
                1.0F, 1.0F, 1.0F, 32, 32);
    }

    private void drawMiniMoon(GuiGraphics graphics, int centerX, int centerY,
                              LunarPrediction lunar, TimelineLunarMarkerKind markerKind,
                              boolean selected) {
        drawSelectedMarkerBorder(graphics, centerX, centerY, selected);
        PlanetariumProjection.TimelineMoonTint tint =
                PlanetariumProjection.timelineMoonTint(lunar, markerKind);
        drawVanillaTimelineDisc(graphics, TIMELINE_FULL_MOON, centerX, centerY,
                tint.red(), tint.green(), tint.blue(), 128, 64);
    }

    private void drawMiniPhaseMoon(GuiGraphics graphics, int centerX, int centerY,
                                   LunarPhaseKind phaseKind, boolean selected) {
        drawSelectedMarkerBorder(graphics, centerX, centerY, selected);
        int sourceV = phaseKind == LunarPhaseKind.NEW_MOON
                ? TIMELINE_NEW_MOON_SOURCE_V : TIMELINE_DISC_SOURCE_V;
        drawVanillaTimelineDisc(graphics, TIMELINE_FULL_MOON, centerX, centerY,
                sourceV, 1.0F, 1.0F, 1.0F, 128, 64);
    }

    private static void drawSelectedMarkerBorder(GuiGraphics graphics, int centerX, int centerY,
                                                 boolean selected) {
        if (!selected) {
            return;
        }
        int edge = 0xFFFFE86D;
        int half = TIMELINE_ICON_SIZE / 2 + 1;
        graphics.fill(centerX - half, centerY - half, centerX + half + 1,
                centerY - half + 1, edge);
        graphics.fill(centerX - half, centerY + half, centerX + half + 1,
                centerY + half + 1, edge);
        graphics.fill(centerX - half, centerY - half, centerX - half + 1,
                centerY + half + 1, edge);
        graphics.fill(centerX + half, centerY - half, centerX + half + 1,
                centerY + half + 1, edge);
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
        graphics.blit(texture, centerX - TIMELINE_ICON_SIZE / 2,
                centerY - TIMELINE_ICON_SIZE / 2,
                TIMELINE_DISC_SOURCE_U, sourceV,
                TIMELINE_DISC_SOURCE_SIZE, TIMELINE_DISC_SOURCE_SIZE,
                textureWidth, textureHeight);
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

        Component selected = selectedSolar.present()
                ? Component.translatable("screen.wildfires.planetarium.map_selected",
                calendarText((long) selectedSolar.greatestCalendarTicks()))
                : Component.translatable("screen.wildfires.planetarium.none");
        graphics.drawString(font, selected, 80, 168, 0xFF4B2E1C, false);
        graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.map_player",
                        signedDegrees(longitude), signedDegrees(latitude)),
                80, 181, 0xFF315C70, false);
        drawBandLegend(graphics);
    }

    private static void drawMapCursor(GuiGraphics graphics, int centerX, int centerY,
                                      int radius, int color) {
        graphics.fill(centerX - radius, centerY, centerX + radius + 1, centerY + 1, color);
        graphics.fill(centerX, centerY - radius, centerX + 1, centerY + radius + 1, color);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFFFFFFF);
    }

    private void drawChartLabels(GuiGraphics graphics) {
        int[] longitudeDegrees = {-180, -90, 0, 90, 180};
        for (int i = 0; i < longitudeDegrees.length; i++) {
            int x = CHART_X + (int) Math.round(CHART_WIDTH * i / 4.0D);
            graphics.drawCenteredString(font, longitudeDegrees[i] + "°", x, CHART_Y + CHART_HEIGHT + 3,
                    0xFF4B321F);
        }
        int[] latitudeDegrees = {90, 45, 0, -45, -90};
        for (int i = 0; i < latitudeDegrees.length; i++) {
            int y = CHART_Y + (int) Math.round(CHART_HEIGHT * i / 4.0D) - 4;
            graphics.drawString(font, latitudeDegrees[i] + "°", CHART_X - 27, y,
                    0xFF4B321F, false);
        }
    }

    private void drawBandLegend(GuiGraphics graphics) {
        String[] labels = {"0–20", "20–40", "40–60", "60–80", "80–100"};
        int x = 87;
        for (int i = 0; i < labels.length; i++) {
            graphics.fill(x, 500, x + 13, 508, ECLIPSE_BAND_COLORS[i] | 0xFF000000);
            graphics.drawString(font, labels[i], x + 16, 499, 0xFF4B321F, false);
            x += 91;
        }
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
        graphics.pose().translate(CLOCK_X, CLOCK_Y, 8.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)
                (PlanetariumClock.pointerFraction(state.fractionOfDay()) * 360.0D)));
        graphics.blit(TIME_POINTER, -POINTER_TEXTURE_SIZE / 2, -POINTER_TEXTURE_SIZE / 2,
                0, 0, POINTER_TEXTURE_SIZE, POINTER_TEXTURE_SIZE,
                POINTER_TEXTURE_SIZE, POINTER_TEXTURE_SIZE);
        graphics.pose().popPose();
        graphics.drawCenteredString(font, percent(schedule.dayFraction()), CLOCK_X,
                CLOCK_Y + 98, 0xFFF0D185);
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
            sectorVertex(builder, matrix, CLOCK_X, CLOCK_Y, 0.5F, 0.5F);
            sectorOuterVertex(builder, matrix, first);
            sectorOuterVertex(builder, matrix, second);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
    }

    private static void sectorOuterVertex(BufferBuilder builder, Matrix4f matrix, double fraction) {
        double angle = fraction * CelestialMath.TAU - Math.PI * 0.5D;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        sectorVertex(builder, matrix,
                (float) (CLOCK_X + cosine * CLOCK_RADIUS),
                (float) (CLOCK_Y + sine * CLOCK_RADIUS),
                (float) (0.5D + cosine * 0.5D),
                (float) (0.5D + sine * 0.5D));
    }

    private static void sectorVertex(BufferBuilder builder, Matrix4f matrix,
                                     float x, float y, float u, float v) {
        builder.vertex(matrix, x, y, 2.0F).uv(u, v).endVertex();
    }

    private void drawInformation(GuiGraphics graphics, CelestialState state) {
        int x = 680;
        int y = 365;
        graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.current"),
                x, y, 0xFFF0D185, false);
        graphics.drawString(font, calendarText(state.calendarTicks()), x, y + 14,
                0xFFD8DFE7, false);
        graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.coordinates",
                        signedDegrees(currentLongitude()), signedDegrees(state.latitudeRadians())),
                x, y + 27, 0xFF9FD6E8, false);

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
        graphics.drawString(font, clockText, x, y + 40, 0xFFE4C477, false);

        if (selectedSolar.present()) {
            graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.coverage",
                            percent(selectedSolar.globalMaximumCoverage()),
                            percent(selectedSolar.observerMaximumCoverage())),
                    x, y + 57, 0xFFE7A16B, false);
        }
        if (selectedPhase.present()) {
            String phaseKey = selectedPhase.kind() == LunarPhaseKind.FULL_MOON
                    ? "screen.wildfires.planetarium.timeline.full_moon"
                    : "screen.wildfires.planetarium.timeline.new_moon";
            graphics.drawString(font, Component.translatable(
                            "screen.wildfires.planetarium.selected_phase",
                            Component.translatable(phaseKey)),
                    x, y + 74, 0xFFF0F0F0, false);
            graphics.drawString(font, calendarText((long) selectedPhase.calendarTicks()),
                    x, y + 87, 0xFFD8DFE7, false);
        } else if (selectedLunar.present()) {
            LunarPrediction lunar = selectedLunar;
            graphics.drawString(font, Component.translatable(
                            "screen.wildfires.planetarium.selected_lunar"),
                    x, y + 74, 0xFFD89BE3, false);
            graphics.drawString(font, calendarText((long) lunar.greatestCalendarTicks()),
                    x, y + 87, 0xFFD8DFE7, false);
            if (selectedLunarMarkerKind == TimelineLunarMarkerKind.SUPERMOON) {
                graphics.drawString(font, Component.translatable(
                                "screen.wildfires.planetarium.supermoon_strength",
                                percent(lunar.supermoonIntensity())),
                        x, y + 100, 0xFF9FCBFF, false);
            } else if (lunar.eclipse()) {
                graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.lunar_shape",
                                Component.translatable("screen.wildfires.planetarium.lunar."
                                        + lunar.kind().name().toLowerCase(Locale.ROOT)),
                                percent(lunar.displayMaximumCoverage())),
                        x, y + 100, 0xFFC2C9D2, false);
            } else if (lunar.supermoon()) {
                graphics.drawString(font, Component.translatable(
                                "screen.wildfires.planetarium.supermoon_strength",
                                percent(lunar.supermoonIntensity())),
                        x, y + 100, 0xFF9FCBFF, false);
            }
        }

        graphics.drawString(font, Component.translatable("screen.wildfires.planetarium.next_lunar"),
                x, y + 114, 0xFFD89BE3, false);
        Component nextLunar = predictions.lunar().present()
                ? calendarText((long) predictions.lunar().greatestCalendarTicks())
                : Component.translatable("screen.wildfires.planetarium.none");
        graphics.drawString(font, nextLunar, x, y + 127, 0xFFD8DFE7, false);

        graphics.drawString(font, Component.translatable(
                        "screen.wildfires.planetarium.current_events"),
                x, y + 143, 0xFFF0D185, false);
        if (displayedCurrentEvents.isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "screen.wildfires.planetarium.current_events.none"),
                    x, y + 156, 0xFFD8DFE7, false);
        } else {
            for (int index = 0; index < displayedCurrentEvents.size(); index++) {
                CurrentEvent event = displayedCurrentEvents.get(index);
                graphics.drawString(font, Component.translatable(
                                "screen.wildfires.planetarium.current_events.line",
                                currentEventName(event.type()),
                                calendarText(event.endCalendarTicks())),
                        x, y + 156 + index * 12, currentEventColor(event.type()), false);
            }
        }
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
            case SOLAR_ECLIPSE -> 0xFFFFC76A;
            case LUNAR_ECLIPSE -> 0xFFE2A0D9;
            case BLOOD_MOON -> 0xFFE15B54;
            case SUPERMOON -> 0xFF9FCBFF;
            default -> 0xFFF0F0F0;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            mapDragState = mapDragState.release();
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
            selectedPhase = LunarPhasePrediction.NONE;
            return true;
        }
        if (marker != null && marker.phase() != null) {
            selectedPhase = marker.phase();
            return true;
        }
        if (button == 0) {
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = button == 0 && mapDragState.dragging();
        if (button == 0) {
            mapDragState = mapDragState.release();
        }
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    private EventMarker markerAt(double x, double y) {
        for (int index = markers.size() - 1; index >= 0; index--) {
            EventMarker marker = markers.get(index);
            if (marker.contains(x, y)) {
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
        } else if (marker.phase() != null) {
            LunarPhasePrediction phase = marker.phase();
            String titleKey = phase.kind() == LunarPhaseKind.FULL_MOON
                    ? "screen.wildfires.planetarium.timeline.full_moon"
                    : "screen.wildfires.planetarium.timeline.new_moon";
            lines.add(Component.translatable(titleKey).withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable("screen.wildfires.planetarium.timeline.time",
                    calendarText((long) phase.calendarTicks())));
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
        return CelestialClientStateCache.state(minecraft.level, minecraft.player.position(), partialTick)
                .orElse(null);
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
            selectedPhase = LunarPhasePrediction.NONE;
            selectedLunarMarkerKind = null;
            currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
            displayedCurrentEvents = List.of();
            rebuildCoverageProfile();
            return;
        }
        long selectedIndex = selectedSolar.present() ? selectedSolar.conjunctionIndex() : Long.MIN_VALUE;
        long selectedFullMoon = selectedLunar.present() ? selectedLunar.fullMoonIndex() : Long.MIN_VALUE;
        long selectedPhaseIndex = selectedPhase.present()
                ? selectedPhase.phaseIndex() : Long.MIN_VALUE;
        LunarPhaseKind selectedPhaseKind = selectedPhase.present() ? selectedPhase.kind() : null;
        predictionCalendarTick = Calendars.get(minecraft.level).getCalendarTicks();
        Vec3 observer = minecraft.player.position();
        predictions = EclipsePredictionService.predict(minecraft.level, observer);
        timeline = EclipsePredictionService.predictTimeline(minecraft.level, observer, TIMELINE_DAYS);
        selectedSolar = PlanetariumProjection.selectSolar(timeline, selectedIndex);
        selectedLunar = PlanetariumProjection.selectLunar(timeline, selectedFullMoon);
        selectedPhase = PlanetariumProjection.selectPhase(timeline, selectedPhaseIndex,
                selectedPhaseKind);
        if (!selectedLunar.present()) {
            selectedLunarMarkerKind = null;
        } else if (selectedLunarMarkerKind != null
                && !PlanetariumProjection.timelineLunarMarkerKinds(selectedLunar)
                .contains(selectedLunarMarkerKind)) {
            selectedLunarMarkerKind = PlanetariumProjection.timelineLunarMarkerKinds(selectedLunar)
                    .stream().findFirst().orElse(null);
        }
        if (!selectedSolar.present() && !timeline.solar().isEmpty()) {
            selectedSolar = timeline.solar().get(0);
        }
        rebuildCoverageProfile();
        refreshCurrentEvents();
    }

    private void refreshCurrentEvents() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            currentEvents = new CurrentEvents(List.of(), Long.MIN_VALUE);
            displayedCurrentEvents = List.of();
            return;
        }
        var observer = minecraft.player.blockPosition();
        currentEvents = EclipsePredictionService.currentEvents(minecraft.level,
                observer.getCenter());
        List<CelestialEventType> activeTypes = currentApiEventTypes();
        if (activeTypes.isEmpty()) {
            displayedCurrentEvents = List.of();
            return;
        }
        List<CurrentEvent> display = new ArrayList<>(activeTypes.size());
        for (CelestialEventType type : activeTypes) {
            long end = currentEvents.events().stream()
                    .filter(event -> event.type() == type)
                    .mapToLong(CurrentEvent::endCalendarTicks)
                    .findFirst().orElse(currentEvents.nextChangeCalendarTicks());
            display.add(new CurrentEvent(type, end));
        }
        displayedCurrentEvents = List.copyOf(display);
    }

    private List<CelestialEventType> currentApiEventTypes() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return List.of();
        }
        CelestialEventState apiEvents = CelestialApi.events(minecraft.level,
                minecraft.player.blockPosition()).orElse(null);
        return PlanetariumProjection.currentEventTypes(apiEvents);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record EventMarker(int x, int y, int width, int height,
                               SolarPrediction solar, LunarPrediction lunar,
                               LunarPhasePrediction phase,
                               TimelineLunarMarkerKind lunarMarkerKind) {
        static EventMarker solar(int x, int y, SolarPrediction prediction) {
            return new EventMarker(x, y, TIMELINE_ICON_SIZE, TIMELINE_ICON_SIZE,
                    prediction, null, null, null);
        }

        static EventMarker lunar(int x, int y, LunarPrediction prediction,
                                 TimelineLunarMarkerKind markerKind) {
            return new EventMarker(x, y, TIMELINE_ICON_SIZE, TIMELINE_ICON_SIZE,
                    null, prediction, null, markerKind);
        }

        static EventMarker phase(int x, int y, LunarPhasePrediction prediction) {
            return new EventMarker(x, y, TIMELINE_ICON_SIZE, TIMELINE_ICON_SIZE,
                    null, null, prediction, null);
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

        EventMarker place(int left) {
            if (solar != null) {
                return EventMarker.solar(left,
                        TIMELINE_Y - TIMELINE_POINTER_LENGTH - TIMELINE_ICON_SIZE / 2, solar);
            }
            int y = TIMELINE_Y + TIMELINE_POINTER_LENGTH - TIMELINE_ICON_SIZE / 2;
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
