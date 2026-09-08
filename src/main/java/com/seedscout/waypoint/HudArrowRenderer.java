package com.seedscout.waypoint;

import com.seedscout.gui.StructureIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class HudArrowRenderer {
    private HudArrowRenderer() {}

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        Waypoint waypoint = WaypointState.get().orElse(null);
        if (waypoint == null) return;

        double px = client.player.getX();
        double pz = client.player.getZ();
        float yaw = client.player.getYaw(tickCounter.getTickProgress(true));
        float relative = Bearing.relativeDegrees(px, pz, yaw, waypoint.x() + 0.5, waypoint.z() + 0.5);
        int distance = (int) Math.round(Bearing.distance(px, pz, waypoint.x() + 0.5, waypoint.z() + 0.5));

        int centerX = context.getScaledWindowWidth() / 2;
        int arrowY = 18;
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(centerX, arrowY);
        matrices.rotate((float) Math.toRadians(relative));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, StructureIcons.ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
        matrices.popMatrix();

        String label = waypoint.name() + "  " + distance + " m";
        context.drawCenteredTextWithShadow(client.textRenderer, label, centerX, arrowY + 12, 0xFFFFFFFF);
    }
}
