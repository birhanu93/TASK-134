package com.fleetride.log;

import com.fleetride.service.Clock;
import com.fleetride.service.IOUtil;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructuredLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private final Path logFile;
    private final Clock clock;
    private final Writer fallback;

    public StructuredLogger(Path logFile, Clock clock) {
        this(logFile, clock, null);
    }

    public StructuredLogger(Path logFile, Clock clock, Writer fallback) {
        if (logFile == null) throw new IllegalArgumentException("logFile required");
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.logFile = logFile;
        this.clock = clock;
        this.fallback = fallback;
        IOUtil.uncheckedRun(() -> {
            if (logFile.getParent() != null) Files.createDirectories(logFile.getParent());
            if (!Files.exists(logFile)) Files.createFile(logFile);
        });
    }

    public void info(String event, Map<String, String> fields) { log(Level.INFO, event, fields); }
    public void warn(String event, Map<String, String> fields) { log(Level.WARN, event, fields); }
    public void error(String event, Map<String, String> fields) { log(Level.ERROR, event, fields); }
    public void debug(String event, Map<String, String> fields) { log(Level.DEBUG, event, fields); }

    public void log(Level level, String event, Map<String, String> fields) {
        if (event == null || event.isBlank()) throw new IllegalArgumentException("event required");
        Map<String, String> safe = Redactor.redactFields(fields == null ? Map.of() : fields);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        append(sb, "ts", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(clock.now()));
        sb.append(',');
        append(sb, "level", level.name());
        sb.append(',');
        append(sb, "event", event);
        for (Map.Entry<String, String> e : safe.entrySet()) {
            sb.append(',');
            append(sb, e.getKey(), e.getValue());
        }
        sb.append('}').append('\n');
        String line = sb.toString();
        IOUtil.uncheckedRun(() -> Files.writeString(logFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.APPEND));
        if (fallback != null) {
            IOUtil.uncheckedRun(() -> { fallback.write(line); fallback.flush(); });
        }
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('"').append(escape(value)).append('"');
        }
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Map<String, String> fields(String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("fields require key/value pairs");
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
