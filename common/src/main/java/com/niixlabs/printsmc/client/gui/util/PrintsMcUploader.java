package com.niixlabs.printsmc.client.gui.util;

import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrintsMcUploader {
    private static final String UPLOAD_URL = "https://printsmc.com/api/upload";
    private static final String USER_AGENT = "PrintsMC/1.2.0 (Java)";
    private static final int MAX_REQUEST_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500;
    private static final int REQUEST_TIMEOUT_MS = 15000;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
            .build();

    private static final ConcurrentHashMap<String, String> UPLOAD_CACHE = new ConcurrentHashMap<>();

    private static volatile long lastUploadFinishedAt = 0L;
    private static volatile boolean uploadInProgress = false;

    public enum RejectReason { NONE, COOLDOWN, BUSY }

    public static String getCachedUrl(File file) {
        return UPLOAD_CACHE.get(file.getAbsolutePath());
    }

    public static RejectReason canUpload() {
        if (uploadInProgress) {
            return RejectReason.BUSY;
        }
        long cooldownMillis = Math.max(0, ModConfig.screenshotUploadCooldownSeconds) * 1000L;
        if (System.currentTimeMillis() - lastUploadFinishedAt < cooldownMillis) {
            return RejectReason.COOLDOWN;
        }
        return RejectReason.NONE;
    }

    public static void uploadAsync(File file, String duration, BiConsumer<String, String> onResult) {
        String cached = UPLOAD_CACHE.get(file.getAbsolutePath());
        if (cached != null) {
            onResult.accept(cached, null);
            return;
        }

        RejectReason reason = canUpload();
        if (reason != RejectReason.NONE) {
            onResult.accept(null, reason.name());
            return;
        }

        uploadInProgress = true;

        CompletableFuture
                .supplyAsync(() -> doUpload(file, duration))
                .whenComplete((url, throwable) -> {
                    uploadInProgress = false;
                    lastUploadFinishedAt = System.currentTimeMillis();

                    Minecraft.getInstance().execute(() -> {
                        if (throwable != null) {
                            Throwable cause = throwable.getCause();
                            onResult.accept(null, cause != null ? cause.getMessage() : throwable.getMessage());
                        } else {
                            UPLOAD_CACHE.put(file.getAbsolutePath(), url);
                            onResult.accept(url, null);
                        }
                    });
                });
    }

    private static String doUpload(File file, String duration) {
        try {
            byte[] imageBytes = Files.readAllBytes(file.toPath());
            String boundary = "LucidBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipartBody(boundary, file.getName(), imageBytes, duration);

            for (int attempt = 0; attempt <= MAX_REQUEST_RETRIES; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(UPLOAD_URL))
                            .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                            .header("User-Agent", USER_AGENT)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();

                    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    String responseBody = response.body() == null ? "" : response.body().trim();

                    if (status == 408 || status == 425 || status == 429 || status >= 500) {
                        if (attempt < MAX_REQUEST_RETRIES) {
                            Thread.sleep((long) (RETRY_DELAY_MS * Math.pow(2, attempt)));
                            continue;
                        }
                    }

                    if (status / 100 != 2) {
                        throw new RuntimeException("HTTP " + status + " - " + (responseBody.isEmpty() ? "Unknown Error" : responseBody));
                    }

                    Matcher matcher = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(responseBody);
                    if (matcher.find()) {
                        return matcher.group(1).replace("\\/", "/");
                    } else {
                        throw new RuntimeException("Failed to parse PrintsMC response.");
                    }

                } catch (HttpTimeoutException | java.net.ConnectException e) {
                    if (attempt < MAX_REQUEST_RETRIES) {
                        Thread.sleep((long) (RETRY_DELAY_MS * Math.pow(2, attempt)));
                        continue;
                    }
                    throw new RuntimeException("Connection to printsmc.com failed.", e);
                }
            }
            throw new RuntimeException("Upload failed after maximum retries.");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] imageBytes, String duration) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String twoHyphens = "--";

        // Duration field
        out.write((twoHyphens + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"duration\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write((duration + crlf).getBytes(StandardCharsets.UTF_8));

        // File field
        out.write((twoHyphens + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: image/webp" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));

        out.write((twoHyphens + boundary + twoHyphens + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}