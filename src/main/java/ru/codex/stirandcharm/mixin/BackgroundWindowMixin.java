package ru.codex.stirandcharm.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Opt-in background verification: create the test window without showing or focusing it. */
@Mixin(Window.class)
public abstract class BackgroundWindowMixin {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwDefaultWindowHints()V"))
    private void handily$backgroundHints(){
        GLFW.glfwDefaultWindowHints();
        if(Boolean.getBoolean("handily.backgroundClient")){
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        }
    }
}
