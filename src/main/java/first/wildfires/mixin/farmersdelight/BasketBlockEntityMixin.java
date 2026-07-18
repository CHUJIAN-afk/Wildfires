package first.wildfires.mixin.farmersdelight;

import net.dries007.tfc.common.blockentities.TFCChestBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

@Mixin(value = BasketBlockEntity.class, remap = false)
public class BasketBlockEntityMixin {
    private static final int TFC_CHEST_SIZE = 18;

    @Shadow(remap = false)
    private NonNullList<ItemStack> items;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeTfcChestSize(CallbackInfo ci) {
        items = NonNullList.withSize(TFC_CHEST_SIZE, ItemStack.EMPTY);
    }

    @Inject(method = "m_6555_", at = @At("HEAD"), cancellable = true, remap = false)
    private void createTfcChestMenu(int containerId, Inventory playerInventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        cir.setReturnValue(new ChestMenu(MenuType.GENERIC_9x2, containerId, playerInventory, (BasketBlockEntity) (Object) this, 2));
    }

    @Inject(method = "m_142466_", at = @At("TAIL"), remap = false)
    private void dropItemsOutsideTfcChestRules(CompoundTag tag, CallbackInfo ci) {
        BasketBlockEntity basket = (BasketBlockEntity) (Object) this;
        Level level = basket.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        boolean changed = false;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && !TFCChestBlockEntity.isValid(stack)) {
                Containers.dropItemStack(level, basket.getBlockPos().getX(), basket.getBlockPos().getY(), basket.getBlockPos().getZ(), stack);
                items.set(slot, ItemStack.EMPTY);
                changed = true;
            }
        }

        ListTag savedItems = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = savedItems.size() - 1; index >= 0; index--) {
            CompoundTag savedItem = savedItems.getCompound(index);
            int slot = savedItem.getByte("Slot") & 255;
            if (slot >= TFC_CHEST_SIZE) {
                ItemStack stack = ItemStack.of(savedItem);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, basket.getBlockPos().getX(), basket.getBlockPos().getY(), basket.getBlockPos().getZ(), stack);
                }
                savedItems.remove(index);
                changed = true;
            }
        }

        if (changed) {
            basket.setChanged();
        }
    }

    public boolean m_7013_(int slot, ItemStack stack) {
        return TFCChestBlockEntity.isValid(stack);
    }
}
