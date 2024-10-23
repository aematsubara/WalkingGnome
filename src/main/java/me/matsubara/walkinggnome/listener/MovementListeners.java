package me.matsubara.walkinggnome.listener;

import me.matsubara.walkinggnome.WalkingGnomePlugin;
import me.matsubara.walkinggnome.files.Config;
import me.matsubara.walkinggnome.gnome.Gnome;
import me.matsubara.walkinggnome.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class MovementListeners implements Listener {

    private final WalkingGnomePlugin plugin;

    private static final float FOV = 70.0f;
    private static final int MIN_SPAWN_RANGE = 5;
    private static final int MAX_SPAWN_RANGE = 15;

    public MovementListeners(WalkingGnomePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleGnomeSpawning(player, player.getLocation());
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleGnomeSpawning(player, player.getLocation());
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        handleMovement(event);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        handleMovement(event);
    }

    private void handleMovement(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        // Only handle renders if the player moved at least 1 block.
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        handleGnomeSpawning(event.getPlayer(), to);
    }

    private void handleGnomeSpawning(@NotNull Player player, Location location) {
        World world = player.getWorld();
        if (!plugin.isEnabledIn(world)) return;

        // Ignore swimming or invisible players.
        if (player.isSwimming()
                || player.isInWater()
                || player.isInvisible()
                || player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        double chance = Config.SPAWN_CHANCE.asDouble();
        if (random.nextFloat() > chance) return;

        boolean atDay = Config.SPAWN_ONLY_AT_DAY.asBool();
        if (atDay && !isDay(world)) return;

        int randomX = random.nextInt(MIN_SPAWN_RANGE, MAX_SPAWN_RANGE);
        int randomZ = random.nextInt(MIN_SPAWN_RANGE, MAX_SPAWN_RANGE);

        Location highest = PluginUtils.getHighestLocation(location.clone().add(
                random.nextBoolean() ? -randomX : randomX,
                0.0d,
                random.nextBoolean() ? -randomZ : randomZ));

        if (highest == null || !canSpawnGnome(highest)) return;

        Block block = highest.getBlock();
        if (block.isLiquid()
                || block.getType().isAir()
                || !plugin.isEnabledIn(block.getBiome())) return;

        float rotated = lookAt(location, highest), yaw = location.getYaw();
        float angleDifference = Math.abs((rotated - yaw + 540) % 360 - 180);

        if (angleDifference > FOV) return;

        plugin.spawnGnome(highest);
    }

    private boolean canSpawnGnome(Location spawn) {
        double distance = Config.SPAWN_DISTANCE_BETWEEN.asDouble();
        for (Gnome gnome : plugin.getGnomes()) {
            Location location = gnome.getFollow().getLocation();
            if (location.distanceSquared(spawn) <= distance * distance) {
                return false;
            }
        }
        return true;
    }

    private float lookAt(@NotNull Location first, @NotNull Location second) {
        double xDifference = second.getX() - first.getX();
        double zDifference = second.getZ() - first.getZ();
        float yaw = (float) (-Math.atan2(xDifference, zDifference) / Math.PI * 180.0d);
        return yaw < 0 ? yaw + 360 : yaw;
    }

    private boolean isDay(@NotNull World world) {
        long time = world.getTime();
        return time < 13000 || time > 23000;
    }
}