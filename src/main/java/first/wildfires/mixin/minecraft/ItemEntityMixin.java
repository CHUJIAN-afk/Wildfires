package first.wildfires.mixin.minecraft;

import first.wildfires.api.customEvent.ItemEntityTickEvent;
import first.wildfires.register.SoundRegister;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void tickHead(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemEntityTickEvent.Pre event = new ItemEntityTickEvent.Pre(itemEntity);
        WildfiresUtil.post(event);
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void tickTail(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemEntityTickEvent.Post event = new ItemEntityTickEvent.Post(itemEntity);
        WildfiresUtil.post(event);
    }

}
