package first.wildfires.mixin.minecraft;

import dev.architectury.registry.CreativeTabRegistry;
import first.wildfires.register.CreativeModeTabRegister;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private float scrollOffs;

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void simulated$render(final GuiGraphics guiGraphics, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (selectedTab == CreativeModeTabRegister.WildfiresTab.get()) {
            CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
            CreativeModeTabRegister.renderBanners(screen, guiGraphics, mouseX, mouseY, scrollOffs);
        }
    }
}