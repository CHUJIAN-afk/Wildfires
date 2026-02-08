package first.wildfires.api.customEvent;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;


/**
 * 玉的抬头药水效果渲染事件
 */
@Cancelable
public class JadeRenderEffectEvent extends Event {

    private final String name;

    public JadeRenderEffectEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
