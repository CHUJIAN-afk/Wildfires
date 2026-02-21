package first.wildfires.api.customEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class AnvilWeldEvent extends Event {

    private ItemStack left;
    private ItemStack right;
    private ItemStack resultItem;

    public AnvilWeldEvent(ItemStack left, ItemStack right) {
        this.left = left;
        this.right = right;
        this.resultItem = null;
    }

    public void setRight(ItemStack right) {
        this.right = right;
    }

    public void setResultItem(ItemStack resultItem) {
        this.resultItem = resultItem;
    }

    public void setLeft(ItemStack left) {
        this.left = left;
    }

    public ItemStack getResultItem() {
        return resultItem;
    }

    public ItemStack getRight() {
        return right;
    }

    public ItemStack getLeft() {
        return left;
    }

}
