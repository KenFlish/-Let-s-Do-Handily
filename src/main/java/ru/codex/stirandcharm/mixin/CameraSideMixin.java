package ru.codex.stirandcharm.mixin;

import com.pockyl.diegesis.api.scene.CameraGlance;
import com.pockyl.diegesis.api.scene.CameraRig;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.focus.FocusController;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.client.HandilyCamera;

/**
 * Diegesis always starts the approach from the scene's front shot. For Handily stations the side is
 * chosen from the camera before the first frame, so the camera opens straight on the player's side,
 * also when moving from a neighbouring station. Other mods' stations are left exactly as they were.
 */
@Mixin(FocusController.class)
public abstract class CameraSideMixin {
    @Shadow private ClientStation station;
    @Shadow private Vector3f basePosition;
    @Shadow private Vector3f baseTarget;

    @Shadow public abstract CameraRig rig();

    @Inject(method = "snapBaseShot", at = @At("TAIL"))
    private void handily$chooseSide(CallbackInfo ci) {
        if (!HandilyCamera.handily(station)) return;
        HandilyCamera.choose(station.pos(), station.facing());
        CameraGlance shot = HandilyCamera.glance(rig(), HandilyCamera.side(station.pos(), station.facing()));
        if (shot != null) {
            basePosition.set(shot.position());
            baseTarget.set(shot.target());
        }
    }

    @Redirect(method = "frame", at = @At(value = "INVOKE",
        target = "Lcom/pockyl/diegesis/api/scene/CameraRig;position()Lorg/joml/Vector3fc;"))
    private Vector3fc handily$framePosition(CameraRig rig) {
        CameraGlance shot = handily$shot(rig);
        return shot != null ? shot.position() : rig.position();
    }

    @Redirect(method = "frame", at = @At(value = "INVOKE",
        target = "Lcom/pockyl/diegesis/api/scene/CameraRig;target()Lorg/joml/Vector3fc;"))
    private Vector3fc handily$frameTarget(CameraRig rig) {
        CameraGlance shot = handily$shot(rig);
        return shot != null ? shot.target() : rig.target();
    }

    private CameraGlance handily$shot(CameraRig rig) {
        if (!HandilyCamera.handily(station)) return null;
        return HandilyCamera.glance(rig, HandilyCamera.side(station.pos(), station.facing()));
    }
}