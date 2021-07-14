package net.minecraftforge.installer.test;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import net.minecraftforge.installer.json.Util;

public class TestTokens {
    @Test
    public void testTokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("VERSION", "1.17");
        tokens.put("NAME", "Foo");
        Assert.assertEquals(Util.replaceTokens(tokens, "{VERSION}"), "1.17");
        Assert.assertEquals(Util.replaceTokens(tokens, "{NAME}"), "Foo");
        Assert.assertEquals(Util.replaceTokens(tokens, "{NAME}-{VERSION}"), "Foo-1.17");
        Assert.assertEquals(Util.replaceTokens(tokens, "{NAME}/{VERSION}/something"), "Foo/1.17/something");
        Assert.assertEquals(Util.replaceTokens(tokens, "{VERSION}}"), "1.17}");
        Assert.assertThrows(IllegalArgumentException.class, () -> Util.replaceTokens(tokens, "{{VERSION}"));
        Assert.assertEquals(Util.replaceTokens(tokens, "'{VERSION}'"), "{VERSION}");
        Assert.assertEquals(Util.replaceTokens(tokens, "'test'"), "test");
        Assert.assertEquals(Util.replaceTokens(tokens, "This is a \\'test\\'"), "This is a 'test'");
    }
}
