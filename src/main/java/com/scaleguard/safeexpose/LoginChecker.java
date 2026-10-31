package com.scaleguard.safeexpose;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;

public class LoginChecker {
    private static final OkHttpClient client = new OkHttpClient();

    public static String checkLogin(String hostUrl, String username, String password) {
        // Create JSON body
        JSONObject json = new JSONObject();
        json.put("username", username);
        json.put("password", password);

        // Build request body
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        // Create HTTP request
        Request request = new Request.Builder()
                .url(hostUrl+"/signin?scaleguard=true")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Login failed: " + response.code());
                return null;
            }else{
                JSONObject jsonObject=new JSONObject(response.body().string());
                return jsonObject.getString("token");
            }

        } catch (IOException e) {
            System.err.println("Error during login: " + e.getMessage());
        }
        return null;

    }

    public static JSONObject createTunnel(String hostUrl, String token,String name,int port) {
        return createTunnel(hostUrl, token, name, port, null);
    }

    /** @param domain publish as name.domain (blank = the server's own choice) */
    public static JSONObject createTunnel(String hostUrl, String token,String name,int port,String domain) {
        // Create JSON body
        JSONObject json = new JSONObject();
        if (domain != null && !domain.isBlank()) json.put("baseDNS", domain.trim());
        json.put("host", hostUrl);
        json.put("name", name);
        json.put("port", port);

        // Build request body
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));

        // Create HTTP request
        Request request = new Request.Builder()
                .addHeader("Authorization",token)
                .url(hostUrl+"/tunnel?scaleguard=true")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Tunnel Creation Failed : " + response.code());
                System.out.println("Failure Details : " + response.body().string());

                return null;
            }else{
                JSONObject jsonObject=new JSONObject(response.body().string());
                return jsonObject;
            }

        } catch (IOException e) {
            System.err.println("Error during login: " + e.getMessage());
        }
        return null;

    }

    public static JSONObject removeApp(String hostUrl, String token,String name) {
        // Create HTTP request
        Request request = new Request.Builder()
                .addHeader("Authorization",token)
                .url(hostUrl+"/tunnel/"+name+"?scaleguard=true")
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Login failed: " + response.code());
                return null;
            }else{
                JSONObject jsonObject=new JSONObject(response.body().string());
                return jsonObject;
            }
        } catch (IOException e) {
            System.err.println("Error during login: " + e.getMessage());
        }
        return null;

    }
}
