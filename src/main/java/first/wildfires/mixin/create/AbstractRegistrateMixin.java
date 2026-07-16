package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.MenuBuilder;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import first.wildfires.client.screen.TemperatureAttributeFilterScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractRegistrate.class)
public class AbstractRegistrateMixin {

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "menu(Ljava/lang/Object;Ljava/lang/String;Lcom/tterrag/registrate/builders/MenuBuilder$ForgeMenuFactory;Lcom/tterrag/registrate/util/nullness/NonNullSupplier;)Lcom/tterrag/registrate/builders/MenuBuilder;")
    private <T extends AbstractContainerMenu, SC extends Screen & MenuAccess<T>, P> MenuBuilder<T, SC, P> menu(P parent, String name, MenuBuilder.ForgeMenuFactory<T> factory, NonNullSupplier<MenuBuilder.ScreenFactory<T, SC>> screenFactory, Operation<MenuBuilder<T, SC, P>> original) {
        if (name.equals("attribute_filter")) {
            MenuBuilder.ForgeMenuFactory<T> menuFactory = (type, id, inv, extraData) -> (T) (new AttributeFilterMenu(type, id, inv, extraData));
            NonNullSupplier<MenuBuilder.ScreenFactory<T, SC>> supplier = () -> (c, inventory, component) -> (SC) (new TemperatureAttributeFilterScreen((AttributeFilterMenu) c, inventory, component));
            return original.call(parent, name, menuFactory, supplier);
        }
        return original.call(parent, name, factory, screenFactory);
    }
}
