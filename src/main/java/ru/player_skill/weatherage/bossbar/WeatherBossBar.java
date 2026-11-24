package ru.player_skill.weatherage.bossbar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.player_skill.weatherage.WeatherAgePlugin;

public class WeatherBossBar {

    private final WeatherAgePlugin plugin;

    public WeatherBossBar(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Показывает боссбар всем игрокам в мире.
     * @param world Мир
     * @param enabled Включен ли боссбар
     * @param style Стиль
     * @param color Цвет
     * @param text Текст (с цветовыми кодами)
     * @param fixedProgress Фиксированный прогресс (0.0–1.0). Если 0.0 — можно сделать countdown.
     * @param seconds Длительность показа
     */
    public void show(World world, boolean enabled, BarStyle style, BarColor color, String text, float fixedProgress, int seconds) {
        if (!enabled || text == null || text.isEmpty()) return;
        String coloredText = ChatColor.translateAlternateColorCodes('&', text);

        for (Player p : world.getPlayers()) {
            org.bukkit.boss.BossBar bossBar = Bukkit.createBossBar(coloredText, style, color, fixedProgress);
            bossBar.addPlayer(p);

            // Удаляем боссбар через указанное время
            new BukkitRunnable() {
                @Override
                public void run() {
                    bossBar.removeAll();
                }
            }.runTaskLater(plugin, seconds * 20L); // ticks = seconds * 20
        }
    }
}
