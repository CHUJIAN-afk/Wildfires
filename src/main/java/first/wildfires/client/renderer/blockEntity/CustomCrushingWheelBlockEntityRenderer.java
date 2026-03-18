package first.wildfires.client.renderer.blockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.block.CustomCrushingWheelBlock;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class CustomCrushingWheelBlockEntityRenderer extends KineticBlockEntityRenderer<CrushingWheelBlockEntity> {

    public CustomCrushingWheelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CrushingWheelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getBlock() instanceof CustomCrushingWheelBlock crushingWheelBlock) {
            PartialModel model = crushingWheelBlock.getModel();
            Direction.Axis axis = getRotationAxisOf(be);
            Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            renderRotatingBuffer(be, CachedBuffers.partialFacingVertical(model, be.getBlockState(), facing), ms, buffer.getBuffer(type), light);
        }
    }

}
