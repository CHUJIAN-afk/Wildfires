package first.wildfires.mixin.legendarysurvivaloverhaul;

import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.api.customEvent.PlayerWetnessEvent;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.wetness.WetnessCapability;

@Mixin(value = WetnessCapability.class,remap = false)
public class WetnessCapabilityMixin {

    @ModifyArg(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 2
            )
    )
    private int addWetnessOfRain(int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.RainIncrease event = new PlayerWetnessEvent.RainIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            return event.getWetness();
        }
        return 0;
    }

    @ModifyArg(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 5
            )
    )
    private int addWetnessOfFluid1(int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.FluidIncrease event = new PlayerWetnessEvent.FluidIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            return event.getWetness();
        }
        return 0;
    }
    @ModifyArg(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 7
            )
    )
    private int addWetnessOfFluid2(int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.FluidIncrease event = new PlayerWetnessEvent.FluidIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            return event.getWetness();
        }
        return 0;
    }

}
