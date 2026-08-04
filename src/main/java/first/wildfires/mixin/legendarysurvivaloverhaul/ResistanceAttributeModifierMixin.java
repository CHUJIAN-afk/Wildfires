package first.wildfires.mixin.legendarysurvivaloverhaul;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;
import sfiomn.legendarysurvivaloverhaul.common.temperature.dynamic.ResistanceAttributeModifier;
import sfiomn.legendarysurvivaloverhaul.registry.AttributeRegistry;

/** Keeps displayed resistance attributes unchanged while doubling their thermal effect. */
@Mixin(value = ResistanceAttributeModifier.class, remap = false)
public class ResistanceAttributeModifierMixin {

    @Inject(method = "applyDynamicPlayerInfluence", at = @At("RETURN"), cancellable = true)
    private void wildfires$doubleDirectionalResistance(
            Player player,
            float staticInfluence,
            float dynamicInfluence,
            CallbackInfoReturnable<Float> cir
    ) {
        float temperatureDelta = staticInfluence - TemperatureEnum.NORMAL.getMiddle();
        float thermalResistance = (float) player.getAttributeValue(AttributeRegistry.THERMAL_RESISTANCE.get());

        if (temperatureDelta > 0f) {
            float resistance = thermalResistance
                    + (float) player.getAttributeValue(AttributeRegistry.HEAT_RESISTANCE.get()) * 2f;
            cir.setReturnValue(-Mth.clamp(resistance, dynamicInfluence, temperatureDelta + dynamicInfluence));
        } else if (temperatureDelta < 0f) {
            float resistance = thermalResistance
                    + (float) player.getAttributeValue(AttributeRegistry.COLD_RESISTANCE.get()) * 2f;
            float coldDelta = -temperatureDelta;
            cir.setReturnValue(Mth.clamp(resistance, -dynamicInfluence, coldDelta - dynamicInfluence));
        }
    }
}
