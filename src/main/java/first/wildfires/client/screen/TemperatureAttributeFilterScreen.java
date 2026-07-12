package first.wildfires.client.screen;

import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TemperatureAttributeFilterScreen extends AttributeFilterScreen {

    public TemperatureAttributeFilterScreen(AttributeFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
