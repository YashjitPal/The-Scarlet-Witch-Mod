package com.yashjit.scarlet.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yashjit.scarlet.client.magic.Hands;
import com.yashjit.scarlet.client.render.*;
import com.yashjit.scarlet.magic.Magic;
import com.yashjit.scarlet.network.MagicEventPayload;
import com.yashjit.scarlet.network.WitchEffect;
import com.yashjit.scarlet.network.WitchEffect.Kind;
import com.yashjit.scarlet.player.AgathaForm;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** World-space spell shapes snapped to the same screen-facing texel grid as Wanda's magic. */
public final class AgathaFx {
    private record Mark(WitchEffect event, long born, Vec3 palm, Pixels.Ramp source) {}
    private static final List<Mark> MARKS = new ArrayList<>();
    private record Ash(long until, float strength) {}
    private static final Map<Integer, Ash> ASH = new HashMap<>();
    private static ClientLevel world;
    private static final Pixels.Ramp V = Pixels.Ramp.AGATHA;
    private static final float TAU = 2 * Mth.PI;
    private static final Vector3f X = new Vector3f(1,0,0), Y = new Vector3f(0,1,0), Z = new Vector3f(0,0,1);
    private AgathaFx() {}

    private static void world(ClientLevel level) {
        if (world != level) { MARKS.clear(); ASH.clear(); world = level; }
    }

    public static void receive(WitchEffect e) {
        world(Minecraft.getInstance().level);
        if (world == null) return;
        if (world.getEntity(e.caster()) instanceof com.yashjit.scarlet.entity.WanderingWitch witch) {
            if (MARKS.size() >= 160) MARKS.removeFirst();
            MARKS.add(new Mark(e,world.getGameTime(),witch.getEyePosition(),Pixels.Ramp.ofCaster(witch)));
            return;
        }
        if (!(world.getEntity(e.caster()) instanceof Player caster)) return;
        long now = world.getGameTime();
        if (e.kind() == Kind.ASH) {
            ASH.put(e.target(), new Ash(now + e.ticks(), Math.min(1, ash(e.target()) + 0.31F)));
            return;
        }
        if (e.kind() == Kind.RELEASE) {
            MARKS.removeIf(m -> m.event.kind() == Kind.SIPHON && m.event.caster() == e.caster());
            return;
        }
        if (e.kind() == Kind.RIM) MARKS.removeIf(m -> m.event.kind() == Kind.RIM && m.event.caster() == e.caster());
        if (e.kind() == Kind.RUNE || e.kind() == Kind.SIPHON || e.kind() == Kind.CHAINS || e.kind() == Kind.RITUAL) {
            MARKS.removeIf(m -> m.event.kind() == e.kind() && m.event.caster() == e.caster() && (e.kind() != Kind.CHAINS || m.event.target() == e.target()));
        }
        Entity victim = world.getEntity(e.target());
        Pixels.Ramp source = victim instanceof Player || victim instanceof com.yashjit.scarlet.entity.WanderingWitch ? Pixels.Ramp.ofCaster(victim) : V;
        if (MARKS.size() >= 160) MARKS.removeFirst();
        MARKS.add(new Mark(e, now, Hands.raisedPalm(caster, Magic.castArm(caster, Magic.state(caster).castCount()), 1), source));
    }

    public static boolean shared(MagicEventPayload e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(e.entityId()) instanceof Player p) || !AgathaForm.worn(p)) return false;
        Kind kind = switch (e.kind()) {
            case MagicEventPayload.STAR, MagicEventPayload.BEAM -> Kind.BLAST;
            case MagicEventPayload.STAR_MISS, MagicEventPayload.BEAM_MISS -> Kind.MISS;
            case MagicEventPayload.WARD -> Kind.WARD;
            case MagicEventPayload.WEAVE, MagicEventPayload.TRANSMUTED, MagicEventPayload.UNWOVEN -> Kind.TRANSMUTE;
            case MagicEventPayload.PENTACLE -> Kind.RITUAL;
            case MagicEventPayload.MIRROR -> Kind.GLAMOUR;
            default -> null;
        };
        if (kind == null) return false;
        receive(new WitchEffect(e.entityId(), -1, kind, e.position(), kind == Kind.WARD ? 65 : kind == Kind.BLAST || kind == Kind.MISS ? 12 : 28));
        return true;
    }

    public static float ash(int id) {
        Ash a = ASH.get(id);
        if (world == null || world != Minecraft.getInstance().level || a == null) return 0;
        return a.strength * Math.clamp((a.until - world.getGameTime()) / 18F, 0, 1);
    }

    public static Player doubleOf(int id) {
        if (world == null || world != Minecraft.getInstance().level) return null;
        for (Mark m : MARKS) if (m.event.kind() == Kind.DOUBLE && m.event.target() == id && world.getEntity(m.event.caster()) instanceof Player p) return p;
        return null;
    }

    public static void submit(SubmitNodeCollector collector, PoseStack poses) {
        Minecraft mc = Minecraft.getInstance();
        world(mc.level);
        if (world == null) return;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = world.getGameTime() + partial;
        MARKS.removeIf(m -> now - m.born >= m.event.ticks());
        ASH.values().removeIf(a -> now >= a.until);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        for (Mark m : MARKS) {
            WitchEffect e = m.event;
            if (e.kind().ordinal() >= Kind.COVEN_GATHER.ordinal()) {
                Vec3 at = e.kind()==Kind.COVEN_GATHER ? m.palm : e.at();
                if (at.distanceToSqr(camera)<96*96) {
                    var end = at.subtract(camera).toVector3f();
                    var start = m.palm.subtract(camera).toVector3f();
                    float age = (float)(now-m.born);
                    GlowPass.submitPixels(collector,poses,(pose,buffer) -> {
                        PixelSprite sprite = PixelSprite.inWorld(pose,end.x,end.y,end.z);
                        coven(sprite,start,end,e,age,m.source);
                        sprite.draw(buffer);
                    });
                }
                continue;
            }
            Entity target = e.target() < 0 ? null : world.getEntity(e.target());
            if (!(world.getEntity(e.caster()) instanceof Player caster)) continue;
            if (e.kind() == Kind.RUNE && (!caster.isAlive() || !AgathaForm.worn(caster))) continue;
            if ((e.kind() == Kind.FAMILIAR || e.kind() == Kind.DOUBLE) && (target == null || !target.isAlive())) continue;
            boolean ray = e.kind() == Kind.BLAST || e.kind() == Kind.MISS;
            Vec3 at = !ray && target != null ? target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0) : e.at();
            if (camera.distanceToSqr(at) > 96 * 96) continue;
            Vec3 palm = ray ? m.palm : Hands.raisedPalm(caster, caster.getMainArm(), partial);
            Vector3f start = palm.subtract(camera).toVector3f(), end = at.subtract(camera).toVector3f();
            float age = (float)(now - m.born);
            float fade = Math.clamp((e.ticks() - age) / 5, 0, 1);
            float height = target == null ? 1.8F : target.getBbHeight();
            float width = target == null ? 0.6F : target.getBbWidth();
            if (e.kind() == Kind.RUNE) {
                Mark impact = null;
                for (Mark other : MARKS) if (other.event.kind() == Kind.RIM && other.event.caster() == e.caster() && other.born >= m.born) impact = other;
                int glyph = impact == null ? -1 : AgathaCircle.nearest(impact.event.at().subtract(at).toVector3f());
                float flash = impact == null ? 0 : Math.clamp((float)(1-(now-impact.born)/14), 0, 1);
                GlowPass.submitPixels(collector, poses, (pose,b) -> AgathaCircle.draw(pose,b,end,age,fade,glyph,flash));
                continue;
            }
            GlowPass.submitPixels(collector, poses, (pose, buffer) -> {
                PixelSprite s = PixelSprite.inWorld(pose, end.x, end.y, end.z);
                switch (e.kind()) {
                    case BLAST, MISS -> blast(s, start, end, age, fade, e.kind() == Kind.BLAST);
                    case SIPHON, DARK, THEFT -> siphon(s, start, end, age, fade, m.source, e.kind());
                    case CHAINS -> chains(s, end, width, height, age, fade);
                    case RITUAL -> rune(s, end, age, fade, 1.8F);
                    case RIM -> {
                        float r = 0.08F + 0.026F * age;
                        for (int i=0;i<6;i++) {
                            float a = i * TAU / 6;
                            s.line(around(end,s.right(),s.up(),r,r,a), around(end,s.right(),s.up(),r+0.13F,r+0.13F,a),
                                    ink(age < 3 ? 0 : 3,fade), 2, 1);
                        }
                    }
                    case NIGHTMARE -> nightmare(s, end, age, fade, height);
                    case CURSE -> curse(s, end, age, fade, height);
                    case DOUBLE, GLAMOUR -> mirror(s, end, age, fade, height);
                    case FAMILIAR -> familiar(s, end, age, fade, height);
                    case SIGHT -> sight(s, start, end, age, fade);
                    case TRANSMUTE -> transmute(s, end, age, fade);
                    case SEAL, WARD -> ward(s, end, age, fade, e.kind() == Kind.WARD);
                    default -> {}
                }
                s.draw(buffer);
            });
        }
    }

    private static int ink(int step, float alpha) { return Glow.withAlpha(V.color(step), alpha); }

    private static void coven(PixelSprite s, Vector3f from, Vector3f to,WitchEffect e,float age,Pixels.Ramp ramp) {
        float fade=Math.clamp((e.ticks()-age)/4,0,1);
        int light=Glow.withAlpha(ramp.color(1),fade), body=Glow.withAlpha(ramp.color(3),fade);
        if (e.kind()==Kind.COVEN_BEAM) {
            var axis=new Vector3f(to).sub(from);
            if (axis.lengthSquared()<0.01F) return;
            Vector3f[] plane=Glow.planeAxes(axis.normalize());
            s.line(from,to,body,1,2);
            for (int i=0;i<12;i++) {
                float t=(i+age*0.15F)%12/12;
                var bead=new Vector3f(from).lerp(to,t);
                loop(s,bead,plane[0],plane[1],0.09F,0.09F,4,t*8+age*0.22F,light,1);
            }
        } else if (e.kind()==Kind.COVEN_RUNE) {
            float r=Math.min(1,(age+1)/8)*2.2F;
            loop(s,to,X,Z,r,r,16,0,body,2);
            loop(s,to,X,Z,r*0.72F,r*0.72F,3,0,light,1);
            loop(s,to,X,Z,r*0.72F,r*0.72F,3,Mth.PI,body,1);
        } else {
            boolean burst=e.kind()==Kind.COVEN_BURST;
            float r=burst ? 0.1F+age*0.065F : 0.13F+(1-age/e.ticks())*0.36F;
            loop(s,to,s.right(),s.up(),r,r,burst?8:4,age*0.15F,body,1);
            for(int i=0;i<4;i++) {
                var dot=around(to,s.right(),s.up(),r,r,age*0.15F+i*TAU/4);
                s.plot(dot.x,dot.y,dot.z,light,3,2);
            }
        }
    }
    private static Vector3f offset(Vector3f p, float x, float y, float z) { return new Vector3f(p).add(x,y,z); }
    private static Vector3f around(Vector3f c, Vector3f u, Vector3f v, float rx, float ry, float a) {
        return new Vector3f(c).fma(rx*Mth.cos(a),u).fma(ry*Mth.sin(a),v);
    }

    private static void loop(PixelSprite s, Vector3f c, Vector3f u, Vector3f v, float rx, float ry,
                             int sides, float angle, int color, int weight) {
        Vector3f last = around(c,u,v,rx,ry,angle);
        for (int i=1;i<=sides;i++) {
            Vector3f next = around(c,u,v,rx,ry,angle+TAU*i/sides);
            s.line(last,next,color,1,weight);
            last=next;
        }
    }

    private static void blast(PixelSprite s, Vector3f from, Vector3f to, float age, float fade, boolean hit) {
        float distance = from.distance(to);
        if (distance < 0.02F) return;
        float flight = Math.clamp(distance/4, 2, 7), t = Math.min(1,age/flight);
        Vector3f head = new Vector3f(from).lerp(to,t);
        Vector3f[] axes = Glow.planeAxes(new Vector3f(to).sub(from).normalize());
        for (int strand=0;strand<3;strand++) {
            Vector3f last=head;
            for (int i=1;i<=12;i++) {
                float k=i/12F, along=Math.clamp((age-k*3)/flight,0,1);
                Vector3f middle=new Vector3f(from).lerp(to,along);
                Vector3f curl=around(middle,axes[0],axes[1],0.18F*k,0.18F*k,age*0.95F+k*6+strand*TAU/3);
                s.line(last,curl,ink(strand==0?1:strand==1?3:5,fade*(1-k*0.7F)),2,2);
                last=curl;
            }
        }
        loop(s,head,axes[0],axes[1],0.17F,0.17F,6,age*0.6F,ink(0,fade),2);
        float after=age-flight;
        if (hit && after>=0) {
            for (int i=0;i<8;i++) {
                float a=i*TAU/8+0.16F;
                float r=0.14F+after*0.095F;
                s.line(around(to,axes[0],axes[1],r,r,a),around(to,axes[0],axes[1],r+0.18F,r+0.18F,a),ink(i%2==0?1:3,fade),3,2);
            }
        }
    }

    private static void siphon(PixelSprite s, Vector3f palm, Vector3f victim, float age, float fade, Pixels.Ramp source, Kind kind) {
        float distance=palm.distance(victim);
        if (distance<0.05F) return;
        Vector3f[] axes=Glow.planeAxes(new Vector3f(palm).sub(victim).normalize());
        int steps=Math.clamp((int)(distance*4),12,72);
        for (int j=0;j<(kind==Kind.DARK?2:3);j++) {
            Vector3f previous=victim;
            for (int i=1;i<=steps;i++) {
                float t=i/(float)steps, a=t*8-age*0.22F+j*TAU/3;
                float radius=Mth.sin(t*Mth.PI)*(0.17F+0.12F*Mth.sin(age*0.11F+t*4))+0.035F;
                Vector3f center=new Vector3f(victim).lerp(palm,t);
                center.y+=Mth.sin(t*Mth.PI)*0.31F;
                Vector3f next=around(center,axes[0],axes[1],radius,radius,a);
                float bead=t*3-age*0.105F+j*0.23F;
                bead-=Math.floor(bead);
                int step=bead<0.14F?1:j==0?3:5;
                int col=t<0.29F?source.color(step):V.color(kind==Kind.DARK&&step==5?6:step);
                s.line(previous,next,Glow.withAlpha(col,fade),bead<0.14F?3:1,bead<0.14F?3:1);
                previous=next;
            }
        }
        loop(s,palm,axes[0],axes[1],0.18F,0.18F,6,-age*0.15F,ink(2,fade),1);
        if (kind == Kind.THEFT) {
            float travel = Math.clamp(age / 20, 0, 1);
            Vector3f seal = new Vector3f(victim).lerp(palm, travel);
            seal.y += 0.48F * Mth.sin(Mth.PI * travel);
            float radius = 0.28F * (1 - travel) + 0.12F;
            loop(s, seal, s.right(), s.up(), radius, radius, 4, age * 0.12F, ink(1, fade), 2);
            loop(s, seal, s.right(), s.up(), radius * 0.55F, radius * 0.55F, 3, -age * 0.12F, ink(3, fade), 1);
        }
    }

    private static void chains(PixelSprite s, Vector3f center, float width, float height, float age, float fade) {
        float radius=width*0.65F + 0.22F*Math.max(0,1-age/8);
        for (int i=0;i<23;i++) {
            float t=i/22F, angle=t*TAU*2.25F;
            Vector3f link=around(center,X,Z,radius,radius,angle);
            link.y+=(t-0.5F)*height*0.8F;
            Vector3f tangent=new Vector3f(-Mth.sin(angle),0.23F,Mth.cos(angle)).normalize();
            Vector3f side=i%2==0?Y:new Vector3f(Mth.cos(angle),0.5F,Mth.sin(angle)).normalize();
            loop(s,link,tangent,side,0.13F,0.077F,4,0,ink(i%2==0?2:4,fade),1);
            s.plot(link.x,link.y+0.075F,link.z,ink(1,fade),2);
        }
    }

    private static void rune(PixelSprite s, Vector3f c, float age, float fade, float radius) {
        float r=radius*Math.min(1,(age+1)/9);
        loop(s,c,X,Z,r,r,12,0,ink(3,fade),2);
        loop(s,c,X,Z,r*0.87F,r*0.87F,6,TAU/12,ink(4,fade),1);
        for (int i=0;i<6;i++) {
            float a=i*TAU/6;
            Vector3f tip=around(c,X,Z,r,r,a), foot=around(c,X,Z,r*0.65F,r*0.65F,a);
            s.line(foot,tip,ink(2,fade),1,2);
            s.line(tip,around(c,X,Z,r*0.89F,r*0.89F,a+0.085F),ink(1,fade),2,2);
            s.line(foot,around(c,X,Z,r*0.78F,r*0.78F,a-0.10F),ink(2,fade),2,2);
            Vector3f mote=around(c,X,Z,r,r,a+age*0.022F);
            s.plot(mote.x,mote.y+0.045F,mote.z,ink(0,fade),3,2);
        }
    }

    private static void eye(PixelSprite s, Vector3f c, float width, float blink, float fade) {
        loop(s,c,s.right(),s.up(),width,Math.max(0.02F,width*0.5F*blink),4,0,ink(2,fade),2);
        s.line(new Vector3f(c).fma(-width*0.24F*blink,s.up()),new Vector3f(c).fma(width*0.24F*blink,s.up()),ink(0,fade),3,2);
    }
    private static void nightmare(PixelSprite s, Vector3f c, float age, float fade, float height) {
        for (int i=0;i<3;i++) {
            float a=i*TAU/3+age*0.026F;
            Vector3f gaze=around(c,X,Z,0.9F,0.9F,a);
            gaze.y+=height*0.4F+0.2F*Mth.sin(a*3+age*0.09F);
            eye(s,gaze,0.3F,Math.min(1,Math.abs(Mth.sin(age*0.085F+i))*3),fade);
            s.line(gaze,offset(gaze,0,-0.17F,-0.05F),ink(5,fade),0,2);
        }
    }
    private static void curse(PixelSprite s, Vector3f c, float age, float fade, float h) {
        for(int vine=0;vine<2;vine++) {
            Vector3f last=offset(c,0,-h/2,0);
            for(int i=0;i<10;i++) {
                float t=i/9F,a=t*5+vine*Mth.PI-age*0.032F;
                Vector3f thorn=around(c,X,Z,0.4F,0.4F,a);
                thorn.y+=(t-0.48F)*h;
                s.line(last,thorn,ink(4,fade),1,2);
                if(i%2==0) s.line(thorn,offset(thorn,Mth.cos(a)*0.18F,0.17F,Mth.sin(a)*0.18F),ink(2,fade),2,1);
                last=thorn;
            }
        }
    }
    private static void mirror(PixelSprite s, Vector3f c, float age, float fade, float h) {
        float aperture=age<12 ? 1-age/12 : 0.13F;
        for(int i=0;i<7;i++) {
            float a=i*TAU/7+age*0.035F;
            Vector3f shard=around(c,X,Z,0.49F+aperture*0.4F,0.49F+aperture*0.4F,a);
            shard.y+=h*0.43F*Mth.sin(a*2);
            loop(s,shard,s.right(),Y,0.05F+aperture*0.08F,0.12F+aperture*0.18F,4,0,ink(2,fade*(0.35F+aperture*0.65F)),1);
        }
    }
    private static void familiar(PixelSprite s, Vector3f c, float age, float fade, float h) {
        if (age<18) rune(s,offset(c,0,-h/2,0),age,fade*(1-age/18),0.82F);
        for (int i=0;i<5;i++) {
            float t=(age*0.035F+i*0.2F)%1, a=t*3+i*1.7F;
            Vector3f w=around(c,X,Z,0.42F,0.42F,a);
            w.y+=t*h*0.85F;
            s.line(w,offset(w,0,0.11F,-0.04F),ink(t<0.4F?2:4,fade*(1-t)),2,2);
        }
    }
    private static void sight(PixelSprite s, Vector3f hand, Vector3f c, float age, float fade) {
        Vector3f axis=new Vector3f(c).sub(hand);
        if(axis.lengthSquared()<0.01F) return;
        axis.normalize();
        for(int i=0;i<4;i++) {
            float t=(age*0.09F+i*0.25F)%1;
            Vector3f b=new Vector3f(hand).fma(0.25F+t*1.9F,axis);
            s.plot(b.x,b.y,b.z,ink(2,fade*(1-t)),1,2);
        }
        eye(s,offset(c,0,0.55F,0),0.24F,1,fade);
    }
    private static void transmute(PixelSprite s, Vector3f c, float age, float fade) {
        float r=0.52F;
        for(int i=0;i<4;i++) {
            float x=(i%2==0?-r:r), z=(i<2?-r:r);
            s.line(offset(c,x,-r,z),offset(c,x,r,z),ink(3,fade),1,1);
        }
        for(int i=0;i<3;i++) {
            float h=((age*0.065F+i/3F)%1)*1.04F-r;
            loop(s,offset(c,0,h,0),X,Z,r*1.42F,r*1.42F,4,Mth.PI/4,ink(i==1?1:4,fade),1);
        }
    }
    private static void ward(PixelSprite s, Vector3f c,float age,float fade,boolean shell) {
        if (!shell) { loop(s,c,s.right(),s.up(),0.12F+age*0.025F,0.12F+age*0.025F,6,age*0.08F,ink(2,fade),2); return; }
        for(int i=0;i<4;i++) {
            float a=i*Mth.PI/4;
            Vector3f u=new Vector3f(Mth.cos(a),0,Mth.sin(a));
            Wisps.ring(s,c,u,Y,1.28F,0,0.5F,ink(i%2==0?2:4,fade*0.7F),1,1);
        }
        loop(s,c,X,Z,1.28F,1.28F,8,0,ink(3,fade),1);
    }
}
