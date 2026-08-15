package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.client.space.ObjComponentVisibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import net.minecraftforge.client.model.renderable.ITextureRenderTypeLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the component visibility operation missing from Forge's runtime OBJ renderer. */
@Mixin(targets = "net.minecraftforge.client.model.renderable.CompositeRenderable$Component", remap = false)
public abstract class CompositeRenderableComponentMixin {

    @Shadow @Final
    private String name;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void wildfires$skipHiddenComponent(PoseStack poses, MultiBufferSource buffers,
                                               ITextureRenderTypeLookup renderTypes,
                                               int light, int overlay,
                                               CompositeRenderable.Transforms transforms,
                                               CallbackInfo callback) {
        if (!ObjComponentVisibility.visible(name)) callback.cancel();
    }
}
