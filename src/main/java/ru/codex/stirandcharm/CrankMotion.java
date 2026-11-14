package ru.codex.stirandcharm;

/** Server-time crank validation: a whole turn in one direction, not a click or a straight drag. */
public final class CrankMotion {
    public record Sample(boolean crank, float turn) {}

    private static final float TURN = (float) (Math.PI * 2.0);
    private static final float MAX_STEP = 0.45F;
    private static final int STALE = 20;

    private long last = Long.MIN_VALUE;
    private float x, z;
    private float net, total;

    public Sample accept(long tick, float nextX, float nextZ) {
        if (!Float.isFinite(nextX) || !Float.isFinite(nextZ) || Math.hypot(nextX, nextZ) > 3.01F) return null;
        if (last != Long.MIN_VALUE && tick <= last) return null;
        if (last != Long.MIN_VALUE && tick - last > STALE) { net = 0; total = 0; }
        long elapsed = last == Long.MIN_VALUE ? 1 : tick - last;
        float turn = 0;
        if (last != Long.MIN_VALUE && elapsed <= 6 && Math.hypot(x, z) > 0.6F && Math.hypot(nextX, nextZ) > 0.6F) {
            double a = Math.atan2(nextZ, nextX) - Math.atan2(z, x);
            a = Math.atan2(Math.sin(a), Math.cos(a));
            float step = (float) Math.min(Math.abs(a), MAX_STEP * elapsed);
            turn = (float) Math.copySign(step, a);
        }
        x = nextX;
        z = nextZ;
        last = tick;
        if (turn == 0) return new Sample(false, 0);
        net += turn;
        total += Math.abs(turn);
        if (Math.abs(net) >= TURN && Math.abs(net) / Math.max(0.0001F, total) >= 0.75F) {
            net = 0;
            total = 0;
            return new Sample(true, Math.abs(turn));
        }
        return new Sample(false, turn);
    }

    public void reset() {
        last = Long.MIN_VALUE;
        x = z = 0;
        net = total = 0;
    }
}