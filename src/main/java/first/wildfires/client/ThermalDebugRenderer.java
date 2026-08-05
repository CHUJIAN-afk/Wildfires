package first.wildfires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.network.ThermalDebugRequestPacket;
import first.wildfires.thermal.ClientThermalState;
import first.wildfires.thermal.ThermalWorldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders bounded snapshots produced by the server-authoritative thermal manager. */
public final class ThermalDebugRenderer {

    private static final int SNAPSHOT_INTERVAL_TICKS = 20;
    private static final int MAX_LABELS_PER_LAYER = 1024;
    private static boolean enabled;
    private static boolean hiddenEnabled;
    private static int lastRequestTick = Integer.MIN_VALUE;
    private static int refreshRequestedTick = Integer.MIN_VALUE;
    private static Map<Long, Float> displayCells = Map.of();
    private static Map<Long, Float> displayHiddenCells = Map.of();
    private static List<ThermalWorldManager.SourceDebug> displaySources = List.of();
    private static List<ThermalWorldManager.SurfaceDebug> displaySurfaces = List.of();
    private static ThermalWorldManager.ThermalDiagnostics diagnostics =
            new ThermalWorldManager.ThermalDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L);

    private ThermalDebugRenderer() {
    }

    public static boolean toggle() {
        enabled = !enabled;
        if (enabled) {
            requestRefresh();
        } else {
            displayCells = Map.of();
            displaySources = List.of();
            displaySurfaces = List.of();
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggleHidden() {
        hiddenEnabled = !hiddenEnabled;
        if (hiddenEnabled) {
            requestRefresh();
        } else {
            displayHiddenCells = Map.of();
        }
        return hiddenEnabled;
    }

    public static boolean isHiddenEnabled() {
        return hiddenEnabled;
    }

    public static void requestRefresh() {
        Minecraft minecraft = Minecraft.getInstance();
        refreshRequestedTick = minecraft.player == null ? Integer.MIN_VALUE : minecraft.player.tickCount;
    }

    public static int refreshAndGetSourceCount() {
        requestSnapshot();
        return displaySources.size();
    }

    public static void acceptSnapshot(Map<Long, Float> cells,
                                      Map<Long, Float> hiddenCells,
                                      List<ThermalWorldManager.SourceDebug> sources,
                                      List<ThermalWorldManager.SurfaceDebug> surfaces,
                                      ThermalWorldManager.ThermalDiagnostics newDiagnostics) {
        displayCells = Map.copyOf(cells);
        displayHiddenCells = Map.copyOf(hiddenCells);
        displaySources = List.copyOf(sources);
        displaySurfaces = List.copyOf(surfaces);
        diagnostics = newDiagnostics;
    }

    public static String diagnosticsSummary() {
        return String.format(Locale.ROOT,
                "Section:%d  源:%d  暴露面:%d  Patch:%d  活跃格:%d  本tick格:%d  顺延Section:%d  射线:%d  命中:%d  旧缓存:%d  顺延射线:%d",
                diagnostics.sectionCount(), diagnostics.sourceCount(), diagnostics.exposedFaceCount(),
                diagnostics.radiantPatchCount(), diagnostics.activeCellCount(),
                diagnostics.processedCellsLastTick(), diagnostics.deferredSectionsLastTick(),
                diagnostics.raysThisTick(), diagnostics.radiationCacheHits(),
                diagnostics.staleRadiationCacheUses(), diagnostics.deferredRays());
    }

    public static void tick() {
        ClientThermalState.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ClientThermalState.clear();
            displayCells = Map.of();
            displayHiddenCells = Map.of();
            displaySources = List.of();
            displaySurfaces = List.of();
            return;
        }
        if ((!enabled && !hiddenEnabled)
                || !minecraft.player.getAbilities().instabuild) {
            return;
        }
        boolean requested = refreshRequestedTick != Integer.MIN_VALUE
                && minecraft.player.tickCount - refreshRequestedTick >= 5;
        if (requested || minecraft.player.tickCount - lastRequestTick >= SNAPSHOT_INTERVAL_TICKS) {
            requestSnapshot();
            refreshRequestedTick = Integer.MIN_VALUE;
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if ((!enabled && !hiddenEnabled) || minecraft.player == null || minecraft.level == null
                || !minecraft.player.getAbilities().instabuild) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-event.getCamera().getPosition().x, -event.getCamera().getPosition().y,
                -event.getCamera().getPosition().z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        if (enabled) {
            for (ThermalWorldManager.SourceDebug source : displaySources) {
                renderSourceLabel(poseStack, buffers, font, minecraft, source);
            }
            for (ThermalWorldManager.SurfaceDebug surface : displaySurfaces) {
                renderSurfaceLabel(poseStack, buffers, font, minecraft, surface);
            }
            renderCells(poseStack, buffers, font, minecraft, displayCells, "", 0xFFFFD900);
        }
        if (hiddenEnabled) {
            renderCells(poseStack, buffers, font, minecraft, displayHiddenCells, "H ", 0xFF40FF40);
        }
        buffers.endBatch();
        poseStack.popPose();
    }

    private static void requestSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getAbilities().instabuild) {
            return;
        }
        new ThermalDebugRequestPacket(enabled, hiddenEnabled).sendToServer();
        lastRequestTick = minecraft.player.tickCount;
    }

    private static void renderCells(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Font font,
                                    Minecraft minecraft, Map<Long, Float> cells, String prefix, int color) {
        int labels = 0;
        for (Map.Entry<Long, Float> entry : cells.entrySet()) {
            if (labels++ >= MAX_LABELS_PER_LAYER) {
                break;
            }
            BlockPos position = BlockPos.of(entry.getKey());
            poseStack.pushPose();
            poseStack.translate(position.getX() + 0.5D, position.getY() + 1.05D, position.getZ() + 0.5D);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.016F, -0.016F, 0.016F);
            String label = prefix + String.format(Locale.ROOT, "%.2f", entry.getValue());
            font.drawInBatch(label, -font.width(label) / 2.0F, 0.0F, color, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
            poseStack.popPose();
        }
    }

    private static void renderSourceLabel(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Font font,
                                          Minecraft minecraft, ThermalWorldManager.SourceDebug source) {
        BlockPos position = BlockPos.of(source.position());
        BlockState state = minecraft.level.getBlockState(position);
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        String properties = state.getValues().entrySet().stream()
                .map(entry -> entry.getKey().getName() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        String description = properties.isEmpty() ? blockId : blockId + " | " + properties;
        String temperature = String.format(Locale.ROOT, "%.1f WTU", source.temperature());
        poseStack.pushPose();
        poseStack.translate(position.getX() + 0.5D, position.getY() + 1.35D, position.getZ() + 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.016F, -0.016F, 0.016F);
        font.drawInBatch(description, -font.width(description) / 2.0F, 0.0F, 0xFFFF8C00, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
        font.drawInBatch(temperature, -font.width(temperature) / 2.0F, 10.0F, 0xFFFFD900, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
        poseStack.popPose();
    }

    private static void renderSurfaceLabel(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Font font,
                                           Minecraft minecraft, ThermalWorldManager.SurfaceDebug surface) {
        Direction direction = Direction.from3DDataValue(surface.direction());
        BlockPos source = BlockPos.of(surface.sourcePosition());
        String label = String.format(Locale.ROOT, "%s %.1f", direction.getName(), surface.temperature());
        poseStack.pushPose();
        poseStack.translate(source.getX() + 0.5D + direction.getStepX() * 0.505D,
                source.getY() + 0.5D + direction.getStepY() * 0.505D,
                source.getZ() + 0.5D + direction.getStepZ() * 0.505D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.012F, -0.012F, 0.012F);
        font.drawInBatch(label, -font.width(label) / 2.0F, 0.0F, 0xFF40E0FF, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
        poseStack.popPose();
    }
}
