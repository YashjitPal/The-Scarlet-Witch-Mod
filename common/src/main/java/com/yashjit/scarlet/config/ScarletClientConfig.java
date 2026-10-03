package com.yashjit.scarlet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.platform.Services;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Visual and accessibility settings. Stored as JSON so both loaders share one format; a custom-styled settings screen
 * will edit it later.
 */
public final class ScarletClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "scarlet-client.json";

    private static ScarletClientConfig instance = new ScarletClientConfig();

    public EffectsQuality effectsQuality = EffectsQuality.HIGH;
    public boolean instantTransformations = false;
    public boolean reduceFlashing = false;
    public boolean reduceCameraShake = false;
    /** Softens what is drawn over the whole view, like the Darkhold's veins beating at its edges. */
    public boolean reduceScreenEffects = false;
    /** While your home rises around you as you found a Hex, the view circles it, coming back to your eyes for the burst. */
    public boolean cinematicFounding = true;
    /** Inside a Hex in an older era, everything sounds as if through an old television's speaker. */
    public boolean eraAudio = true;

    public static ScarletClientConfig get() {
        return instance;
    }

    public static void load() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                ScarletClientConfig loaded = GSON.fromJson(reader, ScarletClientConfig.class);
                if (loaded != null) {
                    instance = loaded.sanitized();
                }
            } catch (IOException | JsonParseException e) {
                Scarlet.LOG.warn("Could not read {}, using defaults", file, e);
            }
        }
        save();
    }

    public static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            Scarlet.LOG.warn("Could not write {}", file, e);
        }
    }

    private ScarletClientConfig sanitized() {
        if (effectsQuality == null) {
            effectsQuality = EffectsQuality.HIGH;
        }
        return this;
    }

    private static Path path() {
        return Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }

    public enum EffectsQuality {
        LOW,
        MEDIUM,
        HIGH,
        ULTRA
    }
}
