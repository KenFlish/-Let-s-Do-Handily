package ru.codex.stirandcharm.mixin;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ru.codex.stirandcharm.Settings;
@Mixin(CookingPotBlockEntity.class)
public abstract class CookingTime {
    @ModifyConstant(method="getMaxCookingTime",constant=@Constant(intValue=900),remap=false)
    private static int handily$displayTime(int original){return Settings.POT.get()?700:original;}
    @ModifyConstant(method="tick",constant=@Constant(intValue=900),remap=false)
    private int handily$cookingTime(int original){return Settings.POT.get()?700:original;}
}
