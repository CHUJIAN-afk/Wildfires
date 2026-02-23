package first.wildfires.api.customEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 食物腐烂事件,玩家可视界面中的腐烂物品会触发此事件，尝试将腐烂物品转化为新的自定义物品
 */
public class FoodRottenEvent extends Event {

    private ItemStack itemStack;

    public FoodRottenEvent(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

}
