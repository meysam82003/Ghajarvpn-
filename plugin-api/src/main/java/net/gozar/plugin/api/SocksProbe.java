package net.gozar.plugin.api;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Explicit user test over the engine SOCKS path, with normal TLS hostname/CA verification. */
public final class SocksProbe {
    private SocksProbe() {}
    public static long test(int port) throws Exception {
        long started = System.nanoTime();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", port));
        try (Socket socket = new Socket(proxy)) {
            socket.connect(InetSocketAddress.createUnresolved("www.gstatic.com", 443), 6000); socket.setSoTimeout(6000);
            try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, "www.gstatic.com", 443, true)) {
                SSLParameters params = tls.getSSLParameters(); params.setEndpointIdentificationAlgorithm("HTTPS"); tls.setSSLParameters(params);
                tls.startHandshake();
                tls.getOutputStream().write("GET /generate_204 HTTP/1.1\r\nHost: www.gstatic.com\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                ByteArrayOutputStream status = new ByteArrayOutputStream();
                int value; while ((value = tls.getInputStream().read()) != -1 && value != '\n') { if (status.size() >= 256) throw new IOException("Invalid test response"); status.write(value); }
                if (!status.toString("US-ASCII").startsWith("HTTP/1.1 204")) throw new IOException("Internet test failed");
            }
        }
        return (System.nanoTime() - started) / 1000000L;
    }
}
