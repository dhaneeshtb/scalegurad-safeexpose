package com.scaleguard.safeexpose;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SshClientTunnel {

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private boolean isDisconnected=false;


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

    public Thread forwardPort(PortForard portForard){
        Thread t= new Thread(()->{
            try (SshClient client = SshClient.setUpDefaultClient()) {
                client.start();

                client.setForwardingFilter(new MyForwardingFilter()); // Set your filter implementation
                try (ClientSession session = client.connect(portForard.getUsername(), portForard.getRemoteHost(), sshPort)
                        .verify(connectSeconds, TimeUnit.SECONDS).getSession()) {
                    session.addPasswordIdentity(portForard.getPassword());
                    session.auth().verify(authSeconds, TimeUnit.SECONDS);




                    // Enable remote port forwarding
                    SshdSocketAddress remoteAddress = new SshdSocketAddress("0.0.0.0", portForard.getRemotePort());
                    SshdSocketAddress localAddress = new SshdSocketAddress(portForard.getLocalHost(), portForard.getLocalPort());
                    PortForwardingTracker tracker = session.createRemotePortForwardingTracker(remoteAddress, localAddress);

                    tracker.getSession();
                    tracker.getSession().addSessionListener(new SessionListener() {
                        @Override
                        public void sessionDisconnect(Session session, int reason, String msg, String language, boolean initiator) {
                            System.out.println("Session Disconnected :" + portForard.getFqdn());
                            if(!isDisconnected) {
                                isDisconnected = true;
                                condition.signalAll(); // Notify consumer
                            }

                        }
                    });
                    System.out.println("✅ Tunnel established: localhost:" + portForard.getLocalPort() + " -> "+portForard.getRemoteHost()+":" + portForard.getRemotePort());
                    System.out.println("✅ Public Accessible FQDN :" + portForard.getFqdn());

                    session.addSessionListener(new SessionListener() {
                        @Override
                        public void sessionCreated(Session session) {
                            System.out.println("Session created: " + session);
                        }

                        @Override
                        public void sessionEvent(Session session, Event event) {
                            System.out.println("Session event: " + event);
                        }

                        @Override
                        public void sessionClosed(Session session) {
                            System.out.println("Session closed: " + session);
                            if(!isDisconnected) {
                                lock.lock();
                                try {
                                    isDisconnected = true;
                                    condition.signalAll(); // Notify consumer
                                }finally {
                                    lock.unlock();
                                }

                            }

                        }
                    });
                    lock.lock();
                    try {
                        // Keep the session open
                        condition.await();
                    }catch (Exception e){
                        e.printStackTrace();
                    }finally {
                        lock.unlock();
                    }


                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("✅ Tunnel removed: localhost:" + portForard.getLocalPort() + " -> remote:" + portForard.getRemotePort());

            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
