package first.wildfires.mixin.farmersdelight;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import vectorwing.farmersdelight.client.renderer.CuttingBoardRenderer;

@Mixin(value = CuttingBoardRenderer.class, remap = false)
public abstract class CuttingBoardRendererMixin {

    @Shadow
    public abstract void renderItemLayingDown(PoseStack matrixStackIn, Direction direction, float xOffset, int yIndex, float zOffset);

    @WrapMethod(method = "renderBlock")
    private void isGui3d(PoseStack matrixStackIn, Direction direction, float xOffset, int yIndex, float zOffset, Operation<Void> original) {
        this.renderItemLayingDown(matrixStackIn, direction, xOffset, yIndex, zOffset);
    }
}
