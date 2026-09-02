package com.ghajarvpn.browser.media

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Receives only public media metadata discovered in the current document. */
class BrowserMediaBridge(private val deliver: (MediaCandidate) -> Unit) {
    @JavascriptInterface
    fun found(value: String) {
        val parsed = runCatching {
            val json = JSONObject(value)
            val base = MediaSourceResolver.resolve(json.optString("url"), json.optString("type")) ?: return
            val tracks = json.optJSONArray("tracks")
            val subtitles = (0 until (tracks?.length() ?: 0)).mapNotNull { i ->
                val item = tracks?.optJSONObject(i) ?: return@mapNotNull null
                val source = MediaSourceResolver.resolve(item.optString("url"))?.url ?: return@mapNotNull null
                SubtitleCandidate(source, item.optString("label"), item.optString("lang"))
            }
            base.copy(title = json.optString("title").take(200), subtitles = subtitles)
        }.getOrNull() ?: return
        deliver(parsed)
    }

    companion object {
        val DISCOVERY_SCRIPT = """
            (() => {
              const seen = new Set();
              const send = (url, type, video) => {
                if (!url || seen.has(url) || /^(blob|data|file):/i.test(url)) return;
                seen.add(url);
                const tracks = video ? Array.from(video.querySelectorAll('track[kind="subtitles"],track[kind="captions"]')).map(t => ({url:new URL(t.src,location.href).href,label:t.label||'',lang:t.srclang||''})) : [];
                try { GhajarMedia.found(JSON.stringify({url:new URL(url,location.href).href,type:type||'',title:document.title||'',tracks})); } catch (_) {}
              };
              document.querySelectorAll('video').forEach(v => {
                send(v.currentSrc || v.src, v.getAttribute('type') || '', v);
                v.querySelectorAll('source').forEach(s => send(s.src, s.type, v));
                v.addEventListener('loadedmetadata', () => send(v.currentSrc || v.src, '', v), {once:true});
              });
              performance.getEntriesByType('resource').forEach(e => { if (/\\.(mp4|webm|m3u8|mpd)(?:$|[?#])/i.test(e.name)) send(e.name,'',null); });
            })()
        """.trimIndent()
    }
}
