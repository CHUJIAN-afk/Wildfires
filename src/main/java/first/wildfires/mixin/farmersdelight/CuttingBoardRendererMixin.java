package first.wildfires.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.client.renderer.CuttingBoardRenderer;

@Mixin(value = CuttingBoardRenderer.class,remap = false)
public class CuttingBoardRendererMixin {

    @ModifyExpressionValue(
            method = "render(Lvectorwing/farmersdelight/common/block/entity/CuttingBoardBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;isGui3d()Z"
            )
    )
    private boolean isGui3d(boolean original) {
        return false;
    }
}
