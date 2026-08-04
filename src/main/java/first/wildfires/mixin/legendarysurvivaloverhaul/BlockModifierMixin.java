package first.wildfires.mixin.legendarysurvivaloverhaul;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.common.temperature.BlockModifier;

@Mixin(value = BlockModifier.class, remap = false)
public class BlockModifierMixin {

    @Inject(method = "getWorldInfluence", at = @At("HEAD"), cancellable = true)
    private void wildfires$disableLegacyBlockThermalSources(
            Player player,
            Level level,
            BlockPos position,
            CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(0.0F);
    }
}
