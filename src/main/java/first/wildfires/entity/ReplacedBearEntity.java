package first.wildfires.entity;

import net.dries007.tfc.common.entities.TFCEntities;
import net.dries007.tfc.common.entities.predator.Predator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ReplacedBearEntity implements GeoReplacedEntity {

    protected final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.bear.walk");

    protected final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.bear.run");

    protected final RawAnimation SLEEP = RawAnimation.begin().thenLoop("animation.bear.sleep");

    protected final RawAnimation ATTACK = RawAnimation.begin().then("animation.bear.attack", Animation.LoopType.PLAY_ONCE);

    protected final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.bear.swim");

    protected final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bear.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ReplacedBearEntity() {
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public String type;

    public static ReplacedBearEntity BlackBear() {
        var instance = new ReplacedBearEntity();

        instance.type = "black_bear";

        return instance;
    }

    public static ReplacedBearEntity PolarBear() {
        var instance = new ReplacedBearEntity();

        instance.type = "polar_bear";

        return instance;
    }

    public static ReplacedBearEntity GrizzlyBear() {
        var instance = new ReplacedBearEntity();

        instance.type = "grizzly_bear";

        return instance;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(
            new AnimationController<>(this, "move", 10, this::MoveController)
                .triggerableAnim("attack", ATTACK)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return switch (type) {
            case "black_bear" -> TFCEntities.BLACK_BEAR.get();
            case "polar_bear" -> TFCEntities.POLAR_BEAR.get();
            case "grizzly_bear" -> TFCEntities.GRIZZLY_BEAR.get();
            default -> null;
        };
    }

    private PlayState MoveController(AnimationState<ReplacedBearEntity> animationState) {
        Predator predator = (Predator) animationState.getData(DataTickets.ENTITY);
        double horizontalSpeed = Math.sqrt(
                predator.getDeltaMovement().x * predator.getDeltaMovement().x
                        + predator.getDeltaMovement().z * predator.getDeltaMovement().z);

        if (predator.isSleeping())
        {
            animationState.getController().transitionLength(0);

            animationState.getController().setAnimationSpeed(0.5f);

            return animationState.setAndContinue(SLEEP);
        }
        else
        {
            animationState.getController().transitionLength(10);

            if (predator.isInWater() && !predator.onGround()) {
                animationState.getController().setAnimationSpeed(1f);

                return animationState.setAndContinue(SWIM);
            }
            else if (horizontalSpeed > 0.01D)
            {
                animationState.getController().setAnimationSpeed(
                        Mth.clamp((float) (horizontalSpeed * 6.0D), 0.5F, 1.5F));

                if(predator.isAggressive())
                    return animationState.setAndContinue(RUN);
                else
                    return animationState.setAndContinue(WALK);
            }
            else {
                animationState.getController().setAnimationSpeed(1f);
                return animationState.setAndContinue(IDLE);
            }
        }
    }
}
