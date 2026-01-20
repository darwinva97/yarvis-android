package com.yarvis.assistant.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Repository<T extends Identifiable> {

    private final List<T> items;
    private final Object lock = new Object();

    public Repository() {
        this.items = new ArrayList<>();
    }

    public Repository(int initialCapacity) {
        this.items = new ArrayList<>(initialCapacity);
    }

    public boolean add(T item) {
        if (item == null) return false;
        synchronized (lock) {
            if (findById(item.getId()).isPresent()) return false;
            return items.add(item);
        }
    }

    public boolean upsert(T item) {
        if (item == null) return false;
        synchronized (lock) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId().equals(item.getId())) {
                    items.set(i, item);
                    return true;
                }
            }
            items.add(item);
            return false;
        }
    }

    public Optional<T> removeById(String id) {
        synchronized (lock) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getId().equals(id)) {
                    return Optional.of(items.remove(i));
                }
            }
            return Optional.empty();
        }
    }

    public Optional<T> findById(String id) {
        synchronized (lock) {
            return items.stream()
                    .filter(item -> item.getId().equals(id))
                    .findFirst();
        }
    }

    public List<T> findAll(Predicate<T> predicate) {
        synchronized (lock) {
            return items.stream()
                    .filter(predicate)
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    public List<T> getAll() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public int size() {
        synchronized (lock) {
            return items.size();
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            return items.isEmpty();
        }
    }

    public void clear() {
        synchronized (lock) {
            items.clear();
        }
    }
}
