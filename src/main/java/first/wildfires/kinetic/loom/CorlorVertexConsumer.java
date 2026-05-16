package first.wildfires.kinetic.loom;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * VertexConsumer代理类，强制使用自定义颜色渲染
 */
public class CorlorVertexConsumer implements VertexConsumer {

    private final VertexConsumer base;
    private final int r, g, b, a;

    public CorlorVertexConsumer(VertexConsumer base, int r, int g, int b, int a) {
        this.base = base;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    /**
     * 使用ARGB格式的颜色创建
     */
    public CorlorVertexConsumer(VertexConsumer base, int argb) {
        this.base = base;
        this.a = (argb >> 24) & 0xFF;
        this.r = (argb >> 16) & 0xFF;
        this.g = (argb >> 8) & 0xFF;
        this.b = argb & 0xFF;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        base.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // 忽略传入的颜色，使用自定义颜色
        base.color(this.r, this.g, this.b, this.a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        base.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        base.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        base.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        base.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        base.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        base.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        base.unsetDefaultColor();
    }
}