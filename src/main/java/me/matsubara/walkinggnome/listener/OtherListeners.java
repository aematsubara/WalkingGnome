package me.matsubara.walkinggnome.listener;

import me.matsubara.walkinggnome.WalkingGnomePlugin;
import me.matsubara.walkinggnome.gnome.Gnome;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.jetbrains.annotations.NotNull;

public class OtherListeners implements Listener {

    private final WalkingGnomePlugin plugin;

    public OtherListeners(WalkingGnomePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        Gnome gnome = plugin.getGnomeByEntity(event.getEntity());
        if (gnome == null) return;

        event.setDroppedExp(0);
        event.getDrops().clear();
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        cancelGnomeEvent(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        cancelGnomeEvent(event);
    }

    @EventHandler
    public void onEntityTarget(@NotNull EntityTargetEvent event) {
        if (event.getReason() != EntityTargetEvent.TargetReason.TEMPT) return;
        cancelGnomeEvent(event);
    }

    private void cancelGnomeEvent(EntityEvent event) {
        if (!(event instanceof Cancellable cancellable)) return;

        if (plugin.getGnomeByEntity(event.getEntity()) != null) {
            cancellable.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDropItem(@NotNull EntityDropItemEvent event) {
        Gnome gnome = plugin.getGnomeByEntity(event.getEntity());
        if (gnome == null) return;

        event.getItemDrop().remove();
        event.setCancelled(true);
    }
}