package com.scaleguard.safeexpose;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReconnectorTest {

    private final List<Integer> sleeps = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();

    private Reconnector reconnector(int initial, int max) {
        return new Reconnector(initial, max, 30, sleeps::add, logs::add);
    }

    private static Reconnector.Attempt lost(long upMillis) {
        return new Reconnector.Attempt(Reconnector.Outcome.LOST, upMillis, "reset");
    }

    private static Reconnector.Attempt notConnected() {
        return new Reconnector.Attempt(Reconnector.Outcome.NOT_CONNECTED, 0, "no route");
    }

    @Test
    public void backoffDoublesToTheCapThenKeepsRetrying() {
        AtomicInteger attempts = new AtomicInteger();
        reconnector(2, 10).run(() -> new JSONObject(), t -> attempts.incrementAndGet() >= 7 ? null : notConnected(), () -> attempts.get() < 7);
        assertEquals(List.of(2, 4, 8, 10, 10, 10), sleeps);
    }

    @Test
    public void theServerSideTunnelIsCreatedOnceWhileOnlyTheLinkFails() {
        AtomicInteger created = new AtomicInteger(), connects = new AtomicInteger();
        reconnector(1, 8).run(() -> { created.incrementAndGet(); return new JSONObject(); },
                t -> { connects.incrementAndGet(); return lost(1000); }, () -> connects.get() < 5);
        assertEquals("route is not recreated on every reconnect", 1, created.get());
        assertEquals(5, connects.get());
    }

    @Test
    public void aLinkThatStayedUpResetsTheBackoff() {
        AtomicInteger n = new AtomicInteger();
        Supplier<Reconnector.Attempt> script = () -> {
            switch (n.incrementAndGet()) {
                case 1: case 2: return notConnected();      // 1 s, then 2 s
                case 3: return lost(60_000);                // was healthy for a minute: next wait is short again
                default: return notConnected();
            }
        };
        reconnector(1, 60).run(() -> new JSONObject(), t -> script.get(), () -> n.get() < 5);
        assertEquals(List.of(1, 2, 4, 1), sleeps);
    }

    @Test
    public void networkFailuresWhileCreatingTheTunnelAreRetried() {
        AtomicInteger tries = new AtomicInteger(), connects = new AtomicInteger();
        reconnector(1, 8).run(() -> {
            if (tries.incrementAndGet() < 3) throw new LoginChecker.NetworkException("UnknownHostException");
            return new JSONObject();
        }, t -> { connects.incrementAndGet(); return lost(0); }, () -> connects.get() < 1);
        assertEquals(3, tries.get());
        assertEquals(List.of(1, 2), sleeps);
        assertTrue(logs.get(0), logs.get(0).contains("Cannot reach ScaleGuard"));
    }

    @Test
    public void aServerThatForgotTheTunnelGetsItCreatedAgainAtOnce() {
        AtomicInteger created = new AtomicInteger(), connects = new AtomicInteger();
        reconnector(1, 8).run(() -> { created.incrementAndGet(); return new JSONObject(); }, t -> {
            return connects.incrementAndGet() == 1 ? new Reconnector.Attempt(Reconnector.Outcome.FORGOTTEN, 0, "auth") : lost(0);
        }, () -> connects.get() < 2);
        assertEquals(2, created.get());
        assertTrue("no waiting before re-creating", sleeps.isEmpty());
    }

    @Test
    public void serverErrorsAreRetriedButARefusalStops() {
        AtomicInteger tries = new AtomicInteger();
        reconnector(1, 8).run(() -> {
            if (tries.incrementAndGet() == 1) throw new LoginChecker.HttpFailure(503, "down");
            throw new LoginChecker.HttpFailure(403, "forbidden");
        }, t -> lost(0), () -> true);
        assertEquals("503 retried, 403 gives up", 2, tries.get());
        assertEquals(List.of(1), sleeps);
        assertTrue(logs.get(logs.size() - 1).contains("forbidden"));
    }

    @Test
    public void stopsWhenAskedToEvenDuringTheWait() {
        AtomicInteger attempts = new AtomicInteger();
        new Reconnector(1, 8, 30, s -> { throw new InterruptedException(); }, logs::add)
                .run(() -> new JSONObject(), t -> { attempts.incrementAndGet(); return notConnected(); }, () -> true);
        assertEquals(1, attempts.get());
    }
}
