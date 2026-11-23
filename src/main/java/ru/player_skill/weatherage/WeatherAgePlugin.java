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
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import ru.player_skill.weatherage.command.WeatherAgeCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WeatherAge — кастомная погода по мирам и группам миров.
 */
public final class WeatherAgePlugin extends JavaPlugin implements Listener {

    private final List<WorldConfig> weatherGroups = new ArrayList<>();
    /** Мир → группа, в которую он входит (после отсечения дублей). */
    private final Map<String, WorldConfig> worldToConfig = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    /** Боссбар на группу миров (ключ — идентификатор группы из конфига). */
    private final Map<String, BossBar> activeBossBars = new HashMap<>();
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

        getLogger().info("WeatherAge включен. Миров в вайтлисте: " + whitelist.size()
                + ", групп кастомной погоды: " + weatherGroups.size());
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

        // Блокируем только НАЧАЛО дождя. Окончание пропускаем.
        if (e.toWeatherState()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent e) {
        String name = e.getWorld().getName();

        if (allowNextWeatherChange.contains(name)) {
            return;
        }

        if (whitelist.contains(name)) {
            return;
        }

        // Блокируем только НАЧАЛО грозы, окончание — не трогаем.
        if (e.toThunderState()) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Конфиг
    // ------------------------------------------------------------------

    private void loadConfig() {
        whitelist.clear();
        weatherGroups.clear();
        worldToConfig.clear();

        List<String> wl = getConfig().getStringList("whitelist");
        if (wl != null) {
            for (String s : wl) {
                if (s != null && !s.trim().isEmpty()) {
                    whitelist.add(s.trim());
                }
            }
        }

        ConfigurationSection custom = getConfig().getConfigurationSection("custom-weather");
        if (custom == null) {
            return;
        }

        for (String groupKey : custom.getKeys(false)) {
            ConfigurationSection sec = custom.getConfigurationSection(groupKey);
            if (sec == null) {
                continue;
            }

            // Разбираем имена миров из ключа: "World,World1" → [World, World1].
            List<String> names = new ArrayList<>();
            for (String raw : groupKey.split(",")) {
                if (raw == null) {
                    continue;
                }
                String trimmed = raw.trim();
                if (!trimmed.isEmpty() && !names.contains(trimmed)) {
                    names.add(trimmed);
                }
            }
            if (names.isEmpty()) {
                getLogger().warning("Секция custom-weather '" + groupKey
                        + "' не содержит имён миров — пропущена.");
                continue;
            }

            // Проверка на дубли между группами: один мир — только в одной группе.
            List<String> accepted = new ArrayList<>();
            for (String name : names) {
                WorldConfig existing = worldToConfig.get(name);
                if (existing != null) {
                    getLogger().warning("КОНФЛИКТ ГРУПП: мир '" + name + "' указан в двух группах "
                            + "custom-weather: '" + existing.getGroupId() + "' и '" + groupKey + "'. "
                            + "В группе '" + groupKey + "' этот мир проигнорирован — "
                            + "он подчиняется циклу группы '" + existing.getGroupId() + "'. "
                            + "Уберите его из одной из групп.");
                    continue;
                }
                accepted.add(name);
            }
            if (accepted.isEmpty()) {
                getLogger().warning("Группа '" + groupKey + "': все её миры уже заняты "
                        + "другими группами — секция пропущена.");
                continue;
            }

            WorldConfig wc = new WorldConfig(groupKey, accepted, sec);
            weatherGroups.add(wc);
            for (String name : accepted) {
                worldToConfig.put(name, wc);
            }
        }
    }

    private void startCustomWeatherCycles() {
        for (WorldConfig wc : weatherGroups) {
            boolean hasWorld = false;
            for (String name : wc.getWorldNames()) {
                if (!whitelist.contains(name)) {
                    getLogger().warning("Мир '" + name + "' (группа '" + wc.getGroupId()
                            + "') не в вайтлисте — исключён из цикла, погода в нём всегда солнечная. "
                            + "Добавьте его в whitelist или уберите из группы.");
                    continue;
                }
                if (Bukkit.getWorld(name) != null) {
                    hasWorld = true;
                } else {
                    getLogger().warning("Мир '" + name + "' (группа '" + wc.getGroupId()
                            + "') не найден (возможно, ещё не загружен).");
                }
            }

            if (!hasWorld) {
                getLogger().warning("Группа '" + wc.getGroupId()
                        + "': не осталось ни одного рабочего мира — цикл не запущен.");
                continue;
            }

            wc.init(); // первичная установка погоды и боссбара (без чата)

            WeatherCycleTask task = new WeatherCycleTask(wc);
            cycleTasks.add(task);
            task.runTaskTimer(this, 0L, 20L);

            getLogger().info("Группа '" + wc.getGroupId() + "' запущена ("
                    + wc.getWorldNames().toString() + ").");
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
        loadConfig(); // здесь же сработает проверка на дубли групп

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

    /**
     * Принудительно ставит погоду в мире.
     */
    public void forceWeather(World world, String type) {
        WorldConfig wc = worldToConfig.get(world.getName());
        if (wc != null && whitelist.contains(world.getName())) {
            WeatherState state;
            if ("sun".equalsIgnoreCase(type)) {
                state = WeatherState.SUN;
            } else if ("rain".equalsIgnoreCase(type)) {
                state = WeatherState.RAIN;
            } else {
                state = WeatherState.THUNDER;
            }
            wc.forceWeather(state);
        } else {
            allowNextWeatherChange.add(world.getName());
            world.setStorm("sun".equalsIgnoreCase(type) ? false : true);
            world.setThundering("storm".equalsIgnoreCase(type));
            if ("sun".equalsIgnoreCase(type)) {
                world.setWeatherDuration(Integer.MAX_VALUE);
            } else {
                // Для дождя/грозы ставим случайную длительность, чтобы не ломать ванильный цикл полностью
                int duration = ThreadLocalRandom.current().nextInt(600, 1200);
                world.setWeatherDuration(duration);
            }
        }
    }

    /**
     * Проверяет, находится ли мир в whitelist.
     * Нужен для WeatherAgeCommand.
     */
    public boolean isWhitelisted(World world) {
        return whitelist.contains(world.getName());
    }
}
