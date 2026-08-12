package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.notenoughmail.kubejs_tfc.util.implementation.custom.climate.KubeJSClimateModel;
import net.dries007.tfc.client.screen.ClimateScreen;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.tracker.WorldTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves TerraFirmaEarth in control of the climate screen and only replaces its classification label for a
 * KubeJS-defined climate model. The priority must be higher than TFE's default 1000 so this injector is allowed to
 * target the method body contributed by TFE's {@code @Overwrite}; this mixin does not overwrite that method itself.
 */
@Mixin(value = ClimateScreen.class, priority = 1100, remap = false)
public abstract class ClimateScreenMixin {
    @ModifyExpressionValue(
            method = {
                    "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    "m_280003_(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/newterraearth/tfe/client/NTEKoppenClimateClassification;translationKey()Ljava/lang/String;",
                    remap = false
            )
    )
    private String wildfires$useKubeJsClimateName(String originalTranslationKey) {
        final Level level = Minecraft.getInstance().level;
        if (level != null && WorldTracker.get(level).getClimateModel() instanceof KubeJSClimateModel kubeJsClimateModel) {
            final ResourceLocation climateId = Climate.getId(kubeJsClimateModel);
            return "kubejs.climate.name." + climateId.getPath();
        }
        return originalTranslationKey;
    }
}
