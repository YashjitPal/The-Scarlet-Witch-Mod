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
- **Look:** a disc of churning red energy drawn pixel by pixel on its own plane.
  - Three arms of energy swirl in toward the middle through a thin red haze, and you see through it between them.
  - Its rim burns, light running around it, with tongues of it licking outward like fire.
  - Every blow sends a ring of light out across it from a white-hot sparkle where it landed.
  - Strands of energy feed it from both palms, beads of light running along them.
  - It whirls in fast as it is raised and dithers away as it is lowered, and a low hum swells and fades with it. When it breaks it bursts into tumbling chunks of itself that burn out as they fly.
- **Pose:** the arms tremble with the strain and are knocked back by each blow.
- **First person:** your own shield sits further out and fainter, so it frames the view instead of covering it.

### Levitation (rank 2, toggle)

- **Turn on and off** by casting it from the wheel, or by **double-tapping jump** (as in creative flight). Fly with the creative flight controls.
- **Cost:** a gentle drain while airborne. Landing ends it. Switching it off in mid-air, or running out of energy, lowers you gently with slow falling.
- **Look:**
  - A pad of magic churning under the feet, white-hot at its heart and licking out at its rim, ringed with six slowly turning marks.
  - Strands of it winding up the legs, with sparks circling the feet and dust falling away.
  - A shock ring racing across the ground at lift-off, white-hot at its leading edge, and a softer one at touch-down. The pad and the rings lie on the ground's own grid of texels.
- **Pose:** the arms float out from the sides and the legs hang loose, with a gentle hover bob. The body leans into the flight and the arms sweep back as you pick up speed.

### Magic on the arms

Whenever powers are in use, scarlet wisps spiral around the hands and forearms, light running down them, and a ball of magic churns in each palm, its rim licking out like flame, flaring brighter at the moment of casting. Visible in first person on your own hands, where its pixels match the arm's own texels. Drawn by our effects engine, not vanilla particles.

They burn steadily while the shield is held and smolder while levitating.

### Mind control

- Look at a creature within range and hold the cast: scarlet tendrils curl from your hands into its head, a crown of two turning rings gathers round it, and its eyes burn red. Once you are inside, a single thread runs from your brow to its head, swelling with each beat of a heart.
- **Mobs and animals:** your view moves into the creature and you control it directly: move, jump, attack. Your own body stands channeling and is vulnerable. Release to return.
- After release, the mob stays loyal to you for a while.
- **Players (short and fair):** a few seconds of control. Their screen tints red and they can struggle free by mashing keys. Long cooldown. Servers can turn it off.
- Dreamwalking is the Darkhold's long-range version that works across dimensions.

---

## 6. The Darkhold

- **Craftable, late game:** a book in the center, crying obsidian on its four sides and echo shards from Ancient Cities at the corners. The recipe unlocks when you pick up an echo shard. One to a stack, epic, and it doesn't burn.
- **The look:** as in the films, an ancient grimoire bound in stone and metal more than leather: a slab of weathered dark stone with a raised, chipped frame, the roots and branches of a great tree carved spreading from a stone set in bronze that glows sickly red, garnet mosaic tiles beside it, and a spine of tarnished bronze cut with hieroglyphs. Inside, old vellum written close in a dark hand around red runes. It is carried shut in the hand, in the first person too, where your arm is drawn holding it.
- **Read, it floats** (Multiverse of Madness, WandaVision's last scene): it rises out of the hand and hangs open before the chest, bobbing and swaying, turning a page by itself each time one is read, its stone and runes burning, while both hands hold it there from either side without touching it, weaving slowly, the head bowed over it. Sparks and a little black smoke rise off its pages. In the first person it floats below the middle of the view between your raised hands. Let go and it closes and sinks back into the hand.
- **Reading it:** hold use. A page turns every three seconds and each takes **4%** of you, so twenty-five pages take all of you. The first page tells you what it offers. The tooltip shows how much it has taken.
- **Carry it to use its dark spells.** Reading it and casting its spells **corrupt** you, 0 to 100%, kept through death:
  - **darker magic for everyone who sees it:** from a tenth of the way in, every spell's light shifts from scarlet toward black-crimson with sickly highlights, its glass toward black, and black smoke rises off it and off your hands. Bolts carry a pall of dark with them and burst dark, so a corrupted blast reads as black even against a bright sky. The energy bar darkens with it;
  - **dark veins** creep in at the edges of your sight, further the deeper it runs, beating with your heart; they flare while you read and as the dark closes in. They soften with *reduce screen effects*;
  - **whispers** all around you, now and then when you merely carry the book, every few seconds once it has most of you, in many voices near the end;
  - **side effects** past halfway (a server option can turn them off): from 50% you hunger faster, from 60% the whispers wake you whenever you try to sleep, and from 70% the dark closes in now and then, with a heartbeat;
  - it **fades** once you stop: a minute after you last read or cast from it, and then all of it in about forty minutes.
- Admins can see and set it: `/scarlet corruption <player> [set <percent>]`.
- Dark spells: **Dreamwalking** first. More to be decided. The book's spells take their places on the spell wheel, set apart in black-crimson, only while it is carried, and can only be cast while it is.

### Dreamwalking

- **Casting it** (mastery VI, sat on solid ground, 30 energy to begin): every dimension opens up to choose from, each a window into the dark showing what it is made of and where in it your spirit would arrive. That is where you last stood there, else where you would wake from death, else by the world's spawn, else where a portal from the spawn would come out (or the End's landing). Click one or press its number.
- Your **body sits down cross-legged** and rises a little off the ground over two seconds, hands held open over the knees and wreathed in magic, as wisps gather and turn about it and the dark closes in on your view (WandaVision finale / Doctor Strange). A blow while it settles breaks the meditation.- Your **spirit goes into the creature nearest where it arrives**, even in the **Nether or End** (Minecraft's dimensions stand in for the multiverse), as long as it is not already held or carried, nor a boss. The view opens out of the dark into its eyes and you steer it as with Mind Control: walk, sprint, jump, fly if it flies, and strike the way it does. Its eyes burn scarlet for everyone who sees it, and embers rise off its head. Its name and health show at the top of your view, which is edged in black-crimson. Should no creature be there, you get up again.
- Your **body stays behind**, hovering cross-legged in a slow swirl of scarlet wisps over a faint ring of light, sparks drifting off it and its hands, in your skin, clothes, crown and costume. Other players can see it, and hostile creatures that catch sight of it go for it. Its ground is kept loaded and moving while you are away.
- If your **body gets hurt, your spirit snaps back** into it instantly, in time to defend yourself.
- **Waking:** press use. It also ends when the creature dies or goes, when your energy runs out (being away drains 1.6 a second), and when you leave the game. However it ends you come back to your body with a flash and get up, and the spell rests ten seconds.
- **Corruption:** 2% as the spirit leaves, then 0.1% each second away, 6% a minute.
- The spirit is you as a spectator looking out through the creature, so it is the creature's surroundings your game loads and shows. Nothing can see or hurt it, a spectator's menu for going elsewhere is closed to it, and its travels don't count as having been anywhere for advancements. Your body's place and your game mode are remembered while you are away, so leaving the game, or even the server going down, still brings you back to your body.
- Servers can turn dreamwalking off.

---

## 7. The Hex

The centerpiece. Inspired by Westview.

### Lifecycle

- Cast it and it **spreads out from you** in the iconic static wave, then **stays where you cast it**.
- The caster can **walk out** and it stays up.
- The caster can **grow or shrink** it anytime.
- It **collapses if the caster stops wearing the crown** (death, stolen, or taken off). A few seconds of flickering warning first, so an accident isn't a disaster.
- When it collapses, **the wall closes in, slowly at first and gathering speed, and everything it passes over changes back**, like the finale. The bigger the hex, the longer the fall: about 7 seconds for a freshly cast hex, up to 16 for the largest.
- **The caster's home goes last, and not with the wall.** When the falling wall reaches it, the house holds on alone, even after the hex is gone. For about 10 seconds it steps back through the eras one at a time, the newest first, down to black and white, like the show rewinding to its first episode: each era sweeps round the house and spirals up it, a line of scarlet running ahead of it, the blocks turning to that era's as it reaches them and the picture of the house turning texel by texel with them, then holds a moment before the next, with the sound of a set changing channels as each begins. Red static crawls over it all the while. The caster's clothes turn with it, each era's as its sweep comes halfway round. Then it goes part by part, still in black and white, the way it went up but backward: the rooms empty, the yard and porch go, then the roof, the walls, the timber frame and the floor. Each block glitches out red as it goes, and last of all the land comes back as it was. It takes about as long as it took to build, some 15 seconds. Casting a new hex over it, or by it, puts it all back at once.
- One hex per caster; while it stands it reserves part of the caster's energy bar.
- **Shape: a hexagon**, like the Westview anomaly on S.W.O.R.D.'s map. Seen from above it is a regular hexagon, with flat walls facing north and south and corners pointing east and west. The six walls stand straight up without end, and nothing closes over the top. Seen from the ground, they rise high over the town (taller for a bigger hex) and fade away into the sky. The corners, where one wall meets the next, glow. The radius is measured to the middle of a wall, so the corners stand about 15% further out. The hex is everything within its walls, as high and as deep as the world goes, so a hex cast on a hill still comes down to the valley.

### Look

- **From outside:** a glitching wall, made of the same glitches the Hex's blocks show as it writes them. It is mostly clear, under the faint snow of a dead channel, and all of it is laid out on a grid of eighths of a block: bars of red light tear across it, red pixels flicker over it (now and then two side by side), a patch of it now and then breaks up hard for a few frames, and white-hot scanlines with scarlet trails roll down it. Struck, it glitches harder around the blow. It is brighter where seen edge-on and up its corners. Through it, the town looks just as it really is, in normal colors: the era is only seen from inside. While spreading or collapsing it burns scarlet, and when the era changes the wall flares with static.
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

- **Changing era** spreads out from the middle of the hex to its wall over a few seconds, a thin line of scarlet glittering along its front. Into black and white or out of it, the color goes or comes the way Minecraft would paint it: texel by texel over every block's own 16 by 16 texture, each texel turning at its own moment in a crisp ordered dither and glinting warm white as it does. A pale wash comes first, as if tinted by hand, then the colors for real, the most vivid first and reds ahead of the rest, the dull walls and roads last, each block in its own time; going back to black and white, the reds linger longest. The sky turns last, over the last stretch before the wall. Between two eras in color the picture turns over the same way.

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

- **Anyone can enter.** Walking through the wall, person or creature, flares it red where they pass, the red soaking out through it around them as rings run out across it, as a chaos blast striking it does, a little gentler. The caster can **kick** someone out: they get flung out through the wall, Monica-style, with a spectacular animation (the wall bulges and ripples, sparks, a boom). They can walk back in.
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
- **Blasts mend themselves.** Blocks break and place as normal inside the hex, but whatever a creeper, TNT, a fireball or any other blast breaks inside the wall comes back, the hex rewinding it: a moment after the blast the crater closes back in from its edge to where it went off, slowly, over about 10 seconds, each block flickering in as an outline of scarlet light and snapping back in glitching red, as the caster's home is written. Nothing drops from what it broke, and a chest comes back with everything that was in it. TNT a blast sets off is used up, no fire catches inside the wall, and paintings and item frames inside aren't broken at all. What lies outside the wall stays broken, so a blast against it leaves a crater cut clean at the wall. Something put in a gap before it mends stays there.
- **Sky:** the caster controls time of day and weather inside.
- **Restyle blocks:** hold a block in your off hand and sweep your aim to paint blocks into it. Your other arm flings out at what you paint and a straight stream of scarlet magic pours from the palm, churning as it flows out, white-hot down its middle, tendrils of it twisting round it, splashing where it lands. Everything changes back when the hex falls. **Breaking a restyled block gives the original block**, so the hex can't be used to farm materials.
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
  - While it goes up, the caster raises both arms to it and a stream of scarlet magic pours from each palm, jumping from block to block as they land.
  - It builds itself the way the house does in WandaVision: scarlet light traces the lot, the timber frame rises from the ground, then the walls, windows and roof close in, and the rooms furnish themselves.
  - When casting, the Hex can start from your home: the house builds first, written a block at a time around the caster as they float in the middle of it, each block glitching in as it lands; then they come down onto its floor and the Hex bursts out of them where they stand and spreads.
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
- **Sounds:** Minecraft's own, layered and pitched in `sounds.json`; the mod ships no recordings.
- **Licensing:** only CC0 or permissively licensed material, every source credited in `CREDITS.md`. No ripped film, comic or game assets.

---

## 9. The quality bar

### Animation

- Anticipation before every cast, follow-through after, overlapping secondary motion (capes, coat tails, energy wisps).
- No linear motion. Every tween has an easing curve.
- Impact frames: brief hit-stop, flash, and camera feedback on big hits.
- First person is posed per spell: hands, gestures, glowing fingers. Third person uses full-body casting animations.

### Effects

The powers are pixel art, drawn the way Minecraft draws everything: in square pixels a sixteenth of a block across, the size of a texel on a block or a skin, each a flat color from a seven-step ramp running from white-hot through scarlet and crimson down to shadow. Nothing soft, and nothing finer. They fade by stepping down the ramp and thinning out in an ordered dither, as pixel art does, rather than by blurring. Their patterns change a tick at a time, like an animated block texture, while the magic itself moves smoothly.

Every power is built from layers, timed together:

1. **Core:** white-hot pixels at its heart.
2. **Body:** the churn of it, smoke or flame in scarlet and crimson.
3. **Wisps:** tendrils and strands curling as lines of pixels, light running along them.
4. **Sparks and embers:** single pixels flung off it, stepping down the ramp as they die.
5. **Trails** that follow hand movements.
6. **Screen:** vignette pulses on big moments.
7. **Sound:** layered, with variations so nothing repeats.

- **Facing the camera:** wisps, tendrils, bursts and fog are plotted onto a grid that faces the camera, the way a particle's sprite does, so they land on whole pixels and a curling wisp reads as a line of them rather than a smear. The grid is pinned at the far end of what it draws, so its pixels are the size of a texel there and come out finer toward the camera, never coarser. Nearer than three blocks it is pinned further off along the same line of sight, so pixels never swell into blocks across the view, and fog thins out right round your own eyes.
- **Lying flat:** the Rune Trap's sigil, the hover pad and shock rings lie on the world's own grid of sixteenths, so their pixels line up with the texels of the ground under them.
- **Blended, not added:** pixels are blended over the world rather than added to it like light, so they can be as dark as crimson over snow and keep their scarlet in daylight. They test depth but never write it, so the world and the caster still hide whatever passes behind them. Magic laid out in a plane is drawn from both sides.

Nothing is drawn over a player's eyes. A skin's eyes can be anywhere on its face, or not there at all, so there is no knowing where to put them. Creatures' eyes, which are always in the same place, can burn red: a white-hot pixel each with a red glint round it, drawn a little in front of the face so the face never hides them.

The Hex's glitches (section 7) are light on a grid of eighths of a block rather than pixels. Glows are pure added light: they must never hide or smear what is behind them. They test depth but never write it. Without improved transparency, world glows are drawn last, after water, clouds and weather. With it, they only report their closest depth to the transparency system, never its depth range or opacity. Where a glow must keep its scarlet in daylight, or something held by Telekinesis reddens through, a **tint** filters what is behind it the way red glass does. With improved transparency, the tint is blended, darker and thinner, since blending is the closest the transparency system allows. Tints follow the same depth rules as glows.

Under an Iris shader pack the magic keeps its own shaders: none of its pipelines is handed to one of the pack's programs. A pack's lightning program can take dark colors for a lightning bolt and recolor them, and its basic program lights what it draws like solid ground, dark at night. Drawn with its own shaders, the magic keeps its colors and only takes the pack's grading, like everything else on screen. Iris logs a line for each pipeline it has no program for; that is expected.

### Performance and accessibility

- Per-effect particle budgets, pooling, distance-based detail, culling.
- **Quality presets:** Low, Medium, High, Ultra.
- **Accessibility:** reduce flashing, reduce camera shake, reduce screen effects, instant transformations.
- **Settings screen:** all of these, and the Hex's cinematic founding and era audio, on the mod's own screen, styled like its magic: a dark panel rimmed in scarlet, switches that light up, the quality as four segments, and what each setting does shown as you hover it. The arrow keys, Enter and Space work it too. Every change takes at once and is saved. It opens from a tiara button in the corner of the game's Options screen on both loaders, from the mod list on NeoForge, and from a key (unbound to begin with).

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
