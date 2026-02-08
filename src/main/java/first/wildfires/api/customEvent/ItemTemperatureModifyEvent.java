package first.wildfires.api.customEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 物品温度修改事件,获取物品温度时触发此事件，尝试修改物品温度（传说生存单位）
 */
public class ItemTemperatureModifyEvent extends Event {

    private final ItemStack itemStack;
    private final float oldTemperature;
    private float newTemperature;

    public ItemTemperatureModifyEvent(ItemStack itemStack, float oldTemperature) {
        this.itemStack = itemStack;
        this.oldTemperature = oldTemperature;
        this.newTemperature = oldTemperature;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public float getOldTemperature() {
        return oldTemperature;
    }

    public float getNewTemperature() {
        return newTemperature;
    }

    public void setNewTemperature(float newTemperature) {
        this.newTemperature = newTemperature;
    }

}
