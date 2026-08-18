package first.wildfires.mixin.farmersdelight;

import net.dries007.tfc.common.blockentities.TFCChestBlockEntity;
import net.dries007.tfc.common.container.RestrictedChestContainer;
import net.dries007.tfc.common.container.TFCContainerTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

@Mixin(value = BasketBlockEntity.class, remap = false)
public abstract class BasketBlockEntityMixin implements Container {
    private static final int TFC_CHEST_SIZE = 18;

    @Shadow(remap = false)
    private NonNullList<ItemStack> items;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeTfcChestSize(CallbackInfo ci) {
        items = NonNullList.withSize(TFC_CHEST_SIZE, ItemStack.EMPTY);
    }

    @Inject(
            method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void createTfcChestMenu(int containerId, Inventory playerInventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        cir.setReturnValue(new RestrictedChestContainer(
                TFCContainerTypes.CHEST_9x2.get(),
                containerId,
                playerInventory,
                (BasketBlockEntity) (Object) this,
                2
        ));
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return TFCChestBlockEntity.isValid(stack);
    }
}
