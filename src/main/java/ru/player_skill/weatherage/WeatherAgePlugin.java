package ru.player_skill.weatherage;

import io.papermc.paper.event.server.ServerLoadEvent;
import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class WeatherAgePlugin extends JavaPlugin implements Listener {

    private final Map<String, WorldConfig> worldConfigs = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    private final Map<World, BossBar> activeBossBars = new HashMap<>();
    private final List<BukkitRunnable> cycleTasks = new ArrayList<>();

    /** Миры, где следующая смена погоды разрешена (для принудительной установки командой вне вайтлиста). */
    private final Set<String> allowNextWeatherChange = new HashSet<>();

    // ------------------------------------------------------------------
    // Жизненный цикл
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        // При старте всё приводим к солнцу
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }

        getServer().getPluginManager().registerEvents(this, this);

        // Команда /weatherage (обработчик вынесен в отдельный класс)
        WeatherAgeCommand cmd = new WeatherAgeCommand(this);
        getCommand("weatherage").setExecutor(cmd);
        getCommand("weatherage").setTabCompleter(cmd);

        getLogger().info("WeatherAge включен. Миров в вайтлисте: " + whitelist.size());
    }

    @Override
    public void onDisable() {
        // Отменяем таймеры
        for (BukkitRunnable task : cycleTasks) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
        cycleTasks.clear();

        // Убираем боссбары у всех игроков
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

        // Разрешена одна принудительная смена (для миров вне вайтлиста)
        if (allowNextWeatherChange.remove(name)) {
            return;
        }

        // Миры из вайтлиста управляются кастомным циклом — не трогаем
        if (whitelist.contains(name)) {
            return;
        }

        // Все остальные миры: любая смена погоды отменяется, погода держится солнечной.
        // Это же блокирует цвет неба — без дождя и грозы небо не меняется.
        e.setCancelled(true);
        World world = e.getWorld();
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerLoad(ServerLoadEvent e) {
        startCustomWeatherCycles();
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
                // Мир в вайтлисте, но без кастомной погоды — держим солнечно
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
                continue;
            }

            WeatherCycleTask task = new WeatherCycleTask(world, wc);
            cycleTasks.add(task);
            task.runTaskTimer(this, 0L, 20L); // проверка раз в секунду
        }
    }

    // ------------------------------------------------------------------
    // Публичный API для команды
    // ------------------------------------------------------------------

    /** Полная перезагрузка: конфиг + реестры миров + перезапуск циклов. */
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

        reloadConfig();
        loadConfig();
        startCustomWeatherCycles();
    }

    /**
     * Принудительно ставит погоду в мире.
     * type: "sun", "rain" или "storm".
     * Для миров с кастомным конфигом сбрасывает счётчик: начинается новый отсчёт
     * длительности из конфига для выбранного типа погоды.
     * Для миров вне конфига погода ставится напрямую (одна смена разрешается).
     */
    public void forceWeather(World world, String type) {
        WorldConfig wc = worldConfigs.get(world.getName());
        if (wc != null) {
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
        final String bossbarSunText;
        final String chatSunText;

        final boolean enableBossbarRain;
        final String bossbarRainText;
        final String chatRainText;
        final int rainMin, rainMax;
        final double chanceRain;

        final boolean enableBossbarThunder;
        final String bossbarThunderText;
        final String chatThunderText;
        final int thunderMin, thunderMax;
        final double chanceThunder;

        final int sunMin, sunMax;
        final double chanceSun;

        final boolean showChatMessage; // глобальное отключение чата для мира

        private WeatherState currentState;
        private long nextChangeAt = 0L;
        private long stateTotalMs = 0L; // полная длительность текущего состояния (для прогресса боссбара)

        WorldConfig(String worldName, ConfigurationSection sec) {
            this.worldName = worldName;

            enableBossbarSun = sec.getBoolean("enable-bossbar-sun", true);
            bossbarSunText = sec.getString("bossbar-sun-text", "\u2615 Солнечная погода");
            chatSunText = sec.getString("chat-sun-text", "");

            enableBossbarRain = sec.getBoolean("enable-bossbar-rain", true);
            bossbarRainText = sec.getString("bossbar-rain-text", "\uD83C\uDF27 Идёт дождь");
            chatRainText = sec.getString("chat-rain-text", "Начался дождь!");
            rainMin = sec.getInt("rain-min-seconds", 1000);
            rainMax = sec.getInt("rain-max-seconds", 2000);
            chanceRain = sec.getDouble("chance-rain", 0.2);

            enableBossbarThunder = sec.getBoolean("enable-bossbar-thunder", true);
            bossbarThunderText = sec.getString("bossbar-thunder-text", "\u26A1 Гроза!");
            chatThunderText = sec.getString("chat-thunder-text", "Началась гроза!");
            thunderMin = sec.getInt("thunder-min-seconds", 1000);
            thunderMax = sec.getInt("thunder-max-seconds", 2000);
            chanceThunder = sec.getDouble("chance-thunder", 0.1);

            sunMin = sec.getInt("sun-min-seconds", 10000);
            sunMax = sec.getInt("sun-max-seconds", 20000);
            chanceSun = sec.getDouble("chance-sun", 0.7);

            showChatMessage = sec.getBoolean("show-chat-message", true);

            // Первое состояние выбирается случайно, стартуем с солнечной длительности по умолчанию
            currentState = pickNextState();
            long firstDuration = durationFor(currentState);
            stateTotalMs = firstDuration * 1000L;
            nextChangeAt = System.currentTimeMillis() + stateTotalMs;
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
                WeatherState next = pickNextState();
                applyState(world, next);
                currentState = next;
                stateTotalMs = durationFor(next) * 1000L;
                nextChangeAt = now + stateTotalMs;
            } else {
                updateBossbar(world);
            }
        }

        /** Принудительная установка погоды: сбрасывает счётчик, отсчёт начинается заново. */
        void forceWeather(World world, WeatherState state) {
            applyState(world, state);
            currentState = state;
            stateTotalMs = durationFor(state) * 1000L;
            nextChangeAt = System.currentTimeMillis() + stateTotalMs;
        }

        private void applyState(World world, WeatherState state) {
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
                    color = BarColor.YELLOW;
                    break;
                case RAIN:
                    isStorm = true;
                    isThunder = false;
                    chatMsg = chatRainText;
                    bossbarText = bossbarRainText;
                    color = BarColor.BLUE;
                    break;
                default: // THUNDER
                    isStorm = true;
                    isThunder = true;
                    chatMsg = chatThunderText;
                    bossbarText = bossbarThunderText;
                    color = BarColor.PURPLE;
                    break;
            }

            world.setStorm(isStorm);
            world.setThundering(isThunder);
            world.setWeatherDuration(Integer.MAX_VALUE);

            // Сообщение только игрокам этого мира, только если разрешено и текст не пустой
            if (showChatMessage && chatMsg != null && !chatMsg.isEmpty()) {
                for (Player p : world.getPlayers()) {
                    p.sendMessage(chatMsg);
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
                    color = BarColor.YELLOW;
                    break;
                case RAIN:
                    text = bossbarRainText;
                    color = BarColor.BLUE;
                    break;
                default:
                    text = bossbarThunderText;
                    color = BarColor.PURPLE;
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

            BossBar bar = activeBossBars.get(world);
            if (bar == null) {
                bar = Bukkit.createBossBar(text, color, BarStyle.SEGMENTED_10);
                activeBossBars.put(world, bar);
            }

            bar.setTitle(text);
            bar.setColor(color);

            // Прогресс: доля оставшегося времени текущего состояния
            long leftMs = Math.max(0L, nextChangeAt - System.currentTimeMillis());
            float progress = stateTotalMs > 0L
                    ? Math.max(0f, Math.min(1f, (float) leftMs / (float) stateTotalMs))
                    : 1f;
            bar.setProgress(progress);

            // Боссбар видят только игроки этого мира
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(world)) {
                    bar.addPlayer(online);
                } else {
                    bar.removePlayer(online);
                }
            }
        }

        private void removeBossbar(World world) {
            BossBar old = activeBossBars.remove(world);
            if (old != null) {
                old.removeAll();
            }
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
