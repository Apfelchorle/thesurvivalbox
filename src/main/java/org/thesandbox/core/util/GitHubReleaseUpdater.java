package org.thesandbox.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;

public class GitHubReleaseUpdater {

    /**
     * Checks the latest release for {@code target.repo}. If its tag differs
     * from {@code currentTag}, downloads the matching asset into
     * {@code updateFolder} (Bukkit's /plugins/update folder).
     */
    public Result checkAndUpdate(UpdateTarget target, String currentTag, File updateFolder) throws Exception {
        String apiUrl = "https://api.github.com/repos/" + target.repo() + "/releases/latest";
        HttpURLConnection metaConn = openConnection(apiUrl, target.githubToken());

        String json = readBody(metaConn, target);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (!root.has("tag_name")) {
            String apiMessage = root.has("message") ? root.get("message").getAsString() : json;
            throw new IllegalStateException("GitHub API error for " + target.repo() + ": " + apiMessage);
        }

        String latestTag = root.get("tag_name").getAsString();

        if (latestTag.equals(currentTag)) {
            return new Result(false, "[" + target.id() + "] Already up to date (" + latestTag + ")", latestTag);
        }

        long assetId = extractAssetId(root, target);
        String assetUrl = "https://api.github.com/repos/" + target.repo() + "/releases/assets/" + assetId;
        downloadAsset(assetUrl, target, updateFolder);

        return new Result(true, "[" + target.id() + "] Updated to " + latestTag, latestTag);
    }

    private long extractAssetId(JsonObject root, UpdateTarget target) {
        JsonArray assets = root.getAsJsonArray("assets");
        if (assets != null) {
            for (var element : assets) {
                JsonObject asset = element.getAsJsonObject();
                if (asset.has("name") && asset.get("name").getAsString().equals(target.jarName())) {
                    return asset.get("id").getAsLong();
                }
            }
        }
        throw new IllegalStateException(
                "Could not find " + target.jarName() + " in the latest release assets for " + target.repo());
    }

    private void downloadAsset(String assetUrl, UpdateTarget target, File updateFolder) throws Exception {
        HttpURLConnection assetConn = openConnection(assetUrl, target.githubToken());
        assetConn.setRequestProperty("Accept", "application/octet-stream");
        assetConn.setInstanceFollowRedirects(false); // handled manually below

        int status = assetConn.getResponseCode();

        if (isRedirect(status)) {
            String redirectUrl = assetConn.getHeaderField("Location");
            if (redirectUrl == null) {
                throw new IOException("Redirect response with no Location header for " + target.repo());
            }

            // Deliberately open a FRESH connection with no Authorization
            // header - the signed S3/CDN URL already carries its own auth
            // in the query string, and forwarding a GitHub token here makes
            // S3 reject the request.
            HttpURLConnection redirected = (HttpURLConnection) new URL(redirectUrl).openConnection();
            redirected.setConnectTimeout(10_000);
            redirected.setReadTimeout(30_000);

            ensureOk(redirected, target);
            saveToUpdateFolder(redirected, target, updateFolder);
            return;
        }

        // Unauthenticated requests to public-repo assets sometimes stream
        // directly without a redirect - handle that case too.
        ensureOk(assetConn, target);
        saveToUpdateFolder(assetConn, target, updateFolder);
    }

    private void saveToUpdateFolder(HttpURLConnection conn, UpdateTarget target, File updateFolder) throws IOException {
        if (!updateFolder.exists()) updateFolder.mkdirs();
        File targetJar = new File(updateFolder, target.jarName());
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, targetJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == 307
                || status == 308;
    }

    private HttpURLConnection openConnection(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "TheSandboxCore-Updater");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        return conn;
    }

    private void ensureOk(HttpURLConnection conn, UpdateTarget target) throws IOException {
        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " downloading asset for " + target.repo()
                    + ": " + readErrorBody(conn));
        }
    }

    private String readBody(HttpURLConnection conn, UpdateTarget target) throws IOException {
        int status = conn.getResponseCode();
        InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            throw new IOException("HTTP " + status + " for " + target.repo() + " with no response body");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String body = reader.lines().collect(Collectors.joining());
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + target.repo() + ": " + body);
            }
            return body;
        }
    }

    private String readErrorBody(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err == null) return "(no error body)";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining());
            }
        } catch (IOException e) {
            return "(failed to read error body: " + e.getMessage() + ")";
        }
    }

    public static class Result {
        public final boolean updated;
        public final String message;
        public final String newTag;

        Result(boolean updated, String message, String newTag) {
            this.updated = updated;
            this.message = message;
            this.newTag = newTag;
        }
    }
}