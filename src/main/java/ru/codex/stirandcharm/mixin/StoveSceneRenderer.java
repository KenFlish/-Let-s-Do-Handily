package ru.codex.stirandcharm.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity;
import net.satisfy.farm_and_charm.client.renderer.block.StoveBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.Settings;

/** The scene owns the oven contents; the native top grill remains separate. */
@Mixin(StoveBlockRenderer.class)
public abstract class StoveSceneRenderer {
    @Inject(method="renderSlots",at=@At("HEAD"),cancellable=true,remap=false)
    private void handily$inputs(StoveBlockEntity stove,PoseStack pose,Vec3 base,Vec3 direction,Vec3 slots,MultiBufferSource buffers,CallbackInfo ci){
        if(Settings.STOVE.get() && DiegesisClient.station(stove.getBlockPos()).map(v->v.isInUse()).orElse(false))ci.cancel();
    }
    @Inject(method="renderOutput",at=@At("HEAD"),cancellable=true,remap=false)
    private void handily$output(StoveBlockEntity stove,PoseStack pose,Vec3 base,Vec3 direction,MultiBufferSource buffers,CallbackInfo ci){
        if(Settings.STOVE.get() && DiegesisClient.station(stove.getBlockPos()).map(v->v.isInUse()).orElse(false))ci.cancel();
    }
}
