package ru.codex.stirandcharm.client;
import net.minecraft.client.Minecraft;
/** Screen-relative circular motion, independent of block occlusion and the camera's angle. */
final class HandMotion {
    private double x, y;
    void grab(boolean stationCenter) {
        var mc = Minecraft.getInstance();
        x = stationCenter ? mc.getWindow().getGuiScaledWidth()/2.0 : mouseX(mc);
        y = stationCenter ? mc.getWindow().getGuiScaledHeight()/2.0 : mouseY(mc)+12;
    }
    float[] sample() {
        var mc = Minecraft.getInstance();
        float dx = (float)((mouseX(mc) - x) / 12), dz = (float)((mouseY(mc) - y) / 12);
        float radius = (float)Math.hypot(dx, dz);
        if (radius > 3) { dx *= 3 / radius; dz *= 3 / radius; }
        return new float[]{dx,dz};
    }
    private static double mouseX(Minecraft mc) { return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth(); }
    private static double mouseY(Minecraft mc) { return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight(); }
}
