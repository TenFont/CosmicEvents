package com.cosmic901.cosmicevents.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MessagesTest {
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    @Test
    void parsesLegacyFormattingWithoutPrintingCodes() {
        assertEquals("EVENT » Ready!", PLAIN.serialize(
                Messages.parse("&6&lEVENT &8» &aReady!")));
    }

    @Test
    void parsesMixedLegacyPrefixAndMiniMessageBody() {
        assertEquals("EVENT » Game starting now!", PLAIN.serialize(
                Messages.parse("&6&lEVENT &8» &r<green>Game starting <bold>now</bold>!</green>")));
    }

    @Test
    void handlesEmptyMessages() {
        assertEquals("", PLAIN.serialize(Messages.parse(null)));
        assertEquals("", PLAIN.serialize(Messages.parse("")));
    }
}

