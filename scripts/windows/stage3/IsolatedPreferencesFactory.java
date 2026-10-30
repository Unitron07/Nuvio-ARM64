package nuvio.testing;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import java.nio.charset.StandardCharsets;

/** Owner-test JVM only: never instantiate the Windows registry Preferences backend. */
public final class IsolatedPreferencesFactory implements PreferencesFactory {
    private final Node user;
    private final Node system;
    public IsolatedPreferencesFactory() {
        String configured = System.getProperty("nuvio.test.preferences", "");
        if (configured.isBlank() || !Path.of(configured).isAbsolute())
            throw new IllegalStateException("An absolute isolated preferences directory is required");
        Path root = Path.of(configured);
        user = new Node(null, "", new State(root.resolve("user.properties")));
        system = new Node(null, "", new State(root.resolve("system.properties")));
    }
    public Preferences userRoot() { return user; }
    public Preferences systemRoot() { return system; }
    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decoded(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private static final class State {
        final Path file;
        final Properties values = new Properties();
        State(Path file) { this.file = file; load(); }
        synchronized void load() {
            values.clear();
            if (Files.exists(file)) {
                try (var input = Files.newInputStream(file)) { values.load(input); }
                catch (IOException error) { throw new IllegalStateException("Isolated preferences cannot be read", error); }
            }
        }
        synchronized void save() {
            Path pending = null;
            try {
                Files.createDirectories(file.getParent());
                pending = Files.createTempFile(file.getParent(), "preferences-", ".pending");
                try (var output = Files.newOutputStream(pending)) { values.store(output, "Private isolated owner-test preferences"); }
                try { Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException error) { throw new IllegalStateException("Isolated preferences cannot be saved", error); }
            finally { if (pending != null) try { Files.deleteIfExists(pending); } catch (IOException ignored) { } }
        }
    }
    private static final class Node extends AbstractPreferences {
        final State state;
        Node(Node parent, String name, State state) {
            super(parent, name);
            this.state = state;
            synchronized (state) { state.values.put("n:" + encoded(absolutePath()), ""); }
        }
        String key(String name) { return "v:" + encoded(absolutePath() + '\0' + name); }
        protected void putSpi(String key, String value) {
            synchronized (state) { state.values.setProperty(key(key), value); state.save(); }
        }
        protected String getSpi(String key) { synchronized (state) { return state.values.getProperty(key(key)); } }
        protected void removeSpi(String key) { synchronized (state) { state.values.remove(key(key)); state.save(); } }
        protected String[] keysSpi() {
            synchronized (state) {
                String prefix = absolutePath() + '\0';
                return state.values.stringPropertyNames().stream().filter(k -> k.startsWith("v:"))
                    .map(k -> decoded(k.substring(2))).filter(k -> k.startsWith(prefix)).map(k -> k.substring(prefix.length())).toArray(String[]::new);
            }
        }
        protected String[] childrenNamesSpi() {
            synchronized (state) {
                String prefix = absolutePath().equals("/") ? "/" : absolutePath() + "/";
                return state.values.stringPropertyNames().stream().filter(k -> k.startsWith("n:"))
                    .map(k -> decoded(k.substring(2))).filter(k -> k.startsWith(prefix))
                    .map(k -> k.substring(prefix.length())).filter(k -> !k.isEmpty() && !k.contains("/"))
                    .distinct().toArray(String[]::new);
            }
        }
        protected AbstractPreferences childSpi(String name) { return new Node(this, name, state); }
        protected void removeNodeSpi() {
            synchronized (state) {
                String path = absolutePath();
                state.values.keySet().removeIf(k -> {
                    String entry = k.toString();
                    String value = decoded(entry.substring(2));
                    return entry.startsWith("n:") ? value.equals(path) : value.startsWith(path + '\0');
                });
                state.save();
            }
        }
        protected void flushSpi() { state.save(); }
        protected void syncSpi() { state.load(); }
    }
}
