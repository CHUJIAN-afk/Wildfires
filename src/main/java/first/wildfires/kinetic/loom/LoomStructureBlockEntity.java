package first.wildfires.kinetic.loom;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class LoomStructureBlockEntity extends KineticBlockEntity {
    public LoomStructureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 返回渲染边界框，扩展到包含整个多方块结构
     * 防止玩家视角不在控制块所在格子时被剔除
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(2);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide()) {
            // 收集本格内的物品实体
            List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(getBlockPos()));
            for (ItemEntity entity : entities) {
                ItemStack stack = entity.getItem();
                if (stack.isEmpty()) continue;
                // 找到控制块并尝试放入物品
                BlockPos masterPos = LoomStructureBlock.findMasterRecursive(level, getBlockPos(), getBlockState());
                if (masterPos != null && level.getBlockEntity(masterPos) instanceof LoomControlBlockEntity controlBE) {
                    IItemHandler handler = controlBE.getItemHandlerLazy().resolve().orElse(null);
                    if (handler != null) {
                        // 尝试插入物品
                        ItemStack remaining = insertItem(handler, stack);
                        if (remaining.getCount() < stack.getCount()) {
                            // 成功插入部分或全部，更新实体物品
                            if (remaining.isEmpty()) {
                                entity.discard();
                            } else {
                                entity.setItem(remaining);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public float calculateStressApplied() {
        BlockState blockState = getBlockState();
        if (level != null && blockState.hasProperty(LoomControlBlock.FACING)) {
            BlockEntity blockEntity = level.getBlockEntity(getBlockPos().relative(blockState.getValue(LoomControlBlock.FACING)));
            if (blockEntity instanceof LoomControlBlockEntity) {
                return 4;
            }
        }
        return super.calculateStressApplied();
    }

    /**
     * 尝试将物品插入ItemHandler，返回剩余物品
     */
    private ItemStack insertItem(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.isItemValid(i, remaining)) {
                remaining = handler.insertItem(i, remaining, false);
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return remaining;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && level != null) {
            BlockPos masterRecursive = LoomStructureBlock.findMasterRecursive(level, getBlockPos(), getBlockState());
            if (masterRecursive != null && level.getBlockEntity(masterRecursive) instanceof LoomControlBlockEntity loomControlBlockEntity) {
                return loomControlBlockEntity.getItemHandlerLazy().cast();
            }
        }
        return super.getCapability(cap, side);
    }
}
