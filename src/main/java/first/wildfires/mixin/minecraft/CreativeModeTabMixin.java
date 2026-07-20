package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import first.wildfires.api.customEvent.CreativeTabBuildEvent;
import first.wildfires.register.CreativeModeTabRegister;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {

    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @WrapMethod(method = "buildContents")
    private void simulated$buildContents(CreativeModeTab.ItemDisplayParameters parameters, Operation<Void> original) {
        final CreativeModeTab self = (CreativeModeTab) (Object) this;
        if (self == CreativeModeTabRegister.WildfiresTab.get()) {
            final List<ItemStack> displayItems = new LinkedList<>();
            final Set<ItemStack> searchItems = new LinkedHashSet<>();
            CreativeModeTabRegister.processItems(displayItems::add, searchItems::add);
            this.displayItems = displayItems;
            this.displayItemsSearchTab = searchItems;
        } else {
            original.call(parameters);
        }

        // Wildfires uses a full empty row as the background for each category banner.
        if (self != CreativeModeTabRegister.WildfiresTab.get()) {
            this.displayItems.removeIf(ItemStack::isEmpty);
            this.displayItemsSearchTab.removeIf(ItemStack::isEmpty);
        }
        self.rebuildSearchTree();
    }
}
