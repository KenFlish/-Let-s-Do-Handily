package ru.codex.stirandcharm;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;

/** Where the next cuts of one item lie: chosen on the server and drawn by every client. */
public final class BoardCutPlan {
    public enum Style { ACROSS, STRAIGHT, WEDGES, STRIPS }

    public static final int MOST_MARKS = 3;
    private static final float SPOT_FROM = .2F, SPOT_TO = .8F, JITTER = .2F;

    private final int marks;
    private final float[] spots;
    private final float[] angles;
    private final Style style;

    private BoardCutPlan(int marks, float[] spots, float[] angles, Style style) {
        this.marks = marks;
        this.spots = spots;
        this.angles = angles;
        this.style = style;
    }

    public int marks() { return marks; }
    public float spot(int mark) { return spots[Math.min(mark, spots.length - 1)]; }
    public float angle(int mark) { return angles[Math.min(mark, angles.length - 1)]; }
    public Style style() { return style; }

    public static BoardCutPlan of(net.minecraft.world.level.Level level, ItemStack stack, CuttingBoardRecipe recipe) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Style style = path.contains("cake") || path.contains("pie") || path.contains("tart") ? Style.WEDGES
            : path.contains("dough") || path.contains("pasta") || path.contains("noodle") ? Style.STRIPS
            : path.contains("roll") ? Style.STRAIGHT : Style.ACROSS;
        int marks = style == Style.STRIPS ? 3 : Mth.clamp(recipe == null ? 1 : recipe.getChops(), 1, MOST_MARKS);
        float[] spots = new float[marks], angles = new float[marks];
        RandomSource random = level.random;
        float room = (SPOT_TO - SPOT_FROM) / marks;
        float turn = random.nextFloat() * Mth.PI;
        for (int mark = 0; mark < marks; mark++) {
            switch (style) {
                case WEDGES -> { spots[mark] = .5F; angles[mark] = turn + mark * Mth.PI / marks; }
                case STRAIGHT, STRIPS -> {
                    float span = style == Style.STRIPS ? .5F : 1F;
                    spots[mark] = .5F + ((mark + 1F) / (marks + 1F) - .5F) * span;
                    angles[mark] = 0F;
                }
                default -> {
                    float even = SPOT_FROM + (mark + .5F) * room;
                    float stray = marks == 1 ? .15F : room * JITTER;
                    spots[mark] = even + Mth.nextFloat(random, -stray, stray);
                    angles[mark] = Mth.nextFloat(random, -1F, 1F);
                }
            }
        }
        if (style == Style.ACROSS) {
            for (int attempt = 0; attempt < 30 && crosses(spots, angles, marks); attempt++) {
                for (int mark = 0; mark < marks; mark++) angles[mark] = Mth.nextFloat(random, -1F, 1F);
            }
            if (crosses(spots, angles, marks)) java.util.Arrays.fill(angles, 0, marks, angles[0]);
            for (int mark = marks - 1; mark > 0; mark--) {
                int other = random.nextInt(mark + 1);
                float s = spots[mark]; spots[mark] = spots[other]; spots[other] = s;
                float a = angles[mark]; angles[mark] = angles[other]; angles[other] = a;
            }
        }
        return new BoardCutPlan(marks, spots, angles, style);
    }

    private static boolean crosses(float[] spots, float[] angles, int count) {
        for (int a = 0; a < count; a++) for (int b = a + 1; b < count; b++) {
            float ax = Mth.cos(angles[a] * .35F), az = Mth.sin(angles[a] * .35F);
            float bx = Mth.cos(angles[b] * .35F), bz = Mth.sin(angles[b] * .35F);
            float across = ax * bz - az * bx;
            if (Math.abs(across) < 1e-4F) continue;
            float t = (0F * bz - (spots[b] - spots[a]) * bx) / across;
            float x = .5F + t * ax, z = spots[a] + t * az;
            if (x >= 0F && x <= 1F && z >= 0F && z <= 1F) return true;
        }
        return false;
    }
}