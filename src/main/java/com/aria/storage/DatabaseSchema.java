package com.aria.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Enhanced database schema with optimal indexing for chat categorization,
 * goal-based queries, conversation states, quizzes, and summaries.
 */
public class DatabaseSchema {
    // Use same connection logic as DatabaseManager for consistency
    private static String getDbHost() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl != null && !dbUrl.isEmpty()) {
            // Extract host from jdbc:postgresql://host:port/database
            try {
                String withoutJdbc = dbUrl.replace("jdbc:postgresql://", "");
                return withoutJdbc.split(":")[0];
            } catch (Exception e) {
                // Fall through to default
            }
        }
        // Check DB_HOST environment variable
        String dbHost = System.getenv("DB_HOST");
        return dbHost != null ? dbHost : "localhost";
    }
    
    private static final String DB_HOST = getDbHost();
    private static final String URL = "jdbc:postgresql://" + DB_HOST + ":5432/aria";
    private static final String USER = System.getenv("DATABASE_USER") != null 
        ? System.getenv("DATABASE_USER") 
        : "postgres";
    private static final String PASSWORD = System.getenv("DATABASE_PASSWORD") != null 
        ? System.getenv("DATABASE_PASSWORD") 
        : "Ezekiel(23)";

    public static void initializeSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            
            // Enable UUID extension if needed
            stmt.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

            // Goals table - stores conversation goals
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS goals (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    target_user_id INT NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    goal_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    meeting_context TEXT,
                    desired_outcome TEXT NOT NULL,
                    status TEXT DEFAULT 'active',
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Chat categories - stores available categories
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chat_categories (
                    id SERIAL PRIMARY KEY,
                    category_name TEXT UNIQUE NOT NULL,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Chat-goal mapping - maps dialogs to goals/categories
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chat_goals (
                    id SERIAL PRIMARY KEY,
                    dialog_id INT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
                    goal_id INT REFERENCES goals(id) ON DELETE SET NULL,
                    category_name TEXT NOT NULL REFERENCES chat_categories(category_name),
                    relevance_score DOUBLE PRECISION DEFAULT 0.0,
                    outcome TEXT,
                    success_score DOUBLE PRECISION DEFAULT 0.0,
                    categorized_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(dialog_id, category_name)
                )
            """);

            // Conversation states - tracks active conversations
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversation_states (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    target_user_id INT NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    goal_id INT NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    platform_dialog_id BIGINT NOT NULL,
                    conversation_history TEXT,
                    last_message_timestamp TIMESTAMPTZ,
                    last_response_timestamp TIMESTAMPTZ,
                    engagement_score DOUBLE PRECISION DEFAULT 0.5,
                    disinterest_indicators JSONB,
                    response_delay_seconds INT DEFAULT 60,
                    status TEXT DEFAULT 'active',
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Disinterest detection logs
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS disinterest_logs (
                    id SERIAL PRIMARY KEY,
                    conversation_state_id INT NOT NULL REFERENCES conversation_states(id) ON DELETE CASCADE,
                    detection_timestamp TIMESTAMPTZ DEFAULT NOW(),
                    disinterest_probability DOUBLE PRECISION NOT NULL,
                    indicators JSONB,
                    recommendation TEXT,
                    acknowledged BOOLEAN DEFAULT FALSE
                )
            """);

            // Conversation summaries
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversation_summaries (
                    id SERIAL PRIMARY KEY,
                    conversation_state_id INT NOT NULL REFERENCES conversation_states(id) ON DELETE CASCADE,
                    goal_id INT NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
                    summary_text TEXT NOT NULL,
                    key_personal_details JSONB,
                    next_steps TEXT,
                    outcome_status TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Quiz questions
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quiz_questions (
                    id SERIAL PRIMARY KEY,
                    conversation_summary_id INT NOT NULL REFERENCES conversation_summaries(id) ON DELETE CASCADE,
                    question_text TEXT NOT NULL,
                    correct_answer TEXT NOT NULL,
                    question_type TEXT DEFAULT 'text',
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Quiz results
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quiz_results (
                    id SERIAL PRIMARY KEY,
                    quiz_question_id INT NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
                    user_answer TEXT,
                    is_correct BOOLEAN,
                    answered_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // Communication style profiles - stores analyzed style profiles
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS style_profiles (
                    id SERIAL PRIMARY KEY,
                    dialog_id INT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
                    humor_level DOUBLE PRECISION DEFAULT 0.5,
                    formality_level DOUBLE PRECISION DEFAULT 0.5,
                    empathy_level DOUBLE PRECISION DEFAULT 0.5,
                    avg_response_time DOUBLE PRECISION,
                    avg_message_length DOUBLE PRECISION,
                    question_rate DOUBLE PRECISION,
                    emoji_usage_rate DOUBLE PRECISION,
                    engagement_level DOUBLE PRECISION DEFAULT 0.5,
                    analyzed_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(dialog_id)
                )
            """);

            // Insert default categories if they don't exist
            insertDefaultCategories(stmt);

            // Create indexes for optimal performance
            createIndexes(stmt);

            System.out.println("Database schema initialized successfully!");
        }
    }

    private static void insertDefaultCategories(Statement stmt) throws SQLException {
        // Use enum to ensure consistent category names and descriptions
        com.aria.core.model.ChatCategory[] categories = com.aria.core.model.ChatCategory.values();

        Connection conn = getConnection();
        for (com.aria.core.model.ChatCategory category : categories) {
            String sql = """
                INSERT INTO chat_categories (category_name, description)
                VALUES (?, ?)
                ON CONFLICT (category_name) DO UPDATE
                SET description = EXCLUDED.description
            """;
            
            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, category.getName());
                pstmt.setString(2, category.getDescription());
                pstmt.executeUpdate();
            }
        }
    }

    private static void createIndexes(Statement stmt) throws SQLException {
        // Goals indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_user_id ON goals(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_target_user_id ON goals(target_user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_status ON goals(status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_goals_goal_type ON goals(goal_type)");

        // Chat-goals indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_goals_dialog_id ON chat_goals(dialog_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_goals_category ON chat_goals(category_name)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_goals_relevance_score ON chat_goals(relevance_score DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_goals_success_score ON chat_goals(success_score DESC)");

        // Conversation states indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conv_states_user_id ON conversation_states(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conv_states_target_user_id ON conversation_states(target_user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conv_states_goal_id ON conversation_states(goal_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conv_states_status ON conversation_states(status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_conv_states_platform ON conversation_states(platform, platform_dialog_id)");

        // Dialog indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dialogs_user_id ON dialogs(user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_dialogs_dialog_id ON dialogs(dialog_id)");

        // Message indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_dialog_id ON messages(dialog_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp DESC)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender)");

        // Style profiles indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_style_profiles_dialog_id ON style_profiles(dialog_id)");

        // Disinterest logs indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_disinterest_conv_state_id ON disinterest_logs(conversation_state_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_disinterest_timestamp ON disinterest_logs(detection_timestamp DESC)");

        // Summary indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_summaries_conv_state_id ON conversation_summaries(conversation_state_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_summaries_goal_id ON conversation_summaries(goal_id)");

            // Quiz indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_quiz_questions_summary_id ON quiz_questions(conversation_summary_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_quiz_results_question_id ON quiz_results(quiz_question_id)");

            // Target Businesses table (for company/organization contexts)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS target_businesses (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, name)
                )
            """);

            // Business Sub-Targets table (channels, groups, private chats within a business)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS business_subtargets (
                    id SERIAL PRIMARY KEY,
                    business_id INT NOT NULL REFERENCES target_businesses(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL CHECK (type IN ('CHANNEL', 'GROUP', 'PRIVATE_CHAT')),
                    platform TEXT NOT NULL,
                    platform_account_id INT REFERENCES platform_accounts(id) ON DELETE SET NULL,
                    dialog_id INT REFERENCES dialogs(id) ON DELETE SET NULL,
                    platform_id BIGINT DEFAULT 0,
                    username TEXT,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(business_id, platform, platform_account_id, platform_id, type)
                )
            """);

            // Business Bot Conversations (for storing bot chat state)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS business_bot_conversations (
                    id SERIAL PRIMARY KEY,
                    business_id INT NOT NULL REFERENCES target_businesses(id) ON DELETE CASCADE,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    openai_response_id VARCHAR(255) UNIQUE,
                    last_message_id BIGINT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(business_id, user_id)
                )
            """);

            // Target user responses (OpenAI Responses API state storage)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS target_user_responses (
                    id SERIAL PRIMARY KEY,
                    target_user_id INTEGER NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    subtarget_user_id INTEGER REFERENCES subtarget_users(id) ON DELETE CASCADE,
                    openai_response_id VARCHAR(255) UNIQUE NOT NULL,
                    last_message_id BIGINT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(target_user_id, subtarget_user_id)
                )
            """);
            
            // Add last_message_id column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE target_user_responses ADD COLUMN IF NOT EXISTS last_message_id BIGINT");
            } catch (SQLException e) {
                // Column might already exist, ignore
            }

            // Indexes for target_user_responses
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_target_user_responses_target_user_id ON target_user_responses(target_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_target_user_responses_subtarget_user_id ON target_user_responses(subtarget_user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_target_user_responses_openai_response_id ON target_user_responses(openai_response_id)");

            // Indexes for target_businesses
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_target_businesses_user_id ON target_businesses(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_target_businesses_name ON target_businesses(name)");

            // Indexes for business_subtargets
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_subtargets_business_id ON business_subtargets(business_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_subtargets_type ON business_subtargets(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_subtargets_dialog_id ON business_subtargets(dialog_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_subtargets_platform ON business_subtargets(platform, platform_account_id)");

            // Indexes for business_bot_conversations
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_bot_conversations_business_id ON business_bot_conversations(business_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_bot_conversations_user_id ON business_bot_conversations(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_business_bot_conversations_openai_response_id ON business_bot_conversations(openai_response_id)");

            System.out.println("Database indexes created successfully!");
        }

    private static Connection getConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static Connection getConnectionInstance() throws SQLException {
        return getConnection();
    }
}

