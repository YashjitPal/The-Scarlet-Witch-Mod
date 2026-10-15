# Changelog

## 0.1.11

- **The town comes down with its Hex wherever it stands**, as it used to. Out where no one was, a Hex drawn in or falling left its town standing there until someone went near it. Now the land just ahead of the wall is loaded as the wall comes, and the wall waits for it if it must, so every part of the town is gone as the wall goes by, whoever is there.
- **What towns left out where no one was goes back on its own**, a few chunks at a time while the server has time to spare, without anyone going near it. So does paint a fallen Hex left there.
- **The server keeps its pace while a big town goes up or comes down.** A town's share of each tick was worked out from the server's own tick times, which leave out the time it spends loading land between ticks, and on Fabric the town's own work too, so a big town could take more than there was and slow everything down. It now times each tick itself and takes only what is really left of it, and while the server is falling behind, it takes only the least it can and holds off loading more land.

## 0.1.10

- **The town keeps up with its wall again.** Since 0.1.8 a town had only a small, fixed share of each tick, so drawing a Hex in or letting it fall left its town standing outside the wall, coming down long after. Now what the wall passes goes first, with as much of each tick as the server can spare, and the wall waits for it if it has to: the town is gone as the wall goes by, drawn in or falling. Towns also build up to twice as fast, from whatever time each tick has to spare.
- **What a fallen town left where no one was goes back as soon as you're near it**, nearest first and many times faster than before.
- **Chaos bolts no longer jump back** while the server is behind: they fly at the server's own pace.
- **The wall is drawn only as far as the world is,** fading out at the edge of your render distance like everything else.
- **Out past where its caster can see, the show stops.** Townspeople there stand stock still, now and then glitching or jerking their heads like a scene stuck skipping. Anyone standing out there sees the picture drain and the signal fray, and hears the laugh track fall silent and every sound go far off. While the caster is away, it is like that all over their Hex.
- **Townspeople turn up the town's way.** Nothing comes out of the dark into a Hex any more, onto its roofs as often as not. Instead, now and then someone new turns up out of sight, at home in a house or out on a sidewalk, walk or lawn, a few about anyone at most, of whatever kind the land would have brought out there.

## 0.1.9

- **No land in the Hex is left as it was.** A lot a house won't go on is made into what suits its land. A pond is kept as it lies, with a railed boardwalk out over it to a deck. A slope, however steep, is laid in terraces held up by stone walls, with steps up through them, flowers along each, and a wall with steps along the street where it stands over or under the sidewalk. The edge of a drop is a lookout with a parapet, benches facing out and a telescope. A lot its street passes far over on a bridge, like a canyon's floor, is a ball court or a picnic grove.
- **More to the town.** Ball courts with hoops and painted lines, picnic groves with tables and a grill under shade trees, and out past the middle a filling station or two: pumps under a canopy in the era's colors, a car pulled up at them, and an office with a service bay.
- **More to the orchard.** Now and then a plot is a greenhouse with benches of potted plants, a chicken coop with a fenced run, a pumpkin patch with a scarecrow, or beehives among beds of flowers with a honey stand by the lane.
- **The woken hunt.** A townsperson you hit goes after you, or any player near, before anyone else. With no player about it goes after the other townspeople, and any it hurts wakes and joins it. The woken never turn on each other.
- **Townspeople sound like people all the way.** An enderman among them no longer gives itself away with a whoosh when it teleports or a cry when you look at it, and a witch drinks like anyone.

## 0.1.8

- **Big Hexes no longer slow the game down.** A town hundreds of blocks across could hold the server up until it ran at a third of its speed, so everything fell seconds behind. The town now does its building, taking down and making over a share of each tick at a time, however big it grows; it finds each part's blocks without going through all of them; and it never waits on land that is still being made. In a world with a Hex at its full size, its town over a million blocks, the server now keeps its pace while the Hex stands and while it's drawn in.
- **The wall no longer jumps back.** While the server was behind, the wall, the town glitching in and out and era changes would run on, then jump back a moment and go again. They now go at the server's own pace, and never back.
- **The whole Hex builds as it grows.** Its outskirts go up as the wall reaches them, a few parts at a time, without anyone having to go there. And what the wall takes back where no one is goes back as soon as anyone does, rather than standing outside the Hex.
- **Streets connect all through the town.** Each run of street from one crossing to the next is laid out as a whole, so every stretch meets the next. It follows the land where the land is near the line between the crossings, and otherwise keeps to the line: through a hill in a tunnel, lined, roofed and lit; over a valley, a canyon or a lake on a long, high bridge on piers. A street never runs under water any more: it's carried a block over it. A canyon or a ridge as narrow as a block of the town is crossed at the town's height either side, and crossings are decked over water and roofed over in a hill.
- **Every house can be come to from its street.** A lot is laid near its street's height, and its front comes down, or up, to the sidewalk in steps: the lawn, the walk up to the porch, and the driveway, which a car climbs like stairs. The street in front of your own home is laid level with it, wherever it rose, and climbs from there to the rest of the town.
- **Holes are covered over.** A pit or a cave's mouth under a street or a lot is capped over like the rest of the town's ground. A lot too steep for a house, at the edge of a drop, or with its street passing high over it on a bridge, becomes a garden instead of being left bare.

## 0.1.7

- **Cars rebuilt.** Every era's car is new: a body with wheel arches, doors, trim and lamps that glow; a cabin you can see out of, with seats, a dash with its gauges and a steering wheel that turns as you steer; wheels that roll and steer; and a body that tips to the slope it's on and leans out of its turns. It turns smoothly however fast the game draws, the wheel comes round as you hold it, it picks up speed quickly and coasts further, and its nose and tail stop at walls instead of going through them while it still climbs steps and stairs. Click anywhere on it to get in. The view from behind stands back to take it all in, and the jump key sounds its horn.
- **Streets run along the land, and bridge what they can't.** Each stretch of street now climbs or falls evenly to meet the next, a step at a time, instead of lying flat and leaving a ledge or a cliff where it can't reach. Where the land drops away under it or it meets a river, it carries on over a bridge on piers, railed both sides, in the era's colors. An orchard's lanes do the same, up steps the color of the earth and over timber bridges.
- **What the Hex makes, the Hex keeps.** Set dressing taken out of thin air is the Hex's own, and says so. Carried out past its wall, or left outside as the wall is drawn in or falls, it glitches in its slot and is gone, from your pockets, an open chest, a box you carry, or the ground; get back inside in time and it stays. Put down inside, it goes when it's outside, and broken there it gives back the Hex's own again. Anyone can still craft the same things to keep. The panel now shows for any caster in their own Hex wearing their crown.
- **Change what a standing Hex builds.** Choosing on the remote what your Hex builds now makes over the one standing: a town into an orchard and back again, keeping your home; just your home; or nothing. From nothing, your home rises where you cast the Hex, and the rest around it.
- **Drawing the Hex in past your home** no longer takes it down at once and raises it again, cut off at the wall, with your magic reaching for it: it's let go, steps back through the eras on its own and comes apart the way it went up, as when the Hex falls. Grow the Hex back over it and it rises again whole. The same goes for an orchard's farmhouse.
- **Chaos Bolt** hurts whatever is within a block of where it strikes, not only what it hits, and flies five times as far before it fizzles out.
- **Magic takes the Hex's era** like everything else in it: a bolt or a shield is grey in black and white.

## 0.1.6

- **Bigger Hexes.** A Hex now spreads 72 blocks out when cast, three times as far as before, and holding the cast grows it as far as 576, three times the old limit. A big Hex falls and changes era a little more slowly so its town still goes block by block, and someone thrown out of one flies no faster than before, however far they have to go.

## 0.1.5

- **Your home always rises where you stand.** Casting on ground a town of yours stood on before, away from where its home was, no longer brings that old home back somewhere else instead of building yours: a new home rises around you, and the old town comes back around it where it stood, its old home now an ordinary house. Cast standing in a home you raised before and that one comes back. A house is made into your home only when you stand in it or at its door, not when it's just nearby.

## 0.1.4

- **Set dressing.** Standing in your own Hex, a panel beside your inventory holds every piece of era furniture and the car. Click one to take it out of thin air: 4 chaos energy for furniture, 15 for the car.
- **Furniture that works.** Get in a car and drive it, with someone beside you: it climbs steps and stairs but not walls, and the town's own cars can't leave their Hex. The fridge keeps as much as a chest, and spills it when broken or when its Hex takes it back. A stove that's on cooks raw meat, fish, potatoes and kelp held to it. The wall clock tells you the time, the Hex's own hour if its caster has set one.
- **Better-looking cars:** a raked windshield and rear window, mirrors, door seams and handles, a plate in the era's colors, and lights that glow.
- **Townspeople have names.** Over their heads they go by sitcom names instead of "Creeper" or "Zombie"; a name tag still wins.
- **Streets climb hills** in flights of steps in the road's own colors, instead of leaving ledges to jump, and the steps change with the era.
- **A different home every time** a Hex is cast somewhere new: eight kinds now, three of them single-story, and never one of your last three. A town you raised before still comes back just as it was.
- **Walking through a parted wall** no longer rolls the picture over as if you'd crossed the wall, and from outside, the rest of the Hex now stands beyond the opening.
- **Fixed:** the Showrunner remote's buttons, the settings screen's switches and the Darkhold's destinations didn't answer the mouse, because Minecraft 26 numbers the mouse buttons differently.
- **Fixed:** breaking a fridge dropped two.

## 0.1.3

- **Crashing out of the Hex.** Someone thrown out of a Hex crashes straight through whatever stands in their way inside it, bursting it open in a crater as wide as a creeper's blast, and the Hex mends it the way it mends any blast, closing in from the edges over about ten seconds. Outside the wall they break nothing.

## 0.1.2

- **Every power in creative.** A crown worn in creative mode has every power at once, at full strength, and its magic never runs dry; cooldowns still pace the spells. Casting in creative adds nothing to the crown's mastery, so back in survival it holds only what it earned there. Darkhold spells still need the book.

## 0.1.1

- **Magic leaves from her hand.** A cast now leaves the caster's palm once the strike has their arm at full reach, where the blast itself comes from, instead of at the end of the wind-up while the hand is still drawn back low at their side.

## 0.1.0

The first version of Scarlet Witch: chaos magic inspired by the Scarlet Witch, for Minecraft 26.3.

- **Two crowns:** the Scarlet Witch's tiara and the Scarlet Warlock's crown. Press G to weave the costume on in scarlet threads.
- **Eight spells** to master on the spell wheel (hold R): Chaos Bolt, Chaos Shield, Levitation, Telekinesis, Red Mist, Shockwave, Mind Control and Rune Trap.
- **The Hex:** a wall of static that turns everything inside it into a sitcom. Six eras from 1950s black and white to the present day, a town that builds itself, townspeople, title cards, blasts that mend themselves, and the Showrunner remote (H), which can even rewind the last ten seconds.
- **The Darkhold:** corruption that darkens your magic, and Dreamwalking into creatures in any dimension.

Known issue: Iris shader packs crash on NeoForge as a world loads. The crash is inside Iris 1.11.7; Iris on Fabric works.
