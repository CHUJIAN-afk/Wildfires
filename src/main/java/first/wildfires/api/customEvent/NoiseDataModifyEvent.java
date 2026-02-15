package first.wildfires.api.customEvent;

import first.wildfires.api.NoiseData;
import net.minecraftforge.eventbus.api.Event;

public class NoiseDataModifyEvent extends Event {

    private final NoiseData noiseData;

    public NoiseDataModifyEvent(NoiseData data) {
        this.noiseData = data;
    }

    public NoiseData getData() {
        return noiseData;
    }

}
