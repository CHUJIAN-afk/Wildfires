package first.wildfires.kinetic.loom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LoomAuxiliaryBlockEntity extends LoomStructureBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LoomAuxiliaryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "weaving_controller", 0, state -> state.setAndContinue(RawAnimation.begin().then("weaving", Animation.LoopType.LOOP))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object object) {
        // 从控制块获取动画tick
        if (level != null) {
            BlockPos masterPos = LoomAuxiliaryBlock.findMaster(level, getBlockPos(), getBlockState());
            if (masterPos != null && level.getBlockEntity(masterPos) instanceof LoomControlBlockEntity controlBE) {
                return controlBE.getTick(object);
            }
        }
        return 0;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // 辅助块不提供物品处理能力，直接返回父类（不转发到控制块）
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return LazyOptional.empty();
        }
        return super.getCapability(cap, side);
    }
}