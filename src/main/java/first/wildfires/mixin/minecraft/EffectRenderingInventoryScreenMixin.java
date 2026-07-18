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
import java.util.Set;

@Mixin(EffectRenderingInventoryScreen.class)
public class EffectRenderingInventoryScreenMixin {

	private static final Set<String> HIDDEN_EFFECTS = Set.of(
			"effect.xaerominimap.no_cave_maps",
			"effect.xaerominimap.no_waypoints",
			"effect.xaerominimap.no_entity_radar",
			"effect.xaerominimap.no_minimap",
			"effect.xaeroworldmap.no_cave_maps",
			"effect.xaeroworldmap.no_world_map"
	);

	@ModifyExpressionValue(
			method = "renderEffects",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;getActiveEffects()Ljava/util/Collection;"
			)
	)
	private Collection<MobEffectInstance> renderEffects(Collection<MobEffectInstance> original) {
		ArrayList<MobEffectInstance> effects = new ArrayList<>(original);
		effects.removeIf(effect -> HIDDEN_EFFECTS.contains(effect.getEffect().getDescriptionId()));
		InventoryEffectRenderEvent event = new InventoryEffectRenderEvent(effects);
		MinecraftForge.EVENT_BUS.post(event);
		return event.getEffectList();
	}

}
