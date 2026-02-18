package first.wildfires.event.forgeEvent;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import first.wildfires.Wildfires;
import first.wildfires.api.KineticData;
import first.wildfires.api.MobPoopData;
import first.wildfires.api.customEvent.*;
import first.wildfires.register.SoundRegister;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
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
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureItemCapability;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;


@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {

	@SubscribeEvent
	public static void entity(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();
		Level level = mob.level();
		if (!level.isClientSide()) {
			if (event.getSpawnType() == MobSpawnType.BREEDING) {
				List<Mob> mobList = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32), living -> living.distanceTo(mob) < 32 && living.getType() == mob.getType());
				if (mobList.size() > 16) {
					event.setSpawnCancelled(true);
				}
			}
		}
	}

	@SubscribeEvent
	public static void livingTick(LivingEvent.LivingTickEvent event){
		LivingEntity living = event.getEntity();
		Level level = living.level();
		if (!level.isClientSide()) {
			EntityType<?> type = living.getType();
			if (WildfiresUtil.PoopList.contains(type)) {
				for (MobPoopData data : WildfiresUtil.mobPoopDataList) {
					if (data.type() == type) {
						int ticks = data.ticks();
						CompoundTag tag = living.getPersistentData();
						int poopTicks = tag.getInt("MobPoopTicks");
						if (poopTicks >= ticks) {
							List<ItemStack> itemStackList = data.list();
							for (ItemStack itemStack : itemStackList) {
								living.spawnAtLocation(itemStack);
							}
							tag.remove("MobPoopTicks");
						} else {
							tag.putInt("MobPoopTicks", poopTicks + 1);
						}
						break;
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void itemTemperatureModify(ItemTemperatureModifyEvent event) {
		ItemStack itemStack = event.getItemStack();
		event.setNewTemperature(event.getNewTemperature() + HeatCapability.getTemperature(itemStack) * 0.1f);
	}

	@SubscribeEvent
	public static void playerTargetTemperatureModify(PlayerTargetTemperatureModifyEvent event) {
		Player player = event.getPlayer();
		double temperature = new ArrayList<>(player.getInventory().items).stream()
				.filter(itemStack -> !player.getMainHandItem().equals(itemStack) && !player.getOffhandItem().equals(itemStack))
				.filter(itemStack -> !itemStack.isEmpty())
				.map(CapabilityUtil::getTempItemCapability)
				.map(TemperatureItemCapability::getWorldTemperatureLevel)
				.map(i -> i - TemperatureEnum.NORMAL.getMiddle())
				.mapToDouble(Float::floatValue)
				.sum() * 0.1;
		List<ItemStack> itemStackList = new ArrayList<>();
		player.getArmorSlots().forEach(itemStackList::add);
		if (Wildfires.CurioLoaded) {
			CuriosApi.getCuriosInventory(player).ifPresent(iCuriosItemHandler -> {
				IItemHandlerModifiable equippedCurios = iCuriosItemHandler.getEquippedCurios();
				for (int i = 0; i < equippedCurios.getSlots(); i++) {
					itemStackList.add(equippedCurios.getStackInSlot(i));
				}
			});
		}
		temperature += itemStackList.stream()
				.filter(itemStack -> !itemStack.isEmpty())
				.map(CapabilityUtil::getTempItemCapability)
				.map(TemperatureItemCapability::getWorldTemperatureLevel)
				.map(i -> i - TemperatureEnum.NORMAL.getMiddle())
				.mapToDouble(Float::floatValue)
				.sum();
		event.setNewTemperature((float) (event.getNewTemperature() + temperature));
	}

	@SubscribeEvent
	public static void kineticBlockEntityTick(KineticBlockEntityTickEvent.Post event) {
		KineticBlockEntity kinetic = event.getKineticBlockEntity();
		Level level = kinetic.getLevel();
		if (level != null && !level.isClientSide()) {
			KineticNetwork network = kinetic.getOrCreateNetwork();
			if (network != null) {
				for (KineticData kineticData : WildfiresUtil.kineticDataList) {
					if (kineticData.block().equals(kinetic.getBlockState().getBlock())) {
						boolean c1 = kineticData.maxNetworkStress() != null && kineticData.maxNetworkStress() < network.calculateStress();
						boolean c2 = kineticData.maxSpeed() != null && Math.abs(kineticData.maxSpeed()) < Math.abs(kinetic.getSpeed());
						if (c1 || c2) {
							List<ItemStack> list = kineticData.list();
							level.destroyBlock(kinetic.getBlockPos(), list == null);
							if (list != null) {
                                for (ItemStack itemStack : list) {
                                    Vec3 center = kinetic.getBlockPos().getCenter();
                                    level.addFreshEntity(new ItemEntity(level, center.x(), center.y(), center.z(), itemStack.copy()));
                                }
                            }
                        }
						break;
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void itemEntityTick(ItemEntityTickEvent.Post event) {
		ItemEntity itemEntity = event.getItemEntity();
		ItemStack itemStack = itemEntity.getItem();
		IHeat iHeat = HeatCapability.get(itemStack);
		if (!itemEntity.level().isClientSide() && itemEntity.isInWaterRainOrBubble() && iHeat != null && (iHeat.getTemperature() >= iHeat.getWorkingTemperature() * 0.6) && itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:tool_head"))) {
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
		if (tag != null && event.getEntity().getMainHandItem().getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:polish"))) {
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

		if (tag != null && event.getSlotType() == EquipmentSlot.MAINHAND&&itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:polish"))) {
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
