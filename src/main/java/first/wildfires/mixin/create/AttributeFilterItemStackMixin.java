package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import first.wildfires.api.tfc.TemperatureFilter;
import net.createmod.catnip.data.Pair;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(value = FilterItemStack.AttributeFilterItemStack.class, remap = false)
public abstract class AttributeFilterItemStackMixin {

    @Unique
    private static final String TEMPERATURE_FILTERS_KEY = "TemperatureFilters";

    @Unique
    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("^([><=]=?)\\s*(-?\\d+(?:\\.\\d+)?)$");

    @Shadow
    public FilterItemStack.AttributeFilterItemStack.WhitelistMode whitelistMode;

    @Shadow public List<Pair<ItemAttribute, Boolean>> attributeTests;

    @WrapMethod(method = "test(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Z)Z")
    private boolean wrapTemperatureFilter(Level world, ItemStack stack, boolean matchNBT, Operation<Boolean> original) {
        List<TemperatureFilter> tempFilters = wildfires$readTemperatureFilters();
        if (!tempFilters.isEmpty()) {
            float itemTemp = HeatCapability.getTemperature(stack);
            return switch (this.whitelistMode) {
                case BLACKLIST -> {
                    if (tempFilters.stream().anyMatch(filter -> filter.matches(itemTemp))) {
                        yield false;
                    }
                    if (attributeTests.isEmpty()){
                        yield true;
                    }
                    yield original.call(world, stack, matchNBT);
                }
                case WHITELIST_CONJ -> tempFilters.stream().allMatch(filter -> filter.matches(itemTemp)) && original.call(world, stack, matchNBT);
                case WHITELIST_DISJ -> tempFilters.stream().anyMatch(filter -> filter.matches(itemTemp)) || original.call(world, stack, matchNBT);
            };
        }
        return original.call(world, stack, matchNBT);
    }

    @Unique
    public ItemStack wildfires$item() {
        return ((FilterItemStack.AttributeFilterItemStack) (Object) this).item();
    }

    @Unique
    private List<TemperatureFilter> wildfires$readTemperatureFilters() {
        List<TemperatureFilter> filters = new ArrayList<>();
        ItemStack filterStack = wildfires$item();
        if (filterStack.isEmpty()) return filters;

        CompoundTag tag = filterStack.getTag();
        if (tag == null || !tag.contains(TEMPERATURE_FILTERS_KEY)) return filters;

        ListTag list = tag.getList(TEMPERATURE_FILTERS_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String raw = list.getString(i);
            Matcher m = TEMPERATURE_PATTERN.matcher(raw);
            if (m.matches()) {
                String op = m.group(1);
                float value = Float.parseFloat(m.group(2));
                filters.add(new TemperatureFilter(op, value));
            }
        }
        return filters;
    }
}
