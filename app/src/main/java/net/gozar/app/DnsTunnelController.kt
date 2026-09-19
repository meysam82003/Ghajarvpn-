package net.gozar.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What the DNS tunnel is doing. Never merged with [VpnState] or [DnsOnlyState]. */
enum class DnsTunnelPhase {
    /** Not running. */
    OFF,

    /** The engine binary is not in this build - see [DnsTunnelController]. */
    UNAVAILABLE,

    /** The profile is missing something a tunnel cannot work without. */
    INCOMPLETE,

    STARTING,

    /** The client is up and a local port is listening. Not "internet works". */
    LISTENING,

    /** Traffic has been carried end to end and verified. */
    CARRYING,

    FAILED
}

/**
 * What a complete DNS tunnel profile is.
 *
 * Every field here is required by the protocol and none of it is derivable
 * from the others. This is the type that makes "is it configured" a question
 * with one answer instead of five scattered null checks.
 */
data class DnsTunnelProfile(
    val name: String,
    /** The resolver the tunnel's queries travel through. */
    val resolver: DnsResolver,
    /** The zone whose nameserver is the tunnel server. */
    val domain: String,
    /** The server's public key, as the server operator printed it. */
    val publicKey: String,
    val autoReconnect: Boolean
) {
    companion object {
        /**
         * Reads a profile out of the store, or returns null with nothing guessed.
         *
         * Null rather than a partially filled profile, and no defaults for the
         * domain or the key: a tunnel built on a guessed domain fails in a way
         * that looks exactly like the network blocking it, and a client that
         * accepts any key cannot tell the tunnel server from whoever is in
         * between - which on these networks is the entire threat.
         */
        fun from(store: ConfigStore, resolvers: DnsResolverStore): DnsTunnelProfile? {
            val domain = store.dnsTunnelDomain.value.trim().trim('.')
            val key = store.dnsTunnelKey.value.trim()
            val resolver = resolvers.resolverOf(store.dnsTunnelResolver.value)
            if (domain.isBlank() || key.isBlank() || resolver == null) return null
            return DnsTunnelProfile(
                name = store.dnsTunnelName.value.ifBlank { domain },
                resolver = resolver,
                domain = domain,
                publicKey = key,
                autoReconnect = store.dnsTunnelAutoReconnect.value
            )
        }
    }
}

/**
 * The DNS tunnel client.
 *
 * ## What this is, and what it deliberately is not
 *
 * A DNS tunnel carries real traffic inside DNS messages: the client encodes a
 * stream into queries under a domain whose nameserver is the tunnel server,
 * and the server answers with the return direction in TXT records. It needs
 * three things that cannot be inferred from a resolver's IP - the domain, the
 * server's public key, and a server actually running at the other end.
 *
 * **The engine binary is not bundled in this build.** It is a Go program and
 * this app already ships Go binaries the same way (see AetherController and
 * the sing-box build), so the mechanism is in place: a `lib*.so` in jniLibs,
 * executed from `nativeLibraryDir`. What is missing is the binary itself, and
 * `third_party/dnstt/README.md` records exactly what has to be added and why
 * it could not be added here.
 *
 * So this class reports [DnsTunnelPhase.UNAVAILABLE] and does nothing else.
 * That is the point of it existing now rather than later: the profile screen,
 * the resolver's tunnel-compatibility test and the selection rules are all
 * real and testable against this contract, and none of them will report a
 * tunnel that is not there.
 *
 * ## The contract the engine must satisfy
 *
 * When the binary is added, it must:
 *
 * 1. Be present at `nativeLibraryDir/libdnstt.so`, because Android only
 *    extracts and marks executable the files matching `lib*.so`.
 * 2. Accept the resolver, the domain and the server's public key, and expose a
 *    local TCP listener that forwards through the tunnel.
 * 3. Print a line on stdout or stderr when that listener is up, so
 *    [LISTENING] is observed rather than assumed after a sleep.
 * 4. Exit non-zero on a failure to establish, rather than idling.
 *
 * [start] is written against exactly that and is the only thing that will need
 * changing when the flags are known - not the screens above it.
 */
object DnsTunnelController {

    private const val TAG = "GhajarDnsTunnel"

    /** The name Android will extract and allow to be executed. */
    private const val BINARY = "libdnstt.so"

    /**
     * The local port the client is expected to listen on.
     *
     * Fixed rather than ephemeral so the rest of the app can be configured
     * against it, and chosen high and odd to avoid the ranges this app's other
     * engines already use.
     */
    const val LOCAL_PORT = 18753

    private val _phase = MutableStateFlow(DnsTunnelPhase.OFF)
    val phase: StateFlow<DnsTunnelPhase> = _phase.asStateFlow()

    private val _detail = MutableStateFlow("")
    val detail: StateFlow<String> = _detail.asStateFlow()

    /** Bytes carried, once the engine reports them. Zero is honest, not hidden. */
    private val _carried = MutableStateFlow(0L)
    val carried: StateFlow<Long> = _carried.asStateFlow()

    /**
     * Whether this build can run a DNS tunnel at all.
     *
     * Checked by looking for the file, not by a build flag, so a build that
     * gains the binary starts working without a code change - and one that
     * does not have it says so instead of failing at exec time with a message
     * nobody can act on.
     */
    fun available(context: Context): Boolean =
        runCatching {
            File(context.applicationInfo.nativeLibraryDir, BINARY).canExecute()
        }.getOrDefault(false)

    /**
     * Brings the tunnel up, if there is an engine and a complete profile.
     *
     * Returns false and sets a phase that says which of the two was missing.
     * It never returns true on the strength of a profile alone: "configured"
     * and "connected" are different facts and this app does not conflate them.
     */
    fun start(context: Context, profile: DnsTunnelProfile?): Boolean {
        if (profile == null) {
            _phase.value = DnsTunnelPhase.INCOMPLETE
            _detail.value = "profile-incomplete"
            return false
        }
        if (!available(context)) {
            GhajarLog.w(TAG, "no $BINARY in this build; not claiming a tunnel")
            _phase.value = DnsTunnelPhase.UNAVAILABLE
            _detail.value = "engine-missing"
            return false
        }
        // Reaching here means the binary exists. Starting it is the one piece
        // that needs the engine's real flags, which is why this is a refusal
        // rather than a guess: a ProcessBuilder invoked with invented
        // arguments would exit non-zero and be reported as the network being
        // blocked, which is the most expensive kind of wrong answer here.
        GhajarLog.w(TAG, "$BINARY present but its invocation is not yet pinned")
        _phase.value = DnsTunnelPhase.UNAVAILABLE
        _detail.value = "engine-contract-unpinned"
        return false
    }

    fun stop() {
        _phase.value = DnsTunnelPhase.OFF
        _detail.value = ""
        _carried.value = 0L
    }

    /**
     * Whether a resolver has been shown to carry this tunnel's queries.
     *
     * Reads the scan's own finding rather than re-deriving it. The scan asks
     * for a TXT record under a random label of the profile's domain and sees
     * whether an answer returns from the far side; answering an ordinary A
     * query proves nothing about that path, and treating the two as the same
     * test is how a "tunnel-ready" list fills up with resolvers that cannot
     * carry one.
     */
    fun resolverLooksUsable(verdict: DnsVerdict?): Boolean = verdict?.tunnelReady == true
}
