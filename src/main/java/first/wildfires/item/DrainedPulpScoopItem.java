package first.wildfires.item;

import first.wildfires.register.ItemRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

public class DrainedPulpScoopItem extends Item {
    private static final ResourceLocation PULP_FILM_ID = ResourceLocation.fromNamespaceAndPath("kubejs", "pulp_film");

    public DrainedPulpScoopItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            Item film = ForgeRegistries.ITEMS.getValue(PULP_FILM_ID);
            if (film == null) {
                return InteractionResultHolder.fail(stack);
            }
            ItemStack filmStack = new ItemStack(film);
            if (!serverPlayer.getInventory().add(filmStack)) {
                serverPlayer.drop(filmStack, false);
            }
            serverPlayer.setItemInHand(hand, new ItemStack(ItemRegister.PulpScoop.get()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return new ItemStack(ItemRegister.PulpScoop.get());
    }
}
