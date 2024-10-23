package me.matsubara.walkinggnome.targets;

import me.matsubara.walkinggnome.WalkingGnomePlugin;
import me.matsubara.walkinggnome.util.PluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TypeTargetManager {

    private final WalkingGnomePlugin plugin;

    public TypeTargetManager(WalkingGnomePlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull Set<TypeTarget> getTargetsFromConfig(String path) {
        Set<TypeTarget> tags = new HashSet<>();
        for (String materialOrTag : plugin.getConfig().getStringList(path)) {
            fillTargets(path, tags, materialOrTag);
        }
        return tags;
    }

    public void fillTargets(@Nullable String path, Set<TypeTarget> tags, @NotNull String materialOrTag) {
        int amount = 1;
        String amountString = StringUtils.substringBetween(materialOrTag, "(", ")");
        if (amountString != null) {
            materialOrTag = materialOrTag.replace("(" + amountString + ")", "");
            amount = amountString.equalsIgnoreCase("$RANDOM") ? -1 : PluginUtils.getRangedAmount(amountString);
        }

        if (materialOrTag.startsWith("$")) {
            String tagName = materialOrTag.substring(1);

            if (addMaterialsFromRegistry(
                    tags,
                    tagName.toLowerCase(Locale.ROOT),
                    amount,
                    Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS)) return;

            return;
        }

        if (materialOrTag.startsWith(";")) {
            String regex = materialOrTag.substring(1);

            for (Material value : Material.values()) {
                if (value.name().matches(regex)) {
                    addAndOverride(tags, createTarget(amount, value));
                }
            }

            return;
        }

        Material material = PluginUtils.getOrNull(Material.class, materialOrTag.toUpperCase(Locale.ROOT));
        if (material != null) {
            addAndOverride(tags, createTarget(amount, material));
        } else {
            log(path, materialOrTag);
        }
    }

    private void log(@Nullable String path, String materialOrTag) {
        if (path != null) plugin.getLogger().info("Invalid material for " + "{" + path + "}! " + materialOrTag);
    }

    private @NotNull TypeTarget createTarget(int amount, Material material) {
        return new TypeTarget(amount, material);
    }

    @SuppressWarnings("SameParameterValue")
    private boolean addMaterialsFromRegistry(Set<TypeTarget> typeTargets, String tagName, int amount, String @NotNull ... registries) {
        boolean found = false;
        for (String registry : registries) {
            Tag<Material> tag = Bukkit.getTag(registry, NamespacedKey.minecraft(tagName), Material.class);
            if (tag == null) continue;

            for (Material material : tag.getValues()) {
                addAndOverride(typeTargets, createTarget(amount, material));
            }
            found = true;
        }
        return found;
    }

    private void addAndOverride(Set<TypeTarget> typeTargets, @NotNull TypeTarget newTarget) {
        Material type = newTarget.type();

        TypeTarget temp = getTarget(typeTargets, type);
        if (temp != null) {
            typeTargets.remove(temp);
        }

        typeTargets.add(newTarget);
    }

    private @Nullable TypeTarget getTarget(@NotNull Set<TypeTarget> typeTargets, Material type) {
        for (TypeTarget typeTarget : typeTargets) {
            if (typeTarget.is(type)) {
                return typeTarget;
            }
        }
        return null;
    }
}