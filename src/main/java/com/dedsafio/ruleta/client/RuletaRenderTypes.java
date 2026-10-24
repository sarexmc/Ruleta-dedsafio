package com.dedsafio.ruleta.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Tipos de render propios (POSITION_COLOR, sin niebla ni luz del mundo: por eso siguen brillando en la oscuridad). */
public final class RuletaRenderTypes extends RenderType {
    private RuletaRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean a, boolean s, Runnable st, Runnable cl) {
        super(n, f, m, b, a, s, st, cl);
        throw new IllegalStateException("no instanciable");
    }

    /** Pantalla completa, sin test de profundidad (oscuridad y destello). */
    public static final RenderType OVERLAY = create("ruleta_overlay",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Geometria del disco: translucido, con profundidad. */
    public static final RenderType DISC = create("ruleta_disc",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Brillo aditivo (glow, estrellas, anillo). */
    public static final RenderType GLOW = create("ruleta_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
}
