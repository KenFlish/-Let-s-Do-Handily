package ru.codex.stirandcharm.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.RoasterBlock;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.RoasterLid;
@Mixin(RoasterBlockEntity.class)
public abstract class RoasterCooking {
    @Inject(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/RoasterBlockEntity;)V", at=@At("HEAD"), cancellable=true, remap=false)
    private void handily$closedOnly(Level level, BlockPos pos, BlockState state, RoasterBlockEntity be, CallbackInfo ci) {
        if (state.hasProperty(RoasterLid.OPEN) && state.getValue(RoasterLid.OPEN)) {
            if (!level.isClientSide && state.getValue(RoasterBlock.ROASTING)) level.setBlock(pos, state.setValue(RoasterBlock.ROASTING, false), 3);
            ci.cancel();
        }
    }
}
