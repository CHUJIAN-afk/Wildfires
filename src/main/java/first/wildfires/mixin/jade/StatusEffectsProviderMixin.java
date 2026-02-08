package first.wildfires.mixin.jade;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.api.customEvent.JadeRenderEffectEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import snownee.jade.addon.vanilla.StatusEffectsProvider;
import snownee.jade.api.ITooltip;

@Mixin(value = StatusEffectsProvider.class,remap = false)
public class StatusEffectsProviderMixin {

    @WrapWithCondition(
            method = "appendTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lsnownee/jade/api/ITooltip;add(Lnet/minecraft/network/chat/Component;)V"
            )
    )
    private boolean skipSpecificEffects(ITooltip instance, Component component, @Local(name = "compound") CompoundTag compound) {
        String name = compound.getString("Name");
        JadeRenderEffectEvent event = new JadeRenderEffectEvent(name);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

}
