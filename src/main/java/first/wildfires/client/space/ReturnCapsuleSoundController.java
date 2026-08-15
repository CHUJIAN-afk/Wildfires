/*
 * Adapted from NTM: Space EntityRideableRocket's reusable flight-audio state handling.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.client.space;

import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.Minecraft;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Prevents repeated short sound instances from turning NTM's loop into audible stutter. */
public final class ReturnCapsuleSoundController {

    private static final Map<UUID, ReturnCapsuleFlightSound> ACTIVE = new ConcurrentHashMap<>();

    private ReturnCapsuleSoundController() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Map.Entry<UUID, ReturnCapsuleFlightSound>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!minecraft.getSoundManager().isActive(entry.getValue())) iterator.remove();
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ReusableReturnCapsuleEntity capsule)) continue;
            if (!ReturnCapsuleFlightSound.shouldPlay(capsule) || ACTIVE.containsKey(capsule.getUUID())) continue;
            ReturnCapsuleFlightSound sound = new ReturnCapsuleFlightSound(capsule);
            ACTIVE.put(capsule.getUUID(), sound);
            minecraft.getSoundManager().play(sound);
        }
    }
}
