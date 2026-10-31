package com.scaleguard.safeexpose;

import org.json.JSONObject;

import java.io.Console;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * SafeExpose: publish a local app through a ScaleGuard server.
 *
 * <pre>
 *   safeexpose 8080                 expose localhost:8080 (name defaults to app-8080)
 *   safeexpose 8080 my-app          expose it as my-app.&lt;your-domain&gt;
 *   safeexpose configure            save server, user and password (asked on first use)
 *   safeexpose delete my-app        remove a tunnel
 * </pre>
 * Settings can also come from SAFEEXPOSE_SERVER, SAFEEXPOSE_USER and SAFEEXPOSE_PASSWORD.
 */
public class TunnelManager {
    private static final Path CONFIG = Paths.get(System.getProperty("user.home"), ".tunnel_config");

    static final String USAGE = String.join("\n",
            "SafeExpose: expose a local app through ScaleGuard",
            "",
            "  safeexpose <port> [name]       expose localhost:<port> (default name: app-<port>)",
            "  safeexpose <host:port> [name]  expose an app on another machine, e.g. 192.168.1.20:3000",
            "      --domain example.com       publish as <name>.example.com (default: the server's tunnel domain)",
            "  safeexpose configure [server user password]",
            "  safeexpose show                show the saved settings (password hidden)",
            "  safeexpose delete <name>       remove a tunnel",
            "",
            "First run asks for your ScaleGuard server, user and password. Env overrides:",
            "  SAFEEXPOSE_SERVER, SAFEEXPOSE_USER, SAFEEXPOSE_PASSWORD, SAFEEXPOSE_DOMAIN",
            "  SAFEEXPOSE_SSH_PORT (only if the server does not report it), SAFEEXPOSE_RECONNECT_SECONDS,",
            "  SAFEEXPOSE_CONNECT_SECONDS, SAFEEXPOSE_AUTH_SECONDS   (or the same keys in ~/.tunnel_config)",
            "A default domain can be saved with:  safeexpose domain example.com");

    /** Removes "--domain x" / "-d x" / "--domain=x" from the arguments; the value is returned in index 0 of the second array. */
    static String[][] splitDomain(String[] args) {
        java.util.List<String> rest = new java.util.ArrayList<>();
        String domain = null;
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("--domain") || args[i].equals("-d")) && i + 1 < args.length) domain = args[++i];
            else if (args[i].startsWith("--domain=")) domain = args[i].substring(9);
            else rest.add(args[i]);
        }
        return new String[][]{rest.toArray(new String[0]), {domain}};
    }

    public static void main(String[] argv) {
        String[][] split = splitDomain(argv);
        String[] args = split[0];
        String domainArg = split[1][0];
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help") || args[0].equals("help")) {
            System.out.println(USAGE);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "configure":
                if (args.length == 4) save(args[1], args[2], args[3]);
                else if (args.length == 1) interactiveConfigure();
                else System.out.println("Usage: safeexpose configure [server user password]");
                break;
            case "show":
                show();
                break;
            case "domain":
                if (args.length == 2) saveDomain(args[1]);
                else System.out.println("Usage: safeexpose domain <example.com>");
                break;
            case "delete":
                if (args.length == 2) deleteTunnel(args[1]);
                else System.out.println("Usage: safeexpose delete <name>");
                break;
            default: {
                // "safeexpose 8080 [name]", and the older "start|expose <name> <port>"
                String[] np = parseExpose(args);
                if (np == null) {
                    System.out.println(USAGE);
                    return;
                }
                expose(np[0], np[1], np[2], domainArg);
            }
        }
    }

    /** The port or "host:port" being exposed -> {host or null, port}; null if it is neither. */
    static String[] splitTarget(String t) {
        if (isPort(t)) return new String[]{null, t};
        int c = t.lastIndexOf(':');
        if (c > 0 && isPort(t.substring(c + 1)) && t.substring(0, c).matches("[A-Za-z0-9.\\-\\[\\]:]+")) return new String[]{t.substring(0, c), t.substring(c + 1)};
        return null;
    }

    /** @return {name, port, host-or-null} or null when the arguments are not an expose request. */
    static String[] parseExpose(String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("expose"))) {
            if (args.length == 3 && isPort(args[2])) return new String[]{args[1], args[2], null};   // older form: start <name> <port>
            String[] t = args.length >= 2 ? splitTarget(args[1]) : null;
            return t == null ? null : new String[]{args.length > 2 ? args[2] : "app-" + t[1], t[1], t[0]};
        }
        String[] t = args.length >= 1 && args.length <= 2 ? splitTarget(args[0]) : null;
        return t == null ? null : new String[]{args.length == 2 ? args[1] : "app-" + t[1], t[1], t[0]};
    }

    /** A number from the saved settings or the environment (SAFEEXPOSE_<KEY>), else the default. */
    static int number(Map<String, String> c, String key, int dflt) {
        String v = System.getenv("SAFEEXPOSE_" + key.replaceAll("([A-Z])", "_$1").toUpperCase());
        if (v == null) v = c.get(key);
        try {
            return v == null ? dflt : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    static boolean isPort(String s) {
        try {
            int p = Integer.parseInt(s);
            return p > 0 && p < 65536;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** "scaleguard.example.com" -> "https://scaleguard.example.com"; trailing slashes dropped. */
    static String normalizeServer(String s) {
        String t = s.trim();
        if (!t.contains("://")) t = "https://" + t;
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    // ------------------------------------------------------------------ settings

    private static Map<String, String> settings() {
        Map<String, String> c = new HashMap<>();
        try {
            if (Files.exists(CONFIG)) {
                for (String line : Files.readAllLines(CONFIG)) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) c.put(kv[0].trim(), kv[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read " + CONFIG + ": " + e.getMessage());
        }
        if (System.getenv("SAFEEXPOSE_SERVER") != null) c.put("server", System.getenv("SAFEEXPOSE_SERVER"));
        if (System.getenv("SAFEEXPOSE_USER") != null) c.put("username", System.getenv("SAFEEXPOSE_USER"));
        if (System.getenv("SAFEEXPOSE_PASSWORD") != null) c.put("password", System.getenv("SAFEEXPOSE_PASSWORD"));
        if (System.getenv("SAFEEXPOSE_DOMAIN") != null) c.put("domain", System.getenv("SAFEEXPOSE_DOMAIN"));
        return c;
    }

    private static boolean complete(Map<String, String> c) {
        return c.get("server") != null && c.get("username") != null && c.get("password") != null;
    }

    /** Saved settings, asking for them (once) when this is the first run. Null if they cannot be had. */
    private static Map<String, String> settingsOrAsk() {
        Map<String, String> c = settings();
        if (complete(c)) return c;
        System.out.println("SafeExpose needs your ScaleGuard login (saved to " + CONFIG + ", readable only by you).");
        interactiveConfigure(c);
        c = settings();
        return complete(c) ? c : null;
    }

    private static void interactiveConfigure() {
        interactiveConfigure(settings());
    }

    /** Asks only for what is not known yet (a changed password: pass the saved settings without it). */
    private static void interactiveConfigure(Map<String, String> known) {
        Console console = System.console();
        Scanner in = console == null ? new Scanner(System.in) : null;
        String server = known.get("server"), user = known.get("username"), pass = known.get("password");
        for (int attempt = 0; attempt < 3; attempt++) {
            if (server == null) server = normalizeServer(ask(console, in, "ScaleGuard server (e.g. scaleguard.example.com): ", false));
            if (user == null) user = ask(console, in, "Username: ", false);
            if (pass == null) pass = ask(console, in, "Password: ", true);
            if (LoginChecker.checkLogin(server, user, pass) != null) {
                save(server, user, pass);
                return;
            }
            System.out.println("Login failed. Check the server address, username and password.");
            pass = null;                                   // ask the password again; keep the server and user
        }
    }

    private static String ask(Console console, Scanner in, String prompt, boolean secret) {
        System.out.print(prompt);
        System.out.flush();
        if (console != null) {
            return secret ? new String(console.readPassword()) : console.readLine();
        }
        return in.hasNextLine() ? in.nextLine() : "";
    }

    private static void save(String server, String user, String password) {
        try {
            String domain = fileSettings().get("domain");
            Files.write(CONFIG, (String.format("server=%s\nusername=%s\npassword=%s\n", normalizeServer(server), user, password)
                    + (domain == null ? "" : "domain=" + domain + "\n")).getBytes());
            try {
                Files.setPosixFilePermissions(CONFIG, PosixFilePermissions.fromString("rw-------"));   // it holds a password
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system
            }
            System.out.println("Saved to " + CONFIG);
        } catch (IOException e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }

    private static Map<String, String> fileSettings() {
        Map<String, String> c = new HashMap<>();
        try {
            if (Files.exists(CONFIG)) {
                for (String line : Files.readAllLines(CONFIG)) {
                    String[] kv = line.split("=", 2);
                    if (kv.length == 2) c.put(kv[0].trim(), kv[1].trim());
                }
            }
        } catch (IOException ignored) {
            // treated as empty
        }
        return c;
    }

    private static void saveDomain(String domain) {
        Map<String, String> c = fileSettings();
        if (!complete(c)) {
            System.out.println("Run 'safeexpose configure' first.");
            return;
        }
        c.put("domain", domain.trim());
        StringBuilder sb = new StringBuilder();
        c.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try {
            Files.write(CONFIG, sb.toString().getBytes());
            System.out.println("Tunnels will be published as <name>." + domain.trim());
        } catch (IOException e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }

    private static void show() {
        Map<String, String> c = settings();
        if (!complete(c)) {
            System.out.println("Not configured. Run 'safeexpose configure'.");
            return;
        }
        System.out.println("Server:   " + c.get("server"));
        System.out.println("Username: " + c.get("username"));
        System.out.println("Password: ********");
        System.out.println("Domain:   " + (c.get("domain") == null ? "(server default)" : c.get("domain")));
    }

    // ------------------------------------------------------------------ tunnels

    private static void expose(String name, String port, String localHost, String domainArg) {
        Map<String, String> c = settingsOrAsk();
        if (c == null) return;
        String server = normalizeServer(c.get("server"));
        String domain = domainArg != null ? domainArg : c.get("domain");
        // remove the tunnel when the program ends (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTunnel(name)));
        while (true) {
            String token = LoginChecker.checkLogin(server, c.get("username"), c.get("password"));
            if (token == null && System.getenv("SAFEEXPOSE_PASSWORD") == null) {
                // the saved password no longer works: ask for the new one and save it
                Map<String, String> known = new HashMap<>(c);
                known.remove("password");
                interactiveConfigure(known);
                c = settings();
                token = complete(c) ? LoginChecker.checkLogin(server, c.get("username"), c.get("password")) : null;
            }
            if (token == null) {
                System.out.println("Login failed. Run 'safeexpose configure' again.");
                return;
            }
            LoginChecker.removeApp(server, token, name);       // clear a stale tunnel with this name
            JSONObject json = LoginChecker.createTunnel(server, token, name, Integer.parseInt(port), domain);
            if (json == null) {
                System.out.println("The server did not create the tunnel (see the message above).");
                return;
            }
            try {
                SshClientTunnel tunnel = new SshClientTunnel();
                int sshPort = json.optInt("sshPort", number(c, "sshPort", 0));      // the server says where to connect
                if (sshPort <= 0) {
                    System.out.println("The server did not say which SSH port to use (an older server?). Set SAFEEXPOSE_SSH_PORT.");
                    return;
                }
                tunnel.setSshPort(sshPort);
                tunnel.setConnectSeconds(number(c, "connectSeconds", 30));
                tunnel.setAuthSeconds(number(c, "authSeconds", 10));
                SshClientTunnel.PortForard pf = new SshClientTunnel.PortForard(json.optString("sshHost", new URL(server).getHost()), json.getString("username"),
                        json.getString("password"), json.getInt("systemPort"), Integer.parseInt(port));
                pf.setFqdn(json.getString("fqdn"));
                if (localHost != null) pf.setLocalHost(localHost);
                System.out.println("\nExposing " + pf.getLocalHost() + ":" + port + " at  " + json.getString("fqdn") + "\n(Ctrl+C to stop)\n");
                tunnel.forwardPort(pf).join();
                if (!tunnel.isDisconnected()) return;
                int wait = number(c, "reconnectSeconds", 10);
                System.out.println("Disconnected. Reconnecting in " + wait + " seconds...");
                Thread.sleep(wait * 1000L);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                System.err.println("Error starting tunnel: " + e.getMessage());
                return;
            }
        }
    }

    private static void deleteTunnel(String name) {
        Map<String, String> c = settings();
        if (!complete(c)) {
            System.out.println("Not configured. Run 'safeexpose configure'.");
            return;
        }
        String server = normalizeServer(c.get("server"));
        String token = LoginChecker.checkLogin(server, c.get("username"), c.get("password"));
        if (token == null) return;
        LoginChecker.removeApp(server, token, name);
        System.out.println("Removed: " + name);
    }
}
