package net.gozar.plugin.api;

import org.json.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ghajar client schema; never accepts a server, Lua script, path or arbitrary command. */
public final class ShadowQuicProfile {
    private ShadowQuicProfile() {}
    private static Set<String> set(String... values) { return new HashSet<>(Arrays.asList(values)); }
    private static String decode(String s) throws Exception { return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); }
    public static JSONObject parse(String original, String format) throws Exception {
        JSONObject p;
        if ("shadowquic-json".equals(format)) p = new JSONObject(original);
        else if ("shadowquic-uri".equals(format)) {
            URI u = new URI(original.trim());
            if (!("sq".equalsIgnoreCase(u.getScheme()) || "shadowquic".equalsIgnoreCase(u.getScheme())) || u.getHost() == null || u.getRawUserInfo() == null) throw new IllegalArgumentException("Invalid ShadowQUIC URI");
            String[] auth = u.getRawUserInfo().split(":", 2);
            if (auth.length != 2) throw new IllegalArgumentException("Username and password required");
            p = new JSONObject().put("server", u.getHost().replace("[", "").replace("]", "")).put("port", u.getPort() < 0 ? 443 : u.getPort())
                .put("username", decode(auth[0])).put("password", decode(auth[1]));
            Map<String, String> keys = new HashMap<>();
            String[] pairs = {"sni", "sni", "udp_mode", "udpMode", "congestion", "congestion", "mtu", "mtu", "alpn", "alpn", "zero_rtt", "zeroRtt", "tag", "name"};
            for (int i = 0; i < pairs.length; i += 2) keys.put(pairs[i], pairs[i+1]);
            Set<String> seen = new HashSet<>();
            if (u.getRawQuery() != null) for (String part : u.getRawQuery().split("&")) {
                String[] kv = part.split("=", 2); String k = decode(kv[0]);
                if (!keys.containsKey(k) || !seen.add(k)) throw new IllegalArgumentException("Unknown or duplicate ShadowQUIC option: " + k);
                String v = kv.length == 2 ? decode(kv[1]) : "";
                if (k.equals("mtu")) p.put("mtu", Integer.parseInt(v));
                else if (k.equals("zero_rtt")) {
                    if (!set("true", "false", "1", "0").contains(v)) throw new IllegalArgumentException("Invalid zero_rtt");
                    p.put("zeroRtt", v.equals("true") || v.equals("1"));
                } else p.put(keys.get(k), v);
            }
            if (u.getRawFragment() != null) p.put("name", decode(u.getRawFragment()));
            if (u.getRawPath() != null && !u.getRawPath().isEmpty() && !u.getRawPath().equals("/")) throw new IllegalArgumentException("Unexpected URI path");
        } else throw new IllegalArgumentException("Unsupported ShadowQUIC format");
        Set<String> known = set("server", "port", "username", "password", "sni", "udpMode", "congestion", "mtu", "alpn", "zeroRtt", "name");
        for (Iterator<String> it = p.keys(); it.hasNext();) if (!known.contains(it.next())) throw new IllegalArgumentException("Unknown ShadowQUIC field");
        for (String key : Arrays.asList("server", "username", "password", "sni")) {
            Object value = p.get(key);
            if (!(value instanceof String) || ((String)value).trim().isEmpty() || ((String)value).length() > 4096 || ((String)value).indexOf(0) >= 0) throw new IllegalArgumentException("Invalid " + key);
        }
        String server = p.getString("server"), sni = p.getString("sni");
        if (!server.matches("[A-Za-z0-9._:%-]+") || !sni.matches("(?i)[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) throw new IllegalArgumentException("Invalid host/SNI");
        int port = exactInt(p, "port", 443), mtu = exactInt(p, "mtu", 1280);
        if (port < 1 || port > 65535 || mtu < 1200 || mtu > 1500) throw new IllegalArgumentException("Invalid port/MTU");
        if (!set("bbr", "cubic", "new-reno").contains(p.optString("congestion", "bbr"))) throw new IllegalArgumentException("Invalid congestion control");
        if (!set("datagram", "stream").contains(p.optString("udpMode", "datagram"))) throw new IllegalArgumentException("Invalid UDP mode");
        if (p.has("zeroRtt") && !(p.get("zeroRtt") instanceof Boolean)) throw new IllegalArgumentException("Invalid zeroRtt");
        for (String alpn : p.optString("alpn", "h3").split(",", -1)) if (alpn.trim().isEmpty() || alpn.length() > 255 || alpn.chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException("Invalid ALPN");
        return p;
    }
    private static int exactInt(JSONObject p, String key, int fallback) throws Exception {
        return p.has(key) ? Integer.parseInt(p.get(key).toString()) : fallback;
    }
    public static String config(JSONObject p, int socksPort) throws Exception {
        p = parse(p.toString(), "shadowquic-json");
        if (socksPort < 1024 || socksPort > 65535) throw new IllegalArgumentException("Invalid SOCKS port");
        String host = p.getString("server"); if (host.contains(":")) host = "[" + host + "]";
        JSONObject out = new JSONObject().put("type", "shadowquic").put("tag", "proxy")
            .put("addr", host + ":" + p.optInt("port", 443)).put("username", p.getString("username")).put("password", p.getString("password"))
            .put("server-name", p.getString("sni")).put("alpn", new JSONArray(Arrays.asList(p.optString("alpn", "h3").split(","))))
            .put("congestion-control", p.optString("congestion", "bbr")).put("zero-rtt", p.optBoolean("zeroRtt", false))
            .put("over-stream", p.optString("udpMode", "datagram").equals("stream"))
            .put("min-mtu", p.optInt("mtu", 1280)).put("initial-mtu", p.optInt("mtu", 1280));
        // JSON is a YAML subset accepted by the pinned serde-saphyr loader.
        return new JSONObject().put("inbounds", new JSONArray().put(new JSONObject().put("type", "socks").put("tag", "local").put("bind-addr", "127.0.0.1:" + socksPort)))
            .put("outbounds", new JSONArray().put(out)).put("router", new JSONObject().put("default-outbound", "proxy")).put("log-level", "info").toString();
    }
}
