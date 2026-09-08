package com.cosmic901.cosmicevents.service;

import com.cosmic901.cosmicevents.EventState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventManagerJoinWindowTest {

    @Test
    void joiningPhaseAlwaysAccepts() {
        assertTrue(EventManager.acceptsJoin(EventState.JOINING, false, -1));
        assertTrue(EventManager.acceptsJoin(EventState.JOINING, true, 1));
    }

    @Test
    void lastSecondCommandsCanEnterBeforeTheMinigameStarts() {
        assertTrue(EventManager.acceptsJoin(EventState.GAME_ACTIVE, false, 1));
        assertFalse(EventManager.acceptsJoin(EventState.GAME_ACTIVE, true, 1));
        assertFalse(EventManager.acceptsJoin(EventState.GAME_ACTIVE, false, -1));
        assertFalse(EventManager.acceptsJoin(EventState.IDLE, false, 1));
        assertFalse(EventManager.acceptsJoin(EventState.STARTING_GAME, false, 1));
    }

    @Test
    void consumeRestoreWaitsUntilRewardTick() {
        assertTrue(InventorySnapshotService.RESTORE_CONSUME_DELAY_TICKS
                >= EventManager.REWARD_DELAY_TICKS);
        assertTrue(InventorySnapshotService.RESTORE_RETRY_TICKS
                < InventorySnapshotService.RESTORE_CONSUME_DELAY_TICKS);
    }
}
