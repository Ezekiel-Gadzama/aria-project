// DatabaseManager.java
package com.aria.storage;

import com.aria.core.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    // ⚠️ Update with your PostgreSQL connection details
    private static final String URL = "jdbc:postgresql://localhost:5432/aria";
    private static final String USER = "postgres";
    private static final String PASSWORD = "Ezekiel(23)";

    static {
        try {
            initializeDatabase();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Users table
            String createUsersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    phone TEXT UNIQUE NOT NULL,
                    username TEXT,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    bio TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """;

            // Platform accounts
            String createPlatformTable = """
                CREATE TABLE IF NOT EXISTS platform_accounts (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    username TEXT,
                    api_id TEXT,
                    api_hash TEXT,
                    password TEXT,
                    access_token TEXT,
                    refresh_token TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, platform)
                )
            """;

            // Dialogs
            String createDialogsTable = """
                CREATE TABLE IF NOT EXISTS dialogs (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    dialog_id BIGINT NOT NULL,
                    name TEXT,
                    type TEXT,
                    message_count INT DEFAULT 0,
                    media_count INT DEFAULT 0,
                    last_synced TIMESTAMPTZ
                )
            """;

            // Messages
            String createMessagesTable = """
                CREATE TABLE IF NOT EXISTS messages (
                    id SERIAL PRIMARY KEY,
                    dialog_id INT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
                    message_id BIGINT NOT NULL,
                    sender TEXT,
                    text TEXT,
                    timestamp TIMESTAMPTZ,
                    has_media BOOLEAN DEFAULT FALSE
                )
            """;

            // Media
            String createMediaTable = """
                CREATE TABLE IF NOT EXISTS media (
                    id SERIAL PRIMARY KEY,
                    message_id INT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                    type TEXT,
                    file_path TEXT,
                    file_name TEXT,
                    file_size BIGINT,
                    mime_type TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """;

            stmt.execute(createUsersTable);
            stmt.execute(createPlatformTable);
            stmt.execute(createDialogsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createMediaTable);

        }
    }

    // =====================
    // User Operations
    // =====================

    public static boolean userExists(String phone) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE phone = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phone);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        }
    }

    public static int saveUser(User user) throws SQLException {
        String sql = """
            INSERT INTO users (phone, username, first_name, last_name, bio)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (phone) DO UPDATE
            SET username = EXCLUDED.username,
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                bio = EXCLUDED.bio
            RETURNING id
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getPhone());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getFirstName());
            pstmt.setString(4, user.getLastName());
            pstmt.setString(5, user.getAppGoal());

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new SQLException("Failed to save user: no ID returned.");
            }
        }
    }

    // =====================
    // Platform Account Operations
    // =====================
    public static int savePlatformAccount(
            int userId, String platform, String username,
            String apiId, String apiHash, String password,
            String accessToken, String refreshToken) throws SQLException {

        String sql = """
            INSERT INTO platform_accounts
                (user_id, platform, username, api_id, api_hash, password, access_token, refresh_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, platform) DO UPDATE
            SET username = EXCLUDED.username,
                api_id = EXCLUDED.api_id,
                api_hash = EXCLUDED.api_hash,
                password = EXCLUDED.password,
                access_token = EXCLUDED.access_token,
                refresh_token = EXCLUDED.refresh_token
            RETURNING id
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, platform);
            pstmt.setString(3, username);
            pstmt.setString(4, apiId);
            pstmt.setString(5, apiHash);
            pstmt.setString(6, password);
            pstmt.setString(7, accessToken);
            pstmt.setString(8, refreshToken);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new SQLException("Failed to save platform account: no ID returned.");
            }
        }
    }

    // =====================
    // Dialog Operations
    // =====================
    public static int saveDialog(int userId, long dialogId, String name, String type,
                                 int messageCount, int mediaCount) throws SQLException {
        String sql = """
            INSERT INTO dialogs (user_id, dialog_id, name, type, message_count, media_count, last_synced)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            RETURNING id
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setLong(2, dialogId);
            pstmt.setString(3, name);
            pstmt.setString(4, type);
            pstmt.setInt(5, messageCount);
            pstmt.setInt(6, mediaCount);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new SQLException("Failed to save dialog: no ID returned.");
            }
        }
    }

    // =====================
    // Message Operations
    // =====================
    public static int saveMessage(int dialogId, long messageId, String sender,
                                  String text, LocalDateTime timestamp, boolean hasMedia) throws SQLException {
        String sql = """
            INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            pstmt.setLong(2, messageId);
            pstmt.setString(3, sender);
            pstmt.setString(4, text);
            pstmt.setObject(5, timestamp);
            pstmt.setBoolean(6, hasMedia);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new SQLException("Failed to save message: no ID returned.");
            }
        }
    }

    // =====================
    // Media Operations
    // =====================
    public static void saveMedia(int messageId, String type, String filePath,
                                 String fileName, long fileSize, String mimeType) throws SQLException {
        String sql = """
            INSERT INTO media (message_id, type, file_path, file_name, file_size, mime_type)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            pstmt.setString(2, type);
            pstmt.setString(3, filePath);
            pstmt.setString(4, fileName);
            pstmt.setLong(5, fileSize);
            pstmt.setString(6, mimeType);
            pstmt.executeUpdate();
        }
    }

    // =====================
    // Query Helpers
    // =====================
    public static List<String> getDialogsForUser(int userId) throws SQLException {
        List<String> dialogs = new ArrayList<>();
        String sql = "SELECT name FROM dialogs WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                dialogs.add(rs.getString("name"));
            }
        }
        return dialogs;
    }

    public static List<String> getMessagesForDialog(int dialogId) throws SQLException {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT text FROM messages WHERE dialog_id = ? ORDER BY timestamp";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                messages.add(rs.getString("text"));
            }
        }
        return messages;
    }

    public static List<String> getMediaForMessage(int messageId) throws SQLException {
        List<String> files = new ArrayList<>();
        String sql = "SELECT file_path FROM media WHERE message_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, messageId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                files.add(rs.getString("file_path"));
            }
        }
        return files;
    }
}
