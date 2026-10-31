package com.scaleguard.safeexpose;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TunnelManagerTest {
    @Test
    public void justAPortExposesIt() {
        assertArrayEquals(new String[]{"app-8080", "8080"}, TunnelManager.parseExpose(new String[]{"8080"}));
        assertArrayEquals(new String[]{"shop", "3000"}, TunnelManager.parseExpose(new String[]{"3000", "shop"}));
    }

    @Test
    public void theOlderStartFormStillWorks() {
        assertArrayEquals(new String[]{"my-app", "8080"}, TunnelManager.parseExpose(new String[]{"start", "my-app", "8080"}));
        assertArrayEquals(new String[]{"app-9000", "9000"}, TunnelManager.parseExpose(new String[]{"expose", "9000"}));
    }

    @Test
    public void nonsenseIsNotAnExposeRequest() {
        assertNull(TunnelManager.parseExpose(new String[]{"bogus"}));
        assertNull(TunnelManager.parseExpose(new String[]{"70000"}));
        assertNull(TunnelManager.parseExpose(new String[]{"start", "name"}));
        assertFalse(TunnelManager.isPort("0"));
        assertTrue(TunnelManager.isPort("65535"));
    }

    @Test
    public void domainOptionIsSplitOffTheArguments() {
        String[][] s = TunnelManager.splitDomain(new String[]{"8080", "--domain", "dhaneesh.in", "shop"});
        assertArrayEquals(new String[]{"8080", "shop"}, s[0]);
        assertEquals("dhaneesh.in", s[1][0]);
        assertEquals("a.com", TunnelManager.splitDomain(new String[]{"-d", "a.com", "8080"})[1][0]);
        assertEquals("b.com", TunnelManager.splitDomain(new String[]{"8080", "--domain=b.com"})[1][0]);
        assertNull(TunnelManager.splitDomain(new String[]{"8080"})[1][0]);
    }

    @Test
    public void serverAddressesGetAScheme() {
        assertEquals("https://sg.example.com", TunnelManager.normalizeServer(" sg.example.com/ "));
        assertEquals("http://localhost:80", TunnelManager.normalizeServer("http://localhost:80"));
    }
}
