// DatabaseManager.java
package com.aria.storage;

import com.aria.core.model.TargetUser;
import com.aria.core.model.User;
import com.aria.platform.Platform;
import com.aria.platform.UserPlatform;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    // PostgresSQL connection details - support both localhost (local dev) and postgres (Docker)
    // Check environment variables first (Docker), then fall back to defaults
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

    // Changed from static initializer to avoid early initialization errors
    // Now initialized explicitly in ApplicationInitializer to ensure proper order
    private static boolean initialized = false;
    
    /**
     * Initialize base database tables (users, dialogs, messages, target_users, etc.)
     * Called explicitly from ApplicationInitializer to ensure proper order
     */
    public static synchronized void ensureInitialized() throws SQLException {
        if (!initialized) {
            try {
                initializeDatabase();
                initialized = true;
            } catch (SQLException e) {
                throw new SQLException("Failed to initialize base database tables", e);
            }
        }
    }

    public static class DialogRecord {
        public final int id;
        public final String name;
        public DialogRecord(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static List<DialogRecord> getDialogsForAccounts(int userId, List<Integer> platformAccountIds) throws SQLException {
        List<DialogRecord> dialogs = new ArrayList<>();
        if (platformAccountIds == null || platformAccountIds.isEmpty()) return dialogs;
        String placeholders = String.join(",", java.util.Collections.nCopies(platformAccountIds.size(), "?"));
        String sql = "SELECT id, COALESCE(name, 'Unknown') AS name FROM dialogs WHERE user_id = ? AND platform_account_id IN (" + placeholders + ") ORDER BY id";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            int idx = 2;
            for (Integer id : platformAccountIds) {
                pstmt.setInt(idx++, id);
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                dialogs.add(new DialogRecord(rs.getInt("id"), rs.getString("name")));
            }
        }
        return dialogs;
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
                    password_hash TEXT,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    bio TEXT,
                    email TEXT,
                    two_factor_secret TEXT,
                    two_factor_enabled BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """;

            // Platform accounts (supports multiple accounts per platform per user)
            String createPlatformTable = """
                CREATE TABLE IF NOT EXISTS platform_accounts (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    username TEXT,
                    number TEXT,
                    api_id TEXT,
                    api_hash TEXT,
                    password TEXT,
                    access_token TEXT,
                    refresh_token TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, platform, username, number)
                )
            """;

            // Dialogs
            // Note: type should be 'private', 'group', 'channel', 'supergroup', or 'bot'
            // We filter out groups, channels, supergroups, and bots during retrieval
            String createDialogsTable = """
                CREATE TABLE IF NOT EXISTS dialogs (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    platform_account_id INT DEFAULT 0,
                    dialog_id BIGINT NOT NULL,
                    name TEXT,
                    type TEXT,
                    is_bot BOOLEAN DEFAULT FALSE,
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
                    has_media BOOLEAN DEFAULT FALSE,
                    raw_json JSONB,
                    reference_id BIGINT,
                    status TEXT DEFAULT 'sent',
                    last_updated TIMESTAMPTZ,
                    pinned BOOLEAN DEFAULT FALSE
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


            // Target Users table (Parent Entity - platform-agnostic person information)
            String createTargetUsersTable = """
                CREATE TABLE IF NOT EXISTS target_users (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    bio TEXT,
                    desired_outcome TEXT,
                    meeting_context TEXT,
                    important_details TEXT,
                    cross_platform_context_enabled BOOLEAN DEFAULT FALSE,
                    profile_json JSONB,
                    profile_picture_url TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, name)
                )
            """;

            // SubTarget Users table (Child Entity - platform-specific instance)
            String createSubTargetUsersTable = """
                CREATE TABLE IF NOT EXISTS subtarget_users (
                    id SERIAL PRIMARY KEY,
                    target_user_id INT NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    name TEXT,
                    username TEXT,
                    platform TEXT NOT NULL,
                    platform_account_id INT REFERENCES platform_accounts(id) ON DELETE SET NULL,
                    platform_id BIGINT DEFAULT 0,
                    number TEXT,
                    advanced_communication_settings JSONB,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(target_user_id, platform, platform_account_id, platform_id)
                )
            """;

            // Target Groups table (Parent Entity for groups)
            String createTargetGroupsTable = """
                CREATE TABLE IF NOT EXISTS target_groups (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, name)
                )
            """;

            // SubTarget Groups table (Child Entity - platform-specific group instance)
            String createSubTargetGroupsTable = """
                CREATE TABLE IF NOT EXISTS subtarget_groups (
                    id SERIAL PRIMARY KEY,
                    target_group_id INT NOT NULL REFERENCES target_groups(id) ON DELETE CASCADE,
                    name TEXT,
                    platform TEXT NOT NULL,
                    platform_account_id INT REFERENCES platform_accounts(id) ON DELETE SET NULL,
                    platform_group_id BIGINT NOT NULL,
                    username TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(target_group_id, platform, platform_account_id, platform_group_id)
                )
            """;

            // Target Channels table (Parent Entity for channels)
            String createTargetChannelsTable = """
                CREATE TABLE IF NOT EXISTS target_channels (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    name TEXT NOT NULL,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id, name)
                )
            """;

            // SubTarget Channels table (Child Entity - platform-specific channel instance)
            String createSubTargetChannelsTable = """
                CREATE TABLE IF NOT EXISTS subtarget_channels (
                    id SERIAL PRIMARY KEY,
                    target_channel_id INT NOT NULL REFERENCES target_channels(id) ON DELETE CASCADE,
                    name TEXT,
                    platform TEXT NOT NULL,
                    platform_account_id INT REFERENCES platform_accounts(id) ON DELETE SET NULL,
                    platform_channel_id BIGINT NOT NULL,
                    username TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(target_channel_id, platform, platform_account_id, platform_channel_id)
                )
            """;

            // Legacy Target User Platforms table (kept for migration compatibility)
            String createTargetUserPlatformsTable = """
                CREATE TABLE IF NOT EXISTS target_user_platforms (
                    id SERIAL PRIMARY KEY,
                    target_user_id INT NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    platform TEXT NOT NULL,
                    username TEXT,
                    number TEXT,
                    platform_id INT DEFAULT 0,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(target_user_id, platform)
                )
            """;

            // Conversations (active per subtarget user - moved from target_user level)
            String createConversationsTable = """
                CREATE TABLE IF NOT EXISTS conversations (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    target_user_id INT NOT NULL REFERENCES target_users(id) ON DELETE CASCADE,
                    subtarget_user_id INT REFERENCES subtarget_users(id) ON DELETE CASCADE,
                    desired_goal TEXT NOT NULL,
                    context TEXT,
                    included_account_ids INT[],
                    active BOOLEAN DEFAULT TRUE,
                    started_at TIMESTAMPTZ DEFAULT NOW(),
                    ended_at TIMESTAMPTZ
                )
            """;

            // Ingestion status per platform account
            String createIngestionStatusTable = """
                CREATE TABLE IF NOT EXISTS ingestion_status (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    platform_account_id INT NOT NULL REFERENCES platform_accounts(id) ON DELETE CASCADE,
                    running BOOLEAN DEFAULT FALSE,
                    started_at TIMESTAMPTZ,
                    finished_at TIMESTAMPTZ,
                    last_error TEXT,
                    UNIQUE(user_id, platform_account_id)
                )
            """;

            // Analysis status at user scope (lightweight overall indicator)
            String createAnalysisStatusUserTable = """
                CREATE TABLE IF NOT EXISTS analysis_status_user (
                    user_id INT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                    running BOOLEAN DEFAULT FALSE,
                    started_at TIMESTAMPTZ,
                    finished_at TIMESTAMPTZ,
                    last_error TEXT
                )
            """;

            stmt.execute(createUsersTable);
            stmt.execute(createPlatformTable);
            stmt.execute(createDialogsTable);
            // Ensure unique index exists for upsert on (user_id, platform_account_id, dialog_id)
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS dialogs_user_platform_dialog_uidx ON dialogs(user_id, platform_account_id, dialog_id)");
            stmt.execute(createMessagesTable);
            // Ensure unique index for message upsert on (dialog_id, message_id)
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS messages_dialog_message_uidx ON messages(dialog_id, message_id)");
            // Ensure raw_json column exists for Python ingestor compatibility
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS raw_json JSONB");
            // Ensure reference_id column exists for message replies
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS reference_id BIGINT");
            // Ensure last_updated column exists for detecting edited messages
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS last_updated TIMESTAMPTZ");
            // Ensure message_link column exists for groups/channels (URL to message)
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS message_link TEXT");
            // Ensure pinned column exists for pinned messages
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS pinned BOOLEAN DEFAULT FALSE");
            // Ensure account_name column exists in platform_accounts
            stmt.execute("ALTER TABLE platform_accounts ADD COLUMN IF NOT EXISTS account_name TEXT");
            
            // Table to track messages deleted via app (to prevent priority ingestion from re-adding them)
            String createAppDeletedMessagesTable = """
                CREATE TABLE IF NOT EXISTS app_deleted_messages (
                    id SERIAL PRIMARY KEY,
                    dialog_id INT NOT NULL REFERENCES dialogs(id) ON DELETE CASCADE,
                    message_id BIGINT NOT NULL,
                    deleted_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(dialog_id, message_id)
                )
            """;
            stmt.execute(createAppDeletedMessagesTable);
            // Create index for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS app_deleted_messages_dialog_message_idx ON app_deleted_messages(dialog_id, message_id)");
            // Clean up old deletion records (older than 1 hour) to prevent table from growing too large
            stmt.execute("""
                CREATE OR REPLACE FUNCTION cleanup_old_deleted_messages()
                RETURNS void AS $$
                BEGIN
                    DELETE FROM app_deleted_messages WHERE deleted_at < NOW() - INTERVAL '1 hour';
                END;
                $$ LANGUAGE plpgsql;
            """);
            
            stmt.execute(createMediaTable);
            stmt.execute(createTargetUsersTable);
            stmt.execute(createSubTargetUsersTable);
            stmt.execute(createTargetGroupsTable);
            stmt.execute(createSubTargetGroupsTable);
            stmt.execute(createTargetChannelsTable);
            stmt.execute(createSubTargetChannelsTable);
            stmt.execute(createTargetUserPlatformsTable); // Legacy table for migration
            stmt.execute(createConversationsTable);
            stmt.execute(createIngestionStatusTable);
            stmt.execute(createAnalysisStatusUserTable);
            
            // Add new columns to target_users for migration
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS bio TEXT");
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS desired_outcome TEXT");
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS meeting_context TEXT");
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS important_details TEXT");
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS cross_platform_context_enabled BOOLEAN DEFAULT FALSE");
            
            // Add subtarget_user_id to conversations for migration
            stmt.execute("ALTER TABLE conversations ADD COLUMN IF NOT EXISTS subtarget_user_id INT REFERENCES subtarget_users(id) ON DELETE CASCADE");

            // Add new columns to existing tables
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email TEXT");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret TEXT");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE");
            stmt.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'sent'");
            stmt.execute("ALTER TABLE target_users ADD COLUMN IF NOT EXISTS profile_picture_url TEXT");

            // API Keys table
            String createApiKeysTable = """
                CREATE TABLE IF NOT EXISTS api_keys (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    key_name TEXT NOT NULL,
                    api_key TEXT UNIQUE NOT NULL,
                    secret_key TEXT NOT NULL,
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    last_used_at TIMESTAMPTZ
                )
            """;
            stmt.execute(createApiKeysTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS api_keys_user_id_idx ON api_keys(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS api_keys_api_key_idx ON api_keys(api_key)");

            // User Credits table
            String createUserCreditsTable = """
                CREATE TABLE IF NOT EXISTS user_credits (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    credits DECIMAL(20, 10) DEFAULT 0.0,
                    last_updated TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id)
                )
            """;
            stmt.execute(createUserCreditsTable);

            // Credit Transactions table (for tracking credit usage)
            String createCreditTransactionsTable = """
                CREATE TABLE IF NOT EXISTS credit_transactions (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    api_key_id INT REFERENCES api_keys(id) ON DELETE SET NULL,
                    amount DECIMAL(20, 10) NOT NULL,
                    transaction_type TEXT NOT NULL,
                    description TEXT,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """;
            stmt.execute(createCreditTransactionsTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS credit_transactions_user_id_idx ON credit_transactions(user_id)");

            // Subscriptions table
            String createSubscriptionsTable = """
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    subscription_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    stripe_subscription_id TEXT,
                    stripe_customer_id TEXT,
                    current_period_start TIMESTAMPTZ,
                    current_period_end TIMESTAMPTZ,
                    cancel_at_period_end BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMPTZ DEFAULT NOW(),
                    updated_at TIMESTAMPTZ DEFAULT NOW(),
                    UNIQUE(user_id)
                )
            """;
            stmt.execute(createSubscriptionsTable);

            // Free Tier Tracking table
            String createFreeTierTable = """
                CREATE TABLE IF NOT EXISTS free_tier_tracking (
                    id SERIAL PRIMARY KEY,
                    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    trial_started_at TIMESTAMPTZ DEFAULT NOW(),
                    trial_ends_at TIMESTAMPTZ,
                    requests_today INT DEFAULT 0,
                    last_request_date DATE,
                    UNIQUE(user_id)
                )
            """;
            stmt.execute(createFreeTierTable);

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

    /**
     * Fetch a user by phone number. Returns null if not found.
     */
    public static User getUserByPhone(String phone) throws SQLException {
        String sql = """
            SELECT id, phone, username, first_name, last_name, COALESCE(bio, '') AS bio
            FROM users WHERE phone = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phone);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("bio")
                    );
                    user.setUserAppId(rs.getInt("id"));
                    return user;
                }
                return null;
            }
        }
    }

    /**
     * Fetch a user by email/username. Returns null if not found.
     * Note: we store email in the 'username' column for now.
     */
    public static User getUserByEmail(String email) throws SQLException {
        String sql = """
            SELECT id, phone, username, first_name, last_name, COALESCE(bio, '') AS bio
            FROM users WHERE username = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("bio")
                    );
                    user.setUserAppId(rs.getInt("id"));
                    return user;
                }
                return null;
            }
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

    /**
     * Set or update a user's password hash (BCrypt). Uses phone to identify user.
     */
    public static void upsertUserPasswordHashByPhone(String phone, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE phone = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, passwordHash);
            pstmt.setString(2, phone);
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new SQLException("No user found with provided phone to set password.");
            }
        }
    }

    /**
     * Verify credentials (phone or email + raw password). Returns User on success, null on failure.
     */
    public static User verifyCredentials(String identifier, String rawPassword) throws SQLException {
        String sql = """
            SELECT id, phone, username, first_name, last_name, COALESCE(bio, '') AS bio, password_hash
            FROM users
            WHERE (phone = ? OR username = ?)
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, identifier);
            pstmt.setString(2, identifier);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (storedHash == null || storedHash.isEmpty()) {
                        return null;
                    }
                    boolean matches = org.springframework.security.crypto.bcrypt.BCrypt.checkpw(rawPassword, storedHash);
                    if (!matches) return null;
                    User user = new User(
                        rs.getString("phone"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("bio")
                    );
                    user.setUserAppId(rs.getInt("id"));
                    return user;
                }
                return null;
            }
        }
    }

    // =====================
    // Platform Account Queries
    // =====================
    public static class PlatformAccount {
        public final int id;
        public final String platform;
        public final String username;
        public final String number;
        public final String apiId;
        public final String apiHash;
        public final String accountName; // Display name for the account (e.g., "Ezekiel")

        public PlatformAccount(int id, String platform, String username, String number,
                               String apiId, String apiHash, String accountName) {
            this.id = id;
            this.platform = platform;
            this.username = username;
            this.number = number;
            this.apiId = apiId;
            this.apiHash = apiHash;
            this.accountName = accountName;
        }
    }

    public static List<PlatformAccount> getPlatformAccountsForUser(int userId) throws SQLException {
        String sql = """
            SELECT id, platform, username, number, api_id, api_hash, account_name
            FROM platform_accounts
            WHERE user_id = ?
            ORDER BY platform, username
        """;
        List<PlatformAccount> accounts = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    accounts.add(new PlatformAccount(
                        rs.getInt("id"),
                        rs.getString("platform"),
                        rs.getString("username"),
                        rs.getString("number"),
                        rs.getString("api_id"),
                        rs.getString("api_hash"),
                        rs.getString("account_name")
                    ));
                }
            }
        }
        return accounts;
    }

    public static PlatformAccount getPlatformAccountById(int accountId) throws SQLException {
        String sql = """
            SELECT id, platform, username, number, api_id, api_hash, account_name
            FROM platform_accounts
            WHERE id = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlatformAccount(
                        rs.getInt("id"),
                        rs.getString("platform"),
                        rs.getString("username"),
                        rs.getString("number"),
                        rs.getString("api_id"),
                        rs.getString("api_hash"),
                        rs.getString("account_name")
                    );
                }
            }
        }
        return null;
    }

    // =====================
    // Admin/Reset Operations
    // =====================
    public static void deleteAllUsersCascade() throws SQLException {
        String sql = "TRUNCATE TABLE users CASCADE";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    // =====================
    // Platform Account Operations
    // =====================
    public static int savePlatformAccount(
            int userId, String platform, String username, String number,
            String apiId, String apiHash, String password,
            String accessToken, String refreshToken, String accountName) throws SQLException {

        String sql = """
            INSERT INTO platform_accounts
                (user_id, platform, username, number, api_id, api_hash, password, access_token, refresh_token, account_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, platform, username, number) DO UPDATE
            SET api_id = EXCLUDED.api_id,
                api_hash = EXCLUDED.api_hash,
                password = EXCLUDED.password,
                access_token = EXCLUDED.access_token,
                refresh_token = EXCLUDED.refresh_token,
                account_name = COALESCE(EXCLUDED.account_name, platform_accounts.account_name)
            RETURNING id
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, platform);
            pstmt.setString(3, username);
            pstmt.setString(4, number);
            pstmt.setString(5, apiId);
            pstmt.setString(6, apiHash);
            pstmt.setString(7, password);
            pstmt.setString(8, accessToken);
            pstmt.setString(9, refreshToken);
            pstmt.setString(10, accountName);

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
    public static int saveDialog(int userId, int platformAccountId, long dialogId, String name, String type,
                                 int messageCount, int mediaCount) throws SQLException {
        // Filter out groups, channels, and bots
        if (type != null && (type.equalsIgnoreCase("group") || 
                          type.equalsIgnoreCase("channel") || 
                          type.equalsIgnoreCase("supergroup") ||
                          type.equalsIgnoreCase("bot"))) {
            throw new SQLException("Skipping group/channel/bot dialog: " + name);
        }
        
        String sql = """
            INSERT INTO dialogs (user_id, platform_account_id, dialog_id, name, type, message_count, media_count, last_synced, is_bot)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?)
            ON CONFLICT (user_id, dialog_id) DO UPDATE
            SET name = EXCLUDED.name,
                type = EXCLUDED.type,
                message_count = EXCLUDED.message_count,
                media_count = EXCLUDED.media_count,
                last_synced = EXCLUDED.last_synced,
                is_bot = EXCLUDED.is_bot,
                platform_account_id = EXCLUDED.platform_account_id
            RETURNING id
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, platformAccountId);
            pstmt.setLong(3, dialogId);
            pstmt.setString(4, name);
            pstmt.setString(5, type != null ? type : "private");
            pstmt.setInt(6, messageCount);
            pstmt.setInt(7, mediaCount);
            pstmt.setBoolean(8, type != null && type.equalsIgnoreCase("bot"));

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                // If no ID returned (ON CONFLICT), query for existing
                return getDialogId(userId, dialogId);
            }
        }
    }

    // Backward compatible overload without platformAccountId
    public static int saveDialog(int userId, long dialogId, String name, String type,
                                 int messageCount, int mediaCount) throws SQLException {
        return saveDialog(userId, 0, dialogId, name, type, messageCount, mediaCount);
    }
    
    private static int getDialogId(int userId, long dialogId) throws SQLException {
        String sql = "SELECT id FROM dialogs WHERE user_id = ? AND dialog_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setLong(2, dialogId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new SQLException("Dialog not found: " + dialogId);
        }
    }

    public static List<Integer> getDialogIdsForAccounts(int userId, List<Integer> platformAccountIds) throws SQLException {
        if (platformAccountIds == null || platformAccountIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(platformAccountIds.size(), "?"));
        String sql = "SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id IN (" + placeholders + ")";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            int idx = 2;
            for (Integer id : platformAccountIds) {
                pstmt.setInt(idx++, id);
            }
            ResultSet rs = pstmt.executeQuery();
            List<Integer> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            return ids;
        }
    }

    // =====================
    // Message Operations
    // =====================
    
    /**
     * Get the last (highest) message ID for a dialog (for incremental ingestion)
     */
    public static Long getLastMessageId(int dialogId) throws SQLException {
        String sql = "SELECT MAX(message_id) FROM messages WHERE dialog_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long lastId = rs.getLong(1);
                return rs.wasNull() ? null : lastId; // Return null if no messages exist
            }
            return null;
        }
    }

    public static int saveMessage(int dialogId, long messageId, String sender,
                                  String text, LocalDateTime timestamp, boolean hasMedia) throws SQLException {
        return saveMessage(dialogId, messageId, sender, text, timestamp, hasMedia, null);
    }

    public static int saveMessage(int dialogId, long messageId, String sender,
                                  String text, LocalDateTime timestamp, boolean hasMedia, Long referenceId) throws SQLException {
        return saveMessage(dialogId, messageId, sender, text, timestamp, hasMedia, referenceId, "sent");
    }
    
    public static int saveMessage(int dialogId, long messageId, String sender,
                                  String text, LocalDateTime timestamp, boolean hasMedia, Long referenceId, String status) throws SQLException {
        String sql = """
            INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media, reference_id, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (dialog_id, message_id) DO UPDATE SET
                sender = EXCLUDED.sender,
                text = EXCLUDED.text,
                timestamp = EXCLUDED.timestamp,
                has_media = EXCLUDED.has_media,
                reference_id = EXCLUDED.reference_id,
                status = COALESCE(EXCLUDED.status, messages.status)
            RETURNING id
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, dialogId);
            pstmt.setLong(2, messageId);
            pstmt.setString(3, sender);
            pstmt.setString(4, text != null ? SecureStorage.encrypt(text) : null);
            pstmt.setObject(5, timestamp);
            pstmt.setBoolean(6, hasMedia);
            pstmt.setObject(7, referenceId);
            pstmt.setString(8, status != null ? status : "sent");

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
                String encrypted = rs.getString("text");
                messages.add(encrypted != null ? SecureStorage.decrypt(encrypted) : null);
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

    // =====================
    // Target User Operations
    // =====================

    public TargetUser getTargetUserById(int targetUserId) throws SQLException {
        String sql = """
            SELECT id, user_id, name, bio, desired_outcome, meeting_context, important_details, 
                   cross_platform_context_enabled, profile_json, profile_picture_url
            FROM target_users
            WHERE id = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetUserId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                TargetUser targetUser = new TargetUser();
                targetUser.setTargetId(rs.getInt("id"));
                targetUser.setUserId(rs.getInt("user_id"));
                targetUser.setName(rs.getString("name"));
                targetUser.setBio(rs.getString("bio"));
                targetUser.setDesiredOutcome(rs.getString("desired_outcome"));
                targetUser.setMeetingContext(rs.getString("meeting_context"));
                targetUser.setImportantDetails(rs.getString("important_details"));
                targetUser.setCrossPlatformContextEnabled(rs.getBoolean("cross_platform_context_enabled"));
                targetUser.setProfileJson(rs.getString("profile_json"));
                targetUser.setProfilePictureUrl(rs.getString("profile_picture_url"));
                
                // Load SubTarget Users
                List<com.aria.core.model.SubTargetUser> subTargets = getSubTargetUsersByTargetUserId(targetUserId);
                targetUser.setSubTargetUsers(subTargets);
                
                // Legacy: Load from target_user_platforms
                targetUser.setPlatforms(new ArrayList<>());
                String legacySql = """
                    SELECT platform, username, number, platform_id
                    FROM target_user_platforms
                    WHERE target_user_id = ?
                """;
                try (PreparedStatement legacyPstmt = conn.prepareStatement(legacySql)) {
                    legacyPstmt.setInt(1, targetUserId);
                    ResultSet legacyRs = legacyPstmt.executeQuery();
                    while (legacyRs.next()) {
                        String platformName = legacyRs.getString("platform");
                        if (platformName != null) {
                            try {
                                Platform platform = Platform.valueOf(platformName.toUpperCase());
                                UserPlatform userPlatform = new UserPlatform(
                                        legacyRs.getString("username"),
                                        legacyRs.getString("number"),
                                        legacyRs.getInt("platform_id"),
                                        platform
                                );
                                targetUser.getPlatforms().add(userPlatform);
                            } catch (IllegalArgumentException e) {
                                System.err.println("Invalid platform name: " + platformName);
                            }
                        }
                    }
                }
                
                return targetUser;
            }
        }
        return null;
    }

    public List<TargetUser> getTargetUsersByUserId(int userId) throws SQLException {
        List<TargetUser> targetUsers = new ArrayList<>();

        // First, get all target users with their new fields
        String targetSql = """
            SELECT id, name, bio, desired_outcome, meeting_context, important_details, 
                   cross_platform_context_enabled, profile_json, profile_picture_url
            FROM target_users
            WHERE user_id = ?
            ORDER BY name
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(targetSql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                TargetUser targetUser = new TargetUser();
                int targetId = rs.getInt("id");
                targetUser.setTargetId(targetId);
                targetUser.setUserId(userId);
                targetUser.setName(rs.getString("name"));
                targetUser.setBio(rs.getString("bio"));
                targetUser.setDesiredOutcome(rs.getString("desired_outcome"));
                targetUser.setMeetingContext(rs.getString("meeting_context"));
                targetUser.setImportantDetails(rs.getString("important_details"));
                targetUser.setCrossPlatformContextEnabled(rs.getBoolean("cross_platform_context_enabled"));
                targetUser.setProfileJson(rs.getString("profile_json"));
                targetUser.setProfilePictureUrl(rs.getString("profile_picture_url"));
                
                // Load SubTarget Users
                List<com.aria.core.model.SubTargetUser> subTargets = getSubTargetUsersByTargetUserId(targetId);
                targetUser.setSubTargetUsers(subTargets);
                
                // Legacy: Also load from target_user_platforms for backward compatibility
                targetUser.setPlatforms(new ArrayList<>());
                String legacySql = """
                    SELECT platform, username, number, platform_id
                    FROM target_user_platforms
                    WHERE target_user_id = ?
                """;
                try (PreparedStatement legacyPstmt = conn.prepareStatement(legacySql)) {
                    legacyPstmt.setInt(1, targetId);
                    ResultSet legacyRs = legacyPstmt.executeQuery();
                    while (legacyRs.next()) {
                        String platformName = legacyRs.getString("platform");
                        if (platformName != null) {
                            try {
                                Platform platform = Platform.valueOf(platformName.toUpperCase());
                                UserPlatform userPlatform = new UserPlatform(
                                        legacyRs.getString("username"),
                                        legacyRs.getString("number"),
                                        legacyRs.getInt("platform_id"),
                                        platform
                                );
                                targetUser.getPlatforms().add(userPlatform);
                            } catch (IllegalArgumentException e) {
                                System.err.println("Invalid platform name: " + platformName);
                            }
                        }
                    }
                }
                
                targetUsers.add(targetUser);
            }
        }
        return targetUsers;
    }

    public boolean saveTargetUser(int userId, TargetUser targetUser) throws SQLException {
        Connection conn = getConnection();
        try {
            conn.setAutoCommit(false);

            int targetUserId;
            if (targetUser.getTargetId() == 0) {
                // Insert new target user
                String insertTargetSql = """
                    INSERT INTO target_users (user_id, name, bio, desired_outcome, meeting_context, 
                                            important_details, cross_platform_context_enabled, 
                                            profile_json, profile_picture_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(insertTargetSql)) {
                    pstmt.setInt(1, userId);
                    pstmt.setString(2, targetUser.getName());
                    pstmt.setString(3, targetUser.getBio());
                    pstmt.setString(4, targetUser.getDesiredOutcome());
                    pstmt.setString(5, targetUser.getMeetingContext());
                    pstmt.setString(6, targetUser.getImportantDetails());
                    pstmt.setBoolean(7, targetUser.isCrossPlatformContextEnabled());
                    // Cast profile_json to jsonb
                    if (targetUser.getProfileJson() != null && !targetUser.getProfileJson().isEmpty()) {
                        pstmt.setObject(8, targetUser.getProfileJson(), java.sql.Types.OTHER);
                    } else {
                        pstmt.setNull(8, java.sql.Types.OTHER);
                    }
                    pstmt.setString(9, targetUser.getProfilePictureUrl());
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        targetUserId = rs.getInt(1);
                        targetUser.setTargetId(targetUserId);
                    } else {
                        throw new SQLException("Failed to insert target user");
                    }
                }
            } else {
                // Update existing target user
                targetUserId = targetUser.getTargetId();
                String updateTargetSql = """
                    UPDATE target_users 
                    SET name = ?, bio = ?, desired_outcome = ?, meeting_context = ?, 
                        important_details = ?, cross_platform_context_enabled = ?,
                        profile_json = ?, profile_picture_url = ?
                    WHERE id = ?
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(updateTargetSql)) {
                    pstmt.setString(1, targetUser.getName());
                    pstmt.setString(2, targetUser.getBio());
                    pstmt.setString(3, targetUser.getDesiredOutcome());
                    pstmt.setString(4, targetUser.getMeetingContext());
                    pstmt.setString(5, targetUser.getImportantDetails());
                    pstmt.setBoolean(6, targetUser.isCrossPlatformContextEnabled());
                    // Cast profile_json to jsonb
                    if (targetUser.getProfileJson() != null && !targetUser.getProfileJson().isEmpty()) {
                        pstmt.setObject(7, targetUser.getProfileJson(), java.sql.Types.OTHER);
                    } else {
                        pstmt.setNull(7, java.sql.Types.OTHER);
                    }
                    pstmt.setString(8, targetUser.getProfilePictureUrl());
                    pstmt.setInt(9, targetUserId);
                    pstmt.executeUpdate();
                }
            }

            // Save SubTarget Users if explicitly provided (not null and not empty)
            // CRITICAL: Only process SubTarget Users if the list is non-null AND non-empty
            // - null list = don't touch SubTarget Users (preserve existing)
            // - empty list = don't touch SubTarget Users (preserve existing)
            // - non-empty list = update/insert/delete as needed
            if (targetUser.getSubTargetUsers() != null && !targetUser.getSubTargetUsers().isEmpty()) {
                // Get existing SubTarget User IDs
                java.util.Set<Integer> existingIds = new java.util.HashSet<>();
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT id FROM subtarget_users WHERE target_user_id = ?")) {
                    pstmt.setInt(1, targetUserId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            existingIds.add(rs.getInt(1));
                        }
                    }
                }
                
                // Get incoming SubTarget User IDs
                java.util.Set<Integer> incomingIds = new java.util.HashSet<>();
                for (com.aria.core.model.SubTargetUser subTarget : targetUser.getSubTargetUsers()) {
                    subTarget.setTargetUserId(targetUserId);
                    saveSubTargetUser(subTarget);
                    if (subTarget.getId() > 0) {
                        incomingIds.add(subTarget.getId());
                    }
                }
                
                // Delete SubTarget Users that are not in the incoming list
                existingIds.removeAll(incomingIds);
                if (!existingIds.isEmpty()) {
                    String deleteSql = "DELETE FROM subtarget_users WHERE id = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                        for (Integer idToDelete : existingIds) {
                            pstmt.setInt(1, idToDelete);
                            pstmt.addBatch();
                        }
                        pstmt.executeBatch();
                    }
                }
            }
            // If subTargetUsers is null or empty, we don't touch existing SubTarget Users (preserve them)

            // Legacy: Insert platforms (for backward compatibility)
            if (targetUser.getPlatforms() != null && !targetUser.getPlatforms().isEmpty()) {
                // Delete existing legacy platforms
                String deletePlatformsSql = "DELETE FROM target_user_platforms WHERE target_user_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deletePlatformsSql)) {
                    pstmt.setInt(1, targetUserId);
                    pstmt.executeUpdate();
                }
                
                // Insert platforms
                String insertPlatformSql = """
                    INSERT INTO target_user_platforms (target_user_id, platform, username, number, platform_id)
                    VALUES (?, ?, ?, ?, ?)
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(insertPlatformSql)) {
                    for (UserPlatform platform : targetUser.getPlatforms()) {
                        pstmt.setInt(1, targetUserId);
                        pstmt.setString(2, platform.getPlatform().name());
                        pstmt.setString(3, platform.getUsername());
                        pstmt.setString(4, platform.getNumber());
                        pstmt.setInt(5, platform.getPlatformId());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    public boolean deleteTargetUser(int userId, String targetName) throws SQLException {
        // Cascading delete will remove all SubTarget Users automatically
        String sql = "DELETE FROM target_users WHERE user_id = ? AND name = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, targetName);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    // =====================
    // SubTarget User Operations
    // =====================

    public com.aria.core.model.SubTargetUser getSubTargetUserById(int subtargetUserId) throws SQLException {
        String sql = """
            SELECT id, target_user_id, name, username, platform, platform_account_id, 
                   platform_id, number, advanced_communication_settings, created_at
            FROM subtarget_users
            WHERE id = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, subtargetUserId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapSubTargetUserFromResultSet(rs);
            }
        }
        return null;
    }

    public List<com.aria.core.model.SubTargetUser> getSubTargetUsersByTargetUserId(int targetUserId) throws SQLException {
        List<com.aria.core.model.SubTargetUser> subTargets = new ArrayList<>();
        String sql = """
            SELECT id, target_user_id, name, username, platform, platform_account_id, 
                   platform_id, number, advanced_communication_settings, created_at
            FROM subtarget_users
            WHERE target_user_id = ?
            ORDER BY platform, created_at
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetUserId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                subTargets.add(mapSubTargetUserFromResultSet(rs));
            }
        }
        return subTargets;
    }

    private com.aria.core.model.SubTargetUser mapSubTargetUserFromResultSet(ResultSet rs) throws SQLException {
        com.aria.core.model.SubTargetUser subTarget = new com.aria.core.model.SubTargetUser();
        subTarget.setId(rs.getInt("id"));
        subTarget.setTargetUserId(rs.getInt("target_user_id"));
        subTarget.setName(rs.getString("name"));
        subTarget.setUsername(rs.getString("username"));
        String platformStr = rs.getString("platform");
        if (platformStr != null) {
            try {
                subTarget.setPlatform(Platform.valueOf(platformStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid platform: " + platformStr);
            }
        }
        Integer accId = rs.getObject("platform_account_id", Integer.class);
        subTarget.setPlatformAccountId(accId);
        Long platformId = rs.getObject("platform_id", Long.class);
        subTarget.setPlatformId(platformId);
        subTarget.setNumber(rs.getString("number"));
        subTarget.setAdvancedCommunicationSettings(rs.getString("advanced_communication_settings"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            subTarget.setCreatedAt(createdAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        return subTarget;
    }

    public int saveSubTargetUser(com.aria.core.model.SubTargetUser subTargetUser) throws SQLException {
        // If ID is set (greater than 0), use UPDATE; otherwise use INSERT...ON CONFLICT
        if (subTargetUser.getId() > 0) {
            // Update existing SubTarget User
            String updateSql = """
                UPDATE subtarget_users 
                SET name = ?, username = ?, platform = ?, platform_account_id = ?, 
                    platform_id = ?, number = ?, advanced_communication_settings = ?
                WHERE id = ?
            """;
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setString(1, subTargetUser.getName());
                pstmt.setString(2, subTargetUser.getUsername());
                pstmt.setString(3, subTargetUser.getPlatform() != null ? subTargetUser.getPlatform().name() : null);
                pstmt.setObject(4, subTargetUser.getPlatformAccountId());
                pstmt.setObject(5, subTargetUser.getPlatformId());
                pstmt.setString(6, subTargetUser.getNumber());
                // Cast advanced_communication_settings to jsonb
                if (subTargetUser.getAdvancedCommunicationSettings() != null && !subTargetUser.getAdvancedCommunicationSettings().isEmpty()) {
                    pstmt.setObject(7, subTargetUser.getAdvancedCommunicationSettings(), java.sql.Types.OTHER);
                } else {
                    pstmt.setNull(7, java.sql.Types.OTHER);
                }
                pstmt.setInt(8, subTargetUser.getId());
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    return subTargetUser.getId();
                } else {
                    throw new SQLException("Failed to update SubTarget User with ID: " + subTargetUser.getId());
                }
            }
        } else {
            // Insert new SubTarget User with ON CONFLICT handling
            String insertSql = """
                INSERT INTO subtarget_users (target_user_id, name, username, platform, platform_account_id, 
                                            platform_id, number, advanced_communication_settings)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (target_user_id, platform, platform_account_id, platform_id) 
                DO UPDATE SET
                    name = EXCLUDED.name,
                    username = EXCLUDED.username,
                    number = EXCLUDED.number,
                    advanced_communication_settings = EXCLUDED.advanced_communication_settings
                RETURNING id
            """;
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, subTargetUser.getTargetUserId());
                pstmt.setString(2, subTargetUser.getName());
                pstmt.setString(3, subTargetUser.getUsername());
                pstmt.setString(4, subTargetUser.getPlatform() != null ? subTargetUser.getPlatform().name() : null);
                pstmt.setObject(5, subTargetUser.getPlatformAccountId());
                pstmt.setObject(6, subTargetUser.getPlatformId());
                pstmt.setString(7, subTargetUser.getNumber());
                // Cast advanced_communication_settings to jsonb
                if (subTargetUser.getAdvancedCommunicationSettings() != null && !subTargetUser.getAdvancedCommunicationSettings().isEmpty()) {
                    pstmt.setObject(8, subTargetUser.getAdvancedCommunicationSettings(), java.sql.Types.OTHER);
                } else {
                    pstmt.setNull(8, java.sql.Types.OTHER);
                }
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int id = rs.getInt(1);
                    subTargetUser.setId(id);
                    return id;
                } else {
                    throw new SQLException("Failed to save SubTarget User");
                }
            }
        }
    }

    public boolean deleteSubTargetUser(int subtargetUserId) throws SQLException {
        String sql = "DELETE FROM subtarget_users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, subtargetUserId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public boolean updateTargetUserFields(int targetUserId, String bio, String desiredOutcome, 
                                          String meetingContext, String importantDetails, 
                                          boolean crossPlatformContextEnabled) throws SQLException {
        String sql = """
            UPDATE target_users 
            SET bio = ?, desired_outcome = ?, meeting_context = ?, 
                important_details = ?, cross_platform_context_enabled = ?
            WHERE id = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bio);
            pstmt.setString(2, desiredOutcome);
            pstmt.setString(3, meetingContext);
            pstmt.setString(4, importantDetails);
            pstmt.setBoolean(5, crossPlatformContextEnabled);
            pstmt.setInt(6, targetUserId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        }
    }

    public boolean getCrossPlatformContextEnabled(int targetUserId) throws SQLException {
        String sql = "SELECT cross_platform_context_enabled FROM target_users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetUserId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("cross_platform_context_enabled");
            }
        }
        return false;
    }

    /**
     * Get all dialog IDs for a Target User's SubTarget Users.
     * Used for cross-platform context aggregation.
     */
    public List<Integer> getDialogIdsForTargetUser(int targetUserId, int userId) throws SQLException {
        List<Integer> dialogIds = new ArrayList<>();
        
        // Get all SubTarget Users for this Target User
        List<com.aria.core.model.SubTargetUser> subTargets = getSubTargetUsersByTargetUserId(targetUserId);
        
        if (subTargets.isEmpty()) {
            return dialogIds;
        }
        
        String sql = """
            SELECT DISTINCT d.id
            FROM dialogs d
            JOIN subtarget_users stu ON d.platform_account_id = stu.platform_account_id
            WHERE stu.target_user_id = ?
            AND d.user_id = ?
            AND d.type = 'private'
            AND (
                -- Match by platform_id (Telegram user ID)
                (stu.platform_id > 0 AND d.dialog_id = stu.platform_id)
                OR
                -- Match by username (case-insensitive)
                (stu.username IS NOT NULL AND (
                    LOWER(d.name) = LOWER(stu.username)
                    OR LOWER(d.name) = LOWER('@' || stu.username)
                    OR LOWER(d.name) LIKE LOWER('%' || stu.username || '%')
                ))
            )
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, targetUserId);
            pstmt.setInt(2, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                dialogIds.add(rs.getInt("id"));
            }
        }
        
        return dialogIds;
    }

    // =====================
    // Conversation Operations
    // =====================
    public static void upsertActiveConversation(int userId, int targetUserId, String desiredGoal, String context, java.util.List<Integer> includedAccountIds) throws SQLException {
        upsertActiveConversation(userId, targetUserId, null, desiredGoal, context, includedAccountIds);
    }

    public static void upsertActiveConversation(int userId, int targetUserId, Integer subtargetUserId, String desiredGoal, String context, java.util.List<Integer> includedAccountIds) throws SQLException {
        try (Connection conn = getConnection()) {
            // End any existing active conversation for this subtarget (or target if subtarget is null)
            String endSql = subtargetUserId != null
                ? "UPDATE conversations SET active = FALSE, ended_at = NOW() WHERE user_id = ? AND subtarget_user_id = ? AND active = TRUE"
                : "UPDATE conversations SET active = FALSE, ended_at = NOW() WHERE user_id = ? AND target_user_id = ? AND subtarget_user_id IS NULL AND active = TRUE";
            try (PreparedStatement end = conn.prepareStatement(endSql)) {
                end.setInt(1, userId);
                if (subtargetUserId != null) {
                    end.setInt(2, subtargetUserId);
                } else {
                    end.setInt(2, targetUserId);
                }
                end.executeUpdate();
            }
            // Insert new active
            String insSql = "INSERT INTO conversations (user_id, target_user_id, subtarget_user_id, desired_goal, context, included_account_ids, active) VALUES (?, ?, ?, ?, ?, ?, TRUE)";
            try (PreparedStatement ins = conn.prepareStatement(insSql)) {
                ins.setInt(1, userId);
                ins.setInt(2, targetUserId);
                if (subtargetUserId != null) {
                    ins.setInt(3, subtargetUserId);
                } else {
                    ins.setNull(3, java.sql.Types.INTEGER);
                }
                ins.setString(4, desiredGoal);
                ins.setString(5, context);
                if (includedAccountIds != null && !includedAccountIds.isEmpty()) {
                    // Convert to PG int[] via JDBC
                    Integer[] arr = includedAccountIds.toArray(new Integer[0]);
                    java.sql.Array sqlArray = conn.createArrayOf("int4", arr);
                    ins.setArray(6, sqlArray);
                } else {
                    ins.setNull(6, java.sql.Types.ARRAY);
                }
                ins.executeUpdate();
            }
        }
    }

    public static void endActiveConversation(int userId, int targetUserId) throws SQLException {
        String sql = "UPDATE conversations SET active = FALSE, ended_at = NOW() WHERE user_id = ? AND target_user_id = ? AND active = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, targetUserId);
            pstmt.executeUpdate();
        }
    }

    public static class ActiveConversation {
        public final String desiredGoal;
        public final String context;
        public final java.util.List<Integer> includedAccountIds;
        public ActiveConversation(String desiredGoal, String context, java.util.List<Integer> includedAccountIds) {
            this.desiredGoal = desiredGoal;
            this.context = context;
            this.includedAccountIds = includedAccountIds;
        }
    }

    public static ActiveConversation getActiveConversation(int userId, int targetUserId) throws SQLException {
        String sql = "SELECT desired_goal, context, included_account_ids FROM conversations WHERE user_id = ? AND target_user_id = ? AND active = TRUE ORDER BY started_at DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, targetUserId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String goal = rs.getString(1);
                    String ctx = rs.getString(2);
                    java.sql.Array arr = rs.getArray(3);
                    java.util.List<Integer> ids = new java.util.ArrayList<>();
                    if (arr != null) {
                        Object o = arr.getArray();
                        if (o instanceof Integer[] intArr) {
                            java.util.Collections.addAll(ids, intArr);
                        } else {
                            // fallback
                            Object[] gen = (Object[]) o;
                            for (Object v : gen) ids.add(((Number) v).intValue());
                        }
                    }
                    return new ActiveConversation(goal, ctx, ids);
                }
            }
        }
        return null;
    }

    // =====================
    // Ingestion/Analysis Status Operations
    // =====================
    public static void setIngestionRunning(int userId, int platformAccountId) throws SQLException {
        String sql = """
            INSERT INTO ingestion_status (user_id, platform_account_id, running, started_at, finished_at, last_error)
            VALUES (?, ?, TRUE, NOW(), NULL, NULL)
            ON CONFLICT (user_id, platform_account_id) DO UPDATE
            SET running = EXCLUDED.running, started_at = EXCLUDED.started_at, finished_at = NULL, last_error = NULL
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, platformAccountId);
            ps.executeUpdate();
        }
    }

    public static void setIngestionFinished(int userId, int platformAccountId, String error) throws SQLException {
        String sql = """
            INSERT INTO ingestion_status (user_id, platform_account_id, running, started_at, finished_at, last_error)
            VALUES (?, ?, FALSE, NOW(), NOW(), ?)
            ON CONFLICT (user_id, platform_account_id) DO UPDATE
            SET running = FALSE, finished_at = NOW(), last_error = EXCLUDED.last_error
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, platformAccountId);
            ps.setString(3, error);
            ps.executeUpdate();
        }
    }

    public static boolean isIngestionRunning(int userId, int platformAccountId) throws SQLException {
        String sql = """
            SELECT running FROM ingestion_status
            WHERE user_id = ? AND platform_account_id = ?
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, platformAccountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("running");
                }
            }
        }
        return false;
    }

    public static java.util.List<java.util.Map<String, Object>> getIngestionStatuses(int userId) throws SQLException {
        String sql = """
            SELECT pa.id AS platform_account_id, pa.platform, COALESCE(pa.username, pa.number) AS account,
                   COALESCE(is.running, FALSE) AS running, is.started_at, is.finished_at, is.last_error
            FROM platform_accounts pa
            LEFT JOIN ingestion_status is ON is.user_id = pa.user_id AND is.platform_account_id = pa.id
            WHERE pa.user_id = ?
            ORDER BY pa.platform, pa.username NULLS LAST, pa.number NULLS LAST
        """;
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("platformAccountId", rs.getInt("platform_account_id"));
                    m.put("platform", rs.getString("platform"));
                    m.put("account", rs.getString("account"));
                    m.put("running", rs.getBoolean("running"));
                    m.put("startedAt", rs.getTimestamp("started_at"));
                    m.put("finishedAt", rs.getTimestamp("finished_at"));
                    m.put("lastError", rs.getString("last_error"));
                    rows.add(m);
                }
            }
        }
        return rows;
    }

    public static void setAnalysisRunning(int userId) throws SQLException {
        String sql = """
            INSERT INTO analysis_status_user (user_id, running, started_at, finished_at, last_error)
            VALUES (?, TRUE, NOW(), NULL, NULL)
            ON CONFLICT (user_id) DO UPDATE
            SET running = TRUE, started_at = NOW(), finished_at = NULL, last_error = NULL
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public static void setAnalysisFinished(int userId, String error) throws SQLException {
        String sql = """
            INSERT INTO analysis_status_user (user_id, running, started_at, finished_at, last_error)
            VALUES (?, FALSE, NOW(), NOW(), ?)
            ON CONFLICT (user_id) DO UPDATE
            SET running = FALSE, finished_at = NOW(), last_error = EXCLUDED.last_error
        """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, error);
            ps.executeUpdate();
        }
    }

    public static java.util.Map<String, Object> getAnalysisStatus(int userId) throws SQLException {
        String sql = "SELECT running, started_at, finished_at, last_error FROM analysis_status_user WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("running", rs.getBoolean("running"));
                    m.put("startedAt", rs.getTimestamp("started_at"));
                    m.put("finishedAt", rs.getTimestamp("finished_at"));
                    m.put("lastError", rs.getString("last_error"));
                    return m;
                }
            }
        }
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("running", false);
        m.put("startedAt", null);
        m.put("finishedAt", null);
        m.put("lastError", null);
        return m;
    }

    // =====================
    // Cascade deletion for platform account
    // =====================
    public static boolean deletePlatformAccountCascade(int accountId, int userId) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                // delete messages via dialogs
                try (PreparedStatement delMsgs = conn.prepareStatement(
                        "DELETE FROM messages WHERE dialog_id IN (SELECT id FROM dialogs WHERE user_id = ? AND platform_account_id = ?)")) {
                    delMsgs.setInt(1, userId);
                    delMsgs.setInt(2, accountId);
                    delMsgs.executeUpdate();
                }
                // delete media orphans (optional cleanup)
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM media WHERE message_id NOT IN (SELECT id FROM messages)");
                }
                // delete dialogs for this account
                try (PreparedStatement delDialogs = conn.prepareStatement(
                        "DELETE FROM dialogs WHERE user_id = ? AND platform_account_id = ?")) {
                    delDialogs.setInt(1, userId);
                    delDialogs.setInt(2, accountId);
                    delDialogs.executeUpdate();
                }
                // collect target users linked to this account (target_user_platforms.platform_id)
                java.util.List<Integer> targetIds = new java.util.ArrayList<>();
                try (PreparedStatement selTargets = conn.prepareStatement(
                        "SELECT DISTINCT target_user_id FROM target_user_platforms WHERE platform_id = ?")) {
                    selTargets.setInt(1, accountId);
                    try (ResultSet rs = selTargets.executeQuery()) {
                        while (rs.next()) targetIds.add(rs.getInt(1));
                    }
                }
                // delete links for this account
                try (PreparedStatement delLinks = conn.prepareStatement(
                        "DELETE FROM target_user_platforms WHERE platform_id = ?")) {
                    delLinks.setInt(1, accountId);
                    delLinks.executeUpdate();
                }
                // delete target users associated
                if (!targetIds.isEmpty()) {
                    String placeholders = String.join(",", java.util.Collections.nCopies(targetIds.size(), "?"));
                    String delTargetsSql = "DELETE FROM target_users WHERE user_id = ? AND id IN (" + placeholders + ")";
                    try (PreparedStatement delTargets = conn.prepareStatement(delTargetsSql)) {
                        delTargets.setInt(1, userId);
                        int idx = 2;
                        for (Integer tId : targetIds) delTargets.setInt(idx++, tId);
                        delTargets.executeUpdate();
                    }
                }
                // delete the platform account
                try (PreparedStatement delAccount = conn.prepareStatement(
                        "DELETE FROM platform_accounts WHERE id = ? AND user_id = ?")) {
                    delAccount.setInt(1, accountId);
                    delAccount.setInt(2, userId);
                    delAccount.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
