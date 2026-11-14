package ru.codex.stirandcharm.mixin;

import java.util.UUID;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CookingPotBlockEntity.class)
public interface CookingPotAccess {
    @Accessor("cookingTime") int stirandcharm$cookingTime();
    @Accessor("ownerUuid") void stirandcharm$owner(UUID owner);
}
