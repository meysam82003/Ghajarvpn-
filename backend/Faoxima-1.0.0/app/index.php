<?php


declare(strict_types=1);


header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Expires: 0');

$scriptName = $_SERVER['SCRIPT_NAME'] ?? '';
$scriptDir = str_replace('\\', '/', dirname($scriptName));
if ($scriptDir === '.' || $scriptDir === '') {
    $scriptDir = '';
} elseif ($scriptDir !== '/') {
    $scriptDir = '/' . ltrim($scriptDir, '/');
    $scriptDir = rtrim($scriptDir, '/');
} else {
    $scriptDir = '/';
}
$basename = $scriptDir === '' ? '/' : $scriptDir;
$prefix = $basename === '/' ? '/' : $basename . '/';
$assetPrefix = $prefix;

$rootForApi = $basename === '/' ? '/' : rtrim(dirname($basename), '/');
if ($rootForApi === '' || $rootForApi === '.') {
    $rootForApi = '/';
}
$apiPath = $rootForApi === '/' ? '/api' : $rootForApi . '/api';

$forwardedProto = $_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '';
if (is_string($forwardedProto) && $forwardedProto !== '') {
    $scheme = explode(',', $forwardedProto)[0];
} elseif (!empty($_SERVER['REQUEST_SCHEME'])) {
    $scheme = $_SERVER['REQUEST_SCHEME'];
} else {
    $https = $_SERVER['HTTPS'] ?? '';
    $scheme = (!empty($https) && $https !== 'off') ? 'https' : 'http';
}
$host = $_SERVER['HTTP_HOST'] ?? 'localhost';
$apiUrl = rtrim($scheme . '://' . $host, '/') . $apiPath;

$brandAppVersion = trim((string)@file_get_contents(__DIR__ . '/version')) ?: '1.0.0';


const FX_DEFAULT_BRAND_NAME = 'faoxima';
const FX_DEFAULT_LOGO_URL = 'https://avatars.githubusercontent.com/u/238855591?s=400&u=059d14b3c8c0993bd7211e6fc4bd7d297df4da35&v=4';

$brandName = FX_DEFAULT_BRAND_NAME;
$brandMark = 'M';
$brandTitle = '';
$brandLogoUrl = '';
$brandLogoState = 'default';
$brandAccent = '';
$brandMode = 'dark';



if (is_file(__DIR__ . '/../config.php') && is_file(__DIR__ . '/../function.php')) {
    @require_once __DIR__ . '/../config.php';
    @require_once __DIR__ . '/../function.php';
    if (isset($GLOBALS['pdo']) && $GLOBALS['pdo'] instanceof PDO && function_exists('select')) {
        try {
            $nameRow = select('shopSetting', '*', 'Namevalue', 'brand_name', 'select');
            $markRow = select('shopSetting', '*', 'Namevalue', 'brand_mark', 'select');
            $titleRow = select('shopSetting', '*', 'Namevalue', 'brand_title', 'select');
            $logoRow = select('shopSetting', '*', 'Namevalue', 'brand_logo', 'select');
            $logoStateRow = select('shopSetting', '*', 'Namevalue', 'brand_logo_state', 'select');
            $accentRow = select('shopSetting', '*', 'Namevalue', 'brand_accent', 'select');
            $qrDisabledRow = select('shopSetting', '*', 'Namevalue', 'qr_disabled', 'select');
            if (is_array($accentRow) && isset($accentRow['value']) && preg_match('/^#[0-9a-fA-F]{6}$/', (string)$accentRow['value'])) {
                $brandAccent = strtolower((string)$accentRow['value']);
            }
            if (is_array($nameRow) && isset($nameRow['value']) && $nameRow['value'] !== '') {
                $brandName = (string)$nameRow['value'];
            }
            if (is_array($markRow) && isset($markRow['value']) && $markRow['value'] !== '') {
                $brandMark = (string)$markRow['value'];
            }
            if (is_array($titleRow) && isset($titleRow['value']) && $titleRow['value'] !== '') {
                $brandTitle = (string)$titleRow['value'];
            }
            if (is_array($logoRow) && isset($logoRow['value']) && $logoRow['value'] !== '') {
                $logoBasename = basename((string)$logoRow['value']);
                $logoPath = __DIR__ . '/assets/branding/' . $logoBasename;
                if (is_file($logoPath)) {
                    $brandLogoUrl = $assetPrefix . 'assets/branding/' . $logoBasename . '?v=' . @filemtime($logoPath);
                }
            }
            if (is_array($logoStateRow) && isset($logoStateRow['value'])) {
                $brandLogoState = (string)$logoStateRow['value'];
            }
            if (is_array($qrDisabledRow) && ((string)($qrDisabledRow['value'] ?? '0')) === '1') {
                $qrEnabled = false;
            }
        } catch (\Throwable $rxBrandLoadError) {

        }
    }
}


if (!in_array($brandLogoState, ['default', 'custom', 'initials'], true)) $brandLogoState = 'default';

// A custom logo file always wins the state; if it's missing, fall back to initials
// (never default) once the user has left the DEFAULT state.
if ($brandLogoUrl !== '') {
    $brandLogoState = 'custom';
} elseif ($brandLogoState === 'custom') {
    $brandLogoState = 'initials';
}

$brandIsDefaultLogo = ($brandLogoState === 'default');
if ($brandIsDefaultLogo) $brandLogoUrl = FX_DEFAULT_LOGO_URL;

$version = $brandAppVersion;
$qrEnabled = $qrEnabled ?? true;

$hardR = preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['_r'] ?? ''));
$versionSafe = preg_replace('/[^A-Za-z0-9._-]/', '', (string)$version);
function fx_asset_v(string $absPath, string $versionSafe, string $hardR): string {
    $stamp = (string) @filemtime($absPath);
    return $versionSafe . '.' . $stamp . ($hardR !== '' ? '.' . $hardR : '');
}

$config = [
    'basename'    => $basename,
    'prefix'      => $prefix,
    'apiUrl'      => $apiUrl,
    'assetPrefix' => $assetPrefix,
    'version'     => $version,
    'qrEnabled'   => $qrEnabled,
    'forceDark'   => ($brandMode === 'dark'),
    'brand'       => [
        'name'            => $brandName,
        'mark'            => $brandMark,
        'title'           => $brandTitle,
        'logo_url'        => $brandLogoUrl,
        'avatar_state'    => $brandLogoState,
        'is_default_logo' => $brandIsDefaultLogo,
        'accent'          => $brandAccent,
        'mode'            => $brandMode,
    ],
];

$sdkLocal    = htmlspecialchars($assetPrefix . 'js/telegram-web-app.js?v=' . fx_asset_v(__DIR__ . '/js/telegram-web-app.js', $versionSafe, $hardR), ENT_QUOTES);
$sdkFallback = 'https://telegram.org/js/telegram-web-app.js';
$cssUrl      = htmlspecialchars($assetPrefix . 'assets/css/app.css?v=' . fx_asset_v(__DIR__ . '/assets/css/app.css', $versionSafe, $hardR), ENT_QUOTES);


$jsUrl       = htmlspecialchars($assetPrefix . 'assets/v1.0.0/app.js?v=' . fx_asset_v(__DIR__ . '/assets/v1.0.0/app.js', $versionSafe, $hardR), ENT_QUOTES);
?>
<!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
    <meta name="theme-color" content="<?php echo $brandMode === 'light' ? '#eef2f8' : '#1b1b1d'; ?>" />
    <style>:root{color-scheme:<?php echo $brandMode; ?>}html,body{background-color:<?php echo $brandMode === 'light' ? '#eef2f8' : '#1b1b1d'; ?>}</style>
    <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate" />
    <meta http-equiv="Pragma" content="no-cache" />
    <meta http-equiv="Expires" content="0" />
    <title>فاکسیما — Faoxima</title>
    <base href="<?php echo htmlspecialchars($prefix, ENT_QUOTES); ?>" />


    <script>
    (function () {
        var THEMES = {
            gold:   { c: '#d4b878', b: '#e2c98c' },
            red:    { c: '#e57373', b: '#ef9a9a' },
            blue:   { c: '#64a8e8', b: '#82bdf3' },
            purple: { c: '#7c5cff', b: '#a98bff' },
            yellow: { c: '#f4d35e', b: '#f8e285' },
            green:  { c: '#7fc987', b: '#9bd5a3' },
            orange: { c: '#f0a868', b: '#f5be8b' }
        };
        function rgba(hex, a) {
            var m = /^#([0-9a-f]{6})$/i.exec(hex);
            if (!m) return hex;
            var n = parseInt(m[1], 16);
            return 'rgba(' + ((n>>16)&255) + ',' + ((n>>8)&255) + ',' + (n&255) + ',' + a + ')';
        }
        function darken(hex, f) {
            var m = /^#([0-9a-f]{6})$/i.exec(hex);
            if (!m) return hex;
            var n = parseInt(m[1], 16);
            return 'rgb(' + Math.round(((n>>16)&255)*f) + ',' + Math.round(((n>>8)&255)*f) + ',' + Math.round((n&255)*f) + ')';
        }
        try {
            function lighten(h,f){var m=/^#([0-9a-f]{6})$/i.exec(h);if(!m)return h;var n=parseInt(m[1],16),r=(n>>16)&255,g=(n>>8)&255,b=n&255;r=Math.round(r+(255-r)*f);g=Math.round(g+(255-g)*f);b=Math.round(b+(255-b)*f);return '#'+((1<<24)+(r<<16)+(g<<8)+b).toString(16).slice(1);}
            var GA = <?php echo $brandAccent !== '' ? ("'" . $brandAccent . "'") : 'null'; ?>;
            var key, t;
            if (GA) { key = 'custom'; t = { c: GA, b: lighten(GA, 0.28) }; }
            else { key = 'purple'; t = THEMES[key] || THEMES.gold; }
            function onAcc(h){var m=/^#([0-9a-f]{6})$/i.exec(h);if(!m)return '#ffffff';var n=parseInt(m[1],16);function lin(v){v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);}var L=0.2126*lin((n>>16)&255)+0.7152*lin((n>>8)&255)+0.0722*lin(n&255);return L>0.45?'#14121d':'#ffffff';}
            var s = document.documentElement.style;
            s.setProperty('--gold', t.c);
            s.setProperty('--gold-bright', t.b);
            s.setProperty('--accent', t.c);
            s.setProperty('--accent-bright', t.b);
            s.setProperty('--on-accent', onAcc(t.c));
            s.setProperty('--gold-soft',   rgba(t.c, 0.10));
            s.setProperty('--gold-soft-2', rgba(t.c, 0.18));
            s.setProperty('--accent-soft',   rgba(t.c, 0.10));
            s.setProperty('--accent-soft-2', rgba(t.c, 0.18));
            s.setProperty('--accent-glow',   rgba(t.c, 0.40));
            s.setProperty('--accent-ink',    darken(t.c, 0.5));
            s.setProperty('--border',        rgba(t.c, 0.12));
            s.setProperty('--border-strong', rgba(t.c, 0.28));
            document.documentElement.dataset.color = key;

            var mode = 'dark';
            document.documentElement.dataset.theme = mode;
            window.__FAOXIMA_MODE__ = mode;
            var mc = document.querySelector('meta[name="theme-color"]');
            if (mc) mc.setAttribute('content', '#1b1b1d');
        } catch (e) {
        }
    })();
</script>

    <link rel="stylesheet" href="<?php echo $cssUrl; ?>" />

    
    <script src="<?php echo $sdkLocal; ?>
" onerror="this.onerror=null;var s=document.createElement('script');s.src='<?php echo $sdkFallback; ?>';document.head.appendChild(s);">
</script>
    <script>
      (function () {
        if (window.Telegram && window.Telegram.WebApp) return;


        try {
          document.write('<scr' + 'ipt src="<?php echo $sdkFallback; ?>"></scr' + 'ipt>');
        } catch (e) {  }
      })();
</script>
    <script>
      (function () {
        var w = window.Telegram && window.Telegram.WebApp;
        if (!w) return;
        try { w.ready(); } catch (e) {}
        try { w.expand(); } catch (e) {}
        try { w.setHeaderColor('#1b1b1d'); } catch (e) {}
        try { w.setBackgroundColor('#1b1b1d'); } catch (e) {}
      })();
</script>

    <script>
      window.__APP_CONFIG__ = <?php echo json_encode($config, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE); ?>;
</script>

    <script>
    /*
     * Browser hand-off.
     *
     * This page authenticates with Telegram's initData, which a plain browser
     * tab never has - so opening the panel outside Telegram showed an
     * unauthenticated page regardless of who opened it. The app now appends a
     * one-time ticket, which is exchanged here for the same session token the
     * Telegram path issues, written to the same sessionStorage key the bundle
     * reads, and then removed from the address bar so it is not left in
     * history or handed to any later referrer.
     *
     * Inside Telegram there is no ticket, so none of this runs and the normal
     * initData flow is untouched.
     */
    window.__FAOXIMA_TICKET_READY__ = (function () {
        var TOKEN_KEY = 'faoxima.token';
        var ticket = '';
        try {
            ticket = new URLSearchParams(window.location.search).get('ticket') || '';
        } catch (e) { ticket = ''; }
        if (!/^[0-9a-f]{64}$/.test(ticket)) return Promise.resolve(false);

        function scrub() {
            try {
                var url = new URL(window.location.href);
                url.searchParams.delete('ticket');
                window.history.replaceState(null, '', url.pathname + (url.search || '') + (url.hash || ''));
            } catch (e) {}
        }

        var cfg = window.__APP_CONFIG__ || {};
        var api = (cfg.apiUrl || '') + '/weblink.php?action=redeem';
        var body = new URLSearchParams();
        body.set('ticket', ticket);

        return fetch(api, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' },
            body: body.toString(),
            cache: 'no-store'
        }).then(function (r) { return r.json().catch(function () { return null; }); })
          .then(function (data) {
              scrub();
              if (data && data.status === true && data.token) {
                  try { sessionStorage.setItem(TOKEN_KEY, data.token); } catch (e) {}
                  return true;
              }
              return false;
          })
          .catch(function () { scrub(); return false; });
    })();
</script>

    <script>
    (function () {
        function escapeHtml(s) {
            var d = document.createElement('div');
            d.textContent = String(s == null ? '' : s);
            return d.innerHTML;
        }

        function showFallback(title, msg, stack) {
            var view = document.getElementById('view');
            if (!view) return;
            view.innerHTML =
                '<div class="empty">' +
                '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" style="width:48px;height:48px;color:#f59e0b">' +
                '<path d="M10.3 3.86l-8.5 14.14a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3l-8.5-14.14a2 2 0 0 0-3.4 0z"/>' +
                '<path d="M12 9v4M12 17v.01"/></svg>' +
                '<h3>' + escapeHtml(title) + '</h3>' +
                '<p class="muted">' + escapeHtml(msg) + '</p>' +
                '<button class="btn btn-primary mt-md" onclick="try{sessionStorage.clear();}catch(e){}location.reload();">تلاش مجدد</button>' +
                (stack ? '<details class="mt-md" style="text-align:start">' +
                  '<summary class="muted mono" style="font-size:11px;cursor:pointer">جزئیات</summary>' +
                  '<pre class="codeblock" style="margin-top:8px;direction:ltr;text-align:start;font-size:11px">' + escapeHtml(stack) + '</pre>' +
                '</details>' : '') +
                '</div>';
        }

        window.addEventListener('error', function (e) {
            var where = (e.filename || '') + ':' + (e.lineno || '?');
            var msg = (e.error && e.error.message) || e.message || 'Script error';
            var stack = (e.error && e.error.stack) || '';
            if (!window.__FAOXIMA_APP_STARTED__) {
                showFallback('خطا در بارگذاری برنامه', msg + ' (' + where + ')', stack);
            }
        }, true);

        window.addEventListener('unhandledrejection', function (e) {
            var r = e.reason;
            var msg = (r && r.message) || String(r || 'Unhandled rejection');
            var stack = (r && r.stack) || '';
            if (!window.__FAOXIMA_APP_STARTED__) {
                showFallback('خطا در بارگذاری برنامه', msg, stack);
            }
        });


        setTimeout(function () {
            if (!window.__FAOXIMA_APP_STARTED__) {
                showFallback('بارگذاری برنامه ناموفق بود',
                    'یکی از فایل‌های JS بارگذاری نشد یا خطای پیوند ماژول داشت.', '');
            }
        }, 6000);
    })();
</script>
</head>
<body>
    <div id="app" class="app-shell">
        <script>
(function () {
    window.__hardReload = function () {
        var btn = document.getElementById('reload-btn');
        if (btn && btn.disabled) return;
        if (btn) {
            btn.disabled = true;
            btn.style.transition = 'opacity .2s';
            btn.style.opacity = '0.4';
            var svg = btn.querySelector('svg');
            if (svg) {
                svg.style.transformOrigin = 'center';
                svg.style.animation = 'hardReloadSpin .7s linear infinite';
            }
        }

        // Toast بازخورد
        try {
            var existing = document.getElementById('__hard-reload-toast');
            if (existing) existing.remove();
            var toast = document.createElement('div');
            toast.id = '__hard-reload-toast';
            toast.style.cssText = 'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);'
                + 'background:rgba(30,30,30,.92);color:#fff;font:13px/1.5 system-ui,sans-serif;'
                + 'padding:8px 18px;border-radius:20px;z-index:99999;pointer-events:none;'
                + 'box-shadow:0 2px 12px rgba(0,0,0,.35);white-space:nowrap;direction:rtl;'
                + 'transition:opacity .3s';
            toast.textContent = 'در حال بروزرسانی…';
            document.body.appendChild(toast);
        } catch (e) {}

        // inject spin keyframe once
        try {
            if (!document.getElementById('__hard-reload-style')) {
                var st = document.createElement('style');
                st.id = '__hard-reload-style';
                st.textContent = '@keyframes hardReloadSpin{to{transform:rotate(360deg)}}';
                document.head.appendChild(st);
            }
        } catch (e) {}

        var tasks = [];

        // پاکسازی sessionStorage
        try { sessionStorage.clear(); } catch (e) {}

        // پاکسازی localStorage با حفظ لاگین و تم
        try {
            var preserveKeys = ['faoxima.theme.accent', 'faoxima.theme.mode', 'faoxima.initData', 'faoxima.initDataUnsafe'];
            var keep = {};
            for (var i = 0; i < preserveKeys.length; i++) {
                try { keep[preserveKeys[i]] = localStorage.getItem(preserveKeys[i]); } catch (e) {}
            }
            try { localStorage.clear(); } catch (e) {}
            for (var k in keep) {
                if (keep[k] !== null) {
                    try { localStorage.setItem(k, keep[k]); } catch (e) {}
                }
            }
        } catch (e) {}

        // پاکسازی Cache API
        if (typeof caches !== 'undefined' && caches && typeof caches.keys === 'function') {
            tasks.push(
                caches.keys().then(function (keys) {
                    return Promise.all(keys.map(function (k) { return caches.delete(k); }));
                }).catch(function () {})
            );
        }

        // unregister Service Worker
        if (navigator.serviceWorker && typeof navigator.serviceWorker.getRegistrations === 'function') {
            tasks.push(
                navigator.serviceWorker.getRegistrations().then(function (regs) {
                    return Promise.all(regs.map(function (r) { return r.unregister(); }));
                }).catch(function () {})
            );
        }

        Promise.all(tasks).catch(function () {}).then(function () {
            try {
                // URL تمیز: حذف ?_r قدیمی، اضافه کردن یکی جدید
                var u = new URL(location.href);
                u.searchParams.delete('_r');
                u.searchParams.set('_r', Date.now().toString(36));
                location.replace(u.toString());
            } catch (e) {
                location.reload(true);
            }
        });

        // پشتیبان: اگر بعد از ۲ ثانیه هنوز reload نشده
        setTimeout(function () {
            try { location.reload(true); } catch (e) {}
        }, 2000);
    };
})();
</script>

        <main id="view" class="view" aria-live="polite">
            <article class="card card-window">
                <header class="card-window-bar">
                    <span class="dots"><span></span><span></span><span></span></span>
                    <span class="window-url">faoxima/loading</span>
                </header>
                <div class="card-body">
                    <div class="skeleton skeleton-row"></div>
                    <div class="skeleton skeleton-row"></div>
                    <div class="skeleton skeleton-row"></div>
                    <p class="muted center mono mt-md" style="font-size:11px">در حال آماده‌سازی…</p>
                </div>
            </article>
        </main>

        <nav class="tabbar">
            <a href="#/" data-route="/" class="tab"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/></svg><span>خانه</span></a>
            <a href="#/services" data-route="/services" class="tab"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3a14 14 0 0 1 0 18"/><path d="M12 3a14 14 0 0 0 0 18"/></svg><span>سرویس‌ها</span></a>
            <a href="#/buy" data-route="/buy" class="tab tab-cta"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v8M8 12h8"/></svg><span>خرید</span></a>
            <a href="#/account" data-route="/account" class="tab"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg><span>حساب</span></a>
        </nav>

        <div id="toast-host" class="toast-host" aria-live="polite" aria-atomic="true"></div>
    </div>

    <script>
    /*
     * Code login, for a plain browser tab with no ticket.
     *
     * The bundle can only authenticate two ways: Telegram's initData, or a
     * bearer already in sessionStorage. A URL typed or pasted into a browser
     * has neither, so it used to dead-end on "open this page from Telegram".
     *
     * The server has had the pieces for this all along - weblink.php's
     * generate/status pair, the same one the Android app uses - and nothing in
     * the front end ever called them. This asks for a code, tells the person
     * to send it to the bot, polls until the bot claims it, then stores the
     * issued session and reloads so the bundle boots signed in.
     *
     * Identity is still only ever established inside Telegram: a code on its
     * own proves nothing until someone with a real account sends it to the bot.
     */
    (function () {
        var TOKEN_KEY = 'faoxima.token';

        function hasTelegram() {
            return !!(window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.initData);
        }
        function hasToken() {
            try { return !!sessionStorage.getItem(TOKEN_KEY); } catch (e) { return false; }
        }
        function esc(v) {
            var d = document.createElement('div');
            d.textContent = String(v == null ? '' : v);
            return d.innerHTML;
        }

        var cfg = window.__APP_CONFIG__ || {};
        var api = (cfg.apiUrl || '') + '/weblink.php';
        var pollTimer = null;
        var deadline = 0;

        function render(body) {
            var view = document.getElementById('view');
            if (view) view.innerHTML = body;
        }

        function panel(code, bot, note) {
            var cmd = '/link ' + code;
            var link = bot ? ('https://t.me/' + encodeURIComponent(bot) + '?start=link_' + encodeURIComponent(code)) : '';
            return '<article class="card"><div class="card-body">' +
                '<h3 style="margin:0 0 6px">ورود با کد</h3>' +
                '<p class="muted" style="margin:0 0 14px">این صفحه بیرون از تلگرام باز شده، پس باید یک‌بار حساب را وصل کنی.</p>' +
                '<pre class="codeblock" style="direction:ltr;text-align:center;font-size:20px;letter-spacing:3px;margin:0 0 12px">' + esc(code) + '</pre>' +
                '<p class="muted" style="margin:0 0 12px">این فرمان را در ربات بفرست:</p>' +
                '<pre class="codeblock" style="direction:ltr;text-align:start;margin:0 0 12px">' + esc(cmd) + '</pre>' +
                '<div style="display:flex;gap:8px;flex-wrap:wrap">' +
                (link ? '<a class="btn btn-primary" href="' + esc(link) + '" target="_blank" rel="noopener">باز کردن ربات</a>' : '') +
                '<button class="btn" id="fx-copy-cmd">کپی فرمان</button>' +
                '<button class="btn" id="fx-restart">کد تازه</button>' +
                '</div>' +
                '<p class="muted mono mt-md" style="font-size:11px" id="fx-link-note">' + esc(note || 'در انتظار تایید در ربات…') + '</p>' +
                '</div></article>';
        }

        function note(text) {
            var el = document.getElementById('fx-link-note');
            if (el) el.textContent = text;
        }

        function wire(code, bot) {
            var copy = document.getElementById('fx-copy-cmd');
            if (copy) copy.onclick = function () {
                var cmd = '/link ' + code;
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    navigator.clipboard.writeText(cmd).then(function () { note('فرمان کپی شد.'); },
                        function () { note('کپی نشد؛ دستی بنویس.'); });
                } else note('کپی پشتیبانی نمی‌شود؛ دستی بنویس.');
            };
            var again = document.getElementById('fx-restart');
            if (again) again.onclick = function () { start(); };
        }

        function poll(sessionToken) {
            if (Date.now() > deadline) {
                note('کد منقضی شد؛ «کد تازه» را بزن.');
                return;
            }
            fetch(api + '?action=status&session_token=' + encodeURIComponent(sessionToken), { cache: 'no-store' })
                .then(function (r) { return r.json().catch(function () { return null; }); })
                .then(function (d) {
                    if (d && d.status === true && d.token) {
                        try { sessionStorage.setItem(TOKEN_KEY, d.token); } catch (e) {}
                        note('وصل شد؛ در حال بازکردن حساب…');
                        window.location.reload();
                        return;
                    }
                    if (d && d.gate) {
                        note('ربات یک مرحلهٔ دیگر می‌خواهد (عضویت یا شماره). آن را در ربات کامل کن.');
                    }
                    pollTimer = setTimeout(function () { poll(sessionToken); }, 2500);
                })
                .catch(function () {
                    pollTimer = setTimeout(function () { poll(sessionToken); }, 4000);
                });
        }

        function start() {
            if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
            render('<article class="card"><div class="card-body">' +
                '<p class="muted center mono">در حال گرفتن کد…</p></div></article>');
            fetch(api + '?action=generate', { method: 'POST', cache: 'no-store' })
                .then(function (r) { return r.json().catch(function () { return null; }); })
                .then(function (d) {
                    if (!d || d.status !== true || !d.code || !d.session_token) {
                        render('<article class="card"><div class="card-body">' +
                            '<h3 style="margin:0 0 8px">کد گرفته نشد</h3>' +
                            '<p class="muted">سرور کد اتصال صادر نکرد. کمی بعد دوباره تلاش کن.</p>' +
                            '<button class="btn btn-primary mt-md" onclick="location.reload()">تلاش مجدد</button>' +
                            '</div></article>');
                        return;
                    }
                    deadline = Date.now() + (Number(d.expires_in || 300) * 1000);
                    render(panel(d.code, d.bot_username || '', null));
                    wire(d.code, d.bot_username || '');
                    poll(d.session_token);
                })
                .catch(function () {
                    render('<article class="card"><div class="card-body">' +
                        '<h3 style="margin:0 0 8px">سرور در دسترس نیست</h3>' +
                        '<p class="muted">اتصال به سرور برقرار نشد.</p>' +
                        '<button class="btn btn-primary mt-md" onclick="location.reload()">تلاش مجدد</button>' +
                        '</div></article>');
                });
        }

        function maybeStart() {
            if (hasTelegram() || hasToken()) return;      // normal paths win
            if (window.__FAOXIMA_APP_STARTED__) return;   // bundle already running
            window.__FAOXIMA_CODE_LOGIN__ = true;
            start();
        }

        // Give the ticket exchange and the Telegram SDK a moment to win first.
        var ready = window.__FAOXIMA_TICKET_READY__ || Promise.resolve(false);
        ready.then(function (ok) {
            if (ok) return;
            setTimeout(maybeStart, 1200);
        }).catch(function () { setTimeout(maybeStart, 1200); });
    })();
</script>

    <script type="module" src="<?php echo $jsUrl; ?>
">
</script>
</body>
</html>


