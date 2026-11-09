package com.niixlabs.printsmc.config;

import com.niixlabs.printsmc.Constants;
import net.minecraft.client.Minecraft;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {
    private static final String FILE_PATH = "config/prints-mc/printsmc.properties";
    private static final String HEADER = "# PrintsMC Configuration File\n# Colors use ARGB Hexadecimal format (e.g., 0xAARRGGBB)\n\n";

    private static volatile long lastKnownModified;
    private static Thread watcherThread;

    private ConfigManager() {}

    private static Path resolveConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FILE_PATH);
    }

    private static void syncModifiedTime(Path path) {
        try {
            lastKnownModified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
        }
    }

    public static void load(Class<?> configClass) {
        Path path = resolveConfigPath();
        if (!Files.exists(path)) {
            save(configClass);
            return;
        }

        try {
            Map<String, String> properties = readProperties(path);
            for (Field field : configClass.getDeclaredFields()) {
                ConfigOption option = field.getAnnotation(ConfigOption.class);
                if (option == null) {
                    continue;
                }

                String rawValue = properties.get(field.getName());
                if (rawValue != null) {
                    try {
                        applyFieldValue(field, option, rawValue);
                    } catch (Exception e) {
                        Constants.LOGGER.warn("Invalid config value '{}' for field '{}', keeping default", rawValue, field.getName());
                    }
                }
            }
            syncModifiedTime(path);
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to load configuration file", e);
        }
    }

    public static void save(Class<?> configClass) {
        Path path = resolveConfigPath();
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                for (Field field : configClass.getDeclaredFields()) {
                    ConfigSection section = field.getAnnotation(ConfigSection.class);
                    if (section != null) {
                        writer.write("\n# --- " + section.value() + " ---\n");
                    }

                    ConfigOption option = field.getAnnotation(ConfigOption.class);
                    if (option == null) {
                        continue;
                    }

                    if (!option.comment().isEmpty()) {
                        writer.write("# " + option.comment() + "\n");
                    }

                    Object value = field.get(null);
                    writer.write(field.getName() + "=" + formatValue(option, value) + "\n");
                }
            }
            syncModifiedTime(path);
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to save configuration file", e);
        }
    }

    public static void updateAndSave(Class<?> configClass, String fieldName, Object value) {
        Path path = resolveConfigPath();
        try {
            Field field = configClass.getDeclaredField(fieldName);
            field.set(null, value);

            if (!Files.exists(path)) {
                save(configClass);
                return;
            }

            ConfigOption option = field.getAnnotation(ConfigOption.class);
            String formattedValue = formatValue(option, value);

            rewritePropertyLine(path, fieldName, formattedValue);
            syncModifiedTime(path);
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to update config key '{}'", fieldName, e);
        }
    }

    private static Map<String, String> readProperties(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int sep = line.indexOf('=');
            if (sep > 0) {
                values.put(line.substring(0, sep).trim(), line.substring(sep + 1).trim());
            }
        }
        return values;
    }

    private static void applyFieldValue(Field field, ConfigOption option, String rawValue) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == int.class) {
            field.setInt(null, option.hex() ? parseHexColor(rawValue) : Integer.parseInt(rawValue));
        } else if (type == double.class) {
            field.setDouble(null, Double.parseDouble(rawValue));
        } else if (type == boolean.class) {
            field.setBoolean(null, Boolean.parseBoolean(rawValue));
        } else if (type == String.class) {
            field.set(null, rawValue);
        }
    }

    private static int parseHexColor(String rawValue) {
        String cleaned = rawValue.trim();
        if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }
        return Integer.parseUnsignedInt(cleaned, 16);
    }

    private static String formatValue(ConfigOption option, Object value) {
        if (option != null && option.hex() && value instanceof Integer intVal) {
            return String.format("0x%08X", intVal);
        }
        return String.valueOf(value);
    }

    private static void rewritePropertyLine(Path path, String targetKey, String newValue) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        boolean updated = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("#")) {
                continue;
            }
            int sep = line.indexOf('=');
            if (sep > 0 && line.substring(0, sep).trim().equals(targetKey)) {
                lines.set(i, targetKey + "=" + newValue);
                updated = true;
                break;
            }
        }

        if (!updated) {
            lines.add(targetKey + "=" + newValue);
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    public static synchronized void startWatcher(Class<?> configClass) {
        if (watcherThread != null && watcherThread.isAlive()) {
            return;
        }

        watcherThread = new Thread(() -> runWatchLoop(configClass), "PrintsMC-ConfigWatcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private static void runWatchLoop(Class<?> configClass) {
        Path path = resolveConfigPath();
        Path dir = path.getParent();
        if (dir == null) {
            return;
        }

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            Files.createDirectories(dir);
            dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watcher.take();
                boolean modified = key.pollEvents().stream()
                        .map(event -> (Path) event.context())
                        .anyMatch(changed -> changed != null && changed.getFileName().equals(path.getFileName()));

                if (modified) {
                    Thread.sleep(150L);
                    if (Files.exists(path) && Files.getLastModifiedTime(path).toMillis() > lastKnownModified) {
                        load(configClass);
                    }
                }

                if (!key.reset()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Constants.LOGGER.error("Config file watcher stopped unexpectedly", e);
        }
    }
}