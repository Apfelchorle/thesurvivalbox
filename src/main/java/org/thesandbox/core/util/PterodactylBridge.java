package org.thesandbox.core.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class PterodactylBridge {

    public static void sendConsoleCommand(String panelUrl, String apiKey, String serverId, String command) throws IOException {
        String endpoint = stripTrailingSlash(panelUrl) + "/api/client/servers/" + serverId + "/command";
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/vnd.pterodactyl.v1+json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);

        String body = "{\"command\":\"" + escapeJson(command) + "\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 204) {
            throw new IOException("Pterodactyl command failed for server " + serverId + ": HTTP " + status);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}