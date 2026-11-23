package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A GeckoLib bone that draws pre-built quads (lofted frustums) instead of cuboids. GeckoLib still does everything
 * else with it: pivot, rotation by the bone snapshot, children, animation. GeoBone's constructor is protected and
 * render() is abstract, so a subclass is the supported way (CuboidGeoBone is just another subclass).
 */
final class LoftBone extends GeoBone {
    private final GeoQuad[] quads;

    LoftBone(GeoBone parent, String name, GeoBone[] children, float px, float py, float pz, GeoQuad[] quads) {
        super(parent, name, children, new com.geckolib.cache.model.GeoLocator[0], px * 16f, py * 16f, pz * 16f, 0f, 0f, 0f);
        this.quads = quads;
    }

    LoftBone(GeoBone parent, String name, GeoBone[] children, float px, float py, float pz, List<Frustum> frustums, float yOffset) {
        super(parent, name, children, new com.geckolib.cache.model.GeoLocator[0], px * 16f, py * 16f, pz * 16f, 0f, 0f, 0f);
        List<GeoQuad> list = new ArrayList<>();
        for (Frustum f : frustums) {
            for (int i = 0; i < f.n; i++) {
                float[] q = f.quads[i];
                GeoVertex[] v = new GeoVertex[4];
                for (int k = 0; k < 4; k++) v[k] = new GeoVertex(q[k * 5], q[k * 5 + 1] + yOffset, q[k * 5 + 2], q[k * 5 + 3], q[k * 5 + 4]);
                float[] n = f.normals[i];
                list.add(new GeoQuad(v, n[0], n[1], n[2], Direction.getApproximateNearest(n[0], n[1], n[2])));
            }
        }
        this.quads = list.toArray(new GeoQuad[0]);
    }

    int quadCount() {
        return quads.length;
    }

    @Override
    public <R extends GeoRenderState> void render(RenderPassInfo<R> info, PoseStack poseStack, VertexConsumer buffer,
                                                  int packedLight, int packedOverlay, int renderColor) {
        if (this.frameSnapshot != null && this.frameSnapshot.isHidden()) return;
        poseStack.pushPose();
        Matrix3f normal = poseStack.last().normal();
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        for (GeoQuad quad : quads) {
            Vector3f n = normal.transform(quad.normalVec());
            quad.render(pose, n, buffer, packedLight, packedOverlay, renderColor);
        }
        poseStack.popPose();
    }
}
