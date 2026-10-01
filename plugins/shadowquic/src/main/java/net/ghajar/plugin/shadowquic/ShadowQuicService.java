package net.ghajar.plugin.shadowquic;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import net.gozar.plugin.api.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One pinned APK-owned executable, launched only on START. Host owns TUN via zeptun. */
public class ShadowQuicService extends GhajarPluginService {
    private Process engine;
    private File config;
    private int port;
    private String lastEvent = "stopped";
    protected Set<String> trustedHostCertificates() { return Collections.singleton(BuildConfig.HOST_CERTIFICATE); }
    protected String pluginId() { return "shadowquic"; }
    protected long pluginVersionCode() { return BuildConfig.VERSION_CODE; }
    private File binary() { return new File(getApplicationInfo().nativeLibraryDir, "libshadowquic.so"); }
    protected Bundle healthCheck() throws Exception {
        if (!binary().canExecute()) throw new IOException("Binary missing");
        Process p = new ProcessBuilder(binary().getPath(), "--version").redirectErrorStream(true).start();
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IOException("Version check failed");
            byte[] buffer = new byte[128]; int n = p.getInputStream().read(buffer);
            String v = n < 0 ? "" : new String(buffer, 0, n, StandardCharsets.UTF_8).trim();
            if (!v.equals("shadowquic 0.4.0")) throw new IOException("Source version mismatch");
            Bundle b = new Bundle(); b.putBoolean("healthy", true); return b;
        } finally { p.destroyForcibly(); }
    }
    protected Bundle prepare(String raw, String format, String settings) throws Exception {
        ShadowQuicProfile.parse(raw, format);
        if (new JSONObject(settings).length() != 0) throw new IllegalArgumentException("Settings belong in the profile");
        Bundle b = new Bundle(); b.putString("mode", "socks"); b.putInt("mtu", 1500); return b;
    }
    protected Bundle startEngine(String raw, String format, String settings, ParcelFileDescriptor tun) throws Exception {
        if (tun != null) throw new IllegalArgumentException("ShadowQUIC does not own TUN");
        prepare(raw, format, settings);
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = reservation.getLocalPort(); }
        config = File.createTempFile("shadowquic-", ".json", getCacheDir());
        Files.write(config.toPath(), ShadowQuicProfile.config(ShadowQuicProfile.parse(raw, format), port).getBytes(StandardCharsets.UTF_8));
        engine = new ProcessBuilder(binary().getPath(), "--config", config.getPath(), "run").directory(getFilesDir()).redirectErrorStream(true).start();
        final Process running = engine;
        // This CLI emits its marker only AFTER build_manager has bound the SOCKS listener.
        // A SOCKS response alone could come from another app winning the ephemeral-port race.
        java.util.concurrent.atomic.AtomicBoolean bound = new java.util.concurrent.atomic.AtomicBoolean();
        Thread logs = new Thread(() -> {
            try (InputStream in = running.getInputStream()) {
                byte[] buf = new byte[4096]; int n; String tail = "";
                while ((n = in.read(buf)) >= 0) {
                    String text = tail + new String(buf, 0, n, StandardCharsets.UTF_8);
                    if (text.contains("shadowquic 0.4.0 running")) bound.set(true);
                    tail = text.substring(Math.max(0, text.length() - 64));
                }
            } catch (IOException ignored) { }
        }, "shadowquic-output");
        logs.setDaemon(true); logs.start();
        long deadline = SystemClock.elapsedRealtime() + 12000;
        boolean ready = false;
        while (running.isAlive() && SystemClock.elapsedRealtime() < deadline) {
            if (!bound.get()) { Thread.sleep(50); continue; }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 200); s.setSoTimeout(300);
                s.getOutputStream().write(new byte[]{5,1,0});
                ready = s.getInputStream().read() == 5 && s.getInputStream().read() == 0;
                if (ready) break;
            } catch (IOException ignored) { }
            Thread.sleep(50); // readiness polling, not UI navigation delay
        }
        if (!ready) { stopEngine(); throw new IOException("ShadowQUIC SOCKS not ready"); }
        config.delete(); config = null; lastEvent = "local SOCKS ready; remote connectivity is tested by traffic";
        Bundle b = engineStatus(); b.putString("socksHost", "127.0.0.1"); b.putInt("socksPort", port); return b;
    }
    protected void stopEngine() {
        Process p = engine; engine = null;
        if (p != null) { p.destroy(); try { if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly(); } catch (InterruptedException e) { p.destroyForcibly(); Thread.currentThread().interrupt(); } }
        if (config != null) { config.delete(); config = null; }
        port = 0; lastEvent = "stopped";
    }
    protected Bundle testEngine() throws Exception {
        if (engine == null || !engine.isAlive()) throw new IOException("Connect first");
        Bundle b = new Bundle(); b.putLong("latencyMs", SocksProbe.test(port)); return b;
    }
    protected Bundle engineStatus() {
        Bundle b = new Bundle(); b.putBoolean("ready", engine != null && engine.isAlive()); b.putString("diagnostic", lastEvent); return b;
    }
}
