package first.wildfires.mixin.legendarysurvivaloverhaul;

import first.wildfires.api.customEvent.PlayerTargetTemperatureModifyEvent;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

@Mixin(value = TemperatureUtil.class,remap = false)
public class TemperatureUtilMixin {

    @Inject(
            method = "getPlayerTargetTemperature",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void getPlayerTargetTemperature(Player player, CallbackInfoReturnable<Float> cir) {
        PlayerTargetTemperatureModifyEvent event = new PlayerTargetTemperatureModifyEvent(player, cir.getReturnValue());
        WildfiresUtil.post(event);
        float oldTemperature = event.getOldTemperature();
        float newTemperature = event.getNewTemperature();
        if (oldTemperature != newTemperature) {
            cir.setReturnValue(newTemperature);
        }
        /*
        List<ItemStack> list = new ArrayList<>(player.getInventory().items).stream()
                .filter(itemStack -> !player.getMainHandItem().equals(itemStack) && !player.getOffhandItem().equals(itemStack))
                .filter(ItemStack::isEmpty)
                .toList();
        float temperature = create_power$getTemperature(list) * 0.1f;
        List<ItemStack> itemStackList = new ArrayList<>();
        player.getArmorSlots().forEach(itemStackList::add);

        CuriosApi.getCuriosInventory(player).ifPresent(iCuriosItemHandler -> {
            IItemHandlerModifiable equippedCurios = iCuriosItemHandler.getEquippedCurios();
            for (int i = 0; i < equippedCurios.getSlots(); i++) {
                ItemStack itemStack = equippedCurios.getStackInSlot(i);
                if (!itemStack.isEmpty()) {
                    itemStackList.add(itemStack);
                }
            }
        });

        itemStackList.removeIf(ItemStack::isEmpty);
        temperature += create_power$getTemperature(itemStackList);
        cir.setReturnValue(temperature + cir.getReturnValue());
        */
    }
/*
    @Unique
    private static float create_power$getTemperature(List<ItemStack> list) {
        return (float) list.stream()
                .map(CapabilityUtil::getTempItemCapability)
                .map(TemperatureItemCapability::getWorldTemperatureLevel)
                .map(i -> i - TemperatureEnum.NORMAL.getMiddle())
                .mapToDouble(Float::floatValue)
                .sum();
    }
*/
}
