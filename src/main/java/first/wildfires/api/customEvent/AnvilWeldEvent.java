package first.wildfires.api.customEvent;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

public class AnvilWeldEvent extends Event {

    private ItemStack left;
    private ItemStack right;

    public AnvilWeldEvent(ItemStack left, ItemStack right) {
        this.left = left;
        this.right = right;
    }

    public void setRight(ItemStack right) {
        this.right = right;
    }

    public void setLeft(ItemStack left) {
        this.left = left;
    }

    public ItemStack getRight() {
        return right;
    }

    public ItemStack getLeft() {
        return left;
    }

}
