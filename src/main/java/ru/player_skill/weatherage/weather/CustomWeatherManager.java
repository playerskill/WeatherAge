package ru.player_skill.weatherage.weather;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.player_skill.weatherage.WeatherAgePlugin;
import ru.player_skill.weatherage.config.CustomWeatherConfig;
import ru.player_skill.weatherage.util.CommandRunner;

import java.util.List;
import java.util.Random;

public class CustomWeatherManager {

    private final WeatherAgePlugin plugin;
    private final Random random = new Random();

    public CustomWeatherManager(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Запускает кастомный цикл погоды для мира.
     * Вызывается при включении плагина для всех миров с режимом CUSTOM.
     */
    public void startCustomCycle(String worldName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    cancel(); // мир выгружен — останавливаем цикл
                    return;
                }

                CustomWeatherConfig cfg = plugin.getCustomConfig(worldName);
                if (cfg == null) {
                    plugin.getLogger().warning("Custom config missing for world '" + worldName + "'. Cycle stopped.");
                    cancel();
                    return;
                }

                // Выбор погоды по шансам
                double r = random.nextDouble();
                String nextWeather;
                int durationSeconds;

                if (r < cfg.chanceSun) {
                    nextWeather = "sun";
                    durationSeconds = randomBetween(cfg.sunMinSeconds, cfg.sunMaxSeconds);
                } else if (r < cfg.chanceSun + cfg.chanceRain) {
                    nextWeather = "rain";
                    durationSeconds = randomBetween(cfg.rainMinSeconds, cfg.rainMaxSeconds);
                } else {
                    nextWeather = "thunder";
                    durationSeconds = randomBetween(cfg.thunderMinSeconds, cfg.thunderMaxSeconds);
                }

                applyCustomWeather(world, nextWeather, durationSeconds, cfg);
            }
        }.runTaskTimer(plugin, 0L, 20L * 60L); // проверка/запуск каждую минуту
    }

    /**
     * Применяет конкретную погоду в мире и настраивает боссбар/сообщения/команды.
     */
    private void applyCustomWeather(World world, String weatherType, int durationSeconds, CustomWeatherConfig cfg) {
        String worldName = world.getName();
        int durationTicks = durationSeconds * 20;

        switch (weatherType) {
            case "sun":
                world.setThundering(false);
                world.setWeatherDuration(0); // отменяем дождь/грозу

                sendChatMessage(world, cfg.chatSunText);
                runCommands(cfg.sunStartCommands);
                showBossBar(world,
                        cfg.enableBossbarSun,
                        cfg.bossbarSunStyle,
                        cfg.bossbarSunColor,
                        cfg.bossbarSunText,
                        cfg.bossbarSunFixedProgress,
                        cfg.bossbarSunDisplaySeconds
                );
                break;

            case "rain":
                world.setThundering(false); // пока без грозы
                world.setWeatherDuration(durationTicks);

                sendChatMessage(world, cfg.chatRainText);
                runCommands(cfg.rainStartCommands);
                showBossBar(world,
                        cfg.enableBossbarRain,
                        cfg.bossbarRainStyle,
                        cfg.bossbarRainColor,
                        cfg.bossbarRainText,
                        cfg.bossbarRainFixedProgress,
                        cfg.bossbarRainDisplaySeconds
                );
                break;

            case "thunder":
                world.setThundering(true);
                world.setWeatherDuration(durationTicks);

                sendChatMessage(world, cfg.chatThunderText);
                runCommands(cfg.thunderStartCommands);
                showBossBar(world,
                        cfg.enableBossbarThunder,
                        cfg.bossbarThunderStyle,
                        cfg.bossbarThunderColor,
                        cfg.bossbarThunderText,
                        cfg.bossbarThunderFixedProgress,
                        cfg.bossbarThunderDisplaySeconds
                );
                break;
        }

        plugin.getLogger().fine("Custom weather applied: " + weatherType + " in '" + worldName + "' for " + durationSeconds + "s");
    }

    /**
     * Принудительная смена погоды через команду /wage sun|rain|storm [world].
     */
    public void forceWeather(String worldName, String weatherType) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        CustomWeatherConfig cfg = plugin.getCustomConfig(worldName);
        if (cfg == null) return; // не кастомный мир

        // Подбираем длительность из конфига
        int durationSeconds;
        if ("sun".equals(weatherType)) {
            durationSeconds = randomBetween(cfg.sunMinSeconds, cfg.sunMaxSeconds);
        } else if ("rain".equals(weatherType)) {
            durationSeconds = randomBetween(cfg.rainMinSeconds, cfg.rainMaxSeconds);
        } else { // storm
            durationSeconds = randomBetween(cfg.thunderMinSeconds, cfg.thunderMaxSeconds);
        }

        applyCustomWeather(world, weatherType, durationSeconds, cfg);
    }

    // --- Вспомогательные методы ---

    private int randomBetween(int min, int max) {
        return min + random.nextInt((max - min) + 1);
    }

    private void sendChatMessage(World world, String message) {
        if (message == null || message.isEmpty() || message.equals("none")) return;
        String colored = ChatColor.translateAlternateColorCodes('&', message);
        for (Player p : world.getPlayers()) {
            p.sendMessage(colored);
        }
    }

    private void runCommands(List<String> commands) {
        CommandRunner.runCommands(commands, plugin);
    }

    private void showBossBar(World world, boolean enabled, BarStyle style, BarColor color,
                             String text, float fixedProgress, long seconds) {
        if (!enabled || text == null || text.isEmpty()) return;

        String coloredText = ChatColor.translateAlternateColorCodes('&', text);

        for (Player p : world.getPlayers()) {
            // Порядок аргументов: (текст, ЦВЕТ, СТИЛЬ) — цвет идёт первым!
            BossBar bossBar = Bukkit.createBossBar(coloredText, color, style);
            bossBar.setProgress(fixedProgress);
            bossBar.addPlayer(p);

            // Удаляем боссбар через указанное время
            new BukkitRunnable() {
                @Override
                public void run() {
                    bossBar.removeAll();
                }
            }.runTaskLater(plugin, seconds * 20L);
        }
    }
}
