package net.gozar.plugin.api;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Publisher subclasses this service and implements an engine adapter, not a second host UI.
 * The adapter receives the original UTF-8 config through a read-only FD. No dynamic class loading.
 * This base rejects ALL IPC from untrusted host certificates, including STOP/STATUS.
 */
public abstract class GhajarPluginService extends Service {
    protected abstract Set<String> trustedHostCertificates();
    protected String trustedHostPackage() { return "com.ghajarvpn.app"; }
    protected abstract String pluginId();
    protected abstract long pluginVersionCode();
    /** Initialize, self-test, then release temporary resources. Throw if not healthy. */
    protected abstract Bundle healthCheck() throws Exception;
    /** Validate full config without mutation; return mode=socks|tun and a complete TUN plan for tun. */
    protected abstract Bundle prepare(String originalConfig, String format, String settingsJson) throws Exception;
    /** Own the duplicated tun descriptor until stopEngine, if present. Return ready=true only when usable. */
    protected abstract Bundle startEngine(String originalConfig, String format, String settingsJson, ParcelFileDescriptor tun) throws Exception;
    protected abstract void stopEngine();
    protected abstract Bundle engineStatus();
    protected Bundle testEngine() throws Exception { throw new UnsupportedOperationException(); }
    protected void networkChanged() throws Exception { }
    private HandlerThread thread;
    private Messenger messenger;
    private IBinder hostToken;
    private final IBinder.DeathRecipient death = () -> { if (messenger != null) new Handler(thread.getLooper()).post(this::stopAndUnlink); };

    @Override public void onCreate() {
        super.onCreate(); thread = new HandlerThread("ghajar-plugin-control"); thread.start();
        messenger = new Messenger(new Handler(thread.getLooper(), this::request));
    }
    @Override public IBinder onBind(Intent intent) { return PluginWire.ACTION.equals(intent.getAction()) ? messenger.getBinder() : null; }
    private boolean authorized(int uid) {
        try {
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages == null || packages.length != 1 || !trustedHostPackage().equals(packages[0])) return false;
            int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo p = getPackageManager().getPackageInfo(packages[0], flags);
            Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? p.signingInfo.getApkContentsSigners() : p.signatures;
            if (signatures == null || signatures.length != 1) return false;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format("%02x", b));
            return trustedHostCertificates().contains(hex.toString());
        } catch (Exception e) { return false; }
    }
    private String readConfig(Bundle b) throws Exception {
        ParcelFileDescriptor fd = b.getParcelable(PluginWire.CONFIG);
        if (fd == null) throw new IOException("Config FD missing");
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) >= 0) { if (out.size() + n > 8 * 1024 * 1024) throw new IOException("Config limit"); out.write(buf, 0, n); }
            return out.toString("UTF-8");
        }
    }
    private boolean request(Message message) {
        if (!authorized(message.sendingUid) || message.replyTo == null) return true;
        Bundle request = message.getData(); Bundle result = new Bundle();
        try {
            if (request.getInt(PluginWire.API_VERSION) != PluginWire.API || !pluginId().equals(request.getString(PluginWire.ID))) throw new SecurityException();
            String settings = request.getString("settings", "{}");
            if (settings.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) throw new IOException("Settings limit");
            switch (message.what) {
                case PluginWire.HEALTH: result = healthCheck(); break;
                case PluginWire.PREPARE: result = prepare(readConfig(request), request.getString("format"), settings); break;
                case PluginWire.START:
                    stopAndUnlink();
                    hostToken = request.getBinder("hostToken");
                    if (hostToken == null) throw new SecurityException();
                    hostToken.linkToDeath(death, 0);
                    ParcelFileDescriptor tun = request.getParcelable(PluginWire.TUN);
                    try { result = startEngine(readConfig(request), request.getString("format"), settings, tun); }
                    catch (Exception e) { if (tun != null) tun.close(); stopAndUnlink(); throw e; }
                    break;
                case PluginWire.STOP: stopAndUnlink(); break;
                case PluginWire.TEST: result = testEngine(); break;
                case PluginWire.STATUS: result = engineStatus(); break;
                case PluginWire.NETWORK_CHANGED: networkChanged(); break;
                default: throw new IllegalArgumentException();
            }
            if (result == null) result = new Bundle(); result.putBoolean(PluginWire.OK, true);
        } catch (Exception e) { result = new Bundle(); result.putBoolean(PluginWire.OK, false); }
        result.putInt(PluginWire.API_VERSION, PluginWire.API);
        result.putLong(PluginWire.VERSION, pluginVersionCode());
        result.putString(PluginWire.ID, pluginId());
        result.putString(PluginWire.REQUEST_ID, request.getString(PluginWire.REQUEST_ID));
        Message reply = Message.obtain(); reply.what = message.what; reply.setData(result);
        try { message.replyTo.send(reply); } catch (RemoteException e) { stopAndUnlink(); }
        return true;
    }
    private void stopAndUnlink() {
        try { stopEngine(); } catch (RuntimeException ignored) { }
        if (hostToken != null) {
            try { hostToken.unlinkToDeath(death, 0); } catch (java.util.NoSuchElementException ignored) { }
            hostToken = null;
        }
    }
    @Override public boolean onUnbind(Intent intent) { new Handler(thread.getLooper()).post(this::stopAndUnlink); return false; }
    @Override public void onDestroy() { new Handler(thread.getLooper()).post(() -> { stopAndUnlink(); thread.quitSafely(); }); super.onDestroy(); }
}
