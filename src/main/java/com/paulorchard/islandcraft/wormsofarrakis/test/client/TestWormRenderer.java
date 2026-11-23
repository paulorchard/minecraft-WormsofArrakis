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
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ScaleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.ShingleMath;
import com.paulorchard.islandcraft.wormsofarrakis.geometry.WormMath;
import com.paulorchard.islandcraft.wormsofarrakis.path.HeadSpec;
import com.paulorchard.islandcraft.wormsofarrakis.path.Moves;
import com.paulorchard.islandcraft.wormsofarrakis.path.SurfaceProvider;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormPose;
import com.paulorchard.islandcraft.wormsofarrakis.path.WormSkin;
import com.paulorchard.islandcraft.wormsofarrakis.test.MoveFactory;
import com.paulorchard.islandcraft.wormsofarrakis.test.TestFlags;
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
    static final DataTicket<Integer> ID = DataTicket.create("arrakis_id", Integer.class);

    private static final Map<String, Double> REACH = new HashMap<>();

    public TestWormRenderer(EntityRendererProvider.Context context) {
        super(context, new TestWormModel());
    }

    @Override
    public void addRenderData(TestWormEntity entity, Void relatedObject, EntityRenderState state, float partialTick) {
        state.addGeckolibData(SPEC, entity.getSpecString());
        state.addGeckolibData(YAW, entity.getYRot());
        state.addGeckolibData(AGE, entity.tickCount + (double) partialTick);
        state.addGeckolibData(ID, entity.getId());
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
        if (headValid) {
            // head assembly of a move: its rear end sits on joint 0, its axis along the chord to the path point headLength ahead
            info.poseStack().translate(0.0, headY, headZ);
            info.poseStack().mulPose(Axis.XP.rotation((float) headPhi));
            info.poseStack().translate(0.0, -headRing, HeadSpec.BODY_LENGTH);
        }
    }

    // ---- head-leading moves (prompt 03) ----

    private boolean headValid;
    private double headY, headZ, headPhi, headRing;

    private static final class MoveCache {
        String key;
        double s = Double.NaN;
        WormSkin skin;
        double headY, headZ, headPhi;
        int exposed;
        double[] heights;
    }

    private static final Map<Integer, MoveCache> MOVE_CACHE = new HashMap<>();
    private static final Map<Integer, Boolean> HOLD_LOGGED = new HashMap<>();
    private static final Map<Long, Double> COLUMN_CACHE = new HashMap<>();
    private static String LAST_MOVE_LOG = "";

    private static SurfaceProvider surfaceFor(EntityRenderState state) {
        if (!TestFlags.worldSurface) return SurfaceProvider.PLANE;
        double yawRad = Math.toRadians(state.getGeckolibData(YAW));
        double sx = state.x, sy = state.y, sz = state.z;
        return z -> {
            double wx = sx - Math.sin(yawRad) * z, wz = sz + Math.cos(yawRad) * z;
            long key = (((long) Math.floor(wx)) << 32) ^ (((long) Math.floor(wz)) & 0xFFFFFFFFL);
            Double h = COLUMN_CACHE.get(key);
            if (h == null) {
                net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
                h = level == null ? 0.0 : (double) level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(wx), (int) Math.floor(wz));
                if (COLUMN_CACHE.size() > 4096) COLUMN_CACHE.clear();
                COLUMN_CACHE.put(key, h);
            }
            return h - sy;
        };
    }

    /** Head-leading move: the body is drawn only for the segments above the sand (plus one neighbour each side). */
    private void submitMove(WormSpec spec, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Moves.Move m = MoveFactory.get(spec);
        double age = state.getGeckolibData(AGE);
        double[] at = m.at(MoveFactory.tau(spec, age));
        double s = at[0];
        int n = WormMath.faceCount(m.width);
        double linerMode = spec.p(16, 1);
        Integer id = state.getGeckolibData(ID);
        MoveCache c = MOVE_CACHE.get(id);
        String key = spec.serialize();
        boolean rebuild = c == null || c.s != s || !key.equals(c.key) || TestFlags.worldSurface;
        long t0 = System.nanoTime();
        if (rebuild) {
            c = new MoveCache();
            c.key = key;
            c.s = s;
            WormPose pose = m.pose(s);
            c.skin = WormSkin.build(pose, n, m.r0, surfaceFor(state), ShingleMath.Options.defaults(ScaleMath.LIP_LENGTH, linerMode > 0));
            double[] b = m.path.pos(s);
            double sf = WormPose.ahead(m.path, s, new double[]{0, b[1], b[0]}, HeadSpec.headLength(m.width));
            double[] f = m.path.pos(sf);
            c.headY = b[1];
            c.headZ = b[0];
            c.headPhi = Math.atan2(b[1] - f[1], -(b[0] - f[0]));   // elevation of the head-to-tail axis (tail = from the front point back to joint 0)
            c.exposed = pose.exposed(0);
            c.heights = m.headHeights(s);
            if (CACHE_LIMIT_CHECK(MOVE_CACHE)) MOVE_CACHE.clear();
            MOVE_CACHE.put(id, c);
            String sig = spec.mode() + n + "/" + m.segments;
            if (!sig.equals(LAST_MOVE_LOG)) {
                LAST_MOVE_LOG = sig;
                ShingleMath.Result x = c.skin.result;
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] path {}: width={} segments={} built segments {}..{} quads={} (liner/skin/lip/rims {}/{}/{}/{}) HEAD_LENGTH body={} total={}",
                        spec.mode(), m.width, m.segments, c.skin.from, c.skin.to, x.quads, x.byKind[0], x.byKind[1], x.byKind[2], x.byKind[3] + x.byKind[4],
                        HeadSpec.BODY_LENGTH, String.format("%.2f", HeadSpec.headLength(m.width)));
            }
        }
        long buildNs = System.nanoTime() - t0;
        ShingleStats.frame(rebuild, buildNs, c.skin.result, Math.max(0, c.skin.to - c.skin.from + 1), n);
        if (at[1] == 1) {
            if (HOLD_LOGGED.put(id, true) == null) {
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] path {} hold: exposed={} headBaseHeight={} headTopHeight={} (n={})", spec.mode(), c.exposed,
                        String.format("%.2f", c.heights[0]), String.format("%.2f", c.heights[1]), (int) spec.p(2, 0));
            }
        } else HOLD_LOGGED.remove(id);
        final ShingleMath.Result res = c.skin.result;
        final int linerColor = linerMode >= 1.5 ? ShingleMath.LINER_DARK : ShingleMath.LINER_TINT;
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getGeckolibData(YAW)));
        if (res.quads > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(TestWormModel.TEXTURE), (pose, buffer) -> {
                long s0 = System.nanoTime();
                Matrix4f mm = pose.pose();
                Matrix3f nm = pose.normal();
                Vector3f out = new Vector3f(), p = new Vector3f();
                float[] v = res.v;
                for (int q = 0; q < res.quads; q++) {
                    int b = q * 32;
                    int color = res.kind[q] == ShingleMath.LINER ? linerColor : ShingleMath.WHITE;
                    nm.transform(v[b + 5], v[b + 6], v[b + 7], out);
                    for (int k = 0; k < 4; k++) {
                        int atv = b + k * 8;
                        mm.transformPosition(v[atv], v[atv + 1], v[atv + 2], p);
                        buffer.addVertex(p.x, p.y, p.z, color, v[atv + 3], v[atv + 4],
                                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, light, out.x, out.y, out.z);
                    }
                }
                ShingleStats.submitted(System.nanoTime() - s0);
            });
        }
        poseStack.popPose();
        // the head: the GeckoLib mouth model, posed on joint 0 by adjustRenderPose
        headValid = true;
        headY = c.headY;
        headZ = c.headZ;
        headPhi = c.headPhi;
        headRing = m.r0;
        super.submit(state, poseStack, collector, camera);
        headValid = false;
    }

    private static boolean CACHE_LIMIT_CHECK(Map<?, ?> map) {
        return map.size() > 64;
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<EntityRenderState> info, BoneSnapshots bones) {
        WormSpec spec = WormSpec.parse(info.renderState().getGeckolibData(SPEC));
        if (spec.kind().equals("mouth") || spec.kind().equals("move")) {
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
        if (spec.kind().equals("move")) {
            submitMove(spec, state, poseStack, collector, camera);
            return;
        }
        if ((spec.kind().equals("chain") || spec.kind().equals("wave")) && spec.mode().equals("shingle")) {
            submitShingle(spec, state, poseStack, collector);
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

    private static final class Cached {
        String key;
        double[] yaw, pitch;
        ShingleMath.Result res;
    }

    private static final Map<Integer, Cached> CACHE = new HashMap<>();
    private static boolean LOGGED_SHINGLE;

    /**
     * Prompt 02 skin: liner + plates with telescoping lips + rims, built from the chain frames by ShingleMath and drawn
     * with submitCustomGeometry. The built geometry is reused when no bone pose changed since the last frame.
     */
    private void submitShingle(WormSpec spec, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int n = WormModels.faces(spec);
        double r = WormMath.circumradius(n, WormMath.FACE_WIDTH);
        int segs = (int) spec.p(1, 12);
        int per = Math.max(1, (int) spec.p(6, 1));
        double base = spec.p(7, ScaleMath.LIP_LENGTH);
        boolean liner = spec.p(8, 1) > 0;
        boolean liner2 = spec.p(8, 1) >= 1.5;   // liner dark: the old 0x404040 tint
        double[][] a = TestAnim.angles(spec, state.getGeckolibData(AGE));
        Integer id = state.getGeckolibData(ID);
        Cached c = CACHE.get(id);
        String key = spec.serialize();
        boolean rebuild = c == null || !key.equals(c.key) || !java.util.Arrays.equals(c.yaw, a[0]) || !java.util.Arrays.equals(c.pitch, a[1]);
        long t0 = System.nanoTime();
        if (rebuild) {
            double[] radius = new double[segs + 1];
            java.util.Arrays.fill(radius, r);
            ChainMath chain = new ChainMath(segs, per * ScaleMath.PITCH, r, a[0], a[1], radius);
            c = new Cached();
            c.key = key;
            c.yaw = a[0].clone();
            c.pitch = a[1].clone();
            c.res = ShingleMath.build(chain, n, r, per, ShingleMath.Options.defaults(base, liner));
            if (CACHE.size() > 64) CACHE.clear();
            CACHE.put(id, c);
            if (!LOGGED_SHINGLE || !key.equals(LAST_LOGGED)) {
                LOGGED_SHINGLE = true;
                LAST_LOGGED = key;
                ShingleMath.Result x = c.res;
                WormsOfArrakis.LOGGER.info("[WormsOfArrakis] skin shingle: segments={} perBone={} faces={} liner={} (colour {}, inset {} blocks) quads liner/skin/lip/rimSkin/rimLip={}/{}/{}/{}/{} maxGap={} minGap={} lipBase={} uncoveredVertices={} cutVertices={} intersectionsLeft={}",
                        segs, per, n, liner ? "on" : "off", Integer.toHexString(liner2 ? ShingleMath.LINER_DARK : ShingleMath.LINER_TINT), String.format("%.3f", x.linerInsetUsed),
                        x.byKind[0], x.byKind[1], x.byKind[2], x.byKind[3], x.byKind[4], String.format("%.3f", x.maxGap), String.format("%.3f", x.minGap),
                        base, x.uncovered, x.cutVertices, x.uncutIntersections);
            }
        }
        long buildNs = System.nanoTime() - t0;
        ShingleStats.frame(rebuild, buildNs, c.res, segs, n);
        final ShingleMath.Result res = c.res;
        final int linerColor = liner2 ? ShingleMath.LINER_DARK : ShingleMath.LINER_TINT;
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.getGeckolibData(YAW)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(TestWormModel.TEXTURE), (pose, buffer) -> {
            long s0 = System.nanoTime();
            Matrix4f m = pose.pose();
            Matrix3f nm = pose.normal();
            Vector3f out = new Vector3f(), p = new Vector3f();
            float[] v = res.v;
            for (int q = 0; q < res.quads; q++) {
                int b = q * 32;
                int color = res.kind[q] == ShingleMath.LINER ? linerColor : ShingleMath.WHITE;
                nm.transform(v[b + 5], v[b + 6], v[b + 7], out);
                for (int k = 0; k < 4; k++) {
                    int at = b + k * 8;
                    m.transformPosition(v[at], v[at + 1], v[at + 2], p);
                    buffer.addVertex(p.x, p.y, p.z, color, v[at + 3], v[at + 4],
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, light, out.x, out.y, out.z);
                }
            }
            ShingleStats.submitted(System.nanoTime() - s0);
        });
        poseStack.popPose();
    }

    private static String LAST_LOGGED = "";
}
