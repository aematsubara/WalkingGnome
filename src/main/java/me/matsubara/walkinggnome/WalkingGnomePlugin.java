package me.matsubara.walkinggnome;

import com.google.common.collect.ImmutableList;
import com.tchristofferson.configupdater.ConfigUpdater;
import lombok.Getter;
import me.matsubara.walkinggnome.files.Config;
import me.matsubara.walkinggnome.gnome.Gnome;
import me.matsubara.walkinggnome.listener.MovementListeners;
import me.matsubara.walkinggnome.listener.OtherListeners;
import me.matsubara.walkinggnome.targets.TypeTarget;
import me.matsubara.walkinggnome.targets.TypeTargetManager;
import me.matsubara.walkinggnome.util.PluginUtils;
import net.donnypz.displayentityutils.managers.DisplayAnimationManager;
import net.donnypz.displayentityutils.managers.DisplayGroupManager;
import net.donnypz.displayentityutils.utils.DisplayEntities.DisplayAnimation;
import net.donnypz.displayentityutils.utils.DisplayEntities.DisplayEntityGroup;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Getter
public final class WalkingGnomePlugin extends JavaPlugin {

    private final List<Gnome> gnomes = new ArrayList<>();
    private final List<TypeTarget> drops = new ArrayList<>();

    private TypeTargetManager typeTargetManager;

    private static final Set<String> SPECIAL_SECTIONS = new HashSet<>();
    private static final List<String> FILTER_TYPES = List.of("WHITELIST", "BLACKLIST");

    @Override
    public void onEnable() {
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(new MovementListeners(this), this);
        manager.registerEvents(new OtherListeners(this), this);

        typeTargetManager = new TypeTargetManager(this);
        saveDefaultConfig();
        updateConfigs();

        reloadDrops();
    }

    @Override
    public void onDisable() {
        gnomes.forEach(Gnome::cancel);
    }

    private void updateConfigs() {
        updateConfig(
                getDataFolder().getPath(),
                "config.yml",
                file -> reloadConfig(),
                file -> saveDefaultConfig(),
                config -> {
                    fillIgnoredSections(config);
                    return SPECIAL_SECTIONS.stream().filter(config::contains).toList();
                },
                Collections.emptyList());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("walkinggnome")) return true;

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            send(sender, Config.MESSAGES_INVALID_COMMAND);
            return true;
        }

        if (!sender.hasPermission("walkinggnome.reload")) {
            send(sender, Config.MESSAGES_NO_PERMISSION);
            return true;
        }

        send(sender, Config.MESSAGES_RELOADING);

        CompletableFuture.runAsync(this::updateConfigs).thenRun(() -> getServer().getScheduler().runTask(this, () -> {
            reloadDrops();
            send(sender, Config.MESSAGES_RELOAD);
        }));

        return true;
    }

    private void send(@NotNull CommandSender sender, @NotNull Config config) {
        sender.sendMessage(config.asStringTranslated());
    }

    @SuppressWarnings("unused")
    private void fillIgnoredSections(FileConfiguration config) {
        // Not needed ATM.
    }

    public void updateConfig(String folderName,
                             String fileName,
                             Consumer<File> reloadAfterUpdating,
                             Consumer<File> resetConfiguration,
                             Function<FileConfiguration, List<String>> ignoreSection,
                             List<ConfigChanges> changes) {
        File file = new File(folderName, fileName);

        FileConfiguration config = PluginUtils.reloadConfig(this, file, resetConfiguration);
        if (config == null) {
            getLogger().severe("Can't find {" + file.getName() + "}!");
            return;
        }

        for (ConfigChanges change : changes) {
            handleConfigChanges(file, config, change.predicate(), change.consumer(), change.newVersion());
        }

        try {
            ConfigUpdater.update(
                    this,
                    fileName,
                    file,
                    ignoreSection.apply(config));
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadAfterUpdating.accept(file);
    }

    private void handleConfigChanges(@NotNull File file, FileConfiguration config, @NotNull Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!predicate.test(config)) return;

        int previousVersion = config.getInt("config-version", 0);
        getLogger().info("Updated {%s} config to v{%s} (from v{%s})".formatted(file.getName(), newVersion, previousVersion));

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public record ConfigChanges(Predicate<FileConfiguration> predicate,
                                Consumer<FileConfiguration> consumer,
                                int newVersion) {

        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private final List<ConfigChanges> changes = new ArrayList<>();

            public Builder addChange(Predicate<FileConfiguration> predicate,
                                     Consumer<FileConfiguration> consumer,
                                     int newVersion) {
                changes.add(new ConfigChanges(predicate, consumer, newVersion));
                return this;
            }

            public List<ConfigChanges> build() {
                return ImmutableList.copyOf(changes);
            }
        }
    }

    public @Nullable Gnome getGnomeByEntity(Entity entity) {
        for (Gnome gnome : gnomes) {
            if (gnome.getFollow().equals(entity)) return gnome;
        }
        return null;
    }

    private void reloadDrops() {
        drops.clear();
        drops.addAll(typeTargetManager.getTargetsFromConfig(Config.RANDOM_ITEM_DROP_ITEMS.getPath()));
    }

    public void spawnGnome(Location location) {
        DisplayEntityGroup group = DisplayGroupManager.getGroup(getResource("gnome.deg"));
        DisplayAnimation animation = DisplayAnimationManager.getAnimation(getResource("gnome_walk.deanim"));

        Gnome gnome = new Gnome(this, location, Chicken.class, group, animation);
        gnomes.add(gnome);
    }

    public boolean isEnabledIn(@NotNull World world) {
        return contains(Config.WORLDS_FILTER_TYPE, Config.WORLDS_FILTER_WORLDS, world.getName());
    }

    public boolean isEnabledIn(@NotNull Biome biome) {
        return contains(Config.BIOMES_FILTER_TYPE, Config.BIOMES_FILTER_WORLDS, biome.name());
    }

    private boolean contains(@NotNull Config filterConfig, Config listConfig, String check) {
        String filter = filterConfig.asString();
        if (filter == null || !FILTER_TYPES.contains(filter.toUpperCase(Locale.ROOT))) return true;

        List<String> list = listConfig.asStringList();
        return filter.equalsIgnoreCase("WHITELIST") == list.contains(check);
    }
}