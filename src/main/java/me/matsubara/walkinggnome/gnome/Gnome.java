package me.matsubara.walkinggnome.gnome;

import lombok.Getter;
import me.matsubara.walkinggnome.WalkingGnomePlugin;
import me.matsubara.walkinggnome.files.Config;
import me.matsubara.walkinggnome.targets.TypeTarget;
import net.donnypz.displayentityutils.events.GroupSpawnedEvent;
import net.donnypz.displayentityutils.utils.DisplayEntities.DisplayAnimation;
import net.donnypz.displayentityutils.utils.DisplayEntities.DisplayEntityGroup;
import net.donnypz.displayentityutils.utils.DisplayEntities.SpawnedDisplayEntityGroup;
import net.donnypz.displayentityutils.utils.DisplayEntities.SpawnedDisplayEntityPart;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class Gnome extends BukkitRunnable {

    private final WalkingGnomePlugin plugin;
    private final int life;
    private final float scale;
    private final LivingEntity follow;
    private final Vector offset;
    private final SpawnedDisplayEntityGroup group;
    private final Set<Item> dropped = new HashSet<>();

    private int ticks;
    private boolean dying;
    private float dyingPitch;

    public Gnome(@NotNull WalkingGnomePlugin plugin,
                 @NotNull Location location,
                 Class<? extends LivingEntity> clazz,
                 @NotNull DisplayEntityGroup group,
                 @NotNull DisplayAnimation animation) {
        this.plugin = plugin;

        ThreadLocalRandom random = ThreadLocalRandom.current();

        int minLife = Config.LIFE_MIN.asInt();
        int maxLife = Config.LIFE_MAX.asInt();
        this.life = random.nextInt(minLife, maxLife + 1);

        float minScale = (float) Config.SCALE_MIN.asDouble();
        float maxScale = (float) Config.SCALE_MAX.asDouble();
        this.scale = random.nextFloat(minScale * 100, maxScale * 100 + 1) / 100.0f;

        this.follow = Objects.requireNonNull(location.getWorld()).spawn(location, clazz, temp -> {
            temp.setPersistent(false);
            temp.setInvisible(true);
            temp.setSilent(true);
        });

        BoundingBox box = this.follow.getBoundingBox();
        offset = new Vector((box.getWidthX() * 1.5d) * scale, 0.0d, box.getWidthZ());

        this.group = group.spawn(getCorrectLocation(), GroupSpawnedEvent.SpawnReason.CUSTOM);
        this.group.scale(scale, 2, true);
        this.group.animateLooping(animation.toSpawnedDisplayAnimation());

        for (SpawnedDisplayEntityPart part : this.group.getSpawnedDisplayParts()) {
            if (!(part.getEntity() instanceof Display display)) continue;
            display.setTeleportDuration(2);
            display.setInterpolationDelay(2);
            display.setInterpolationDuration(2);
            display.setPersistent(false);
        }

        runTaskTimer(plugin, 1L, 1L);
    }

    private boolean isValid(boolean ignoreEntity) {
        Location location = follow.getLocation();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return (ignoreEntity || follow.isValid()) && follow.getWorld().isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public void run() {
        if (dying) {
            if (!isValid(true) || (ticks % 20 == 0 && ticks / 20 == 1)) {
                cancel();
                return;
            }

            Location location = getCorrectLocation();
            location.setPitch(dyingPitch);

            group.teleport(location, false);

            dyingPitch = Math.min(dyingPitch + 8.0f, 90.0f);

            if (++ticks == Integer.MAX_VALUE) ticks = 0;
            return;
        }

        boolean valid = isValid(false);
        if (!valid || (ticks % 20 == 0 && ticks / 20 == life)) {
            if (valid) {
                follow.remove();
                dying = true;
                ticks = 0;
            }

            plugin.getGnomes().remove(this);

            if (!dying) {
                cancel();
            }

            return;
        }

        handleLoot();

        group.teleport(getCorrectLocation(), false);

        if (++ticks == Integer.MAX_VALUE) ticks = 0;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        handleDeath();
        group.unregister(true);

        // Remove drops from the world.
        for (Item drop : dropped) {
            if (drop.isValid()) drop.remove();
        }
        dropped.clear();

        super.cancel();
    }

    private void handleLoot() {
        if (!Config.RANDOM_ITEM_DROP_ENABLED.asBool()) return;

        int delay = (int) Config.RANDOM_ITEM_DROP_DELAY.asDouble() * 20;
        double chance = Config.RANDOM_ITEM_DROP_CHANCE.asDouble();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (ticks == 0 || ticks % delay != 0 || random.nextFloat() > chance) return;

        List<TypeTarget> drops = plugin.getDrops();
        TypeTarget target = drops.get(random.nextInt(drops.size()));

        Material type = target.type();

        int amount, temp = target.amount();
        amount = temp == -1 ?
                random.nextInt(1, type.getMaxStackSize() + 1) :
                temp;

        dropped.add(follow.getWorld().dropItemNaturally(
                follow.getLocation(),
                new ItemStack(type, amount),
                item -> item.setPersistent(false)));
    }

    private void handleDeath() {
        World world = follow.getWorld();
        Location at = follow.getEyeLocation();

        world.spawnParticle(Particle.POOF, at, 8, 0.3d, 0.3d, 0.3d, 0);
        world.playSound(at, Sound.ENTITY_GENERIC_DEATH, 1.0f, 1.0f);
    }

    private @NotNull Location getCorrectLocation() {
        Location location = follow.getLocation();
        location.setYaw(location.getYaw() + 180.0f);
        location.setPitch(0.0f);

        return location.add(offsetVector(offset, location.getYaw(), location.getPitch()));
    }

    private @NotNull Vector offsetVector(@NotNull Vector vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees), pitch = Math.toRadians(-pitchDegrees);

        double cosYaw = Math.cos(yaw), cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw), sinPitch = Math.sin(pitch);

        double initialX, initialY, initialZ, x, y, z;

        initialX = vector.getX();
        initialY = vector.getY();
        x = initialX * cosPitch - initialY * sinPitch;
        y = initialX * sinPitch + initialY * cosPitch;

        initialZ = vector.getZ();
        initialX = x;
        z = initialZ * cosYaw - initialX * sinYaw;
        x = initialZ * sinYaw + initialX * cosYaw;

        return new Vector(x, y, z);
    }
}