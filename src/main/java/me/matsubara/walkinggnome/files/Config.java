package me.matsubara.walkinggnome.files;

import lombok.Getter;
import me.matsubara.walkinggnome.WalkingGnomePlugin;
import me.matsubara.walkinggnome.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@Getter
public enum Config {
    WORLDS_FILTER_TYPE("worlds-filter.type"),
    WORLDS_FILTER_WORLDS("worlds-filter.worlds"),
    BIOMES_FILTER_TYPE("biomes-filter.type"),
    BIOMES_FILTER_WORLDS("biomes-filter.worlds"),
    SPAWN_CHANCE("spawn.chance"),
    SPAWN_ONLY_AT_DAY("spawn.only-at-day"),
    SPAWN_DISTANCE_BETWEEN("spawn.distance-between"),
    SCALE_MIN("scale.min"),
    SCALE_MAX("scale.max"),
    LIFE_MIN("life.min"),
    LIFE_MAX("life.max"),
    RANDOM_ITEM_DROP_ENABLED("random-item-drop.enabled"),
    RANDOM_ITEM_DROP_DELAY("random-item-drop.delay"),
    RANDOM_ITEM_DROP_CHANCE("random-item-drop.chance"),
    RANDOM_ITEM_DROP_ITEMS("random-item-drop.items"),
    MESSAGES_RELOADING("messages.reloading"),
    MESSAGES_RELOAD("messages.reload"),
    MESSAGES_NO_PERMISSION("messages.no-permission"),
    MESSAGES_INVALID_COMMAND("messages.invalid-command");

    private final String path;
    private final WalkingGnomePlugin plugin = JavaPlugin.getPlugin(WalkingGnomePlugin.class);

    @SuppressWarnings("unused")
    Config() {
        this.path = name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    Config(String path) {
        this.path = path;
    }

    public boolean asBool() {
        return plugin.getConfig().getBoolean(path);
    }

    public int asInt() {
        return plugin.getConfig().getInt(path);
    }

    public String asString() {
        return plugin.getConfig().getString(path);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public double asDouble() {
        return plugin.getConfig().getDouble(path);
    }

    public @NotNull List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }
}