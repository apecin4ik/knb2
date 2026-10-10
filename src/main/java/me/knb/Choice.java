package me.knb;

import org.bukkit.Material;

public enum Choice {
    ROCK("Камень", Material.COBBLESTONE, "§7"),
    SCISSORS("Ножницы", Material.SHEARS, "§e"),
    PAPER("Бумага", Material.PAPER, "§f");

    private final String name;
    private final Material icon;
    private final String color;

    Choice(String name, Material icon, String color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public Material getIcon() {
        return icon;
    }

    public String getColored() {
        return color + "§l" + name;
    }

    /** Бьёт ли этот ход ход other. */
    public boolean beats(Choice other) {
        return (this == ROCK && other == SCISSORS)
                || (this == SCISSORS && other == PAPER)
                || (this == PAPER && other == ROCK);
    }

    /** Какой ход этот ход бьёт. */
    public Choice victim() {
        switch (this) {
            case ROCK:
                return SCISSORS;
            case SCISSORS:
                return PAPER;
            default:
                return ROCK;
        }
    }
}
