package net.gozar.app.engine

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import org.json.JSONObject
import net.gozar.app.configtoolkit.BoundedInput

/** Per-process, authenticated loopback endpoint. Never use connection-list snapshots as totals. */
object SingBoxUsage {
    data class Sample(val upload: Long, val download: Long, val generation: String)
    fun read(port: Int, secret: String, generation: String, freeze: Boolean = false): Sample {
        require(port in 1..65535 && secret.length >= 32 && generation.isNotBlank())
        val connection = URL("http://127.0.0.1:$port/ghajar/${if(freeze) "freeze" else "usage"}").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.connectTimeout = 1500; connection.readTimeout = 1500
            connection.instanceFollowRedirects = false
            if(freeze)connection.requestMethod="POST"
            connection.setRequestProperty("Authorization", "Bearer $secret")
            check(connection.responseCode == 200) { "Accounting endpoint unavailable" }
            return decode(connection.inputStream.use { BoundedInput.read(it, 4096) }.toString(Charsets.UTF_8), generation)
        } finally { connection.disconnect() }
    }
    fun decode(raw: String, generation: String): Sample {
        val o = JSONObject(raw)
        require(o.getInt("version") == 1)
        fun counter(key: String): Long {
            val v = o.get(key)
            require(v is Long || v is Int)
            return (v as Number).toLong().also { require(it >= 0) { "Counter overflow" } }
        }
        return Sample(counter("upload"), counter("download"), generation)
    }
}
