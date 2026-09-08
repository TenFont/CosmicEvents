package com.cosmic901.cosmicevents.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinStatsServiceTest {
    @Test
    void incrementStartsAtOneAndStacks() {
        Map<UUID, Integer> wins = new HashMap<>();
        UUID player = UUID.randomUUID();

        assertEquals(1, WinStatsService.increment(wins, player));
        assertEquals(2, WinStatsService.increment(wins, player));
        assertEquals(2, wins.get(player));
    }

    @Test
    void incrementTracksPlayersSeparately() {
        Map<UUID, Integer> wins = new HashMap<>();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        WinStatsService.increment(wins, a);
        WinStatsService.increment(wins, a);
        WinStatsService.increment(wins, b);

        assertEquals(2, wins.get(a));
        assertEquals(1, wins.get(b));
    }
}

