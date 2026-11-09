package com.niixlabs.printsmc.client.gui.util;

import com.niixlabs.printsmc.config.ModConfig;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrintsMcUploader {
    private static final String UPLOAD_URL = "https://printsmc.com/api/upload";
    private static final String USER_AGENT = "PrintsMC/1.2.0 (Java)";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 500L;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private static final Map<String, String> UPLOAD_CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean UPLOAD_IN_PROGRESS = new AtomicBoolean(false);
    private static volatile long lastUploadFinishedAt;

    public enum RejectReason { NONE, COOLDOWN, BUSY }

    private PrintsMcUploader() {}

    public static String getCachedUrl(File file) {
        return UPLOAD_CACHE.get(file.getAbsolutePath());
    }

    public static RejectReason canUpload() {
        if (UPLOAD_IN_PROGRESS.get()) {
            return RejectReason.BUSY;
        }
        long cooldownMillis = Math.max(0, ModConfig.screenshotUploadCooldownSeconds) * 1000L;
        if (System.currentTimeMillis() - lastUploadFinishedAt < cooldownMillis) {
            return RejectReason.COOLDOWN;
        }
        return RejectReason.NONE;
    }

    public static void uploadAsync(File file, String duration, BiConsumer<String, String> onResult) {
        String cached = getCachedUrl(file);
        if (cached != null) {
            onResult.accept(cached, null);
            return;
        }

        RejectReason reason = canUpload();
        if (reason != RejectReason.NONE) {
            onResult.accept(null, reason.name());
            return;
        }

        if (!UPLOAD_IN_PROGRESS.compareAndSet(false, true)) {
            onResult.accept(null, RejectReason.BUSY.name());
            return;
        }

        CompletableFuture.supplyAsync(() -> executeUpload(file, duration))
                .whenComplete((url, throwable) -> {
                    UPLOAD_IN_PROGRESS.set(false);
                    lastUploadFinishedAt = System.currentTimeMillis();

                    Minecraft.getInstance().execute(() -> {
                        if (throwable != null) {
                            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                            onResult.accept(null, cause.getMessage());
                        } else {
                            UPLOAD_CACHE.put(file.getAbsolutePath(), url);
                            onResult.accept(url, null);
                        }
                    });
                });
    }

    private static String executeUpload(File file, String duration) {
        try {
            byte[] imageBytes = Files.readAllBytes(file.toPath());
            String boundary = "PrintsMCBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipartBody(boundary, file.getName(), imageBytes, duration);

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(UPLOAD_URL))
                            .timeout(TIMEOUT)
                            .header("User-Agent", USER_AGENT)
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();

                    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    String responseBody = response.body() == null ? "" : response.body().trim();

                    if (isRetryableStatus(status) && attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS * (1L << attempt));
                        continue;
                    }

                    if (status < 200 || status >= 300) {
                        throw new RuntimeException("HTTP " + status + " - " + (responseBody.isEmpty() ? "Unknown Error" : responseBody));
                    }

                    Matcher matcher = URL_PATTERN.matcher(responseBody);
                    if (matcher.find()) {
                        return matcher.group(1).replace("\\/", "/");
                    }
                    throw new RuntimeException("Failed to parse PrintsMC response.");
                } catch (HttpTimeoutException | ConnectException e) {
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(RETRY_DELAY_MS * (1L << attempt));
                        continue;
                    }
                    throw new RuntimeException("Connection to printsmc.com failed.", e);
                }
            }
            throw new RuntimeException("Upload failed after maximum retries.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upload interrupted.", e);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes, String duration) throws IOException {
        String mimeType = fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/webp";
        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 512);

        writeUtf8(out, "--" + boundary + "\r\n");
        writeUtf8(out, "Content-Disposition: form-data; name=\"duration\"\r\n\r\n");
        writeUtf8(out, duration + "\r\n");

        writeUtf8(out, "--" + boundary + "\r\n");
        writeUtf8(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        writeUtf8(out, "Content-Type: " + mimeType + "\r\n\r\n");
        out.write(fileBytes);
        writeUtf8(out, "\r\n--" + boundary + "--\r\n");

        return out.toByteArray();
    }

    private static void writeUtf8(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }
}