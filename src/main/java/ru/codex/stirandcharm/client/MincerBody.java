package ru.codex.stirandcharm.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.satisfy.farm_and_charm.core.block.MincerBlock;
import net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity;
/** A native mincer copy aligned with the approaching player's scene and its crank hit box. */
public final class MincerBody implements NodePainter {
    public static boolean renderingScene;
    private final StationView view;
    MincerBody(StationView view) { this.view=view; }
    public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(view.level().getBlockEntity(view.pos()) instanceof MincerBlockEntity be)) return;
        var renderer=Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer==null) return;
        pose.pushPose(); pose.scale(16,16,16); pose.translate(.5,0,.5);
        float yaw=be.getBlockState().getValue(MincerBlock.FACING).toYRot()-180;
        pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5,0,-.5);
        renderingScene=true;
        try {
            renderer.render(be,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        } finally { renderingScene=false; pose.popPose(); }
    }
}
