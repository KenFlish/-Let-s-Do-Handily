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
import net.satisfy.farm_and_charm.core.block.StoveBlock;

/** Splits the installed stove's facade into two native doors. No bundled models or textures. */
final class StoveModels implements NodePainter {
    private static final float[][] FRAME={{0,0,16,1},{0,1,4,6},{12,1,16,6},{0,6,16,8},{0,8,3,12},{13,8,16,12},{0,12,16,16}};
    private final StationView view;private final int part;
    private Object cachedModel;private List<BakedQuad> cached;
    StoveModels(StationView view,int part){this.view=view;this.part=part;}
    public void paint(PoseStack pose,MultiBufferSource buffers,int light){
        var renderer=Minecraft.getInstance().getBlockRenderer();
        var base=view.level().getBlockState(view.pos()).setValue(StoveBlock.FACING,Direction.NORTH);
        boolean lit=base.getValue(StoveBlock.LIT);
        var state=base.setValue(StoveBlock.LIT,part==0);var original=renderer.getBlockModel(state);
        if(cachedModel!=original){
            cachedModel=original;var all=new ArrayList<BakedQuad>();var random=RandomSource.create(42);
            all.addAll(original.getQuads(state,null,random,ModelData.EMPTY,null));
            for(var side:Direction.values())all.addAll(original.getQuads(state,side,random,ModelData.EMPTY,null));
            var out=new ArrayList<BakedQuad>();
            for(var q:all){
                if(front(q)){
                    if(part==0){for(var r:FRAME)out.add(crop(q,r[0],r[1],r[2],r[3]));}
                    else{var face=part==1?crop(q,3,8,13,12):crop(q,4,1,12,6);out.add(face);out.add(RoasterBody.reverse(face));}
                }else if(part==0)out.add(q);
            }
            cached=List.copyOf(out);
        }
        var quads=cached.stream().filter(q->lit || !q.getSprite().contents().name().getPath().startsWith("block/fire_")).toList();
        var model=new BakedModelWrapper<>(original){
            @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType layer){return side==null?quads:List.of();}
            @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random){return side==null?quads:List.of();}
        };
        pose.pushPose();if(part==1)pose.translate(-3,-8,0);else if(part==2)pose.translate(-4,-1,0);
        pose.scale(16,16,16);
        renderer.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),state,model,1,1,1,light,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,null);
        pose.popPose();
    }
    private static boolean front(BakedQuad q){
        if(q.getDirection()!=Direction.NORTH || !q.getSprite().contents().name().getPath().startsWith("block/stove_front"))return false;
        var v=q.getVertices();int stride=v.length/4;
        for(int i=0;i<4;i++)if(Math.abs(Float.intBitsToFloat(v[i*stride+2]))>.001F)return false;
        return true;
    }
    private static BakedQuad crop(BakedQuad q,float x0,float y0,float x1,float y1){
        int[] old=q.getVertices(),data=old.clone();int stride=data.length/4;
        float[] uv00=null,uv10=null,uv01=null;
        for(int i=0;i<4;i++){
            int j=i*stride;float x=Float.intBitsToFloat(old[j]),y=Float.intBitsToFloat(old[j+1]);
            float[] uv={Float.intBitsToFloat(old[j+4]),Float.intBitsToFloat(old[j+5])};
            if(x<.5F && y<.5F)uv00=uv;else if(x>.5F && y<.5F)uv10=uv;else if(x<.5F && y>.5F)uv01=uv;
        }
        if(uv00==null || uv10==null || uv01==null)return q;
        for(int i=0;i<4;i++){
            int j=i*stride;float x=(Float.intBitsToFloat(old[j])<.5F?x0:x1)/16F,y=(Float.intBitsToFloat(old[j+1])<.5F?y0:y1)/16F;
            data[j]=Float.floatToRawIntBits(x);data[j+1]=Float.floatToRawIntBits(y);
            data[j+4]=Float.floatToRawIntBits(uv00[0]+x*(uv10[0]-uv00[0])+y*(uv01[0]-uv00[0]));
            data[j+5]=Float.floatToRawIntBits(uv00[1]+x*(uv10[1]-uv00[1])+y*(uv01[1]-uv00[1]));
        }
        return new BakedQuad(data,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade());
    }
}
