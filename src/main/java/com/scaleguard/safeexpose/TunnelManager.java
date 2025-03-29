package com.scaleguard.safeexpose;

import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class TunnelManager {
    private static final String CONFIG_FILE_PATH = System.getProperty("user.home") + "/.tunnel_config";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: <configure/start/delete> [options]");
            return;
        }



        switch (args[0].toLowerCase()) {
            case "configure":
                handleConfiguration(args);
                break;
            case "show":
                showConfiguration(args);
                break;
            case "expose":
            case "start":
                Runtime.getRuntime().addShutdownHook(new Thread(()->{
                    deleteTunnel(args[1]);
                }));
                deleteTunnel(args[1]);
                if (args.length == 3) startTunnel(args[1], args[2]);
                else System.out.println("Usage: java TunnelManager start <app-name> <port>");
                break;
            case "delete":
                if (args.length == 2) deleteTunnel(args[1]);
                else System.out.println("Usage: java TunnelManager delete <app-name>");
                break;
            default:
                System.out.println("Unknown command. Use 'configure', 'start', or 'delete'.");
        }
    }

    private static void handleConfiguration(String[] args) {
        if (args.length == 4) {
            configureTunnel(args[1], args[2], args[3]);
        } else if (args.length == 1) {
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("Enter Scaleguard Host URL: ");
                    String hostUrl = scanner.nextLine();
                    System.out.print("Enter Scaleguard Username: ");
                    String username = scanner.nextLine();
                    System.out.print("Enter Scaleguard Password: ");
                    String password = scanner.nextLine();

                    if (LoginChecker.checkLogin(hostUrl, username, password) != null) {
                        configureTunnel(hostUrl, username, password);
                        break;
                    } else {
                        System.out.println("Invalid credentials or host URL. Try again.");
                    }
                }
            }
        } else {
            System.out.println("Usage: java TunnelManager configure <server-host> <username> <password>");
        }
    }

    private static void showConfiguration(String[] args) {
        if (!Files.exists(Paths.get(CONFIG_FILE_PATH))) {
            System.out.println("Configuration not found. Run 'configure' first.");
            return;
        }
        try {
            Map<String, String> config = loadConfiguration();
            System.out.println("Scaleguard Host URL:"+config.get("server"));
            System.out.println("Scaleguard Username:"+config.get("username"));
            System.out.println("Scaleguard Password:"+config.get("password"));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void configureTunnel(String serverHost, String username, String password) {
        try {
            String configData = String.format("server=%s\nusername=%s\npassword=%s", serverHost, username, password);
            Files.write(Paths.get(CONFIG_FILE_PATH), configData.getBytes());
            System.out.println("Configuration saved successfully.");
        } catch (IOException e) {
            System.err.println("Error saving configuration: " + e.getMessage());
        }
    }

    private static Map<String, String> loadConfiguration() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE_PATH));
        Map<String, String> config = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) config.put(parts[0].trim(), parts[1].trim());
        }
        return config;
    }

    private static void startTunnel(String appName, String port) {
        if (!Files.exists(Paths.get(CONFIG_FILE_PATH))) {
            System.out.println("Configuration not found. Run 'configure' first.");
            return;
        }

        try {
            Map<String, String> config = loadConfiguration();
            String token = LoginChecker.checkLogin(config.get("server"), config.get("username"), config.get("password"));
            if (token == null) {
                System.out.println("Invalid configuration. Run 'configure' again.");
                return;
            }

            JSONObject json = LoginChecker.createTunnel(config.get("server"), token, appName, Integer.parseInt(port));
            System.out.println(json);

            URL hUrl = new URL(config.get("server"));
            SshClientTunnel sshClientTunnel = new SshClientTunnel();
            SshClientTunnel.PortForard pf = new SshClientTunnel.PortForard(
                    hUrl.getHost(), json.getString("username"), json.getString("password"), json.getInt("systemPort"), Integer.parseInt(port));

            pf.setFqdn(json.getString("fqdn"));
            Thread t = sshClientTunnel.forwardPort(pf);
            t.join();
            if(sshClientTunnel.isDisconnected()){
                System.out.println("Disconnected Tunnel.. Sleeping 10 seconds");
                Thread.sleep(10000);
                deleteTunnel(appName);
                startTunnel(appName,port);
            }
        } catch (Exception e) {
            System.err.println("Error starting tunnel: " + e.getMessage());
        }
    }

    private static void deleteTunnel(String appName) {
        if (!Files.exists(Paths.get(CONFIG_FILE_PATH))) {
            System.out.println("Configuration not found. Run 'configure' first.");
            return;
        }

        try {
            Map<String, String> config = loadConfiguration();
            String token = LoginChecker.checkLogin(config.get("server"), config.get("username"), config.get("password"));
            if (token == null) {
                System.out.println("Invalid configuration. Run 'configure' again.");
                return;
            }

            LoginChecker.removeApp(config.get("server"), token, appName);
            System.out.println("App removed successfully: " + appName);
        } catch (Exception e) {
            System.err.println("Error deleting tunnel: " + e.getMessage());
        }
    }
}