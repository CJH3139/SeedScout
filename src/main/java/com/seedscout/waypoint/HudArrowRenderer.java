package com.seedscout.waypoint;

import com.seedscout.gui.StructureIcons;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class HudArrowRenderer {
    private HudArrowRenderer() {}

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        Waypoint waypoint = WaypointState.get().orElse(null);
        if (waypoint == null) return;

        double px = client.player.getX();
        double pz = client.player.getZ();
        float yaw = client.player.getViewYRot(tickCounter.getGameTimeDeltaPartialTick(true));
        float relative = Bearing.relativeDegrees(px, pz, yaw, waypoint.x() + 0.5, waypoint.z() + 0.5);
        int distance = (int) Math.round(Bearing.distance(px, pz, waypoint.x() + 0.5, waypoint.z() + 0.5));

        int centerX = context.guiWidth() / 2;
        int arrowY = 18;
        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(centerX, arrowY);
        matrices.rotate((float) Math.toRadians(relative));
        context.blit(RenderPipelines.GUI_TEXTURED, StructureIcons.ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
        matrices.popMatrix();

        String label = waypoint.name() + "  " + distance + " m";
        context.centeredText(client.font, label, centerX, arrowY + 12, 0xFFFFFFFF);
    }
}
