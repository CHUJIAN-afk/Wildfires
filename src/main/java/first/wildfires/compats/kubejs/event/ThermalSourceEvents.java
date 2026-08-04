package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** KubeJS startup event exposed as WildfiresThermalSources.sources(...). */
public interface ThermalSourceEvents {
    EventGroup GROUP = EventGroup.of("WildfiresThermalSources");

    EventHandler SOURCES = GROUP.startup("sources", () -> ThermalSourceEventJS.class);
}
