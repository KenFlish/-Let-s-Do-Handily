package ru.codex.stirandcharm.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Background checks use virtual input and must never capture the user's physical pointer. */
@Mixin(MouseHandler.class)
public abstract class BackgroundMouseMixin {
    @Inject(method="grabMouse",at=@At("HEAD"),cancellable=true)
    private void handily$leavePointerAlone(CallbackInfo ci){if(Boolean.getBoolean("handily.backgroundClient"))ci.cancel();}
}
