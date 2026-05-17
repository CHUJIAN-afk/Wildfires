package first.wildfires.kinetic.loom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingType;
import first.wildfires.register.PartialModelRegister;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LoomStructureBlockEntityRenderer extends KineticBlockEntityRenderer<LoomStructureBlockEntity> {

    public LoomStructureBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LoomStructureBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        RenderContext context = getModel(be);
        if (context != null) {
            Direction.Axis axis = getRotationAxisOf(be);
            if (axis == Direction.Axis.Z) {
                axis = Direction.Axis.X;
            } else if (axis == Direction.Axis.X) {
                axis = Direction.Axis.Z;
            }
            Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            BlockState blockState = be.getBlockState();
            if (blockState.hasProperty(LoomStructureBlock.FACING)) {
                Direction face = blockState.getValue(LoomStructureBlock.FACING);
                if (face == Direction.SOUTH) {
                    facing = facing.getOpposite();
                }
                if (face == Direction.EAST) {
                    facing = facing.getOpposite();
                }
            }
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            VertexConsumer consumer = buffer.getBuffer(type);
            renderRotatingBuffer(be, CachedBuffers.partialFacingVertical(PartialModelRegister.EmptySpool, blockState, facing), ms, consumer, light);
            WeavingRecipe weavingRecipe = context.weavingRecipe();
            if (weavingRecipe != null) {
                PartialModel partial;
                if (weavingRecipe.getWeavingType() == WeavingType.KNITTED_CLOTH) {
                    partial = PartialModelRegister.Spool;
                } else {
                    partial = PartialModelRegister.Fabric;
                }
                SuperByteBuffer superBuffer = CachedBuffers.partialFacingVertical(partial, blockState, facing);
                int color = weavingRecipe.getColor();
                renderRotatingBuffer(be, superBuffer, ms, new CorlorVertexConsumer(consumer, color), light);
            }
        }
    }

    private RenderContext getModel(LoomStructureBlockEntity be) {
        BlockPos blockPos = be.getBlockPos();
        Level level = be.getLevel();
        if (level != null) {
            BlockState state = level.getBlockState(blockPos);
            if (state.hasProperty(LoomStructureBlock.FACING)) {
                BlockPos targetPos = blockPos.relative(state.getValue(LoomStructureBlock.FACING));
                if (level.getBlockEntity(targetPos) instanceof LoomControlBlockEntity loomControlBlockEntity) {
                    WeavingRecipe currentRecipe = loomControlBlockEntity.getCurrentRecipe();
                    return new RenderContext(currentRecipe);
                }
            }
        }
        return null;
    }

    private record RenderContext(WeavingRecipe weavingRecipe) {

    }
}
