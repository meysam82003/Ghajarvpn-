package net.gozar.plugin.api;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** API 1 additive settings schema. Embedded files are data, never executable paths or trust anchors. */
public final class MihomoFiles {
    public static final int MAX_BYTES = 32768, MAX_FILES = 16;
    private MihomoFiles() {}
    public static void validPath(String path) {
        if (path.length() > 160 || !path.matches("(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*") ||
            path.equals("config.yaml") || path.equals("cache.db")) throw new IllegalArgumentException("Invalid relative dependency path");
    }
    public static String hash(byte[] data) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) s.append(String.format(Locale.ROOT,"%02x", b));
        return s.toString();
    }
    public static Map<String, byte[]> decode(JSONObject settings) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        if (settings.length() == 0) return result;
        if (settings.length() != 2 || settings.getInt("filesVersion") != 1 || settings.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Unsupported Mihomo file schema");
        JSONArray files = settings.getJSONArray("files");
        if (files.length() > MAX_FILES) throw new IllegalArgumentException("Too many dependencies");
        int total = 0;
        for (int i=0; i<files.length(); i++) {
            JSONObject entry=files.getJSONObject(i); String path=entry.getString("path"); validPath(path);
            if (entry.length()!=3 || result.containsKey(path)) throw new IllegalArgumentException("Duplicate dependency");
            String encoded=entry.getString("data");
            if (encoded.length() > (MAX_BYTES+2)/3*4) throw new IllegalArgumentException("Dependency size limit");
            byte[] data=Base64.getDecoder().decode(encoded); total+=data.length;
            if (total>MAX_BYTES || !hash(data).equals(entry.getString("sha256"))) throw new IllegalArgumentException("Dependency integrity/size");
            for (String other:result.keySet()) if (path.startsWith(other+"/") || other.startsWith(path+"/")) throw new IllegalArgumentException("File/directory collision");
            result.put(path,data);
        }
        return result;
    }
    public static JSONObject put(JSONObject previous, String path, byte[] data) throws Exception {
        validPath(path); Map<String,byte[]> files=decode(previous); files.put(path,data);
        JSONArray entries=new JSONArray();
        for (Map.Entry<String,byte[]> e:files.entrySet()) entries.put(new JSONObject().put("path",e.getKey()).put("data",Base64.getEncoder().encodeToString(e.getValue())).put("sha256",hash(e.getValue())));
        JSONObject out=new JSONObject().put("filesVersion",1).put("files",entries); decode(out); return out;
    }
    public static void materialize(File directory, JSONObject settings) throws Exception {
        Path root=directory.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) throw new IOException("Symlink profile root");
        Files.createDirectories(root);
        for (Map.Entry<String,byte[]> e:decode(settings).entrySet()) {
            Path target=root.resolve(e.getKey()).normalize();
            if (!target.startsWith(root)) throw new IOException("Dependency outside profile");
            Path parent=root;
            String[] segments=e.getKey().split("/");
            for (int i=0;i<segments.length-1;i++) {
                parent=parent.resolve(segments[i]);
                if (Files.isSymbolicLink(parent)) throw new IOException("Symlink directory");
                Files.createDirectories(parent);
            }
            if (Files.isSymbolicLink(target) || (Files.exists(target,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))) throw new IOException("Unsafe dependency target");
            // New profile directory is private to this APK; no engine runs during preparation.
            Files.write(target,e.getValue(),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
        }
    }
}
