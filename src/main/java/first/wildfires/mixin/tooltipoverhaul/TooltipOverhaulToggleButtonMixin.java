package first.wildfires.mixin.tooltipoverhaul;

import first.wildfires.client.TooltipOverhaulLocalization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.xylonity.tooltipoverhaul.client.screen.config.TooltipOverhaulConfigScreen$ToggleButton", remap = false)
public class TooltipOverhaulToggleButtonMixin {
    @ModifyArg(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;m_237113_(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;", remap = false),
            index = 0,
            require = 0
    )
    private String wildfires$localizeToggleComponentText(String text) {
        return TooltipOverhaulLocalization.localize(text);
    }

    @ModifyArg(
            method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280056_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", remap = false),
            index = 1,
            require = 0
    )
    private String wildfires$localizeToggleText(String text) {
        return TooltipOverhaulLocalization.localize(text);
    }
}
