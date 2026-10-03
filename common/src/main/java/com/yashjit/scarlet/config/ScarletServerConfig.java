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
 * Rules a server sets for everyone on it. Stored as JSON so both loaders share one format.
 */
public final class ScarletServerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "scarlet-server.json";

    private static ScarletServerConfig instance = new ScarletServerConfig();

    /** Whether Mind Control can take hold of players, and for how many seconds at most. */
    public boolean mindControlPlayers = true;
    public int mindControlPlayerSeconds = 5;

    public static ScarletServerConfig get() {
        return instance;
    }

    public static void load() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                ScarletServerConfig loaded = GSON.fromJson(reader, ScarletServerConfig.class);
                if (loaded != null) {
                    instance = loaded.sanitized();
                }
            } catch (IOException | JsonParseException e) {
                Scarlet.LOG.warn("Could not read {}, using defaults", file, e);
            }
        }
        save();
    }

    private static void save() {
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

    private ScarletServerConfig sanitized() {
        mindControlPlayerSeconds = Math.clamp(mindControlPlayerSeconds, 1, 30);
        return this;
    }

    private static Path path() {
        return Services.PLATFORM.getConfigDir().resolve(FILE_NAME);
    }
}
