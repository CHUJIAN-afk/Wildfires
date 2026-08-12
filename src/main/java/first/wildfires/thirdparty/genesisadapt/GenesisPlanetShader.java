/*
 * Adapted from VS: Genesis ShaderRegistry and planet renderers.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Wildfires modifications: uses Forge RegisterShadersEvent directly, removes Lodestone and VS2,
 * and exposes only the two shaders needed by the fixed Wildfires orbit renderer.
 */
package first.wildfires.thirdparty.genesisadapt;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Forge-managed shader handles for the isolated Genesis-derived rendering partition. */
public final class GenesisPlanetShader {

    private static ShaderInstance textured;
    private static ShaderInstance atmosphere;

    private GenesisPlanetShader() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("genesis_planet_textured"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                loaded -> textured = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("genesis_planet_atmosphere"),
                        DefaultVertexFormat.POSITION_COLOR),
                loaded -> atmosphere = loaded);
    }

    public static ShaderInstance textured() {
        return textured;
    }

    public static ShaderInstance atmosphere() {
        return atmosphere;
    }
}
