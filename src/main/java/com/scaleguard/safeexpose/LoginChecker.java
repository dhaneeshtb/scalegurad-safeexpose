package com.scaleguard.safeexpose;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LoginChecker {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** The server could not be reached (no network, DNS, timeout, reset): worth retrying. */
    public static class NetworkException extends RuntimeException {
        public NetworkException(String message) {
            super(message);
        }
    }

    /** The server answered with an error status. 5xx is worth retrying; 4xx (bad request, forbidden) is not. */
    public static class HttpFailure extends RuntimeException {
        public final int code;

        public HttpFailure(int code, String message) {
            super(message);
            this.code = code;
        }

        public boolean retryable() {
            return code >= 500 || code == 408 || code == 429;
        }
    }

    private static String describe(IOException e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    /** @return the token, or null when the server refused the username or password. */
    public static String checkLogin(String hostUrl, String username, String password) {
        JSONObject json = new JSONObject();
        json.put("username", username);
        json.put("password", password);
        Request request = new Request.Builder().url(hostUrl + "/signin?scaleguard=true").post(RequestBody.create(json.toString(), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) return null;
            if (!response.isSuccessful()) throw new HttpFailure(response.code(), "login failed: HTTP " + response.code());
            return new JSONObject(response.body().string()).getString("token");
        } catch (IOException e) {
            throw new NetworkException(describe(e));
        }
    }

    public static JSONObject createTunnel(String hostUrl, String token, String name, int port) {
        return createTunnel(hostUrl, token, name, port, null);
    }

    /** @param domain publish as name.domain (blank = the server's own choice) */
    public static JSONObject createTunnel(String hostUrl, String token, String name, int port, String domain) {
        JSONObject json = new JSONObject();
        if (domain != null && !domain.isBlank()) json.put("baseDNS", domain.trim());
        json.put("host", hostUrl);
        json.put("name", name);
        json.put("port", port);
        Request request = new Request.Builder().addHeader("Authorization", token)
                .url(hostUrl + "/tunnel?scaleguard=true").post(RequestBody.create(json.toString(), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new HttpFailure(response.code(), "the server did not create the tunnel (HTTP " + response.code() + ") " + body);
            return new JSONObject(body);
        } catch (IOException e) {
            throw new NetworkException(describe(e));
        }
    }

    /** Best effort: a missing tunnel or a server error is not a problem here. */
    public static JSONObject removeApp(String hostUrl, String token, String name) {
        Request request = new Request.Builder().addHeader("Authorization", token)
                .url(hostUrl + "/tunnel/" + name + "?scaleguard=true").delete().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            return new JSONObject(response.body().string());
        } catch (IOException e) {
            throw new NetworkException(describe(e));
        }
    }
}
