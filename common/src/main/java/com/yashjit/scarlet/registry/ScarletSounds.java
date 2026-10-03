package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's own sound events. What each one plays is chosen in {@code sounds.json}, from the game's own recordings
 * pitched and mixed for the purpose.
 */
public final class ScarletSounds {

    /** The Darkhold speaking to whoever it has a hold on. */
    public static final Supplier<SoundEvent> DARKHOLD_WHISPER = register("darkhold.whisper");
    /** Many voices at once, once it has a strong hold. */
    public static final Supplier<SoundEvent> DARKHOLD_VOICES = register("darkhold.voices");
    /** A page of it turning. */
    public static final Supplier<SoundEvent> DARKHOLD_PAGE = register("darkhold.page");
    /** A heart pounding as the darkness closes in. */
    public static final Supplier<SoundEvent> DARKHOLD_HEARTBEAT = register("darkhold.heartbeat");
    /** The darkness itself closing in. */
    public static final Supplier<SoundEvent> DARKHOLD_DREAD = register("darkhold.dread");

    private ScarletSounds() {
    }

    public static void bootstrap() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return Services.REGISTRY.register(BuiltInRegistries.SOUND_EVENT, name, () -> SoundEvent.createVariableRangeEvent(Scarlet.id(name)));
    }
}
