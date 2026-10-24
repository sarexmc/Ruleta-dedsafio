package com.dedsafio.ruleta;

import java.util.Random;

/** Linea de tiempo compartida (servidor y cliente). Todo en ticks (20 = 1 s). */
public final class Timeline {
    private Timeline() {}

    public static final int SECTORS = 7;
    public static final float TWO_PI = (float) (Math.PI * 2.0);
    public static final float SECTOR_ANGLE = TWO_PI / SECTORS;
    public static final float GAP = 0.022f;
    public static final float R = 9.0f;
    public static final float POINTER_ANGLE = (float) (-Math.PI / 2.0);
    public static final int TURNS = 9;

    public static final int DARK_IN = 50;
    public static final int FLASH_START = 50;
    public static final int OPEN_START = 70;
    public static final int OPEN_DUR = 55;
    public static final int OPEN_STAGGER = 3;
    public static final int SPIN_START = 145;
    public static final int ACCEL = 60;
    public static final int CRUISE = 70;
    public static final int DECEL = 130;
    public static final int SPIN_END = SPIN_START + ACCEL + CRUISE + DECEL;
    public static final int CLOSE_START = SPIN_END + 40;
    public static final int CLOSE_DUR = 55;
    public static final int CORE_FADE_START = CLOSE_START + 75;
    public static final int CORE_FADE_DUR = 40;
    public static final int RING_START = CLOSE_START + 80;
    public static final int RING_DUR = 75;
    public static final int RESTORE_START = CLOSE_START + 95;
    public static final int RESTORE_DUR = 100;
    public static final int TOTAL = RESTORE_START + RESTORE_DUR;

    /** Orden horario visto desde arriba. */
    public static final float[][] COLORS = {
            {0.15f, 0.95f, 0.25f}, // verde
            {0.10f, 0.85f, 0.95f}, // cian
            {0.15f, 0.20f, 1.00f}, // azul
            {0.70f, 0.20f, 0.95f}, // morado
            {0.95f, 0.10f, 0.25f}, // rojo
            {1.00f, 0.55f, 0.10f}, // naranja
            {1.00f, 0.90f, 0.10f}  // amarillo
    };
    public static final String[] NAMES = {"Verde", "Cian", "Azul", "Morado", "Rojo", "Naranja", "Amarillo"};

    public static float clamp01(float x) { return x < 0f ? 0f : (x > 1f ? 1f : x); }
    public static float smooth(float x) { x = clamp01(x); return x * x * (3f - 2f * x); }
    public static float easeOutCubic(float x) { x = clamp01(x); float i = 1f - x; return 1f - i * i * i; }
    public static float easeInOut(float x) { return smooth(x); }

    public static float radiusFactor(int i, float t) {
        float op = clamp01((t - OPEN_START - i * OPEN_STAGGER) / OPEN_DUR);
        float cl = clamp01((t - CLOSE_START - (SECTORS - 1 - i) * OPEN_STAGGER) / (float) CLOSE_DUR);
        return easeOutCubic(op) * (1f - easeInOut(cl));
    }

    public static float widthFactor(int i, float t) {
        float op = clamp01((t - OPEN_START - i * OPEN_STAGGER) / (OPEN_DUR * 0.6f));
        float cl = clamp01((t - CLOSE_START - (SECTORS - 1 - i) * OPEN_STAGGER) / (float) CLOSE_DUR);
        cl = clamp01((cl - 0.45f) / 0.55f);
        return smooth(op) * (1f - smooth(cl));
    }

    public static float maxRadiusFactor(float t) {
        float m = 0f;
        for (int i = 0; i < SECTORS; i++) m = Math.max(m, radiusFactor(i, t));
        return m;
    }

    public static float darkness(float t) {
        if (t <= 0f) return 0f;
        if (t < DARK_IN) return smooth(t / DARK_IN);
        if (t < RESTORE_START) return 1f;
        return 1f - smooth((t - RESTORE_START) / RESTORE_DUR);
    }

    public static float flash(float t) {
        float u = t - FLASH_START;
        if (u < 0f) return 0f;
        if (u < 3f) return u / 3f;
        float f = 1f - (u - 3f) / 40f;
        return f <= 0f ? 0f : f * f;
    }

    public static float core(float t) {
        if (t < FLASH_START) return 0f;
        float in = smooth((t - FLASH_START) / 20f);
        float out = 1f - smooth((t - CORE_FADE_START) / CORE_FADE_DUR);
        return in * out;
    }

    public static float shake(float t) {
        float u = t - FLASH_START;
        if (u < 0f || u > 25f) return 0f;
        float k = 1f - u / 25f;
        return k * k;
    }

    public static float ringProgress(float t) { return (t - RING_START) / RING_DUR; }
    public static float ringRadius(float u) { return 0.8f + 11f * easeOutCubic(u); }

    public static int win(long seed) { return new Random(seed).nextInt(SECTORS); }

    /** Angulo total (rad) que recorre el disco: TURNS vueltas + ajuste para parar con el puntero en el sector ganador. */
    public static float targetAngle(long seed) {
        int w = win(seed);
        Random r = new Random(seed ^ 0x9E3779B9L);
        r.nextInt(SECTORS);
        float off = (r.nextFloat() - 0.5f) * 0.6f;
        float local = (w + 0.5f + off) * SECTOR_ANGLE;
        float base = POINTER_ANGLE - local;
        base = ((base % TWO_PI) + TWO_PI) % TWO_PI;
        return TURNS * TWO_PI + base;
    }

    /** Angulo real del disco en el instante t (continuo, aceleracion/desaceleracion suaves). */
    public static float rotation(float t, float target) {
        float s = t - SPIN_START;
        if (s <= 0f) return 0f;
        float wmax = target / (ACCEL / 2f + CRUISE + DECEL / 2f);
        if (s < ACCEL) {
            float u = s / ACCEL;
            return wmax * ACCEL * (u * u * u - u * u * u * u / 2f);
        }
        float a1 = wmax * ACCEL * 0.5f;
        s -= ACCEL;
        if (s < CRUISE) return a1 + wmax * s;
        float a2 = a1 + wmax * CRUISE;
        s -= CRUISE;
        if (s < DECEL) {
            float u = s / DECEL;
            return a2 + wmax * DECEL * (u - (u * u * u - u * u * u * u / 2f));
        }
        return target;
    }

    public static int sectorAtPointer(float rot) {
        float local = POINTER_ANGLE - rot;
        local = ((local % TWO_PI) + TWO_PI) % TWO_PI;
        return Math.min(SECTORS - 1, (int) (local / SECTOR_ANGLE));
    }
}
