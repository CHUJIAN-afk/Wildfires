package first.wildfires.api.customEvent;

import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


/**
 * 玉的抬头药水效果渲染事件
 */
@Cancelable
@OnlyIn(Dist.CLIENT)
public class JadeRenderEffectEvent extends Event {

    private final String name;

    public JadeRenderEffectEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
