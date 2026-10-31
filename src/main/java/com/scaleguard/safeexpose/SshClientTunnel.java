package com.scaleguard.safeexpose;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import org.apache.sshd.core.CoreModuleProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SshClientTunnel {

    private final CountDownLatch ended = new CountDownLatch(1);

    private volatile boolean isDisconnected = false;
    private volatile boolean established = false;
    private volatile boolean authRejected = false;
    private volatile String error;
    private int heartbeatSeconds = 15;


    /** The address the server binds the forwarded port to. */
    private static final String REMOTE_BIND = System.getProperty("safeexpose.remoteBind", "0.0.0.0");

    private int sshPort;
    private int connectSeconds = 30;
    private int authSeconds = 10;

    /** The SSH port the server reported for tunnels (or the one configured). */
    public void setSshPort(int port) {
        this.sshPort = port;
    }

    public void setConnectSeconds(int s) {
        this.connectSeconds = s;
    }

    public void setAuthSeconds(int s) {
        this.authSeconds = s;
    }

    /** How often the connection is probed; a server that stops answering ends the session, so a dead link is noticed. */
    public void setHeartbeatSeconds(int s) {
        this.heartbeatSeconds = s;
    }

    public static class PortForard{
        public String getFqdn() {
            return fqdn;
        }

        public void setFqdn(String fqdn) {
            this.fqdn = fqdn;
        }

        private String fqdn;
        private String username;
        private String password;
        private int remotePort;
        private String remoteHost;
        private int localPort;
        private String localHost = "localhost";

        /** The machine the exposed app runs on, as seen from this one (default: this machine). */
        public String getLocalHost() {
            return localHost;
        }

        public void setLocalHost(String localHost) {
            this.localHost = localHost;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getRemotePort() {
            return remotePort;
        }

        public void setRemotePort(int remotePort) {
            this.remotePort = remotePort;
        }

        public String getRemoteHost() {
            return remoteHost;
        }

        public void setRemoteHost(String remoteHost) {
            this.remoteHost = remoteHost;
        }

        public int getLocalPort() {
            return localPort;
        }

        public void setLocalPort(int localPort) {
            this.localPort = localPort;
        }

        PortForard(String remoteHost,String username,String password,int remotePort,int localPort){

            this.remoteHost=remoteHost;
            this.remotePort=remotePort;
            this.localPort=localPort;

            this.username=username;
            this.password=password;

        }
    }
    public boolean isDisconnected() {
        return isDisconnected;
    }

    public void setDisconnected(boolean disconnected) {
        isDisconnected = disconnected;
    }

    /** True once the port forward was set up (even if the link was lost later). */
    public boolean isEstablished() {
        return established;
    }

    /** True if the server answered but refused the tunnel's login (it no longer knows this tunnel, e.g. after a restart). */
    public boolean isAuthRejected() {
        return authRejected;
    }

    /** Why the tunnel could not start or ended, or null. */
    public String getError() {
        return error;
    }

    /** Ends the tunnel (the session is closed by the thread that owns it). */
    public void stop() {
        ended.countDown();
    }

    public Thread forwardPort(PortForard pf) {
        Thread t = new Thread(() -> {
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.setForwardingFilter(new MyForwardingFilter());
                // probe the link: a connection that silently died (sleep, Wi-Fi change) is detected and the session ended
                client.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.RESERVED, Duration.ofSeconds(heartbeatSeconds));
                CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, Duration.ofSeconds(heartbeatSeconds));
                client.start();
                try (ClientSession session = client.connect(pf.getUsername(), pf.getRemoteHost(), sshPort)
                        .verify(connectSeconds, TimeUnit.SECONDS).getSession()) {
                    session.addPasswordIdentity(pf.getPassword());
                    try {
                        session.auth().verify(authSeconds, TimeUnit.SECONDS);
                    } catch (IOException e) {
                        authRejected = session.isOpen();      // answered but refused, as opposed to the link dying
                        throw e;
                    }
                    SshdSocketAddress remote = new SshdSocketAddress(REMOTE_BIND, pf.getRemotePort());
                    SshdSocketAddress local = new SshdSocketAddress(pf.getLocalHost(), pf.getLocalPort());
                    PortForwardingTracker tracker = session.createRemotePortForwardingTracker(remote, local);
                    session.addSessionListener(new SessionListener() {
                        @Override
                        public void sessionClosed(Session closed) {
                            ended.countDown();
                        }

                        @Override
                        public void sessionDisconnect(Session closed, int reason, String msg, String language, boolean initiator) {
                            ended.countDown();
                        }
                    });
                    established = true;
                    System.out.println("Tunnel up: " + pf.getLocalHost() + ":" + pf.getLocalPort() + " -> " + pf.getRemoteHost() + ":" + pf.getRemotePort());
                    if (!session.isOpen()) ended.countDown();
                    ended.await();                                // until the session closes or stop() is called
                    tracker.close();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            } finally {
                isDisconnected = true;
                ended.countDown();
            }
        }, "safeexpose-ssh");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
