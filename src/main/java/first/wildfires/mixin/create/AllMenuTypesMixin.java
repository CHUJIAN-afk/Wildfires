package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.tterrag.registrate.builders.MenuBuilder;
import com.tterrag.registrate.util.entry.MenuEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import first.wildfires.client.screen.TemperatureAttributeFilterScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = AllMenuTypes.class,remap = false)
public class AllMenuTypesMixin {

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "register(Ljava/lang/String;Lcom/tterrag/registrate/builders/MenuBuilder$ForgeMenuFactory;Lcom/tterrag/registrate/util/nullness/NonNullSupplier;)Lcom/tterrag/registrate/util/entry/MenuEntry;")
    private static <C extends AbstractContainerMenu, S extends Screen & MenuAccess<C>> MenuEntry<C> register(String name, MenuBuilder.ForgeMenuFactory<C> factory, NonNullSupplier<MenuBuilder.ScreenFactory<C, S>> screenFactory, Operation<MenuEntry<C>> original) {
        if (name.equals("attribute_filter")) {
            return original.call(name, (MenuBuilder.ForgeMenuFactory<C>) (type, id, inv, extraData) -> (C) (new AttributeFilterMenu(type, id, inv, extraData)), (NonNullSupplier<MenuBuilder.ScreenFactory<C, S>>) () -> (c, inventory, component) -> (S) (new TemperatureAttributeFilterScreen((AttributeFilterMenu) c, inventory, component)));
        }
        return original.call(name, factory, screenFactory);
    }
}
