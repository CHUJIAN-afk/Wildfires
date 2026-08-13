package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Immutable physical anchor and primary docking endpoint of one fixed station. */
public final class StationCoreBlock extends BaseEntityBlock {

    public StationCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationCoreBlockEntity(pos, state);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // The core is the persistent station anchor. Explosion damage is intentionally ignored.
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(SpaceContentRegister.STATION_ID_TAPE.get())) {
            return StationIdTapeItem.programFromCore(level, pos, player, hand);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel
                && level.getBlockEntity(pos) instanceof StationCoreBlockEntity core) {
            player.displayClientMessage(core.stationId()
                    .map(id -> Component.translatable("space.wildfires.station_core.bound", id))
                    .orElseGet(() -> Component.translatable("space.wildfires.station_core.unbound")), true);
        }
        return InteractionResult.CONSUME;
    }
}
