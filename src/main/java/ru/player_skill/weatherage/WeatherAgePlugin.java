package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WeatherAgePlugin extends JavaPlugin {

    private FileConfiguration config;
    private final Set<String> whitelist = new HashSet<>();
    
    // Карта режимов: мир -> режим (ALWAYS_SUN, VANILLA, и т.д.)
    private final Map<String, WorldMode> worldModeMap = new HashMap<>();
    
    // Конфигурационные секции
    private final Map<String, CustomWeatherConfig> customWeatherConfig = new HashMap<>();
    private final Map<String, VanillaWeatherConfig> vanillaWeatherConfig = new HashMap<>();

    // Компоненты плагина (менеджеры и хелперы)
    private CustomWeatherManager customWeatherManager;
    
    // Слушатели (нужны для регистрации и доступа к tick() у VanillaWeatherListener)
    private Listener weatherGuardListener;
    private VanillaWeatherListener vanillaListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        loadWhitelist();
        loadCustomWeather();
        loadVanillaWeather();
        validateWorlds();

        // --- Инициализация компонентов ---
        
        // 1. Менеджер кастомной погоды
        customWeatherManager = new CustomWeatherManager(this);

        // 2. Слушатель защиты (отмена погоды в ALWAYS_SUN)
        weatherGuardListener = new WeatherChangeEventGuard(this);
        getServer().getPluginManager().registerEvents(weatherGuardListener, this);

        // 3. Слушатель ванильной погоды (сообщения + защита от дублей дождь/гроза)
        vanillaListener = new VanillaWeatherListener(this);
        getServer().getPluginManager().registerEvents(vanillaListener, this);

        // 4. Запуск тикера для уменьшения кулдауна грозы (защита от дублей)
        new BukkitRunnable() {
            @Override
            public void run() {
                vanillaListener.tick();
            }
        }.runTaskTimer(this, 20L, 20L); // Каждую секунду (20 тиков)

        // 5. Запуск кастомных циклов для всех миров с режимом CUSTOM
        for (String worldName : worldModeMap.keySet()) {
            if (getWorldMode(worldName) == WorldMode.CUSTOM) {
                customWeatherManager.startCustomCycle(worldName);
            }
        }

        // Регистрация команды
        getCommand("weatherage").setExecutor(new WeatherAgeCommand(this));

        getLogger().info("WeatherAge enabled. Loaded worlds: " + worldModeMap.size());
    }

    @Override
    public void onDisable() {
        // При выключении плагина можно остановить активные циклы, если нужно.
        // Сейчас BukkitRunnable сами умрут при выгрузке плагина.
        getLogger().info("WeatherAge disabled.");
    }

    private void loadWhitelist() {
        List<String> list = config.getStringList("whitelist");
        if (list == null || list.isEmpty()) {
            getLogger().severe("Whitelist is missing or empty in config.yml. All worlds will be ALWAYS_SUN!");
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
            if (!(value instanceof Map)) {
                getLogger().warning("Invalid config for world '" + worldName + "' in custom-weather.");
                continue;
            }
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
            if (!(value instanceof Map)) {
                getLogger().warning("Invalid config for world '" + worldName + "' in vanilla-weather.");
                continue;
            }
            VanillaWeatherConfig cfg = new VanillaWeatherConfig((Map<String, Object>) value);
            vanillaWeatherConfig.put(worldName, cfg);
        }
    }

    private void validateWorlds() {
        // Проверка конфликта: custom и vanilla для одного мира
        for (String worldName : vanillaWeatherConfig.keySet()) {
            if (customWeatherConfig.containsKey(worldName)) {
                getLogger().warning(
                    "Мир '" + worldName + "' указан и в custom-weather, и в vanilla-weather. Приоритет у custom-weather."
                );
            }
        }

        // Расстановка режимов для всех миров на сервере
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
                // В вайтлисте, но без настроек → обычная ванильная погода
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

    public CustomWeatherManager getCustomWeatherManager() {
        return customWeatherManager;
    }

    public Map<String, String> getMessages() {
        Map<String, String> map = new HashMap<>();
        map.put("no-permission", config.getString("messages.no-permission", "&cУ вас нет прав для этой команды."));
        map.put("reload-success", config.getString("messages.reload-success", "&aКонфиг перезагружен."));
        map.put("forced-sun", config.getString("messages.forced-sun", "&6Принудительно установлено солнце в мире %world%"));
        map.put("forced-rain", config.getString("messages.forced-rain", "&6Принудительно установлен дождь в мире %world%"));
        map.put("forced-storm", config.getString("messages.forced-storm", "&6Принудительно установлена гроза в мире %world%"));
        return map;
    }

    // Перечисление режимов мира (оставляем внутри класса для удобства, но лучше вынести в отдельный файл)
    public enum WorldMode {
        ALWAYS_SUN,           // не в whitelist
        VANILLA,              // в whitelist, без настроек
        VANILLA_WITH_MESSAGES,// vanilla-weather
        CUSTOM                // custom-weather
    }
}
