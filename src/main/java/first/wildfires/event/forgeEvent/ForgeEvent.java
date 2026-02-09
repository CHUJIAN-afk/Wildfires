package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.api.customEvent.FoodRottenEvent;
import first.wildfires.api.customEvent.ItemEntityTickEvent;
import first.wildfires.register.SoundRegister;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {

	@SubscribeEvent
	public static void itemEntityTick(ItemEntityTickEvent.Post event) {
		ItemEntity itemEntity = event.getItemEntity();
		ItemStack itemStack = itemEntity.getItem();
		IHeat iHeat = HeatCapability.get(itemStack);
		if (iHeat != null && iHeat.canWork() && itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:tool_head"))) {
			CompoundTag tag = itemStack.getOrCreateTag();
			tag.putInt("Quenching", tag.getInt("Quenching") + 1);
			tag.putInt("Polish", tag.getInt("Polish") + 1);
			if (iHeat.canWeld()) {
				tag.putBoolean("Broken", true);
			}
		}
	}

	@SubscribeEvent
	public static void metalPipeSound(ItemEntityTickEvent.Post event) {
		ItemEntity itemEntity = event.getItemEntity();
		Level level = itemEntity.level();
		ItemStack item = itemEntity.getItem();
		if (!level.isClientSide() && item.is(TFCItems.METAL_ITEMS.get(Metal.Default.STEEL).get(Metal.ItemType.ROD).get())) {
			CompoundTag tag = itemEntity.getPersistentData();
			boolean onGround = itemEntity.onGround();
			boolean lastOnGround = tag.getBoolean("lastOnGround");
			if (onGround && !lastOnGround) {
				WildfiresUtil.playSound(
						level,
						itemEntity.blockPosition(),
						SoundRegister.MetalPipe.get(),
						SoundSource.BLOCKS
				);
			}
			if (onGround != lastOnGround) {
				tag.putBoolean("lastOnGround", onGround);
			}
		}
	}

	@SubscribeEvent
	public static void hurt(LivingHurtEvent event) {
		if (event.getSource().getEntity() instanceof LivingEntity attacker) {
			ItemStack item = attacker.getMainHandItem();
			if (item.is(Tags.Items.TOOLS)) {
				LivingEntity target = event.getEntity();
				AABB aabb = target.getBoundingBox();
				Vec3 center = attacker.getBoundingBox().getCenter();
				double dx = Math.max(aabb.minX - center.x, Math.max(0, center.x - aabb.maxX));
				double dy = Math.max(aabb.minY - center.y, Math.max(0, center.y - aabb.maxY));
				double dz = Math.max(aabb.minZ - center.z, Math.max(0, center.z - aabb.maxZ));
				float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (distance <= 4) {
					event.setAmount(event.getAmount() * (0.5f + (distance * 0.125f)));
				}
			}
		}
	}

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
			if (polish > 0) {
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
