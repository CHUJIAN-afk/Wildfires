/*
 * Adapted from NTM: Space EntityRideableRocket's AudioWrapper lifecycle.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: Forge tickable audio gated to a directly seated player passenger.
 */
package first.wildfires.client.space;

import first.wildfires.register.SoundRegister;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleState;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One entity-bound loop kept alive for exactly the NTM light-flight states. */
final class ReturnCapsuleFlightSound extends AbstractTickableSoundInstance {

    private final ReusableReturnCapsuleEntity capsule;

    ReturnCapsuleFlightSound(ReusableReturnCapsuleEntity capsule) {
        super(SoundRegister.ReturnCapsuleFlight.get(), SoundSource.PLAYERS,
                RandomSource.create(capsule.getUUID().getLeastSignificantBits()));
        this.capsule = capsule;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        updatePosition();
    }

    @Override
    public boolean canPlaySound() {
        return !capsule.isSilent() && shouldPlay(capsule);
    }

    @Override
    public void tick() {
        if (capsule.isRemoved() || !shouldPlay(capsule)) {
            stop();
            return;
        }
        updatePosition();
    }

    static boolean shouldPlay(ReusableReturnCapsuleEntity capsule) {
        ReturnCapsuleState state = capsule.capsuleState();
        return capsule.hasPlayerPassenger()
                && (state == ReturnCapsuleState.SURFACE_LAUNCHING
                || (state == ReturnCapsuleState.REENTRY
                || state == ReturnCapsuleState.SURFACE_LANDING)
                && capsule.getDeltaMovement().y > -0.4D);
    }

    private void updatePosition() {
        this.x = capsule.getX();
        this.y = capsule.getY();
        this.z = capsule.getZ();
    }
}
