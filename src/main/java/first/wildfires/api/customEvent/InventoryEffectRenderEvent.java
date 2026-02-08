package first.wildfires.api.customEvent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;

/**
 * 物品栏药水效果渲染事件,玩家物品栏界面中的物品栏药水效果会触发此事件，尝试移除物品栏药水效果的渲染
 */
@OnlyIn(Dist.CLIENT)
public class InventoryEffectRenderEvent extends Event {

	private final List<MobEffectInstance> effectList;

	public InventoryEffectRenderEvent(List<MobEffectInstance> list) {
		this.effectList = list;
	}

	public List<MobEffectInstance> getEffectList() {
		return effectList;
	}

	public void removeEffect(MobEffectInstance effectInstance) {
		effectList.remove(effectInstance);
	}

}
