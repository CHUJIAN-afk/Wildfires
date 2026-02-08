package first.wildfires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.block.CustomCogWheelBlock;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CustomCogWheelBlockEntityRenderer extends BracketedKineticBlockEntityRenderer {

    public CustomCogWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BracketedKineticBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getBlock() instanceof CustomCogWheelBlock customCogWheelBlock) {
            PartialModel model = customCogWheelBlock.getModel();
            Direction.Axis axis = getRotationAxisOf(be);
            Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            if (customCogWheelBlock.isLarge()) {
                VertexConsumer vc = buffer.getBuffer(RenderType.solid());
                renderRotatingBuffer(be, CachedBuffers.partialFacingVertical(model, be.getBlockState(), facing), ms, vc, light);
                float angle = getAngleForLargeCogShaft(be, axis);
                SuperByteBuffer shaft = CachedBuffers.partialFacingVertical(AllPartialModels.COGWHEEL_SHAFT, be.getBlockState(), facing);
                kineticRotationTransform(shaft, be, axis, angle, light);
                shaft.renderInto(ms, vc);
            } else {
                BlockState state = this.getRenderedBlockState(be);
                RenderType type = this.getRenderType(be, state);
                renderRotatingBuffer(be, CachedBuffers.partialFacingVertical(model, be.getBlockState(), facing), ms, buffer.getBuffer(type), light);
            }
        }
    }

}
