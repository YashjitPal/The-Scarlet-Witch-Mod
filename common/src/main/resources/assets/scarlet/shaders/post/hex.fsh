#version 330
#extension GL_ARB_separate_shader_objects : require

// The Hex, drawn over the finished world.
// From outside only its wall shows, with everything inside looking as it really is; inside one, the whole view is in
// its era.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HandDepthSampler;

layout(std140) uniform HexView {
    // clip space to camera-relative world space
    mat4 InvProjView;
    // xyz: center relative to the camera, w: radius, out to the middle of each wall
    vec4 Shapes[4];
    // x: era, y: how brightly the front burns, z: warning flicker, w: the static of an era change
    vec4 Styles[4];
    // x: 1 + index of the Hex around the camera (0 for none), y: how far into it the view has turned, z: its era, w: count
    vec4 Camera;
    // x: seconds, yz: screen size, w: old-TV framing
    vec4 Params;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);
// blocks across a cell of the wall's honeycomb
const float CELL = 1.4;
// The Hex's shape, as HexShape has it: six walls standing straight up without end. Opposite walls face the same way,
// so three directions cover all six.
const int WALLS = 6;
const vec2 FACES[3] = vec2[3](vec2(0.0, 1.0), vec2(0.8660254, 0.5), vec2(0.8660254, -0.5));

// Where the eye is, relative to the camera. View bobbing moves it a little off the camera with every step, so it is
// found from the same matrix the world was drawn with: the one point every ray of the screen starts from.
vec3 eyePosition() {
    vec4 eye = InvProjView * vec4(0.0, 0.0, 1.0, 0.0);
    return eye.xyz / eye.w;
}

// The direction the eye looks through a point of the screen.
vec3 ray(vec2 uv, vec3 eye) {
    vec4 point = InvProjView * vec4(uv * 2.0 - 1.0, 0.5, 1.0);
    return normalize(point.xyz / point.w - eye);
}

// Which way wall j faces, out across the ground.
vec2 faceDirection(int j) {
    return (j < 3 ? 1.0 : -1.0) * FACES[j % 3];
}

// Where a ray from origin along dir is inside a Hex, as distances along it: x going in, y coming out (x >= y when it
// misses), and the walls it goes in and comes out through, z and w. The ray is inside where it is inside all six.
vec4 traceHex(vec3 origin, vec3 dir, vec3 center, float radius) {
    vec2 from = (origin - center).xz;
    vec4 span = vec4(-1.0e9, 1.0e9, 0.0, 0.0);
    for (int j = 0; j < WALLS; j++) {
        vec2 f = faceDirection(j);
        float toward = dot(f, dir.xz);
        float room = radius - dot(f, from);
        if (abs(toward) < 1.0e-9) {
            // running along this wall: inside it all the way, or never
            if (room < 0.0) {
                return vec4(1.0, 0.0, 0.0, 0.0);
            }
            continue;
        }
        float t = room / toward;
        if (toward < 0.0) {
            if (t > span.x) {
                span.x = t;
                span.z = float(j);
            }
        } else if (t < span.y) {
            span.y = t;
            span.w = float(j);
        }
    }
    return span;
}

// How far out a point is, as a share of the way from the center to the wall it faces: under 1 inside.
float shapeLevel(vec3 local, float radius) {
    return max(abs(local.z), max(abs(dot(local.xz, FACES[1])), abs(dot(local.xz, FACES[2])))) / radius;
}

// How much of a wall shows at a height over the cast point: all of it up to rise, thinning out over fall until it is
// gone into the sky.
float wallFade(float height, float rise, float fall) {
    return 1.0 - smoothstep(rise, rise + fall, height);
}

// From outside, a Hex stands tall over everything, the bigger the taller.
float outsideFade(float height, float radius) {
    return wallFade(height, radius + 16.0, radius * 1.5 + 32.0);
}

// From inside, the walls are gone well before they would meet overhead in the distance, so the sky above stays open.
float insideFade(float height, float radius) {
    return wallFade(height, radius * 0.3 + 4.0, radius + 8.0);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 desaturate(vec3 color, float saturation) {
    return mix(vec3(dot(color, LUMA)), color, saturation);
}

float vignette(vec2 uv, float strength) {
    vec2 d = (uv - 0.5) * vec2(1.15, 1.0);
    return 1.0 - strength * smoothstep(0.2, 0.8, length(d));
}

vec3 eraLook(vec3 color, int era, vec2 uv, vec2 screen, float time) {
    vec2 pixel = uv * screen;
    float grain = hash12(floor(pixel) + floor(time * 24.0) * vec2(37.0, 17.0)) - 0.5;
    if (era == 0) {
        // 1950s: soft black and white, film grain, a gentle vignette
        float l = dot(color, LUMA);
        l = mix(l, smoothstep(0.0, 1.0, l), 0.25) * 0.92 + 0.04;
        return (vec3(l) * vec3(1.02, 1.0, 0.95) + grain * 0.09) * vignette(uv, 0.45);
    }
    if (era == 1) {
        // 1960s: crisp, high-contrast black and white
        float l = clamp((dot(color, LUMA) - 0.5) * 1.4 + 0.5, 0.0, 1.0);
        return (vec3(l) + grain * 0.045) * vignette(uv, 0.25);
    }
    if (era == 2) {
        // 1970s: warm, saturated film
        vec3 warm = desaturate(color * vec3(1.08, 1.0, 0.84), 1.3);
        return (warm * 0.9 + vec3(0.06, 0.04, 0.02) + grain * 0.05) * vignette(uv, 0.3);
    }
    if (era == 3) {
        // 1980s: bright color on VHS, with bleeding chroma, scanlines and a rolling tracking band
        float band = smoothstep(0.97, 1.0, fract(uv.y * 0.7 - time * 0.12));
        vec2 wobble = vec2(band * 5.0 * sin(time * 43.0 + uv.y * 310.0) / screen.x, 0.0);
        float shift = 1.6 / screen.x;
        vec3 tape = vec3(
                texture(InSampler, uv + wobble + vec2(shift, 0.0)).r,
                texture(InSampler, uv + wobble).g,
                texture(InSampler, uv + wobble - vec2(shift, 0.0)).b);
        tape = desaturate(tape, 1.35) * 1.06;
        tape *= 0.93 + 0.07 * sin(pixel.y * 3.14159);
        tape += band * 0.22 * (hash12(pixel + floor(time * 30.0)) - 0.35);
        return tape + grain * 0.06;
    }
    if (era == 4) {
        // 2000s: the flat look of a sitcom camera
        vec3 flattened = (color - 0.5) * 0.9 + 0.5;
        return desaturate(flattened, 0.9) * vec3(0.98, 1.0, 1.03);
    }
    return color;
}

// A honeycomb of unit cells: x is the distance to the nearest border, yz the cell.
vec3 honeycomb(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 centers = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 offsets = vec4(p - centers.xy * s, p - (centers.zw + 0.5) * s);
    vec4 cell = dot(offsets.xy, offsets.xy) < dot(offsets.zw, offsets.zw)
            ? vec4(offsets.xy, centers.xy)
            : vec4(offsets.zw, centers.zw + 0.5);
    vec2 a = abs(cell.xy);
    return vec3(0.5 - max(dot(a, s * 0.5), a.x), cell.zw);
}

// The lines of the honeycomb and how brightly its cell is lit, at one point of a wall. Lines are drawn at least as wide
// as a pixel there, so they never shimmer apart.
vec2 honeycombAt(vec2 p, float footprint, float time, float lit) {
    vec3 h = honeycomb(p);
    float width = footprint * 1.5 + 0.02;
    float line = (1.0 - smoothstep(0.0, width, h.x)) * clamp(0.06 / width, 0.0, 1.0);
    float seed = hash12(h.yz * 1.37);
    float pulse = hash12(h.yz + floor(time * 2.5 + seed * 4.0) * 0.61);
    float glow = smoothstep(1.0 - 0.12 - lit * 0.5, 1.0, pulse) * (0.55 + 0.45 * sin(time * 9.0 + seed * 6.283));
    return vec2(line, glow * smoothstep(0.0, 0.25, h.x));
}

// The wall: mostly clear, a faint honeycomb with cells lighting up, TV static sparkling over it, bright where it is
// seen edge-on and up the corners where one wall meets the next, with slow bands rolling down it.
vec4 wallLayer(vec3 hit, float travel, vec3 center, float radius, int face, vec3 dir, float pixelAngle, float time, vec4 style,
               float strength) {
    vec3 local = hit - center;
    vec2 f = faceDirection(face);
    float facing = abs(dot(dir.xz, f));
    float rim = pow(1.0 - facing, 3.0);
    float flare = style.y;
    float footprint = travel * pixelAngle / max(facing, 0.2);

    // the corners: how near the next wall over is
    float nearest = 1.0e9;
    for (int j = 0; j < WALLS; j++) {
        if (j != face) {
            nearest = min(nearest, radius - dot(faceDirection(j), local.xz));
        }
    }
    float ridge = 1.0 - smoothstep(0.0, footprint * 1.5 + 0.1, max(nearest, 0.0));

    vec2 surface = vec2(dot(local.xz, vec2(-f.y, f.x)), local.y);
    vec2 comb = honeycombAt(surface / CELL + float(face) * 7.31, footprint / CELL, time, flare);

    // the static clings to the wall, in specks a set size on it, coarser far off so they never shrink below a pixel
    float speck = exp2(ceil(log2(max(footprint * 1.5, 0.125))));
    float frame = mod(floor(time * 30.0), 997.0);
    float grain = hash12(floor(surface / speck) + frame * vec2(13.1, 7.7));
    float sparkle = smoothstep(0.975, 1.0, hash12(floor(surface / (speck * 2.0)) + frame * vec2(5.3, 11.9) + 71.0));
    float bands = smoothstep(0.55, 1.0, 0.5 + 0.5 * sin(local.y * 0.9 - time * 2.4));

    float alpha = 0.04 + 0.35 * rim + 0.12 * comb.x + 0.22 * comb.y + 0.05 * bands + 0.45 * sparkle * (0.35 + rim) + 0.4 * ridge;
    alpha *= 0.85 + 0.3 * grain;
    vec3 color = mix(vec3(0.94, 0.93, 0.98), vec3(1.0, 0.62, 0.7), clamp(rim * 0.4 + comb.y * 0.6, 0.0, 1.0));
    color = mix(color, vec3(1.0, 0.9, 0.93), ridge * 0.6);
    color += (grain - 0.5) * 0.12;

    // spreading out or rushing back in, it burns scarlet
    alpha += flare * (0.16 + 0.4 * rim + 0.35 * comb.x + 0.3 * ridge);
    color = mix(color, mix(vec3(1.0, 0.36, 0.45), vec3(1.0, 0.85, 0.88), max(comb.x, ridge)), flare * 0.7);

    float warning = style.z;
    if (warning > 0.0) {
        float roll = hash12(vec2(floor(time * 12.0), 3.0));
        alpha *= roll < warning * 0.6 ? 0.15 : 1.0 + warning * 0.5;
    }

    // changing era, the whole wall flares with static
    float channel = style.w;
    color = mix(color, vec3(grain), channel * 0.8);
    alpha = mix(alpha, 0.75, channel * 0.8);
    return vec4(clamp(color, 0.0, 1.0), clamp(alpha * strength, 0.0, 0.9));
}

// The snow of a set between channels, with bars rolling up through it.
vec3 channelStatic(vec2 uv, vec2 pixel, float time) {
    float s = hash12(floor(pixel / 2.0) + floor(time * 30.0) * vec2(7.0, 3.0));
    float bars = 0.75 + 0.25 * smoothstep(0.0, 0.25, abs(fract(uv.y * 2.0 + time * 3.0) - 0.5));
    return vec3(s * bars);
}

vec4 over(vec4 below, vec4 above) {
    float a = above.a + below.a * (1.0 - above.a);
    vec3 c = (above.rgb * above.a + below.rgb * below.a * (1.0 - above.a)) / max(a, 1.0e-4);
    return vec4(c, a);
}

// The rounded 4:3 screen of an old set; outside it is black.
float televisionMask(vec2 uv, vec2 screen, float amount, float roundness) {
    float aspect = screen.x / screen.y;
    float target = 4.0 / 3.0;
    vec2 p = uv - 0.5;
    if (aspect > target) {
        p.x *= aspect / target;
    } else {
        p.y *= target / aspect;
    }
    vec2 q = abs(p) - vec2(0.5 - roundness);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - roundness;
    float inside = 1.0 - smoothstep(-0.003, 0.003, d);
    return mix(1.0, inside, amount);
}

void main() {
    float time = Params.x;
    vec2 screen = Params.yz;
    vec2 pixel = texCoord * screen;
    vec3 scene = texture(InSampler, texCoord).rgb;

    float depth = texture(DepthSampler, texCoord).r;
    bool hand = texture(HandDepthSampler, texCoord).r > 0.0;
    float ndcDepth = depth;
#ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    ndcDepth = depth * 2.0 - 1.0;
#endif
    vec4 world = InvProjView * vec4(texCoord * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 position = world.xyz / world.w;
    vec3 eye = eyePosition();
    float dist = length(position - eye);
    vec3 dir = ray(texCoord, eye);
    float pixelAngle = length(ray(texCoord + vec2(0.0, 1.0 / screen.y), eye) - dir);
    if (depth <= 0.0) {
        dist = 1.0e9;
    }
    if (hand) {
        dist = 0.0;
    }

    int count = int(Camera.w + 0.5);
    int around = int(Camera.x + 0.5) - 1;
    float crossing = 0.0;
    vec4 wall = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= count) {
            break;
        }
        vec3 c = Shapes[i].xyz;
        float r = Shapes[i].w;
        crossing = max(crossing, 1.0 - smoothstep(0.0, 1.6, abs(shapeLevel(eye - c, r) - 1.0) * r));
        vec4 span = traceHex(eye, dir, c, r);
        float t0 = span.x;
        float t1 = span.y;
        if (t0 >= t1 || t1 <= 0.0) {
            continue;
        }
        if (t0 < 0.0) {
            // from inside: the far side of the wall, faintly, wherever it stands before what you are looking at
            if (t1 < dist) {
                vec3 farSide = eye + dir * t1;
                vec4 layer = wallLayer(farSide, t1, c, r, int(span.w + 0.5), dir, pixelAngle, time, Styles[i], 0.7);
                layer.a *= insideFade(farSide.y - c.y, r);
                wall = over(wall, layer);
            }
        } else if (t0 < dist) {
            // from outside: only the wall. What stands inside it looks just as it really is; the era is only seen
            // from within
            vec3 entry = eye + dir * t0;
            // burning brighter where the wall meets the ground and whatever stands in it
            float contact = exp(-max(dist - t0, 0.0) * 3.0);
            vec4 layer = wallLayer(entry, t0, c, r, int(span.z + 0.5), dir, pixelAngle, time, Styles[i], 1.0);
            layer.rgb = mix(layer.rgb, vec3(1.0, 0.8, 0.84), contact * 0.5);
            layer.a = clamp(layer.a + contact * 0.4, 0.0, 0.95) * outsideFade(entry.y - c.y, r);
            wall = over(wall, layer);
        }
    }

    float insideAmount = Camera.y;
    if (around >= 0 && Styles[around].z > 0.0) {
        // a falling Hex flickers in and out of its era
        float roll = hash12(vec2(floor(time * 9.0), 11.0));
        insideAmount *= roll < Styles[around].z * 0.5 ? 0.0 : 1.0;
    }
    int insideEra = int(Camera.z + 0.5);

    vec3 color = scene;
    if (insideAmount > 0.0) {
        color = mix(color, eraLook(scene, insideEra, texCoord, screen, time), insideAmount);
    }
    // changing era: the picture drops into static for a moment, like a set changing channels
    float channel = around >= 0 ? Styles[around].w * insideAmount : 0.0;
    if (channel > 0.0) {
        color = mix(color, channelStatic(texCoord, pixel, time), channel * 0.9);
    }
    color = mix(color, wall.rgb, wall.a);

    if (crossing > 0.0) {
        // stepping through the wall is stepping through a television
        float s = hash12(floor(pixel / 2.0) + floor(time * 30.0) * 7.0);
        color = mix(color, vec3(s), crossing * 0.75);
    }

    float television = Params.w;
    if (television > 0.0) {
        float roundness = insideEra <= 3 ? 0.055 : 0.0;
        color *= televisionMask(texCoord, screen, television * insideAmount, roundness);
    }
    fragColor = vec4(color, 1.0);
}
