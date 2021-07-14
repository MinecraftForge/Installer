/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
