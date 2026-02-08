package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import first.wildfires.api.customEvent.InventoryEffectRenderEvent;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(EffectRenderingInventoryScreen.class)
public class EffectRenderingInventoryScreenMixin {

	@ModifyExpressionValue(
			method = "renderEffects",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"
			)
	)
	private Collection<MobEffectInstance> renderEffects(Collection<MobEffectInstance> original) {
		InventoryEffectRenderEvent event = new InventoryEffectRenderEvent(new ArrayList<>(original.stream().toList()));
		MinecraftForge.EVENT_BUS.post(event);
		return event.getEffectList();
	}

}
