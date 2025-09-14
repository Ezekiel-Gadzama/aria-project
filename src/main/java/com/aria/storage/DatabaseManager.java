// DatabaseManager.java

package com.aria.storage;

import com.aria.core.model.ConversationGoal;
import com.aria.core.model.Message;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:aria.db";

    static {
        initializeDatabase();
    }

    private static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {

            // Create conversations table
            String createConversationsTable = """
                CREATE TABLE IF NOT EXISTS conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_name TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    goal TEXT NOT NULL,
                    context TEXT,
                    status TEXT DEFAULT 'active',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """;

            // Create messages table
            String createMessagesTable = """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id INTEGER,
                    content TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_from_user BOOLEAN,
                    FOREIGN KEY (conversation_id) REFERENCES conversations (id)
                )
            """;

            stmt.execute(createConversationsTable);
            stmt.execute(createMessagesTable);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int saveConversationGoal(ConversationGoal goal) {
        String sql = "INSERT INTO conversations (target_name, platform, goal, context) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, goal.getTargetName());
            pstmt.setString(2, goal.getPlatform());
            pstmt.setString(3, goal.getDesiredOutcome());
            pstmt.setString(4, goal.getMeetingContext());

            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static void saveMessage(int conversationId, Message message) {
        String sql = "INSERT INTO messages (conversation_id, content, sender, is_from_user) VALUES (?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, conversationId);
            pstmt.setString(2, message.getContent());
            pstmt.setString(3, message.getSender());
            pstmt.setBoolean(4, message.isFromUser());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Message> getConversationHistory(int conversationId) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT content, sender, timestamp, is_from_user FROM messages WHERE conversation_id = ? ORDER BY timestamp";

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, conversationId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Message message = new Message();
                message.setContent(rs.getString("content"));
                message.setSender(rs.getString("sender"));
                message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                message.setFromUser(rs.getBoolean("is_from_user"));
                messages.add(message);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return messages;
    }
}