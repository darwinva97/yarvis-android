package com.yarvis.assistant.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Clase genérica que implementa un caché LRU (Least Recently Used).
 *
 * Demuestra: GENÉRICOS CON MÚLTIPLES PARÁMETROS DE TIPO (K, V)
 *
 * @param <K> Tipo de la clave
 * @param <V> Tipo del valor
 */
public class Cache<K, V> {

    private final Map<K, CacheEntry<V>> cache;
    private final int maxSize;
    private final long defaultTtlMs;

    /**
     * Entrada de caché con tiempo de expiración.
     * Demuestra: CLASE GENÉRICA ANIDADA
     */
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
        // LinkedHashMap con accessOrder=true para LRU
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

    /**
     * Obtiene un valor o lo computa si no existe.
     * Demuestra: GENÉRICOS EN PARÁMETROS DE MÉTODO con Function<K, V>
     */
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

    /**
     * Elimina entradas expiradas.
     */
    public void evictExpired() {
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
}
