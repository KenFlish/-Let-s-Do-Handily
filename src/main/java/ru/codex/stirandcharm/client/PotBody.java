package ru.codex.stirandcharm.client;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.satisfy.farm_and_charm.core.block.CookingPotBlock;

/** Draws the body or the native stirrer as separate moving parts of the installed model. */
final class PotBody implements NodePainter {
    private final StationView view;
    private final boolean spoon;
    PotBody(StationView view, boolean spoon) { this.view = view; this.spoon = spoon; }
    @Override public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState state = view.level().getBlockState(view.pos());
        boolean candlelight = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("candlelight");
        if (!candlelight && !state.hasProperty(CookingPotBlock.COOKING)) return;
        // Use the empty floor; Diegesis supplies the animated soup above it.
        if (candlelight) state=state.getBlock().defaultBlockState().setValue(CookingPotBlock.FACING,Direction.NORTH);
        else state = state.setValue(CookingPotBlock.COOKING, false).setValue(CookingPotBlock.FACING, Direction.NORTH)
            .setValue(CookingPotBlock.STAGE,CookingPotBlock.CookpotStage.NORMAL);
        if (candlelight && spoon) state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farm_and_charm:cooking_pot")).defaultBlockState();
        var renderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel installed = renderer.getBlockModel(state);
        BakedModel body = new BakedModelWrapper<>(installed) {
            @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random, ModelData data, RenderType layer) {
                return originalModel.getQuads(s, side, random, data, layer).stream().filter(q -> candlelight && !spoon ? belowRim(q) : fixedSpoon(q) == spoon).toList();
            }
            @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random) {
                return originalModel.getQuads(s, side, random).stream().filter(q -> candlelight && !spoon ? belowRim(q) : fixedSpoon(q) == spoon).toList();
            }
        };
        pose.pushPose();
        pose.translate(0, .025F, 0);
        if (spoon) pose.translate(-8, candlelight ? 3 : 0, -8);
        pose.scale(16, 16, 16);
        renderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, body, 1, 1, 1,
            light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }
    private static boolean belowRim(BakedQuad quad) {
        int[] v=quad.getVertices();int stride=v.length/4;
        for(int i=0;i<4;i++) if(Float.intBitsToFloat(v[i*stride+1])>.751F) return false;
        return true;
    }
    private static boolean fixedSpoon(BakedQuad quad) {
        if (!quad.getSprite().contents().name().getPath().equals("block/window_sill")) return false;
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) if (Float.intBitsToFloat(vertices[i * stride + 1]) > 0.51F) return true;
        return false;
    }
}
