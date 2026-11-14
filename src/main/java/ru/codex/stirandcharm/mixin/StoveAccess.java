package ru.codex.stirandcharm.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity;
@Mixin(StoveBlockEntity.class)
public interface StoveAccess {
    @Accessor("cookTime") int handily$time();
}
