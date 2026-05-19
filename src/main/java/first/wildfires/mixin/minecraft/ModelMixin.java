package first.wildfires.mixin.minecraft;

import net.dries007.tfc.client.model.entity.JavelinModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Model.class)
public class ModelMixin {

    @Inject(
            method = "renderType",
            at = @At("HEAD"),
            cancellable = true
    )
    private void renderType(ResourceLocation pLocation, CallbackInfoReturnable<RenderType> cir) {
        Model model = (Model) (Object) this;
        if (model instanceof JavelinModel) {
            cir.setReturnValue(RenderType.entityTranslucent(pLocation));
        }
    }
}
