#version 330
#extension GL_ARB_separate_shader_objects : require

// The Hex, drawn over the finished world.
// From outside only its wall shows, with everything inside looking as it really is; inside one, the whole view is in
// its era. A home a fallen Hex has left standing slips through the eras wherever it is seen from.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HandDepthSampler;
// the light of the magic in view on its own, drawn again over black: what of the picture is magic rather than world
uniform sampler2D MagicSampler;

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
    // xyz: where a wall was struck or passed through, relative to the camera; w: ticks since, negative for none
    vec4 Ripples[8];
    // how hard each was struck, four to a vec4
    vec4 RippleStrength[2];
    // per Hex, xyz: where its caster took hold of its wall to part it, relative to the camera; w: how far each edge of
    // the opening stands from there, in blocks, below 0 for none
    vec4 Tears[4];
    // the Hex around the camera changing era, x: the era before, y: how far out from its middle the new one has spread,
    // z: the band behind that it comes in over, w: 1 while it spreads
    vec4 EraChange;
    // the view coming through a wall, x: seconds since, below 0 for none; y: 1 going in, 0 coming out; z: how strongly
    // it shows, less with flashing turned down; w: 1 for the picture to tear and roll, 0 with camera shake turned down
    vec4 Crossing;
    // homes fallen Hexes have left standing: xyz the lowest corner of the box around each, relative to the camera, w how
    // wildly it glitches (0 for none)
    vec4 RemnantLow[2];
    // xyz the highest corner, w a seed of its own
    vec4 RemnantHigh[2];
    // how each is slipping right now, x: 0 not at all, 1 all into one era, 2 each patch into its own, 3 bits of it gone;
    // y: the era it slipped into, z: the slip's seed
    vec4 RemnantStyle[2];
};

// Seconds the view takes to come through a wall.
const float CROSS_SECONDS = 1.6;

const int MAX_RIPPLES = 8;

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
        // 1950s: black and white like a brightly lit sitcom stage on film: deep blacks, bright whites rolling off softly,
        // a little grain and a gentle vignette
        float l = smoothstep(0.03, 0.97, dot(color, LUMA));
        l = mix(l, l * l * (3.0 - 2.0 * l), 0.4);
        return (vec3(l) * vec3(1.0, 0.99, 0.96) + grain * 0.045) * vignette(uv, 0.4);
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

// Where a color lies around the wheel, 0 to 1 from red.
float hueOf(vec3 c) {
    float hi = max(c.r, max(c.g, c.b));
    float lo = min(c.r, min(c.g, c.b));
    float d = hi - lo;
    if (d < 1.0e-4) {
        return 0.0;
    }
    float h = hi == c.r ? mod((c.g - c.b) / d, 6.0) : hi == c.g ? (c.b - c.r) / d + 2.0 : (c.r - c.g) / d + 4.0;
    return h / 6.0;
}

// Color coming into a black and white picture, the way a sitcom turned to color: first a pale tint over the grey as if
// painted on by hand, keeping the grey's own light, then the colors blooming in for real, the most vivid first, the
// flowers and the hedges, reds a little ahead of the rest, the dull walls and roads last. k runs from 0, all grey, to 1,
// all color; run backwards it drains the color out the same way, the vivid reds lingering longest.
vec3 colorIn(vec3 grey, vec3 color, float k, float scatter) {
    float hi = max(color.r, max(color.g, color.b));
    float lo = min(color.r, min(color.g, color.b));
    float saturation = hi > 1.0e-4 ? (hi - lo) / hi : 0.0;
    float hue = hueOf(color);
    float fromRed = min(hue, 1.0 - hue) * 2.0;
    float delay = clamp(0.42 * (1.0 - saturation) + 0.18 * fromRed + (scatter - 0.5) * 0.12, 0.0, 0.62);
    float tinted = smoothstep(0.0, 0.3, k);
    float bloomed = smoothstep(delay, delay + 0.36, k);
    vec3 tint = grey + (color - vec3(dot(color, LUMA))) * 0.45;
    return mix(mix(grey, tint, tinted), color, bloomed);
}

// The look of a point of the Hex while one era gives way to the next: k from 0, still the era before, to 1, all the new
// one.
vec3 eraChange(vec3 world, int before, int after, float k, vec3 local, vec2 uv, vec2 screen, float time) {
    vec3 prior = eraLook(world, before, uv, screen, time);
    vec3 next = eraLook(world, after, uv, screen, time);
    // a little unevenness, so the color comes in patchily, as if brushed on
    float scatter = hash12(floor(local.xz * 0.5) + vec2(3.1, 7.7));
    bool wasGrey = before <= 1;
    bool isGrey = after <= 1;
    if (wasGrey && !isGrey) {
        return colorIn(prior, next, k, scatter);
    }
    if (!wasGrey && isGrey) {
        return colorIn(next, prior, 1.0 - k, scatter);
    }
    return mix(prior, next, smoothstep(0.0, 1.0, k));
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

// How a point of a wall is stirred by the blows it has taken: x the red rings racing out from each, y the white-hot
// flash where it struck, z the red glow it leaves behind for a while.
vec3 rippleAt(vec3 hit) {
    vec3 stir = vec3(0.0);
    for (int i = 0; i < MAX_RIPPLES; i++) {
        float age = Ripples[i].w;
        if (age < 0.0) {
            continue;
        }
        float strength = RippleStrength[i / 4][i % 4];
        float d = length(hit - Ripples[i].xyz);
        float reach = 6.0 + 22.0 * strength;
        float width = 0.8 + 0.12 * age;
        float fade = exp(-age / (10.0 + 16.0 * strength));
        float front = (d - reach * (1.0 - exp(-age / 10.0))) / width;
        stir.x += strength * fade * exp(-front * front);
        // a second, fainter ring a little behind the first
        float echo = (d - reach * 0.6 * (1.0 - exp(-max(age - 4.0, 0.0) / 10.0))) / (width * 1.5);
        stir.x += 0.55 * strength * fade * exp(-echo * echo) * step(4.0, age);
        stir.y += strength * exp(-d * (0.8 - 0.4 * strength)) * exp(-age / 4.0);
        stir.z += strength * exp(-d / (4.0 + 7.0 * strength)) * exp(-age / 35.0);
    }
    return min(stir, vec3(1.0, 1.5, 1.0));
}

// Texels to a block where the Hex parts: chunky, like Minecraft's own textures.
const float TEXEL = 8.0;

// Blocks short of its wall's corners that an opening's edges stop, as Hexes has it.
const float PART_MARGIN = 2.0;

// Where a caster is parting their Hex: a seam of light runs up the wall from where they took hold, and the wall pulls
// apart from it like curtains, from the ground up, as wide as they choose but never past its corners. Drawn on the
// wall's own texel grid. x: no wall here; y: how hot the burning edge is here, in flat bands like Minecraft fire (0
// none, 1 crimson, 2 scarlet, 3 white-hot); z: the wall bunched up just beyond the edge.
vec3 openingAt(vec3 hit, int face, vec3 center, float radius, vec4 tear, float time) {
    float pulled = tear.w;
    if (pulled < 0.0) {
        return vec3(0.0);
    }
    // only the wall that was taken hold of parts; the opening ends at its corners
    vec2 held = (tear.xyz - center).xz;
    int heldFace = 0;
    float best = -1.0e9;
    for (int j = 0; j < WALLS; j++) {
        float d = dot(faceDirection(j), held);
        if (d > best) {
            best = d;
            heldFace = j;
        }
    }
    if (face != heldFace) {
        return vec3(0.0);
    }
    vec2 f = faceDirection(face);
    vec2 along = vec2(-f.y, f.x);
    vec2 texel = floor(vec2(dot((hit - tear.xyz).xz, along), hit.y - center.y) * TEXEL);
    float frame = floor(time * 8.0);
    float side = texel.x < 0.0 ? 1.0 : 0.0;
    // each edge stops short of the corner on its side
    float halfSide = radius * 0.5773503;
    float fromMiddle = dot(held, along);
    float limit = max(0.0, min(pulled, (side > 0.5 ? halfSide + fromMiddle : halfSide - fromMiddle) - PART_MARGIN));
    // the edges step in whole texels, jagged and crawling like flames
    float jag = floor(hash12(vec2(texel.y, frame + side * 31.0)) * 3.0) + floor(hash12(vec2(floor(texel.y / 4.0), floor(time * 3.0) + side * 17.0)) * 4.0);
    float edge = (abs(texel.x + 0.5) - jag) / TEXEL - limit;
    if (edge < 0.0) {
        // now and then a hot texel still clinging just inside, about to drop away
        float ember = step(0.94, hash12(texel + frame * 3.1)) * step(-0.4, edge);
        return vec3(1.0 - ember, 2.0 * ember, 0.0);
    }
    float heat = edge + hash12(texel + frame * 1.7) * 0.22;
    float level = heat < 0.18 ? 3.0 : heat < 0.42 ? 2.0 : heat < 0.75 ? 1.0 : 0.0;
    float bunched = edge < 1.5 ? 1.0 : edge < 3.0 ? 0.5 : 0.0;
    return vec3(0.0, level, bunched);
}

// The wall: mostly clear, a faint honeycomb with cells lighting up, TV static sparkling over it, bright where it is
// seen edge-on and up the corners where one wall meets the next, with slow bands rolling down it. Where it has been
// struck it flares red, and rings of red run out across it. Where its caster parts it, it stands open between two
// burning edges.
vec4 wallLayer(vec3 hit, float travel, vec3 center, float radius, int face, vec3 dir, float pixelAngle, float time, vec4 style,
               float strength, vec4 tear) {
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

    vec3 stir = rippleAt(hit);
    vec2 surface = vec2(dot(local.xz, vec2(-f.y, f.x)), local.y);
    vec2 comb = honeycombAt(surface / CELL + float(face) * 7.31, footprint / CELL, time, flare + stir.x + stir.y);

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

    // spreading out or rushing back in, it burns scarlet, as brightly seen from inside as out
    float vivid = flare * (0.16 + 0.4 * rim + 0.35 * comb.x + 0.3 * ridge);
    color = mix(color, mix(vec3(1.0, 0.36, 0.45), vec3(1.0, 0.85, 0.88), max(comb.x, ridge)), flare * 0.7);

    // struck: it reddens where it was hit and in the rings running out from there, white-hot right at the blow, and
    // the red lingers in the honeycomb around it. However hard it is hit, what stands inside still shows through.
    vivid += min(0.6, 0.8 * stir.x + 0.35 * stir.z + 0.6 * stir.z * comb.x + 0.4 * stir.x * comb.x) + 1.0 * stir.y;
    color = mix(color, vec3(1.0, 0.08, 0.16), clamp(stir.x + stir.y + 0.9 * stir.z, 0.0, 1.0));
    color = mix(color, vec3(1.0, 0.88, 0.9), clamp(0.7 * stir.y * stir.y, 0.0, 0.7));

    float warning = style.z;
    if (warning > 0.0) {
        float roll = hash12(vec2(floor(time * 12.0), 3.0));
        alpha *= roll < warning * 0.6 ? 0.15 : 1.0 + warning * 0.5;
    }

    // changing era, the whole wall flares with static
    float channel = style.w;
    color = mix(color, vec3(grain), channel * 0.8);
    alpha = mix(alpha, 0.75, channel * 0.8);

    // parted: nothing where it stands open, the bunched wall reddening toward the edges, and the edges themselves
    // burning in flat bands of crimson, scarlet and white
    vec3 parted = openingAt(hit, face, center, radius, tear, time);
    alpha = alpha * (1.0 - parted.x) + 0.22 * parted.z;
    vivid *= 1.0 - parted.x;
    color = mix(color, vec3(1.0, 0.3, 0.36), 0.55 * parted.z);
    // faint as the wall itself is from inside, its flaring, its wounds and its parting never are
    alpha = alpha * strength + vivid * mix(0.75, 1.0, strength);
    if (parted.y > 0.0) {
        color = parted.y > 2.5 ? vec3(1.0, 0.93, 0.88) : parted.y > 1.5 ? vec3(1.0, 0.27, 0.33) : vec3(0.74, 0.05, 0.15);
        alpha = max(alpha, parted.y > 2.5 ? 0.97 : parted.y > 1.5 ? 0.9 : 0.8);
    }
    return vec4(clamp(color, 0.0, 1.0), clamp(alpha, 0.0, 0.97));
}

// The snow of a set between channels, with bars rolling up through it.
vec3 channelStatic(vec2 uv, vec2 pixel, float time) {
    float s = hash12(floor(pixel / 2.0) + floor(time * 30.0) * vec2(7.0, 3.0));
    float bars = 0.75 + 0.25 * smoothstep(0.0, 0.25, abs(fract(uv.y * 2.0 + time * 3.0) - 0.5));
    return vec3(s * bars);
}

// 0 below a, rising to 1 by b, holding, and falling back to 0 from c to d.
float window(float x, float a, float b, float c, float d) {
    return smoothstep(a, b, x) * (1.0 - smoothstep(c, d, x));
}

// The colors of the spectrum around a wheel: red at 0, then yellow, green, blue and violet.
vec3 spectrum(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// The picture split into its spectrum as if through a prism: copies of it from red to violet, each thrown a little
// further aside, most toward the edges of the view.
vec3 prism(vec2 uv, float spread, float time) {
    vec2 away = uv - 0.5;
    vec3 sum = vec3(0.0);
    vec3 weight = vec3(0.0);
    for (int i = 0; i < 6; i++) {
        float k = float(i) / 5.0;
        vec3 tint = spectrum(k * 0.8);
        vec2 offset = (away * 0.09 + vec2(0.018 * sin(time * 6.0 + k * 4.0), 0.0)) * (k - 0.5) * spread;
        sum += texture(InSampler, clamp(uv + offset, 0.0, 1.0)).rgb * tint;
        weight += tint;
    }
    return sum / max(weight, vec3(1.0e-3));
}

// The world seen in its heat: cold violet and blue through to red, and white-hot.
vec3 thermal(float l) {
    vec3 c = spectrum(0.72 * (1.0 - l));
    return mix(c * (0.3 + 0.7 * smoothstep(0.0, 0.35, l)), vec3(1.0), smoothstep(0.82, 1.0, l));
}

// The world as if x-rayed: dark where it was bright, its outlines glowing pale blue.
vec3 xray(vec2 uv, vec2 screen, float l) {
    vec2 d = 1.5 / screen;
    float gx = dot(texture(InSampler, uv + vec2(d.x, 0.0)).rgb - texture(InSampler, uv - vec2(d.x, 0.0)).rgb, LUMA);
    float gy = dot(texture(InSampler, uv + vec2(0.0, d.y)).rgb - texture(InSampler, uv - vec2(0.0, d.y)).rgb, LUMA);
    float edge = clamp(length(vec2(gx, gy)) * 5.0, 0.0, 1.0);
    return vec3(0.3, 0.55, 0.85) * (1.0 - l) * 0.5 + vec3(0.8, 0.93, 1.0) * edge;
}

// The snow of a color set with no signal: grey speckles with a little of every color in them.
vec3 snow(vec2 pixel, float time) {
    vec2 cell = floor(pixel / 2.0);
    float frame = floor(time * 30.0);
    float grey = hash12(cell + frame * vec2(7.0, 3.0));
    vec3 tint = vec3(hash12(cell + frame * 1.31 + 11.0), hash12(cell + frame * 1.73 + 23.0), hash12(cell + frame * 2.11 + 37.0));
    return mix(vec3(grey), tint, 0.35);
}

// How far the view is into rolling once as it comes through, as a set does losing its hold: 0 to 1.
float crossingRoll(float p, float motion) {
    return smoothstep(0.24, 0.44, p) * motion;
}

// Where the beam painting the new picture in has come down to, coming through: from over the top of the view at the
// start to under its bottom at the end.
float beamHeight(float p) {
    return 1.03 - 1.06 * smoothstep(0.42, 0.9, p);
}

// Whether the beam has painted a row of the screen yet.
float paintedBy(float beam, float y) {
    return smoothstep(beam - 0.002, beam + 0.002, y);
}

// Where a point of the screen shows from while the view comes through a wall: rippling with the field, lines torn
// sideways as the signal breaks up, rolling once, and bulging like the glass of a picture tube as the picture becomes
// one.
vec2 crossingWarp(vec2 uv, float p, float time, float strength, float motion) {
    float field = window(p, 0.0, 0.1, 0.45, 0.7) * strength;
    float torn = window(p, 0.15, 0.3, 0.5, 0.75) * strength * motion;
    float bulge = window(p, 0.4, 0.62, 0.78, 1.0) * strength;
    vec2 q = uv;
    q.x += (sin(q.y * 38.0 + time * 31.0) * 0.004 + sin(q.y * 9.0 - time * 13.0) * 0.006) * field * (0.3 + 0.7 * motion);
    float row = floor(q.y * 72.0);
    float frame = floor(time * 24.0);
    float tear = step(0.72, hash12(vec2(row, frame)));
    q.x += (hash12(vec2(row * 1.7, frame + 5.0)) - 0.5) * 0.09 * tear * torn;
    q.y = fract(q.y + crossingRoll(p, motion));
    vec2 c = q * 2.0 - 1.0;
    c *= 1.0 + bulge * 0.1 * dot(c, c);
    return c * 0.5 + 0.5;
}

// The view coming through a wall, over the picture. It breaks up into the spectrum: band by band the world flashes
// into its heat, x-rayed, or split as through a prism, with rainbow light running across it. The snow of a set between
// channels tears through it and swallows it, it rolls, and then a beam runs down the screen painting the picture on
// the far side in, line by line, as a television's is, the phosphor glowing behind it and its scanlines fading.
vec3 crossingLook(vec3 color, vec2 uv, vec2 screenUv, vec2 pixel, float p, float time, vec2 screen, float strength, float motion) {
    float field = window(p, 0.0, 0.1, 0.45, 0.72) * strength;
    float noise = window(p, 0.16, 0.34, 0.52, 0.85) * strength;
    float beam = beamHeight(p);
    float painted = paintedBy(beam, screenUv.y);
    float rough = 1.0 - painted;
    vec3 scene = texture(InSampler, uv).rgb;
    float l = dot(scene, LUMA);
    // bands of it, their edges stepping raggedly across the view, each a few frames in one part of the spectrum
    float row = floor(uv.y * 9.0 + (hash12(vec2(floor(uv.x * 6.0), floor(time * 9.0))) - 0.5) * 0.6 - time * 2.3);
    float pick = hash12(vec2(row, floor(time * 11.0)));
    vec3 split = prism(uv, 0.4 + field * 1.4, time);
    vec3 view = pick < 0.2 ? mix(thermal(l), split, 0.3) * 0.9 : pick < 0.36 ? xray(uv, screen, l) : split;
    color = mix(color, view, field * mix(0.2, 0.92, rough));
    float streak = pow(0.5 + 0.5 * sin((uv.x * 0.7 + uv.y) * 17.0 - time * 13.0), 12.0);
    color += spectrum(uv.x * 0.5 + uv.y * 0.35 - time * 0.8) * streak * field * 0.4 * rough;
    // the snow, tearing through in bands, and everywhere at its worst
    float strip = smoothstep(0.35, 0.65, hash12(vec2(floor(uv.y * 26.0 + time * 7.0), floor(time * 15.0))));
    float everywhere = window(p, 0.28, 0.36, 0.44, 0.52);
    color = mix(color, snow(pixel, time), noise * rough * max(strip, everywhere) * 0.85);
    // the black bar between frames, rolling through
    float roll = crossingRoll(p, motion);
    if (roll > 0.0 && roll < 1.0) {
        color *= smoothstep(0.0, 0.035, abs(screenUv.y - (1.0 - roll)));
    }
    // the beam, white-hot, and the phosphor still glowing just behind it
    if (beam > -0.03 && beam < 1.03) {
        float dy = screenUv.y - beam;
        float line = exp(-pow(dy * screen.y / 3.0, 2.0));
        float afterglow = painted * exp(-max(dy, 0.0) * 10.0);
        color += vec3(1.0, 0.94, 0.96) * line * 0.85 * strength + vec3(1.0, 0.3, 0.42) * afterglow * 0.15 * strength;
    }
    // scanlines over the fresh picture, fading as it settles
    float lines = 0.8 + 0.2 * sin(pixel.y * 3.14159);
    color *= mix(1.0, lines, clamp((painted * (1.0 - smoothstep(0.85, 1.0, p)) + rough * noise) * strength, 0.0, 1.0));
    // and the flash of passing through the field itself
    float flash = (1.0 - smoothstep(0.0, 0.08, p)) * 0.5 * strength;
    return mix(color, vec3(0.96, 0.9, 1.0), flash);
}

// A home left standing after its Hex fell, slipping through the eras with its blocks: all of it in another era for a
// moment, or each patch of it in one of its own, or bits of it lost to the snow of a dead channel. In between, patch
// after patch of it slips on its own, more of it the wilder it glitches, black and white most of all. Bars roll down it
// like a set losing its hold, static crawls over it, and the magic on it keeps its own red.
vec3 remnantLook(vec3 color, vec3 magic, vec3 local, vec2 uv, vec2 pixel, vec2 screen, float time, float amount, vec4 style, float seed) {
    int kind = int(style.x + 0.5);
    vec3 world = max(color - magic, vec3(0.0));
    vec3 cell = floor(local / 3.0);
    float frame = floor(time * (4.0 + 10.0 * amount));
    float pick = hash12(cell.xz * 1.37 + vec2(cell.y * 7.1, 0.0) + vec2(frame * 0.61, seed));
    float which = hash12(cell.zx * 2.11 + vec2(frame * 0.37, cell.y * 5.3 + seed));
    // black and white two times in three, and otherwise one of the eras in color
    int era = which < 0.66 ? (which < 0.33 ? 0 : 1) : 2 + int(floor((which - 0.66) / 0.34 * 3.0));
    vec3 look;
    if (kind == 1) {
        look = eraLook(world, int(style.y + 0.5), uv, screen, time);
    } else if (kind == 2) {
        float mixed = floor(hash12(cell.xz * 0.73 + vec2(cell.y * 3.3, style.z)) * 6.0);
        look = eraLook(world, int(mixed), uv, screen, time);
    } else if (kind == 3) {
        look = mix(world, snow(pixel, time), step(0.45, pick) * 0.8);
    } else {
        float slipped = step(1.0 - (0.2 + 0.45 * amount), pick);
        look = mix(world, eraLook(world, era, uv, screen, time), slipped);
    }
    float bar = smoothstep(0.9, 1.0, 0.5 + 0.5 * sin(local.y * 1.7 - time * 7.0 + seed));
    look = mix(look, vec3(dot(look, LUMA)), bar * 0.5 * amount);
    float grain = hash12(floor(pixel / 2.0) + floor(time * 30.0) * vec2(7.0, 3.0));
    look = mix(look, vec3(grain), 0.07 * amount);
    return look + magic;
}

vec4 over(vec4 below, vec4 above) {
    float a = above.a + below.a * (1.0 - above.a);
    vec3 c = (above.rgb * above.a + below.rgb * below.a * (1.0 - above.a)) / max(a, 1.0e-4);
    return vec4(c, a);
}

// The rounded 4:3 screen of an old set; outside it is black. Coming in, its edges close in from past the edges of the
// view.
float televisionMask(vec2 uv, vec2 screen, float amount, float roundness) {
    if (amount <= 0.0) {
        return 1.0;
    }
    float aspect = screen.x / screen.y;
    float target = 4.0 / 3.0;
    vec2 p = uv - 0.5;
    if (aspect > target) {
        p.x *= aspect / target;
    } else {
        p.y *= target / aspect;
    }
    p /= mix(1.45, 1.0, smoothstep(0.0, 1.0, amount));
    vec2 q = abs(p) - vec2(0.5 - roundness);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - roundness;
    float inside = 1.0 - smoothstep(-0.003, 0.003, d);
    return mix(1.0, inside, smoothstep(0.0, 0.25, amount));
}

void main() {
    float time = Params.x;
    vec2 screen = Params.yz;
    vec2 pixel = texCoord * screen;

    // coming through a wall, the picture itself is thrown about: everything is drawn from where it now shows from
    float p = Crossing.x >= 0.0 ? Crossing.x / CROSS_SECONDS : 2.0;
    bool coming = p < 1.0;
    vec2 uv = coming ? crossingWarp(texCoord, p, time, Crossing.z, Crossing.w) : texCoord;
    // nothing shows past the edge of the bulging glass
    float tube = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
    uv = clamp(uv, 0.0, 1.0);
    vec3 scene = texture(InSampler, uv).rgb;

    float depth = texture(DepthSampler, uv).r;
    bool hand = texture(HandDepthSampler, uv).r > 0.0;
    float ndcDepth = depth;
#ifndef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    ndcDepth = depth * 2.0 - 1.0;
#endif
    vec4 world = InvProjView * vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 position = world.xyz / world.w;
    vec3 eye = eyePosition();
    float dist = length(position - eye);
    vec3 dir = ray(uv, eye);
    float pixelAngle = length(ray(uv + vec2(0.0, 1.0 / screen.y), eye) - dir);
    if (depth <= 0.0) {
        dist = 1.0e9;
    }
    if (hand) {
        dist = 0.0;
    }

    int count = int(Camera.w + 0.5);
    int around = int(Camera.x + 0.5) - 1;
    float crossing = 0.0;
    // how much of what is seen lies out past a tear in the wall around you, the world as it really is
    float throughTear = 0.0;
    vec4 wall = vec4(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= count) {
            break;
        }
        vec3 c = Shapes[i].xyz;
        float r = Shapes[i].w;
        // standing at an opening, holding it, is not standing in the wall
        crossing = max(crossing, (1.0 - smoothstep(0.0, 1.2, abs(shapeLevel(eye - c, r) - 1.0) * r)) * step(Tears[i].w, -0.5));
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
                int farFace = int(span.w + 0.5);
                // as in the show, those inside barely see it: a faint shimmer, unless it flares
                vec4 layer = wallLayer(farSide, t1, c, r, farFace, dir, pixelAngle, time, Styles[i], 0.28, Tears[i]);
                layer.a *= insideFade(farSide.y - c.y, r);
                wall = over(wall, layer);
                if (i == around) {
                    throughTear = max(throughTear, openingAt(farSide, farFace, c, r, Tears[i], time).x);
                }
            }
        } else if (t0 < dist) {
            // from outside: only the wall. What stands inside it looks just as it really is; the era is only seen
            // from within
            vec3 entry = eye + dir * t0;
            int entryFace = int(span.z + 0.5);
            // burning brighter where the wall meets the ground and whatever stands in it, though not where it stands open
            float contact = exp(-max(dist - t0, 0.0) * 3.0) * (1.0 - openingAt(entry, entryFace, c, r, Tears[i], time).x);
            vec4 layer = wallLayer(entry, t0, c, r, entryFace, dir, pixelAngle, time, Styles[i], 1.0, Tears[i]);
            layer.rgb = mix(layer.rgb, vec3(1.0, 0.8, 0.84), contact * 0.5);
            layer.a = clamp(layer.a + contact * 0.4, 0.0, 0.95) * outsideFade(entry.y - c.y, r);
            wall = over(wall, layer);
        }
    }

    float insideAmount = Camera.y;
    if (coming) {
        // the picture on the far side of the wall is wherever the beam has painted it in
        float painted = paintedBy(beamHeight(p), texCoord.y);
        insideAmount = Crossing.y > 0.5 ? painted : 1.0 - painted;
    }
    if (around >= 0 && Styles[around].z > 0.0) {
        // a falling Hex flickers in and out of its era
        float roll = hash12(vec2(floor(time * 9.0), 11.0));
        insideAmount *= roll < Styles[around].z * 0.5 ? 0.0 : 1.0;
    }
    int insideEra = int(Camera.z + 0.5);

    vec3 color = scene;
    if (insideAmount > 0.0) {
        // magic keeps its own color in any era, scarlet in a black and white world: the world is drained around the
        // magic's light, and the light laid back over it. Out through where the wall stands open, the world is seen as it
        // really is.
        vec3 magic = texture(MagicSampler, uv).rgb;
        vec3 world = max(scene - magic, vec3(0.0));
        vec3 inEra;
        if (around >= 0 && EraChange.w > 0.5) {
            // a new era spreading out from the middle: how far it has come in here, the sky last of all
            vec3 center = Shapes[around].xyz;
            vec3 local = hand ? -center : position - center;
            float level = dist >= 1.0e8 ? Shapes[around].w + EraChange.z : shapeLevel(local, 1.0);
            float ahead = EraChange.y - level;
            float k = clamp(ahead / EraChange.z, 0.0, 1.0);
            inEra = eraChange(world, int(EraChange.x + 0.5), insideEra, k, local, uv, screen, time);
            // the front itself is her magic, a thin scarlet shimmer running out over the ground and everything on it
            if (dist < 1.0e8) {
                float edge = exp(-ahead * ahead / 0.9);
                float glint = hash12(floor(local.xz * 2.0 + local.y * 1.3) + floor(time * 14.0) * vec2(5.0, 9.0));
                magic += vec3(1.0, 0.16, 0.24) * edge * (0.05 + 0.6 * step(0.8, glint) * glint);
            }
        } else {
            inEra = eraLook(world, insideEra, uv, screen, time);
        }
        color = mix(color, inEra + magic, insideAmount * (1.0 - throughTear));
    }
    // changing era: the picture drops into static for a moment, like a set changing channels
    float channel = around >= 0 ? Styles[around].w * insideAmount : 0.0;
    if (channel > 0.0) {
        color = mix(color, channelStatic(uv, pixel, time), channel * 0.9);
    }
    // the homes fallen Hexes have left standing, wherever they are seen from
    if (!hand && dist < 1.0e8) {
        for (int i = 0; i < 2; i++) {
            float amount = RemnantLow[i].w;
            if (amount <= 0.0 || any(lessThan(position, RemnantLow[i].xyz)) || any(greaterThan(position, RemnantHigh[i].xyz))) {
                continue;
            }
            color = remnantLook(color, texture(MagicSampler, uv).rgb, position - RemnantLow[i].xyz, uv, pixel, screen, time, amount, RemnantStyle[i],
                    RemnantHigh[i].w);
        }
    }
    color = mix(color, wall.rgb, wall.a);

    if (crossing > 0.0) {
        // standing in the wall itself, the picture breaks up into its spectrum and its snow
        color = mix(color, prism(uv, 0.6 + crossing, time), crossing * 0.55);
        color = mix(color, snow(pixel, time), crossing * 0.4);
    }
    if (coming) {
        color = crossingLook(color, uv, texCoord, pixel, p, time, screen, Crossing.z, Crossing.w);
    }

    float television = Params.w;
    if (television > 0.0) {
        // coming in, the set's screen closes in around the picture as it is painted; going out, it opens away
        float framed = !coming ? insideAmount : Crossing.y > 0.5 ? smoothstep(0.5, 0.95, p) : 1.0 - smoothstep(0.42, 0.9, p);
        float roundness = insideEra <= 3 ? 0.055 : 0.0;
        color *= televisionMask(texCoord, screen, television * framed, roundness);
    }
    fragColor = vec4(color * tube, 1.0);
}
