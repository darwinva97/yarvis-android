package com.yarvis.assistant.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class Cache<K, V> {

    private final Map<K, CacheEntry<V>> cache;
    private final int maxSize;
    private final long defaultTtlMs;

    private static class CacheEntry<V> {
        final V value;
        final long expiresAt;

        CacheEntry(V value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public Cache(int maxSize, long defaultTtlMs) {
        this.maxSize = maxSize;
        this.defaultTtlMs = defaultTtlMs;
        this.cache = new LinkedHashMap<K, CacheEntry<V>>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > Cache.this.maxSize;
            }
        };
    }

    public void put(K key, V value) {
        put(key, value, defaultTtlMs);
    }

    public void put(K key, V value, long ttlMs) {
        synchronized (cache) {
            cache.put(key, new CacheEntry<>(value, ttlMs));
        }
    }

    public Optional<V> get(K key) {
        synchronized (cache) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.isExpired()) {
                cache.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.value);
        }
    }

    public V getOrCompute(K key, Function<K, V> computeFunction) {
        return get(key).orElseGet(() -> {
            V computed = computeFunction.apply(key);
            put(key, computed);
            return computed;
        });
    }

    public void remove(K key) {
        synchronized (cache) {
            cache.remove(key);
        }
    }

    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    public void evictExpired() {
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
}
