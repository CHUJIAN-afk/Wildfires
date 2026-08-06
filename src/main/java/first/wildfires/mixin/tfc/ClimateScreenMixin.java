package first.wildfires.mixin.tfc;

import com.newterraearth.tfe.client.NTEClimateRenderCacheBridge;
import com.newterraearth.tfe.client.NTEClimateRenderHelpers;
import com.newterraearth.tfe.client.NTEKoppenClimateClassification;
import com.notenoughmail.kubejs_tfc.util.implementation.custom.climate.KubeJSClimateModel;
import net.dries007.tfc.client.ClimateRenderCache;
import net.dries007.tfc.client.screen.ClimateScreen;
import net.dries007.tfc.client.screen.TFCContainerScreen;
import net.dries007.tfc.common.container.Container;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.config.TemperatureDisplayStyle;
import net.dries007.tfc.util.tracker.WorldTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ClimateScreen.class, remap = false)
public abstract class ClimateScreenMixin extends TFCContainerScreen<Container> {
    @Unique private static final int WILDFIRES_ALIGN_LEFT = 0;
    @Unique private static final int WILDFIRES_ALIGN_CENTER = 1;
    @Unique private static final int WILDFIRES_ALIGN_RIGHT = 2;

    public ClimateScreenMixin(Container container, Inventory playerInventory, Component name, ResourceLocation texture) {
        super(container, playerInventory, name, texture);
    }

    @Override
    protected void renderLabels(GuiGraphics stack, int mouseX, int mouseY) {
        super.renderLabels(stack, mouseX, mouseY);

        final NTEClimateRenderCacheBridge cache = (NTEClimateRenderCacheBridge) (Object) ClimateRenderCache.INSTANCE;
        final float averageTemp = cache.getAverageSeaLevelTemperature();
        final float averageRainfall = cache.getAverageRainfall();
        final float currentTemp = cache.getInstantTemperature();
        final float currentRainfall = cache.getInstantRainfall();
        final float rainVariance = cache.getRainVariance();

        final TemperatureDisplayStyle style = TFCConfig.CLIENT.climateTooltipStyle.get();
        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        final Player player = minecraft.player;
        final boolean northernHemisphere = level == null || player == null || NTEClimateRenderHelpers.isNorthernHemisphere(level, player.blockPosition());
        final float displayRainVariance = northernHemisphere ? rainVariance : -rainVariance;
        final float peakRainfall = averageRainfall * (1f + Math.abs(rainVariance));
        final String peakRainfallKey = displayRainVariance > 0f
            ? "tfc.tooltip.climate_peak_rainfall_july"
            : "tfc.tooltip.climate_peak_rainfall_january";
        final NTEKoppenClimateClassification classification = NTEKoppenClimateClassification.classify(averageTemp, averageRainfall, rainVariance, northernHemisphere);
        Component climateName = Component.translatable(classification.translationKey());

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

        wildfires$drawLine(stack, climateName, WILDFIRES_ALIGN_CENTER, 18);

        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_name"), WILDFIRES_ALIGN_LEFT, 32);
        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_average", style.format(averageTemp, true)), WILDFIRES_ALIGN_LEFT, -1, 36, 32);
        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_now", style.format(currentTemp, true)), WILDFIRES_ALIGN_LEFT, -1, 96, 32);

        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_name"), WILDFIRES_ALIGN_LEFT, 0x202080, 46);
        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_average", String.format("%.0f", averageRainfall)), WILDFIRES_ALIGN_LEFT, 0x202080, 36, 46);
        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_now", String.format("%.0f", currentRainfall)), WILDFIRES_ALIGN_LEFT, 0x202080, 96, 46);

        wildfires$drawLine(stack, Component.translatable("tfc.tooltip.climate_peak_rainfall"), WILDFIRES_ALIGN_LEFT, 0x202080, 57);
        wildfires$drawLine(stack, Component.translatable(peakRainfallKey, String.format("%.0f", peakRainfall)), WILDFIRES_ALIGN_LEFT, 0x202080, 36, 57);
    }

    @Unique
    private void wildfires$drawLine(GuiGraphics graphics, Component text, int alignment, int y) {
        wildfires$drawLine(graphics, text, alignment, 0x404040, 0, y);
    }

    @Unique
    private void wildfires$drawLine(GuiGraphics graphics, Component text, int alignment, int color, int y) {
        wildfires$drawLine(graphics, text, alignment, color, 0, y);
    }

    @Unique
    private void wildfires$drawLine(GuiGraphics graphics, Component text, int alignment, int color, int x, int y) {
        int drawX = x;
        final int drawColor = color == -1 ? 0x404040 : color;
        if (alignment == WILDFIRES_ALIGN_RIGHT) {
            drawX *= -1;
        }
        switch (alignment) {
            case WILDFIRES_ALIGN_LEFT -> drawX += 8;
            case WILDFIRES_ALIGN_CENTER -> drawX += (imageWidth - font.width(text)) / 2;
            case WILDFIRES_ALIGN_RIGHT -> drawX += imageWidth - font.width(text) - 8;
            default -> drawX += 8;
        }
        graphics.drawString(font, text, drawX, y, drawColor, false);
    }
}
