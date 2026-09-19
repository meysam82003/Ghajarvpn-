package dev.zeptun

/**
 * The class zeptun's JNI bridge binds its native methods to.
 *
 * The package and name are not a choice: `libzeptun-jni.so` looks this exact
 * class up in JNI_OnLoad (`ZEPTUN_JNI_CLASS` = "dev/zeptun/Zeptun" in its
 * src/jni/zeptun_jni.c) and registers the four methods on it. Renaming or
 * moving this file stops the binding, with no compile error to say so.
 *
 * Nothing in the app calls this directly - net.gozar.app.ZeptunEngine is the
 * wrapper that knows whether the library is even present. This declares the
 * contract and nothing else.
 *
 * See third_party/zeptun/ for the licence and how the library is built.
 */
object Zeptun {

    /**
     * Starts the engine on an established tun file descriptor.
     *
     * [service] is the VpnService, which the engine uses to protect its own
     * upstream sockets so they are not captured by the tunnel it is serving.
     * [config] is TOML. Returns 0 on success.
     */
    external fun nativeStart(service: Any?, fd: Int, config: String?): Int

    external fun nativeStop()

    external fun nativeVersion(): String

    /** One of the engine's counters, by index. */
    external fun nativeCounter(index: Int): Long
}
