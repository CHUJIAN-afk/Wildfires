package first.wildfires.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import first.wildfires.mixin.create.AttributeFilterMenuAccessor;
import first.wildfires.mixin.create.AttributeFilterScreenAccessor;
import first.wildfires.network.TemperatureFilterSyncPacket;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TemperatureAttributeFilterScreen extends AttributeFilterScreen {

    private static final String TEMPERATURE_FILTERS_KEY = "TemperatureFilters";
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("^([><=]=?)\\s*(-?\\d+(?:\\.\\d+)?)$");

    /** Staging list: populated on Enter, committed to NBT when screen closes (confirm button). */
    private final List<String> pendingTemperatureFilters = new ArrayList<>();

    private EditBox temperatureInput;

    public TemperatureAttributeFilterScreen(AttributeFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();

        AttributeFilterScreenAccessor screenAccessor = (AttributeFilterScreenAccessor) this;

        // Shift the three whitelist/blacklist buttons right to make room for the input box
        IconButton dis = screenAccessor.wildfires$getWhitelistDis();
        IconButton con = screenAccessor.wildfires$getWhitelistCon();
        IconButton blk = screenAccessor.wildfires$getBlacklist();

        dis.setPosition(dis.getX() + 58, dis.getY());
        con.setPosition(con.getX() + 58, con.getY());
        blk.setPosition(blk.getX() + 58, blk.getY());

        // Add temperature input box at the original button position area
        int x = this.leftPos;
        int y = this.topPos;
        this.temperatureInput = new EditBox(this.font, x + 42, y + 66, 24, 14, Component.literal(""));
        this.temperatureInput.setMaxLength(12);
        this.temperatureInput.setHint(Component.literal(">=0").withStyle(style -> style.withColor(ChatFormatting.GRAY)));
        this.temperatureInput.setCanLoseFocus(true);
        this.temperatureInput.setBordered(false);
        this.addRenderableWidget(this.temperatureInput);

        // Load existing temperature filters from the item's NBT into the staging list
        loadExistingFilters();
    }

    private void loadExistingFilters() {
        pendingTemperatureFilters.clear();
        ItemStack filterStack = this.menu.contentHolder;
        if (!filterStack.isEmpty()) {
            CompoundTag tag = filterStack.getTag();
            if (tag != null && tag.contains(TEMPERATURE_FILTERS_KEY)) {
                ListTag filters = tag.getList(TEMPERATURE_FILTERS_KEY, net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < filters.size(); i++) {
                    pendingTemperatureFilters.add(filters.getString(i));
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.temperatureInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                String text = this.temperatureInput.getValue().trim();
                if (!text.isEmpty()) {
                    if (stageTemperatureFilter(text)) {
                        this.temperatureInput.setValue("");
                    }
                }
                return true;
            }
            if (this.temperatureInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.temperatureInput.isFocused()) {
            if (this.temperatureInput.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.temperatureInput.isFocused() && !this.temperatureInput.isMouseOver(mouseX, mouseY)) {
            this.temperatureInput.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean stageTemperatureFilter(String input) {
        var matcher = TEMPERATURE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return false;
        }

        String operator = matcher.group(1);
        String number = matcher.group(2);
        String filterValue = operator + number;

        // Check for duplicates - silent handling
        if (pendingTemperatureFilters.contains(filterValue)) {
            return false;
        }

        pendingTemperatureFilters.add(filterValue);
        return true;
    }

    private void commitTemperatureFilters() {
        TemperatureFilterSyncPacket.fromFilterList(pendingTemperatureFilters).sendToServer();
    }

    @Override
    public void removed() {
        commitTemperatureFilters();
        super.removed();
    }

    /**
     * Include temperature filters in the total count displayed on the filter slot.
     */
    @Override
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ItemStack stack = this.menu.ghostInventory.getStackInSlot(1);
        int attributeCount = ((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes().size();
        int totalCount = attributeCount + pendingTemperatureFilters.size();

        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(0.0F, 0.0F, 150.0F);
        graphics.renderItemDecorations(this.font, stack, this.leftPos + 16, this.topPos + 62, String.valueOf(totalCount));
        matrixStack.popPose();


        this.renderTooltip(graphics, mouseX, mouseY);

        for(Renderable widget : this.renderables) {
            if (widget instanceof AbstractSimiWidget simiWidget) {
                if (simiWidget.isMouseOver(mouseX, mouseY)) {
                    List<Component> tooltip = simiWidget.getToolTip();
                    if (!tooltip.isEmpty()) {
                        int ttx = simiWidget.lockedTooltipX == -1 ? mouseX : simiWidget.lockedTooltipX + simiWidget.getX();
                        int tty = simiWidget.lockedTooltipY == -1 ? mouseY : simiWidget.lockedTooltipY + simiWidget.getY();
                        graphics.renderComponentTooltip(this.font, tooltip, ttx, tty);
                    }
                }
            }
        }
    }

    /**
     * When the clear/reset button is pressed, also clear temperature filters.
     */
    @Override
    protected void contentsCleared() {
        super.contentsCleared();
        pendingTemperatureFilters.clear();
    }

    /**
     * Convert operator symbols to Chinese description for display.
     * e.g. ">=100" → "大于等于100", "<0" → "小于0", "=50" → "等于50"
     */
    private String formatTemperatureFilter(String filter) {
        var matcher = TEMPERATURE_PATTERN.matcher(filter);
        if (!matcher.matches()) {
            return filter;
        }
        String operator = matcher.group(1);
        String number = matcher.group(2);
        String opText = switch (operator) {
            case ">=" -> "大于或等于 ";
            case ">"  -> "大于 ";
            case "<=" -> "小于或等于 ";
            case "<"  -> "小于 ";
            case "==", "=" -> "等于 ";
            default   -> operator;
        };
        return opText + number + " 温度";
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.index == 37) {
                List<Component> tooltipLines = new ArrayList<>();

                // Header
                boolean hasContent = !pendingTemperatureFilters.isEmpty() || !((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes()
                        .isEmpty();
                if (hasContent) {
                    tooltipLines.add(CreateLang.translateDirect("gui.attribute_filter.selected_attributes")
                                             .withStyle(ChatFormatting.YELLOW));
                }

                // Temperature filter entries with Chinese operator description
                for (String filter : pendingTemperatureFilters) {
                    tooltipLines.add(Component.literal("- " + formatTemperatureFilter(filter))
                                             .withStyle(ChatFormatting.GRAY));
                }

                // Original selected attributes
                List<Pair<ItemAttribute, Boolean>> selectedAttrs = ((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes();
                for (Pair<ItemAttribute, Boolean> attr : selectedAttrs) {
                    tooltipLines.add(Component.literal("- ")
                                             .append(attr.getFirst()
                                                             .format(attr.getSecond()))
                                             .withStyle(ChatFormatting.GRAY));
                }

                if (!tooltipLines.isEmpty()) {
                    graphics.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
                    return;
                }
            }

            graphics.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
            return;
        }

        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
