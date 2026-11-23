package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CustomWeatherManager {

    private final WeatherAgePlugin plugin;
    private final Map<String, ConfigurationSection> sections = new HashMap<>();
    private final Map<UUID, String> currentWeather = new HashMap<>();
    private final Map<UUID, Integer> timeLeft = new HashMap<>();
    private final Map<UUID, Integer> totalDuration = new HashMap<>();
    private final Map<UUID, WeatherBossBar> bossBars = new HashMap<>();
    private final Map<UUID, Integer> fixedBarTimeLeft = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask task;

    public CustomWeatherManager(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        stop();

        // Preserve ongoing cycles across /weatherage reload
        Map<UUID, String> savedWeather = new HashMap<>(currentWeather);
        Map<UUID, Integer> savedLeft = new HashMap<>(timeLeft);

        sections.clear();
        currentWeather.clear();
        timeLeft.clear();
        totalDuration.clear();
        fixedBarTimeLeft.clear();
        for (WeatherBossBar bar : bossBars.values()) {
            bar.remove();
        }
        bossBars.clear();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("custom-weather");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) continue;
                for (String worldName : key.split(",")) {
                    sections.put(worldName.trim(), section);
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            UUID id = world.getUID();
            String saved = savedWeather.get(id);
            if (saved != null && isCustom(world)) {
                // Restore the ongoing cycle (timer value from before the reload)
                ConfigurationSection section = section(world);
                int dur = savedLeft.containsKey(id) ? savedLeft.get(id) : randomDuration(section, saved);
                currentWeather.put(id, saved);
                timeLeft.put(id, dur);
                totalDuration.put(id, dur);
                setupBossBar(world, section, saved);
            } else {
                initWorld(world); // picks a random weather by chances, applied silently
            }
        }

        start();
    }

    public boolean isCustom(World world) {
        return sections.containsKey(world.getName());
    }

    /** Forces a specific weather in a custom world (used by /weatherage). Resets the cycle timer. */
    public void forceWeather(World world, String type) {
        ConfigurationSection section = section(world);
        if (section == null) return;
        UUID id = world.getUID();

        currentWeather.put(id, type);
        int dur = randomDuration(section, type);
        timeLeft.put(id, dur);
        totalDuration.put(id, dur);

        announce(world, section, type);
        applyWeather(world, type);
    }

    private ConfigurationSection section(World world) {
        return sections.get(world.getName());
    }

    /** First weather of the world: random by chances, applied silently (no chat/commands). */
    private void initWorld(World world) {
        ConfigurationSection section = section(world);
        if (section == null) return;
        UUID id = world.getUID();
        if (currentWeather.containsKey(id)) return; // do not reset an ongoing cycle

        String type = pickByChance(section);
        int dur = randomDuration(section, type);
        currentWeather.put(id, type);
        timeLeft.put(id, dur);
        totalDuration.put(id, dur);

        setupBossBar(world, section, type);
        applyWeather(world, type);
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (WeatherBossBar bar : bossBars.values()) {
            bar.remove();
        }
        bossBars.clear();
        fixedBarTimeLeft.clear();
    }

    private void tick() {
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            ConfigurationSection section = section(world);
            if (section == null) continue;

            UUID id = world.getUID();
            initWorld(world); // world loaded after plugin start
            if (!currentWeather.containsKey(id)) continue;

            WeatherBossBar bar = bossBars.get(id);
            int left = timeLeft.get(id);
            int total = totalDuration.get(id);

            // Bossbar: countdown mode drains over the weather duration
            if (bar != null && bar.isCountdown()) {
                bar.setProgress(total > 0 ? (double) left / total : 0.0);
            }

            // Bossbar: fixed mode disappears after display-seconds
            Integer fixedLeft = fixedBarTimeLeft.get(id);
            if (fixedLeft != null) {
                if (fixedLeft <= 1) {
                    if (bar != null && !bar.isCountdown()) {
                        bar.remove();
                        bossBars.remove(id);
                    }
                    fixedBarTimeLeft.remove(id);
                } else {
                    fixedBarTimeLeft.put(id, fixedLeft - 1);
                }
            }

            if (bar != null) {
                bar.updateViewers(world);
            }

            left--;
            if (left > 0) {
                timeLeft.put(id, left);
                continue;
            }

            String next = pickByChance(section);
            String current = currentWeather.get(id);
            currentWeather.put(id, next);
            int dur = randomDuration(section, next);
            timeLeft.put(id, dur);
            totalDuration.put(id, dur);

            // Chat, bossbar and commands ONLY on an actual change.
            // If the same weather came up again - stay silent, just reset the timer.
            if (!next.equals(current)) {
                announce(world, section, next);
                applyWeather(world, next);
            }
        }
    }

    /** Chat message, bossbar and console commands for the new weather. */
    private void announce(World world, ConfigurationSection section, String type) {
        if (section.getBoolean("show-chat-message", true)) {
            String text = section.getString("chat-" + type + "-text");
            if (text != null && !text.trim().isEmpty()) {
                String colored = color(text.replace("%world%", world.getName()));
                for (Player player : world.getPlayers()) {
                    for (String line : colored.split("\n")) {
                        player.sendMessage(line);
                    }
                }
            }
        }

        setupBossBar(world, section, type);

        for (String command : section.getStringList("commands-" + type)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%world%", world.getName()));
        }
    }

    /** Creates (or replaces) the bossbar for the given weather type. */
    private void setupBossBar(World world, ConfigurationSection section, String type) {
        UUID id = world.getUID();
        if (!section.getBoolean("enable-bossbar-" + type, false)) {
            WeatherBossBar old = bossBars.remove(id);
            if (old != null) old.remove();
            fixedBarTimeLeft.remove(id);
            return;
        }

        WeatherBossBar old = bossBars.remove(id);
        if (old != null) old.remove();

        String text = color(section.getString("bossbar-" + type + "-text", type));
        String colorName = section.getString("bossbar-" + type + "-color", "WHITE");
        String styleName = section.getString("bossbar-" + type + "-style", "SOLID");
        String mode = section.getString("bossbar-" + type + "-mode", "countdown");
        boolean countdown = !"fixed".equalsIgnoreCase(mode);
        double fixedProgress = section.getDouble("bossbar-" + type + "-fixed-progress", 1.0);
        int displaySeconds = section.getInt("bossbar-" + type + "-display-seconds", 30);

        WeatherBossBar bar = new WeatherBossBar(text, colorName, styleName,
                countdown, fixedProgress, displaySeconds);
        bossBars.put(id, bar);

        if (countdown) {
            // Progress starts full and drains over the weather duration
            int total = totalDuration.containsKey(id) ? totalDuration.get(id) : 1;
            bar.setProgress((double) timeLeft.get(id) / total);
        } else {
            fixedBarTimeLeft.put(id, displaySeconds);
        }
        bar.show(world);
    }

    /** Maps a custom weather type to a vanilla one by name and applies it visually. */
    private void applyWeather(World world, String type) {
        String vanillaType = mapToVanilla(type);
        // Align listener state BEFORE setters so vanilla events are treated as "no change"
        plugin.getVanillaListener().syncState(world, vanillaType);

        boolean raining = world.hasStorm();
        boolean thundering = world.isThundering();
        switch (vanillaType) {
            case "sun":
                if (raining) world.setStorm(false);
                if (thundering) world.setThundering(false);
                break;
            case "rain":
                if (thundering) world.setThundering(false);
                if (!raining) world.setStorm(true);
                break;
            case "thunder":
                if (!raining) world.setStorm(true);
                if (!thundering) world.setThundering(true);
                break;
        }
    }

    private String mapToVanilla(String type) {
        String t = type.toLowerCase();
        if (t.contains("thunder") || t.contains("storm")) return "thunder";
        if (t.contains("rain")) return "rain";
        return "sun";
    }

    /** Picks the next weather by chances from the config (same weather CAN repeat). */
    private String pickByChance(ConfigurationSection section) {
        double sun = section.getDouble("chance-sun", 0.626);
        double rain = section.getDouble("chance-rain", 0.256);
        double thunder = section.getDouble("chance-thunder", 0.118);

        double roll = random.nextDouble(); // 0.0 .. 1.0
        if (roll < sun) return "sun";
        if (roll < sun + rain) return "rain";
        if (roll < sun + rain + thunder) return "thunder";
        return "sun"; // if chances sum is below 1.0 — fallback to sun
    }

    /** Random duration between <type>-min-seconds and <type>-max-seconds. */
    private int randomDuration(ConfigurationSection section, String type) {
        int min = section.getInt(type + "-min-seconds", 300);
        int max = section.getInt(type + "-max-seconds", 600);
        if (max < min) max = min;
        return min + random.nextInt(max - min + 1);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getCurrentWeather(World world) {
        return currentWeather.get(world.getUID());
    }
}
