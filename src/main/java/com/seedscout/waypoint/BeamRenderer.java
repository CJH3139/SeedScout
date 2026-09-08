package com.seedscout.waypoint;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.seedscout.SeedScoutClient;
import com.seedscout.gui.StructureIcons;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class BeamRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of(SeedScoutClient.MOD_ID, "pipeline/beam"))
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());
    private static final RenderLayer LAYER = RenderLayer.of("seedscout_beam",
            RenderSetup.builder(PIPELINE).translucent().build());

    private static final float HALF_WIDTH = 0.5f;
    private static final float MIN_Y = -64f;
    private static final float MAX_Y = 320f;

    private BeamRenderer() {}

    public static void init() {}

    public static void render(WorldRenderContext context) {
        if (!SeedScoutClient.config().showBeam) return;
        Waypoint waypoint = WaypointState.get().orElse(null);
        if (waypoint == null) return;
        MatrixStack matrices = context.matrices();
        if (matrices == null || context.consumers() == null) return;

        Vec3d camera = context.worldState().cameraRenderState.pos;
        float cx = (float) (waypoint.x() + 0.5 - camera.x);
        float cz = (float) (waypoint.z() + 0.5 - camera.z);
        float y0 = (float) (MIN_Y - camera.y);
        float y1 = (float) (MAX_Y - camera.y);

        int color = StructureIcons.colorFor(waypoint.structureId());
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 110;

        matrices.push();
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = context.consumers().getBuffer(LAYER);

        quad(vc, m, cx - HALF_WIDTH, cz, cx + HALF_WIDTH, cz, y0, y1, r, g, b, a);
        quad(vc, m, cx, cz - HALF_WIDTH, cx, cz + HALF_WIDTH, y0, y1, r, g, b, a);
        matrices.pop();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1,
                             float y0, float y1, int r, int g, int b, int a) {
        vc.vertex(m, x0, y0, z0).color(r, g, b, a);
        vc.vertex(m, x0, y1, z0).color(r, g, b, a);
        vc.vertex(m, x1, y1, z1).color(r, g, b, a);
        vc.vertex(m, x1, y0, z1).color(r, g, b, a);
    }
}
