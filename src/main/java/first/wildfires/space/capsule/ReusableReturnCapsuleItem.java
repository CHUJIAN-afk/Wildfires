/*
 * Adapted from NTM: Space TileEntityOrbitalStation reusable-rocket deployment contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: atomic Forge item deployment on surfaces or below the immutable core.
 */
package first.wildfires.space.capsule;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreBlockEntity;
import first.wildfires.space.content.StationCoreService;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;

/** Deploys the one-piece reusable return capsule; no modular rocket assembly is introduced. */
public final class ReusableReturnCapsuleItem extends Item {

    public ReusableReturnCapsuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel
                && serverLevel.dimension() == SpaceDimensions.ORBIT) {
            BlockPos core = resolveCore(serverLevel, context.getClickedPos());
            if (core != null) return deployDocked(context, serverLevel, core);
        } else if (context.getLevel().isClientSide()
                && (context.getLevel().getBlockState(context.getClickedPos())
                .is(SpaceContentRegister.STATION_CORE.get())
                || context.getLevel().getBlockState(context.getClickedPos())
                .is(SpaceContentRegister.STATION_STRUCTURE.get()))) {
            return InteractionResult.SUCCESS;
        }
        double x = context.getClickedPos().getX() + 0.5D;
        double y = context.getClickedPos().getY() + 1.0D;
        double z = context.getClickedPos().getZ() + 0.5D;
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(serverLevel,
                    x, y, z, context.getPlayer().getUUID());
            if (!serverLevel.noCollision(capsule, ReturnCapsuleService.capsuleBoundsAt(
                    new Vec3(x, y, z)))) return InteractionResult.FAIL;
            CompoundTag itemTag = context.getItemInHand().getTag();
            if (itemTag != null && itemTag.get("wildfires_capsule_fuel") instanceof CompoundTag fuel
                    && !capsule.loadFuelFromItem(fuel)) {
                return InteractionResult.FAIL;
            }
            if (itemTag != null && itemTag.get("wildfires_capsule_navigation_tape") instanceof CompoundTag tape
                    && !capsule.loadNavigationTapeFromItem(tape)) return InteractionResult.FAIL;
            capsule.setYRot(context.getPlayer().getYRot());
            if (!serverLevel.addFreshEntity(capsule)) {
                return InteractionResult.FAIL;
            }
            if (!context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private static BlockPos resolveCore(ServerLevel level, BlockPos clicked) {
        if (level.getBlockState(clicked).is(SpaceContentRegister.STATION_CORE.get())
                && StationCoreService.isComplete(level, clicked)) return clicked.immutable();
        return StationCoreService.coreForStructureBlock(level, clicked).orElse(null);
    }

    private static InteractionResult deployDocked(UseOnContext context, ServerLevel level,
                                                    BlockPos corePosition) {
        if (!(level.getBlockEntity(corePosition) instanceof StationCoreBlockEntity core)) {
            return InteractionResult.FAIL;
        }
        StationRecord station = core.stationId().flatMap(id -> SpaceSavedData.get(level.getServer())
                .station(id)).orElse(null);
        if (station == null || station.journey().isPresent()
                || !station.mayOperate(context.getPlayer().getUUID())
                || core.claimedCapsuleId().isPresent()) {
            return InteractionResult.FAIL;
        }
        Vec3 dock = ReturnCapsuleService.stationDockedPosition(corePosition);
        AABB bounds = ReturnCapsuleService.capsuleBoundsAt(dock);
        ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(level,
                dock.x, dock.y, dock.z, context.getPlayer().getUUID());
        if (!level.noCollision(capsule, bounds) || !level.getEntities(null, bounds).isEmpty()) {
            return InteractionResult.FAIL;
        }
        CompoundTag itemTag = context.getItemInHand().getTag();
        if (itemTag != null && itemTag.get("wildfires_capsule_fuel") instanceof CompoundTag fuel
                && !capsule.loadFuelFromItem(fuel)) return InteractionResult.FAIL;
        if (itemTag != null && itemTag.get("wildfires_capsule_navigation_tape") instanceof CompoundTag tape
                && !capsule.loadNavigationTapeFromItem(tape)) return InteractionResult.FAIL;
        if (capsule.stationId().isEmpty()) capsule.bindStation(station.stationId());
        if (capsule.stationId().filter(station.stationId()::equals).isEmpty()) return InteractionResult.FAIL;
        capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
        capsule.setDockLocked(true);
        capsule.setNoGravity(true);
        capsule.setYRot(ReturnCapsuleService.DOCK_YAW);
        capsule.setXRot(0.0F);
        if (!level.addFreshEntity(capsule)) return InteractionResult.FAIL;
        if (!core.completeDock(capsule.getUUID())) {
            capsule.discard();
            return InteractionResult.FAIL;
        }
        StationService.OperationResult recorded = StationService.setReturnCapsule(
                SpaceSavedData.get(level.getServer()), station.stationId(), capsule.getUUID(), true,
                level.getServer().overworld().getGameTime());
        if (!recorded.successful()) {
            core.releaseDockLock(capsule.getUUID());
            capsule.discard();
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
