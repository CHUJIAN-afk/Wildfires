package first.wildfires;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import first.wildfires.register.*;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
		NetworkPacketRegister.register();
		CreativeModeTabRegister.register(eventBus);
		ItemRegister.register(eventBus);
		SoundRegister.register(eventBus);
		
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

}
