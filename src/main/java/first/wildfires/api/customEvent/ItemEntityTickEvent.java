package first.wildfires.api.customEvent;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.eventbus.api.Event;

public abstract class ItemEntityTickEvent extends Event {

    private final ItemEntity itemEntity;

    public ItemEntityTickEvent(ItemEntity itemEntity) {
        this.itemEntity = itemEntity;
    }

    public ItemEntity getItemEntity() {
        return itemEntity;
    }

    public static class Pre extends ItemEntityTickEvent {

        public Pre(ItemEntity itemEntity) {
            super(itemEntity);
        }
    }

    public static class Post extends ItemEntityTickEvent {

        public Post(ItemEntity itemEntity) {
            super(itemEntity);
        }
    }

}