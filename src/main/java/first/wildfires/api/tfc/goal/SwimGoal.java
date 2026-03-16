package first.wildfires.api.tfc.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public class SwimGoal extends Goal {
    private final Mob mob;

    private final double speed;

    public SwimGoal(Mob mob, double speed) {
        this.mob = mob;

        this.speed = speed;
    }

    @Override
    public boolean canUse() {
        return mob.isInWater();
    }

    @Override
    public void tick() {
        var target = mob.getTarget();

        if (target == null)
            return;

        Vec3 direction = new Vec3(
            target.getX() - mob.getX(),
            target.getY() - mob.getY(),
            target.getZ() - mob.getZ()
        ).normalize().scale(speed);

        mob.setDeltaMovement(mob.getDeltaMovement().add(direction));
    }
}
