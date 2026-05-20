package first.wildfires;

import com.oblivioussp.spartanweaponry.client.renderer.entity.JavelinRenderer;
import com.oblivioussp.spartanweaponry.entity.projectile.JavelinEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import first.wildfires.ponder.WildfiresPonderPlugin;
import first.wildfires.register.WeavingRecipeRegister;
import first.wildfires.register.*;
import net.createmod.ponder.foundation.PonderIndex;
import net.dries007.tfc.client.model.entity.JavelinModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
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
		AttributeRegister.register(eventBus);
		BlockEntityRegister.register();
		BlockRegister.register(eventBus);
		NetworkPacketRegister.register();
		CreativeModeTabRegister.register(eventBus);
		ItemRegister.register(eventBus);
		SoundRegister.register(eventBus);
		WeavingRecipeRegister.register(eventBus);
		//MechanicalPistonHeadBlock


	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

}
