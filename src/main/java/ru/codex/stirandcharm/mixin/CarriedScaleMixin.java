package ru.codex.stirandcharm.mixin;

import com.pockyl.diegesis.impl.client.focus.FocusController;
import com.pockyl.diegesis.impl.client.focus.FocusInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.codex.stirandcharm.StirAndCharm;

/** Use Diegesis's ordinary screen cursor icon instead of its world-space carried model. */
@Mixin(value=FocusInput.class,remap=false)
public abstract class CarriedScaleMixin {
    @Inject(method="carried",at=@At("RETURN"),cancellable=true)
    private void handily$stableCarrySize(CallbackInfoReturnable<FocusInput.Carried> cir) {
        var carried=cir.getReturnValue();
        var station=FocusController.get().station();
        if(carried==null || carried.preview()!=null || station==null
            || !station.type().id().getNamespace().equals(StirAndCharm.ID)) return;
        cir.setReturnValue(new FocusInput.Carried(carried.stack(),carried.stationPos(),carried.lean(),false,
            4.5f,carried.mouseX(),carried.mouseY(),carried.preview()));
    }
}
