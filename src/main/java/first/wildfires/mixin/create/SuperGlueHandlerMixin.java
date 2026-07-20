package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.glue.SuperGlueHandler;
import first.wildfires.register.ItemRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/** Replaces the exhausted offhand glue at Create's actual durability-consumption call. */
@Mixin(value = SuperGlueHandler.class, remap = false)
public class SuperGlueHandlerMixin {

    @WrapOperation(
            method = "glueInOffHandAppliesOnBlockPlace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"
            )
    )
    private static void replaceExhaustedSuperGlue(ItemStack glue, int amount, LivingEntity entity,
                                                  Consumer<LivingEntity> onBreak, Operation<Void> original,
                                                  BlockEvent.EntityPlaceEvent event, BlockPos placedPos, Player player) {
        boolean exhausted = !player.level().isClientSide()
                && glue.getDamageValue() + amount >= glue.getMaxDamage();

        original.call(glue, amount, entity, onBreak);

        if (exhausted) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegister.EmptySuperGlue.get()));
        }
    }
}
