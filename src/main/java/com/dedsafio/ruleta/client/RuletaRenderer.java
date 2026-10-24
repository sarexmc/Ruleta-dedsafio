package com.dedsafio.ruleta.client;

import com.dedsafio.ruleta.RuletaMod;
import com.dedsafio.ruleta.Timeline;
import com.dedsafio.ruleta.block.RuletaAltarBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Dibujo de la secuencia: oscuridad, destello, disco que gira (geometria real), estrellas y anillo final. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT)
public final class RuletaRenderer {
    private RuletaRenderer() {}

    private static final int RINGS = 18;
    private static final int SUB = 10;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ClientRuletaState.ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Matrix4f m = ps.last().pose();
        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        Vector3f right = new Vector3f(left).negate();

        // 1) Oscuridad + destello de pantalla (maximo entre secuencias cercanas)
        float dark = 0f, whiteFlash = 0f;
        for (ClientRuletaState.Seq s : ClientRuletaState.ACTIVE) {
            float t = s.ticks + pt;
            double dx = cp.x - s.cx(), dz = cp.z - s.cz();
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            float prox = Timeline.clamp01(1f - (dist - 48f) / 48f);
            dark = Math.max(dark, Timeline.darkness(t) * prox);
            float fprox = Timeline.clamp01(1f - (dist - 24f) / 24f);
            whiteFlash = Math.max(whiteFlash, Timeline.flash(t) * fprox);
        }
        if (dark > 0.002f) {
            VertexConsumer vc = buf.getBuffer(RuletaRenderTypes.OVERLAY);
            screenQuad(vc, m, look, up, left, 0f, 0f, 0f, 0.965f * dark);
            buf.endBatch(RuletaRenderTypes.OVERLAY);
        }

        // 2) El altar sigue visible: se redibuja a brillo maximo sobre la oscuridad
        if (dark > 0.05f) {
            for (ClientRuletaState.Seq s : ClientRuletaState.ACTIVE) {
                BlockState st = level.getBlockState(s.pos);
                if (st.getBlock() instanceof RuletaAltarBlock) {
                    ps.pushPose();
                    ps.translate(s.pos.getX() - cp.x, s.pos.getY() - cp.y, s.pos.getZ() - cp.z);
                    mc.getBlockRenderer().renderSingleBlock(st, ps, buf, 0xF000F0, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                    buf.endBatch();
                }
            }
        }

        // 3) Cada ruleta
        for (ClientRuletaState.Seq s : ClientRuletaState.ACTIVE) {
            float t = s.ticks + pt;
            if (t > Timeline.TOTAL) continue;
            float ox = (float) (s.cx() - cp.x);
            float oy = (float) (s.groundY() - cp.y);
            float oz = (float) (s.cz() - cp.z);
            float gy = (float) (ClientRuletaState.glowY(level, s) - cp.y);
            float d = Timeline.darkness(t);

            // estrellas parpadeantes
            if (d > 0.4f) {
                VertexConsumer g = buf.getBuffer(RuletaRenderTypes.GLOW);
                for (int i = 0; i < ClientRuletaState.STAR_COUNT; i++) {
                    float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 0.25f + s.starPhase[i]));
                    float a = tw * d * 0.9f;
                    float[] c = starColor(s.starColor[i]);
                    float sx = ox + s.starDir[i * 3] * 70f;
                    float sy = gy + s.starDir[i * 3 + 1] * 70f;
                    float sz = oz + s.starDir[i * 3 + 2] * 70f;
                    billboard(g, m, sx, sy, sz, right, up, s.starSize[i], c[0], c[1], c[2], a);
                }
                buf.endBatch(RuletaRenderTypes.GLOW);
            }

            // disco: capa solida + capa aditiva (brillo)
            if (Timeline.maxRadiusFactor(t) > 0.002f) {
                VertexConsumer solid = buf.getBuffer(RuletaRenderTypes.DISC);
                disc(solid, m, s, t, ox, oy, oz, false);
                buf.endBatch(RuletaRenderTypes.DISC);
                VertexConsumer add = buf.getBuffer(RuletaRenderTypes.GLOW);
                disc(add, m, s, t, ox, oy + 0.03f, oz, true);
                pointer(add, m, t, ox, oy + 0.05f, oz);
                buf.endBatch(RuletaRenderTypes.GLOW);
            }

            // anillo arcoiris final
            float u = Timeline.ringProgress(t);
            if (u > 0f && u < 1.2f) {
                VertexConsumer add = buf.getBuffer(RuletaRenderTypes.GLOW);
                rainbowRing(add, m, ox, oy + 0.05f, oz, u, t, 1.0f);
                rainbowRing(add, m, ox, oy + 0.06f, oz, u - 0.16f, t, 0.8f);
                buf.endBatch(RuletaRenderTypes.GLOW);
            }

            // punto de energia central + destello
            float flash = Timeline.flash(t);
            float core = Timeline.core(t);
            float inten = Math.max(flash, core * 0.8f);
            if (inten > 0.01f) {
                VertexConsumer add = buf.getBuffer(RuletaRenderTypes.GLOW);
                float pulse = 1f + 0.15f * (float) Math.sin(t * 0.5f);
                float radius = 0.7f + 3.8f * flash + 0.7f * core * pulse;
                glow(add, m, ox, gy, oz, right, up, radius, inten);
                spikes(add, m, ox, gy, oz, right, up, radius * 1.9f, inten, t);
                buf.endBatch(RuletaRenderTypes.GLOW);
            }
        }

        // 4) Destello blanco de pantalla (entrada rapida, salida gradual)
        if (whiteFlash > 0.01f) {
            VertexConsumer vc = buf.getBuffer(RuletaRenderTypes.OVERLAY);
            screenQuad(vc, m, look, up, left, 1f, 1f, 1f, 0.55f * whiteFlash);
            buf.endBatch(RuletaRenderTypes.OVERLAY);
        }
    }

    /** Vibracion de camara en el destello. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (ClientRuletaState.ACTIVE.isEmpty()) return;
        Vec3 cp = e.getCamera().getPosition();
        float amp = 0f, tt = 0f;
        for (ClientRuletaState.Seq s : ClientRuletaState.ACTIVE) {
            float t = s.ticks + (float) e.getPartialTick();
            double dx = cp.x - s.cx(), dz = cp.z - s.cz();
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            float a = Timeline.shake(t) * Timeline.clamp01(1f - (dist - 16f) / 32f);
            if (a > amp) {
                amp = a;
                tt = t;
            }
        }
        if (amp <= 0f) return;
        float k = 1.3f * amp;
        e.setYaw(e.getYaw() + (float) Math.sin(tt * 2.9f + 2f) * k);
        e.setPitch(e.getPitch() + (float) Math.sin(tt * 2.3f + 1f) * k);
        e.setRoll(e.getRoll() + (float) Math.sin(tt * 1.7f) * k * 0.7f);
    }

    // ------------------------------------------------------------------ geometria

    private static void screenQuad(VertexConsumer vc, Matrix4f m, Vector3f look, Vector3f up, Vector3f left,
                                   float r, float g, float b, float a) {
        float d = 0.25f, S = 4f;
        float cx = look.x * d, cy = look.y * d, cz = look.z * d;
        v(vc, m, cx + left.x * S + up.x * S, cy + left.y * S + up.y * S, cz + left.z * S + up.z * S, r, g, b, a);
        v(vc, m, cx - left.x * S + up.x * S, cy - left.y * S + up.y * S, cz - left.z * S + up.z * S, r, g, b, a);
        v(vc, m, cx - left.x * S - up.x * S, cy - left.y * S - up.y * S, cz - left.z * S - up.z * S, r, g, b, a);
        v(vc, m, cx + left.x * S - up.x * S, cy + left.y * S - up.y * S, cz + left.z * S - up.z * S, r, g, b, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(clamp(r), clamp(g), clamp(b), clamp(a)).endVertex();
    }

    private static float clamp(float x) { return x < 0f ? 0f : (x > 1f ? 1f : x); }

    /** Disco completo: cada sector rota con el MISMO angulo 'rot' alrededor del centro del altar. */
    private static void disc(VertexConsumer vc, Matrix4f m, ClientRuletaState.Seq s, float t,
                             float ox, float oy, float oz, boolean glow) {
        float rot = Timeline.rotation(t, s.target);
        float S = Timeline.SECTOR_ANGLE;
        for (int i = 0; i < Timeline.SECTORS; i++) {
            float rf = Timeline.radiusFactor(i, t);
            float wf = Timeline.widthFactor(i, t);
            if (rf <= 0.002f || wf <= 0.002f) continue;
            float rMax = Timeline.R * rf;
            float half = (S / 2f - Timeline.GAP) * wf;
            float mid = i * S + S / 2f;
            float[] col = Timeline.COLORS[i];
            for (int k = 0; k < RINGS; k++) {
                float r0 = rMax * k / RINGS, r1 = rMax * (k + 1) / RINGS;
                for (int j = 0; j < SUB; j++) {
                    float a0 = mid - half + 2f * half * j / SUB;
                    float a1 = mid - half + 2f * half * (j + 1) / SUB;
                    dv(vc, m, ox, oy, oz, r0, a0, rot, i, col, t, rMax, mid, half, glow);
                    dv(vc, m, ox, oy, oz, r0, a1, rot, i, col, t, rMax, mid, half, glow);
                    dv(vc, m, ox, oy, oz, r1, a1, rot, i, col, t, rMax, mid, half, glow);
                    dv(vc, m, ox, oy, oz, r1, a0, rot, i, col, t, rMax, mid, half, glow);
                }
            }
        }
    }

    private static void dv(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float r, float a, float rot,
                           int i, float[] col, float t, float rMax, float mid, float half, boolean glow) {
        float wa = a + rot;
        float x = ox + r * (float) Math.cos(wa);
        float z = oz + r * (float) Math.sin(wa);
        float s1 = 0.5f + 0.5f * (float) Math.sin(r * 0.9f + a * 4f - t * 0.12f);
        float s2 = 0.5f + 0.5f * (float) Math.sin(r * 0.35f - a * 7f + t * 0.07f + i);
        float b = 0.30f + 0.60f * (0.6f * s1 + 0.4f * s2);
        float lat = Math.abs(a - mid) / Math.max(half, 0.0001f);
        b *= 1f - 0.6f * lat * lat * lat;
        float cg = Math.max(0f, 1f - r / (Timeline.R * 0.35f));
        float ef = r / Math.max(rMax, 0.0001f);
        float alpha = ef > 0.82f ? 1f - (ef - 0.82f) / 0.18f * 0.9f : 1f;
        float rr = col[0] * b + 0.45f * cg;
        float gg = col[1] * b + 0.45f * cg;
        float bb = col[2] * b + 0.45f * cg;
        if (glow) {
            v(vc, m, x, oy, z, rr, gg, bb, alpha * 0.35f);
        } else {
            v(vc, m, x, oy, z, rr, gg, bb, alpha * 0.93f);
        }
    }

    /** Puntero fijo (norte) que marca el sector ganador. */
    private static void pointer(VertexConsumer vc, Matrix4f m, float t, float ox, float oy, float oz) {
        float rf = Timeline.maxRadiusFactor(t);
        if (rf < 0.3f) return;
        float a = Timeline.clamp01((rf - 0.3f) * 3f);
        float rr = Timeline.R * rf;
        v(vc, m, ox, oy, oz - (rr - 0.8f), 1f, 1f, 1f, a);
        v(vc, m, ox - 0.6f, oy, oz - (rr + 0.6f), 1f, 1f, 1f, a * 0.6f);
        v(vc, m, ox, oy, oz - (rr + 0.2f), 1f, 1f, 1f, a * 0.8f);
        v(vc, m, ox + 0.6f, oy, oz - (rr + 0.6f), 1f, 1f, 1f, a * 0.6f);
    }

    private static void rainbowRing(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz, float u, float t, float strength) {
        if (u <= 0f || u >= 1.2f) return;
        float uu = Timeline.clamp01(u);
        float rad = Timeline.ringRadius(uu);
        float thick = 1.4f * (1f - uu * 0.6f);
        float fadeIn = Timeline.clamp01(uu / 0.1f);
        float alpha = (float) Math.pow(1f - uu, 1.2) * fadeIn * strength;
        if (alpha <= 0.002f) return;
        int N = 72;
        for (int j = 0; j < N; j++) {
            float a0 = Timeline.TWO_PI * j / N, a1 = Timeline.TWO_PI * (j + 1) / N;
            float[] c0 = hsv(((float) j / N + t * 0.01f) % 1f);
            float[] c1 = hsv(((float) (j + 1) / N + t * 0.01f) % 1f);
            ringStrip(vc, m, ox, oy, oz, rad - thick, 0f, rad, alpha, a0, a1, c0, c1);
            ringStrip(vc, m, ox, oy, oz, rad, alpha, rad + thick, 0f, a0, a1, c0, c1);
        }
    }

    private static void ringStrip(VertexConsumer vc, Matrix4f m, float ox, float oy, float oz,
                                  float rIn, float aIn, float rOut, float aOut, float a0, float a1, float[] c0, float[] c1) {
        v(vc, m, ox + rIn * (float) Math.cos(a0), oy, oz + rIn * (float) Math.sin(a0), c0[0], c0[1], c0[2], aIn);
        v(vc, m, ox + rOut * (float) Math.cos(a0), oy, oz + rOut * (float) Math.sin(a0), c0[0], c0[1], c0[2], aOut);
        v(vc, m, ox + rOut * (float) Math.cos(a1), oy, oz + rOut * (float) Math.sin(a1), c1[0], c1[1], c1[2], aOut);
        v(vc, m, ox + rIn * (float) Math.cos(a1), oy, oz + rIn * (float) Math.sin(a1), c1[0], c1[1], c1[2], aIn);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                                  float size, float r, float g, float b, float a) {
        v(vc, m, x + (-right.x - up.x) * size, y + (-right.y - up.y) * size, z + (-right.z - up.z) * size, r, g, b, a);
        v(vc, m, x + (right.x - up.x) * size, y + (right.y - up.y) * size, z + (right.z - up.z) * size, r, g, b, a);
        v(vc, m, x + (right.x + up.x) * size, y + (right.y + up.y) * size, z + (right.z + up.z) * size, r, g, b, a);
        v(vc, m, x + (-right.x + up.x) * size, y + (-right.y + up.y) * size, z + (-right.z + up.z) * size, r, g, b, a);
    }

    private static void glow(VertexConsumer vc, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                             float radius, float inten) {
        int N = 20;
        float mid = radius * 0.3f;
        for (int j = 0; j < N; j++) {
            float t0 = Timeline.TWO_PI * j / N, t1 = Timeline.TWO_PI * (j + 1) / N;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            float[] p0m = rp(right, up, c0, s0, mid), p1m = rp(right, up, c1, s1, mid);
            float[] p0o = rp(right, up, c0, s0, radius), p1o = rp(right, up, c1, s1, radius);
            v(vc, m, x, y, z, 1f, 1f, 1f, inten);
            v(vc, m, x + p0m[0], y + p0m[1], z + p0m[2], 1f, 1f, 1f, inten * 0.45f);
            v(vc, m, x + p1m[0], y + p1m[1], z + p1m[2], 1f, 1f, 1f, inten * 0.45f);
            v(vc, m, x, y, z, 1f, 1f, 1f, inten);
            v(vc, m, x + p0m[0], y + p0m[1], z + p0m[2], 0.9f, 0.95f, 1f, inten * 0.45f);
            v(vc, m, x + p0o[0], y + p0o[1], z + p0o[2], 0.8f, 0.9f, 1f, 0f);
            v(vc, m, x + p1o[0], y + p1o[1], z + p1o[2], 0.8f, 0.9f, 1f, 0f);
            v(vc, m, x + p1m[0], y + p1m[1], z + p1m[2], 0.9f, 0.95f, 1f, inten * 0.45f);
        }
    }

    private static float[] rp(Vector3f right, Vector3f up, float c, float s, float rad) {
        return new float[]{(right.x * c + up.x * s) * rad, (right.y * c + up.y * s) * rad, (right.z * c + up.z * s) * rad};
    }

    private static void spikes(VertexConsumer vc, Matrix4f m, float x, float y, float z, Vector3f right, Vector3f up,
                               float len, float inten, float t) {
        for (int k = 0; k < 4; k++) {
            float ang = t * 0.01f + k * (float) (Math.PI / 4.0);
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float dx = right.x * c + up.x * s, dy = right.y * c + up.y * s, dz = right.z * c + up.z * s;
            float px = -right.x * s + up.x * c, py = -right.y * s + up.y * c, pz = -right.z * s + up.z * c;
            float L = (k % 2 == 0) ? len : len * 0.6f;
            float w = L * 0.07f;
            float a = inten * 0.9f;
            v(vc, m, x + dx * L, y + dy * L, z + dz * L, 1f, 1f, 1f, 0f);
            v(vc, m, x + px * w, y + py * w, z + pz * w, 1f, 1f, 1f, a);
            v(vc, m, x - dx * L, y - dy * L, z - dz * L, 1f, 1f, 1f, 0f);
            v(vc, m, x - px * w, y - py * w, z - pz * w, 1f, 1f, 1f, a);
        }
    }

    public static float[] hsv(float h) {
        h = (h % 1f + 1f) % 1f;
        float x = h * 6f;
        int i = (int) x;
        float f = x - i;
        float q = 1f - f;
        switch (i % 6) {
            case 0: return new float[]{1f, f, 0f};
            case 1: return new float[]{q, 1f, 0f};
            case 2: return new float[]{0f, 1f, f};
            case 3: return new float[]{0f, q, 1f};
            case 4: return new float[]{f, 0f, 1f};
            default: return new float[]{1f, 0f, q};
        }
    }

    private static float[] starColor(int i) {
        switch (i) {
            case 0: return new float[]{1f, 1f, 1f};
            case 1: return new float[]{0.6f, 0.9f, 1f};
            case 2: return new float[]{1f, 0.6f, 0.9f};
            case 3: return new float[]{0.7f, 1f, 0.7f};
            case 4: return new float[]{1f, 0.9f, 0.5f};
            case 5: return new float[]{0.8f, 0.6f, 1f};
            default: return new float[]{1f, 1f, 1f};
        }
    }
}
