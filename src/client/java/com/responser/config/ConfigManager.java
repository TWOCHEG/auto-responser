package com.responser.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final String MOD_ID = "auto_responser";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Object> DEFAULTS = initDefaults();
    private static final ConfigManager INSTANCE = new ConfigManager();

    private final Path configDir;
    private final Path configFile;
    private Map<String, Object> config;

    private ConfigManager() {
        this.configDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MOD_ID);
        this.configFile = configDir.resolve(MOD_ID + ".json");
        this.config = new HashMap<>();
        load();
    }

    private static Map<String, Object> initDefaults() {
        Map<String, Object> map = new HashMap<>();
        map.put("enable", true);
        map.put("apiKey", "sk-...");
        map.put("modelId", "meta-llama/llama-3.3-70b-instruct");
        map.put("delayS", 3);
        map.put("customMentions", "");
        map.put("autoOutputMentions", true);
        map.put("sendDelay", 4000);
        map.put("delayRandomFactor", 500);
        return map;
    }

    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Object value = config.getOrDefault(key, DEFAULTS.get(key));
        if (value instanceof Double && DEFAULTS.get(key) instanceof Integer) {
            return (T) Integer.valueOf(((Double) value).intValue());
        }
        return (T) value;
    }

    public void set(String key, Object value) {
        if (DEFAULTS.containsKey(key) && DEFAULTS.get(key) instanceof Integer && value instanceof Double) {
            config.put(key, ((Double) value).intValue());
        } else {
            config.put(key, value);
        }
        save();
    }

    private void load() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            if (!Files.exists(configFile)) {
                save();
            } else {
                String json = Files.readString(configFile, StandardCharsets.UTF_8);                
                
                if (!json.isBlank()) {
                    Map<String, Object> loadedConfig = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
                    for (String key : loadedConfig.keySet()) {
                        Object value = loadedConfig.get(key);
                        if (DEFAULTS.containsKey(key) && DEFAULTS.get(key) instanceof Integer && value instanceof Double) {
                            loadedConfig.put(key, ((Double) value).intValue());
                        }
                    }
                    config = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            String json = GSON.toJson(config);
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
