package first.wildfires.mixin.kubejs;

import dev.latvian.mods.kubejs.recipe.ingredientaction.DamageAction;
import first.wildfires.register.ItemRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes KubeJS damageIngredient recipes return an empty glue container on the final use. */
@Mixin(value = DamageAction.class, remap = false)
public class DamageActionMixin {
    private static final ResourceLocation CREATE_SUPER_GLUE_ID = ResourceLocation.fromNamespaceAndPath("create", "super_glue");

    @Inject(method = "transform", at = @At("RETURN"), cancellable = true)
    private void returnEmptyGlueWhenExhausted(ItemStack ingredient, int slot, CraftingContainer container,
                                              CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue().isEmpty()
                && CREATE_SUPER_GLUE_ID.equals(ForgeRegistries.ITEMS.getKey(ingredient.getItem()))) {
            cir.setReturnValue(new ItemStack(ItemRegister.EmptySuperGlue.get()));
        }
    }
}
