/*
 * Adapted from VS: Genesis' separation of authoritative world transfer and client visual transition.
 * SPDX-License-Identifier: Apache-2.0
 * Wildfires implementation: a bounded, state-driven fade around the reusable capsule's dimension
 * boundary; it contains no VS2, ship, OBB, scale or Genesis registry types.
 */
package first.wildfires.client.space;

import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Hides the single authoritative dimension hand-off behind a short black-vacuum fade. */
public final class ReturnCapsuleTransitionOverlay {

    private ReturnCapsuleTransitionOverlay() {
    }

    public static final IGuiOverlay INSTANCE = ReturnCapsuleTransitionOverlay::render;

    static float opacity(ReturnCapsuleState state, int phaseTicks, float partialTick) {
        double ticks = Math.max(0.0D, phaseTicks + partialTick);
        return switch (state) {
            case ASCENT_TRANSITION, DEORBIT -> 1.0F;
            case SURFACE_LAUNCHING -> fadeIn(ticks, 84.0D, 100.0D);
            case ORBIT_INSERTION -> fadeOut(ticks, 0.0D, 20.0D);
            case STATION_UNDOCKING -> fadeIn(ticks, 24.0D, 40.0D);
            case REENTRY -> Math.max(fadeOut(ticks, 0.0D, 20.0D), fadeIn(ticks, 84.0D, 100.0D));
            case SURFACE_LANDING -> fadeOut(ticks, 0.0D, 20.0D);
            default -> 0.0F;
        };
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                               GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player != null
                && minecraft.player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)) {
            return;
        }
        float alpha = opacity(capsule.capsuleState(), capsule.phaseTicks(), partialTick);
        if (alpha <= 0.001F) return;
        int packedAlpha = Math.min(255, Math.max(0, Math.round(alpha * 255.0F)));
        graphics.fill(0, 0, screenWidth, screenHeight, packedAlpha << 24);
    }

    private static float fadeIn(double ticks, double from, double to) {
        return smooth((ticks - from) / (to - from));
    }

    private static float fadeOut(double ticks, double from, double to) {
        return 1.0F - smooth((ticks - from) / (to - from));
    }

    private static float smooth(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return (float) (clamped * clamped * (3.0D - 2.0D * clamped));
    }
}
