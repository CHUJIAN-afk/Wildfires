package first.wildfires.api.customEvent;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 机械动力机器tick事件，分为pre和post事件
 */
public abstract class KineticBlockEntityTickEvent extends Event {

    private final KineticBlockEntity kineticBlockEntity;

    public KineticBlockEntityTickEvent(KineticBlockEntity kineticBlockEntity) {
        this.kineticBlockEntity = kineticBlockEntity;
    }

    public KineticBlockEntity getKineticBlockEntity() {
        return kineticBlockEntity;
    }

    @Cancelable
    public static class Pre extends KineticBlockEntityTickEvent {

        public Pre(KineticBlockEntity kineticBlockEntity) {
            super(kineticBlockEntity);
        }

    }

    public static class Post extends KineticBlockEntityTickEvent {

        public Post(KineticBlockEntity kineticBlockEntity) {
            super(kineticBlockEntity);
        }

    }

}

