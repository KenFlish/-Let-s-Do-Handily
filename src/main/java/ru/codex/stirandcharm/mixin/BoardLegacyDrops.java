package ru.codex.stirandcharm.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.codex.stirandcharm.BoardSelection;
/** Migration leftovers are real saved items, including when the old board is broken. */
@Mixin(CuttingBoardBlock.class)
public abstract class BoardLegacyDrops {
    @Inject(method="onRemove",at=@At("HEAD"),remap=false)
    private void handily$legacy(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving,CallbackInfo ci){
        if(!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof BoardSelection board){
            for(var item:board.handily$outputs())if(!item.isEmpty())Block.popResource(level,pos,item.copy());
            board.handily$outputs().clear();
        }
    }
}
