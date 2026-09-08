package com.cosmic901.cosmicevents.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPlaceholdersTest {

    @Test
    void needsPlayerDetectsCommonPlaceholders() {
        assertTrue(CommandPlaceholders.needsPlayer("etokens give {player} 1 -s"));
        assertTrue(CommandPlaceholders.needsPlayer("lp user %player% permission set x true"));
        assertTrue(CommandPlaceholders.needsPlayer("msg {uuid} hi"));
        assertFalse(CommandPlaceholders.needsPlayer("broadcast Event started"));
        assertFalse(CommandPlaceholders.needsPlayer("ba forcestart {arena}"));
    }

    @Test
    void stripLeadingSlashCollapsesWhitespace() {
        assertEquals("etokens give Steve 1 -s",
                CommandPlaceholders.stripLeadingSlash("etokens give Steve 1  -s"));
        assertEquals("etokens give Steve 1 -s",
                CommandPlaceholders.stripLeadingSlash("/etokens give Steve 1  -s"));
    }

    @Test
    void applyRewardSupportsPercentAndPositionPlaceholders() {
        assertEquals("etokens give %player% 1 -s",
                CommandPlaceholders.applyReward("etokens give %player% 1 -s", null, 1));
        assertEquals("say finished 2",
                CommandPlaceholders.applyReward("say finished {position}", null, 2));
    }
}

