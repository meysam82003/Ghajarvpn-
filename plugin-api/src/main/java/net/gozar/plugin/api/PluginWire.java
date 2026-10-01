package net.gozar.plugin.api;

/** API 1. All requests/replies carry a fresh requestId; no discovery broadcast or executable path. */
public final class PluginWire {
    private PluginWire() {}
    public static final int API = 1;
    public static final int HEALTH = 1, PREPARE = 2, START = 3, STOP = 4, STATUS = 5, NETWORK_CHANGED = 6;
    public static final String ACTION = "net.gozar.plugin.BIND_V1";
    public static final String ID = "pluginId", API_VERSION = "apiVersion", VERSION = "versionCode";
    public static final String REQUEST_ID = "requestId", OK = "ok", CONFIG = "configFd", TUN = "tunFd";
}
