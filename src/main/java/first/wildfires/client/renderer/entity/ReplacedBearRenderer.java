package first.wildfires.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.api.gecko.GeckoAnimal;
import first.wildfires.client.model.ReplacedBearModel;
import first.wildfires.entity.ReplacedBearEntity;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

public class ReplacedBearRenderer extends GeoReplacedEntityRenderer<Predator, ReplacedBearEntity> {
    public ReplacedBearRenderer(EntityRendererProvider.Context renderManager, ReplacedBearModel model, ReplacedBearEntity entity) {
        super(renderManager, model, entity);
    }

    public static ReplacedBearRenderer BlackBear(EntityRendererProvider.Context renderManager) {
        return new ReplacedBearRenderer(renderManager, new ReplacedBearModel(), ReplacedBearEntity.BlackBear());
    }

    public static ReplacedBearRenderer PolarBear(EntityRendererProvider.Context renderManager) {
        return new ReplacedBearRenderer(renderManager, new ReplacedBearModel(), ReplacedBearEntity.PolarBear());
    }

    public static ReplacedBearRenderer GrizzlyBear(EntityRendererProvider.Context renderManager) {
        return new ReplacedBearRenderer(renderManager, new ReplacedBearModel(), ReplacedBearEntity.GrizzlyBear());
    }

    @Override
    public void render(@NotNull Predator entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.isBaby())
            poseStack.scale(0.6f, 0.6f, 0.6f);
        else
            poseStack.scale(1.2f, 1.2f, 1.2f);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        if (entity instanceof GeckoAnimal animal) {
            if (animal.wildfires$consumeAttackAnimation()) {
                var moveController = animatable.getAnimatableInstanceCache().getManagerForId(entity.getId()).getAnimationControllers().get("move");

                moveController.setAnimationSpeed(1.2f);

                moveController.transitionLength(0);

                animatable.triggerAnim(entity, "move", "attack");
            }
        }
    }
}
