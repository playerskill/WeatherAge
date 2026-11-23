package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WeatherBossBar {

    private final BossBar bar;
    private final boolean countdown;
    private final int displaySeconds;

    public WeatherBossBar(String text, String colorName, String styleName,
                          boolean countdown, double fixedProgress, int displaySeconds) {
        this.countdown = countdown;
        this.displaySeconds = displaySeconds;
        this.bar = Bukkit.createBossBar(text, parseColor(colorName), parseStyle(styleName));
        if (!countdown) {
            bar.setProgress(clamp(fixedProgress));
        }
    }

    /** Adds all current players of the world as viewers. */
    public void show(World world) {
        for (Player player : world.getPlayers()) {
            bar.addPlayer(player);
        }
    }

    /** Keeps viewers in sync: players who joined the world see the bar, players who left don't. */
    public void updateViewers(World world) {
        for (Player player : world.getPlayers()) {
            bar.addPlayer(player);
        }
        List<Player> viewers = new ArrayList<>(bar.getPlayers());
        for (Player player : viewers) {
            if (!player.getWorld().equals(world)) {
                bar.removePlayer(player);
            }
        }
    }

    public void setProgress(double progress) {
        bar.setProgress(clamp(progress));
    }

    public boolean isCountdown() {
        return countdown;
    }

    public int getDisplaySeconds() {
        return displaySeconds;
    }

    public void remove() {
        bar.removeAll();
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private static BarColor parseColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BarColor.WHITE;
        }
    }

    private static BarStyle parseStyle(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BarStyle.SOLID;
        }
    }
}
