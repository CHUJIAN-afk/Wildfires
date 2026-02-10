package first.wildfires.api.customEvent;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import first.wildfires.api.KineticData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * 对机器性能设置上限
 */
public class KineticDataModifyEvent extends Event {

    private final List<KineticData> kineticData;

    public KineticDataModifyEvent() {
        this.kineticData = new ArrayList<>();
    }

    public void addKineticData(BlockEntityType<? extends KineticBlockEntity> type, Integer maxSpeed, Integer maxNetStress, List<ItemStack> list) {
        kineticData.add(new KineticData(type, maxSpeed, maxNetStress, list));
    }

    public List<KineticData> getKineticData() {
        return kineticData;
    }

}
