package first.wildfires.mixin.legendarysurvivaloverhaul;

import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sfiomn.legendarysurvivaloverhaul.client.render.RenderHealthGui;

@Mixin(value = RenderHealthGui.class,remap = false)
public class RenderHealthGuiMixin {

    @Final
    @Mutable
    @Shadow(remap = false)
    public static IGuiOverlay HEALTH_GUI;

    @Inject(
            method = "<clinit>",
            at = @At(value = "RETURN")
    )
    private static void clinit(CallbackInfo ci) {
        HEALTH_GUI = (forgeGui, guiGraphics, partialTicks, width, height) -> {
        };
    }


}
