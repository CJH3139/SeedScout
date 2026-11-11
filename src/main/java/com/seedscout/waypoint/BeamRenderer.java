package com.seedscout.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seedscout.SeedScoutClient;
import com.seedscout.compat.Compat;
import com.seedscout.gui.StructureIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

public final class BeamRenderer {
    private static final RenderType LAYER = Compat.seeThroughLayer("seedscout_beam");

    private static final float HALF_WIDTH = 0.5f;
    private static final float MIN_Y = -64f;
    private static final float MAX_Y = 320f;

    private BeamRenderer() {}

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState) {
        if (!SeedScoutClient.config().showBeam) return;
        Waypoint waypoint = WaypointState.get().orElse(null);
        if (waypoint == null) return;
        if (!WaypointState.playerInDimension(Minecraft.getInstance(), waypoint)) return;
        if (poseStack == null || collector == null) return;

        Vec3 camera = levelState.cameraRenderState.pos;
        float cx = (float) (waypoint.x() + 0.5 - camera.x);
        float cz = (float) (waypoint.z() + 0.5 - camera.z);
        float y0 = (float) (MIN_Y - camera.y);
        float y1 = (float) (MAX_Y - camera.y);

        int color = WaypointState.colorOf(waypoint);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 110;

        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, LAYER, (pose, vc) -> {
            quad(vc, pose, cx - HALF_WIDTH, cz, cx + HALF_WIDTH, cz, y0, y1, r, g, b, a);
            quad(vc, pose, cx, cz - HALF_WIDTH, cx, cz + HALF_WIDTH, y0, y1, r, g, b, a);
        });
        poseStack.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1,
                             float y0, float y1, int r, int g, int b, int a) {
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(pose, x0, y1, z0).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y0, z1).setColor(r, g, b, a);
    }
}
