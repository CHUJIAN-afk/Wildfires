package first.wildfires.kinetic.loom;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LoomStructureBlockEntity extends KineticBlockEntity {
    public LoomStructureBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide()) {
            List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(getBlockPos()));
            for (ItemEntity entity : entities) {
            }
        }
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
