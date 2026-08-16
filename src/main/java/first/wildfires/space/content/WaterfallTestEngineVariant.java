package first.wildfires.space.content;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

/** Distinguishes the two ordinary Daedalus drives sharing the Waterfall runtime. */
public enum WaterfallTestEngineVariant {
    DAEDALUS_V1,
    DAEDALUS_V2;

    public RegistryObject<BlockEntityType<WaterfallTestEngineBlockEntity>> blockEntityType() {
        return this == DAEDALUS_V1
                ? SpaceContentRegister.DAEDALUS_V1_TEST_ENGINE_BLOCK_ENTITY
                : SpaceContentRegister.DAEDALUS_V2_TEST_ENGINE_BLOCK_ENTITY;
    }

    public static WaterfallTestEngineVariant fromState(BlockState state) {
        return state.is(SpaceContentRegister.DAEDALUS_V1_TEST_ENGINE.get())
                ? DAEDALUS_V1 : DAEDALUS_V2;
    }
}
