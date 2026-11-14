package ru.codex.stirandcharm;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Settings {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue POT, BOWL, BOOK, MINCER, STOVE, ROASTER, BOARD, CANDLELIGHT;
    static {
        var b = new ModConfigSpec.Builder();
        POT = b.comment("Enable the cooking pot station; false uses Farm & Charm's normal interaction.").define("cooking_pot", true);
        BOWL = b.comment("Enable the crafting bowl station.").define("crafting_bowl", true);
        MINCER = b.define("mincer", true);
        STOVE = b.define("stove", true);
        ROASTER = b.define("roaster", true);
        BOARD = b.define("cutting_board", true);
        CANDLELIGHT = b.comment("Use Handily scenes for compatible Candlelight cooking stations when installed.").define("candlelight", true);
        BOOK = b.comment("Show the in-world cookbook (server decides for all station users).").define("recipe_book", true);
        SPEC = b.build();
    }
    private Settings() {}
}
