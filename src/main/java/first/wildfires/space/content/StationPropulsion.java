package first.wildfires.space.content;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Forge-era equivalent of NTM Space's IPropulsion contract for normal station engines. */
public interface StationPropulsion {

    int REFERENCE_SHIP_MASS = 200_000;

    BlockEntity blockEntity();

    boolean canPerformBurn(int shipMass, double deltaV);

    float thrust();

    int startBurn();

    int endBurn();
}
