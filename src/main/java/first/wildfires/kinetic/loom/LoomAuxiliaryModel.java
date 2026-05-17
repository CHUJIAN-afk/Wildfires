package first.wildfires.kinetic.loom;

import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
        Level level = animatable.getLevel();
        if (level != null) {
            BlockPos master = LoomAuxiliaryBlock.findMaster(level, animatable.getBlockPos(), animatable.getBlockState());
            if (master != null && level.getBlockEntity(master) instanceof LoomControlBlockEntity loomControlBlockEntity) {
                WeavingRecipe currentRecipe = loomControlBlockEntity.getCurrentRecipe();
                if (currentRecipe != null && currentRecipe.getWeavingType() == WeavingType.WOVEN_BLOCK) {
                    return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/block/loom_fabric.png");
                }
            }
        }
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/block/loom_silk.png");
    }

    @Override
    public ResourceLocation getAnimationResource(LoomAuxiliaryBlockEntity animatable) {
        // 使用与控制块相同的动画文件
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "animations/block/loom.animation.json");
    }
}