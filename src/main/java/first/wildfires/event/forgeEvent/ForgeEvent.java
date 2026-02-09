package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.api.customEvent.FoodRottenEvent;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {

	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		CompoundTag tag = event.getEntity().getMainHandItem().getTag();
		if (tag != null) {
			int polish = tag.getInt("Polish");
			if (polish > 0) {
				event.setNewSpeed(event.getNewSpeed() * Math.min(2, (1 + polish * 0.001f)));
			}
		}
	}

	@SubscribeEvent
	public static void itemModify(ItemAttributeModifierEvent event) {
		ItemStack itemStack = event.getItemStack();
		CompoundTag tag = itemStack.getTag();
		if (tag != null && event.getSlotType() == EquipmentSlot.MAINHAND) {
			int polish = tag.getInt("Polish");
			String name = "Polish";
			AttributeModifier modifier = new AttributeModifier(
					WildfiresUtil.getUUID(name),
					name,
					Math.min(0.15, polish * 0.001),
					AttributeModifier.Operation.MULTIPLY_TOTAL
			);
			event.addModifier(Attributes.ATTACK_DAMAGE, modifier);
		}
	}

	@SubscribeEvent
	public static void PlayerTick(TickEvent.PlayerTickEvent event) {
		Player player = event.player;
		Level level = player.level();
		if (Wildfires.TFCLoaded && !level.isClientSide()) {
			AbstractContainerMenu containerMenu = player.containerMenu;
			for (Slot slot : containerMenu.slots) {
				ItemStack item = slot.getItem();
				if (FoodCapability.isRotten(item)) {
					FoodRottenEvent rottenEvent = new FoodRottenEvent(item);
					ItemStack oldItemStack = rottenEvent.getOldItemStack();
					ItemStack newItemStack = rottenEvent.getNewItemStack();
					if (!oldItemStack.equals(newItemStack)) {
						slot.set(newItemStack);
					}
				}
			}
		}
	}

}
