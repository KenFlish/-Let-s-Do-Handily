package ru.codex.stirandcharm.client;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.impl.client.focus.FocusInput;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import ru.codex.stirandcharm.StirAndCharm;

/** Draw the actual server-synchronised carried stack above the scene, so no block can occlude it. */
@EventBusSubscriber(modid = StirAndCharm.ID, value = Dist.CLIENT)
public final class CarriedItemOverlay {
    @SubscribeEvent public static void render(ScreenEvent.Render.Post event) {
        if (DiegesisClient.focusedStation().filter(s -> s.isFocused() && s.type().id().getNamespace().equals(StirAndCharm.ID)).isEmpty()) return;
        var carried = FocusInput.get().carried();
        if (carried == null || !carried.inWorld() || carried.preview() != null) return;
        var graphics = event.getGuiGraphics();
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 400);
        graphics.renderItem(carried.stack(), event.getMouseX() - 8, event.getMouseY() - 8);
        graphics.renderItemDecorations(Minecraft.getInstance().font, carried.stack(), event.getMouseX() - 8, event.getMouseY() - 8);
        graphics.pose().popPose();
    }
}
