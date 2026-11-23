package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VanillaWeatherListener implements Listener {

    private final WeatherAgePlugin plugin;
    private final Map<UUID, Boolean> lastRain = new HashMap<>();
    private final Map<UUID, Boolean> lastThunder = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingRainTasks = new HashMap<>();
    // Expected weather type ("sun" | "rain" | "thunder"); consumed only when it matches the real event
    private final Map<UUID, String> suppressState = new HashMap<>();

    public VanillaWeatherListener(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    public void initStates() {
        for (World world : Bukkit.getWorlds()) {
            rememberState(world);
        }
    }

    public void reset() {
        shutdown();
        initStates();
    }

    /** Cancels pending tasks and clears all cached state. */
    public void shutdown() {
        for (BukkitTask task : pendingRainTasks.values()) {
            if (task != null) task.cancel();
        }
        pendingRainTasks.clear();
        lastRain.clear();
        lastThunder.clear();
        suppressState.clear();
    }

    /** Suppresses the announcement of the given weather type (used by /weatherage). */
    public void suppressNext(World world, String type) {
        suppressState.put(world.getUID(), type);
    }

    /**
     * Aligns the internal state with the given weather WITHOUT announcements.
     * Call BEFORE setStorm/setThundering so the events are treated as "no change".
     */
    public void syncState(World world, String type) {
        UUID id = world.getUID();
        BukkitTask pending = pendingRainTasks.remove(id);
        if (pending != null) pending.cancel();
        lastRain.put(id, !"sun".equals(type));
        lastThunder.put(id, "thunder".equals(type));
    }

    /** Returns true if this is the first observation of the world (state was just initialized). */
    private boolean ensureInitialized(World world) {
        UUID id = world.getUID();
        if (lastRain.containsKey(id)) return false;
        rememberState(world);
        return true;
    }

    private void rememberState(World world) {
        UUID id = world.getUID();
        lastRain.put(id, world.hasStorm());
        lastThunder.put(id, world.isThundering());
    }

    private boolean takeSuppress(UUID id, String type) {
        return type.equals(suppressState.remove(id));
    }

    private void cancelPending(UUID id) {
        BukkitTask task = pendingRainTasks.remove(id);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();

        // Worlds outside the whitelist: always sunny, any weather change is blocked
        if (!plugin.isWhitelisted(world)) {
            if (event.toWeatherState()) event.setCancelled(true);
            return;
        }

        // Custom worlds are fully managed by CustomWeatherManager — no vanilla announcements
        if (plugin.getCustomManager().isCustom(world)) return;
        if (!plugin.getVanillaManager().isManaged(world)) return;

        if (event.isCancelled()) return;
        UUID id = world.getUID();
        if (ensureInitialized(world)) return; // first observation — no announcement

        boolean toRaining = event.toWeatherState();
        Boolean previous = lastRain.get(id);
        if (previous != null && previous == toRaining) return; // no real change
        lastRain.put(id, toRaining);

        if (toRaining) {
            if (takeSuppress(id, "rain")) return;
            // Thunder usually starts right after rain, so wait before announcing rain
            long delay = plugin.getConfig().getLong("vanilla-weather.thunder-check-delay", 20);
            pendingRainTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingRainTasks.remove(id);
                if (!plugin.isEnabled()) return;
                if (world.isThundering()) return; // thunder message covers it
                plugin.getVanillaManager().announce(world, "rain");
            }, delay));
        } else {
            cancelPending(id);
            if (!takeSuppress(id, "sun")) {
                plugin.getVanillaManager().announce(world, "sun");
            }
        }
    }

    @EventHandler
    public void onThunderChange(ThunderChangeEvent event) {
        World world = event.getWorld();

        // Worlds outside the whitelist: any thunder is blocked
        if (!plugin.isWhitelisted(world)) {
            if (event.toThunderState()) event.setCancelled(true);
            return;
        }

        if (plugin.getCustomManager().isCustom(world)) return;
        if (!plugin.getVanillaManager().isManaged(world)) return;

        if (event.isCancelled()) return;
        UUID id = world.getUID();
        if (ensureInitialized(world)) return;

        boolean toThunder = event.toThunderState();
        Boolean previous = lastThunder.get(id);
        if (previous != null && previous == toThunder) return; // no real change
        lastThunder.put(id, toThunder);

        if (toThunder) {
            cancelPending(id); // no "rain started" flood right before "thunder started"
            if (!takeSuppress(id, "thunder")) {
                plugin.getVanillaManager().announce(world, "thunder");
            }
        }
        // Thunder end: silent — rain usually continues after thunder stops
    }
}
