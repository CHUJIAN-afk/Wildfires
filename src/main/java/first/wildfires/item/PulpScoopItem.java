package first.wildfires.item;

import first.wildfires.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

public class PulpScoopItem extends Item {
    private static final int PULP_AMOUNT_MB = 10;
    private static final ResourceLocation PULP_ID = ResourceLocation.fromNamespaceAndPath("kubejs", "pulp");

    public PulpScoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Fluid pulp = ForgeRegistries.FLUIDS.getValue(PULP_ID);
        if (blockEntity == null || pulp == null) {
            return InteractionResult.PASS;
        }

        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side).map(handler -> {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);
                if (fluid.getFluid() != pulp || fluid.getAmount() < PULP_AMOUNT_MB) {
                    continue;
                }

                FluidStack drained = handler.drain(new FluidStack(pulp, PULP_AMOUNT_MB), IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() == PULP_AMOUNT_MB && context.getPlayer() instanceof ServerPlayer player) {
                    player.setItemInHand(context.getHand(), new ItemStack(ItemRegister.FilledPulpScoop.get()));
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.PASS;
            }
            return InteractionResult.PASS;
        }).orElse(InteractionResult.PASS);
    }
}
