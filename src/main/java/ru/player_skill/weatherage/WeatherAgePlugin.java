package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class WeatherAgePlugin extends JavaPlugin {

    private FileConfiguration config;
    private final Set<String> whitelist = new HashSet<>();
    // ключ = имя мира, значение = тип режима
    private final Map<String, WorldMode> worldModeMap = new HashMap<>();
    
    // Конфигурационные секции
    private Map<String, CustomWeatherConfig> customWeatherConfig = new HashMap<>();
    private Map<String, VanillaWeatherConfig> vanillaWeatherConfig = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        loadWhitelist();
        loadCustomWeather();
        loadVanillaWeather();
        validateWorlds();

        // Регистрируем команды и слушатели (если будут)
        getCommand("weatherage").setExecutor(new WeatherAgeCommand(this));

        getLogger().info("WeatherAge enabled. Loaded worlds: " + worldModeMap.size());
    }

    private void loadWhitelist() {
        List<String> list = config.getStringList("whitelist");
        if (list == null) {
            getLogger().severe("Whitelist is missing or empty in config.yml");
            return;
        }
        whitelist.addAll(list);
    }

    private void loadCustomWeather() {
        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) config.get("custom-weather");
        if (section == null) return;

        for (Map.Entry<String, Object> entry : section.entrySet()) {
            String worldName = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map)) continue;

            CustomWeatherConfig cfg = new CustomWeatherConfig((Map<String, Object>) value);
            customWeatherConfig.put(worldName, cfg);
        }
    }

    private void loadVanillaWeather() {
        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) config.get("vanilla-weather");
        if (section == null) return;

        for (Map.Entry<String, Object> entry : section.entrySet()) {
            String worldName = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof Map)) continue;

            VanillaWeatherConfig cfg = new VanillaWeatherConfig((Map<String, Object>) value);
            vanillaWeatherConfig.put(worldName, cfg);
        }
    }

    private void validateWorlds() {
        // Приоритет: custom > vanilla. Если мир есть в обоих — выбираем custom, пишем в консоль.
        for (String worldName : vanillaWeatherConfig.keySet()) {
            if (customWeatherConfig.containsKey(worldName)) {
                getLogger().warning("Мир '" + worldName + "' указан и в custom-weather, и в vanilla-weather. Приоритет у custom-weather.");
            }
        }

        // Заполняем режимы для всех миров на сервере
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            WorldMode mode;

            if (!whitelist.contains(name)) {
                // Не в вайтлисте → всегда солнце, никакая смена не работает
                mode = WorldMode.ALWAYS_SUN;
            } else if (customWeatherConfig.containsKey(name)) {
                mode = WorldMode.CUSTOM;
            } else if (vanillaWeatherConfig.containsKey(name)) {
                mode = WorldMode.VANILLA_WITH_MESSAGES;
            } else {
                // В вайтлисте, но нет настроек → обычная ванильная погода без доп. сообщений
                mode = WorldMode.VANILLA;
            }

            worldModeMap.put(name, mode);
        }
    }

    public boolean isWorldAllowedForCommand(String worldName) {
        WorldMode mode = worldModeMap.get(worldName);
        if (mode == null) return false;
        // Команды /weatherage sun/rain/storm разрешены только для CUSTOM и VANILLA_WITH_MESSAGES
        return mode == WorldMode.CUSTOM || mode == WorldMode.VANILLA_WITH_MESSAGES;
    }

    public WorldMode getWorldMode(String worldName) {
        return worldModeMap.getOrDefault(worldName, WorldMode.ALWAYS_SUN);
    }

    public CustomWeatherConfig getCustomConfig(String worldName) {
        return customWeatherConfig.get(worldName);
    }

    public VanillaWeatherConfig getVanillaConfig(String worldName) {
        return vanillaWeatherConfig.get(worldName);
    }

    public Map<String, String> getMessages() {
        Map<String, String> map = new HashMap<>();
        map.put("no-permission", config.getString("messages.no-permission"));
        map.put("reload-success", config.getString("messages.reload-success"));
        map.put("forced-sun", config.getString("messages.forced-sun"));
        map.put("forced-rain", config.getString("messages.forced-rain"));
        map.put("forced-storm", config.getString("messages.forced-storm"));
        return map;
    }

    // Перечисление режимов мира
    public enum WorldMode {
        ALWAYS_SUN,       // не в whitelist
        VANILLA,          // в whitelist, без настроек
        VANILLA_WITH_MESSAGES, // vanilla-weather
        CUSTOM            // custom-weather
    }
}
