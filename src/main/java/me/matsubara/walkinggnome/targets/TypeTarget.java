package me.matsubara.walkinggnome.targets;

import org.bukkit.Material;

public record TypeTarget(int amount, Material type) {

    public boolean is(Material type) {
        return this.type == type;
    }
}