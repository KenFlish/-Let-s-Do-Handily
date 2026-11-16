package ru.codex.stirandcharm.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Pseudo
@Mixin(targets="net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity",remap=false)
public interface JamAccess {
    @Accessor("cook") String handily$cook();
}
