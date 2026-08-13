package first.wildfires.space.capsule;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
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
        double x = context.getClickedPos().getX() + 0.5D;
        double y = context.getClickedPos().getY() + 1.0D;
        double z = context.getClickedPos().getZ() + 0.5D;
        if (!context.getLevel().noCollision(new AABB(x - 0.9D, y, z - 0.9D,
                x + 0.9D, y + 3.0D, z + 0.9D))) {
            return InteractionResult.FAIL;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel) {
            ReusableReturnCapsuleEntity capsule = new ReusableReturnCapsuleEntity(serverLevel,
                    x, y, z, context.getPlayer().getUUID());
            CompoundTag itemTag = context.getItemInHand().getTag();
            if (itemTag != null && itemTag.get("wildfires_capsule_fuel") instanceof CompoundTag fuel
                    && !capsule.loadFuelFromItem(fuel)) {
                return InteractionResult.FAIL;
            }
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
}
