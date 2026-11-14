package ru.codex.stirandcharm;

/** Server-time motion validation. One sample per tick, a radius and speed bound, no client timestamps. */
public final class StirMotion {
    private long last = Long.MIN_VALUE;
    private float x, z;
    public record Sample(float x, float z, float impulse) {}
    public Sample accept(long tick, float nextX, float nextZ) {
        if (!Float.isFinite(nextX) || !Float.isFinite(nextZ) || Math.hypot(nextX, nextZ) > 3.01) return null;
        if (last != Long.MIN_VALUE && tick <= last) return null;
        long elapsed = last == Long.MIN_VALUE ? Long.MAX_VALUE : tick - last;
        float turn = 0;
        if (elapsed <= 6 && Math.hypot(x, z) > 0.6 && Math.hypot(nextX, nextZ) > 0.6) {
            double a = Math.atan2(nextZ, nextX) - Math.atan2(z, x);
            a = Math.atan2(Math.sin(a), Math.cos(a));
            // Radial movement does no work. Limit each interval to an ordinary hand's turn.
            turn = (float) Math.min(Math.abs(a), 0.45 * elapsed);
        }
        x = nextX; z = nextZ; last = tick;
        return new Sample(x, z, Math.min(0.25F, turn * 0.35F));
    }
}
