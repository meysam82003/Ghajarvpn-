package net.gozar.app.sharing

import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/** TCP-only authenticated gateway. The only outbound socket destination is a fixed loopback SOCKS endpoint. */
class AuthenticatedRelay(private val upstreamPort: Int, private val user: String, private val password: String) : Closeable {
    data class Client(val ip: String, val since: Long, val uploaded: Long, val downloaded: Long)
    private class Session(val ip: String) {
        val since = System.currentTimeMillis(); val up = AtomicLong(); val down = AtomicLong()
        @Volatile var authenticated = false
    }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val sessions = ConcurrentHashMap<Socket, Session>()
    private val permits = Semaphore(16)
    private val pool = Executors.newFixedThreadPool(32) { r -> Thread(r, "Ghajar-share-relay").apply { isDaemon = true } }
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var closed = false
    val port: Int get() = listener?.localPort ?: 0
    fun clients(): List<Client> = sessions.values.filter { it.authenticated }.map { Client(it.ip, it.since, it.up.get(), it.down.get()) }
    fun start(address: InetAddress, port: Int = 18686) {
        require(user.isNotBlank() && password.length >= 16 && upstreamPort in 1..65535)
        check(!closed && listener == null)
        val server = ServerSocket()
        try { server.bind(InetSocketAddress(address, port), 16) } catch (e: Exception) { server.close(); throw e }
        listener = server
        Thread({
            while (!closed) {
                val client = try { server.accept() } catch (_: IOException) { break }
                if (!permits.tryAcquire()) { client.close(); continue }
                synchronized(this) { if (closed) client.close() else sockets.add(client) }
                if (closed) { permits.release(); break }
                try { pool.execute { serve(client) } } catch (_: RejectedExecutionException) { client.close(); sockets.remove(client); permits.release() }
            }
        }, "Ghajar-share-accept").apply { isDaemon = true; start() }
    }
    private fun equal(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    private fun read(input: InputStream, n: Int): ByteArray {
        require(n in 0..16384)
        val b = ByteArray(n); var offset = 0
        while (offset < n) { val got = input.read(b, offset, n-offset); if (got < 0) throw EOFException(); offset += got }
        return b
    }
    private fun byte(input: InputStream) = input.read().also { if (it < 0) throw EOFException() }
    private data class Target(val atyp: Int, val address: ByteArray, val port: ByteArray)
    private fun socks(input: InputStream, out: OutputStream): Target {
        val methods = read(input, byte(input)); require(methods.contains(2.toByte()))
        out.write(byteArrayOf(5,2)); out.flush()
        require(byte(input) == 1)
        val u = String(read(input, byte(input)), Charsets.UTF_8)
        val p = String(read(input, byte(input)), Charsets.UTF_8)
        val valid = equal(u,user) and equal(p,password)
        out.write(byteArrayOf(1, if(valid) 0 else 1)); out.flush(); require(valid)
        require(byte(input)==5 && byte(input)==1 && byte(input)==0) // CONNECT only; no UDP/BIND fallback
        val type = byte(input)
        val address = when(type) { 1 -> read(input,4); 4 -> read(input,16); 3 -> { val n=byte(input); require(n>0); byteArrayOf(n.toByte())+read(input,n) }; else -> error("address") }
        val port=read(input,2); require(port.any { it.toInt()!=0 })
        return Target(type,address,port)
    }
    private fun http(first: Int, input: InputStream, out: OutputStream): Target {
        val buf=ByteArrayOutputStream(); buf.write(first); var tail=first
        while(buf.size()<8192) { val next=byte(input); buf.write(next); tail=(tail shl 8) or next; if(buf.size()>=4 && tail==0x0d0a0d0a) break }
        val header=buf.toString("ISO-8859-1"); require(header.endsWith("\r\n\r\n"))
        val lines=header.split("\r\n"); val parts=lines.first().split(' ')
        if(parts.size!=3 || parts[0]!="CONNECT" || parts[2] !in setOf("HTTP/1.1","HTTP/1.0")) {
            out.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()); out.flush(); error("CONNECT required")
        }
        val auth=lines.drop(1).filter { it.substringBefore(':').equals("Proxy-Authorization",true) }
        val expected=Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
        val supplied=auth.singleOrNull()?.substringAfter(':')?.trim().orEmpty()
        if(auth.size!=1 || !supplied.substringBefore(' ').equals("Basic",true) || !equal(supplied.substringAfter(' ',"" ).trim(),expected)) {
            out.write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"Ghajar\"\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()); out.flush(); error("authentication")
        }
        val authority=parts[1]; val port=authority.substringAfterLast(':').toIntOrNull() ?: error("port")
        require(port in 1..65535)
        val host=authority.substringBeforeLast(':').removeSurrounding("[","]")
        require(host.isNotEmpty() && host.none { it.isWhitespace() || it in "/@\\?#" })
        val address: ByteArray; val type: Int
        if(host.contains(':')) { require(host.matches(Regex("[0-9a-fA-F:]+"))); address=InetAddress.getByName(host).address; require(address.size==16); type=4 }
        else { val ascii=java.net.IDN.toASCII(host).toByteArray(Charsets.US_ASCII); require(ascii.size in 1..253); address=byteArrayOf(ascii.size.toByte())+ascii; type=3 }
        return Target(type,address,byteArrayOf((port shr 8).toByte(),port.toByte()))
    }
    private fun serve(client: Socket) {
        val session=Session(client.inetAddress.hostAddress.orEmpty()); sessions[client]=session
        var upstream: Socket?=null
        try {
            client.soTimeout=10000
            val rawInput=client.getInputStream()
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            val input=object : FilterInputStream(rawInput) {
                private fun remaining() { val ms=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()); if(ms<=0) throw SocketTimeoutException(); client.soTimeout=ms.toInt().coerceAtLeast(1) }
                override fun read():Int { remaining(); return rawInput.read() }
                override fun read(b:ByteArray, off:Int, len:Int):Int { remaining(); return rawInput.read(b,off,len) }
            }
            val output=client.getOutputStream(); val first=byte(input)
            val target=if(first==5) socks(input,output) else http(first,input,output)
            upstream=Socket()
            synchronized(this) { check(!closed); sockets.add(upstream) }
            upstream.soTimeout=15000
            upstream.connect(InetSocketAddress("127.0.0.1",upstreamPort),4000)
            val ui=upstream.getInputStream(); val uo=upstream.getOutputStream()
            uo.write(byteArrayOf(5,1,0)); uo.flush(); require(byte(ui)==5 && byte(ui)==0)
            uo.write(byteArrayOf(5,1,0,target.atyp.toByte())+target.address+target.port); uo.flush()
            require(byte(ui)==5 && byte(ui)==0 && byte(ui)==0)
            when(byte(ui)) { 1->read(ui,4); 4->read(ui,16); 3->read(ui,byte(ui)); else->error("upstream address") }; read(ui,2)
            check(!closed)
            if(first==5) output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0)) else output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
            output.flush(); session.authenticated=true
            client.soTimeout=120000; upstream.soTimeout=120000
            val remote=upstream
            val downstream=pool.submit { try { pump(ui,output,session.down); client.shutdownOutput() } catch (_:Exception) { client.close(); remote.close() } }
            try { pump(rawInput,uo,session.up); upstream.shutdownOutput(); downstream.get(120,TimeUnit.SECONDS) }
            finally { downstream.cancel(true) }
        } catch (_:Exception) { /* No credentials, request destinations or payloads in logs. */ }
        finally { sessions.remove(client); sockets.remove(client); runCatching { client.close() }; upstream?.let { sockets.remove(it); runCatching { it.close() } }; permits.release() }
    }
    private fun pump(input: InputStream, output: OutputStream, counter: AtomicLong) {
        val buffer=ByteArray(16384)
        while(!closed) { val n=input.read(buffer); if(n<0) break; output.write(buffer,0,n); counter.addAndGet(n.toLong()) }
    }
    @Synchronized override fun close() { closed=true; runCatching { listener?.close() }; sockets.forEach { runCatching { it.close() } }; sockets.clear(); sessions.clear(); pool.shutdownNow() }
}
