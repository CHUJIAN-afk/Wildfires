package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** KubeJS startup event exposed as WildfiresTemperatureRanges.modify(...). */
public interface TemperatureRangeEvents {
    EventGroup GROUP = EventGroup.of("WildfiresTemperatureRanges");

    EventHandler MODIFY = GROUP.startup("modify", () -> TemperatureRangeEventJS.class);
}
