package ru.codex.stirandcharm.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.PotStirring;
@Mixin(CookingPotBlockEntity.class)
public abstract class PotStirBoost implements PotStirring {
    @Shadow private int cookingTime;
    @Unique private long handily$lastStir = Long.MIN_VALUE;
    @Unique private int handily$beforeTick;
    @Unique private float handily$extra;
    public void handily$stirred(long tick) { handily$lastStir = tick; }
    @Inject(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/CookingPotBlockEntity;)V", at=@At("HEAD"), remap=false)
    private void handily$before(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity be, CallbackInfo ci) { handily$beforeTick = cookingTime; }
    @Inject(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/CookingPotBlockEntity;)V", at=@At("TAIL"), remap=false)
    private void handily$boost(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity be, CallbackInfo ci) {
        if (level.isClientSide || cookingTime != handily$beforeTick + 1 || handily$lastStir == Long.MIN_VALUE) return;
        long age = level.getGameTime() - handily$lastStir;
        if (age < 0 || age >= 16) { handily$extra = 0; return; }
        handily$extra += .5F * Math.min(1F, (16 - age) / 8F);
        if (handily$extra >= 1 && cookingTime < CookingPotBlockEntity.getMaxCookingTime() - 1) {
            cookingTime++; handily$extra--; be.setChanged();
        }
    }
}
