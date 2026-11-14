package ru.codex.stirandcharm.mixin.candlelight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.satisfy.foundation.util.ShapeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.codex.stirandcharm.Settings;

/**
 * Candlelight's open pot still carries the closed lid in its hit box. The shape is the open pot:
 * walls, bottom and both handles, exactly what the scene draws.
 */
@Mixin(targets = "net.satisfy.candlelight.core.block.LargeCookingPotBlock")
public abstract class CandlelightPotShape {
    @Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void handily$openShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                   CallbackInfoReturnable<VoxelShape> ci) {
        boolean enabled;
        try {
            enabled = Settings.CANDLELIGHT.get();
        } catch (IllegalStateException beforeConfig) {
            // Shapes are also asked for while the blocks register, before the common config is loaded.
            return;
        }
        if (!enabled) return;
        VoxelShape open = Shapes.or(
            Block.box(3, 0, 3, 13, 12, 13),
            Block.box(1, 10, 5, 3, 12, 11),
            Block.box(13, 10, 5, 15, 12, 11));
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        ci.setReturnValue(ShapeUtil.rotateShape(Direction.NORTH, facing, open));
    }
}