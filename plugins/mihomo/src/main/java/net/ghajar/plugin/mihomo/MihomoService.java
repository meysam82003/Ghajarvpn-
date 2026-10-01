package net.ghajar.plugin.mihomo;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import net.gozar.plugin.api.GhajarPluginService;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Preserves original YAML; one engine process/profile. No native code is loaded in the host. */
public class MihomoService extends GhajarPluginService {
    private Process engine;
    private LocalSocket control;
    private ParcelFileDescriptor tun;
    private File payload;
    protected Set<String> trustedHostCertificates() { return Collections.singleton(BuildConfig.HOST_CERTIFICATE); }
    protected String pluginId() { return "mihomo"; }
    protected long pluginVersionCode() { return BuildConfig.VERSION_CODE; }
    private String binary() { return new File(getApplicationInfo().nativeLibraryDir, "libmihomoghajar.so").getPath(); }
    private static final class Capture extends Thread {
        private final InputStream input;
        private final StringBuilder text = new StringBuilder();
        Capture(InputStream input) { this.input = input; setDaemon(true); }
        public void run() {
            try (Reader r = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                char[] b = new char[4096]; int n;
                while ((n = r.read(b)) >= 0) synchronized(text) { text.append(b, 0, n); if (text.length() > 32768) text.delete(0, text.length()-32768); }
            } catch (IOException ignored) { }
        }
        String result() { synchronized(text) { return text.toString(); } }
    }
    private String check(String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>(); command.add(binary()); Collections.addAll(command, args);
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start(); Capture output = new Capture(p.getInputStream()); output.start();
        try {
            if (!p.waitFor(12, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IOException("Mihomo validation failed (full config, Android TUN policy, or provider files)");
            output.join(500); return output.result();
        } finally { p.destroyForcibly(); }
    }
    protected Bundle healthCheck() throws Exception {
        if (!check("--version").trim().equals("mihomo-ghajar 3189346611caeba73aa87feaf708e4fd65115d16")) throw new IOException("Mihomo provenance mismatch");
        Bundle b = new Bundle(); b.putBoolean("healthy", true); return b;
    }
    private File write(String raw, String format, String settings) throws Exception {
        if (!Arrays.asList("mihomo-yaml", "mihomo-json").contains(format)) throw new IllegalArgumentException("Mihomo needs full YAML/JSON");
        if (new JSONObject(settings).length() != 0) throw new IllegalArgumentException("Mihomo settings belong in the full config");
        byte[] data = raw.getBytes(StandardCharsets.UTF_8); if (data.length == 0 || data.length > 8*1024*1024) throw new IllegalArgumentException("Config size limit");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data); StringBuilder id = new StringBuilder(); for (byte v : digest) id.append(String.format("%02x", v));
        File dir = new File(getFilesDir(), "profiles/" + id); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Profile directory");
        File file = new File(dir, "config.yaml"); Files.write(file.toPath(), data); return file;
    }
    protected Bundle prepare(String raw, String format, String settings) throws Exception {
        File file = write(raw, format, settings);
        try {
            String result = check("--check", "--config", file.getPath()); JSONObject plan = null;
            for (String line : result.split("\\r?\\n")) if (line.startsWith("{")) { JSONObject parsed = new JSONObject(line); if (parsed.has("mode")) plan = parsed; }
            if (plan == null) throw new IOException("Missing TUN plan");
            Bundle b = new Bundle(); b.putString("mode", plan.getString("mode")); b.putInt("mtu", plan.getInt("mtu")); b.putInt("socksPort", plan.optInt("socksPort")); b.putBoolean("fullConfigPreserved", plan.getBoolean("fullConfigPreserved"));
            for (String key : Arrays.asList("addresses", "routes", "dns")) {
                ArrayList<String> entries = new ArrayList<>(); JSONArray array = plan.optJSONArray(key);
                if (array != null) for (int i = 0; i < array.length(); i++) entries.add(array.getString(i));
                b.putStringArrayList(key, entries);
            }
            return b;
        } finally { file.delete(); }
    }
    protected Bundle startEngine(String raw, String format, String settings, ParcelFileDescriptor descriptor) throws Exception {
        Bundle plan = prepare(raw, format, settings);
        boolean ownsTun = "tun".equals(plan.getString("mode"));
        if (ownsTun != (descriptor != null)) throw new IllegalArgumentException("TUN ownership mismatch");
        tun = descriptor; payload = write(raw, format, settings);
        File socket = new File(getCacheDir(), "mihomo-" + UUID.randomUUID().toString().substring(0,8));
        engine = new ProcessBuilder(binary(), "--config", payload.getPath(), "--socket", socket.getPath()).directory(payload.getParentFile()).redirectErrorStream(true).start();
        // Native output can contain YAML credentials. Drain, but do not retain or forward it.
        final Process process = engine;
        Thread drain = new Thread(() -> { try (InputStream in = process.getInputStream()) { byte[] b = new byte[4096]; while (in.read(b) >= 0) {} } catch (IOException ignored) {} }, "mihomo-output"); drain.setDaemon(true); drain.start();
        long deadline = SystemClock.elapsedRealtime() + 8000;
        while (process.isAlive() && SystemClock.elapsedRealtime() < deadline) {
            LocalSocket attempt = new LocalSocket();
            try { attempt.connect(new LocalSocketAddress(socket.getPath(), LocalSocketAddress.Namespace.FILESYSTEM)); control = attempt; break; }
            catch (IOException e) { attempt.close(); Thread.sleep(50); }
        }
        if (control == null) throw new IOException("Mihomo control socket not ready");
        control.setSoTimeout(8000);
        if (tun != null) control.setFileDescriptorsForSend(new FileDescriptor[]{tun.getFileDescriptor()});
        control.getOutputStream().write(ownsTun ? 1 : 0); control.setFileDescriptorsForSend(null);
        if (control.getInputStream().read() != 1) throw new IOException("Mihomo did not activate the configured listener");
        payload.delete(); payload = null;
        Bundle b = engineStatus(); b.putString("socksHost", "127.0.0.1"); b.putInt("socksPort", plan.getInt("socksPort")); return b;
    }
    protected void stopEngine() {
        if (control != null) { try { control.close(); } catch (IOException ignored) {} control = null; }
        if (engine != null) { engine.destroy(); try { if (!engine.waitFor(2, TimeUnit.SECONDS)) engine.destroyForcibly(); } catch (InterruptedException e) { engine.destroyForcibly(); Thread.currentThread().interrupt(); } engine = null; }
        if (tun != null) { try { tun.close(); } catch (IOException ignored) {} tun = null; }
        if (payload != null) { payload.delete(); payload = null; }
    }
    protected Bundle engineStatus() { Bundle b = new Bundle(); b.putBoolean("ready", engine != null && engine.isAlive() && control != null); b.putString("diagnostic", "Full config engine; provider and rule semantics preserved"); return b; }
}
