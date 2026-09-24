package com.mystipixel.royaljoin;

import com.mystipixel.royaljoin.CooldownTracker.Result;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownTrackerTest {

    private final UUID player = UUID.randomUUID();

    /** 400ms gap, lockout on the 6th use inside 3s, 5s lockout — the shipped defaults. */
    private CooldownTracker tracker() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.configure(400, 6, 3000, 5);
        return tracker;
    }

    @Test
    void firstClickIsAllowed() {
        assertEquals(Result.ALLOW, tracker().check(player, 0));
    }

    @Test
    void clickInsideTheGapIsTooSoon() {
        CooldownTracker tracker = tracker();
        tracker.check(player, 1_000);
        assertEquals(Result.TOO_SOON, tracker.check(player, 1_399));
        assertEquals(Result.ALLOW, tracker.check(player, 1_400));
    }

    @Test
    void burstTripsTheLockoutOnce() {
        CooldownTracker tracker = tracker();
        for (int i = 0; i < 5; i++) {
            assertEquals(Result.ALLOW, tracker.check(player, i * 400L));
        }
        assertEquals(Result.LOCKED_OUT_NOW, tracker.check(player, 2_000));
        assertEquals(5, tracker.secondsRemaining(player, 2_000));
        assertEquals(Result.STILL_LOCKED_OUT, tracker.check(player, 6_999));
        assertEquals(Result.ALLOW, tracker.check(player, 7_000));
    }

    @Test
    void steadyUseSlowerThanTheThresholdNeverLocksOut() {
        CooldownTracker tracker = tracker();
        // One use every 700ms: at most five inside any 3s window.
        for (long t = 0; t < 60_000; t += 700) {
            assertEquals(Result.ALLOW, tracker.check(player, t));
        }
    }

    @Test
    void burstStraddlingAWindowBoundaryIsStillCaught() {
        CooldownTracker tracker = tracker();
        tracker.check(player, 0);
        // Idle, then six quick uses spread either side of where a fixed window starting at 0 would end.
        long[] times = {2_000, 2_400, 2_800, 3_200, 3_600};
        for (long t : times) {
            assertEquals(Result.ALLOW, tracker.check(player, t));
        }
        assertEquals(Result.LOCKED_OUT_NOW, tracker.check(player, 4_000));
    }

    @Test
    void zeroThresholdDisablesTheBurstGuard() {
        CooldownTracker tracker = new CooldownTracker();
        tracker.configure(0, 0, 3000, 5);
        for (int i = 0; i < 100; i++) {
            assertEquals(Result.ALLOW, tracker.check(player, i));
        }
    }

    @Test
    void playersAreTrackedSeparately() {
        CooldownTracker tracker = tracker();
        tracker.check(player, 0);
        assertEquals(Result.ALLOW, tracker.check(UUID.randomUUID(), 1));
    }
}
