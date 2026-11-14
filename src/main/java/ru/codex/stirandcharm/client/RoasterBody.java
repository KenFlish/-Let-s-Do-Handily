package ru.codex.stirandcharm.client;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.satisfy.farm_and_charm.core.block.RoasterBlock;

/** Separates the installed roaster's own lid; the closed solid body becomes an open tray. */
final class RoasterBody implements NodePainter {
    private final StationView view;
    private final boolean lid;
    RoasterBody(StationView view, boolean lid) { this.view = view; this.lid = lid; }
    public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
        var state = view.level().getBlockState(view.pos()).setValue(RoasterBlock.FACING, Direction.NORTH)
            .setValue(ru.codex.stirandcharm.RoasterLid.OPEN, false);
        boolean hanging = state.getValue(RoasterBlock.HANGING);
        float lidY = hanging ? 8F : 6F;
        var renderer = Minecraft.getInstance().getBlockRenderer();
        var model = new BakedModelWrapper<>(renderer.getBlockModel(state)) {
            private List<BakedQuad> parts(List<BakedQuad> all) {
                var result = new ArrayList<BakedQuad>();
                for (var quad : all) {
                    String sprite = quad.getSprite().contents().name().getPath();
                    boolean isLid = sprite.equals("block/roaster_parts") && minY(quad) >= lidY / 16F - .001F;
                    if (isLid != lid) continue;
                    if (!lid && sprite.equals("block/roaster_bottom") && quad.getDirection() == Direction.UP) continue;
                    result.add(quad);
                    if (!lid && (sprite.equals("block/roaster_side") || sprite.equals("block/roaster_bottom")) || lid)
                        result.add(reverse(quad));
                }
                return result;
            }
            @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random, ModelData data, RenderType layer) {
                return parts(originalModel.getQuads(s, side, random, data, layer));
            }
            @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random) {
                return parts(originalModel.getQuads(s, side, random));
            }
        };
        pose.pushPose();
        pose.translate(0, .025F, 0);
        if (lid) pose.translate(-8, -lidY, -12);
        pose.scale(16,16,16);
        renderer.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, model,
            1,1,1, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }
    static float minY(BakedQuad quad) {
        int[] v = quad.getVertices(); int stride = v.length / 4; float y = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) y = Math.min(y, Float.intBitsToFloat(v[i * stride + 1]));
        return y;
    }
    static BakedQuad reverse(BakedQuad quad) {
        int[] source = quad.getVertices(), copy = source.clone(); int stride = copy.length / 4;
        for (int i = 0; i < 4; i++) System.arraycopy(source, (3-i)*stride, copy, i*stride, stride);
        var normal = quad.getDirection().getOpposite().getNormal();
        int packed = (normal.getX()*127 & 255) | (normal.getY()*127 & 255) << 8 | (normal.getZ()*127 & 255) << 16;
        for (int i = 0; i < 4; i++) copy[i*stride + 7] = packed;
        return new BakedQuad(copy, quad.getTintIndex(), quad.getDirection().getOpposite(), quad.getSprite(), quad.isShade());
    }
}
