package first.wildfires.mixin.tfc;

import net.dries007.tfc.client.model.entity.JavelinModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JavelinModel.class, remap = false)
public class JavelinModelMixin {

    @Inject(
            method = "createBodyLayer",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void createBodyLayer(CallbackInfoReturnable<LayerDefinition> cir) {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create(), PartPose.offset(0.1667F, -1.1667F, 0.3333F));
        bb_main.addOrReplaceChild("cube_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.1667F, -3.8333F, -0.3333F, 1.5708F, 0.7854F, 3.1416F));
        bb_main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(-7, 13).addBox(-4.5F, -0.5F, -2.5F, 7.0F, 0.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(1, 6).addBox(-2.0F, -1.0F, 5.0F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.1F))
                .texOffs(50, 0).addBox(-2.0F, -1.5F, 30.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(-0.3F))
                .texOffs(1, 1).addBox(-2.0F, -1.0F, 5.0F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.3333F, -7.3333F, 0.6667F, 0.0F, -1.5708F, -1.5708F));
        bb_main.addOrReplaceChild("stick_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-0.5F, -0.5F, -13.0F, 1.0F, 1.0F, 29.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1667F, 11.1667F, -0.3333F, 0.0F, -1.5708F, -1.5708F));
        cir.setReturnValue(LayerDefinition.create(meshdefinition, 64, 64));
    }
}
