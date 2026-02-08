package first.wildfires.mixin.minecraft;

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
    private void tick(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        Level level = itemEntity.level();
        ItemStack item = itemEntity.getItem();
        if (!level.isClientSide() && item.is(TFCItems.METAL_ITEMS.get(Metal.Default.STEEL).get(Metal.ItemType.ROD).get())) {
            CompoundTag tag = itemEntity.getPersistentData();
            boolean onGround = itemEntity.onGround();
            boolean lastOnGround = tag.getBoolean("lastOnGround");
            if (onGround && !lastOnGround) {
                WildfiresUtil.playSound(
                        level,
                        itemEntity.blockPosition(),
                        SoundRegister.MetalPipe.get(),
                        SoundSource.BLOCKS
                );
            }
            if (onGround != lastOnGround) {
                tag.putBoolean("lastOnGround", onGround);
            }
        }
    }

}
