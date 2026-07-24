package first.wildfires.mixin.create;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AllGuiTextures.class, remap = false)
public class AllGuiTexturesMixin {

    @SuppressWarnings("all")
    @Inject(
            method = "<clinit>",
            at = @At(value = "TAIL")
    )
    private static void clinit(CallbackInfo ci) {
        ((AllGuiTexturesAccessor) (Object) AllGuiTextures.ATTRIBUTE_FILTER).wildfires$setLocation(
                ResourceLocation.fromNamespaceAndPath("wildfires", "textures/gui/filters.png"));
    }
}
