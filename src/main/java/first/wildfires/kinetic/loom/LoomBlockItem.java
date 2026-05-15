package first.wildfires.kinetic.loom;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

public class LoomBlockItem extends BlockItem implements IWrenchable {

    public LoomBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();

        // 获取玩家面向的方向
        Direction playerFacing = context.getHorizontalDirection();

        // 织布机正面朝向玩家（玩家看到的是织布机的正面）
        Direction loomFacing = playerFacing.getOpposite();

        // 控制块在织布机结构的左下角（从玩家视角看）
        // 织布机结构：
        //   [后方右] [后方左]
        //   [右]     [控制块]
        // 从玩家视角：控制块在右下角，右侧结构块在左下角

        // 计算结构块相对位置
        Direction right = loomFacing.getClockWise();  // 织布机的右侧
        Direction back = loomFacing.getOpposite();    // 织布机的后方

        // 控制块位置就是点击位置
        BlockPos controlPos = clickedPos;
        BlockPos rightPos = controlPos.relative(right);
        BlockPos backPos1 = controlPos.relative(back);
        BlockPos backPos2 = backPos1.relative(right);

        List<BlockPos> positions = new ArrayList<>();
        positions.add(controlPos);
        positions.add(rightPos);
        positions.add(backPos1);
        positions.add(backPos2);

        for (BlockPos checkPos : positions) {
            BlockState state = level.getBlockState(checkPos);
            if (!state.canBeReplaced()) {
                if (level.isClientSide()) {
                    showBounds(context, positions);
                }
                return InteractionResult.FAIL;
            }
        }

        // 设置控制块的朝向
        BlockState placementState = this.getBlock().defaultBlockState()
                .setValue(LoomControlBlock.FACING, loomFacing);

        if (level.setBlockAndUpdate(controlPos, placementState)) {
            // 手动调用onPlace来生成结构块
            this.getBlock().onPlace(placementState, level, controlPos, level.getBlockState(controlPos), false);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }

    @OnlyIn(Dist.CLIENT)
    private void showBounds(BlockPlaceContext context, List<BlockPos> positions) {
        if (!(context.getPlayer() instanceof LocalPlayer player)) return;

        // 计算包围盒
        AABB box = new AABB(positions.get(0));
        for (BlockPos p : positions) {
            box = box.minmax(new AABB(p));
        }

        Outliner.getInstance().showAABB("loom", box)
                .colored(0xFF_ff5d6c)
                .lineWidth(1 / 32f);

        player.displayClientMessage(
                Component.translatable("wildfires.loom.space_insufficient"),
                true
        );
    }
}