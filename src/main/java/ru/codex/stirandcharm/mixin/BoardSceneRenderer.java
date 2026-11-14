package ru.codex.stirandcharm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.satisfy.farm_and_charm.client.renderer.block.CuttingBoardRenderer;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CuttingBoardRenderer.class)
public abstract class BoardSceneRenderer {
    @Inject(method="render(Lnet/satisfy/farm_and_charm/core/block/entity/CuttingBoardBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void handily$scene(CuttingBoardBlockEntity board,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay,CallbackInfo ci) {
        if(DiegesisClient.station(board.getBlockPos()).map(s -> s.isInUse()).orElse(false)) ci.cancel();
    }
}
