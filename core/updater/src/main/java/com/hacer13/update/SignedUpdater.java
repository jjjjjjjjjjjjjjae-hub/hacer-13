package com.hacer13.update;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Data-only updates. Trust boundary: APK pinned key + app-private storage.
 * No scripts, dynamic classes, native libraries, shell or installer execution.
 */
public final class SignedUpdater {
    public interface Transport { byte[] get(String url, int limit) throws Exception; }
    public interface Fault { void at(String point) throws Exception; }
    public static final int FILE_LIMIT = 2 * 1024 * 1024, TOTAL_LIMIT = 8 * 1024 * 1024;
    private static final Object PROCESS_LOCK = new Object();
    private static final Object INSTALL_LOCK = new Object();
    private final Path root;
    private final byte[] key;
    private final Transport transport;
    private final Fault fault;
    public SignedUpdater(File directory) throws Exception { this(directory, Trust.PUBLIC_KEY, new HttpsTransport(), p -> {}); }
    public SignedUpdater(File directory, String publicKey, Transport transport, Fault fault) throws Exception {
        root = directory.toPath();
        // Android's default Ed25519 KeyFactory may select AndroidKeyStore and reject
        // imported X.509 keys. Use the bundled, version-pinned lightweight verifier.
        byte[] spki = Base64.getDecoder().decode(publicKey);
        byte[] prefix = {0x30,0x2a,0x30,0x05,0x06,0x03,0x2b,0x65,0x70,0x03,0x21,0x00};
        if (spki.length != 44 || !Arrays.equals(prefix, Arrays.copyOf(spki,12))) throw new SecurityException("Invalid Ed25519 SPKI");
        key = Arrays.copyOfRange(spki,12,44);
        if (!Ed25519.validatePublicKeyFull(key,0)) throw new SecurityException("Invalid Ed25519 public point");
        this.transport = transport; this.fault = fault; Files.createDirectories(root);
    }
    public static final class Snapshot {
        public final int revision, highest;
        public final JSONObject menu, theme;
        public final File badge;
        Snapshot(int revision, int highest, JSONObject menu, JSONObject theme, File badge) {
            this.revision = revision; this.highest = highest; this.menu = menu; this.theme = theme; this.badge = badge;
        }
    }
    private interface Action<T> { T run() throws Exception; }
    private <T> T locked(Action<T> action) throws Exception {
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = FileChannel.open(root.resolve("update.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) { return action.run(); }
        }
    }
    public Snapshot current() throws Exception { return locked(() -> recover(readState())); }
    public boolean rollback() throws Exception {
        return locked(() -> {
            JSONObject s = readState(); String previous = s.getString("previous");
            if (previous.isEmpty() && s.getString("active").isEmpty()) return false;
            if (!previous.isEmpty()) snapshot(previous, s.getInt("highest"));
            s.put("active", previous).put("previous", ""); writeState(s); return true;
        });
    }
    public String check() throws Exception { return install(transport.get(Trust.MANIFEST, 65536)); }
    public String install(byte[] envelope) throws Exception {
        // Serialize writers separately: rendering the current menu must never wait for network I/O.
        synchronized (INSTALL_LOCK) {
            try (FileChannel channel = FileChannel.open(root.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) { return installLocked(envelope); }
        }
    }
    private String installLocked(byte[] envelope) throws Exception {
        JSONObject state = locked(() -> { JSONObject s = readState(); recover(s); return s; });
        JSONObject manifest = verify(envelope);
        int incoming = integer(manifest, "revision", 1, 1_000_000);
        if (incoming <= state.getInt("highest")) return "Жаңарту жоқ · қабылданған v" + state.getInt("highest");
        Path stage = root.resolve("stage"); remove(stage); Files.createDirectories(stage);
        try {
            JSONArray files = manifest.getJSONArray("files"); Map<String, byte[]> reusable = new HashMap<>();
            String active = state.getString("active");
            if (!active.isEmpty()) try {
                JSONObject old = verify(read(root.resolve(active).resolve("envelope.json"), 65536));
                for (int i = 0; i < old.getJSONArray("files").length(); i++) {
                    JSONObject f = old.getJSONArray("files").getJSONObject(i);
                    byte[] b = read(root.resolve(active).resolve(f.getString("path")), FILE_LIMIT);
                    if (validHash(b, f)) reusable.put(f.getString("sha256"), b);
                }
            } catch (Exception ignored) { /* Damaged cache never becomes trusted. */ }
            int downloaded = 0;
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i); byte[] bytes = reusable.get(file.getString("sha256"));
                if (bytes == null) {
                    bytes = transport.get(Trust.BASE + "blobs/" + file.getString("sha256"), file.getInt("size")); downloaded += bytes.length;
                }
                if (!validHash(bytes, file)) throw new SecurityException("SHA-256/size mismatch: " + file.getString("path"));
                validateData(file.getString("path"), bytes);
                Path target = stage.resolve(file.getString("path")); Files.createDirectories(target.getParent()); write(target, bytes);
            }
            write(stage.resolve("envelope.json"), envelope); fault.at("staged");
            String id = "bundle-" + incoming + "-" + sha256(envelope).substring(0, 16);
            Path bundle = root.resolve(id); remove(bundle);
            Files.move(stage, bundle, StandardCopyOption.ATOMIC_MOVE); syncDir(root); fault.at("before_commit");
            // One atomic pointer commits active, backup and replay floor together.
            locked(() -> {
                JSONObject latest = readState(); recover(latest);
                if (incoming <= latest.getInt("highest")) throw new SecurityException("Revision changed during update");
                JSONObject next = new JSONObject().put("highest", incoming).put("active", id).put("previous", latest.getString("active"));
                writeState(next);
                try { garbageCollect(next); } catch (IOException ignored) { /* Cleanup cannot invalidate a committed update. */ }
                return null;
            });
            return "Ed25519 + SHA-256 расталды · data v" + incoming + " · " + downloaded + " B жүктелді";
        } finally { remove(stage); }
    }
    private JSONObject readState() throws Exception {
        Path path = root.resolve("state.json");
        if (!Files.exists(path)) {
            if (Files.exists(root.resolve("initialized"))) throw new SecurityException("Update state missing; replay floor reset rejected");
            JSONObject initial = new JSONObject().put("highest", 0).put("active", "").put("previous", "");
            writeState(initial); write(root.resolve("initialized"), new byte[]{1}); return initial;
        }
        JSONObject s = object(read(path, 4096)); exact(s, "highest", "active", "previous"); integer(s, "highest", 0, 1_000_000);
        for (String field : new String[]{"active", "previous"}) {
            String id = s.getString(field);
            if (!id.isEmpty() && !id.matches("bundle-[0-9]{1,7}-[a-f0-9]{16}")) throw new SecurityException("Invalid state path");
        }
        return s;
    }
    private Snapshot recover(JSONObject s) throws Exception {
        String active = s.getString("active"); if (active.isEmpty()) return fallback(s.getInt("highest"));
        try { return snapshot(active, s.getInt("highest")); }
        catch (Exception damaged) {
            String previous = s.getString("previous"); Snapshot safe;
            try { safe = previous.isEmpty() ? fallback(s.getInt("highest")) : snapshot(previous, s.getInt("highest")); }
            catch (Exception alsoDamaged) { previous = ""; safe = fallback(s.getInt("highest")); }
            s.put("active", previous).put("previous", ""); writeState(s); return safe;
        }
    }
    private Snapshot snapshot(String id, int highest) throws Exception {
        Path dir = root.resolve(id); byte[] envelope = read(dir.resolve("envelope.json"), 65536); JSONObject manifest = verify(envelope);
        int rev = manifest.getInt("revision");
        if (rev > highest || !id.equals("bundle-" + rev + "-" + sha256(envelope).substring(0,16))) throw new SecurityException("Bundle/state mismatch");
        JSONObject menu = null, theme = new JSONObject().put("accent", "#40E4BE"); File badge = null;
        JSONArray files = manifest.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i); String name = f.getString("path"); byte[] bytes = read(dir.resolve(name), FILE_LIMIT);
            if (!validHash(bytes, f)) throw new SecurityException("Cached file damaged"); validateData(name, bytes);
            if (name.equals("menu.json")) menu = object(bytes);
            if (name.equals("theme.json")) theme = object(bytes);
            if (name.equals("resources/badge.png")) badge = dir.resolve(name).toFile();
        }
        if (menu == null) throw new SecurityException("No menu"); return new Snapshot(rev, highest, menu, theme, badge);
    }
    private Snapshot fallback(int highest) throws Exception {
        return new Snapshot(0, highest, new JSONObject(Trust.FALLBACK), new JSONObject().put("accent", "#40E4BE"), null);
    }
    private JSONObject verify(byte[] envelopeBytes) throws Exception {
        if (envelopeBytes.length > 65536) throw new SecurityException("Envelope too large");
        JSONObject envelope = object(envelopeBytes); exact(envelope, "algorithm", "payload_b64", "signature_b64");
        if (!"Ed25519".equals(envelope.getString("algorithm"))) throw new SecurityException("Algorithm rejected");
        byte[] payload = Base64.getDecoder().decode(envelope.getString("payload_b64")), signature = Base64.getDecoder().decode(envelope.getString("signature_b64"));
        if (payload.length > 32768 || signature.length != 64) throw new SecurityException("Signature size rejected");
        if (!Ed25519.verify(signature,0,key,0,payload,0,payload.length)) throw new SecurityException("Ed25519 signature rejected");
        JSONObject m = object(payload); exact(m, "schema", "channel", "revision", "min_core", "files");
        integer(m, "schema", 2, 2); if (!"lac".equals(m.getString("channel"))) throw new SecurityException("Wrong channel");
        integer(m, "revision", 1, 1_000_000); integer(m, "min_core", 1, 1);
        JSONArray files = m.getJSONArray("files"); if (files.length() < 1 || files.length() > 32) throw new SecurityException("Invalid inventory size");
        int total = 0; Set<String> names = new HashSet<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i); exact(f, "path", "sha256", "size"); String name = f.getString("path");
            if (!(name.equals("menu.json") || name.equals("theme.json") || name.matches("resources/[a-z0-9_-]{1,32}\\.png"))) throw new SecurityException("Executable/unknown path rejected");
            if (!names.add(name) || !f.getString("sha256").matches("[a-f0-9]{64}")) throw new SecurityException("Duplicate path/bad hash");
            total += integer(f, "size", 1, name.endsWith(".json") ? 32768 : FILE_LIMIT);
        }
        if (!names.contains("menu.json") || total > TOTAL_LIMIT) throw new SecurityException("Incomplete/oversized inventory"); return m;
    }
    public static void validateData(String path, byte[] bytes) throws Exception {
        if (path.equals("menu.json")) {
            JSONObject menu = object(bytes); exact(menu, "title", "features"); text(menu, "title", 80);
            JSONArray items = menu.getJSONArray("features"); Set<String> ids = new HashSet<>();
            if (items.length() < 1 || items.length() > 24) throw new SecurityException("Bad feature count");
            for (int i = 0; i < items.length(); i++) {
                JSONObject f = items.getJSONObject(i); exact(f, "id", "title", "description", "kind");
                if (!f.getString("id").matches("[a-z0-9_-]{1,32}") || !ids.add(f.getString("id")) || !"demo".equals(f.getString("kind"))) throw new SecurityException("Only explicit demo features allowed");
                text(f, "title", 70); text(f, "description", 180);
            }
        } else if (path.equals("theme.json")) {
            JSONObject theme = object(bytes); exact(theme, "accent");
            if (!theme.getString("accent").matches("#[A-Fa-f0-9]{6}")) throw new SecurityException("Bad theme");
        } else if (path.matches("resources/[a-z0-9_-]{1,32}\\.png")) {
            byte[] magic = {(byte)137,80,78,71,13,10,26,10};
            if (bytes.length < 33 || !Arrays.equals(magic, Arrays.copyOf(bytes, 8)) || ByteBuffer.wrap(bytes,8,4).getInt()!=13 || !"IHDR".equals(new String(bytes,12,4,StandardCharsets.US_ASCII))) throw new SecurityException("Invalid PNG");
            int w = ByteBuffer.wrap(bytes,16,4).getInt(), h = ByteBuffer.wrap(bytes,20,4).getInt();
            if (w < 1 || h < 1 || w > 2048 || h > 2048) throw new SecurityException("PNG dimensions too large");
        } else throw new SecurityException("Unknown data type");
    }
    private static void text(JSONObject j, String key, int max) throws Exception {
        String v = j.getString(key);
        if (v.isEmpty() || v.length() > max || v.matches("(?s).*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")) throw new SecurityException("Invalid text");
    }
    private static int integer(JSONObject j, String key, int min, int max) throws Exception {
        Object n = j.get(key);
        if (!(n instanceof Integer || n instanceof Long) || ((Number)n).longValue() < min || ((Number)n).longValue() > max) throw new SecurityException("Invalid integer: " + key);
        return ((Number)n).intValue();
    }
    private static JSONObject object(byte[] bytes) throws Exception { return new JSONObject(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()); }
    private static void exact(JSONObject j, String... keys) {
        Set<String> expected = new HashSet<>(Arrays.asList(keys)), actual = new HashSet<>(); Iterator<String> it = j.keys(); while (it.hasNext()) actual.add(it.next());
        if (!actual.equals(expected)) throw new SecurityException("Unknown/missing fields");
    }
    public static String sha256(byte[] b) throws Exception {
        StringBuilder out = new StringBuilder(); for (byte n : MessageDigest.getInstance("SHA-256").digest(b)) out.append(String.format(Locale.ROOT,"%02x",n&255)); return out.toString();
    }
    private static boolean validHash(byte[] b, JSONObject f) throws Exception { return b.length == f.getInt("size") && sha256(b).equals(f.getString("sha256")); }
    private static byte[] read(Path path, int max) throws Exception { try (InputStream in = Files.newInputStream(path)) { return bounded(in, max); } }
    public static byte[] bounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) != -1) { if (out.size() > max - n) throw new IOException("Download limit exceeded"); out.write(buf,0,n); } return out.toByteArray();
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(path.toFile())) { out.write(bytes); out.getFD().sync(); }
    }
    private void writeState(JSONObject state) throws IOException {
        Path tmp = root.resolve("state.tmp"); write(tmp, state.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(tmp, root.resolve("state.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); syncDir(root);
    }
    private static void syncDir(Path dir) {
        try (FileChannel c = FileChannel.open(dir, StandardOpenOption.READ)) { c.force(true); } catch (IOException ignored) { /* Not supported by every Android filesystem. */ }
    }
    private static void remove(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) { for (Path p : entries) remove(p); }
        Files.deleteIfExists(path);
    }
    private void garbageCollect(JSONObject state) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root, "bundle-*")) {
            for (Path p : entries) if (!p.getFileName().toString().equals(state.optString("active")) && !p.getFileName().toString().equals(state.optString("previous"))) remove(p);
        }
    }
    public static final class HttpsTransport implements Transport {
        @Override public byte[] get(String address, int max) throws Exception {
            URL url = new URL(address);
            if (!address.startsWith(Trust.BASE) || !"https".equals(url.getProtocol()) || !"raw.githubusercontent.com".equals(url.getHost()) || url.getPort()!=-1 || url.getUserInfo()!=null || url.getQuery()!=null || url.getRef()!=null
                || !(address.equals(Trust.MANIFEST) || address.matches(java.util.regex.Pattern.quote(Trust.BASE)+"blobs/[a-f0-9]{64}"))) throw new SecurityException("Update URL rejected");
            HttpsURLConnection c = (HttpsURLConnection)url.openConnection(); c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(false); c.setUseCaches(false); c.setRequestProperty("Accept-Encoding", "identity");
            try {
                if (c.getResponseCode()!=200) throw new IOException("GitHub HTTP " + c.getResponseCode());
                if (c.getContentLengthLong()>max) throw new IOException("Download too large");
                try (InputStream in = c.getInputStream()) { return bounded(in,max); }
            } finally { c.disconnect(); }
        }
    }
}
