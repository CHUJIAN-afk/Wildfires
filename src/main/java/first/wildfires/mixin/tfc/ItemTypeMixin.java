package first.wildfires.mixin.tfc;

import first.wildfires.item.GeckoSimpleArmorItem;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.registry.RegistryMetal;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Metal.ItemType.class, remap = false)
public class ItemTypeMixin {

    @Inject(method = "create", at = @At("HEAD"), remap = false, cancellable = true)
    public void create(RegistryMetal metal, CallbackInfoReturnable<Item> cir) {
        var name = wildfires$getType(((Metal.ItemType) (Object) this).name());

        if (name == null)
            return;

        if (metal == Metal.Default.BLUE_STEEL || metal == Metal.Default.RED_STEEL)
            cir.setReturnValue(new GeckoSimpleArmorItem(metal.armorTier(), name, Metal.ItemType.properties(metal)));
    }

    @Unique
    private static ArmorItem.Type wildfires$getType(String name) {
        return switch (name) {
            case "HELMET" -> ArmorItem.Type.HELMET;
            case "CHESTPLATE" -> ArmorItem.Type.CHESTPLATE;
            case "GREAVES" -> ArmorItem.Type.LEGGINGS;
            case "BOOTS" -> ArmorItem.Type.BOOTS;
            default -> null;
        };
    }
}
