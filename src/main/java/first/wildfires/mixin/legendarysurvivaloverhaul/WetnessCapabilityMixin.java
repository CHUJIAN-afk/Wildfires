package first.wildfires.mixin.legendarysurvivaloverhaul;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.api.customEvent.PlayerWetnessEvent;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.wetness.WetnessCapability;

@Mixin(value = WetnessCapability.class, remap = false)
public class WetnessCapabilityMixin {

    @WrapWithCondition(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 2
            )
            , remap = true
    )
    private boolean addWetnessOfRain(WetnessCapability instance, int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.RainIncrease event = new PlayerWetnessEvent.RainIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            instance.addWetness(event.getWetness());
        }
        return false;
    }

    @WrapWithCondition(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 5
            )
            , remap = true
    )
    private boolean addWetnessOfFluid1(WetnessCapability instance, int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.FluidIncrease event = new PlayerWetnessEvent.FluidIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            instance.addWetness(event.getWetness());
        }
        return false;
    }
    @WrapWithCondition(
            method = "tickUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lsfiomn/legendarysurvivaloverhaul/common/capabilities/wetness/WetnessCapability;addWetness(I)V",
                    ordinal = 8
            )
            , remap = true
    )
    private boolean addWetnessOfFluid2(WetnessCapability instance, int wetness, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Level level) {
        PlayerWetnessEvent.FluidIncrease event = new PlayerWetnessEvent.FluidIncrease(player, level, wetness);
        WildfiresUtil.post(event);
        if (!event.isCanceled()) {
            instance.addWetness(event.getWetness());
        }
        return false;
    }

}
