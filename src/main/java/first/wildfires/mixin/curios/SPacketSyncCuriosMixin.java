package first.wildfires.mixin.curios;

import first.wildfires.utils.CuriosUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;

/** Invalidates Wildfires' read cache after Curios installs a complete client inventory snapshot. */
@Mixin(value = SPacketSyncCurios.class, remap = false)
public abstract class SPacketSyncCuriosMixin {

    @Inject(method = "lambda$handle$0", at = @At("RETURN"), remap = false)
    private static void wildfires$afterClientInventorySync(SPacketSyncCurios packet, Entity entity,
                                                            ICuriosItemHandler handler, CallbackInfo ci) {
        if (entity instanceof LivingEntity livingEntity) {
            CuriosUtil.invalidate(livingEntity);
        }
    }
}
