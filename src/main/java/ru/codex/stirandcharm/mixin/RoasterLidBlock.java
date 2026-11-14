package ru.codex.stirandcharm.mixin;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import net.satisfy.farm_and_charm.core.block.RoasterBlock;
import net.satisfy.foundation.util.ShapeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ru.codex.stirandcharm.RoasterLid;
@Mixin(RoasterBlock.class)
public abstract class RoasterLidBlock {
    @Inject(method="createBlockStateDefinition", at=@At("TAIL"), remap=false)
    private void handily$property(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) { builder.add(RoasterLid.OPEN); }
    @Inject(method="getShape", at=@At("HEAD"), cancellable=true, remap=false)
    private void handily$shape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> ci) {
        if (!state.getValue(RoasterLid.OPEN)) return;
        double y = state.getValue(RoasterBlock.HANGING) ? 2 : 0;
        VoxelShape open = Shapes.or(Block.box(2,y,3,14,y+6,13), Block.box(0,y+3,5,2,y+5,11),
            Block.box(14,y+3,5,16,y+5,11), Block.box(3,y+6,11.7,13,y+14,15.1));
        if (y > 0) open = Shapes.or(open, Block.box(1,7,7,1.2,14,9), Block.box(14.8,7,7,15,14,9), Block.box(1,14,7,15,14.2,9));
        ci.setReturnValue(ShapeUtil.rotateShape(Direction.NORTH, state.getValue(RoasterBlock.FACING), open));
    }
}
