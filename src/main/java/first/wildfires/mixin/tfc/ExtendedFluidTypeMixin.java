package first.wildfires.mixin.tfc;

import first.wildfires.api.tfc.fluid.IExtendedFluidTypeMixin;
import fr.lucreeper74.createmetallurgy.registries.CMDamageTypes;
import net.dries007.tfc.common.fluids.ExtendedFluidType;
import net.dries007.tfc.common.fluids.FluidTypeClientProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.*;

import javax.annotation.Nullable;
import java.util.function.Consumer;

@Mixin(value = ExtendedFluidType.class, remap = false)
public abstract class ExtendedFluidTypeMixin extends FluidType implements IExtendedFluidTypeMixin {
    public ExtendedFluidTypeMixin(Properties properties) {
        super(properties);
    }

    @Unique
    public ResourceLocation wildfires$StillTexture = null;

    @Unique
    public ResourceLocation wildfires$FlowingTexture = null;

    @Unique
    public ResourceLocation wildfires$OverlayTexture = null;

    @Unique
    public ResourceLocation wildfires$RenderOverlayTexture = null;

    @Unique
    public Integer wildfires$TintColor = null;

    @Unique
    private static final int MOLTEN_FLUID_BURNING_TIME = 15;

    @Override
    public void wildfires$SetStillTexture(ResourceLocation resourceLocation) {
        wildfires$StillTexture = resourceLocation;
    }

    @Override
    public void wildfires$SetFlowingTexture(ResourceLocation resourceLocation) {
        wildfires$FlowingTexture = resourceLocation;
    }

    @Override
    public void wildfires$SetOverlayTexture(ResourceLocation resourceLocation) {
        wildfires$OverlayTexture = resourceLocation;
    }

    @Override
    public void wildfires$SetRenderOverlayTexture(ResourceLocation resourceLocation) {
        wildfires$RenderOverlayTexture = resourceLocation;
    }

    @Override
    public void wildfires$SetTintColor(long color) {
        wildfires$TintColor = (int) color;
    }

    @Final
    @Shadow(remap = false)
    private FluidTypeClientProperties clientProperties;

    @Override
    public boolean canDrownIn(LivingEntity entity) {
        return !entity.canBreatheUnderwater();
    }

    /**
     * @author Wildfires
     * @reason Overwrite the default behavior to allow for custom tint colors
     */
    @Overwrite
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public int getTintColor()
            {
                return wildfires$TintColor == null ? clientProperties.tintColor() : wildfires$TintColor;
            }

            @Override
            public int getTintColor(FluidStack stack) {
                return wildfires$TintColor == null ? clientProperties.tintColor() : wildfires$TintColor;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos)
            {
                return wildfires$TintColor == null ? clientProperties.tintColorFunction().applyAsInt(getter, pos) : wildfires$TintColor;
            }

            @Override
            public ResourceLocation getStillTexture()
            {
                return wildfires$StillTexture == null ? clientProperties.stillTexture() : wildfires$StillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture()
            {
                return wildfires$FlowingTexture == null ? clientProperties.flowingTexture() : wildfires$FlowingTexture;
            }

            @Override
            @Nullable
            public ResourceLocation getOverlayTexture()
            {
                return wildfires$OverlayTexture == null ? clientProperties.overlayTexture() : wildfires$OverlayTexture;
            }

            @Override
            @Nullable
            public ResourceLocation getRenderOverlayTexture(Minecraft minecraft)
            {
                return wildfires$RenderOverlayTexture == null ? clientProperties.renderOverlayTexture() :  wildfires$RenderOverlayTexture;
            }
        });
    }

    @Override
    public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double gravity) {
        if (getTemperature() != 1300)
            return false;

        entity.setDeltaMovement(entity.getDeltaMovement().multiply(.3F, .8F, .3F));

        if (!entity.fireImmune()) {
            entity.setSecondsOnFire(MOLTEN_FLUID_BURNING_TIME);
            if (entity.hurt(CMDamageTypes.moltenFluid(entity.level()), 4.0F))
                entity.playSound(SoundEvents.GENERIC_BURN, .4F, 3F);
        }

        return false;
    }

    @Override
    public void setItemMovement(ItemEntity entity) {
        if (entity.fireImmune()) {
            Vec3 vec3 = entity.getDeltaMovement();
            entity.setDeltaMovement(vec3.x * (double) .95F, vec3.y + (double) (vec3.y < (double) .06F ? 5.0E-4F : .0F), vec3.z * (double) .95F);
        } else {
            entity.setSecondsOnFire(MOLTEN_FLUID_BURNING_TIME);
            if (entity.hurt(CMDamageTypes.moltenFluid(entity.level()), 4.0F))
                entity.playSound(SoundEvents.GENERIC_BURN, .4F, 3F);
        }
    }

    @Override
    public boolean supportsBoating(Boat boat) {
        boat.setSecondsOnFire(MOLTEN_FLUID_BURNING_TIME);
        return super.supportsBoating(boat);
    }
}
