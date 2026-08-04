package first.wildfires.item;

import first.wildfires.register.ItemRegister;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FilledPulpScoopItem extends Item {
    private static final String DRYING_TICKS_TAG = "PulpScoopDryingTicks";
    private static final int DRYING_TICKS = 100;

    public FilledPulpScoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        int dryingTicks = stack.getOrCreateTag().getInt(DRYING_TICKS_TAG) + 1;
        if (dryingTicks < DRYING_TICKS) {
            stack.getOrCreateTag().putInt(DRYING_TICKS_TAG, dryingTicks);
            return;
        }

        ItemStack drainedScoop = new ItemStack(ItemRegister.DrainedPulpScoop.get());
        for (int inventorySlot = 0; inventorySlot < player.getInventory().items.size(); inventorySlot++) {
            if (player.getInventory().items.get(inventorySlot) == stack) {
                player.getInventory().items.set(inventorySlot, drainedScoop);
                return;
            }
        }
        for (int offhandSlot = 0; offhandSlot < player.getInventory().offhand.size(); offhandSlot++) {
            if (player.getInventory().offhand.get(offhandSlot) == stack) {
                player.getInventory().offhand.set(offhandSlot, drainedScoop);
                return;
            }
        }
    }
}
