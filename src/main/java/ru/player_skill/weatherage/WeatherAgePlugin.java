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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

public final class WeatherAgePlugin extends JavaPlugin implements Listener {

    private final List<WorldConfig> weatherGroups = new ArrayList<>();
    private final Map<String, WorldConfig> worldToConfig = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    private final Map<String, BossBar> activeBossBars = new HashMap<>();
    private final List<BukkitRunnable> cycleTasks = new ArrayList<>();

    // Ванильные настройки по мирам: ключ — имя мира
    private final Map<String, VanillaWorldSettings> vanillaByWorld = new HashMap<>();

    // Для защиты от дублей сообщений при паре событий (дождь+гроза)
    private final Map<String, BukkitRunnable> pendingChecks = new HashMap<>();
    private final Map<String, WeatherState> lastVanillaState = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        // При старте фиксируем состояние ванильных миров, чтобы не спамить сообщением
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (whitelist.contains(name) && !worldToConfig.containsKey(name)) {
                lastVanillaState.put(name, currentStateOf(world));
            } else if (!whitelist.contains(name)) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        WeatherAgeCommand cmd = new WeatherAgeCommand(this);
        Objects.requireNonNull(getCommand("weatherage")).setExecutor(cmd);
        getCommand("weatherage").setTabCompleter(cmd);

        startCustomWeatherCycles();

        getLogger().info("WeatherAge включен. Ванильных миров с настройками: " + vanillaByWorld.size());
    }

    @Override
    public void onDisable() {
        for (BukkitRunnable task : cycleTasks) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
        }
        cycleTasks.clear();
        for (BukkitRunnable task : pendingChecks.values()) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
        }
        pendingChecks.clear();
        for (BossBar bar : activeBossBars.values()) bar.removeAll();
        activeBossBars.clear();
    }

    // ------------------------------------------------------------------
    // События погоды
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent e) {
        String name = e.getWorld().getName();
        if (whitelist.contains(name)) return;
        if (e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent e) {
        String name = e.getWorld().getName();
        if (whitelist.contains(name)) return;
        if (e.toThunderState()) e.setCancelled(true);
    }

    // MONITOR — сюда попадают только неотменённые события.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChangeMonitor(WeatherChangeEvent e) {
        scheduleVanillaCheck(e.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunderChangeMonitor(ThunderChangeEvent e) {
        scheduleVanillaCheck(e.getWorld().getName());
    }

    // ------------------------------------------------------------------
    // Ванильные миры: один мир — свои настройки
    // ------------------------------------------------------------------

    private void scheduleVanillaCheck(final String worldName) {
        // Только для ванильных миров (в вайтлисте, но без кастомной группы)
        if (!whitelist.contains(worldName) || worldToConfig.containsKey(worldName)) {
            return;
        }

        // Если уже есть задача — отменяем, ставим новую (защита от дублей при быстрых событиях)
        BukkitRunnable old = pendingChecks.remove(worldName);
        if (old != null) {
            try { old.cancel(); } catch (IllegalStateException ignored) {}
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                pendingChecks.remove(worldName);
                World world = Bukkit.getWorld(worldName);
                if (world == null) return;

                WeatherState now = currentStateOf(world);
                WeatherState last = lastVanillaState.get(worldName);

                // Сообщение только если состояние реально изменилось
                if (last == null || now != last) {
                    lastVanillaState.put(worldName, now);
                    announceVanilla(worldName, now);
                }
            }
        };
        pendingChecks.put(worldName, task);
        task.runTaskLater(this, 1L); // 1 тик задержки — чтобы поймать итоговое состояние (дождь/гроза)
    }

    private WeatherState currentStateOf(World world) {
        if (world.hasStorm()) {
            return world.isThundering() ? WeatherState.THUNDER : WeatherState.RAIN;
        }
        return WeatherState.SUN;
    }

    private void announceVanilla(String worldName, WeatherState state) {
        VanillaWorldSettings settings = vanillaByWorld.get(worldName);
        if (settings == null || !settings.showChat) return;

        String text;
        List<String> commands;

        switch (state) {
            case SUN:
                text = settings.sunText;
                commands = settings.sunCmds;
                break;
            case RAIN:
                text = settings.rainText;
                commands = settings.rainCmds;
                break;
            default:
                text = settings.thunderText;
                commands = settings.thunderCmds;
                break;
        }

        if (text != null && !text.isEmpty()) {
            String msg = cc(text);
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player p : world.getPlayers()) {
                    p.sendMessage(msg);
                }
            }
        }

        if (!commands.isEmpty()) {
            CommandSender console = Bukkit.getConsoleSender();
            for (String raw : commands) {
                if (raw == null || raw.trim().isEmpty()) continue;
                String cmd = raw.trim().replace("%world%", worldName);
                Bukkit.dispatchCommand(console, cmd);
            }
        }
    }

    private static final class VanillaWorldSettings {
        final boolean showChat;
        final String sunText;
        final String rainText;
        final String thunderText;
        final List<String> sunCmds;
        final List<String> rainCmds;
        final List<String> thunderCmds;

        VanillaWorldSettings(ConfigurationSection sec) {
            showChat = sec.getBoolean("show-chat-message", true);
            sunText = sec.getString("chat-sun-text", "");
            rainText = sec.getString("chat-rain-text", "");
            thunderText = sec.getString("chat-thunder-text", "");

            sunCmds = safeList(sec.getStringList("commands-start-sun"));
            rainCmds = safeList(sec.getStringList("commands-start-rain"));
            thunderCmds = safeList(sec.getStringList("commands-start-thunder"));
        }

        private static List<String> safeList(List<String> raw) {
            List<String> out = new ArrayList<>();
            if (raw != null) {
                for (String s : raw) {
                    if (s != null && !s.trim().isEmpty()) out.add(s.trim());
                }
            }
            return out;
        }
    }

    // ------------------------------------------------------------------
    // Конфиг
    // ------------------------------------------------------------------

    private void loadConfig() {
        whitelist.clear();
        weatherGroups.clear();
        worldToConfig.clear();
        vanillaByWorld.clear();

        List<String> wl = getConfig().getStringList("whitelist");
        if (wl != null) {
            for (String s : wl) {
                if (s != null && !s.trim().isEmpty()) whitelist.add(s.trim());
            }
        }

        ConfigurationSection vanillaRoot = getConfig().getConfigurationSection("vanilla-weather");
        if (vanillaRoot != null) {
            for (String worldName : vanillaRoot.getKeys(false)) {
                ConfigurationSection sec = vanillaRoot.getConfigurationSection(worldName);
                if (sec != null) {
                    vanillaByWorld.put(worldName, new VanillaWorldSettings(sec));
                }
            }
        }

        ConfigurationSection custom = getConfig().getConfigurationSection("custom-weather");
        if (custom == null) return;

        for (String groupKey : custom.getKeys(false)) {
            ConfigurationSection sec = custom.getConfigurationSection(groupKey);
            if (sec == null) continue;

            List<String> names = new ArrayList<>();
            for (String raw : groupKey.split(",")) {
                String trimmed = raw == null ? "" : raw.trim();
                if (!trimmed.isEmpty() && !names.contains(trimmed)) names.add(trimmed);
            }
            if (names.isEmpty()) {
                getLogger().warning("Секция custom-weather '" + groupKey + "' без миров — пропущена.");
                continue;
            }

            List<String> accepted = new ArrayList<>();
            for (String name : names) {
                if (worldToConfig.containsKey(name)) {
                    getLogger().warning("КОНФЛИКТ ГРУПП: мир '" + name + "' в двух группах. Оставляем первую.");
                    continue;
                }
                accepted.add(name);
            }
            if (accepted.isEmpty()) continue;

            WorldConfig wc = new WorldConfig(groupKey, accepted, sec);
            weatherGroups.add(wc);
            for (String name : accepted) worldToConfig.put(name, wc);
        }
    }

    private void startCustomWeatherCycles() {
        for (WorldConfig wc : weatherGroups) {
            boolean hasWorld = false;
            for (String name : wc.getWorldNames()) {
                if (!whitelist.contains(name)) {
                    getLogger().warning("Мир '" + name + "' (группа '" + wc.getGroupId() + "') не в вайтлисте — исключён из цикла.");
                    continue;
                }
                if (Bukkit.getWorld(name) != null) hasWorld = true;
            }
            if (!hasWorld) {
                getLogger().warning("Группа '" + wc.getGroupId() + "': нет рабочих миров — цикл не запущен.");
                continue;
            }
            wc.init();
            WeatherCycleTask task = new WeatherCycleTask(wc);
            cycleTasks.add(task);
            task.runTaskTimer(this, 0L, 20L);
            getLogger().info("Группа '" + wc.getGroupId() + "' запущена: " + wc.getWorldNames());
        }
    }

    public void reloadAll() {
        for (BukkitRunnable t : cycleTasks) { try { t.cancel(); } catch (IllegalStateException ignored) {} }
        cycleTasks.clear();
        for (BukkitRunnable t : pendingChecks.values()) { try { t.cancel(); } catch (IllegalStateException ignored) {} }
        pendingChecks.clear();
        for (BossBar b : activeBossBars.values()) b.removeAll();
        activeBossBars.clear();

        reloadConfig();
        loadConfig();

        lastVanillaState.clear();
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (whitelist.contains(name) && !worldToConfig.containsKey(name)) {
                lastVanillaState.put(name, currentStateOf(world));
            } else if (!whitelist.contains(name)) {
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
            }
        }

        startCustomWeatherCycles();
    }

    public void forceWeather(World world, String type) {
        String name = world.getName();
        WorldConfig wc = worldToConfig.get(name);
        if (wc != null && whitelist.contains(name)) {
            WeatherState state = switch (type.toLowerCase()) {
                case "sun" -> WeatherState.SUN;
                case "rain" -> WeatherState.RAIN;
                default -> WeatherState.THUNDER;
            };
            wc.forceWeather(state);
        } else {
            world.setStorm(!"sun".equalsIgnoreCase(type));
            world.setThundering("storm".equalsIgnoreCase(type) || "thunder".equalsIgnoreCase(type));
            world.setWeatherDuration(Integer.MAX_VALUE);
            lastVanillaState.put(name, currentStateOf(world)); // чтобы не было анонса сразу после команды
        }
    }

    public boolean isWhitelisted(World world) {
        return whitelist.contains(world.getName());
    }
