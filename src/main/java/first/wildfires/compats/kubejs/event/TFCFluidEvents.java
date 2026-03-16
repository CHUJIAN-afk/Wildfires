package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface TFCFluidEvents {
    EventGroup GROUP = EventGroup.of("TFCFluidEvents");

    EventHandler MODIFY = GROUP.startup("modify", () -> TFCFluidModificationEventJS.class);
}
