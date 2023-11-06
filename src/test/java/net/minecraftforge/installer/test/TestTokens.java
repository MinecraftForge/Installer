/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.test;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraftforge.installer.json.Util;
import static org.junit.jupiter.api.Assertions.*;

public class TestTokens {
    @Test
    public void testTokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("VERSION", "1.17");
        tokens.put("NAME", "Foo");
        assertEquals(Util.replaceTokens(tokens, "{VERSION}"), "1.17");
        assertEquals(Util.replaceTokens(tokens, "{NAME}"), "Foo");
        assertEquals(Util.replaceTokens(tokens, "{NAME}-{VERSION}"), "Foo-1.17");
        assertEquals(Util.replaceTokens(tokens, "{NAME}/{VERSION}/something"), "Foo/1.17/something");
        assertEquals(Util.replaceTokens(tokens, "{VERSION}}"), "1.17}");
        assertThrows(IllegalArgumentException.class, () -> Util.replaceTokens(tokens, "{{VERSION}"));
        assertEquals(Util.replaceTokens(tokens, "'{VERSION}'"), "{VERSION}");
        assertEquals(Util.replaceTokens(tokens, "'test'"), "test");
        assertEquals(Util.replaceTokens(tokens, "This is a \\'test\\'"), "This is a 'test'");
    }
}
