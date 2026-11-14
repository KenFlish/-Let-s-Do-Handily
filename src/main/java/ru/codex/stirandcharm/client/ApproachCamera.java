package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.StationView;

/**
 * Turns the station's interface nodes to the side the camera opens on. The camera itself is placed on
 * that side by {@link ru.codex.stirandcharm.mixin.CameraSideMixin} before the approach starts; the
 * block model is never turned by this, so the pot and the mincer keep their place in the world.
 */
final class ApproachCamera {
    static void open(StationView view) {
        if (!HandilyCamera.handily(view)) return;
        int turns = HandilyCamera.side(view.pos(), view.facing());
        view.node("approach_ui").ifPresent(n -> n.rotateTo(0, -turns * 90F, 0, 0));
    }

    private ApproachCamera() {}
}