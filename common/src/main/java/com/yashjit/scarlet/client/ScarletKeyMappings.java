package com.yashjit.scarlet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yashjit.scarlet.Scarlet;
import java.util.List;
import net.minecraft.client.KeyMapping;

public final class ScarletKeyMappings {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Scarlet.id("main"));

    public static final KeyMapping SUIT_UP = new KeyMapping("key.scarlet.suit_up", InputConstants.KEY_G, CATEGORY);

    public static final KeyMapping SPELL_WHEEL = new KeyMapping("key.scarlet.spell_wheel", InputConstants.KEY_R, CATEGORY);

    private ScarletKeyMappings() {
    }

    public static List<KeyMapping> all() {
        return List.of(SUIT_UP, SPELL_WHEEL);
    }
}
