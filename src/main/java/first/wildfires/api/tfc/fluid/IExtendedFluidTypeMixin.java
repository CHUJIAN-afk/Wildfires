package first.wildfires.api.tfc.fluid;

import net.minecraft.resources.ResourceLocation;

public interface IExtendedFluidTypeMixin {
    void wildfires$SetStillTexture(ResourceLocation resourceLocation);

    void wildfires$SetFlowingTexture(ResourceLocation resourceLocation);

    void wildfires$SetTintColor(long color);

    void wildfires$SetOverlayTexture(ResourceLocation resourceLocation);

    void wildfires$SetRenderOverlayTexture(ResourceLocation resourceLocation);
}
