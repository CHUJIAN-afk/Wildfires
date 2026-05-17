package first.wildfires.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import first.wildfires.kinetic.loom.*;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.register.BlockEntityRegister;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.PartialModelRegister;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * JEI动画渲染器 - 组合渲染控制块、辅助块和结构块
 * 使用静态GeoLib模型渲染和Flywheel旋转动画
 */
public class AnimatedLoom {

    // 用于JEI渲染的虚拟方块实体
    private static LoomControlBlockEntity dummyControlEntity;
    private static LoomAuxiliaryBlockEntity dummyAuxiliaryEntity;

    // 渲染参数 - 可以热重载调试
    private static float rotationX = 30f;  // X轴旋转角度
    private static float rotationY = 135f; // Y轴旋转角度
    private static float scale = 20f;      // 缩放比例
    private static float zOffset = 200f;   // Z轴偏移（渲染层级）

    /**
     * 初始化JEI渲染所需的虚拟方块实体
     */
    private static void initDummyEntities() {
        if (dummyControlEntity == null) {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                BlockState controlState = BlockRegister.LoomControlBlock.get().defaultBlockState();
                dummyControlEntity = new LoomControlBlockEntity(
                        BlockEntityRegister.LoomControlBlockEntity.get(),
                        BlockPos.ZERO,
                        controlState
                );
                dummyControlEntity.setLevel(level);
            }
        }
        if (dummyAuxiliaryEntity == null) {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                BlockState auxiliaryState = BlockRegister.LoomAuxiliaryBlock.get().defaultBlockState();
                dummyAuxiliaryEntity = new LoomAuxiliaryBlockEntity(
                        BlockEntityRegister.LoomAuxiliaryBlockEntity.get(),
                        BlockPos.ZERO,
                        auxiliaryState
                );
                dummyAuxiliaryEntity.setLevel(level);
            }
        }
    }

    /**
     * 渲染完整的织布机动画
     */
    public static void render(GuiGraphics guiGraphics, WeavingRecipe recipe, int x, int y) {
        initDummyEntities();

        setScale(30);
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // 移动到渲染位置（旋转中心）
        poseStack.translate(x + 60, y + 80, 300);

        // 应用等轴视角旋转 - 围绕模型中心旋转
        poseStack.mulPose(Axis.XP.rotationDegrees(-30));
        poseStack.mulPose(Axis.YP.rotationDegrees(135));

        poseStack.pushPose();
        poseStack.translate(0, 0, scale);
        // 渲染结构块（旋转轴和线轴）
        renderStructureBlock(guiGraphics, recipe);
        poseStack.popPose();

        float partialTick = Minecraft.getInstance().getPartialTick();
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        double renderTick = Mth.lerp(partialTick, level.getGameTime() - 1, level.getGameTime());
        // 渲染控制块（框架）
        if (dummyControlEntity != null) {
            dummyControlEntity.setCurrentRecipe(recipe);
            dummyControlEntity.setRenderTick(renderTick);
            renderControlBlockGeo(guiGraphics, poseStack, recipe);
            dummyControlEntity.setCurrentRecipe(null);
            dummyControlEntity.setRenderTick(-1);
        }

        // 渲染辅助块（丝线）
        poseStack.pushPose();
        poseStack.translate(scale, 0, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(90));
        if (dummyAuxiliaryEntity != null && recipe != null) {
            dummyAuxiliaryEntity.setCurrentRecipe(recipe);
            dummyAuxiliaryEntity.setRenderTick(renderTick);
            renderAuxiliaryBlockGeo(guiGraphics, poseStack, recipe);
            dummyAuxiliaryEntity.setCurrentRecipe(null);
            dummyAuxiliaryEntity.setRenderTick(-1);
        }
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void renderStructureBlock(GuiGraphics guiGraphics, WeavingRecipe recipe) {
        float angle = -getCurrentAngle() + Minecraft.getInstance().getPartialTick();

        // 渲染空线轴 - 使用相对坐标
        GuiGameElement.of(PartialModelRegister.EmptySpool)
                .rotateBlock(angle, 0, 0)
                .scale(scale)
                .at(0, 0, 0)
                .render(guiGraphics);

        // 如果有配方，渲染带颜色的线轴
        if (recipe != null) {
            int color = recipe.getColor();
            GuiGameElement.of(PartialModelRegister.Spool)
                    .rotateBlock(angle, 0, 0)
                    .scale(scale)
                    .color(color)
                    .at(0, 0, 0)
                    .render(guiGraphics);
        }
    }

    /**
     * 使用GeoLib渲染控制块（框架）
     */
    private static void renderControlBlockGeo(GuiGraphics guiGraphics, PoseStack poseStack, WeavingRecipe recipe) {
        poseStack.pushPose();
        poseStack.scale(scale, -scale, scale);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        GeoBlockRenderer<LoomControlBlockEntity> renderer =
                (GeoBlockRenderer<LoomControlBlockEntity>) Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher()
                        .getRenderer(dummyControlEntity);

        if (renderer != null) {
            renderer.render(
                    dummyControlEntity,
                    Minecraft.getInstance().getPartialTick(),
                    poseStack,
                    bufferSource,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY
            );
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /**
     * 使用GeoLib渲染辅助块（丝线）
     */
    private static void renderAuxiliaryBlockGeo(GuiGraphics guiGraphics, PoseStack poseStack, WeavingRecipe recipe) {
        poseStack.pushPose();
        poseStack.scale(scale, -scale, scale);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        GeoBlockRenderer<LoomAuxiliaryBlockEntity> renderer =
                (GeoBlockRenderer<LoomAuxiliaryBlockEntity>) Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher()
                        .getRenderer(dummyAuxiliaryEntity);

        if (renderer != null) {
            renderer.render(
                    dummyAuxiliaryEntity,
                    Minecraft.getInstance().getPartialTick(),
                    poseStack,
                    bufferSource,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY
            );
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static float getCurrentAngle() {
        long gameTime = System.currentTimeMillis();
        return (gameTime % 3600) / 10f;
    }

    // 热重载调试方法
    public static void setRotationX(float value) { rotationX = value; }
    public static void setRotationY(float value) { rotationY = value; }
    public static void setScale(float value) { scale = value; }
    public static void setZOffset(float value) { zOffset = value; }
}
