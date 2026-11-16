package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.codex.stirandcharm.Broth;
import ru.codex.stirandcharm.KitchenIngredients;

/** Spoon pose and soup drift adapted from Pocky's MIT PotBehavior, commit 292fb192.
 * Uses Farm & Charm's actual spoon axis and native inventory; changes visuals only. */
final class PotMotion {
    private static final float REACH=2.3F, ROOM=2.9F, RADIUS=1.05F;
    private final float[] places=new float[6], x=new float[6], z=new float[6], pushX=new float[6], pushZ=new float[6];
    private final Vector3f restEnd=new Vector3f(1.7071F,3.7071F,1);
    private float tipX,tipZ,grip,homing,swirl,sign=1,turned,time,spoonX,spoonZ;

    static float surface(StationView view) {
        return view.type().id().getPath().equals("candlelight_pot")?Broth.CANDLELIGHT_HEIGHT:Broth.FARM_HEIGHT;
    }
    static float[] point(StationView view) {
        var p=view.cursorOn(surface(view)).orElse(null);
        if(p==null)return null;
        float x=p.x-8,z=p.z-8,r=(float)Math.hypot(x,z);
        if(r>REACH){x*=REACH/r;z*=REACH/r;}
        return new float[]{x,z};
    }
    void frame(StationView view,float delta,boolean hand) {
        float dt=Math.clamp(delta,0,.1F);time+=dt;
        boolean stirred=view.state().getControl("stirring")>.5F;
        float wantX=0,wantZ=0;
        if(hand){var p=point(view);if(p!=null){wantX=p[0];wantZ=p[1];}else{wantX=tipX;wantZ=tipZ;}}
        else if(stirred){wantX=view.state().getControl("spoon_x");wantZ=view.state().getControl("spoon_z");}
        boolean home=!hand && !stirred;
        homing=home?Math.min(1,homing+dt/.6F):0;
        grip=Mth.lerp(Math.min(1,dt*(home?2*homing:7)),grip,home?0:1);
        float follow=1-(float)Math.exp(-dt*(hand?18:home?1.8F*homing:9));
        float dx=(wantX-tipX)*follow,dz=(wantZ-tipZ)*follow,across=tipX*dz-tipZ*dx;
        tipX+=dx;tipZ+=dz;
        float moved=(float)Math.hypot(dx,dz);
        if(grip>.5F && dt>0){
            if(moved>.02F && Math.abs(across)>.001F)sign=Mth.lerp(Math.min(1,dt*1.5F),sign,Math.signum(across));
            float wanted=Math.min(1,moved/dt/7);
            if(wanted>swirl)swirl=Mth.lerp(Math.min(1,dt*2.5F),swirl,wanted);
        }
        swirl*=(float)Math.pow(.5,dt/.9F);
        pose(view);
        drift(view,dt);
        if(moved>.005F && grip>.5F)view.node("broth").ifPresent(n -> n.leaveWake(8+spoonX,8+spoonZ));
    }
    private void pose(StationView view) {
        // Native spoon leans 45 degrees to the left, about its lower end, not the world Y axis.
        float held=(float)Math.tan(7*Mth.DEG_TO_RAD)/REACH;
        float leanX=Mth.lerp(grip,-1,tipX*held),leanZ=grip*tipZ*held;
        var turn=new Quaternionf().rotationTo(new Vector3f(-1,1,0).normalize(),new Vector3f(leanX,1,leanZ).normalize());
        var angles=turn.getEulerAnglesYXZ(new Vector3f()).mul(Mth.RAD_TO_DEG);
        var end=turn.transform(new Vector3f(restEnd));
        float targetX=Mth.lerp(grip,restEnd.x,tipX),targetZ=Mth.lerp(grip,restEnd.z,tipZ);
        view.node("spoon").ifPresent(n -> {
            n.moveTo(targetX-end.x,restEnd.y-end.y,targetZ-end.z,0);
            n.rotateTo(angles.x,angles.y,angles.z,0);
        });
        float height=surface(view)-(restEnd.y+(surface(view)>8?3:0));
        spoonX=targetX+leanX*height;spoonZ=targetZ+leanZ*height;
    }
    private void drift(StationView view,float dt) {
        float current=(.05F+swirl*1.5F)*sign;turned+=dt*current;
        var inputs=view.state().getItems("ingredients");int count=0;
        for(var stack:inputs)if(!stack.isEmpty() && !KitchenIngredients.liquid(stack))count++;
        int rank=0;
        for(int i=0;i<6;i++){
            if(i>=inputs.length || inputs[i].isEmpty() || KitchenIngredients.liquid(inputs[i]))continue;
            float wanted=rank++*Mth.TWO_PI/Math.max(count,1);
            places[i]=Mth.lerp(Math.min(1,dt*2.5F),places[i],wanted);
            float angle=turned+places[i];
            place(i,Mth.cos(angle)*2.2F,Mth.sin(angle)*2.2F,dt);
        }
        for(int pass=0;pass<2;pass++)for(int i=0;i<6;i++)for(int j=i+1;j<6;j++){
            if(inputs[i].isEmpty() || inputs[j].isEmpty() || KitchenIngredients.liquid(inputs[i]) || KitchenIngredients.liquid(inputs[j]))continue;
            float dx=x[j]-x[i],dz=z[j]-z[i],apart=(float)Math.hypot(dx,dz);
            if(apart<RADIUS*2){float nx=apart>.001F?dx/apart:Mth.cos(i+j),nz=apart>.001F?dz/apart:Mth.sin(i+j),move=(RADIUS*2-apart)/2;
                shift(i,-nx*move,-nz*move);shift(j,nx*move,nz*move);}
        }
        for(int i=0;i<6;i++){
            final int index=i;
            if(inputs[i].isEmpty() || KitchenIngredients.liquid(inputs[i]))continue;
            view.node("ingredient_"+i).ifPresent(n -> {
                var base=n.definition().transform().pos();
                float bob=Mth.sin(time*.9F+index*1.3F)*.07F;
                n.moveTo(8+x[index]-base.x(),surface(view)+.22F+bob-base.y(),8+z[index]-base.z(),0);
                n.rotateTo(Mth.sin(time*.5F+index)*3,ContentFacing.yaw(view)-(turned+places[index])*Mth.RAD_TO_DEG,Mth.cos(time*.4F+index)*3,0);
            });
        }
        float[] obstacles=new float[3+count*3];obstacles[0]=8+spoonX;obstacles[1]=8+spoonZ;obstacles[2]=1.05F;int at=3;
        for(int i=0;i<6;i++)if(!inputs[i].isEmpty() && !KitchenIngredients.liquid(inputs[i])){obstacles[at++]=8+x[i];obstacles[at++]=8+z[i];obstacles[at++]=RADIUS;}
        view.node("broth").ifPresent(n -> {n.setCurrent(current);n.setFloatingObstacles(obstacles);});
    }
    private void place(int i,float px,float pz,float dt){
        float settle=(float)Math.pow(.5,dt/.35F);pushX[i]*=settle;pushZ[i]*=settle;
        float nx=px+pushX[i],nz=pz+pushZ[i];
        for(int pass=0;pass<2;pass++){
            float dx=nx-spoonX,dz=nz-spoonZ,away=(float)Math.hypot(dx,dz);
            if(away<2.1F){if(away<.01F){dx=Mth.cos(i+1)*.01F;dz=Mth.sin(i+1)*.01F;away=.01F;}float out=2.1F/away;nx=spoonX+dx*out;nz=spoonZ+dz*out;}
            nx=Mth.clamp(nx,-ROOM,ROOM);nz=Mth.clamp(nz,-ROOM,ROOM);
        }
        pushX[i]=nx-px;pushZ[i]=nz-pz;x[i]=nx;z[i]=nz;
    }
    private void shift(int i,float dx,float dz){float nx=Mth.clamp(x[i]+dx,-ROOM,ROOM),nz=Mth.clamp(z[i]+dz,-ROOM,ROOM);pushX[i]+=nx-x[i];pushZ[i]+=nz-z[i];x[i]=nx;z[i]=nz;}
}
