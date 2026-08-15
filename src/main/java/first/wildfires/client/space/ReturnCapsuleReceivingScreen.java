/*
 * Adapted from VS: Genesis TransitionScreen.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236.
 * SPDX-License-Identifier: Apache-2.0
 * Wildfires changes: retains the exact capsule departure/entry frame until the reliable
 * destination remount handshake completes instead of showing Minecraft's receiving-level screen.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.NotNull;

/** Shows the captured in-flight scene until the target world's first safe frame is ready. */
public final class ReturnCapsuleReceivingScreen extends ReceivingLevelScreen {

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** The server-confirmed capsule handshake, not vanilla chunk heuristics, owns closure. */
    @Override
    public void tick() {
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // GameRenderer has already drawn the target level behind this screen. Once the exact
        // passenger graph and (for orbit) the Genesis square body are proven, leave that live
        // frame visible while the last server commit packet completes instead of covering it with
        // an older departure capture.
        if (ReturnCapsuleClientTransition.targetSceneReadyForPreview()) {
            return;
        }
        var target = ReturnCapsuleClientTransition.captured();
        if (target == null) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(0, height, 0).uv(0, 0).endVertex();
        buffer.vertex(width, height, 0).uv(1, 0).endVertex();
        buffer.vertex(width, 0, 0).uv(1, 1).endVertex();
        buffer.vertex(0, 0, 0).uv(0, 1).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (ReturnCapsuleClientTransition.timedOut()) {
            graphics.drawCenteredString(font,
                    net.minecraft.network.chat.Component.translatable(
                            "space.wildfires.return_capsule.transition_stalled"),
                    width / 2, height / 2 + 28, 0xFFFF7777);
        }
    }

    @Override
    public void onClose() {
    }

    @Override
    public void removed() {
    }
}
