package first.wildfires.kinetic.loom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LoomControlBlockEntityRenderer extends GeoBlockRenderer<LoomControlBlockEntity> {

    public LoomControlBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new LoomModel());
    }

    @Override
    public void actuallyRender(PoseStack poseStack, LoomControlBlockEntity animatable, BakedGeoModel model, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // 在渲染前设置位置偏移
        if (!isReRender) {
            Direction facing = animatable.getBlockState().getValue(LoomControlBlock.FACING);
            // 根据朝向调整模型位置
            switch (facing) {
                case NORTH -> poseStack.translate(0, 0, 1);
                case EAST -> poseStack.translate(-1, 0, 0);
                case SOUTH -> poseStack.translate(0, 0, -1);
                case WEST -> poseStack.translate(1, 0, 0);
            }
        }
        // 正确传递isReRender参数
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

}