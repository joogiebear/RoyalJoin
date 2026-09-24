package com.mystipixel.royaljoin;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limits item activations, in two stages.
 *
 * <p>A short gap between uses stops a menu re-opening on every frame of a held click. On top of that,
 * a burst of activations inside a short window trips a lockout — the pattern an auto-clicker produces,
 * which a simple per-use delay would happily serve forever at exactly the delay interval.
 *
 * <p>The burst window slides: it always covers the last {@code spamWindowMillis}, so a burst can't
 * dodge the guard by straddling the boundary of a fixed window.
 *
 * <p>Main-thread only: clicks arrive on the server thread, so no locking. State is dropped when a
 * player leaves so the map can't grow without bound.
 */
public final class CooldownTracker {

    /** What a click is allowed to do. */
    public enum Result {
        /** Run the command. */
        ALLOW,
        /** Too soon after the last use; say nothing, the player is just clicking fast. */
        TOO_SOON,
        /** A burst just tripped the lockout — worth telling the player once. */
        LOCKED_OUT_NOW,
        /** Already locked out; stay quiet until it expires. */
        STILL_LOCKED_OUT
    }

    private static final class State {
        long lastUse = Long.MIN_VALUE;
        /** Times of recent activations, oldest first, never older than the spam window. */
        final Deque<Long> recent = new ArrayDeque<>();
        long lockedUntil;
    }

    private final Map<UUID, State> states = new HashMap<>();

    private long betweenUsesMillis = 400;
    private int spamThreshold = 6;
    private long spamWindowMillis = 3000;
    private long lockoutMillis = 5000;

    public void configure(long betweenUsesMillis, int spamThreshold, long spamWindowMillis, long lockoutSeconds) {
        this.betweenUsesMillis = Math.max(0, betweenUsesMillis);
        this.spamThreshold = Math.max(0, spamThreshold);
        this.spamWindowMillis = Math.max(1, spamWindowMillis);
        this.lockoutMillis = Math.max(0, lockoutSeconds) * 1000L;
    }

    /** Test and record a click. Call once per activation attempt. */
    public Result check(Player player) {
        return check(player.getUniqueId(), System.currentTimeMillis());
    }

    Result check(UUID id, long now) {
        State state = states.computeIfAbsent(id, key -> new State());

        if (now < state.lockedUntil) {
            return Result.STILL_LOCKED_OUT;
        }
        if (betweenUsesMillis > 0 && state.lastUse != Long.MIN_VALUE && now - state.lastUse < betweenUsesMillis) {
            return Result.TOO_SOON;
        }
        state.lastUse = now;

        // Forget activations that have slid out of the window, so ordinary use never accumulates.
        while (!state.recent.isEmpty() && now - state.recent.peekFirst() >= spamWindowMillis) {
            state.recent.pollFirst();
        }
        state.recent.addLast(now);

        if (spamThreshold > 0 && state.recent.size() >= spamThreshold) {
            state.lockedUntil = now + lockoutMillis;
            state.recent.clear();
            return Result.LOCKED_OUT_NOW;
        }
        return Result.ALLOW;
    }

    /** Seconds left on a player's lockout, for the message. */
    public long secondsRemaining(Player player) {
        return secondsRemaining(player.getUniqueId(), System.currentTimeMillis());
    }

    long secondsRemaining(UUID id, long now) {
        State state = states.get(id);
        if (state == null) {
            return 0;
        }
        return Math.max(0, (state.lockedUntil - now + 999) / 1000);
    }

    public void forget(Player player) {
        states.remove(player.getUniqueId());
    }
}
