package ru.codex.stirandcharm.mixin;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
@Mixin(RoasterBlockEntity.class)
public interface RoasterAccess {
    @Accessor("roastingTime") int handily$time();
    @Accessor("ownerUuid") void handily$owner(UUID owner);
}
