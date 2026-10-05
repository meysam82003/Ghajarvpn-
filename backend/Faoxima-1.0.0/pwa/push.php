<?php

declare(strict_types=1);

/**
 * Web Push registration for the Ghajar PWA.
 *
 *   GET  ?action=key          the VAPID public key (no account needed)
 *   POST ?action=subscribe    {subscription:{endpoint,keys:{p256dh,auth}}, prefs, ua, old_endpoint}
 *   POST ?action=unsubscribe  {endpoint}
 *   POST ?action=test         sends one test notification to this account's devices
 *
 * Lives inside pwa/ and only reads the bot's own files: nothing of the bot
 * is changed. Every write needs the same bearer token the app and the PWA
 * use for the shop. The notices themselves are sent by push-cron.php from
 * the same feed the Android app reads (notices.php).
 */

if (!defined('FAOXIMA_SKIP_BOTAPI_ROUTER')) {
    define('FAOXIMA_SKIP_BOTAPI_ROUTER', true);
}

ob_start();
@ini_set('display_errors', '0');

function __push_emit(int $http, array $payload): void
{
    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    if (!headers_sent()) {
        http_response_code($http);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store, max-age=0');
        header('X-Content-Type-Options: nosniff');
    }
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}

try {
    require_once __DIR__ . '/../api/lib/Bootstrap.php';
    require_once __DIR__ . '/push-lib.php';

    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    ob_start();

    $pdo = FaoximaDb::pdo();
    $action = (string)($_GET['action'] ?? '');

    if ($action === 'key') {
        $keys = GhajarWebPush::vapidKeys($pdo);
        __push_emit(200, ['status' => true, 'key' => $keys['public']]);
        exit;
    }

    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        __push_emit(405, ['status' => false, 'msg' => 'POST required']);
        exit;
    }
    $bearer = FaoximaAuth::extractBearerToken();
    $user = $bearer !== null ? FaoximaAuth::userFromToken($bearer) : null;
    if (!is_array($user) || (int)($user['id'] ?? 0) <= 0) {
        __push_emit(401, ['status' => false, 'msg' => 'Unauthorized']);
        exit;
    }
    $userId = (int)$user['id'];
    $raw = (string)file_get_contents('php://input', false, null, 0, 16384);
    $body = json_decode($raw, true);
    if (!is_array($body)) {
        $body = [];
    }
    GhajarWebPush::ensureSchema($pdo);

    if ($action === 'subscribe') {
        $sub = $body['subscription'] ?? null;
        $endpoint = is_array($sub) ? (string)($sub['endpoint'] ?? '') : '';
        $p256 = is_array($sub) ? (string)($sub['keys']['p256dh'] ?? '') : '';
        $auth = is_array($sub) ? (string)($sub['keys']['auth'] ?? '') : '';
        if (!GhajarWebPush::validEndpoint($endpoint) || strlen(GhajarWebPush::b64urlDecode($p256)) !== 65 || strlen(GhajarWebPush::b64urlDecode($auth)) < 16) {
            __push_emit(400, ['status' => false, 'msg' => 'invalid subscription']);
            exit;
        }
        $prefs = [];
        foreach (['general', 'service', 'important'] as $k) {
            if (isset($body['prefs'][$k])) {
                $prefs[$k] = (bool)$body['prefs'][$k];
            }
        }
        // Where the cron reaches the notice feed for this device: the bot's
        // api/ beside this folder, as the device reached it. No host is configured.
        $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https') ? 'https' : 'http';
        $apiBase = $scheme . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . rtrim(str_replace('\\', '/', dirname(dirname($_SERVER['SCRIPT_NAME'] ?? '/pwa/push.php'))), '/') . '/api/';
        if (!empty($body['old_endpoint']) && is_string($body['old_endpoint'])) {
            $pdo->prepare('DELETE FROM ghajar_push_subscriptions WHERE endpoint_hash = ? AND user_id = ?')
                ->execute([hash('sha256', $body['old_endpoint']), $userId]);
        }
        $existingPrefs = $pdo->prepare('SELECT prefs FROM ghajar_push_subscriptions WHERE endpoint_hash = ?');
        $existingPrefs->execute([hash('sha256', $endpoint)]);
        $old = $existingPrefs->fetchColumn();
        if (!$prefs && is_string($old) && $old !== '') {
            $prefs = json_decode($old, true) ?: [];
        }
        $stmt = $pdo->prepare('INSERT INTO ghajar_push_subscriptions
            (user_id, endpoint, endpoint_hash, p256dh, auth, prefs, api_base, ua, created_at, last_ok_at, fail_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), p256dh = VALUES(p256dh), auth = VALUES(auth),
              prefs = VALUES(prefs), api_base = VALUES(api_base), ua = VALUES(ua), fail_count = 0');
        $stmt->execute([$userId, $endpoint, hash('sha256', $endpoint), $p256, $auth, json_encode($prefs), substr($apiBase, 0, 500),
            substr((string)($body['ua'] ?? ''), 0, 250), time()]);
        __push_emit(200, ['status' => true]);
        exit;
    }

    if ($action === 'unsubscribe') {
        $endpoint = (string)($body['endpoint'] ?? '');
        $pdo->prepare('DELETE FROM ghajar_push_subscriptions WHERE endpoint_hash = ? AND user_id = ?')
            ->execute([hash('sha256', $endpoint), $userId]);
        __push_emit(200, ['status' => true]);
        exit;
    }

    if ($action === 'test') {
        $keys = GhajarWebPush::vapidKeys($pdo);
        $subs = $pdo->prepare('SELECT * FROM ghajar_push_subscriptions WHERE user_id = ?');
        $subs->execute([$userId]);
        $sent = 0;
        foreach ($subs->fetchAll(PDO::FETCH_ASSOC) as $sub) {
            $payload = json_encode(['id' => 'test:' . time(), 'title' => 'اعلان آزمایشی قاجار', 'body' => 'اعلان‌های این دستگاه درست کار می‌کند.',
                'kind' => 'test', 'channel' => 'general', 'url' => '#/settings/notifications'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            $code = GhajarWebPush::send($sub, $payload, $keys, 'https://t.me/Ghajarvpn', 300, 'high');
            if ($code === 404 || $code === 410) {
                $pdo->prepare('DELETE FROM ghajar_push_subscriptions WHERE id = ?')->execute([$sub['id']]);
            } elseif ($code >= 200 && $code < 300) {
                $sent++;
            }
        }
        __push_emit($sent > 0 ? 200 : 409, ['status' => $sent > 0, 'sent' => $sent, 'msg' => $sent > 0 ? 'sent' : 'no reachable device']);
        exit;
    }

    __push_emit(400, ['status' => false, 'msg' => 'Unknown action']);
} catch (Throwable $e) {
    error_log('[GhajarPush] ' . $e->getMessage());
    __push_emit(500, ['status' => false, 'msg' => 'push unavailable']);
}
