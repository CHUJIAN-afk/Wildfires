package first.wildfires.mixin.tfc;

import first.wildfires.api.gecko.GeckoAnimal;
import first.wildfires.api.tfc.goal.SwimGoal;
import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.entities.predator.Predator;
import net.dries007.tfc.common.entities.prey.WildAnimal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Predator.class,remap = false)
public abstract class PredatorMixin extends WildAnimal implements GeckoAnimal {

    public PredatorMixin(EntityType<? extends AgeableMob> type, Level level, TFCSounds.EntitySound sounds) {
        super(type, level, sounds);
    }

    @Unique
    private boolean wildfires$startAttack;

    public boolean wildfires$consumeAttackAnimation() {
        if (!wildfires$startAttack) {
            return false;
        }

        wildfires$startAttack = false;
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new BreathAirGoal(this));
        goalSelector.addGoal(1, new SwimGoal(this, 1f));
    }

    @Inject(
            method = "handleEntityEvent",
            at = @At("HEAD"),
            remap = true
    )
    public void handleEntityEvent(byte id, CallbackInfo ci) {
        // TFC broadcasts id 4 for every attack attempt, including misses.
        // Wildfires uses a separate event emitted only after a successful hit.
        if (id == 60) {
            wildfires$startAttack = true;
        }
    }

    @Inject(
            method = "doHurtTarget(Lnet/minecraft/world/entity/Entity;I)Z",
            at = @At("RETURN"),
            remap = false
    )
    private void wildfires$broadcastSuccessfulAttack(Entity target, int knockback,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!level().isClientSide && cir.getReturnValue()) {
            level().broadcastEntityEvent((Entity) (Object) this, (byte) 60);
        }
    }

}
