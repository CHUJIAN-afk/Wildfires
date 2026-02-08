package first.wildfires.api.customEvent;

import net.minecraftforge.eventbus.api.Event;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

/**
 * 温度枚举修改事件,尝试修改温度枚举的上下界
 */
public class TemperatureEnumModifyEvent extends Event {

    private final TemperatureEnum temperatureEnum;
    private int lowerBound,upperBound;

    public TemperatureEnumModifyEvent(TemperatureEnum temperatureEnum) {
        this.temperatureEnum = temperatureEnum;
        this.lowerBound = temperatureEnum.getLowerBound();
        this.upperBound = temperatureEnum.getUpperBound();
    }

    public TemperatureEnum getTemperatureEnum() {
        return temperatureEnum;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(int lowerBound) {
        this.lowerBound = lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(int upperBound) {
        this.upperBound = upperBound;
    }

}
