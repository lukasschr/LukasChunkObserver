package com.github.lukasschr.lukaschunkobserver;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * TOML config stored in {@code config/lukaschunkobserver.toml}, parsed with night-config.
 * Missing keys are filled with defaults (and written back with an explanatory comment),
 * so an existing file is never overwritten wholesale.
 */
public class ModConfig {

    private static final String FILE_NAME = "lukaschunkobserver.toml";
    private static final String KEY_DATABASE_PATH = "databasePath";
    private static final String KEY_MIN_DURATION_MS = "minDurationMs";

    /**
     * Path to the SQLite database file.
     * <p>
     * Empty (the default) means: use a per-world file at {@code <world>/lukaschunkobserver.sqlite}.
     * If set, an absolute path is used as-is; a relative path is resolved against the game directory.
     */
    public final String databasePath;

    /**
     * Minimum stay duration in milliseconds. Chunk stays shorter than this are discarded
     * instead of being written to the database.
     */
    public final long minDurationMs;

    private ModConfig(String databasePath, long minDurationMs) {
        this.databasePath = databasePath;
        this.minDurationMs = minDurationMs;
    }

    /**
     * Loads the config, creating the file and filling any missing keys with commented defaults.
     *
     * @return the loaded config
     */
    public static ModConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try (CommentedFileConfig config = CommentedFileConfig.builder(path).build()) {
            config.load();

            boolean changed = false;
            if (!config.contains(KEY_DATABASE_PATH)) {
                config.set(KEY_DATABASE_PATH, "");
                config.setComment(KEY_DATABASE_PATH,
                        " Path to the SQLite database file.\n"
                        + " Empty = per-world default (<world>/lukaschunkobserver.sqlite).\n"
                        + " Absolute path is used as-is; a relative path is resolved against the game directory.");
                changed = true;
            }
            if (!config.contains(KEY_MIN_DURATION_MS)) {
                config.set(KEY_MIN_DURATION_MS, 1000L);
                config.setComment(KEY_MIN_DURATION_MS,
                        " Minimum stay duration in milliseconds.\n"
                        + " Chunk stays shorter than this are discarded (not written to the database).\n"
                        + " Default: 1000 (1 second).");
                changed = true;
            }
            if (changed) {
                config.save();
            }

            String databasePath = config.getOrElse(KEY_DATABASE_PATH, "");
            long minDurationMs = config.getLongOrElse(KEY_MIN_DURATION_MS, 1000L);
            return new ModConfig(databasePath, minDurationMs);
        }
    }
}
