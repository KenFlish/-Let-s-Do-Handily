package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;

/** Turns flat contents to the camera side without moving them or the block's own model. */
final class ContentFacing {
    static float yaw(StationView view) {
        return -HandilyCamera.side(view.pos(), view.facing()) * 90F;
    }

    static void turn(StationView view, NodeView node) {
        node.rotateTo(0.0F, yaw(view), 0.0F, 0.0F);
    }

    private ContentFacing() {}
}