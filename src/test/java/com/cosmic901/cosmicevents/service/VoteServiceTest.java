package com.cosmic901.cosmicevents.service;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteServiceTest {

    @Test
    void selectsHighestWeightedVote() {
        List<String> games = List.of("tnt_run", "block_party", "spleef");
        Map<String, Integer> votes = new LinkedHashMap<>();
        votes.put("tnt_run", 2);
        votes.put("block_party", 5);
        votes.put("spleef", 1);

        String winner = VoteService.selectWinner(games, votes, new Random(1L));
        assertEquals("block_party", winner);
    }

    @Test
    void breaksTiesDeterministicallyWithSeed() {
        List<String> games = List.of("a", "b", "c");
        Map<String, Integer> votes = Map.of("a", 3, "b", 3, "c", 1);

        String winner = VoteService.selectWinner(games, votes, new Random(42L));
        assertTrue(winner.equals("a") || winner.equals("b"));
        assertEquals(winner, VoteService.selectWinner(games, votes, new Random(42L)));
    }

    @Test
    void returnsNullWhenNoGamesConfigured() {
        assertNull(VoteService.selectWinner(List.of(), Map.of(), new Random(1L)));
    }

    @Test
    void ignoresVotesForUnknownGames() {
        List<String> games = List.of("race");
        Map<String, Integer> votes = Map.of("not_real", 99);
        assertEquals("race", VoteService.selectWinner(games, votes, new Random(7L)));
    }

    @Test
    void normalizeAndValidateMinigames() {
        VoteService service = new VoteService();
        service.reload(List.of("TNT_Run", "block_party"), null, true);
        assertTrue(service.isValidMinigame("tnt_run"));
        assertEquals("TNT_Run", service.normalizeMinigameId("tnt_run"));
    }

    @Test
    void dottedGroupKeysAreFlattenedToLuckPermsNodes() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("permissions.group.sun", 4);
        config.set("permissions.group.moon", 2);
        config.set("permissions.cosmicevents.vote.vip", 3);

        Map<String, Integer> weights = VoteService.flattenPermissionWeights(config);
        assertEquals(4, weights.get("group.sun"));
        assertEquals(2, weights.get("group.moon"));
        assertEquals(3, weights.get("cosmicevents.vote.vip"));
    }

    @Test
    void groupsSectionMapsRankNamesToGroupPermissions() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("groups.sun", 4);
        config.set("groups.moon", 2);

        Map<String, Integer> weights = VoteService.flattenPermissionWeights(config);
        assertEquals(4, weights.get("group.sun"));
        assertEquals(2, weights.get("group.moon"));
    }

    @Test
    void sunRankVoteUsesConfiguredWeightInsteadOfDefault() {
        Map<String, Integer> weights = Map.of("group.sun", 4, "group.moon", 2);
        int sun = VoteService.resolveVoteWeight(1, 5, false, false, weights, "group.sun"::equals);
        int nobody = VoteService.resolveVoteWeight(1, 5, false, false, weights, permission -> false);

        assertEquals(4, sun);
        assertEquals(1, nobody);
    }

    @Test
    void bareRankNameInConfigMatchesLuckPermsGroupPermission() {
        Map<String, Integer> weights = Map.of("sun", 4);
        int sun = VoteService.resolveVoteWeight(1, 5, false, false, weights, "group.sun"::equals);
        assertEquals(4, sun);
    }

    @Test
    void highestMatchingRankWeightWins() {
        Map<String, Integer> weights = Map.of("group.sun", 4, "group.staff", 8);
        int weight = VoteService.resolveVoteWeight(
                1, 5, false, false, weights, Set.of("group.sun", "group.staff")::contains);
        assertEquals(8, weight);
    }

    @Test
    void opsUseOpWeightWhenEnabled() {
        Map<String, Integer> weights = Map.of("group.sun", 4);
        int weight = VoteService.resolveVoteWeight(1, 5, true, true, weights, "group.sun"::equals);
        assertEquals(5, weight);
    }
}

