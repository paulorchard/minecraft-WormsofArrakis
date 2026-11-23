package com.paulorchard.islandcraft.wormsofarrakis.test.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.paulorchard.islandcraft.wormsofarrakis.WormsOfArrakis;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ChainMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.Frustum;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestAnim;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestWormEntity;
import com.paulorchard.islandcraft.wormsofarrakis.test.WormSpec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renderer of the test worm (GeckoLib GeoEntityRenderer).
 * Per-frame hook for bones: adjustModelBonesForRender (GeoRenderer default method, called once per render pass with
 * the BoneSnapshots of this pass). Closer "loft" bypasses the bone model and submits generated quads through
 * SubmitNodeCollector.submitCustomGeometry.
 */
public class TestWormRenderer extends GeoEntityRenderer<TestWormEntity, EntityRenderState> {
    static final DataTicket<String> SPEC = DataTicket.create("arrakis_spec", String.class);
    static final DataTicket<Float> YAW = DataTicket.create("arrakis_yaw", Float.class);
    static final DataTicket<Double> AGE = DataTicket.create("arrakis_age", Double.class);

    private static final Map<String, Double> REACH = new HashMap<>();

    public TestWormRenderer(EntityRendererProvider.Context context) {
        super(context, new TestWormModel());
    }

    @Override
    public void addRenderData(TestWormEntity entity, Void relatedObject, EntityRenderState state, float partialTick) {
        state.addGeckolibData(SPEC, entity.getSpecString());
        state.addGeckolibData(YAW, entity.getYRot());
        state.addGeckolibData(AGE, entity.tickCount + (double) partialTick);
    }

    /** Back faces culled (entityCutoutCull): the faces are one-sided, nothing shows from inside the tube. */
    @Override
    public RenderType getRenderType(EntityRenderState state, Identifier texture) {
        return RenderTypes.entityCutoutCull(texture);
    }

    /**
     * A 48-wide, 100-segment worm is hundreds of blocks long, far outside a normal entity box. The vanilla frustum test
     * uses this box, so it is made loose enough to cover everything the spec draws from every horizontal heading.
     */
    @Override
    protected AABB getBoundingBoxForCulling(TestWormEntity entity) {
        WormSpec spec = WormSpec.parse(entity.getSpecString());
        double reach = REACH.computeIfAbsent(spec.serialize(), k -> {
            int n = WormModels.faces(spec);
            return TestAnim.reach(spec, WormMath.circumradius(n, WormMath.FACE_WIDTH));
        });
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        return new AABB(x - reach, y - 1, z - reach, x + reach, y + reach, z + reach);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
        // plain Entity has no body rotation of its own: rotate the model so model -Z (head side) is the entity's forward
        info.poseStack().mulPose(Axis.YP.rotationDegrees(-info.renderState().getGeckolibData(YAW)));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> info, BoneSnapshots bones) {
        WormSpec spec = WormSpec.parse(info.renderState().getGeckolibData(SPEC));
        if (spec.kind().equals("mouth")) {
            // the frame bones only orient each flap (120 degrees apart); the flaps themselves are moved by the GeckoLib animation
            for (int j = 0; j < MouthModel.FLAPS; j++) {
                final float rot = (float) (2 * Math.PI * j / MouthModel.FLAPS);
                bones.ifPresent("flap_frame_" + j, snap -> snap.setRotation(0f, 0f, rot));
            }
        }
        if (spec.kind().equals("chain") || spec.kind().equals("wave")) {
            double[][] a = TestAnim.angles(spec, info.renderState().getGeckolibData(AGE));
            int segs = a[0].length;
            for (int k = 1; k < segs; k++) {
                final int kk = k;
                bones.ifPresent("seg_" + k, snap -> snap.setRotation((float) a[1][kk], (float) a[0][kk], 0f));
            }
        }
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        WormSpec spec = WormSpec.parse(state.getGeckolibData(SPEC));
        if ((spec.kind().equals("chain") || spec.kind().equals("wave")) && spec.mode().equals("loft")) {
            submitLoft(spec, state, poseStack, collector);
            return;
        }
        super.submit(state, poseStack, collector, camera);
    }

    /** Closer c: code-generated skin. Every ring is a mitre ring shared by the two segments around a joint. */
    private void submitLoft(WormSpec spec, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int n = WormModels.faces(spec);
        double r = WormMath.circumradius(n, WormMath.FACE_WIDTH);
        int segs = (int) spec.p(1, 12);
        double len = spec.p(4, 2), taper = spec.p(5, 1);
        double[] radius = new double[segs + 1];
        for (int j = 0; j <= segs; j++) radius[j] = r * Math.pow(taper, j);
        double[][] a = TestAnim.angles(spec, state.getGeckolibData(AGE));
        ChainMath chain = new ChainMath(segs, len, r, a[0], a[1], radius);
        Frustum[] skin = new Frustum[segs];
        for (int k = 0; k < segs; k++) {
            skin[k] = Frustum.fromRings(n, chain.miterRing(k, false, n), chain.miterRing(k + 1, true, n));
        }
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getGeckolibData(YAW)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(TestWormModel.TEXTURE), (pose, buffer) -> {
            Matrix4f m = pose.pose();
            Matrix3f nm = pose.normal();
            Vector3f out = new Vector3f();
            for (Frustum f : skin) {
                for (int i = 0; i < f.n; i++) {
                    float[] q = f.quads[i];
                    nm.transform(f.normals[i][0], f.normals[i][1], f.normals[i][2], out);
                    for (int k = 0; k < 4; k++) {
                        Vector3f p = m.transformPosition(q[k * 5], q[k * 5 + 1], q[k * 5 + 2], new Vector3f());
                        buffer.addVertex(p.x, p.y, p.z, 0xFFFFFFFF, q[k * 5 + 3], q[k * 5 + 4],
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, light, out.x, out.y, out.z);
                    }
                }
            }
        });
        poseStack.popPose();
        if (!LOGGED_LOFT) {
            LOGGED_LOFT = true;
            WormsOfArrakis.LOGGER.info("[WormsOfArrakis] exp4 loft skin: submitCustomGeometry, {} segments x {} quads = {} quads per frame (rebuilt every frame)",
                    segs, n, segs * n);
        }
    }

    private static boolean LOGGED_LOFT;
}
