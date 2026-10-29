package com.alteredarena.rules;

/**
 * The six fictional arcade power-ups. This table is the single place to tune their effects.
 * Colors are RGBA8888.
 */
public enum PowerUpType {
    //         label        tagline                                   seconds  color         effect
    WEED(     "WEED",      "Take 35% less incoming damage",            15f, 0x4CFF5AFF, Modifiers.NONE.damageTaken(0.65f)),
    MOLLY(    "MOLLY",     "Heal 25% of the damage you deal",          12f, 0xFF4FD8FF, Modifiers.NONE.lifesteal(0.25f)),
    ACID(     "ACID",      "Shots pierce one extra opponent",          12f, 0xF2FF3BFF, Modifiers.NONE.extraPierce(1)),
    MUSHROOMS("MUSHROOMS", "Double jump, 25% higher jumps",            15f, 0xB066FFFF, Modifiers.NONE.extraJumps(1).jumpHeight(1.25f)),
    COKE(     "COKE",      "40% faster movement, sharper acceleration", 10f, 0xCFF4FFFF, Modifiers.NONE.speed(1.40f).acceleration(1.6f)),
    ADDY(     "ADDY",      "30% faster fire rate, 50% less spread",    12f, 0xFF9A1FFF, Modifiers.NONE.fireRate(1.30f).spread(0.5f));

    public final String label;
    public final String description;
    public final float duration;
    public final int rgba;
    public final Modifiers modifiers;

    PowerUpType(String label, String description, float duration, int rgba, Modifiers modifiers) {
        this.label = label;
        this.description = description;
        this.duration = duration;
        this.rgba = rgba;
        this.modifiers = modifiers;
    }
}
