package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Allocation-free equivalent of Forge's BakedQuad bulk submission for explicitly selected NTM
 * OBJ meshes. Every other consumer and every malformed/non-quad input falls back to Forge.
 */
public final class NtmObjFastRenderer {

    private static final int INTS_PER_VERTEX = 8;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INTS_PER_QUAD = INTS_PER_VERTEX * VERTICES_PER_QUAD;
    private static final IdentityHashMap<List<BakedQuad>, MeshData> CACHE = new IdentityHashMap<>();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NtmObjFastRenderer() {
    }

    /** Returns false before writing anything whenever the exact no-loss contract is unavailable. */
    public static boolean render(PoseStack.Pose pose, VertexConsumer consumer,
                                 List<BakedQuad> quads, int light, int overlay) {
        if (consumer.getClass() != BufferBuilder.class) return false;
        MeshData mesh = CACHE.get(quads);
        if (mesh == null) {
            mesh = decode(quads);
            if (mesh == null) return false;
            CACHE.put(quads, mesh);
        }
        emit(pose, (BufferBuilder) consumer, mesh, light, overlay);
        return true;
    }

    public static void clear() {
        CACHE.clear();
        SCRATCH.remove();
    }

    static int cachedMeshCountForTesting() {
        return CACHE.size();
    }

    private static MeshData decode(List<BakedQuad> quads) {
        int quadCount = quads.size();
        int[] vertices = new int[quadCount * INTS_PER_QUAD];
        byte[] faceNormals = new byte[quadCount * 3];
        for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
            BakedQuad quad = quads.get(quadIndex);
            int[] source = quad.getVertices();
            if (source.length != INTS_PER_QUAD || quad.getDirection() == null) return null;
            System.arraycopy(source, 0, vertices, quadIndex * INTS_PER_QUAD, INTS_PER_QUAD);
            Vec3i normal = quad.getDirection().getNormal();
            int normalOffset = quadIndex * 3;
            faceNormals[normalOffset] = (byte) normal.getX();
            faceNormals[normalOffset + 1] = (byte) normal.getY();
            faceNormals[normalOffset + 2] = (byte) normal.getZ();
        }
        return new MeshData(vertices, faceNormals, quadCount);
    }

    private static void emit(PoseStack.Pose pose, BufferBuilder consumer, MeshData mesh,
                             int light, int overlay) {
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Scratch scratch = SCRATCH.get();
        Vector4f position = scratch.position;
        Vector3f normal = scratch.normal;
        int[] vertices = mesh.vertices;
        byte[] faceNormals = mesh.faceNormals;

        for (int quadIndex = 0; quadIndex < mesh.quadCount; quadIndex++) {
            int normalOffset = quadIndex * 3;
            normal.set(faceNormals[normalOffset], faceNormals[normalOffset + 1],
                    faceNormals[normalOffset + 2]);
            normalMatrix.transform(normal);
            int quadOffset = quadIndex * INTS_PER_QUAD;
            for (int vertexIndex = 0; vertexIndex < VERTICES_PER_QUAD; vertexIndex++) {
                int offset = quadOffset + vertexIndex * INTS_PER_VERTEX;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);
                int color = vertices[offset + 3];
                float red = (color & 0xFF) / 255.0F;
                float green = ((color >>> 8) & 0xFF) / 255.0F;
                float blue = ((color >>> 16) & 0xFF) / 255.0F;
                float alpha = ((color >>> 24) & 0xFF) / 255.0F;
                float u = Float.intBitsToFloat(vertices[offset + 4]);
                float v = Float.intBitsToFloat(vertices[offset + 5]);
                int bakedLight = vertices[offset + 6];
                int combinedLight = Math.max(light & 0xFFFF, bakedLight & 0xFFFF)
                        | Math.max((light >> 16) & 0xFFFF,
                        (bakedLight >> 16) & 0xFFFF) << 16;

                position.set(x, y, z, 1.0F);
                poseMatrix.transform(position);

                int packedNormal = vertices[offset + 7];
                byte normalX = (byte) packedNormal;
                byte normalY = (byte) (packedNormal >>> 8);
                byte normalZ = (byte) (packedNormal >>> 16);
                if (normalX != 0 || normalY != 0 || normalZ != 0) {
                    normal.set(normalX / 127.0F, normalY / 127.0F, normalZ / 127.0F);
                    normal.mul(normalMatrix);
                }

                consumer.vertex(position.x(), position.y(), position.z(), red, green, blue, alpha,
                        u, v, overlay, combinedLight, normal.x(), normal.y(), normal.z());
            }
        }
    }

    private record MeshData(int[] vertices, byte[] faceNormals, int quadCount) {
    }

    private static final class Scratch {
        private final Vector4f position = new Vector4f();
        private final Vector3f normal = new Vector3f();
    }
}
