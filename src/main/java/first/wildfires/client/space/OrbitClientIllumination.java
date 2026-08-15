/*
 * Adapted from NTM: Space WorldProviderOrbit.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: resolves the upstream station-local illumination contract against
 * synchronized Forge 1.20.1 observation contexts and the unified Wildfires ephemeris.
 */
package first.wildfires.client.space;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.space.render.OrbitVisualFrameCache;
import first.wildfires.client.space.render.OrbitVisualRules;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Resolves the station-local NTM illumination without leaking it into surface dimensions. */
public final class OrbitClientIllumination {

    private OrbitClientIllumination() {
    }

    public static Optional<OrbitVisualRules.OrbitIllumination> resolve(ClientLevel level,
                                                                      Vec3 observer,
                                                                      float partialTick) {
        if (level == null || level.dimension() != SpaceDimensions.ORBIT) {
            return Optional.empty();
        }
        ObservationContext context = ObservationContextResolver.resolve(level, observer).orElse(null);
        CelestialState celestial = CelestialClientStateCache.stateOrNull(level, observer, partialTick);
        if (context == null || celestial == null) {
            return Optional.empty();
        }
        double gameTime = OrbitVisualDebugClock.gameTime()
                .orElse(level.getGameTime() + partialTick);
        double calendarTicks = OrbitVisualDebugClock.calendarTicks()
                .orElse(celestial.calendarTicks());
        double calendarRate = OrbitVisualDebugClock.calendarTicks().isPresent()
                ? 0.0D : TfcCalendarRateController.clientMultiplier();
        return Optional.of(OrbitVisualFrameCache.frame(context, celestial, gameTime, calendarTicks,
                calendarRate, Calendars.get(level).getCalendarDaysInMonth()).illumination());
    }
}
