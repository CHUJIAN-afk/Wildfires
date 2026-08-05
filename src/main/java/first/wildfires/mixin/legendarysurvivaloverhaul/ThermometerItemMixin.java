package first.wildfires.mixin.legendarysurvivaloverhaul;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sfiomn.legendarysurvivaloverhaul.common.items.ThermometerItem;

import java.util.Locale;

/** Restricts the LSO thermometer action-bar reading to one decimal without changing thermal data. */
@Mixin(value = ThermometerItem.class, remap = false)
public abstract class ThermometerItemMixin {

    @ModifyArg(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"
            ),
            index = 0,
            remap = true
    )
    private Component wildfires$formatThermometerReading(Component original) {
        String text = original.getString();
        int unitIndex = text.lastIndexOf('°');
        if (unitIndex <= 0) {
            return original;
        }
        try {
            float value = Float.parseFloat(text.substring(0, unitIndex));
            if (Math.abs(value) < 0.05F) {
                value = 0.0F;
            }
            return Component.literal(String.format(Locale.ROOT, "%.1f%s", value, text.substring(unitIndex)))
                    .withStyle(original.getStyle());
        } catch (NumberFormatException ignored) {
            return original;
        }
    }
}
