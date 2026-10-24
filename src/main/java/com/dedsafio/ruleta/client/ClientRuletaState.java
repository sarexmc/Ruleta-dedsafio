package com.dedsafio.ruleta.client;

import com.dedsafio.ruleta.ModRegistry;
import com.dedsafio.ruleta.RuletaMod;
import com.dedsafio.ruleta.Timeline;
import com.dedsafio.ruleta.block.RuletaAltarBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Estado y logica de cliente: reloj de la secuencia, sonidos y particulas. */
@Mod.EventBusSubscriber(modid = RuletaMod.MODID, value = Dist.CLIENT)
public final class ClientRuletaState {
    private ClientRuletaState() {}

    public static final int STAR_COUNT = 150;

    public static final class Seq {
        public final ResourceLocation dim;
        public final BlockPos pos;
        public final long seed;
        public final float target;
        public int ticks;
        public int prev;
        public final float[] starDir = new float[STAR_COUNT * 3];
        public final float[] starSize = new float[STAR_COUNT];
        public final float[] starPhase = new float[STAR_COUNT];
        public final int[] starColor = new int[STAR_COUNT];

        Seq(ResourceLocation dim, BlockPos pos, int elapsed, long seed) {
            this.dim = dim;
            this.pos = pos;
            this.seed = seed;
            this.ticks = elapsed;
            this.prev = elapsed;
            this.target = Timeline.targetAngle(seed);
            Random r = new Random(seed * 31L + 7L);
            for (int i = 0; i < STAR_COUNT; i++) {
                float y = 0.12f + r.nextFloat() * 0.88f;
                float ang = r.nextFloat() * Timeline.TWO_PI;
                float h = (float) Math.sqrt(1f - y * y);
                starDir[i * 3] = h * (float) Math.cos(ang);
                starDir[i * 3 + 1] = y;
                starDir[i * 3 + 2] = h * (float) Math.sin(ang);
                starSize[i] = 0.5f + r.nextFloat() * 1.1f;
                starPhase[i] = r.nextFloat() * 20f;
                starColor[i] = r.nextInt(8);
            }
        }

        public float cx() { return pos.getX() + 0.5f; }
        public float cz() { return pos.getZ() + 0.5f; }
        public float groundY() { return pos.getY() + 0.03f; }
    }

    public static final List<Seq> ACTIVE = new ArrayList<>();

    public static void start(ResourceLocation dim, BlockPos pos, int elapsed, long seed) {
        ACTIVE.removeIf(s -> s.pos.equals(pos) && s.dim.equals(dim));
        ACTIVE.add(new Seq(dim, pos, elapsed, seed));
    }

    public static void stop(ResourceLocation dim, BlockPos pos) {
        for (Seq s : ACTIVE) {
            if (s.pos.equals(pos) && s.dim.equals(dim) && s.ticks < Timeline.CLOSE_START) {
                s.ticks = Timeline.CLOSE_START;
                s.prev = Timeline.CLOSE_START;
            }
        }
    }

    public static boolean isAltar(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RuletaAltarBlock;
    }

    public static float glowY(ClientLevel level, Seq s) {
        return s.pos.getY() + (isAltar(level, s.pos) ? 1.95f : 1.2f);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        if (mc.isPaused() || ACTIVE.isEmpty()) return;
        ResourceLocation cur = mc.level.dimension().location();
        Iterator<Seq> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Seq s = it.next();
            if (!s.dim.equals(cur)) {
                it.remove();
                continue;
            }
            s.prev = s.ticks;
            s.ticks++;
            if (s.ticks >= Timeline.TOTAL) {
                it.remove();
                continue;
            }
            cues(mc.level, s);
            particles(mc.level, s);
        }
    }

    private static boolean cross(Seq s, int t) {
        return s.prev < t && s.ticks >= t;
    }

    private static void snd(ClientLevel level, Seq s, SoundEvent ev, float vol, float pitch) {
        level.playLocalSound(s.cx(), s.pos.getY() + 1.0, s.cz(), ev, SoundSource.AMBIENT, vol, pitch, false);
    }

    private static void cues(ClientLevel level, Seq s) {
        if (cross(s, 1)) snd(level, s, SoundEvents.PORTAL_AMBIENT, 2.0f, 0.5f);
        if (cross(s, Timeline.FLASH_START)) {
            snd(level, s, SoundEvents.BEACON_ACTIVATE, 3.0f, 1.5f);
            snd(level, s, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0f, 1.7f);
            snd(level, s, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0f, 0.8f);
        }
        if (cross(s, Timeline.OPEN_START)) {
            snd(level, s, SoundEvents.PISTON_EXTEND, 3.0f, 0.6f);
            snd(level, s, SoundEvents.BEACON_POWER_SELECT, 3.0f, 0.7f);
        }
        if (cross(s, Timeline.SPIN_START)) snd(level, s, SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.0f);
        if (cross(s, Timeline.SPIN_END)) {
            snd(level, s, SoundEvents.PLAYER_LEVELUP, 3.0f, 1.0f);
            snd(level, s, SoundEvents.AMETHYST_BLOCK_CHIME, 3.0f, 0.8f);
        }
        if (cross(s, Timeline.CLOSE_START)) {
            snd(level, s, SoundEvents.PISTON_CONTRACT, 3.0f, 0.6f);
            snd(level, s, SoundEvents.BEACON_DEACTIVATE, 3.0f, 0.8f);
        }
        if (cross(s, Timeline.RING_START)) {
            snd(level, s, SoundEvents.BEACON_POWER_SELECT, 3.0f, 0.5f);
            snd(level, s, SoundEvents.AMETHYST_BLOCK_CHIME, 3.0f, 1.4f);
        }
        // "tic" cada vez que un sector pasa por el puntero (mas rapido = mas agudo)
        if (s.ticks > Timeline.SPIN_START && s.ticks < Timeline.SPIN_END + 10) {
            float r0 = Timeline.rotation(s.prev, s.target);
            float r1 = Timeline.rotation(s.ticks, s.target);
            if (Timeline.sectorAtPointer(r0) != Timeline.sectorAtPointer(r1) && r1 > r0) {
                float wmax = s.target / (Timeline.ACCEL / 2f + Timeline.CRUISE + Timeline.DECEL / 2f);
                float speed = Timeline.clamp01((r1 - r0) / wmax);
                snd(level, s, SoundEvents.STONE_BUTTON_CLICK_ON, 1.2f, 0.9f + 0.6f * speed);
            }
        }
    }

    private static void particles(ClientLevel level, Seq s) {
        RandomSource rnd = level.random;
        int t = s.ticks;
        double cx = s.cx(), cz = s.cz();
        double gy = glowY(level, s);
        double gnd = s.groundY() + 0.1;

        if (cross(s, Timeline.FLASH_START)) {
            for (int i = 0; i < 160; i++) {
                double dx = rnd.nextGaussian(), dy = rnd.nextGaussian(), dz = rnd.nextGaussian();
                double len = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
                double sp = 0.15 + rnd.nextDouble() * 0.55;
                level.addParticle(ParticleTypes.END_ROD, cx, gy, cz, dx / len * sp, dy / len * sp, dz / len * sp);
            }
        }
        if (t > Timeline.FLASH_START && t < Timeline.FLASH_START + 25) {
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double sp = 0.1 + rnd.nextDouble() * 0.25;
                level.addParticle(ParticleTypes.END_ROD, cx, gy, cz, Math.cos(a) * sp, (rnd.nextDouble() - 0.3) * 0.2, Math.sin(a) * sp);
            }
        }
        if (t >= Timeline.OPEN_START && t < Timeline.CLOSE_START + 60) {
            float maxR = Timeline.R * Timeline.maxRadiusFactor(t);
            if (maxR > 0.5f) {
                float rot = Timeline.rotation(t, s.target);
                int n = t < Timeline.SPIN_START ? 3 : 7;
                for (int i = 0; i < n; i++) {
                    float r = (float) Math.sqrt(rnd.nextFloat()) * maxR;
                    float ang = rnd.nextFloat() * Timeline.TWO_PI;
                    float local = ((ang - rot) % Timeline.TWO_PI + Timeline.TWO_PI) % Timeline.TWO_PI;
                    int sec = Math.min(Timeline.SECTORS - 1, (int) (local / Timeline.SECTOR_ANGLE));
                    float[] c = Timeline.COLORS[sec];
                    double x = cx + r * Math.cos(ang), z = cz + r * Math.sin(ang);
                    level.addParticle(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.3f),
                            x, gnd, z, 0, 0.03 + rnd.nextDouble() * 0.06, 0);
                    if (rnd.nextInt(4) == 0) {
                        level.addParticle(ParticleTypes.END_ROD, x, gnd + 0.2, z, 0, 0.02 + rnd.nextDouble() * 0.04, 0);
                    }
                }
            }
        }
        if (t >= Timeline.RING_START && t < Timeline.RING_START + Timeline.RING_DUR) {
            float u = (t - Timeline.RING_START) / (float) Timeline.RING_DUR;
            float rad = Timeline.ringRadius(u);
            for (int i = 0; i < 8; i++) {
                float a = rnd.nextFloat() * Timeline.TWO_PI;
                float[] c = RuletaRenderer.hsv(rnd.nextFloat());
                level.addParticle(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.2f),
                        cx + rad * Math.cos(a), gnd, cz + rad * Math.sin(a), 0, 0.03, 0);
            }
        }
    }
}
