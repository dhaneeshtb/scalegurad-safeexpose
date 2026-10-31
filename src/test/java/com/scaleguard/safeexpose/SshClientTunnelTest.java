package com.scaleguard.safeexpose;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The tunnel against an embedded SSH server on loopback: it works, and every way it can end is reported correctly. */
public class SshClientTunnelTest {

    private SshServer server;
    private Path hostKey;
    private ServerSocket localApp;
    private Thread appThread;
    private int sshPort;

    private SshServer newServer(int port) throws Exception {
        SshServer s = SshServer.setUpDefaultServer();
        s.setPort(port);
        s.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey));
        s.setPasswordAuthenticator((user, pass, session) -> user.equals("tunnel") && pass.equals("secret"));
        s.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        s.start();
        return s;
    }

    @Before
    public void start() throws Exception {
        hostKey = Files.createTempFile("hostkey", ".ser");
        Files.delete(hostKey);
        server = newServer(0);
        sshPort = server.getPort();
        localApp = new ServerSocket(0);
        appThread = new Thread(() -> {                         // the "app" being exposed: answers "hi <line>"
            while (!localApp.isClosed()) {
                try (Socket c = localApp.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    PrintWriter out = new PrintWriter(c.getOutputStream(), true);
                    out.println("hi " + in.readLine());
                } catch (Exception ignored) {
                    // closed
                }
            }
        });
        appThread.setDaemon(true);
        appThread.start();
    }

    @After
    public void stop() throws Exception {
        server.stop(true);
        localApp.close();
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private SshClientTunnel tunnel(String password, int port, int remotePort) {
        SshClientTunnel t = new SshClientTunnel();
        t.setSshPort(port);
        t.setConnectSeconds(5);
        t.setAuthSeconds(5);
        t.setHeartbeatSeconds(1);
        return t;
    }

    private static boolean waitFor(java.util.function.BooleanSupplier c) throws Exception {
        for (int i = 0; i < 100 && !c.getAsBoolean(); i++) Thread.sleep(50);
        return c.getAsBoolean();
    }

    private SshClientTunnel.PortForard forward(String password, int remotePort) {
        return new SshClientTunnel.PortForard("127.0.0.1", "tunnel", password, remotePort, localApp.getLocalPort());
    }

    @Test
    public void trafficFlowsAndALostLinkIsReportedAsEstablishedThenLost() throws Exception {
        int remote = freePort();
        SshClientTunnel t = tunnel("secret", sshPort, remote);
        Thread th = t.forwardPort(forward("secret", remote));
        assertTrue(waitFor(t::isEstablished));
        try (Socket s = new Socket("127.0.0.1", remote)) {
            s.setSoTimeout(5000);
            new PrintWriter(s.getOutputStream(), true).println("there");
            assertEquals("hi there", new BufferedReader(new InputStreamReader(s.getInputStream())).readLine());
        }
        server.stop(true);                                      // the network "drops"
        th.join(15_000);
        assertFalse("the tunnel thread ends instead of hanging", th.isAlive());
        assertTrue(t.isEstablished());
        assertFalse("a lost link is not an auth failure", t.isAuthRejected());
    }

    @Test
    public void aNewConnectionAfterTheServerIsBackWorksWithTheSameCredentials() throws Exception {
        int remote = freePort();
        SshClientTunnel first = tunnel("secret", sshPort, remote);
        Thread th = first.forwardPort(forward("secret", remote));
        assertTrue(waitFor(first::isEstablished));
        server.stop(true);
        th.join(15_000);
        server = newServer(sshPort);                            // back on the same port
        SshClientTunnel second = tunnel("secret", sshPort, remote);
        second.forwardPort(forward("secret", remote));
        assertTrue("reconnects without creating anything new on the server", waitFor(second::isEstablished));
        second.stop();
    }

    @Test
    public void aRefusedLoginIsToldApartFromANetworkFailure() throws Exception {
        SshClientTunnel t = tunnel("wrong", sshPort, 0);
        t.forwardPort(forward("wrong", freePort())).join(15_000);
        assertTrue(t.isAuthRejected());
        assertFalse(t.isEstablished());
    }

    @Test
    public void anUnreachableServerIsNotMistakenForARefusedLogin() throws Exception {
        SshClientTunnel t = tunnel("secret", freePort(), 0);      // nothing listens there
        t.forwardPort(forward("secret", freePort())).join(15_000);
        assertFalse(t.isAuthRejected());
        assertFalse(t.isEstablished());
        assertNotNull(t.getError());
    }

    @Test
    public void stopClosesATunnelThatIsUp() throws Exception {
        int remote = freePort();
        SshClientTunnel t = tunnel("secret", sshPort, remote);
        Thread th = t.forwardPort(forward("secret", remote));
        assertTrue(waitFor(t::isEstablished));
        t.stop();
        th.join(10_000);
        assertFalse(th.isAlive());
    }
}
