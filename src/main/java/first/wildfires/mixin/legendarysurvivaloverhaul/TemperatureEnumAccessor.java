package first.wildfires.mixin.legendarysurvivaloverhaul;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

@Mixin(value = TemperatureEnum.class, remap = false)
public interface TemperatureEnumAccessor {

    @Mutable
    @Accessor("lowerBound")
    void setLowerBound(int value);

    @Mutable
    @Accessor("upperBound")
    void setUpperBound(int value);

}
