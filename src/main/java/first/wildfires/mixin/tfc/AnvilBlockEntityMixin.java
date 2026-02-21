package first.wildfires.mixin.tfc;

import first.wildfires.api.customEvent.AnvilWeldEvent;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnvilBlockEntity.class, remap = false)
public class AnvilBlockEntityMixin {

    @Inject(
            method = "weld",
            at = @At("HEAD"),
            cancellable = true
    )
    private void weld(Player player, CallbackInfoReturnable<InteractionResult> cir) {
        AnvilBlockEntity anvilBlockEntity = (AnvilBlockEntity) (Object) this;
        InventoryBlockEntityAccessor<?> accessor = (InventoryBlockEntityAccessor<?>) anvilBlockEntity;
        Level level = anvilBlockEntity.getLevel();
        if (level != null && accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory) {
            ItemStack left = inventory.getLeft();
            ItemStack right = inventory.getRight();
            WeldingRecipe recipe = level.getRecipeManager().getRecipeFor(TFCRecipeTypes.WELDING.get(), inventory, level).orElse(null);
            if (recipe == null && !left.isEmpty() && !right.isEmpty()) {
                IHeat leftHeat = HeatCapability.get(left);
                IHeat rightHeat = HeatCapability.get(right);
                if (leftHeat != null && rightHeat != null && leftHeat.canWeld()) {
                    AnvilWeldEvent event = new AnvilWeldEvent(left, right);
                    WildfiresUtil.post(event);
                    ItemStack resultItem = event.getResultItem();
                    if (resultItem != null) {
                        cir.setReturnValue(InteractionResult.SUCCESS);
                    }
                }
            }
        }
    }

}
