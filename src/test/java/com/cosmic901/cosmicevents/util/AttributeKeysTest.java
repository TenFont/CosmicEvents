package com.cosmic901.cosmicevents.util;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeKeysTest {

    @Test
    void vanillaModifiersAreLeftAloneOnEnforcementPasses() {
        assertFalse(AttributeKeys.isPluginOwned(NamespacedKey.minecraft("effect.speed")));
        assertFalse(AttributeKeys.isPluginOwned(NamespacedKey.minecraft("effect.jump_boost")));
        assertFalse(AttributeKeys.isPluginOwned(NamespacedKey.minecraft("effect.slow_falling")));
        assertTrue(AttributeKeys.keepDuringLiveGame(NamespacedKey.minecraft("effect.speed")));
        assertTrue(AttributeKeys.keepDuringLiveGame(NamespacedKey.minecraft("effect.jump_boost")));
    }

    @Test
    void pluginOwnedModifiersStayNeutralized() {
        assertTrue(AttributeKeys.isPluginOwned(NamespacedKey.fromString("essentials:speed")));
        assertTrue(AttributeKeys.isPluginOwned(NamespacedKey.fromString("luckperms:donor_speed")));
        assertFalse(AttributeKeys.keepDuringLiveGame(NamespacedKey.fromString("essentials:speed")));
        assertFalse(AttributeKeys.keepDuringLiveGame(NamespacedKey.fromString("luckperms:donor_speed")));
    }

    @Test
    void nullKeyIsIgnored() {
        assertFalse(AttributeKeys.isPluginOwned(null));
        assertFalse(AttributeKeys.keepDuringLiveGame(null));
    }
}

