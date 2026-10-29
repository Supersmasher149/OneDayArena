package com.alteredarena.audio;

/** Every bundled sound effect; the file is assets/sounds/&lt;name&gt;.wav. */
public enum Sfx {
    RIFLE("rifle"), SCATTERGUN("scattergun"), IMPACT("impact"), HIT("hit"), KILL("kill"), HURT("hurt"),
    DEATH("death"), RESPAWN("respawn"), PICKUP_POWER("pickup_power"), PICKUP_HEALTH("pickup_health"),
    PICKUP_AMMO("pickup_ammo"), PICKUP_SPAWN("pickup_spawn"), EXPIRE("expire"), RELOAD("reload"),
    RELOAD_DONE("reload_done"), DRY("dry"), SWITCH("switch"), JUMP("jump"), LAND("land"),
    UI_CLICK("ui_click"), UI_HOVER("ui_hover"), MATCH_END("match_end");

    public final String path;

    Sfx(String name) {
        this.path = "sounds/" + name + ".wav";
    }
}
