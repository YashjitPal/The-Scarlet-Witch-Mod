# Scarlet — Design Document

A premium chaos-magic mod inspired by the Scarlet Witch. This is the living source of truth for what we are building and why. Update it whenever a decision changes.

Working names: mod ID `scarlet`, display name "Scarlet Witch" (public name still to be decided, see Open questions).

---

## 1. Pillars

1. **Looks like it had a $1M budget.** Every cast, transition, costume and screen is animated, layered and timed with care. Nothing pops in; everything eases.
2. **Faithful to the films.** Costumes, crowns and powers follow the films' look and feel, rebuilt from scratch (no ripped assets).
3. **Magic feels alive.** Effects are procedural (shaders, noise, physics), so they flow and never look like flat sprites.
4. **Runs smoothly.** Expensive-looking, cheap to run. Quality presets and strict per-effect budgets.
5. **Multiplayer-safe.** The server decides what happens; clients draw it. Everything works on dedicated servers.
6. **Easy to port.** Minimal dependencies, loader code kept thin, all JSON generated, rendering only through Mojang's own APIs.

---

## 2. Platform

| Piece | Choice |
|---|---|
| Minecraft | 26.3 (port to 26.4 when it releases) |
| Loaders | NeoForge **and** Fabric, from one shared codebase |
| Java | 25 |
| Build | Gradle 9.6, ModDevGradle (NeoForge + common), Fabric Loom 1.17 |
| Rendering | Blaze3D render pipelines + GLSL only. No raw OpenGL, so everything works on both the OpenGL and Vulkan backends |
| Dependencies | Fabric API (Fabric only). Player Animation Library when we add casting poses. GeckoLib only if a feature truly needs it. Nothing else |

---

## 3. Identity: the crown is the power

- **Whoever wears the crown is the Witch or Warlock.** Take it off and the powers are gone. Crowns can be passed on, stolen, or fought over.
- Two crowns, same powers, same animations:
  - **Scarlet Witch's tiara**
  - **Scarlet Warlock's crown**
- The only differences are the crown and the costume.
- **Swap recipe:** a crown alone in a crafting grid becomes the other crown. It keeps everything stored on it: enchantments, name, durability, mastery, and its link to an active hex.

### Crown item

- Worn in the **helmet slot**. Protects like a **diamond helmet** and takes helmet enchantments.
- **No durability:** it never wears out or breaks, so mastery is never lost. It survives fire and lava.
- **Recipe (early game, cheap):** gold ingots, redstone, and an amethyst shard. Each crown has its own shape. *(Proposed.)*

### Crown designs

- **Scarlet Witch's tiara (film-accurate, WandaVision):** a slim piece that frames the face. A downward chevron sits at the center of the forehead like a widow's peak, swept wings carry flame-like swirl strands, two tall points rise above the temples, and thin strips run down the temples to points at the cheekbones. Deep burgundy with dark grooves.
- **Scarlet Warlock's crown:** the same design language, made bolder: an upward blade at the center, horns that curve outward, an angular brow band, and strips down to the jaw. Darker.
- Both are cut-out sprites extruded into thin 3D pieces and laid over the face, drawn at the costume's density (2 texels per skin pixel) so their pixels match the suit's. Engraved grooves only cut where the metal is at least three texels thick.

```
Scarlet Witch's tiara      Scarlet Warlock's crown
  G A G                      R A R
  R G R                      G G G

G = gold ingot   R = redstone dust   A = amethyst shard
```

---

## 4. Suit-up

- Wear the crown and you have every power, with **just the crown** showing.
- Press the **suit-up key** and the full costume weaves on. Press it again to dismiss it.
- Take the crown off and the costume dissolves with it.

### Transformation

- **Suit up:** scarlet threads spiral up from the feet; the costume dissolves in with glowing edges; the cape unfurls; the crown flares last with a flash and a sound sting.
- **Suit down:** the costume burns away into drifting embers.
- About **1.5 seconds**. You can keep moving. Other players see it. A setting makes it instant.
- **First person:** the gloves visibly form on your hands.

### Rules

- The costume is **purely visual**. Armor under it still protects; it is just hidden while the costume is on.
- Your **face stays your own**. Both costumes fit **slim and classic** skins.
- **The costume reacts to your magic:** accents glow brighter while casting, capes and coat tails sway with physics and lift while levitating, the crown flares during big spells.

### Costume designs (film-accurate)

- **Witch:** a deep crimson corset shaded in many reds (sweetheart neckline under a sheer dark yoke, bright center busk, curving seams, black side panels), an open crimson coat whose lapels frame it down to the hem, a standing collar with silver clasps, a black belt with a silver buckle, red gauntlets, black trousers, boots, an ankle-length cape with a patterned magenta lining, and the tiara.
- **Warlock:** a rich crimson tunic quilted in faint diamonds, open at the throat in a gold-trimmed V over a dark undershirt, a standing collar, a wide gold belt with a red gem buckle, black sleeves under gold-trimmed gauntlets, and the horned crown.
- Both styles take after Arrzee's Multiverse (reference only, all art is our own) and are painted at 2x texel density.

---

## 5. Powers

### Casting

- **Spell wheel:** hold the wheel key, aim at a spell, release. Locked spells show their required mastery rank.
- **Cast:** right click with an **empty main hand** while wearing the crown. With an item in hand, right click behaves normally.
- **Favorites:** optional quick-cast keys (unbound by default).
- Each spell defines its input: tap, hold to channel, charge and release, or aim and place.

### Limits

- **Chaos energy bar** that refills over time, faster after a moment of not casting.
- **Short per-spell cooldowns.**
- Max energy and regeneration grow with mastery.

### Progression: mastery

- Powers **grow with use**, and **the crown remembers** (mastery is stored on the crown item, so a stolen crown carries its mastery).
- Mastery ranks unlock spells and raise their strength. The **hex unlocks last**. *(Proposed order, tunable by config:)*

| Rank | Unlocks |
|---|---|
| 1 | Chaos Bolt, Chaos Shield, suit-up |
| 2 | Levitation |
| 3 | Telekinesis |
| 4 | Red Mist |
| 5 | Shockwave |
| 6 | Mind Control |
| 7 | Rune Trap |
| 8–9 | Strength, range and energy growth |
| 10 | The Hex |

### Crown spells

| Spell | What it does |
|---|---|
| **Chaos Bolt** | Fire scarlet energy blasts |
| **Telekinesis** | Grab, lift and throw mobs, players and blocks |
| **Levitation** | Float and fly, energy trailing from your hands |
| **Chaos Shield** | Block attacks and projectiles |
| **Mind Control** | Take control of a mob, animal or player (see below) |
| **Shockwave** | A burst that throws everything back |
| **Rune Trap** | Glowing sigils that bind enemies in place |
| **Red Mist** | Dissolve and reappear a short distance away |
| **The Hex** | Reality warping (section 7) |

### Chaos Shield (rank 1, hold to channel)

- **Hold right click** with an empty hand: both palms push forward and a disc of scarlet energy opens in front of you. Let go to lower it.
- **What it stops:** blows from the front, and projectiles, which are turned back the way they came and become yours. Anything that ignores shields (fall damage, starvation, magic) passes through.
- **Cost:** a slow drain while held, plus energy for every blow, more for harder hits. Melee attackers are thrown back.
- **Shatter:** when a blow costs more than you have, the shield still stops it but bursts into shards, and the spell rests for 3 seconds.
- **Held still:** you move at 60% speed while shielding. Using an item or taking off the crown drops the shield.
- **Look:**
  - A faint membrane that brightens toward a burning rim, with motes circling it.
  - A honeycomb lattice shimmering in slow waves, with a ripple from every hit.
  - Wisps spiraling across the face, and ribbons of energy feeding it from both palms.
  - It flares open when raised, and a low hum swells and fades with it.
- **Pose:** the arms tremble with the strain and are knocked back by each blow.
- **First person:** your own shield sits further out and fainter, so it frames the view instead of covering it.

### Levitation (rank 2, toggle)

- **Turn on and off** by casting it from the wheel, or by **double-tapping jump** (as in creative flight). Fly with the creative flight controls.
- **Cost:** a gentle drain while airborne. Landing ends it. Switching it off in mid-air, or running out of energy, lowers you gently with slow falling.
- **Look:**
  - A pad of light under the feet, ringed with slowly turning marks.
  - Threads of energy winding up the legs, with sparks circling the feet and dust falling away.
  - A shock ring across the ground at lift-off, and a softer one at touch-down.
- **Pose:** the arms float out from the sides and the legs hang loose, with a gentle hover bob. The body leans into the flight and the arms sweep back as you pick up speed.

### Magic on the arms

Whenever powers are in use, scarlet wisps spiral around the hands and forearms and sparkles drift off the fingers, flaring brighter at the moment of casting. Visible in first person on your own hands. Drawn by our effects engine, not vanilla particles.

They burn steadily while the shield is held and smolder while levitating.

### Mind control

- Look at a creature within range and hold the cast: scarlet tendrils stream from your hands into its head, and its eyes glow red.
- **Mobs and animals:** your view moves into the creature and you control it directly: move, jump, attack. Your own body stands channeling and is vulnerable. Release to return.
- After release, the mob stays loyal to you for a while.
- **Players (short and fair):** a few seconds of control. Their screen tints red and they can struggle free by mashing keys. Long cooldown. Servers can turn it off.
- Dreamwalking is the Darkhold's long-range version that works across dimensions.

---

## 6. The Darkhold

- **Craftable, late game:** a book with echo shards from Ancient Cities. *(Exact recipe proposed: book in the center, echo shards and crying obsidian around it.)*
- **Carry it to use its dark spells.** Heavy use slowly **corrupts** you:
  - darker magic (scarlet shifts toward black-crimson), dark veins at the screen edges, whispers;
  - side effects at high corruption (to be tuned: hunger drain, darkness pulses, restless sleep);
  - corruption slowly fades while you are not using it.
- Dark spells: **Dreamwalking** first. More to be decided.

### Dreamwalking

- Your **body sits cross-legged in a meditation pose**, hovering slightly off the ground in a slow swirl of scarlet wisps (WandaVision finale / Doctor Strange). Other players can see it.
- Your **spirit takes over a mob**, even in the **Nether or End** (Minecraft's dimensions stand in for the multiverse).
- If your **body gets hurt, your spirit snaps back** instantly so you can defend yourself.
- *(Proposed:)* choose a dimension; you arrive in a mob near the last place you stood there (or near spawn). Drains energy and adds corruption over time. The possessed mob's eyes glow scarlet.

---

## 7. The Hex

The centerpiece. Inspired by Westview.

### Lifecycle

- Cast it and it **spreads out from you** in the iconic static wave, then **stays where you cast it**.
- The caster can **walk out** and it stays up.
- The caster can **grow or shrink** it anytime.
- It **collapses if the caster stops wearing the crown** (death, stolen, or taken off). A few seconds of flickering warning first, so an accident isn't a disaster.
- When it collapses, **the wall closes in, slowly at first and gathering speed, and everything it passes over changes back**, like the finale. The bigger the hex, the longer the fall: about 7 seconds for a freshly cast hex, up to 16 for the largest.
- **The caster's home goes last, and not with the wall.** When the falling wall reaches it, the house holds on alone, even after the hex is gone. For about 10 seconds it glitches through the eras, wilder and wilder: all of it jumps into another era for a moment, or each part into its own (one era's walls under another's roof), patches of it drain to black and white, bits of it blink out, and red static crawls over it. The caster's clothes slip through the era outfits with it. Then it goes part by part, the way it went up but backward: the rooms empty, the yard and porch go, then the roof, the walls, the timber frame and the floor. Each block glitches out red as it goes, and last of all the land comes back as it was. It takes about as long as it took to build, some 15 seconds. Casting a new hex over it, or by it, puts it all back at once.
- One hex per caster; while it stands it reserves part of the caster's energy bar.
- **Shape: a hexagon**, like the Westview anomaly on S.W.O.R.D.'s map. Seen from above it is a regular hexagon, with flat walls facing north and south and corners pointing east and west. The six walls stand straight up without end, and nothing closes over the top. Seen from the ground, they rise high over the town (taller for a bigger hex) and fade away into the sky. The corners, where one wall meets the next, glow. The radius is measured to the middle of a wall, so the corners stand about 15% further out. The hex is everything within its walls, as high and as deep as the world goes, so a hex cast on a hill still comes down to the valley.

### Look

- **From outside:** a shimmering, staticky wall. It is mostly clear, with a faint honeycomb whose cells light up at random, TV-static sparkles, and slow bands rolling down it. It is brighter where seen edge-on and up its corners. Through it, the town looks just as it really is, in normal colors: the era is only seen from inside. While spreading or collapsing it burns scarlet, and when the era changes the wall flares with static.
- **From inside:** your **whole view is in the era**, like being inside the show.
- Implemented as a screen shader that reconstructs each pixel's world position. Works on Vulkan and OpenGL.

### Eras

| Era | Look |
|---|---|
| 1950s | Black and white, soft film grain, gentle vignette |
| 1960s | Crisp, high-contrast black and white |
| 1970s | Warm, saturated film color |
| 1980s | Bright colors with VHS fuzz |
| 2000s | Flat, sitcom-camera look |
| Present day | Normal colors, still inside the barrier |

### Extras (all enabled)

- **Old-TV framing** for older eras: 4:3 bars, rounded screen corners.
- **Title card** when someone comes in, and again when a new episode begins while they're inside: the hex's name (set by the caster with `/scarlet hex name`), the episode and its title, and who stars in it, with the era's theme playing on note blocks.
  - 1950s: the name glowing white over a darkened screen, a ruled line drawn out under it, credits in small capitals. Harp and bells.
  - 1960s: a cartoon opening, inked letters bouncing in one at a time with stars twinkling around them. Xylophone and walking bass.
  - 1970s: bands of orange, gold and brown sweeping in above and below a cream title. Strummed guitar and flute.
  - 1980s: neon on videotape, a dark band edged in pink and cyan, scanlines, split colors and tracking jumps. Synth arpeggios and a drum machine.
  - 2000s: every letter slammed in its own tilted box of color. Power chords and fast drums.
  - Present day: a documentary caption sliding in at the side of the frame. Banjo and handclaps.
- **Episodes:** every change of era begins the next episode. Episodes mode (`/scarlet hex episodes on`) moves the era forward by itself every morning. After the present day it goes back to the 1950s for a new season.
- **Era audio:** sounds get muffled like an old TV.
- **Laugh track** when someone gets hurt inside, now and then (with a cooldown). Only in the eras filmed before a live audience, the 1950s to the 1980s: the 2000s and the present day are single-camera and silent.

### Reality warping

- **Anyone can enter.** The caster can **kick** someone out: they get flung out through the wall, Monica-style, with a spectacular animation (the wall bulges and ripples, sparks, a boom). They can walk back in.
- **Residents:** every hostile mob inside (zombies, skeletons, creepers, spiders and so on) is rewritten into a townsperson in era clothes. Animals stay animals. Bosses resist the hex.
  - They are the same mobs underneath, so nothing is ever lost or duplicated.
  - They follow **sitcom routines**: wander, chat, sit on couches, watch the era TV, wave at players.
    - They go looking for somewhere to sit, a couch or an armchair, best of all one facing a television, which they switch on if it is off. They sit as long as an episode's scene, a minute or so.
    - Two with nothing to do stop for a chat: they turn to each other and take turns talking with their hands.
    - Whoever comes by gets a wave, the hand up high beside the head, from the couch or mid-conversation too. Once a minute for the same person.
    - They open doors on their way and close them behind them.
    - Villagers keep the village life they have, which is sitcom enough.
  - They never attack, and creepers never explode.
  - **Attack one and the spell breaks for that mob:** it turns back into what it really is and fights back.
  - A resident that leaves the hex turns back at the wall. When the hex collapses, each one turns back as the inward-moving wall passes it.
- **Rewrites:** things that cross in get rewritten to fit (arrows into flowers, etc.).
- **Sky:** the caster controls time of day and weather inside.
- **Restyle blocks:** hold a block in your off hand and sweep your aim to paint blocks into it. Your other arm flings out at what you paint and a straight, crackling beam of scarlet magic flies from the palm. Everything changes back when the hex falls. **Breaking a restyled block gives the original block**, so the hex can't be used to farm materials.
- **Era outfits:** everyone inside, players and residents, wears era clothes, like Wanda and Vision each episode.
  - Assigned automatically: each person always gets the same outfit for a given era (picked from the era's wardrobe by their UUID).
  - Fitted like armor (chest, legs, shoes) over the skin. Casual, well-fitting and gender neutral.
  - Real armor is hidden while inside but keeps protecting, and reappears outside.
  - The caster's own suit-up costume takes priority over the era outfit.
- **Everything era-related exists only inside.** The era's picture (black and white, warm 70s color, videotape and so on) is only seen from within: from outside, everything in the hex looks normal through the wall. Step out and it falls away with a quick TV-static flicker: normal colors, normal clothes, armor back. The one exception is the home a fallen hex leaves glitching behind it, which slips through the eras' pictures wherever it is seen from.

### Homes and the town

The Hex builds Westview.

- **Your home:** the caster can raise a sitcom house anywhere inside the Hex. A scarlet outline previews the lot where you aim, scrolling turns it, and a click confirms.
  - Started from the Showrunner remote while the Hex stands. Right-click raises it there, left-click cancels, and the outline shows crossed out where it can't stand (past the wall, on deep water, or on land too steep to level).
  - The old house dissolves, and so does whatever of the town stood on the new lot. Its old lot takes an ordinary house. The town remembers where the home stood, so casting again by it brings it back there.
  - While it goes up, the caster raises both arms to it and a crackling beam flies from each palm, jumping from block to block as they land.
  - It builds itself the way the house does in WandaVision: scarlet light traces the lot, the timber frame rises from the ground, then the walls, windows and roof close in, and the rooms furnish themselves.
  - When casting, the Hex can start from your home: the house builds first, then the Hex bursts out from it and spreads.
  - **It's never the same house twice.** Each town picks its home from five two-story designs: a colonial under a portico, a farmhouse with a porch across its front, a foursquare under a hipped roof, a front-gabled house with a porch wrapping round one side, and a wide house under a hipped roof. Its paint, shutters, chimney, flowers, hedge or picket fence and shade tree vary on top of that. A town the Hex remembers raises the same home again.
  - **Cast it by something standing and the home is made of it.** The Hex looks for a house, a ruin, a shell of walls or a bare foundation within about 10 blocks of the caster. It carries them inside, through the walls if it must, and lays the town out from its front door. Nothing of it is torn down: it keeps its shape, is made over in the era's look, and is finished around what stands.
    - Fallen walls close up with windows in them, a floor goes down where there is none, and a door goes in its front if it has none.
    - A ruin with no roof rises two stories under a new roof, with a stair up to a bedroom. One that still has its roof keeps its shape, recessed porches and all.
    - A stoop, the yard, the fence and gate, a mailbox and a furnished living room go wherever there's room.
    - When the Hex falls it goes part by part like any home, and the building comes back as it was, chests and all. Buildings another Hex has made are left alone.
- **The town:** where the land inside is open, or holds a vanilla village, the Hex can build a whole sitcom suburb as its wave spreads. That means streets with sidewalks and street lamps, rows of houses with lawns, picket fences and mailboxes, and a town square. Each house assembles as the wave passes over its lot.
  - **Real suburban sizes.** Lots are 17 by 19 blocks and stories 5 blocks tall. Every house is picked at random: long, low ranch houses (often with a garage and a driveway up to it), bungalows, colonials, foursquares and cottages, with stoops, porticos, full-width or wraparound porches, and bedrooms with beds. Civic buildings, parks and orchard plots sit centered on the same lots.
  - **It never tears down anything that was there.** Player builds and village houses keep their shape but are made over in the era's style, the way Westview's own buildings became sitcom houses: every block takes on the part it plays (wall, trim, roof, window, door, floor, fence) and that part's era look. A building that stays becomes what it was in the town: the library, the church, a shop, a barn. Farms become flower gardens. Chests, beds, furnaces, workbenches and the like are left just as they are. The town is laid out around everything else that isn't natural ground. In a village, the paths become streets and new houses fill the empty lots.
  - **Growing the Hex extends the town** as the wall moves out. Shrinking it takes down whatever ends up outside.
- **Era makeovers:** homes, streets and furniture are built from era blocks that restyle with the era. That's picket-fence pastels in the 1950s, wood paneling and earth tones in the 1970s, bold colors in the 1980s, and beige modern in the 2000s. An era change sweeps through the town with a channel-change flicker.
- **It exists only while the Hex stands.** Every block the Hex builds remembers what was there before. When the Hex collapses, the inward-moving wall takes the town down with it and leaves the land as it was. Only the caster's home outlasts it for a little while, glitching through the eras before it goes part by part (see the lifecycle above). Breaking a Hex-built block gives nothing, so the town can't be farmed for materials.
- The caster chooses on the Showrunner remote what their next Hex builds: nothing, just their home, a whole town, or a farmhouse among orchards in blossom with country lanes and a red barn, every tree it covers turned into a fruit tree.
- Villagers and wandering traders inside are townspeople too and wear era outfits like everyone else.

### The Showrunner remote

The caster's controls for their Hex, opened with the Showrunner key once the crown has mastered the Hex. It's styled as an old television remote in dark bakelite with round buttons that light scarlet, and it slides up beside the view without pausing anything, so every change is seen happening.

- **Channel:** the era.
- **A new episode every morning:** episodes mode on or off.
- **Time of day and weather** inside the Hex.
- **Name:** the Hex's name for its title cards.
- **Your home, somewhere new:** puts the remote down to place the home (see above).
- **Your next Hex builds:** what the next cast raises.
- A little display at the top shows the era and episode on air, or "off the air" with no Hex standing.

### Era decorations

Blocks that change model with the hex's era, with a channel-change flicker. Hex homes are furnished with them. Outside a hex they show the **present-day** version.

- **TV:** wooden console set to flat screen
- **Music:** tube radio, record player, boombox, smart speaker
- **Telephone:** rotary dial to cordless
- **Kitchen:** fridge, stove, toaster
- **Living room:** couch, armchair, lamps
- **Wall clock and picture frames**
- **Posters** with a fake ad for each era
- **A parked car** that changes model every era

---

## 8. Art direction

### Palette: chaos magic

| Role | Hex |
|---|---|
| Core (hot center) | `#FFE6EC` |
| Bright scarlet | `#FF3355` |
| Scarlet | `#E0143C` |
| Crimson | `#A50D2C` |
| Wine | `#5C0717` |
| Shadow | `#1C0307` |

Darkhold corruption pulls the palette toward `#3A0010` and `#0B0004`, with sickly `#9B1B30` highlights and black smoke.

### UI

**Elegant and minimal:** thin lines, soft scarlet glow, subtle runes, smooth easing. The energy bar is a thin glowing bar just above the experience bar, which fades away when full.

### Assets are code

- **Textures:** pixel art generated by scripts from the shared palette (`tools/`), so one palette edit restyles the whole mod.
- **Models:** authored as files in the repo.
- **Sounds:** synthesized by scripts, optionally layered with public-domain (CC0) recordings.
- **Licensing:** only CC0 or permissively licensed material, every source credited in `CREDITS.md`. No ripped film, comic or game assets.

---

## 9. The quality bar

### Animation

- Anticipation before every cast, follow-through after, overlapping secondary motion (capes, coat tails, energy wisps).
- No linear motion. Every tween has an easing curve.
- Impact frames: brief hit-stop, flash, and camera feedback on big hits.
- First person is posed per spell: hands, gestures, glowing fingers. Third person uses full-body casting animations.

### Effects

Every power is built from layers, timed together:

1. **Core:** the bright hot center.
2. **Glow:** soft falloff around it.
3. **Wisps:** flowing noise-driven tendrils.
4. **Sparks and embers.**
5. **Trails and ribbons** that follow hand movements.
6. **Distortion:** heat-haze style refraction.
7. **Light:** glow on nearby surfaces.
8. **Screen:** vignette pulses, a touch of chromatic aberration on big moments.
9. **Sound:** layered, with variations so nothing repeats.

Glows are pure added light: they must never hide or smear what is behind them. They test depth but never write it. Without improved transparency, world glows are drawn last, after water, clouds and weather. With it, they only report their closest depth to the transparency system, never its depth range or opacity.

Added light alone washes out to pink against a bright sky. So magic that must keep its scarlet in daylight (the shield, the hover pad, later the Hex) has a **tint** under its glow. Without improved transparency, the tint filters what is behind it the way red glass does. With it, the tint is blended, darker and thinner, since blending is the closest the transparency system allows. Tints follow the same depth rules as glows. Magic laid out in a plane is drawn from both sides.

### Performance and accessibility

- Per-effect particle budgets, pooling, distance-based detail, culling.
- **Quality presets:** Low, Medium, High, Ultra.
- **Accessibility:** reduce flashing, reduce camera shake, reduce screen effects, instant transformations.

---

## 10. Technical architecture

```
common/     All gameplay, rendering, UI, audio. Compiles against vanilla only.
neoforge/   Thin layer: entry point, registration, events, networking glue.
fabric/     Thin layer: same, for Fabric.
tools/      Asset generators (textures, sounds).
docs/       This document.
```

- **Platform services** (Java `ServiceLoader`): the common code calls interfaces; each loader provides the implementation.
- **Data:**
  - **Crown item components:** mastery, hex link.
  - **Player attachments:** energy, cooldowns, suit state, corruption, dreamwalk state.
  - **World saved data:** hexes, restyled blocks and their originals.
- **Networking:** vanilla custom payloads with stream codecs, registered per loader.
- **Rendering:** our own effects engine (particles, ribbons, beams, glow sprites) on custom render pipelines; post-processing for the hex and distortion.
- **Animation:** Player Animation Library for body poses; our own easing/tween library; procedural cloth for capes.
- **Config:** our own, in common, with a custom-styled settings screen (Fabric has no built-in config).
- **Data generation:** all models, recipes, loot tables, tags and translations are generated from code.

### Testing

- Every feature is checked on **both loaders**, in single player and on a dedicated server.
- Compatibility: Sodium, Iris shader packs (OpenGL), and the Vulkan backend.

---

## 11. Roadmap

| # | Milestone | Contents |
|---|---|---|
| M0 | Setup | Java 25, two-loader project, design doc, git |
| M1 | Foundation | Crowns, recipes, equipment, item components, player data and sync, networking, config, keybinds |
| M2 | Render core | Render pipelines, effects engine, post-processing, easing and tween library |
| M3 | Suit-up | Both costumes, dissolve transformation, physics capes, first-person gloves |
| M4 | Casting core | Spell wheel, energy HUD, cooldowns, mastery, Chaos Bolt at full polish (sets the bar) |
| M5 | Crown spells | The remaining seven spells |
| M6 | The Hex | Lifecycle and the static wall, the era view, homes and the town, residents and outfits, kicking, rewrites, sky, restyling |
| M7 | Era content | Decorations, era makeovers, title cards, era audio, laugh track, episodes |
| M8 | The Darkhold | Item, corruption, dreamwalking |
| M9 | Release polish | Compatibility, performance, accessibility, localization, mod pages, publishing |

---

## 12. Open questions

- Public name for the release (and whether to use Marvel names in it).
- More Darkhold spells beyond dreamwalking.
- Exact numbers: energy costs, mastery curve, hex radius limits.
- Default keybinds (avoiding common conflicts such as voice chat mods).
- Final crown and Darkhold recipes.
