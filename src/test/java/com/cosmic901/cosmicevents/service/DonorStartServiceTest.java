package com.cosmic901.cosmicevents.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DonorStartServiceTest {
    private static final long HOUR = 3600_000L;
    private static final long DAY = 24L * HOUR;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void freshUseBlocksForTheFullWindow() {
        assertEquals(DAY, DonorStartService.remainingCooldownMillis(NOW, NOW, DAY));
    }

    @Test
    void partiallyElapsedCooldownReportsWhatIsLeft() {
        assertEquals(DAY - HOUR, DonorStartService.remainingCooldownMillis(NOW - HOUR, NOW, DAY));
        assertEquals(HOUR, DonorStartService.remainingCooldownMillis(NOW - 23L * HOUR, NOW, DAY));
    }

    @Test
    void expiredCooldownIsZeroAndNeverNegative() {
        assertEquals(0L, DonorStartService.remainingCooldownMillis(NOW - DAY, NOW, DAY));
        assertEquals(0L, DonorStartService.remainingCooldownMillis(NOW - 10L * DAY, NOW, DAY));
    }

    @Test
    void zeroWindowDisablesTheCooldown() {
        assertEquals(0L, DonorStartService.remainingCooldownMillis(NOW, NOW, 0L));
    }

    @Test
    void clockMovingBackwardsDoesNotGrantAFreeStart() {
        assertEquals(DAY, DonorStartService.remainingCooldownMillis(NOW + HOUR, NOW, DAY));
    }
}

