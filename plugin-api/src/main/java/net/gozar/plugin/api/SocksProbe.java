package net.gozar.plugin.api;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Explicit user test over the engine SOCKS path, with normal TLS hostname/CA verification. */
public final class SocksProbe {
    private SocksProbe() {}
    public static long test(int port) throws Exception { return test(port, null, null); }
    public static long test(int port, String user, String password) throws Exception {
        long started = System.nanoTime();
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", port));
        try (Socket socket = user == null ? new Socket(proxy) : new Socket(Proxy.NO_PROXY)) {
            if (user == null) socket.connect(InetSocketAddress.createUnresolved("www.gstatic.com", 443), 6000);
            else {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 6000);
                socket.setSoTimeout(6000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                OutputStream out = socket.getOutputStream();
                out.write(new byte[]{5, 1, 2});
                if (in.readUnsignedByte() != 5 || in.readUnsignedByte() != 2) throw new IOException("SOCKS authentication required");
                byte[] u = user.getBytes(StandardCharsets.UTF_8), p = password.getBytes(StandardCharsets.UTF_8);
                if (u.length > 255 || p.length > 255) throw new IOException("Invalid credentials length");
                out.write(1); out.write(u.length); out.write(u); out.write(p.length); out.write(p);
                if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != 0) throw new IOException("SOCKS authentication rejected");
                byte[] host = "www.gstatic.com".getBytes(StandardCharsets.US_ASCII);
                out.write(new byte[]{5,1,0,3}); out.write(host.length); out.write(host); out.write(new byte[]{1,(byte)187});
                if (in.readUnsignedByte()!=5 || in.readUnsignedByte()!=0 || in.readUnsignedByte()!=0) throw new IOException("SOCKS connect rejected");
                int kind=in.readUnsignedByte(); int len=kind==1?4:kind==4?16:kind==3?in.readUnsignedByte():-1;
                if(len<0) throw new IOException("Invalid SOCKS response");
                byte[] address=new byte[len+2]; in.readFully(address);
            }
            socket.setSoTimeout(6000);
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
