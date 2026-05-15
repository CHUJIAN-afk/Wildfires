package first.wildfires.kinetic.loom;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.dries007.tfc.common.capabilities.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class LoomControlBlockEntity extends KineticBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ItemStackHandler itemStackHandler = new ItemStackHandler(2);
    private final LazyOptional<IItemHandler> itemHandlerLazy = LazyOptional.of(() -> itemStackHandler);
    private float lastProgress = 0;
    private float progress = 0;

    public LoomControlBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide()) {
            float speed = getSpeed();
            if (speed != 0) {
                progress += speed / 60;
                if (progress > 160) {
                    progress -= 160;
                }
            }
            setChanged();
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putFloat("progress", progress);
        compound.put("inventory", itemStackHandler.serializeNBT());
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (compound.contains("progress")) {
            progress = compound.getFloat("progress");
        }
        if (compound.contains("inventory")) {
            itemStackHandler.deserializeNBT(compound.getCompound("inventory"));
        }
    }

    @Override
    public float getSpeed() {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide() && state.hasProperty(LoomControlBlock.FACING)) {
            Direction facing = state.getValue(LoomControlBlock.FACING);
            Direction back = facing.getOpposite();
            BlockPos backPos = getBlockPos().relative(back);
            BlockEntity backBE = level.getBlockEntity(backPos);
            if (backBE instanceof LoomStructureBlockEntity kineticBE) {
                return kineticBE.getSpeed();
            }
        }
        return super.getSpeed();
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
        if (level != null && level.isClientSide()) {
            return getRenderTick();
        }
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    public double getRenderTick() {
        float partialTick = Minecraft.getInstance().getPartialTick();
        if (lastProgress > progress) {
            lastProgress -= 160;
        }
        lastProgress = Mth.lerp(partialTick, lastProgress, progress);
        return lastProgress;
    }

    public float getProgress() {
        return progress;
    }

    public LazyOptional<IItemHandler> getItemHandlerLazy() {
        return itemHandlerLazy;
    }
}