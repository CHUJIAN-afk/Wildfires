package first.wildfires.mixin.curios;

import first.wildfires.utils.CuriosUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncStack;

/** Invalidates Wildfires' read cache after Curios applies one synchronized client slot change. */
@Mixin(value = SPacketSyncStack.class, remap = false)
public abstract class SPacketSyncStackMixin {

    @Inject(method = "lambda$handle$1", at = @At("RETURN"), remap = false)
    private static void wildfires$afterClientStackSync(SPacketSyncStack packet, Entity entity,
                                                        ICurioStacksHandler handler, CallbackInfo ci) {
        if (entity instanceof LivingEntity livingEntity) {
            CuriosUtil.invalidate(livingEntity);
        }
    }
}
