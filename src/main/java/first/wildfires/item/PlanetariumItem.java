package first.wildfires.item;

import first.wildfires.client.celestial.PlanetariumClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Non-consuming player entry point for the in-game planetarium screen. */
public final class PlanetariumItem extends Item {

    public PlanetariumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PlanetariumClient::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
