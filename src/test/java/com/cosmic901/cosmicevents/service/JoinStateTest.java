package com.cosmic901.cosmicevents.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinStateTest {

    @Test
    void yamlRoundTripPreservesOriginAndEffects(@TempDir Path tempDir) throws IOException {
        UUID worldUid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        JoinState snapshot = new JoinState(
                "spawn",
                worldUid,
                10.5D,
                64.0D,
                -3.25D,
                90.0f,
                12.5f,
                "SURVIVAL",
                0.4f,
                0.1f,
                false,
                false,
                List.of(new JoinState.SerializedEffect(
                        "minecraft:speed", 200, 1, false, true, true))
        );

        YamlConfiguration yaml = new YamlConfiguration();
        snapshot.save(yaml);
        File file = tempDir.resolve(UUID.randomUUID() + ".yml").toFile();
        yaml.save(file);

        YamlConfiguration loadedYaml = YamlConfiguration.loadConfiguration(file);
        JoinState loaded = JoinState.load(loadedYaml);

        assertEquals("spawn", loaded.worldName());
        assertEquals(worldUid, loaded.worldUid());
        assertEquals(10.5D, loaded.x());
        assertEquals(64.0D, loaded.y());
        assertEquals(-3.25D, loaded.z());
        assertEquals(90.0f, loaded.yaw());
        assertEquals(12.5f, loaded.pitch());
        assertEquals("SURVIVAL", loaded.gameMode());
        assertEquals(0.4f, loaded.walkSpeed());
        assertEquals(0.1f, loaded.flySpeed());
        assertFalse(loaded.allowFlight());
        assertFalse(loaded.flying());
        assertEquals(1, loaded.effects().size());
        assertEquals("minecraft:speed", loaded.effects().get(0).type());
        assertEquals(200, loaded.effects().get(0).duration());
        assertEquals(1, loaded.effects().get(0).amplifier());
        assertEquals(1, loadedYaml.getInt("version"));
        assertEquals("spawn", loadedYaml.getString("world"));
        assertTrue(loadedYaml.contains("effects"));
    }

    @Test
    void loadAcceptsMapStyleEffectsAndMissingOptionalFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", "world");
        yaml.set("x", 1);
        yaml.set("y", 2);
        yaml.set("z", 3);
        yaml.set("effects", List.of(Map.of(
                "type", "minecraft:jump_boost",
                "duration", 40,
                "amplifier", 2
        )));

        JoinState loaded = JoinState.load(yaml);
        assertEquals("world", loaded.worldName());
        assertEquals(1.0D, loaded.x());
        assertEquals(0.2f, loaded.walkSpeed());
        assertEquals(1, loaded.effects().size());
        assertEquals("minecraft:jump_boost", loaded.effects().get(0).type());
        assertEquals(40, loaded.effects().get(0).duration());
        assertEquals(2, loaded.effects().get(0).amplifier());
        assertTrue(loaded.effects().get(0).particles());
        assertTrue(loaded.effects().get(0).icon());
    }

    @Test
    void loadSkipsEffectsWithoutAType() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", "world");
        yaml.set("effects", List.of(
                Map.of("duration", 10),
                Map.of("type", "minecraft:speed", "duration", 5, "amplifier", 0)
        ));

        JoinState loaded = JoinState.load(yaml);
        assertEquals(1, loaded.effects().size());
        assertEquals("minecraft:speed", loaded.effects().get(0).type());
    }
}
