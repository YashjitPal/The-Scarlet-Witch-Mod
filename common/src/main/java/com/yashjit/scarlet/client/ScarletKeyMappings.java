package com.yashjit.scarlet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yashjit.scarlet.Scarlet;
import java.util.List;
import net.minecraft.client.KeyMapping;

public final class ScarletKeyMappings {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Scarlet.id("main"));

    public static final KeyMapping SUIT_UP = new KeyMapping("key.scarlet.suit_up", InputConstants.KEY_G, CATEGORY);

    public static final KeyMapping SPELL_WHEEL = new KeyMapping("key.scarlet.spell_wheel", InputConstants.KEY_R, CATEGORY);

    /** Unbound to begin with: a double tap of jump does the same. */
    public static final KeyMapping LEVITATE = new KeyMapping("key.scarlet.levitate", InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** The remote for a caster's Hex: its era, episodes, sky, name, and what the next one builds. */
    public static final KeyMapping SHOWRUNNER = new KeyMapping("key.scarlet.showrunner", InputConstants.KEY_H, CATEGORY);

    /** Unbound to begin with: the settings are also a click away in the corner of the game's options. */
    public static final KeyMapping SETTINGS = new KeyMapping("key.scarlet.settings", InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** Held to wind back the last seconds inside a caster's Hex. Unbound to begin with: the remote has the button too. */
    public static final KeyMapping REWIND = new KeyMapping("key.scarlet.rewind", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private ScarletKeyMappings() {
    }

    public static List<KeyMapping> all() {
        return List.of(SUIT_UP, SPELL_WHEEL, LEVITATE, SHOWRUNNER, REWIND, SETTINGS);
    }
}
