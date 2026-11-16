package ru.codex.stirandcharm.mixin;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.render.SceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.codex.stirandcharm.StirAndCharm;
import ru.codex.stirandcharm.client.HandilyCamera;

/**
 * Diegesis turns a carried stack to the block's front. Handily's camera can stand on any side, so
 * the stack is turned to that side instead; other mods' stations keep Diegesis's own frame.
 */
@Mixin(SceneRenderer.class)
public abstract class CarriedFacingMixin {
    @ModifyConstant(method = {
        "lambda$renderCarried$20(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;ZF)V",
        "lambda$renderCarried$21(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;ZF)V"},
        constant = @Constant(floatValue = 180.0F), remap = false)
    private static float handily$faceCamera(float original) {
        var station = FocusController.get().station();
        if (station == null || !station.type().id().getNamespace().equals(StirAndCharm.ID)) return original;
        int side = HandilyCamera.side(station.pos(), station.facing());
        return original - side * 90.0F;
    }
}
