package first.wildfires.kinetic.loom;

import first.wildfires.Wildfires;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LoomAuxiliaryModel extends GeoModel<LoomAuxiliaryBlockEntity> {

    @Override
    public RenderType getRenderType(LoomAuxiliaryBlockEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }

    @Override
    public ResourceLocation getModelResource(LoomAuxiliaryBlockEntity animatable) {
        // 使用与控制块相同的模型
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "geo/block/loom.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LoomAuxiliaryBlockEntity animatable) {
        // 使用与控制块相同的材质（后续可修改）
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/block/loom_silk.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LoomAuxiliaryBlockEntity animatable) {
        // 使用与控制块相同的动画文件
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "animations/block/loom.animation.json");
    }
}