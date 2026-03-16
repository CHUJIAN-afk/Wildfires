package first.wildfires.mixin.tfc;

import com.notenoughmail.kubejs_tfc.util.implementation.custom.climate.KubeJSClimateModel;
import net.dries007.tfc.client.ClimateRenderCache;
import net.dries007.tfc.client.screen.ClimateScreen;
import net.dries007.tfc.client.screen.TFCContainerScreen;
import net.dries007.tfc.common.container.Container;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.config.TemperatureDisplayStyle;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.climate.KoppenClimateClassification;
import net.dries007.tfc.util.tracker.WorldTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ClimateScreen.class, remap = false)
public abstract class ClimateScreenMixin extends TFCContainerScreen<Container> {

    public ClimateScreenMixin(Container container, Inventory playerInventory, Component name, ResourceLocation texture) {
        super(container, playerInventory, name, texture);
    }

    @Override
    protected void renderLabels(GuiGraphics stack, int mouseX, int mouseY) {
        super.renderLabels(stack, mouseX, mouseY);

        final float averageTemp = ClimateRenderCache.INSTANCE.getAverageTemperature();
        final float rainfall = ClimateRenderCache.INSTANCE.getRainfall();
        final float currentTemp = ClimateRenderCache.INSTANCE.getTemperature();

        final TemperatureDisplayStyle style = TFCConfig.CLIENT.climateTooltipStyle.get();

        Level level = Minecraft.getInstance().level;

        MutableComponent climateName = Helpers.translateEnum(KoppenClimateClassification.classify(averageTemp, rainfall));

        if (level != null && WorldTracker.get(level).getClimateModel() instanceof KubeJSClimateModel kjsModel) {
            Class<?> clazz = kjsModel.getClass();

            try {
                var field = clazz.getDeclaredField("name");

                field.setAccessible(true);

                ResourceLocation location = (ResourceLocation) field.get(kjsModel);

                String name = I18n.get("kubejs.climate.name." + location.getPath());

                climateName = Component.literal(name);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        drawCenteredLine(stack, Component.translatable("tfc.tooltip.climate_koppen_climate_classification", climateName), 19);
        drawCenteredLine(stack, Component.translatable("tfc.tooltip.climate_average_temperature", style.format(averageTemp, true)), 30);
        drawCenteredLine(stack, Component.translatable("tfc.tooltip.climate_annual_rainfall", String.format("%.1f", rainfall)), 41);
        drawCenteredLine(stack, Component.translatable("tfc.tooltip.climate_current_temp", style.format(currentTemp, true)), 52);
    }
}
