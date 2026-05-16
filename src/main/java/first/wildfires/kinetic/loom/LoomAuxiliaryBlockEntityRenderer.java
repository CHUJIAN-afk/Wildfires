package first.wildfires.kinetic.loom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LoomAuxiliaryBlockEntityRenderer extends GeoBlockRenderer<LoomAuxiliaryBlockEntity> {

    public LoomAuxiliaryBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new LoomAuxiliaryModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> poseStack.mulPose(Axis.XN.rotationDegrees(90.0F));
        }
    }

    @Override
    public void actuallyRender(PoseStack poseStack, LoomAuxiliaryBlockEntity animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // 在渲染前设置位置偏移，与控制块渲染逻辑相同
        if (!isReRender) {
            Direction facing = animatable.getBlockState().getValue(LoomAuxiliaryBlock.FACING);
            // 根据朝向调整模型位置，使辅助块渲染与控制块完全重合
            switch (facing) {
                case SOUTH -> poseStack.translate(1, 0, 1);
                case EAST -> poseStack.translate(1, 0, -1);
                case WEST -> poseStack.translate(-1, 0, 1);
                case NORTH -> poseStack.translate(-1, 0, -1);
            }
        }
        Level level = animatable.getLevel();
        if (level != null) {
            BlockPos master = LoomAuxiliaryBlock.findMaster(level, animatable.getBlockPos(), animatable.getBlockState());
            if (master != null && level.getBlockEntity(master) instanceof LoomControlBlockEntity loomControlBlockEntity) {
                if (loomControlBlockEntity.getTick(loomControlBlockEntity) > 0) {
                    WeavingRecipe currentRecipe = loomControlBlockEntity.getCurrentRecipe();
                    if (currentRecipe != null) {
                        red = (float) currentRecipe.getRed() / 255;
                        green = (float) currentRecipe.getGreen() / 255;
                        blue = (float) currentRecipe.getBlue() / 255;
                    }
                    super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
        }
    }
}