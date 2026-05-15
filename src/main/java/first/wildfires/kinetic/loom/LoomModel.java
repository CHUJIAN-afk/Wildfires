package first.wildfires.kinetic.loom;

import first.wildfires.Wildfires;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LoomModel extends GeoModel<LoomControlBlockEntity> {

    @Override
    public RenderType getRenderType(LoomControlBlockEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentCull(texture);
    }

    @Override
    public ResourceLocation getModelResource(LoomControlBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "geo/block/loom.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LoomControlBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/block/loom.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LoomControlBlockEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "animations/block/loom.animation.json");
    }
}