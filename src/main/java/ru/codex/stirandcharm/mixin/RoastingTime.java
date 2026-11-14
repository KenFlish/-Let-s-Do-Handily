package ru.codex.stirandcharm.mixin;
import net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ru.codex.stirandcharm.Settings;
@Mixin(RoasterBlockEntity.class)
public abstract class RoastingTime {
    @ModifyConstant(method="getMaxRoastingTime",constant=@Constant(intValue=900),remap=false)
    private static int handily$displayTime(int original){return Settings.ROASTER.get()?700:original;}
    @ModifyConstant(method="tick",constant=@Constant(intValue=900),remap=false)
    private int handily$cookingTime(int original){return Settings.ROASTER.get()?700:original;}
}
