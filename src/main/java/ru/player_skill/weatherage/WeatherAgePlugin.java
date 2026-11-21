package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import ru.player_skill.weatherage.command.WeatherAgeCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WeatherAge — кастомная погода по мирам.
 *
 * Логика режимов:
 *  - Мир в вайтлисте + есть секция custom-weather → кастомный цикл (солнце/дождь/гроза,
 *    боссбары, чат-оповещения).
 *  - Мир в вайтлисте + нет секции → обычная ванильная погода (плагин не вмешивается).
 *  - Мир не в вайтлисте → погода всегда солнечная (любая смена погоды отменяется).
 */
public final class WeatherAgePlugin extends JavaPlugin implements Listener {

    private final Map<String, WorldConfig> worldConfigs = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    private final Map<World, BossBar> activeBossBars = new HashMap<>();
    private final List<BukkitRunnable> cycleTasks = new ArrayList<>();

    /** Миры, где разрешена одна принудительная смена погоды (для миров вне вайтлиста). */
    private final Set<String> allowNextWeatherChange = new HashSet<>();

    // ------------------------------------------------------------------
    // Жизненный цикл
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        // Солнце ставим только НЕ-вайтлистным мирам.
        for (World world : Bukkit.getWorlds()) {
            if (whitelist.contains(world.getName())) {
                continue;
            }
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }

        getServer().getPluginManager().registerEvents(this, this);

        WeatherAgeCommand cmd = new WeatherAgeCommand(this);
        getCommand("weatherage").setExecutor(cmd);
        getCommand("weatherage").setTabCompleter(cmd);

        startCustomWeatherCycles();

        getLogger().info("WeatherAge включен. Миров в вайтлисте: " + whitelist.size());
    }

    @Override
    public void onDisable() {
        for (BukkitRunnable task : cycleTasks) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        cycleTasks.clear();

        for (BossBar bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();
    }

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent e) {
        String name = e.getWorld().getName();

        if (allowNextWeatherChange.remove(name)) {
            return;
        }

        if (whitelist.contains(name)) {
            return;
        }

        // Запрещаем смену погоды. Внутри обработчика нельзя вызывать setStorm —
        // иначе бесконечная рекурсия (WeatherChangeEvent сам себя перезапускает).
        e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Конфиг
    // ------------------------------------------------------------------

    private void loadConfig() {
        whitelist.clear();
        worldConfigs.clear();

        List<String> wl = getConfig().getStringList("whitelist");
        if (wl != null) {
            for (String s : wl) {
                if (s != null && !s.trim().isEmpty()) {
                    whitelist.add(s.trim());
                }
            }
        }

        ConfigurationSection custom = getConfig().getConfigurationSection("custom-weather");
        if (custom != null) {
            for (String worldName : custom.getKeys(false)) {
                ConfigurationSection sec = custom.getConfigurationSection(worldName);
                if (sec != null) {
                    worldConfigs.put(worldName, new WorldConfig(worldName, sec));
                }
            }
        }
    }

    private void startCustomWeatherCycles() {
        for (String name : whitelist) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                getLogger().warning("Мир '" + name + "' из вайтлиста не найден (возможно, ещё не загружен).");
                continue;
            }

            WorldConfig wc = worldConfigs.get(name);
            if (wc == null) {
                getLogger().info("Мир '" + name + "': в вайтлисте без секции custom-weather — обычная ванильная погода.");
                continue;
            }

            // Первичная установка погоды и боссбара — чтобы всё сходило с первой секунды.
            wc.init(world);

            WeatherCycleTask task = new WeatherCycleTask(world, wc);
            cycleTasks.add(task);
            task.runTaskTimer(this, 0L, 20L);
        }

        for (String cfgWorld : worldConfigs.keySet()) {
            if (!whitelist.contains(cfgWorld)) {
                getLogger().warning("Для мира '" + cfgWorld + "' есть секция custom-weather, "
                        + "но мир не в вайтлисте — настройки игнорируются, погода в мире всегда солнечная. "
                        + "Добавьте мир в whitelist или удалите секцию.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Публичный API для команды
    // ------------------------------------------------------------------

    public void reloadAll() {
        for (BukkitRunnable task : cycleTasks) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        cycleTasks.clear();

        for (BossBar bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();
        allowNextWeatherChange.clear();

        reloadConfig();
        loadConfig();

        for (World world : Bukkit.getWorlds()) {
            if (whitelist.contains(world.getName())) {
                continue;
            }
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }

        startCustomWeatherCycles();
    }

    public void forceWeather(World world, String type) {
        WorldConfig wc = worldConfigs.get(world.getName());
        if (wc != null && whitelist.contains(world.getName())) {
            WeatherState state;
            if ("sun".equalsIgnoreCase(type)) {
                state = WeatherState.SUN;
            } else if ("rain".equalsIgnoreCase(type)) {
                state = WeatherState.RAIN;
            } else {
                state = WeatherState.THUNDER;
            }
            wc.forceWeather(world, state);
        } else {
            allowNextWeatherChange.add(world.getName());
            boolean storm = !"sun".equalsIgnoreCase(type);
            boolean thunder = "storm".equalsIgnoreCase(type);
            world.setStorm(storm);
            world.setThundering(thunder);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
    }

    // ------------------------------------------------------------------
    // Конфигурация одного мира
    // ------------------------------------------------------------------

    private class WorldConfig {
        final String worldName;

        final boolean enableBossbarSun;
        final String bossbarSunMode;          // "countdown" | "fixed"
        final int bossbarSunDisplaySeconds;   // fixed: сколько секунд показывать, -1 = вся погода
        final double bossbarSunFixedProgress; // fixed: уровень бара 0.0..1.0
        final String bossbarSunText;
        final String chatSunText;
        final BarStyle bossbarSunStyle;
        final BarColor bossbarSunColor;

        final boolean enableBossbarRain;
        final String bossbarRainMode;
        final int bossbarRainDisplaySeconds;
        final double bossbarRainFixedProgress;
        final String bossbarRainText;
        final String chatRainText;
        final BarStyle bossbarRainStyle;
        final BarColor bossbarRainColor;
        final int rainMin, rainMax;
        final double chanceRain;

        final boolean enableBossbarThunder;
        final String bossbarThunderMode;
        final int bossbarThunderDisplaySeconds;
        final double bossbarThunderFixedProgress;
        final String bossbarThunderText;
        final String chatThunderText;
        final BarStyle bossbarThunderStyle;
        final BarColor bossbarThunderColor;
        final int thunderMin, thunderMax;
        final double chanceThunder;

        final int sunMin, sunMax;
        final double chanceSun;

        final boolean showChatMessage;

        private WeatherState currentState;
        private long nextChangeAt = 0L;
        private long stateTotalMs = 0L;

        WorldConfig(String worldName, ConfigurationSection sec) {
            this.worldName = worldName;

            // ---------- Солнце ----------
            enableBossbarSun = sec.getBoolean("enable-bossbar-sun", true);
            bossbarSunMode = normalizeMode(sec.getString("bossbar-sun-mode", "countdown"));
            bossbarSunDisplaySeconds = sec.getInt("bossbar-sun-display-seconds", -1);
            bossbarSunFixedProgress = clampProgress(sec.getDouble("bossbar-sun-fixed-progress", 1.0));
            bossbarSunText = sec.getString("bossbar-sun-text", "\u2615 Солнечная погода");
            chatSunText = sec.getString("chat-sun-text", "");
            String sunStyleRaw = sec.getString("bossbar-sun-style", defaultStyleFor(bossbarSunMode));
            bossbarSunStyle = parseStyle(sunStyleRaw);
            String sunColorRaw = sec.getString("bossbar-sun-color", "YELLOW");
            bossbarSunColor = parseColor(sunColorRaw, BarColor.YELLOW);

            // ---------- Дождь ----------
            enableBossbarRain = sec.getBoolean("enable-bossbar-rain", true);
            bossbarRainMode = normalizeMode(sec.getString("bossbar-rain-mode", "countdown"));
            bossbarRainDisplaySeconds = sec.getInt("bossbar-rain-display-seconds", -1);
            bossbarRainFixedProgress = clampProgress(sec.getDouble("bossbar-rain-fixed-progress", 1.0));
            bossbarRainText = sec.getString("bossbar-rain-text", "\uD83C\uDF27 Идёт дождь");
            chatRainText = sec.getString("chat-rain-text", "Начался дождь!");
            String rainStyleRaw = sec.getString("bossbar-rain-style", defaultStyleFor(bossbarRainMode));
            bossbarRainStyle = parseStyle(rainStyleRaw);
            String rainColorRaw = sec.getString("bossbar-rain-color", "BLUE");
            bossbarRainColor = parseColor(rainColorRaw, BarColor.BLUE);
            rainMin = sec.getInt("rain-min-seconds", 1000);
            rainMax = sec.getInt("rain-max-seconds", 2000);
            chanceRain = sec.getDouble("chance-rain", 0.2);

            // ---------- Гроза ----------
            enableBossbarThunder = sec.getBoolean("enable-bossbar-thunder", true);
            bossbarThunderMode = normalizeMode(sec.getString("bossbar-thunder-mode", "countdown"));
            bossbarThunderDisplaySeconds = sec.getInt("bossbar-thunder-display-seconds", -1);
            bossbarThunderFixedProgress = clampProgress(sec.getDouble("bossbar-thunder-fixed-progress", 1.0));
            bossbarThunderText = sec.getString("bossbar-thunder-text", "\u26A1 Гроза!");
            chatThunderText = sec.getString("chat-thunder-text", "Началась гроза!");
            String thunderStyleRaw = sec.getString("bossbar-thunder-style", defaultStyleFor(bossbarThunderMode));
            bossbarThunderStyle = parseStyle(thunderStyleRaw);
            String thunderColorRaw = sec.getString("bossbar-thunder-color", "PURPLE");
            bossbarThunderColor = parseColor(thunderColorRaw, BarColor.PURPLE);
            thunderMin = sec.getInt("thunder-min-seconds", 1000);
            thunderMax = sec.getInt("thunder-max-seconds", 2000);
            chanceThunder = sec.getDouble("chance-thunder", 0.1);

            // ---------- Тайминги солнца ----------
            sunMin = sec.getInt("sun-min-seconds", 10000);
            sunMax = sec.getInt("sun-max-seconds", 20000);
            chanceSun = sec.getDouble("chance-sun", 0.7);

            showChatMessage = sec.getBoolean("show-chat-message", true);

            currentState = pickNextState();
        }

        /**
         * Первичная установка погоды и боссбара при запуске цикла.
         * Чат не трогаем: погода не «сменялась», она просто установилась.
         */
        void init(World world) {
            long duration = durationFor(currentState);
            stateTotalMs = duration * 1000L;
            nextChangeAt = System.currentTimeMillis() + stateTotalMs;
            applyState(world, currentState, false);
        }

        private WeatherState pickNextState() {
            double r = ThreadLocalRandom.current().nextDouble();
            double sum = chanceSun;
            if (r < sum) {
                return WeatherState.SUN;
            }
            sum += chanceRain;
            if (r < sum) {
                return WeatherState.RAIN;
            }
            return WeatherState.THUNDER;
        }

        private long durationFor(WeatherState state) {
            switch (state) {
                case SUN:
                    return randomRange(sunMin, sunMax);
                case RAIN:
                    return randomRange(rainMin, rainMax);
                default:
                    return randomRange(thunderMin, thunderMax);
            }
        }

        private long randomRange(int min, int max) {
            if (max < min) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            return ThreadLocalRandom.current().nextLong(min, max + 1);
        }

        void update(World world) {
            long now = System.currentTimeMillis();
            if (now >= nextChangeAt) {
                // Сначала обновляем счётчики нового состояния, потом применяем погоду —
                // расчёт elapsed внутри updateBossbar идёт по актуальному состоянию.
                WeatherState next = pickNextState();
                boolean changed = (next != currentState);
                currentState = next;
                stateTotalMs = durationFor(next) * 1000L;
                nextChangeAt = now + stateTotalMs;
                applyState(world, next, changed);
            } else {
                updateBossbar(world);
            }
        }

        /**
         * Принудительная установка погоды: сбрасывает счётчик, отсчёт начинается заново.
         * Если поставили тот же тип погоды — только сброс таймера, без чата.
         */
        void forceWeather(World world, WeatherState state) {
            boolean changed = (state != currentState);
            currentState = state;
            stateTotalMs = durationFor(state) * 1000L;
            nextChangeAt = System.currentTimeMillis() + stateTotalMs;
            applyState(world, state, changed);
        }

        /**
         * Применяет состояние к миру. Сообщение в чат — только если погода
         * реально сменилась (announce = true) и текст непустой.
         */
        private void applyState(World world, WeatherState state, boolean announce) {
            boolean isStorm;
            boolean isThunder;

            String chatMsg;
            String bossbarText;
            BarColor color;

            switch (state) {
                case SUN:
                    isStorm = false;
                    isThunder = false;
                    chatMsg = chatSunText;
                    bossbarText = bossbarSunText;
                    color = bossbarSunColor;
                    break;
                case RAIN:
                    isStorm = true;
                    isThunder = false;
                    chatMsg = chatRainText;
                    bossbarText = bossbarRainText;
                    color = bossbarRainColor;
                    break;
                default: // THUNDER
                    isStorm = true;
                    isThunder = true;
                    chatMsg = chatThunderText;
                    bossbarText = bossbarThunderText;
                    color = bossbarThunderColor;
                    break;
            }

            world.setStorm(isStorm);
            world.setThundering(isThunder);
            world.setWeatherDuration(Integer.MAX_VALUE);

            if (announce && showChatMessage && chatMsg != null && !chatMsg.isEmpty()) {
                String coloredMsg = cc(chatMsg);
                for (Player p : world.getPlayers()) {
                    p.sendMessage(coloredMsg);
                }
            }

            updateBossbar(world, bossbarText, color);
        }

        private void updateBossbar(World world) {
            String text;
            BarColor color;
            switch (currentState) {
                case SUN:
                    text = bossbarSunText;
                    color = bossbarSunColor;
                    break;
                case RAIN:
                    text = bossbarRainText;
                    color = bossbarRainColor;
                    break;
                default:
                    text = bossbarThunderText;
                    color = bossbarThunderColor;
                    break;
            }
            updateBossbar(world, text, color);
        }

        private void updateBossbar(World world, String text, BarColor color) {
            boolean showBar;
            switch (currentState) {
                case SUN:
                    showBar = enableBossbarSun;
                    break;
                case RAIN:
                    showBar = enableBossbarRain;
                    break;
                default:
                    showBar = enableBossbarThunder;
                    break;
            }

            if (!showBar) {
                removeBossbar(world);
                return;
            }

            boolean fixed = isFixedMode(currentState);

            // В fixed-режиме бар может скрываться раньше конца погоды (display-seconds)
            if (fixed) {
                int disp = displaySecondsFor(currentState);
                if (disp >= 0) {
                    long stateStart = nextChangeAt - stateTotalMs;
                    long elapsedMs = System.currentTimeMillis() - stateStart;
                    if (elapsedMs >= disp * 1000L) {
                        removeBossbar(world);
                        return;
                    }
                }
            }

            BarStyle style = styleFor(currentState);

            BossBar bar = activeBossBars.get(world);
            if (bar == null) {
                bar = Bukkit.createBossBar(cc(text), color, style);
                activeBossBars.put(world, bar);
            }

            bar.setTitle(cc(text));
            bar.setColor(color);
            bar.setStyle(style);

            float progress;
            if (fixed) {
                progress = (float) fixedProgressFor(currentState);
            } else {
                long leftMs = Math.max(0L, nextChangeAt - System.currentTimeMillis());
                progress = stateTotalMs > 0L
                        ? Math.max(0f, Math.min(1f, (float) leftMs / (float) stateTotalMs))
                        : 1f;
            }
            bar.setProgress(progress);

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(world)) {
                    bar.addPlayer(online);
                } else {
                    bar.removePlayer(online);
                }
            }
        }

        private BarStyle styleFor(WeatherState state) {
            switch (state) {
                case SUN:
                    return bossbarSunStyle;
                case RAIN:
                    return bossbarRainStyle;
                default:
                    return bossbarThunderStyle;
            }
        }

        private boolean isFixedMode(WeatherState state) {
            String mode;
            switch (state) {
                case SUN:
                    mode = bossbarSunMode;
                    break;
                case RAIN:
                    mode = bossbarRainMode;
                    break;
                default:
                    mode = bossbarThunderMode;
                    break;
            }
            return "fixed".equals(mode);
        }

        private int displaySecondsFor(WeatherState state) {
            switch (state) {
                case SUN:
                    return bossbarSunDisplaySeconds;
                case RAIN:
                    return bossbarRainDisplaySeconds;
                default:
                    return bossbarThunderDisplaySeconds;
            }
        }

        private double fixedProgressFor(WeatherState state) {
            switch (state) {
                case SUN:
                    return bossbarSunFixedProgress;
                case RAIN:
                    return bossbarRainFixedProgress;
                default:
                    return bossbarThunderFixedProgress;
            }
        }

        private void removeBossbar(World world) {
            BossBar old = activeBossBars.remove(world);
            if (old != null) {
                old.removeAll();
            }
        }
    }

    /** Переводит &-коды цветов. null → пустая строка. */
    private static String cc(String raw) {
        return raw == null ? "" : ChatColor.translateAlternateColorCodes('&', raw);
    }

    private static String normalizeMode(String raw) {
        if (raw == null) {
            return "countdown";
        }
        return "fixed".equalsIgnoreCase(raw.trim()) ? "fixed" : "countdown";
    }

    private static double clampProgress(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String defaultStyleFor(String mode) {
        return "fixed".equals(mode) ? "SOLID" : "SEGMENTED_10";
    }

    /**
     * Парсит имя стиля. Значение по умолчанию уже подставлено при чтении конфига
     * (defaultStyleFor), поэтому fallback нужен только для опечаток.
     */
    private static BarStyle parseStyle(String raw) {
        if (raw == null) {
            return BarStyle.SEGMENTED_10;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return BarStyle.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return BarStyle.SEGMENTED_10;
        }
    }

    /** Парсит имя цвета с fallback'ом на дефолт при опечатке. */
    private static BarColor parseColor(String raw, BarColor def) {
        if (raw == null) {
            return def;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return BarColor.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private enum WeatherState { SUN, RAIN, THUNDER }

    // ------------------------------------------------------------------
    // Задача-цикл
    // ------------------------------------------------------------------

    private class WeatherCycleTask extends BukkitRunnable {
        private final World world;
        private final WorldConfig config;

        WeatherCycleTask(World world, WorldConfig config) {
            this.world = world;
            this.config = config;
        }

        @Override
        public void run() {
            config.update(world);
        }
    }
}
