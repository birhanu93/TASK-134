package com.fleetride.repository;

import java.util.Map;
import java.util.Optional;

public interface ConfigRepository {
    Optional<String> getSetting(String key);
    void setSetting(String key, String value);
    Map<String, String> allSettings();

    Optional<String> getDictionary(String key);
    void setDictionary(String key, String value);
    Map<String, String> allDictionaries();

    Optional<String> getTemplate(String key);
    void setTemplate(String key, String value);
    Map<String, String> allTemplates();
}
