package com.cosmic901.cosmicevents.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRunnerTest {
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void plainTellrawTextBecomesAJsonComponent() {
        String result = CommandRunner.normalizeTellraw("tellraw Steve you won");
        assertTrue(result.startsWith("tellraw Steve "), result);
        Component component = GSON.deserialize(result.substring("tellraw Steve ".length()));
        assertEquals("you won", PLAIN.serialize(component));
    }

    @Test
    void prepareStripsSlashAndConvertsTellraw() {
        String result = CommandRunner.prepare("/tellraw  Steve   you won");
        assertTrue(result.startsWith("tellraw Steve "), result);
        Component component = GSON.deserialize(result.substring("tellraw Steve ".length()));
        assertEquals("you won", PLAIN.serialize(component));
    }

    @Test
    void legacyColourCodesSurviveConversion() {
        String result = CommandRunner.normalizeTellraw("tellraw Steve &aYou won!");
        Component component = GSON.deserialize(result.substring("tellraw Steve ".length()));
        assertEquals("You won!", PLAIN.serialize(component));
        assertEquals(NamedTextColor.GREEN, component.color());
    }

    @Test
    void selectorsWithBracketsAreKeptIntact() {
        String result = CommandRunner.normalizeTellraw("minecraft:tellraw @a[distance=..5] hi there");
        assertTrue(result.startsWith("minecraft:tellraw @a[distance=..5] "), result);
        Component component = GSON.deserialize(result.substring("minecraft:tellraw @a[distance=..5] ".length()));
        assertEquals("hi there", PLAIN.serialize(component));
    }

    @Test
    void existingJsonIsLeftAlone() {
        String json = "tellraw Steve {\"text\":\"already json\",\"color\":\"gold\"}";
        assertEquals(json, CommandRunner.normalizeTellraw(json));
        String array = "tellraw Steve [\"\",{\"text\":\"a\"}]";
        assertEquals(array, CommandRunner.normalizeTellraw(array));
        String quoted = "tellraw Steve \"plain string\"";
        assertEquals(quoted, CommandRunner.normalizeTellraw(quoted));
    }

    @Test
    void otherCommandsAreUntouched() {
        assertEquals("etokens give Steve 1 -s", CommandRunner.normalizeTellraw("etokens give Steve 1 -s"));
        assertEquals("tellraw", CommandRunner.normalizeTellraw("tellraw"));
        assertEquals("tellraw Steve", CommandRunner.normalizeTellraw("tellraw Steve"));
        assertEquals("", CommandRunner.normalizeTellraw(null));
    }

    @Test
    void rootDetection() {
        assertEquals("tellraw", CommandRunner.rootOf("tellraw Steve hi"));
        assertEquals("lp", CommandRunner.rootOf("  lp user x"));
        assertEquals("", CommandRunner.rootOf(null));
        assertFalse(CommandRunner.looksLikeComponent("you won"));
        assertTrue(CommandRunner.looksLikeComponent("{\"text\":\"x\"}"));
    }
}
