package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventJS;
import first.wildfires.api.tfc.fluid.IExtendedFluidTypeMixin;
import net.dries007.tfc.common.fluids.ExtendedFluidType;
import net.minecraft.resources.ResourceLocation;

public class TFCFluidModificationEventJS extends EventJS {

    public TFCFluidModificationEventJS(ExtendedFluidType fluidType, String name) {
        extendedFluidType = fluidType;

        this.name = name;
    }

    private final ExtendedFluidType extendedFluidType;

    public String name;

    public void setStillTexture(ResourceLocation resourceLocation)
    {
        ((IExtendedFluidTypeMixin)extendedFluidType).wildfires$SetStillTexture(resourceLocation);
    }

    public void setFlowingTexture(ResourceLocation resourceLocation)
    {
        ((IExtendedFluidTypeMixin)extendedFluidType).wildfires$SetFlowingTexture(resourceLocation);
    }

    public void setOverlayTexture(ResourceLocation resourceLocation)
    {
        ((IExtendedFluidTypeMixin)extendedFluidType).wildfires$SetOverlayTexture(resourceLocation);
    }

    public void setRenderOverlayTexture(ResourceLocation resourceLocation)
    {
        ((IExtendedFluidTypeMixin)extendedFluidType).wildfires$SetRenderOverlayTexture(resourceLocation);
    }

    public void setTintColor(long color)
    {
        ((IExtendedFluidTypeMixin)extendedFluidType).wildfires$SetTintColor(color);
    }
}
