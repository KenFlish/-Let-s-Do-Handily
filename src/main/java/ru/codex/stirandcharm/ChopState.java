package ru.codex.stirandcharm;

/** How many native chops the board already counts for the item lying on it. */
public interface ChopState {
    void handily$pendingChops(int value);
}