package com.aria.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

/**
 * Redis cache manager for caching messages, user data, and other frequently accessed data
 */
public class RedisCacheManager {
    private static RedisCacheManager instance;
    private JedisPool jedisPool;
    private Gson gson;
    
    private static String getRedisHost() {
        String redisHost = System.getenv("REDIS_HOST");
        return redisHost != null ? redisHost : "localhost";
    }
    
    private static int getRedisPort() {
        String redisPort = System.getenv("REDIS_PORT");
        return redisPort != null ? Integer.parseInt(redisPort) : 6379;
    }
    
    private RedisCacheManager() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(8);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        
        String redisHost = getRedisHost();
        int redisPort = getRedisPort();
        String redisPassword = System.getenv("REDIS_PASSWORD");
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
        } else {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
        }
        
        gson = new GsonBuilder().create();
    }
    
    public static synchronized RedisCacheManager getInstance() {
        if (instance == null) {
            instance = new RedisCacheManager();
        }
        return instance;
    }
    
    /**
     * Check if an exception is a connection error (should be silently ignored)
     */
    private boolean isConnectionError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Failed to connect") || 
                               msg.contains("DNS") || 
                               msg.contains("Connection refused") ||
                               msg.contains("Connection timed out"));
    }
    
    /**
     * Cache messages for a conversation
     */
    public void cacheMessages(int userId, int targetId, List<?> messages, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "messages:user:" + userId + ":target:" + targetId;
            String json = gson.toJson(messages);
            jedis.setex(key, ttlSeconds, json);
        } catch (Exception e) {
            // Silently fail - Redis is optional, app should work without it
            // Only log if it's not a connection issue to avoid spam
            if (!e.getMessage().contains("Failed to connect") && !e.getMessage().contains("DNS")) {
                System.err.println("Failed to cache messages: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get cached messages for a conversation
     */
    public <T> List<T> getCachedMessages(int userId, int targetId, Class<T> messageClass) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "messages:user:" + userId + ":target:" + targetId;
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, 
                    com.google.gson.reflect.TypeToken.getParameterized(List.class, messageClass).getType());
            }
        } catch (Exception e) {
            System.err.println("Failed to get cached messages: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get cached messages as List<Map<String, Object>>
     * This is a convenience method for the common case of message maps
     */
    public java.util.List<java.util.Map<String, Object>> getCachedMessagesAsMapList(int userId, int targetId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "messages:user:" + userId + ":target:" + targetId;
            String json = jedis.get(key);
            if (json != null) {
                java.lang.reflect.Type type = com.google.gson.reflect.TypeToken.getParameterized(
                    java.util.List.class, 
                    com.google.gson.reflect.TypeToken.getParameterized(
                        java.util.Map.class, String.class, Object.class
                    ).getType()
                ).getType();
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to get cached messages: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Invalidate cached messages for a conversation
     */
    public void invalidateMessages(int userId, int targetId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "messages:user:" + userId + ":target:" + targetId;
            jedis.del(key);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to invalidate cached messages: " + e.getMessage());
            }
        }
    }
    
    /**
     * Alias for invalidateMessages (for backward compatibility)
     */
    public void invalidateCachedMessages(int userId, int targetId) {
        invalidateMessages(userId, targetId);
    }
    
    /**
     * Cache user data
     */
    public void cacheUser(int userId, Object userData, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "user:" + userId;
            String json = gson.toJson(userData);
            jedis.setex(key, ttlSeconds, json);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to cache user: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get cached user data
     */
    public <T> T getCachedUser(int userId, Class<T> userClass) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "user:" + userId;
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, userClass);
            }
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to get cached user: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Cache target user data
     */
    public void cacheTarget(int userId, int targetId, Object targetData, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "target:user:" + userId + ":id:" + targetId;
            String json = gson.toJson(targetData);
            jedis.setex(key, ttlSeconds, json);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to cache target: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get cached target user data
     */
    public <T> T getCachedTarget(int userId, int targetId, Class<T> targetClass) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "target:user:" + userId + ":id:" + targetId;
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, targetClass);
            }
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to get cached target: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Invalidate target user cache
     */
    public void invalidateTarget(int userId, int targetId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "target:user:" + userId + ":id:" + targetId;
            jedis.del(key);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to invalidate cached target: " + e.getMessage());
            }
        }
    }
    
    /**
     * Cache analysis data
     */
    public void cacheAnalysis(int userId, Integer targetId, Object analysisData, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = targetId != null 
                ? "analysis:user:" + userId + ":target:" + targetId
                : "analysis:user:" + userId + ":general";
            String json = gson.toJson(analysisData);
            jedis.setex(key, ttlSeconds, json);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to cache analysis: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get cached analysis data
     */
    public <T> T getCachedAnalysis(int userId, Integer targetId, Class<T> analysisClass) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = targetId != null 
                ? "analysis:user:" + userId + ":target:" + targetId
                : "analysis:user:" + userId + ":general";
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, analysisClass);
            }
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to get cached analysis: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Generic cache set
     */
    public void set(String key, String value, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to set cache: " + e.getMessage());
            }
        }
    }
    
    /**
     * Generic cache get
     */
    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to get cache: " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Generic cache delete
     */
    public void delete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        } catch (Exception e) {
            if (!isConnectionError(e)) {
                System.err.println("Failed to delete cache: " + e.getMessage());
            }
        }
    }
    
    /**
     * Close the connection pool
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}

