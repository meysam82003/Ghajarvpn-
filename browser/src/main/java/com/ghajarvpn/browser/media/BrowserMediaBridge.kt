package com.ghajarvpn.browser.media

import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Receives only public media metadata discovered in the current document. */
class BrowserMediaBridge(private val deliver: (MediaCandidate) -> Unit) {
    @JavascriptInterface
    fun found(value: String) {
        if (value.length > MAX_BRIDGE_MESSAGE_LENGTH) return
        val parsed = runCatching {
            val json = JSONObject(value)
            val base = MediaSourceResolver.resolve(json.optString("url"), json.optString("type")) ?: return
            val tracks = json.optJSONArray("tracks")
            val subtitles = (0 until minOf(tracks?.length() ?: 0, MAX_SUBTITLE_TRACKS)).mapNotNull { i ->
                val item = tracks?.optJSONObject(i) ?: return@mapNotNull null
                val source = MediaSourceResolver.resolve(item.optString("url"))?.url ?: return@mapNotNull null
                SubtitleCandidate(source, item.optString("label"), item.optString("lang"))
            }.distinctBy(SubtitleCandidate::url)
            base.copy(title = json.optString("title").take(200), subtitles = subtitles)
        }.getOrNull() ?: return
        deliver(parsed)
    }

    companion object {
        internal const val MAX_BRIDGE_MESSAGE_LENGTH = 64 * 1024
        internal const val MAX_SUBTITLE_TRACKS = 16

        val DISCOVERY_SCRIPT = """
            (() => {
              if (window.__ghajarMediaObserverInstalled) return;
              window.__ghajarMediaObserverInstalled = true;
              const seen = new Set(), watched = new WeakSet();
              const absolute = value => {
                try {
                  const url = new URL(value, location.href).href;
                  return url.length <= 8192 && !/^(blob|data|file):/i.test(url) ? url : '';
                } catch (_) { return ''; }
              };
              const send = (url, type, video) => {
                const resolved = absolute(url);
                if (!resolved) return;
                const tracks = video ? Array.from(video.querySelectorAll('track[kind="subtitles"],track[kind="captions"]')).slice(0,16).map(t => ({url:absolute(t.src),label:(t.label||'').slice(0,100),lang:(t.srclang||'').slice(0,35)})).filter(t => t.url) : [];
                const signature = resolved + '|' + tracks.map(t => t.url).join('|');
                if (seen.has(signature)) return;
                if (seen.size >= 128) seen.clear();
                seen.add(signature);
                try { GhajarMedia.found(JSON.stringify({url:resolved,type:(type||'').slice(0,100),title:(document.title||'').slice(0,200),tracks})); } catch (_) {}
              };
              const inspectVideo = video => {
                if (!video || video.tagName !== 'VIDEO') return;
                const report = () => send(video.currentSrc || video.src, video.getAttribute('type') || '', video);
                report();
                video.querySelectorAll('source').forEach(source => send(source.src, source.type, video));
                if (watched.has(video)) return;
                watched.add(video);
                video.addEventListener('play', report);
                video.addEventListener('loadedmetadata', report);
                video.addEventListener('durationchange', report);
              };
              const inspectNode = node => {
                if (!node || node.nodeType !== Node.ELEMENT_NODE) return;
                inspectVideo(node);
                node.querySelectorAll && node.querySelectorAll('video').forEach(inspectVideo);
              };
              document.querySelectorAll('video').forEach(inspectVideo);
              const inspectResource = entry => { if (/\\.(mp4|webm|m3u8|mpd)(?:$|[?#])/i.test(entry.name)) send(entry.name,'',null); };
              performance.getEntriesByType('resource').forEach(inspectResource);
              try {
                const performanceObserver = new PerformanceObserver(list => list.getEntries().forEach(inspectResource));
                performanceObserver.observe({type:'resource', buffered:true});
              } catch (_) {}
              const mutationObserver = new MutationObserver(mutations => mutations.forEach(mutation => {
                mutation.addedNodes.forEach(inspectNode);
                const target = mutation.target;
                inspectVideo(target && target.tagName === 'SOURCE' ? target.parentElement : target);
              }));
              mutationObserver.observe(document.documentElement || document, {subtree:true, childList:true, attributes:true, attributeFilter:['src','type']});
            })()
        """.trimIndent()
    }
}
