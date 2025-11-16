package com.aria.platform;

import com.aria.platform.telegram.TelegramConnector;
import com.aria.storage.DatabaseManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory registry to reuse connector instances per platform account.
 * This avoids repeated process spin-up and ensures a single connector instance
 * (which in turn reuses the same Telethon session file).
 */
public class ConnectorRegistry {

    private static final ConnectorRegistry INSTANCE = new ConnectorRegistry();

    private final Map<Integer, PlatformConnector> accountIdToConnector = new ConcurrentHashMap<>();

    private ConnectorRegistry() {}

    public static ConnectorRegistry getInstance() {
        return INSTANCE;
    }

    public PlatformConnector getOrCreateTelegramConnector(DatabaseManager.PlatformAccount acc) {
        if (acc == null) return null;
        return accountIdToConnector.computeIfAbsent(acc.id, id ->
            new TelegramConnector(acc.apiId, acc.apiHash, acc.number, acc.username, acc.id)
        );
    }

    public PlatformConnector getByAccountId(int accountId) {
        return accountIdToConnector.get(accountId);
    }

    public void put(int accountId, PlatformConnector connector) {
        if (connector != null) {
            accountIdToConnector.put(accountId, connector);
        }
    }

    public void remove(int accountId) {
        accountIdToConnector.remove(accountId);
    }
}


