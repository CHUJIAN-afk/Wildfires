/*
 * Adapted from VS: Genesis ShaderRegistry and planet renderers.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Wildfires modifications: uses Forge RegisterShadersEvent directly, removes Lodestone and VS2,
 * and exposes only the two shaders needed by the fixed Wildfires orbit renderer.
 */
package first.wildfires.thirdparty.genesisadapt;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import first.wildfires.Wildfires;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.function.Supplier;

/** Forge-managed shader handles for the isolated Genesis-derived rendering partition. */
public final class GenesisPlanetShader {

    private static TexturedBindings textured;
    private static AtmosphereBindings atmosphere;

    private GenesisPlanetShader() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("genesis_planet_textured"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                loaded -> textured = TexturedBindings.create(loaded));
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("genesis_planet_atmosphere"),
                        DefaultVertexFormat.POSITION_COLOR),
                loaded -> atmosphere = AtmosphereBindings.create(loaded));
    }

    public static ShaderInstance textured() {
        return textured == null ? null : textured.shader();
    }

    public static ShaderInstance atmosphere() {
        return atmosphere == null ? null : atmosphere.shader();
    }

    public static TexturedBindings texturedBindings() {
        return textured;
    }

    public static AtmosphereBindings atmosphereBindings() {
        return atmosphere;
    }

    public static final class TexturedBindings implements Supplier<ShaderInstance> {
        private final ShaderInstance shader;
        private final AbstractUniform lightDirection;
        private final AbstractUniform alpha;
        private final AbstractUniform layerColor;
        private final AbstractUniform receiverRadius;
        private final SatelliteShadowBindings satelliteShadows;

        private TexturedBindings(ShaderInstance shader) {
            this.shader = shader;
            this.lightDirection = shader.safeGetUniform("LightDirection");
            this.alpha = shader.safeGetUniform("Alpha");
            this.layerColor = shader.safeGetUniform("LayerColor");
            this.receiverRadius = shader.safeGetUniform("ReceiverRadius");
            this.satelliteShadows = new SatelliteShadowBindings(shader);
        }

        private static TexturedBindings create(ShaderInstance shader) {
            return new TexturedBindings(shader);
        }

        public ShaderInstance shader() {
            return shader;
        }

        public AbstractUniform lightDirection() {
            return lightDirection;
        }

        public AbstractUniform alpha() {
            return alpha;
        }

        public AbstractUniform layerColor() {
            return layerColor;
        }

        public AbstractUniform receiverRadius() {
            return receiverRadius;
        }

        public SatelliteShadowBindings satelliteShadows() {
            return satelliteShadows;
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }

    public static final class AtmosphereBindings implements Supplier<ShaderInstance> {
        private final ShaderInstance shader;
        private final AbstractUniform cameraPosition;
        private final AbstractUniform lightDirection;
        private final AbstractUniform dayColor;
        private final AbstractUniform sunsetColor;
        private final AbstractUniform nightColor;
        private final AbstractUniform regionBrightness;
        private final AbstractUniform lightTransitions;
        private final AbstractUniform limbParameters;
        private final AbstractUniform opacityExposure;
        private final AbstractUniform atmosphereThickness;
        private final AbstractUniform density;
        private final AbstractUniform alpha;
        private final SatelliteShadowBindings satelliteShadows;

        private AtmosphereBindings(ShaderInstance shader) {
            this.shader = shader;
            this.cameraPosition = shader.safeGetUniform("CameraPosition");
            this.lightDirection = shader.safeGetUniform("LightDirection");
            this.dayColor = shader.safeGetUniform("DayColor");
            this.sunsetColor = shader.safeGetUniform("SunsetColor");
            this.nightColor = shader.safeGetUniform("NightColor");
            this.regionBrightness = shader.safeGetUniform("RegionBrightness");
            this.lightTransitions = shader.safeGetUniform("LightTransitions");
            this.limbParameters = shader.safeGetUniform("LimbParameters");
            this.opacityExposure = shader.safeGetUniform("OpacityExposure");
            this.atmosphereThickness = shader.safeGetUniform("AtmosphereThickness");
            this.density = shader.safeGetUniform("Density");
            this.alpha = shader.safeGetUniform("Alpha");
            this.satelliteShadows = new SatelliteShadowBindings(shader);
        }

        private static AtmosphereBindings create(ShaderInstance shader) {
            return new AtmosphereBindings(shader);
        }

        public ShaderInstance shader() {
            return shader;
        }

        public AbstractUniform cameraPosition() {
            return cameraPosition;
        }

        public AbstractUniform lightDirection() {
            return lightDirection;
        }

        public AbstractUniform dayColor() {
            return dayColor;
        }

        public AbstractUniform sunsetColor() {
            return sunsetColor;
        }

        public AbstractUniform nightColor() {
            return nightColor;
        }

        public AbstractUniform regionBrightness() {
            return regionBrightness;
        }

        public AbstractUniform lightTransitions() {
            return lightTransitions;
        }

        public AbstractUniform limbParameters() {
            return limbParameters;
        }

        public AbstractUniform opacityExposure() {
            return opacityExposure;
        }

        public AbstractUniform atmosphereThickness() {
            return atmosphereThickness;
        }

        public AbstractUniform density() {
            return density;
        }

        public AbstractUniform alpha() {
            return alpha;
        }

        public SatelliteShadowBindings satelliteShadows() {
            return satelliteShadows;
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }

    public static final class SatelliteShadowBindings {
        private static final int MAX_SHADOWS = 4;

        private final AbstractUniform shadowCount;
        private final AbstractUniform sunHalfTangent;
        private final AbstractUniform[] centers;
        private final AbstractUniform[] halfSizes;
        private final AbstractUniform[] axesX;
        private final AbstractUniform[] axesY;
        private final AbstractUniform[] axesZ;

        private SatelliteShadowBindings(ShaderInstance shader) {
            this.shadowCount = shader.safeGetUniform("ShadowCount");
            this.sunHalfTangent = shader.safeGetUniform("SunHalfTangent");
            this.centers = uniforms(shader, "ShadowCenter");
            this.halfSizes = uniforms(shader, "ShadowHalfSize");
            this.axesX = uniforms(shader, "ShadowAxisX");
            this.axesY = uniforms(shader, "ShadowAxisY");
            this.axesZ = uniforms(shader, "ShadowAxisZ");
        }

        public AbstractUniform shadowCount() {
            return shadowCount;
        }

        public AbstractUniform sunHalfTangent() {
            return sunHalfTangent;
        }

        public AbstractUniform center(int index) {
            return centers[index];
        }

        public AbstractUniform halfSize(int index) {
            return halfSizes[index];
        }

        public AbstractUniform axisX(int index) {
            return axesX[index];
        }

        public AbstractUniform axisY(int index) {
            return axesY[index];
        }

        public AbstractUniform axisZ(int index) {
            return axesZ[index];
        }

        private static AbstractUniform[] uniforms(ShaderInstance shader, String prefix) {
            AbstractUniform[] uniforms = new AbstractUniform[MAX_SHADOWS];
            for (int index = 0; index < uniforms.length; index++) {
                uniforms[index] = shader.safeGetUniform(prefix + index);
            }
            return uniforms;
        }
    }
}
