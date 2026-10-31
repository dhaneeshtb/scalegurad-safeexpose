package com.scaleguard.safeexpose;

import org.json.JSONObject;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Keeps a tunnel up across network failures. The server-side tunnel (route, credentials) is created once; when the link
 * drops only the SSH connection is re-opened, with exponential back-off while the network is down, so a flaky connection does not
 * churn the server's route. If the server no longer knows the tunnel (it restarted) it is created again.
 */
final class Reconnector {

    enum Outcome {
        /** The link was up and then lost. */
        LOST,
        /** The connection could not be made at all (no network, port closed). */
        NOT_CONNECTED,
        /** The server answered but refused the tunnel's login: it forgot the tunnel. */
        FORGOTTEN
    }

    /** What one connection attempt did; {@code upMillis} is how long it stayed up. */
    static final class Attempt {
        private final Outcome outcome;
        private final long upMillis;
        private final String detail;

        Attempt(Outcome outcome, long upMillis, String detail) {
            this.outcome = outcome;
            this.upMillis = upMillis;
            this.detail = detail;
        }

        Outcome outcome() { return outcome; }
        long upMillis() { return upMillis; }
        String detail() { return detail; }
    }

    interface Provision {
        /** Creates the tunnel on the server (login, remove stale, create). May throw LoginChecker.NetworkException / HttpFailure. */
        JSONObject create();
    }

    interface Connect {
        /** Opens the SSH tunnel for the created tunnel and blocks until it ends. */
        Attempt run(JSONObject tunnel);
    }

    interface Sleeper {
        void sleep(int seconds) throws InterruptedException;
    }

    private final int initialSeconds, maxSeconds, healthySeconds;
    private final Sleeper sleeper;
    private final Consumer<String> log;

    Reconnector(int initialSeconds, int maxSeconds, int healthySeconds, Sleeper sleeper, Consumer<String> log) {
        this.initialSeconds = Math.max(1, initialSeconds);
        this.maxSeconds = Math.max(this.initialSeconds, maxSeconds);
        this.healthySeconds = healthySeconds;
        this.sleeper = sleeper;
        this.log = log;
    }

    static int nextDelay(int current, int initial, int max, boolean wasHealthy) {
        return wasHealthy ? initial : (int) Math.min((long) current * 2, max);
    }

    /** Runs until {@code running} says stop, or the server refuses for a reason retrying cannot fix. */
    void run(Provision provision, Connect connect, BooleanSupplier running) {
        JSONObject tunnel = null;
        int delay = initialSeconds;
        while (running.getAsBoolean()) {
            boolean healthy = false;
            try {
                if (tunnel == null) {
                    tunnel = provision.create();
                    log.accept("Tunnel created on the server");
                }
                Attempt a = connect.run(tunnel);
                if (!running.getAsBoolean()) return;
                healthy = a.outcome() == Outcome.LOST && a.upMillis() >= healthySeconds * 1000L;
                if (a.outcome() == Outcome.FORGOTTEN) {
                    log.accept("The server no longer knows this tunnel (restarted?). Creating it again.");
                    tunnel = null;
                    delay = initialSeconds;
                    continue;                                      // straight away, no wait
                }
                String why = a.detail() == null ? "" : " (" + a.detail() + ")";
                if (a.outcome() == Outcome.LOST) log.accept("Connection lost" + why + ". Reconnecting in " + delay + " s...");
                else log.accept("Cannot reach the tunnel server" + why + ". Retrying in " + delay + " s...");
            } catch (LoginChecker.NetworkException e) {
                log.accept("Cannot reach ScaleGuard (" + e.getMessage() + "). Retrying in " + delay + " s...");
            } catch (LoginChecker.HttpFailure e) {
                if (!e.retryable()) {
                    log.accept(e.getMessage());
                    return;
                }
                log.accept("ScaleGuard answered " + e.code + ". Retrying in " + delay + " s...");
                tunnel = null;
            }
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                return;
            }
            delay = nextDelay(delay, initialSeconds, maxSeconds, healthy);
        }
    }
}
