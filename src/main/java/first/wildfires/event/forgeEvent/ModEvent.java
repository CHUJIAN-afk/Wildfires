package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.api.MobPoopData;
import first.wildfires.api.customEvent.KineticDataModifyEvent;
import first.wildfires.api.customEvent.MobPoopDataModifyEvent;
import first.wildfires.api.customEvent.TemperatureEnumModifyEvent;
import first.wildfires.mixin.legendarysurvivaloverhaul.TemperatureEnumAccessor;
import first.wildfires.utils.WildfiresUtil;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

import java.util.List;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvent {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void modLoading(FMLCommonSetupEvent event) {
        ModList modList = ModList.get();
        Wildfires.TFCLoaded = modList.isLoaded("tfc");
        Wildfires.CurioLoaded = modList.isLoaded("curios");
        Wildfires.LSOLoaded = modList.isLoaded("legendarysurvivaloverhaul");
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        if (Wildfires.LSOLoaded) {
            TemperatureEnum[] values = TemperatureEnum.values();
            for (TemperatureEnum value : values) {
                TemperatureEnumModifyEvent modifyEvent = new TemperatureEnumModifyEvent(value);
                WildfiresUtil.post(modifyEvent);
                TemperatureEnumAccessor anEnum = (TemperatureEnumAccessor) (Object) value;
                if (anEnum != null) {
                    int lowerBound = modifyEvent.getLowerBound();
                    if (value.getLowerBound() != lowerBound) {
                        anEnum.setLowerBound(lowerBound);
                    }
                    int upperBound = modifyEvent.getUpperBound();
                    if (value.getUpperBound() != upperBound) {
                        anEnum.setUpperBound(upperBound);
                    }
                }
            }
        }
        KineticDataModifyEvent kineticDataModifyEvent = new KineticDataModifyEvent();
        WildfiresUtil.post(kineticDataModifyEvent);
        WildfiresUtil.kineticDataList.clear();
        WildfiresUtil.kineticDataList.addAll(kineticDataModifyEvent.getKineticData());
        MobPoopDataModifyEvent mobPoopDataModifyEvent = new MobPoopDataModifyEvent();
        WildfiresUtil.post(mobPoopDataModifyEvent);
        WildfiresUtil.mobPoopDataList.clear();
        List<MobPoopData> list = mobPoopDataModifyEvent.getMobPoopDataList();
        WildfiresUtil.mobPoopDataList.addAll(list);
        WildfiresUtil.PoopList.clear();
        for (MobPoopData data : list) {
            WildfiresUtil.PoopList.add(data.type());
        }
    }

}
