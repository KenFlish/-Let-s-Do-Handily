package ru.codex.stirandcharm.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.satisfy.farm_and_charm.client.renderer.block.MincerRenderer;
import net.satisfy.farm_and_charm.core.block.MincerBlock;
import net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.client.WorldIngredients;
/** The native mincer renderer stays on while its scene is open; only the food fits the open hopper. */
@Mixin(MincerRenderer.class)
public abstract class MincerFoodRenderer {
    @Inject(method = "render(Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("TAIL"), remap = false)
    private void handily$food(MincerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        pose.pushPose(); pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - be.getBlockState().getValue(MincerBlock.FACING).toYRot()));
        pose.translate(-.5,0,-.5);
        WorldIngredients.item(be, be.getItem(0), pose, buffers, light, overlay, .406F, .83F, .5F, .25F, 0);
        pose.popPose();
    }
}