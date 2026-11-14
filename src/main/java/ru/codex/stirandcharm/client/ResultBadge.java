package ru.codex.stirandcharm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;
import ru.codex.stirandcharm.*;

/** A camera-facing dish preview; drawn as an interface object, never as a dropped item or a shadow. */
final class ResultBadge implements NodePainter {
    private final boolean bowl;
    private final StationView view;
    private Appliance kind;
    private ApplianceCookbook applianceSource;
    private ItemStack shown = ItemStack.EMPTY;
    private boolean ready;
    ResultBadge(StationView view, boolean bowl) { this.view = view; this.bowl = bowl; refresh(); }
    ResultBadge(StationView view, Appliance kind, ApplianceCookbook source) {
        this.view = view; this.bowl = false; this.kind = kind; this.applianceSource = source; refresh();
    }
    void refresh() {
        var result = view.state().getItems("result")[0];
        ready = !result.isEmpty();
        shown = ready ? result.copy() : (applianceSource == null ? FarmCookbook.matching(view, bowl) : applianceSource.matching(view))
            .map(h -> KitchenRecipes.result(view.level(), h.value())).orElse(ItemStack.EMPTY);
        Component tooltip=shown.isEmpty() ? Component.translatable("handson_farm_charm.result") : shown.getHoverName();
        if (ready && view.state().data().getInt("unserved")>0) {
            var vessel=ResourceLocation.tryParse(view.state().data().getString("vessel"));
            if (vessel!=null) tooltip=Component.translatable("handson_farm_charm.serve_with",shown.getHoverName(),
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(vessel)).getHoverName());
        }
        final var text=tooltip;
        view.node("result").ifPresent(n -> n.setTooltip(text));
    }
    @Override public boolean smooth() { return true; }
    @Override public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
        if (shown.isEmpty() || !view.isFocused()) return;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.last().pose().mul(new Matrix4f(-.09F,0,0,0, 0,0,-.09F,0, 0,.09F,0,0, 0,0,0,1));
        int lit = LightTexture.FULL_BRIGHT;
        if (view.node("result").map(NodeView::isHovered).orElse(false)) {
            rect(pose, buffers, -15, -15, 30, .6F, 0xFFFFE8C0);
            rect(pose, buffers, -15, 14.4F, 30, .6F, 0xFFFFE8C0);
            rect(pose, buffers, -15, -15, .6F, 30, 0xFFFFE8C0);
            rect(pose, buffers, 14.4F, -15, .6F, 30, 0xFFFFE8C0);
        }
        pose.pushPose(); pose.scale(34, -34, 2);
        var renderer = mc.getItemRenderer();
        var model = renderer.getModel(shown, view.level(), null, 0);
        var gui = model.applyTransform(ItemDisplayContext.GUI, pose, false);
        renderer.render(shown, ItemDisplayContext.NONE, false, pose, FlatLit.of(buffers), lit, OverlayTexture.NO_OVERLAY, gui);
        pose.popPose();
        float y = 21;
        var lines = mc.font.split(shown.getHoverName(), 78);
        for (var line : lines.subList(0, Math.min(2, lines.size()))) {
            mc.font.drawInBatch(line, -mc.font.width(line) / 2F, y, 0xFFFFE8C0, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, lit);
            y += 10;
        }
        boolean batch=kind==Appliance.MINCER && !view.state().getItems("ingredients")[0].isEmpty();
        int percent = ready && !batch ? 100 : Math.round(view.state().getControl("progress") * 100);
        Component caption = ready && batch ? Component.translatable("handson_farm_charm.badge.batch",shown.getCount(),percent)
            : ready ? Component.translatable("handson_farm_charm.badge.ready", shown.getCount())
            : kind == Appliance.ROASTER && view.state().getControl("lid_open") > 0 ? Component.translatable("handson_farm_charm.close_lid")
            : Component.translatable(kind == Appliance.MINCER ? "handson_farm_charm.mincing" : bowl ? "handson_farm_charm.mixing" : view.state().getControl("heated") > 0
                ? "handson_farm_charm.cooking" : "handson_farm_charm.no_heat", percent);
        String label = caption.getString();
        mc.font.drawInBatch(label, -mc.font.width(label) / 2F, y + 1, ready ? 0xFFB7E79C : 0xFFF4D5A1, true,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, lit);
        rect(pose, buffers, -32, y + 13, 64, 2, 0x88352B1D);
        rect(pose, buffers, -32, y + 13, .64F * percent, 2, ready ? 0xFF9ACA77 : 0xFFE0B266);
        pose.popPose();
    }
    private static void rect(PoseStack pose, MultiBufferSource buffers, float x, float y, float w, float h, int color) {
        if (w <= 0) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        var out = buffers.getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
        var m = pose.last().pose();
        out.addVertex(m, x, y + h, .3F).setColor(color).setUv(sprite.getU0(), sprite.getV1()).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(m, x + w, y + h, .3F).setColor(color).setUv(sprite.getU1(), sprite.getV1()).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(m, x + w, y, .3F).setColor(color).setUv(sprite.getU1(), sprite.getV0()).setLight(LightTexture.FULL_BRIGHT);
        out.addVertex(m, x, y, .3F).setColor(color).setUv(sprite.getU0(), sprite.getV0()).setLight(LightTexture.FULL_BRIGHT);
    }
}
