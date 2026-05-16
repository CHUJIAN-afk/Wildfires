package first.wildfires.register;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import first.wildfires.Wildfires;
import first.wildfires.client.renderer.blockEntity.CustomCogWheelBlockEntityRenderer;
import first.wildfires.client.renderer.blockEntity.CustomCrushingWheelBlockEntityRenderer;
import first.wildfires.client.renderer.blockEntity.CustomMillstoneBlockEntityRenderer;
import first.wildfires.kinetic.loom.LoomAuxiliaryBlockEntity;
import first.wildfires.kinetic.loom.LoomAuxiliaryBlockEntityRenderer;
import first.wildfires.kinetic.loom.LoomControlBlockEntity;
import first.wildfires.kinetic.loom.LoomControlBlockEntityRenderer;
import first.wildfires.kinetic.loom.LoomStructureBlockEntity;
import first.wildfires.kinetic.loom.LoomStructureBlockEntityRenderer;

import static com.simibubi.create.api.contraption.storage.item.MountedItemStorageType.mountedItemStorage;
import static first.wildfires.register.BlockRegister.*;
//import static first.wildfires.register.BlockRegister.StoneCrushingWheeldiorite;


public class BlockEntityRegister {

    private static final CreateRegistrate Registrate = Wildfires.Registrate;

    //织布机控制中心
    public static final BlockEntityEntry<LoomControlBlockEntity> LoomControlBlockEntity =
            Registrate.blockEntity("loom_control_block_entity", LoomControlBlockEntity::new)
                    .validBlock(BlockRegister.LoomControlBlock)
                    .renderer(() -> LoomControlBlockEntityRenderer::new)
                    .register();

    //织布机结构块
    public static final BlockEntityEntry<LoomStructureBlockEntity> LoomStructureBlockEntity =
            Registrate.blockEntity("loom_structure_block_entity", LoomStructureBlockEntity::new)
                    .validBlock(BlockRegister.LoomStructureBlock)
                    .renderer(() -> LoomStructureBlockEntityRenderer::new)
                    .register();

    //织布机辅助块
    public static final BlockEntityEntry<LoomAuxiliaryBlockEntity> LoomAuxiliaryBlockEntity =
            Registrate.blockEntity("loom_auxiliary_block_entity", LoomAuxiliaryBlockEntity::new)
                    .validBlock(BlockRegister.LoomAuxiliaryBlock)
                    .renderer(() -> LoomAuxiliaryBlockEntityRenderer::new)
                    .register();

    // 磨石方块实体注册
    public static final BlockEntityEntry<MillstoneBlockEntity> CustomMillstoneBlockEntity =
            Registrate.blockEntity("millstone", MillstoneBlockEntity::new)
                    .validBlocks(StoneMillgranite)
                    .validBlocks(StoneMilldiorite)
                    .validBlocks(StoneMillgabbro)
                    .validBlocks(StoneMillrhyolite)
                    .validBlocks(StoneMillbasalt)
                    .validBlocks(StoneMilldacite)
                    .renderer(() -> CustomMillstoneBlockEntityRenderer::new)
                    .register();

    // 破碎轮方块实体注册
    public static final BlockEntityEntry<CrushingWheelBlockEntity> CustomCrushingWheelBlockEntity =
            Registrate.blockEntity("crushing_wheel", CrushingWheelBlockEntity::new)
                    .validBlocks(StoneCrushingWheelGranite)
                    .validBlocks(StoneCrushingWheelDiorite)
                    .validBlocks(StoneCrushingWheelGabbro)
                    .validBlocks(StoneCrushingWheelRhyolite)
                    .validBlocks(StoneCrushingWheelBasalt)
                    .validBlocks(StoneCrushingWheelDacite)
                    .renderer(() -> CustomCrushingWheelBlockEntityRenderer::new)
                    .register();
    public static final BlockEntityEntry<BracketedKineticBlockEntity> CustomCogWheelBlockEntity =
            Registrate.blockEntity("cogwheel", BracketedKineticBlockEntity::new)
                    .validBlocks(StoneCogWheel, StoneLargeCogWheel)
                    .renderer(() -> CustomCogWheelBlockEntityRenderer::new)
                    .register();

    public static void register() {

    }

}
