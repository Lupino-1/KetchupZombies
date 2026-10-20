package dev.rajce.ketchupZombies.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.lupino1.folia.FoliaManager;
import dev.rajce.ketchupZombies.KetchupZombies;
import org.mariadb.jdbc.message.client.QuitPacket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlDatabase implements AutoCloseable {

    public enum Mode {
        NORMAL,
        ENDLESS
    }

    public record Entry(String name, int round, long timeMs) {
    }

    private final KetchupZombies plugin;
    private volatile HikariDataSource dataSource;
    private volatile boolean ready;
    private volatile List<Entry> normal = List.of();
    private volatile List<Entry> endless = List.of();

    public SqlDatabase(KetchupZombies plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.config().database.enabled) {
            return;
        }
        FoliaManager.runAsync(() -> {
            try {
                open();
                schema();
                ready = true;
                reloadNow();
                plugin.getLogger().info("MariaDB leaderboard is ready.");
            } catch (Exception e) {
                plugin.getLogger().warning("MariaDB is offline: " + e.getMessage());
            }
        });
    }

    public boolean ready() {
        return ready;
    }

    public List<Entry> top(Mode mode) {
        return mode == Mode.NORMAL ? normal : endless;
    }

    public CompletableFuture<Boolean> record(UUID uuid, String username, Mode mode, int round, long timeMs) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FoliaManager.runAsync(() -> {
            if (!ready) {
                future.complete(false);
                return;
            }
            try {
                boolean saved = write(uuid, username, mode, round, timeMs);
                reloadNow();
                future.complete(saved);
            } catch (SQLException e) {
                plugin.getLogger().warning("Score save: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void open() {
        var config = plugin.config().database;
        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setJdbcUrl("jdbc:mariadb://" + config.host + ":" + config.port + "/" + config.name);
        hikari.setUsername(config.username);
        hikari.setPassword(config.password);
        hikari.setMaximumPoolSize(Math.max(1, config.poolSize));
        hikari.setConnectionTimeout(5000);
        hikari.setPoolName("KetchupZombies");
        ClassLoader loader = SqlDatabase.class.getClassLoader();
        hikari.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable, "KetchupZombies-hikari");
            thread.setDaemon(true);
            thread.setContextClassLoader(loader);
            return thread;
        });
        dataSource = new HikariDataSource(hikari);
    }

    private void schema() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ketchupzombies_players (
                        player_uuid CHAR(36) NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        PRIMARY KEY (player_uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ketchupzombies_scores (
                        player_uuid CHAR(36) NOT NULL,
                        mode VARCHAR(16) NOT NULL,
                        best_round INT NOT NULL,
                        best_time_ms BIGINT NOT NULL,
                        achieved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, mode)
                    )
                    """);
        }
    }

    private boolean write(UUID uuid, String username, Mode mode, int round, long timeMs) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement player = connection.prepareStatement("""
                    INSERT INTO ketchupzombies_players (player_uuid, username) VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE username = VALUES(username)
                    """)) {
                player.setString(1, uuid.toString());
                player.setString(2, username);
                player.executeUpdate();
            }
            boolean better;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT best_round, best_time_ms FROM ketchupzombies_scores
                    WHERE player_uuid = ? AND mode = ? FOR UPDATE
                    """)) {
                select.setString(1, uuid.toString());
                select.setString(2, mode.name().toLowerCase());
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        better = true;
                    } else if (mode == Mode.NORMAL) {
                        better = timeMs < rows.getLong("best_time_ms");
                    } else {
                        int storedRound = rows.getInt("best_round");
                        long storedTime = rows.getLong("best_time_ms");
                        better = round > storedRound || (round == storedRound && timeMs < storedTime);
                    }
                }
            }
            if (better) {
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO ketchupzombies_scores (player_uuid, mode, best_round, best_time_ms)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE best_round = VALUES(best_round), best_time_ms = VALUES(best_time_ms), achieved_at = CURRENT_TIMESTAMP
                        """)) {
                    upsert.setString(1, uuid.toString());
                    upsert.setString(2, mode.name().toLowerCase());
                    upsert.setInt(3, round);
                    upsert.setLong(4, timeMs);
                    upsert.executeUpdate();
                }
            }
            connection.commit();
            return better;
        }
    }

    private void reloadNow() throws SQLException {
        normal = load(Mode.NORMAL);
        endless = load(Mode.ENDLESS);
    }

    private List<Entry> load(Mode mode) throws SQLException {
        String order = mode == Mode.NORMAL
                ? "s.best_time_ms ASC, s.achieved_at ASC"
                : "s.best_round DESC, s.best_time_ms ASC, s.achieved_at ASC";
        String sql = """
                SELECT p.username, s.best_round, s.best_time_ms
                FROM ketchupzombies_scores s
                JOIN ketchupzombies_players p ON p.player_uuid = s.player_uuid
                WHERE s.mode = ?
                ORDER BY %s
                LIMIT 10
                """.formatted(order);
        List<Entry> entries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mode.name().toLowerCase());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new Entry(rows.getString("username"), rows.getInt("best_round"), rows.getLong("best_time_ms")));
                }
            }
        }
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        HikariDataSource source = dataSource;
        if (source == null) {
            return;
        }
        touch(QuitPacket.class);
        source.close();
    }

    private static void touch(Class<?> type) {
        type.getName();
    }
}
