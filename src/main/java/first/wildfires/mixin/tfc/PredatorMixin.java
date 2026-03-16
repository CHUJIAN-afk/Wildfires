package first.wildfires.mixin.tfc;

import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.entities.predator.Predator;
import net.dries007.tfc.common.entities.prey.WildAnimal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreathAirGoal;
import net.minecraft.world.level.Level;
import first.wildfires.api.gecko.GeckoAnimal;
import first.wildfires.api.tfc.goal.SwimGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Predator.class,remap = false)
public abstract class PredatorMixin extends WildAnimal implements GeckoAnimal {

    public PredatorMixin(EntityType<? extends AgeableMob> type, Level level, TFCSounds.EntitySound sounds) {
        super(type, level, sounds);
    }

    @Unique
    private boolean wildfires$startAttack;

    public boolean wildfires$startedAttack() {
        return wildfires$startAttack;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new BreathAirGoal(this));
        goalSelector.addGoal(1, new SwimGoal(this, 1f));
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    public void handleEntityEvent(byte id, CallbackInfo ci) {
        if (id == 4) {
            wildfires$startAttack = true;
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        if (wildfires$startAttack)
            wildfires$startAttack = false;
    }
}
