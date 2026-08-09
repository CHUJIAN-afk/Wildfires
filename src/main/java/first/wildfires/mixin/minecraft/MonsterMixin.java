package first.wildfires.mixin.minecraft;

import first.wildfires.celestial.CelestialGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Relaxes only the vanilla monster darkness predicate while the local moon is a visible blood moon. */
@Mixin(Monster.class)
public abstract class MonsterMixin {

    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void wildfires$allowVisibleBloodMoonSpawns(ServerLevelAccessor level, BlockPos position,
                                                               RandomSource random,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (CelestialGameplay.visibleBloodMoon(level.getLevel(), position) > 0.0D) {
            cir.setReturnValue(true);
        }
    }
}
