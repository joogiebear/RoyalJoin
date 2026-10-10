package com.mystipixel.royaljoin;

import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limits item activations: a minimum gap between uses, plus a lockout when a sliding window sees
 * a burst (an auto-clicker would otherwise be served forever at exactly the gap interval).
 *
 * <p>Main-thread only, no locking.
 */
public final class CooldownTracker {

    public enum Result {
        ALLOW,
        /** Too soon after the last use; say nothing, the player is just clicking fast. */
        TOO_SOON,
        /** A burst just tripped the lockout: tell the player once. */
        LOCKED_OUT_NOW,
        /** Already locked out; stay quiet until it expires. */
        STILL_LOCKED_OUT
    }

    private static final class State {
        long lastUse = Long.MIN_VALUE;
        // oldest first, never older than the spam window
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
