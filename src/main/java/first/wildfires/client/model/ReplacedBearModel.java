package first.wildfires.client.model;

import first.wildfires.Wildfires;
import first.wildfires.entity.ReplacedBearEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class ReplacedBearModel extends DefaultedEntityGeoModel<ReplacedBearEntity> {
    public ReplacedBearModel() {
        super(ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "bear"));
    }

    @Override
    public ResourceLocation getTextureResource(ReplacedBearEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, String.format("textures/entity/%s.png", animatable.type));
    }

    @Override
    public void setCustomAnimations(ReplacedBearEntity animatable, long instanceId, AnimationState<ReplacedBearEntity> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone("head2");

        if (head != null) {
            EntityModelData data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);

            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }
}
