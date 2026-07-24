package first.wildfires.client;

import first.wildfires.thermal.ThermalSourceRegistry;
import first.wildfires.thermal.ThermalGrid;
import first.wildfires.thermal.SimpleThermalField;
import first.wildfires.thermal.ComplexThermalField;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class ThermalDebugRenderer {

    private static final int SCAN_RADIUS = 32;
    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final int LABEL_INTERVAL = 1;
    private static final int LABEL_HALF_EXTENT = 12;
    private static final int MAX_LABELS = 8192;
    private static int lastScanTick = Integer.MIN_VALUE;
    private static int refreshRequestedTick = Integer.MIN_VALUE;
    private static BlockPos lastGridCenter;
    private static boolean enabled;
    private static final java.util.List<SourceVisual> SOURCES = new java.util.ArrayList<>();
    private static Map<Long, Float> displayCells = Map.of();
    private static Map<Long, Float> displayReflectedCells = Map.of();

    private ThermalDebugRenderer() {
    }

    public static boolean toggle() {
        enabled = !enabled;
        if (enabled) {
            requestRefresh();
        }
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void requestRefresh() {
        Minecraft minecraft = Minecraft.getInstance();
        refreshRequestedTick = minecraft.player == null ? Integer.MIN_VALUE : minecraft.player.tickCount;
    }

    public static int refreshAndGetSourceCount() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return 0;
        }
        updateSources(minecraft.level, minecraft.player.blockPosition(), minecraft.player.tickCount);
        SimpleThermalField.get(minecraft.level, minecraft.player.blockPosition());
        ThermalGrid.rebuildAround(minecraft.level, minecraft.player.blockPosition());
        updateDisplayCells(minecraft.level);
        return SOURCES.size();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null || minecraft.level == null || !minecraft.player.getAbilities().instabuild) {
            return;
        }

        boolean refreshReady = refreshRequestedTick != Integer.MIN_VALUE
                && minecraft.player.tickCount - refreshRequestedTick >= 5;
        if (lastGridCenter == null
                || lastGridCenter.distManhattan(minecraft.player.blockPosition()) > 2
                || minecraft.player.tickCount - lastScanTick >= SCAN_INTERVAL_TICKS
                || refreshReady) {
            SimpleThermalField.get(minecraft.level, minecraft.player.blockPosition());
            ThermalGrid.rebuildAround(minecraft.level, minecraft.player.blockPosition());
            lastGridCenter = minecraft.player.blockPosition().immutable();
            refreshRequestedTick = Integer.MIN_VALUE;
            lastScanTick = minecraft.player.tickCount;
            updateDisplayCells(minecraft.level);
        }
        if ((minecraft.player.tickCount & 1) != 0) {
            return;
        }

    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null || minecraft.level == null || !minecraft.player.getAbilities().instabuild) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-event.getCamera().getPosition().x, -event.getCamera().getPosition().y, -event.getCamera().getPosition().z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        BlockPos playerPosition = minecraft.player.blockPosition();
        int labels = 0;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int distance = 0; distance <= LABEL_HALF_EXTENT && labels < MAX_LABELS; distance++) {
            for (int x = -distance; x <= distance && labels < MAX_LABELS; x++) {
                for (int y = -distance; y <= distance && labels < MAX_LABELS; y++) {
                    for (int z = -distance; z <= distance && labels < MAX_LABELS; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != distance) {
                            continue;
                        }
                        position.set(playerPosition.getX() + x, playerPosition.getY() + y, playerPosition.getZ() + z);
                        long key = position.asLong();
                        Float temperature = displayCells.get(key);
                        if (temperature == null
                                || Math.floorMod(position.getX(), LABEL_INTERVAL) != 0
                                || Math.floorMod(position.getY(), LABEL_INTERVAL) != 0
                                || Math.floorMod(position.getZ(), LABEL_INTERVAL) != 0) {
                            continue;
                        }
                        BlockState state = minecraft.level.getBlockState(position);
                        if (!state.isAir() && !state.getCollisionShape(minecraft.level, position).isEmpty()) {
                            continue;
                        }
                        poseStack.pushPose();
                        poseStack.translate(position.getX() + 0.5D, position.getY() + 1.05D, position.getZ() + 0.5D);
                        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
                        poseStack.scale(-0.016F, -0.016F, 0.016F);
                        float reflected = displayReflectedCells.getOrDefault(key, 0.0F);
                        String label = Math.abs(reflected) > 0.05F
                                ? String.format(Locale.ROOT, "%.1f R%+.1f", temperature, reflected)
                                : String.format(Locale.ROOT, "%.1f", temperature);
                        font.drawInBatch(label, -font.width(label) / 2.0F, 0.0F, 0xFFFFD900, false,
                                poseStack.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0, 0x00F000F0);
                        poseStack.popPose();
                        labels++;
                    }
                }
            }
        }
        buffers.endBatch();
        poseStack.popPose();
    }

    private static void updateSources(Level level, BlockPos center, int currentTick) {
        SOURCES.clear();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    position.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(position);
                    ThermalSourceRegistry.ThermalSourceDefinition definition = ThermalSourceRegistry.getDefinition(state);
                    if (definition != null) {
                        SOURCES.add(new SourceVisual(position.immutable(), definition.radiationRadius(),
                                ThermalSourceRegistry.getRadiationTemperature(level, position, state)));
                    }
                }
            }
        }
        lastScanTick = currentTick;
    }

    private static void updateDisplayCells(Level level) {
        java.util.List<BlockPos> activeCenters = java.util.List.of(Minecraft.getInstance().player.blockPosition());
        SimpleThermalField.prune(level, activeCenters);
        ComplexThermalField.prune(level, activeCenters);
        Map<Long, Float> cells = new HashMap<>(ThermalGrid.snapshot(level));
        for (Map.Entry<Long, Float> simpleCell : SimpleThermalField.snapshot(level).entrySet()) {
            cells.merge(simpleCell.getKey(), simpleCell.getValue(), Float::sum);
        }
        displayCells = Map.copyOf(cells);
        displayReflectedCells = ThermalGrid.reflectionSnapshot(level);
    }

    private record SourceVisual(BlockPos position, int radius, float temperature) {
    }
}
