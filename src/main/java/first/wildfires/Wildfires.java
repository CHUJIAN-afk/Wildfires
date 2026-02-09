package first.wildfires;

import com.simibubi.create.foundation.data.CreateRegistrate;
import first.wildfires.api.customEvent.TemperatureEnumModifyEvent;
import first.wildfires.mixin.legendarysurvivaloverhaul.TemperatureEnumAccessor;
import first.wildfires.register.*;
import net.dries007.tfc.common.entities.livestock.TFCAnimal;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

@Mod(Wildfires.MODID)
public class Wildfires {

	public static final String MODID = "wildfires";

	public static final CreateRegistrate Registrate = CreateRegistrate.create(MODID);
	public static boolean TFCLoaded;
	public static boolean LSOLoaded;
	public static boolean CurioLoaded;

	public Wildfires(FMLJavaModLoadingContext context) {
		IEventBus eventBus = context.getModEventBus();
		Registrate.registerEventListeners(eventBus);
		BlockEntityRegister.register();
		BlockRegister.register();
		CreativeModeTabRegister.register(eventBus);
		ItemRegister.register(eventBus);
		SoundRegister.register(eventBus);
		eventBus.addListener(this::initCommon);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			eventBus.addListener(this::initClient);
		}
	}

	public void initCommon(final FMLCommonSetupEvent event) {
		ModList modList = ModList.get();
		TFCLoaded = modList.isLoaded("tfc");
		CurioLoaded = modList.isLoaded("curios");
		LSOLoaded = modList.isLoaded("legendarysurvivaloverhaul");
		if (LSOLoaded) {
			TemperatureEnum[] values = TemperatureEnum.values();
			for (TemperatureEnum value : values) {
				TemperatureEnumModifyEvent modifyEvent = new TemperatureEnumModifyEvent(value);
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
	}

	private void initClient(final FMLClientSetupEvent event) {
		PartialModelRegister.register();
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

}
