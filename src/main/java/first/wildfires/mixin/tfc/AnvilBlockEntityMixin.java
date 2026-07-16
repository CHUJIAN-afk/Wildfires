package first.wildfires.mixin.tfc;

import com.mojang.logging.LogUtils;
import first.wildfires.api.customEvent.AnvilWeldEvent;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.dries007.tfc.common.recipes.WeldingRecipe;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

@Mixin(value = AnvilBlockEntity.class, remap = false)
public class AnvilBlockEntityMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "weld",
            at = @At("HEAD"),
            cancellable = true
    )
    private void weld(Player player, CallbackInfoReturnable<InteractionResult> cir) {
        AnvilBlockEntity anvilBlockEntity = (AnvilBlockEntity) (Object) this;
        InventoryBlockEntityAccessor<?> accessor = (InventoryBlockEntityAccessor<?>) anvilBlockEntity;
        Level level = anvilBlockEntity.getLevel();
        if (level != null && accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory) {
            ItemStack tool = inventory.getLeft();
            ItemStack material = inventory.getRight();
            WeldingRecipe recipe = level.getRecipeManager().getRecipeFor(TFCRecipeTypes.WELDING.get(), inventory, level).orElse(null);
            LOGGER.info("[WeldRepairDebug] weld invoked: physicalLeft={}, physicalRight={}, existingRecipe={}",
                    tool, material, recipe != null);
            if (recipe == null && !tool.isEmpty() && !material.isEmpty()) {
                IHeat toolHeat = HeatCapability.get(tool);
                IHeat materialHeat = HeatCapability.get(material);
                ItemStack flux = inventory.getStackInSlot(3);
                LOGGER.info("[WeldRepairDebug] gates: toolHeat={}, materialHeat={}, toolCanWeld={}, materialCanWeld={}, flux={}",
                        toolHeat != null,
                        materialHeat != null,
                        toolHeat != null && toolHeat.canWeld(),
                        materialHeat != null && materialHeat.canWeld(),
                        flux);
                if (toolHeat != null && materialHeat != null && toolHeat.canWeld() && materialHeat.canWeld() && !flux.isEmpty()) {
                    // The legacy KubeJS contract exposes repair material as left and tool as right.
                    AnvilWeldEvent event = new AnvilWeldEvent(material, tool);
                    WildfiresUtil.post(event);
                    LOGGER.info("[WeldRepairDebug] event posted: result={}, eventLeft={}, eventRight={}, rightDamage={}/{}",
                            event.getResult(), event.getLeft(), event.getRight(),
                            event.getRight().getDamageValue(), event.getRight().getMaxDamage());
                    if (event.getResult() == Event.Result.ALLOW) {
                        inventory.setStackInSlot(1, event.getLeft());
                        inventory.setStackInSlot(0, event.getRight());
                        flux.shrink(1);
                        anvilBlockEntity.markForSync();
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        LOGGER.info("[WeldRepairDebug] repair applied and inventory synced");
                    }
                }
            }
        }
    }

}
