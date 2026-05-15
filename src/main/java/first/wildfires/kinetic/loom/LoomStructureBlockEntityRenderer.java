package first.wildfires.kinetic.loom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.register.BlockRegister;
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
        PartialModel model = getModel(be);
        if (model != null) {
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
                if (face == Direction.NORTH) {
                    ms.translate(-1, 0, 0);
                }
                if (face == Direction.WEST) {
                    ms.translate(0, 0, 1);
                }
                if (face == Direction.SOUTH) {
                    facing = facing.getOpposite();
                    ms.translate(1, 0, 0);
                }
                if (face == Direction.EAST) {
                    facing = facing.getOpposite();
                    ms.translate(0, 0, -1);
                }
            }
            BlockState state = this.getRenderedBlockState(be);
            RenderType type = this.getRenderType(be, state);
            renderRotatingBuffer(be, CachedBuffers.partialFacingVertical(model, blockState, facing), ms, buffer.getBuffer(type), light);
        }
    }

    private PartialModel getModel(LoomStructureBlockEntity be){
        BlockPos blockPos = be.getBlockPos();
        Level level = be.getLevel();
        if (level != null) {
            BlockState state = level.getBlockState(blockPos);
            if (state.hasProperty(LoomStructureBlock.FACING)) {
                BlockPos targetPos = blockPos.relative(state.getValue(LoomStructureBlock.FACING));
                BlockState blockState = level.getBlockState(targetPos);
                if (!blockState.is(BlockRegister.LoomControlBlock.get())) {
                    BlockPos masterRecursive = LoomStructureBlock.findMasterRecursive(level, blockPos, state);
                    if (masterRecursive != null && level.getBlockEntity(masterRecursive) instanceof LoomControlBlockEntity loomControlBlockEntity) {
                        if (loomControlBlockEntity.getProgress() != 0) {
                            return PartialModelRegister.Spool;
                        } else {
                            return PartialModelRegister.EmptySpool;
                        }
                    }
                }
            }
        }
        return null;
    }

}
