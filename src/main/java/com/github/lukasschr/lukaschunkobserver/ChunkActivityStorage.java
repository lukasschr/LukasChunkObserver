package com.github.lukasschr.lukaschunkobserver;

import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection and the {@code chunk_activity} table.
 * Every chunk session a player finishes is appended as one row (event-log / data-warehouse style).
 */
public class ChunkActivityStorage implements AutoCloseable {

    private final Connection connection;

    /**
     * Opens (or creates) the SQLite database at the given file and ensures the schema exists.
     *
     * @param dbFile the database file location
     * @throws SQLException if the connection cannot be opened or the schema cannot be created
     */
    public ChunkActivityStorage(Path dbFile) throws SQLException {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFile.toString());
        this.connection = dataSource.getConnection();
        initSchema();
    }

    /**
     * Creates the {@code chunk_activity} table if it does not already exist.
     *
     * @throws SQLException if the statement fails
     */
    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS chunk_activity (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid  TEXT    NOT NULL,
                        dimension    TEXT    NOT NULL,
                        chunk_x      INTEGER NOT NULL,
                        chunk_z      INTEGER NOT NULL,
                        entered_at   INTEGER NOT NULL,
                        left_at      INTEGER NOT NULL,
                        duration_ms  INTEGER NOT NULL
                    )
                    """);
        }
    }

    /**
     * Appends one finished chunk session as a row; {@code duration_ms} is derived from the timestamps.
     *
     * @param playerUuid the player's UUID
     * @param dimension  the dimension id, e.g. {@code minecraft:overworld}
     * @param chunkX     the chunk X coordinate
     * @param chunkZ     the chunk Z coordinate
     * @param enteredAt  epoch millis when the session started
     * @param leftAt     epoch millis when the session ended
     * @throws SQLException if the insert fails
     */
    public void insert(String playerUuid, String dimension, int chunkX, int chunkZ,
                       long enteredAt, long leftAt) throws SQLException {
        long durationMs = leftAt - enteredAt;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO chunk_activity "
                        + "(player_uuid, dimension, chunk_x, chunk_z, entered_at, left_at, duration_ms) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, playerUuid);
            ps.setString(2, dimension);
            ps.setInt(3, chunkX);
            ps.setInt(4, chunkZ);
            ps.setLong(5, enteredAt);
            ps.setLong(6, leftAt);
            ps.setLong(7, durationMs);
            ps.executeUpdate();
        }
    }

    /**
     * Closes the underlying database connection.
     *
     * @throws SQLException if closing the connection fails
     */
    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
