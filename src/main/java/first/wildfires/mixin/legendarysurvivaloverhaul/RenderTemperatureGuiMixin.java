package first.wildfires.mixin.legendarysurvivaloverhaul;


import first.wildfires.utils.CuriosUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureDisplayEnum;
import sfiomn.legendarysurvivaloverhaul.client.render.RenderTemperatureGui;
import sfiomn.legendarysurvivaloverhaul.config.Config;
import sfiomn.legendarysurvivaloverhaul.registry.ItemRegistry;

import java.util.Objects;
import java.util.Random;

@Mixin(value = RenderTemperatureGui.class, remap = false)
public class RenderTemperatureGuiMixin {

    @Mutable
    @Shadow(remap = false)
    public static IGuiOverlay TEMPERATURE_GUI;

    @Shadow
    @Final
    private static Random rand;

    @Inject(
            method = "<clinit>",
            at = @At(value = "RETURN")
    )
    private static void clinit(CallbackInfo ci) {
        TEMPERATURE_GUI = (forgeGui, guiGraphics, partialTicks, width, height) -> {
            if (Config.Baked.temperatureEnabled && !Minecraft.getInstance().options.hideGui && forgeGui.shouldDrawSurvivalElements()) {
                Player player = forgeGui.getMinecraft().player;
                if (player != null) {
                    rand.setSeed((long) player.tickCount * 445L);
                    forgeGui.setupOverlayRenderState(true, false);
                    boolean equipped = CuriosUtil.isEquipped(player, ItemRegistry.THERMOMETER.get());
                    if (Objects.requireNonNull(Config.Baked.temperatureDisplayMode) == TemperatureDisplayEnum.SYMBOL && !equipped) {
                        Minecraft.getInstance().getProfiler().push("temperature_gui");
                        RenderTemperatureGui.drawTemperatureAsSymbol(guiGraphics, player, width, height);
                        Minecraft.getInstance().getProfiler().pop();
                    }
                    if (equipped) {
                        Minecraft.getInstance().getProfiler().push("body_temperature_gui");
                        RenderTemperatureGui.drawBodyTemperature(guiGraphics, player, width, height);
                        Minecraft.getInstance().getProfiler().pop();
                    }
                }
            }
        };
    }


}
