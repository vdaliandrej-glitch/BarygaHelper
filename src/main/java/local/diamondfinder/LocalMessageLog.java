package local.diamondfinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.function.Consumer;

public final class LocalMessageLog implements AutoCloseable {
    private static final Set<String> SECRET_COMMANDS = Set.of(
        "login",
        "l",
        "log",
        "register",
        "reg",
        "r",
        "changepassword",
        "changepass",
        "cp",
        "password",
        "pass",
        "unregister",
        "unreg",
        "auth",
        "authenticate",
        "2fa",
        "totp",
        "otp",
        "token",
        "email"
    );
    public static final String DEFAULT_CONFIG = "# ShintaroHelper: local outgoing-message journal. No uploads.\n# Enable only on your own client. Settings apply on the next game launch.\nmessage.enabled=false\nmessage.chat=true\nmessage.commands=true\n# Files: logs/ShintaroHelper/message/YYYY-MM-DD.log\n# Authentication command arguments are always redacted.\n";
    private final Path directory;
    private final Clock clock;
    private final Consumer<String> error;
    private final boolean enabled;
    private final boolean chat;
    private final boolean commands;
    private final ThreadPoolExecutor writer;

    public LocalMessageLog(Path config, Path directory, Clock clock, Consumer<String> error) {
        this.directory = directory;
        this.clock = clock;
        this.error = error;
        Properties properties = new Properties();
        boolean loaded = false;

        try {
            Files.createDirectories(config.getParent());
            if (!Files.exists(config)) {
                Files.writeString(
                    config,
                    "# ShintaroHelper: local outgoing-message journal. No uploads.\n# Enable only on your own client. Settings apply on the next game launch.\nmessage.enabled=false\nmessage.chat=true\nmessage.commands=true\n# Files: logs/ShintaroHelper/message/YYYY-MM-DD.log\n# Authentication command arguments are always redacted.\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW
                );
            }

            try (BufferedReader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }

            loaded = true;
        } catch (IOException e) {
            error.accept("Cannot read local message-log settings: " + e.getClass().getSimpleName());
        }

        this.enabled = loaded && Boolean.parseBoolean(properties.getProperty("message.enabled", "false"));
        this.chat = Boolean.parseBoolean(properties.getProperty("message.chat", "true"));
        this.commands = Boolean.parseBoolean(properties.getProperty("message.commands", "true"));
        this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), r -> {
            Thread t = new Thread(r, "ShintaroHelper-local-log");
            t.setDaemon(true);
            return t;
        }, new AbortPolicy());
    }

    public void record(String content, boolean command) {
        if (this.enabled && (command ? this.commands : this.chat)) {
            ZonedDateTime timestamp = ZonedDateTime.now(this.clock);
            String safe = command ? redactCommand(content) : oneLine(content);
            String line = timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + " ["
                + (command ? "COMMAND" : "MESSAGE")
                + "] "
                + safe
                + System.lineSeparator();
            Path file = this.directory.resolve(timestamp.toLocalDate() + ".log");

            try {
                this.writer.execute(() -> {
                    try {
                        Files.createDirectories(this.directory);
                        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        this.error.accept("Cannot write local message log: " + e.getClass().getSimpleName());
                    }
                });
            } catch (RejectedExecutionException e) {
                this.error.accept("Local message-log queue unavailable; entry skipped");
            }
        }
    }

    public static String redactCommand(String input) {
        String text = input.strip();

        while (text.startsWith("/")) {
            text = text.substring(1);
        }

        int split = 0;

        while (split < text.length() && !Character.isWhitespace(text.charAt(split))) {
            split++;
        }

        String head = text.substring(0, split);
        String name = head.toLowerCase(Locale.ROOT);
        name = name.substring(name.lastIndexOf(58) + 1);
        return SECRET_COMMANDS.contains(name) && split < text.length() ? "/" + oneLine(head) + " [REDACTED]" : "/" + oneLine(text);
    }

    public static String oneLine(String text) {
        return text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t").replace("\u001b", "\\u001b");
    }

    @Override
    public void close() {
        this.writer.shutdown();

        try {
            if (!this.writer.awaitTermination(3L, TimeUnit.SECONDS)) {
                this.error.accept("Local message log is still flushing");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
