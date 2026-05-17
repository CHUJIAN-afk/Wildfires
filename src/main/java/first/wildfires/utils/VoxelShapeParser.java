package first.wildfires.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.Tags;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 体素解析辅助类
 * 通过 ResourceLocation 获取方块 JSON 模型，解析成体素形状（VoxelShape）。
 * 使用静态缓存避免重复解析。
 */
public class VoxelShapeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoxelShapeParser.class);

    // 静态缓存：模型路径 -> 体素形状
    private static final ConcurrentHashMap<ResourceLocation, VoxelShape> SHAPE_CACHE = new ConcurrentHashMap<>();
    // 变换缓存：缓存键 -> 变换后的体素形状
    private static final ConcurrentHashMap<String, VoxelShape> TRANSFORM_CACHE = new ConcurrentHashMap<>();

    /**
     * 从 ResourceLocation 获取或解析体素形状
     * @param location 模型的资源位置，如 "wildfires:block/loom"
     * @return 解析后的 VoxelShape，如果解析失败返回完整方块形状
     */
    public static VoxelShape getOrParse(ResourceLocation location) {
        return SHAPE_CACHE.computeIfAbsent(location, VoxelShapeParser::parse);
    }

    /**
     * 获取变换后的体素形状（带缓存）
     * @param location 模型的资源位置
     * @param rotation 旋转方向（可为null表示不旋转）
     * @param offsetX X轴偏移（可为0）
     * @param offsetY Y轴偏移（可为0）
     * @param offsetZ Z轴偏移（可为0）
     * @return 变换后的 VoxelShape（已缓存）
     */
    public static VoxelShape getOrParseTransformed(ResourceLocation location, Direction rotation, double offsetX, double offsetY, double offsetZ) {
        String cacheKey = buildCacheKey(location, rotation, offsetX, offsetY, offsetZ);
        VoxelShape voxelShape = TRANSFORM_CACHE.computeIfAbsent(cacheKey, k -> {
            VoxelShape shape = getOrParse(location);
            if (shape == Shapes.block()){
                return shape;
            }
            if (rotation != null) {
                shape = rotate(shape, rotation);
            }
            if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
                shape = offset(shape, offsetX, offsetY, offsetZ);
            }
            return shape.optimize();
        });
        if (voxelShape == Shapes.block()) {
            SHAPE_CACHE.remove(location);
            TRANSFORM_CACHE.remove(cacheKey);
        }
        return voxelShape;
    }

    /**
     * 构建缓存键
     */
    private static String buildCacheKey(ResourceLocation location, Direction rotation, double offsetX, double offsetY, double offsetZ) {
        return location.toString() + "|" + (rotation != null ? rotation.name() : "none") + "|" + offsetX + "|" + offsetY + "|" + offsetZ;
    }

    /**
     * 获取体素形状并应用偏移
     * @param location 模型的资源位置
     * @param x X轴偏移（方块坐标）
     * @param y Y轴偏移（方块坐标）
     * @param z Z轴偏移（方块坐标）
     * @return 偏移后的 VoxelShape
     */
    public static VoxelShape getOrParseWithOffset(ResourceLocation location, double x, double y, double z) {
        VoxelShape shape = getOrParse(location);
        return offset(shape, x, y, z);
    }

    /**
     * 获取体素形状并应用旋转
     * @param location 模型的资源位置
     * @param rotation 旋转方向
     * @return 旋转后的 VoxelShape
     */
    public static VoxelShape getOrParseWithRotation(ResourceLocation location, Direction rotation) {
        VoxelShape shape = getOrParse(location);
        return rotate(shape, rotation);
    }

    /**
     * 获取体素形状并应用偏移和旋转
     * @param location 模型的资源位置
     * @param x X轴偏移（方块坐标）
     * @param y Y轴偏移（方块坐标）
     * @param z Z轴偏移（方块坐标）
     * @param rotation 旋转方向
     * @return 变换后的 VoxelShape
     */
    public static VoxelShape getOrParseWithOffsetAndRotation(ResourceLocation location, double x, double y, double z, Direction rotation) {
        VoxelShape shape = getOrParse(location);
        shape = rotate(shape, rotation);
        return offset(shape, x, y, z);
    }

    // ========== 体素形状变换方法 ==========

    /**
     * 偏移体素形状
     * @param shape 原始形状
     * @param x X轴偏移（方块坐标）
     * @param y Y轴偏移（方块坐标）
     * @param z Z轴偏移（方块坐标）
     * @return 偏移后的形状
     */
    public static VoxelShape offset(VoxelShape shape, double x, double y, double z) {
        return shape.move(x, y, z);
    }

    /**
     * 旋转体素形状（围绕Y轴）
     * @param shape 原始形状
     * @param rotation 旋转方向
     * @return 旋转后的形状
     */
    public static VoxelShape rotate(VoxelShape shape, Direction rotation) {
        return switch (rotation) {
            case NORTH -> rotateY(shape, 180);
            case SOUTH -> shape;
            case WEST -> rotateY(shape, 90);
            case EAST -> rotateY(shape, 270);
            case UP -> rotateX(shape, 270);
            case DOWN -> rotateX(shape, 90);
        };
    }

    /**
     * 围绕Y轴旋转体素形状
     * @param shape 原始形状
     * @param degrees 旋转角度（90, 180, 270）
     * @return 旋转后的形状
     */
    public static VoxelShape rotateY(VoxelShape shape, int degrees) {
        if (degrees == 0 || degrees % 90 != 0) {
            return shape;
        }

        int rotations = (degrees / 90) % 4;
        for (int i = 0; i < rotations; i++) {
            shape = rotateY90(shape);
        }
        return shape.optimize();
    }

    /**
     * 围绕Y轴旋转90度
     */
    private static VoxelShape rotateY90(VoxelShape shape) {
        List<VoxelShape> boxes = new ArrayList<>();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            boxes.add(Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2));
        });
        return mergeShapes(boxes);
    }

    /**
     * 围绕X轴旋转体素形状
     * @param shape 原始形状
     * @param degrees 旋转角度（90, 180, 270）
     * @return 旋转后的形状
     */
    public static VoxelShape rotateX(VoxelShape shape, int degrees) {
        if (degrees == 0 || degrees % 90 != 0) {
            return shape;
        }

        int rotations = (degrees / 90) % 4;
        for (int i = 0; i < rotations; i++) {
            shape = rotateX90(shape);
        }
        return shape.optimize();
    }

    /**
     * 围绕X轴旋转90度
     */
    private static VoxelShape rotateX90(VoxelShape shape) {
        List<VoxelShape> boxes = new ArrayList<>();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            boxes.add(Shapes.box(x1, 1 - z2, y1, x2, 1 - z1, y2));
        });
        return mergeShapes(boxes);
    }

    /**
     * 围绕Z轴旋转体素形状
     * @param shape 原始形状
     * @param degrees 旋转角度（90, 180, 270）
     * @return 旋转后的形状
     */
    public static VoxelShape rotateZ(VoxelShape shape, int degrees) {
        if (degrees == 0 || degrees % 90 != 0) {
            return shape;
        }

        int rotations = (degrees / 90) % 4;
        for (int i = 0; i < rotations; i++) {
            shape = rotateZ90(shape);
        }
        return shape.optimize();
    }

    /**
     * 围绕Z轴旋转90度
     */
    private static VoxelShape rotateZ90(VoxelShape shape) {
        List<VoxelShape> boxes = new ArrayList<>();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            boxes.add(Shapes.box(1 - y2, x1, z1, 1 - y1, x2, z2));
        });
        return mergeShapes(boxes);
    }

    /**
     * 缩放体素形状
     * @param shape 原始形状
     * @param scale 缩放比例
     * @return 缩放后的形状
     */
    public static VoxelShape scale(VoxelShape shape, double scale) {
        return scale(shape, scale, scale, scale);
    }

    /**
     * 缩放体素形状
     * @param shape 原始形状
     * @param scaleX X轴缩放比例
     * @param scaleY Y轴缩放比例
     * @param scaleZ Z轴缩放比例
     * @return 缩放后的形状
     */
    public static VoxelShape scale(VoxelShape shape, double scaleX, double scaleY, double scaleZ) {
        List<VoxelShape> boxes = new ArrayList<>();
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            boxes.add(Shapes.box(
                    x1 * scaleX, y1 * scaleY, z1 * scaleZ,
                    x2 * scaleX, y2 * scaleY, z2 * scaleZ
            ));
        });
        return mergeShapes(boxes).optimize();
    }

    /**
     * 合并多个体素形状
     */
    private static VoxelShape mergeShapes(List<VoxelShape> boxes) {
        VoxelShape result = Shapes.empty();
        for (VoxelShape box : boxes) {
            result = Shapes.join(result, box, BooleanOp.OR);
        }
        return result;
    }

    // ========== 私有方法 ==========

    /**
     * 解析模型 JSON 文件生成体素形状
     */
    private static VoxelShape parse(ResourceLocation location) {
        try {
            ResourceLocation modelPath = ResourceLocation.fromNamespaceAndPath(
                    location.getNamespace(),
                    "models/" + location.getPath() + ".json"
            );
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return Shapes.block();

            Resource resource = server.getResourceManager()
                    .getResource(modelPath)
                    .orElse(null);

            if (resource == null) {
                LOGGER.warn("Model resource not found: {}", modelPath);
                return Shapes.block();
            }

            JsonObject json;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            if (!json.has("elements")) {
                LOGGER.warn("No elements found in model: {}", modelPath);
                return Shapes.block();
            }

            VoxelShape result = Shapes.empty();
            JsonArray elements = json.getAsJsonArray("elements");

            for (JsonElement element : elements) {
                JsonObject elementObj = element.getAsJsonObject();
                VoxelShape elementShape = parseElement(elementObj);
                result = Shapes.join(result, elementShape, BooleanOp.OR);
            }

            LOGGER.debug("Parsed VoxelShape for model: {}", modelPath);
            return result.optimize();

        } catch (Exception e) {
            LOGGER.error("Failed to parse VoxelShape for model: {}", location, e);
            return Shapes.block();
        }
    }

    /**
     * 解析单个元素
     */private static VoxelShape parseElement(JsonObject element) {
        float[] from = {0, 0, 0};
        if (element.has("from")) {
            JsonArray fromArray = element.getAsJsonArray("from");
            from[0] = fromArray.get(0).getAsFloat();
            from[1] = fromArray.get(1).getAsFloat();
            from[2] = fromArray.get(2).getAsFloat();
        }

        float[] to = {16, 16, 16};
        if (element.has("to")) {
            JsonArray toArray = element.getAsJsonArray("to");
            to[0] = toArray.get(0).getAsFloat();
            to[1] = toArray.get(1).getAsFloat();
            to[2] = toArray.get(2).getAsFloat();
        }

        double x1 = Math.min(from[0], to[0]) / 16.0;
        double y1 = Math.min(from[1], to[1]) / 16.0;
        double z1 = Math.min(from[2], to[2]) / 16.0;
        double x2 = Math.max(from[0], to[0]) / 16.0;
        double y2 = Math.max(from[1], to[1]) / 16.0;
        double z2 = Math.max(from[2], to[2]) / 16.0;

        return Shapes.box(x1, y1, z1, x2, y2, z2);
    }

    // ========== 缓存管理 ==========

    /**
     * 清除所有缓存
     */
    public static void clearCache() {
        SHAPE_CACHE.clear();
        TRANSFORM_CACHE.clear();
        LOGGER.debug("VoxelShapeParser cache cleared");
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return SHAPE_CACHE.size() + TRANSFORM_CACHE.size();
    }
}
