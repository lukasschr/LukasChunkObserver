package com.github.lukasschr.lukaschunkobserver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mod entry point. Tracks how long each player stays in a chunk and writes finished
 * sessions to the {@link ChunkActivityStorage} database.
 */
public class LukasChunkObserver implements ModInitializer {

    /** Mod id, also used as the logger name. */
    public static final String MOD_ID = "lukaschunkobserver";

    /** Shared logger for the mod. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String DB_FILE_NAME = "lukaschunkobserver.sqlite";

    /** A player's ongoing session within one chunk, started at enteredAt. */
    private record ChunkSession(String dimension, int chunkX, int chunkZ, long enteredAt) {}

    /**
     * Per-player open session. Only ever touched from the server thread
     * (tick / disconnect / server-stop all run there), so no synchronization is needed.
     */
    private final Map<UUID, ChunkSession> activeSessions = new HashMap<>();

    private ChunkActivityStorage storage;
    private long minDurationMs;

    /** Registers the server lifecycle, tick and player-disconnect handlers. */
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerDisconnect(handler.player));
    }

    /**
     * Loads the config and opens the activity database for the starting server.
     *
     * @param server the starting server
     */
    private void onServerStarting(MinecraftServer server) {
        ModConfig config = ModConfig.load();
        this.minDurationMs = config.minDurationMs;
        Path dbFile = resolveDbPath(server, config);
        try {
            storage = new ChunkActivityStorage(dbFile);
        } catch (SQLException e) {
            LOGGER.error("Failed to open chunk activity database at {}", dbFile, e);
        }
    }

    /**
     * Flushes every open session and closes the database on shutdown.
     *
     * @param server the stopping server
     */
    private void onServerStopping(MinecraftServer server) {
        long now = System.currentTimeMillis();
        activeSessions.forEach((uuid, session) -> recordSession(uuid, session, now));
        activeSessions.clear();

        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException e) {
                LOGGER.error("Failed to close chunk activity database", e);
            }
            storage = null;
        }
    }

    /**
     * Records a session for every player that changed chunk or dimension since the last tick.
     *
     * @param server the ticking server
     */
    private void onEndServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String dimension = player.level().dimension().identifier().toString();
            ChunkPos chunk = player.chunkPosition();

            ChunkSession current = activeSessions.get(uuid);
            boolean movedOrNew = current == null
                    || current.chunkX() != chunk.x()
                    || current.chunkZ() != chunk.z()
                    || !current.dimension().equals(dimension);
            if (movedOrNew) {
                // Close the previous session (if any) before opening a new one for this chunk.
                if (current != null) {
                    recordSession(uuid, current, now);
                }
                activeSessions.put(uuid, new ChunkSession(dimension, chunk.x(), chunk.z(), now));
            }
        }
    }

    /**
     * Records the open session of a player leaving the server.
     *
     * @param player the disconnecting player
     */
    private void onPlayerDisconnect(ServerPlayer player) {
        ChunkSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            recordSession(player.getUUID(), session, System.currentTimeMillis());
        }
    }

    /**
     * Persists a finished session, unless it was shorter than the configured minimum duration.
     *
     * @param uuid    the player the session belongs to
     * @param session the session being closed
     * @param leftAt  epoch millis at which the session ended
     */
    private void recordSession(UUID uuid, ChunkSession session, long leftAt) {
        if (storage == null) {
            return;
        }
        long duration = leftAt - session.enteredAt();
        if (duration < minDurationMs) {
            return;
        }
        try {
            storage.insert(uuid.toString(), session.dimension(), session.chunkX(), session.chunkZ(),
                    session.enteredAt(), leftAt);
        } catch (SQLException e) {
            LOGGER.error("Failed to write chunk activity for {}", uuid, e);
        }
    }

    /**
     * Resolves the database file path. An empty configured path yields the per-world default
     * {@code <world>/lukaschunkobserver.sqlite}; a custom absolute path is used as-is, a relative one
     * is resolved against the game directory.
     *
     * @param server the running server, used for the per-world default location
     * @param config the loaded config holding the optional custom path
     * @return the resolved database file path
     */
    private Path resolveDbPath(MinecraftServer server, ModConfig config) {
        String custom = config.databasePath;
        if (custom != null && !custom.isBlank()) {
            Path path = Path.of(custom);
            return path.isAbsolute() ? path : FabricLoader.getInstance().getGameDir().resolve(path);
        }
        return server.getWorldPath(LevelResource.ROOT).resolve(DB_FILE_NAME);
    }
}
