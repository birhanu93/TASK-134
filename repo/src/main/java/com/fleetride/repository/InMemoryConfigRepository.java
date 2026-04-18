package com.fleetride.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConfigRepository implements ConfigRepository {
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    private final Map<String, String> dictionaries = new ConcurrentHashMap<>();
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getSetting(String key) { return Optional.ofNullable(settings.get(key)); }

    @Override
    public void setSetting(String key, String value) { settings.put(key, value); }

    @Override
    public Map<String, String> allSettings() { return new java.util.HashMap<>(settings); }

    @Override
    public Optional<String> getDictionary(String key) { return Optional.ofNullable(dictionaries.get(key)); }

    @Override
    public void setDictionary(String key, String value) { dictionaries.put(key, value); }

    @Override
    public Map<String, String> allDictionaries() { return new java.util.HashMap<>(dictionaries); }

    @Override
    public Optional<String> getTemplate(String key) { return Optional.ofNullable(templates.get(key)); }

    @Override
    public void setTemplate(String key, String value) { templates.put(key, value); }

    @Override
    public Map<String, String> allTemplates() { return new java.util.HashMap<>(templates); }
}
