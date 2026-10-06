<?php

declare(strict_types=1);

/**
 * Subscription relay for desktop VPN clients.
 *
 * Happ (and others) on Windows/macOS draw a server's flag only from a flag
 * emoji at the very start of its name, and Windows has no flag glyphs to
 * draw one anywhere else (it prints "IR"). Store panels often name servers
 * "🏪 Shop | 🇮🇷 Tunnel…", so desktop clients showed a globe. This relay
 * serves the same subscription with each flag moved to the front; usage,
 * expiry and title headers pass through untouched.
 *
 *   POST ?action=sign  {url}  (Bearer)  → {status, url: relay address}
 *   GET  ?u=<base64url>&k=<signature>    → the subscription, names fixed
 *
 * Only signed addresses are relayed, only to public hosts. Lives inside
 * pwa/ and only reads the bot's own files.
 */

@ini_set('display_errors', '0');

function __sub_secret(): string
{
    // Derived from this server's own database credentials: stable, never sent anywhere.
    global $passworddb, $usernamedb, $dbname;
    return hash('sha256', 'ghajar-sub-relay|' . ($passworddb ?? '') . '|' . ($usernamedb ?? '') . '|' . ($dbname ?? ''), true);
}

function __sub_b64(string $raw): string
{
    return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
}

function __sub_unb64(string $s): string
{
    $s = strtr($s, '-_', '+/');
    return (string)base64_decode($s . str_repeat('=', (4 - strlen($s) % 4) % 4), true);
}

function __sub_sign(string $url): string
{
    return substr(__sub_b64(hash_hmac('sha256', $url, __sub_secret(), true)), 0, 22);
}

/** A public http(s) address only: no credentials, no private or reserved IPs. */
function __sub_public_target(string $url): ?array
{
    $p = parse_url($url);
    if (!$p || !in_array(strtolower($p['scheme'] ?? ''), ['http', 'https'], true) || empty($p['host']) || isset($p['user'])) {
        return null;
    }
    $host = $p['host'];
    $ips = filter_var($host, FILTER_VALIDATE_IP) ? [$host] : (gethostbynamel($host) ?: []);
    if (!$ips) {
        return null;
    }
    foreach ($ips as $ip) {
        if (!filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE)) {
            return null;
        }
    }
    $port = (int)($p['port'] ?? (strtolower($p['scheme']) === 'https' ? 443 : 80));
    return ['host' => $host, 'port' => $port, 'ip' => $ips[0]];
}

/** "🏪 Shop | 🇮🇷 Tunnel" → "🇮🇷 🏪 Shop | Tunnel". */
function ghajar_flag_first(string $name): string
{
    if (!preg_match('/[\x{1F1E6}-\x{1F1FF}]{2}/u', $name, $m, PREG_OFFSET_CAPTURE) || $m[0][1] === 0) {
        return $name;
    }
    $flag = $m[0][0];
    $rest = substr_replace($name, '', $m[0][1], strlen($flag));
    $rest = trim((string)preg_replace('/\s{2,}/u', ' ', $rest));
    return $flag . ' ' . $rest;
}

function ghajar_fix_link(string $line): string
{
    $trim = trim($line);
    if ($trim === '') {
        return $line;
    }
    if (stripos($trim, 'vmess://') === 0) {
        $json = json_decode(__sub_unb64(trim(substr($trim, 8))) ?: (string)base64_decode(substr($trim, 8)), true);
        if (is_array($json) && isset($json['ps']) && is_string($json['ps'])) {
            $json['ps'] = ghajar_flag_first($json['ps']);
            return 'vmess://' . base64_encode(json_encode($json, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        }
        return $trim;
    }
    $hash = strpos($trim, '#');
    if ($hash === false || strpos($trim, '://') === false) {
        return $trim;
    }
    $name = rawurldecode(substr($trim, $hash + 1));
    return substr($trim, 0, $hash + 1) . rawurlencode(ghajar_flag_first($name));
}

function ghajar_fix_body(string $body): string
{
    $lines = preg_split('/\r\n|\r|\n/', $body) ?: [];
    if (strpos($body, '://') !== false) {
        return implode("\n", array_map('ghajar_fix_link', $lines));
    }
    $packed = preg_replace('/\s+/', '', $body);
    $decoded = base64_decode((string)$packed, true);
    if ($decoded === false) {
        $decoded = __sub_unb64((string)$packed);
    }
    if ($decoded !== '' && strpos($decoded, '://') !== false && mb_check_encoding($decoded, 'UTF-8')) {
        $fixed = implode("\n", array_map('ghajar_fix_link', preg_split('/\r\n|\r|\n/', $decoded) ?: []));
        return base64_encode($fixed);
    }
    return $body; // Clash / sing-box / JSON formats pass through as they are.
}

$action = (string)($_GET['action'] ?? '');

if ($action === 'sign') {
    if (!defined('FAOXIMA_SKIP_BOTAPI_ROUTER')) {
        define('FAOXIMA_SKIP_BOTAPI_ROUTER', true);
    }
    ob_start();
    $emit = function (int $code, array $payload) {
        while (ob_get_level() > 0) {
            @ob_end_clean();
        }
        http_response_code($code);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    };
    try {
        require_once __DIR__ . '/../api/lib/Bootstrap.php';
        $bearer = FaoximaAuth::extractBearerToken();
        $user = $bearer !== null ? FaoximaAuth::userFromToken($bearer) : null;
        if (!is_array($user)) {
            $emit(401, ['status' => false, 'msg' => 'Unauthorized']);
        }
        $body = json_decode((string)file_get_contents('php://input', false, null, 0, 8192), true);
        $url = is_array($body) ? trim((string)($body['url'] ?? '')) : '';
        if ($url === '' || strlen($url) > 2000 || __sub_public_target($url) === null) {
            $emit(400, ['status' => false, 'msg' => 'invalid subscription']);
        }
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https') ? 'https' : 'http';
        $self = $scheme . '://' . ($_SERVER['HTTP_HOST'] ?? '') . ($_SERVER['SCRIPT_NAME'] ?? '/sub.php');
        $emit(200, ['status' => true, 'url' => $self . '?u=' . __sub_b64($url) . '&k=' . __sub_sign($url)]);
    } catch (Throwable $e) {
        error_log('[GhajarSub] ' . $e->getMessage());
        $emit(500, ['status' => false, 'msg' => 'unavailable']);
    }
}

// ------------------------------------------------------------------ relay
require_once __DIR__ . '/../config.php';
$url = __sub_unb64((string)($_GET['u'] ?? ''));
if ($url === '' || !hash_equals(__sub_sign($url), (string)($_GET['k'] ?? ''))) {
    http_response_code(403);
    exit('forbidden');
}
$target = __sub_public_target($url);
if ($target === null) {
    http_response_code(400);
    exit('invalid');
}

$passHeaders = ['subscription-userinfo', 'profile-title', 'profile-update-interval', 'content-disposition', 'profile-web-page-url',
    'support-url', 'announce', 'announce-url', 'routing', 'routing-enable', 'hide-settings', 'content-type'];
$headers = [];
$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_FOLLOWLOCATION => false,
    CURLOPT_TIMEOUT => 20,
    CURLOPT_CONNECTTIMEOUT => 8,
    CURLOPT_MAXFILESIZE => 4 * 1024 * 1024,
    CURLOPT_RESOLVE => [$target['host'] . ':' . $target['port'] . ':' . $target['ip']],
    CURLOPT_HTTPHEADER => [
        'User-Agent: ' . substr((string)($_SERVER['HTTP_USER_AGENT'] ?? 'Happ'), 0, 300),
        'Accept: ' . substr((string)($_SERVER['HTTP_ACCEPT'] ?? '*/*'), 0, 200),
    ],
    CURLOPT_HEADERFUNCTION => function ($c, $line) use (&$headers, $passHeaders) {
        $parts = explode(':', $line, 2);
        if (count($parts) === 2 && in_array(strtolower(trim($parts[0])), $passHeaders, true)) {
            $headers[] = trim($parts[0]) . ': ' . trim($parts[1]);
        }
        return strlen($line);
    },
]);
$body = curl_exec($ch);
$code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);
if (!is_string($body) || $code < 200 || $code >= 300) {
    http_response_code(502);
    exit('upstream unavailable');
}
foreach ($headers as $h) {
    header($h);
}
header('Cache-Control: no-store');
echo ghajar_fix_body($body);
