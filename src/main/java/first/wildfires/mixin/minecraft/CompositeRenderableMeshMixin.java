package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.wildfires.client.space.NtmObjFastRenderer;
import first.wildfires.client.space.ObjComponentVisibility;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.renderable.ITextureRenderTypeLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Exact primitive submission for explicitly selected NTM OBJ meshes and vanilla BufferBuilder. */
@Mixin(targets = "net.minecraftforge.client.model.renderable.CompositeRenderable$Mesh", remap = false)
public abstract class CompositeRenderableMeshMixin {

    @Shadow @Final
    private ResourceLocation texture;

    @Shadow @Final
    private List<BakedQuad> quads;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void wildfires$renderSelectedMeshWithoutBulkAllocations(
            PoseStack poses, MultiBufferSource buffers,
            ITextureRenderTypeLookup renderTypes, int light, int overlay,
            CallbackInfo callback) {
        if (!ObjComponentVisibility.fastPathActive()) return;
        VertexConsumer consumer = buffers.getBuffer(renderTypes.get(texture));
        if (NtmObjFastRenderer.render(poses.last(), consumer, quads, light, overlay)) {
            callback.cancel();
        }
    }
}
