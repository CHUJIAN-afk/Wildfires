package first.wildfires.client.space.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import first.wildfires.Wildfires;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.thirdparty.genesisadapt.GenesisCubeAtlasLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves supplied surface cubemaps and owns generated fallbacks until reload or logout. */
public final class OrbitBodyTextureManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CELL_SIZE = 256;
    private static final int WIDTH = CELL_SIZE * GenesisCubeAtlasLayout.COLUMNS;
    private static final int HEIGHT = CELL_SIZE * GenesisCubeAtlasLayout.ROWS;
    private static final Map<Key, ResolvedTexture> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> GENERATED = new LinkedHashSet<>();
    private static long cachedGeneration = Long.MIN_VALUE;

    private OrbitBodyTextureManager() {
    }

    public static ResolvedTexture surface(ResourceLocation body, CelestialVisualDefinition visual,
                                          long generation) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(visual, "visual");
        prepareGeneration(generation);
        Key key = new Key(body, visual.surfaceAtlas().orElse(null), "surface");
        return CACHE.computeIfAbsent(key, ignored -> resolveSurface(body, visual));
    }

    public static ResolvedTexture clouds(ResourceLocation body,
                                         CelestialVisualDefinition.CloudLayer clouds,
                                         long generation) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(clouds, "clouds");
        prepareGeneration(generation);
        Key key = new Key(body, clouds.texture().orElse(null), "cloud");
        return CACHE.computeIfAbsent(key, ignored -> resolveClouds(body, clouds));
    }

    private static ResolvedTexture resolveSurface(ResourceLocation body, CelestialVisualDefinition visual) {
        boolean resourceExists = visual.surfaceAtlas()
                .filter(id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent())
                .isPresent();
        if (OrbitTextureRules.surface(visual, resourceExists)
                == OrbitTextureRules.SourceChoice.DIRECT_CUBE_ATLAS) {
            ResourceLocation location = visual.surfaceAtlas().orElseThrow();
            Minecraft.getInstance().getTextureManager().getTexture(location).setFilter(false, false);
            return new ResolvedTexture(location, false, false);
        }
        if (visual.surfaceAtlas().isPresent()) {
            LOGGER.warn("Wildfires orbit surface {} is missing or does not use the Genesis 3x2 contract; "
                            + "using the deterministic surface fallback",
                    visual.surfaceAtlas().orElseThrow());
        }
        return registerGenerated(body, generate(body), true, "surface");
    }

    private static ResolvedTexture resolveClouds(ResourceLocation body,
                                                  CelestialVisualDefinition.CloudLayer clouds) {
        ResourceLocation source = clouds.texture().orElse(null);
        boolean resourceExists = source != null
                && Minecraft.getInstance().getResourceManager().getResource(source).isPresent();
        if (resourceExists && clouds.mapping() == CelestialVisualDefinition.CloudMapping.CUBE_ATLAS) {
            Minecraft.getInstance().getTextureManager().getTexture(source).setFilter(false, false);
            return new ResolvedTexture(source, false, false);
        }
        if (resourceExists && clouds.mapping() == CelestialVisualDefinition.CloudMapping.EQUIRECTANGULAR) {
            try (InputStream stream = Minecraft.getInstance().getResourceManager()
                    .getResource(source).orElseThrow().open()) {
                NativeImage sourceImage = NativeImage.read(stream);
                try {
                    return registerGenerated(body, reprojectClouds(sourceImage), true, "cloud");
                } finally {
                    sourceImage.close();
                }
            } catch (IOException exception) {
                LOGGER.warn("Unable to read orbital cloud texture {}; using procedural fallback",
                        source, exception);
            }
        } else if (source != null && !resourceExists) {
            LOGGER.warn("Wildfires orbit cloud texture {} is missing; using procedural fallback", source);
        }
        return registerGenerated(body, generateClouds(body), true, "cloud");
    }

    private static NativeImage generate(ResourceLocation body) {
        NativeImage output = new NativeImage(NativeImage.Format.RGBA, WIDTH, HEIGHT, false);
        for (GenesisCubeAtlasLayout.Face face : GenesisCubeAtlasLayout.FACES) {
            for (int y = 0; y < CELL_SIZE; y++) {
                for (int x = 0; x < CELL_SIZE; x++) {
                    GenesisCubeAtlasLayout.Direction direction = GenesisCubeAtlasLayout.direction(face,
                            (x + 0.5D) / CELL_SIZE, (y + 0.5D) / CELL_SIZE);
                    OrbitProceduralTexture.Rgba color = OrbitProceduralTexture.surface(body, direction);
                    output.setPixelRGBA(face.column() * CELL_SIZE + x, face.row() * CELL_SIZE + y,
                            abgr(color));
                }
            }
        }
        return output;
    }

    private static NativeImage generateClouds(ResourceLocation body) {
        NativeImage output = new NativeImage(NativeImage.Format.RGBA, WIDTH, HEIGHT, false);
        for (GenesisCubeAtlasLayout.Face face : GenesisCubeAtlasLayout.FACES) {
            for (int y = 0; y < CELL_SIZE; y++) {
                for (int x = 0; x < CELL_SIZE; x++) {
                    GenesisCubeAtlasLayout.Direction direction = GenesisCubeAtlasLayout.direction(face,
                            (x + 0.5D) / CELL_SIZE, (y + 0.5D) / CELL_SIZE);
                    output.setPixelRGBA(face.column() * CELL_SIZE + x, face.row() * CELL_SIZE + y,
                            abgr(OrbitProceduralTexture.cloud(body, direction)));
                }
            }
        }
        return output;
    }

    private static NativeImage reprojectClouds(NativeImage source) {
        NativeImage output = new NativeImage(NativeImage.Format.RGBA, WIDTH, HEIGHT, false);
        for (GenesisCubeAtlasLayout.Face face : GenesisCubeAtlasLayout.FACES) {
            for (int y = 0; y < CELL_SIZE; y++) {
                for (int x = 0; x < CELL_SIZE; x++) {
                    GenesisCubeAtlasLayout.Direction direction = GenesisCubeAtlasLayout.direction(face,
                            (x + 0.5D) / CELL_SIZE, (y + 0.5D) / CELL_SIZE);
                    double longitude = Math.atan2(direction.z(), direction.x());
                    double latitude = Math.asin(Math.max(-1.0D, Math.min(1.0D, direction.y())));
                    int sampleX = Math.floorMod((int) Math.floor((longitude / (Math.PI * 2.0D) + 0.5D)
                            * source.getWidth()), source.getWidth());
                    int sampleY = Math.max(0, Math.min(source.getHeight() - 1,
                            (int) Math.floor((0.5D - latitude / Math.PI) * source.getHeight())));
                    output.setPixelRGBA(face.column() * CELL_SIZE + x, face.row() * CELL_SIZE + y,
                            source.getPixelRGBA(sampleX, sampleY));
                }
            }
        }
        return output;
    }

    private static ResolvedTexture registerGenerated(ResourceLocation body, NativeImage image,
                                                     boolean fallback, String layer) {
        RenderSystem.assertOnRenderThread();
        String path = "dynamic/orbit/" + layer + "/" + body.getNamespace() + "/"
                + body.getPath().replace('/', '_') + "_" + Long.toUnsignedString(cachedGeneration);
        ResourceLocation id = Wildfires.rl(path);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        GENERATED.add(id);
        return new ResolvedTexture(id, true, fallback);
    }

    private static int abgr(OrbitProceduralTexture.Rgba color) {
        return channel(color.alpha()) << 24 | channel(color.blue()) << 16
                | channel(color.green()) << 8 | channel(color.red());
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * 255.0D);
    }

    private static void prepareGeneration(long generation) {
        RenderSystem.assertOnRenderThread();
        if (generation < 0L) {
            throw new IllegalArgumentException("Texture generation must be non-negative");
        }
        if (cachedGeneration != generation) {
            reset();
            cachedGeneration = generation;
        }
    }

    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(OrbitBodyTextureManager::reset);
            return;
        }
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation id : GENERATED) {
            manager.release(id);
        }
        GENERATED.clear();
        CACHE.clear();
        cachedGeneration = Long.MIN_VALUE;
    }

    public record ResolvedTexture(ResourceLocation location, boolean generated,
                                  boolean proceduralFallback) {
        public ResolvedTexture {
            Objects.requireNonNull(location, "location");
        }
    }

    private record Key(ResourceLocation body, ResourceLocation source, String layer) {
    }
}
