<?php
/**
 * Sends due notices to every installed Ghajar web app (run every minute).
 *
 * For each account with a subscribed device it reads the very feed the
 * Android app polls (notices.php?action=feed) with that account's own token,
 * so a phone gets the same notices the app gets: volume and time warnings,
 * shop announcements and discount codes, payment results, ticket replies and
 * broadcast messages. What was pushed is reported back with `shown`, which
 * starts the server's repeat clock exactly as the app's posting does.
 *
 * Lives inside pwa/ and only reads the bot's config: nothing of the bot is
 * changed. Run it from the host's cron, either way:
 *   php /path/to/Ghajarvpn/pwa/push-cron.php
 *   curl -s https://<host>/…/Ghajarvpn/pwa/push-cron.php
 */
@ini_set('display_errors', '0');
@set_time_limit(60);
ignore_user_abort(true);
if (PHP_SAPI !== 'cli') {
    header('Content-Type: text/plain; charset=utf-8');
    header('Cache-Control: no-store');
}
require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/push-lib.php';
$pdo = $GLOBALS['pdo'] ?? ($pdo ?? null);
if (!($pdo instanceof PDO)) {
    echo "no database\n";
    return;
}

$lock = fopen(sys_get_temp_dir() . '/ghajar_webpush.lock', 'c');
if (!$lock || !flock($lock, LOCK_EX | LOCK_NB)) {
    return;
}

/** Optional: GHAJAR_PUSH_API_BASE in config.php, when the server cannot reach its own public address. */
$apiOverride = defined('GHAJAR_PUSH_API_BASE') ? (string)GHAJAR_PUSH_API_BASE : '';
$started = time();

try {
    GhajarWebPush::ensureSchema($pdo);
    $keys = GhajarWebPush::vapidKeys($pdo);
    $users = $pdo->query('SELECT DISTINCT user_id FROM ghajar_push_subscriptions ORDER BY user_id')->fetchAll(PDO::FETCH_COLUMN);
    foreach ($users as $userId) {
        if (time() - $started > 50) {
            break;
        }
        $userId = (int)$userId;
        $tokenStmt = $pdo->prepare('SELECT token FROM user WHERE id = ?');
        $tokenStmt->execute([$userId]);
        $token = (string)$tokenStmt->fetchColumn();
        $subsStmt = $pdo->prepare('SELECT * FROM ghajar_push_subscriptions WHERE user_id = ?');
        $subsStmt->execute([$userId]);
        $subs = $subsStmt->fetchAll(PDO::FETCH_ASSOC);
        if ($token === '' || !$subs) {
            continue;
        }
        $base = $apiOverride !== '' ? rtrim($apiOverride, '/') . '/' : (string)$subs[0]['api_base'];
        if ($base === '') {
            continue;
        }
        $feed = ghajar_push_http($base . 'notices.php?action=feed&client=app', $token);
        if (!is_array($feed) || ($feed['status'] ?? false) !== true || !is_array($feed['notices'] ?? null)) {
            continue;
        }
        $unseen = (int)($feed['unseen'] ?? 0);
        $pushedIds = [];
        foreach ($feed['notices'] as $row) {
            if (!is_array($row) || ($row['should_float'] ?? true) === false) {
                continue;
            }
            $body = trim((string)($row['body'] ?? ''));
            $noticeId = (int)($row['id'] ?? 0);
            if ($body === '' || $noticeId === 0) {
                continue;
            }
            $kind = (string)($row['kind'] ?? '');
            $repeat = max(0, (int)($row['repeat_after'] ?? 0));
            $fingerprint = substr(hash('sha256', ($row['title'] ?? '') . "\n" . $body), 0, 24);
            $sentStmt = $pdo->prepare('SELECT fingerprint, sent_at FROM ghajar_push_sent WHERE user_id = ? AND notice_id = ?');
            $sentStmt->execute([$userId, $noticeId]);
            $prev = $sentStmt->fetch(PDO::FETCH_ASSOC);
            if ($prev) {
                $sameText = $prev['fingerprint'] === $fingerprint;
                // Once per text; a repeating warning again only after its interval
                // (and never more than once an hour), as the server decides it is due.
                if ($sameText && ($repeat <= 0 || time() - (int)$prev['sent_at'] < max($repeat, 3600))) {
                    continue;
                }
            }
            $channel = ($kind === 'shop_status') ? 'important' : (($kind === 'service_time' || $kind === 'service_volume') ? 'service' : 'general');
            $titles = ['service_time' => 'مهلت سرویس رو به پایان است', 'service_volume' => 'حجم سرویس رو به پایان است', 'shop_status' => 'وضعیت فروشگاه'];
            $title = trim((string)($row['title'] ?? '')) ?: ($titles[$kind] ?? 'اعلان قاجار وی پی ان');
            $payload = json_encode([
                'id' => 'notice:' . $noticeId, 'title' => $title, 'body' => $body, 'kind' => $kind, 'channel' => $channel,
                'action' => (string)($row['action'] ?? 'none'), 'action_ref' => (string)($row['action_ref'] ?? ''), 'unseen' => $unseen,
            ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            $delivered = false;
            foreach ($subs as $sub) {
                $prefs = json_decode((string)$sub['prefs'], true) ?: [];
                if (isset($prefs[$channel]) && $prefs[$channel] === false) {
                    continue;
                }
                try {
                    $code = GhajarWebPush::send($sub, $payload, $keys, 'https://t.me/Ghajarvpn', $repeat > 0 ? $repeat : 86400,
                        $channel === 'general' ? 'normal' : 'high');
                } catch (Throwable $e) {
                    $code = 0;
                }
                if ($code === 404 || $code === 410) {
                    $pdo->prepare('DELETE FROM ghajar_push_subscriptions WHERE id = ?')->execute([$sub['id']]);
                } elseif ($code >= 200 && $code < 300) {
                    $delivered = true;
                    $pdo->prepare('UPDATE ghajar_push_subscriptions SET last_ok_at = ?, fail_count = 0 WHERE id = ?')->execute([time(), $sub['id']]);
                } else {
                    $pdo->prepare('UPDATE ghajar_push_subscriptions SET fail_count = fail_count + 1 WHERE id = ?')->execute([$sub['id']]);
                }
            }
            if ($delivered) {
                $pdo->prepare('INSERT INTO ghajar_push_sent (user_id, notice_id, fingerprint, sent_at) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE fingerprint = VALUES(fingerprint), sent_at = VALUES(sent_at)')
                    ->execute([$userId, $noticeId, $fingerprint, time()]);
                if ($noticeId > 0) {
                    $pushedIds[] = $noticeId;
                }
            }
        }
        if ($pushedIds) {
            ghajar_push_http($base . 'notices.php?action=shown&client=app', $token, ['ids' => $pushedIds]);
        }
    }
    // Devices that have failed for days are dropped.
    $pdo->exec('DELETE FROM ghajar_push_subscriptions WHERE fail_count >= 200');
    $pdo->prepare('DELETE FROM ghajar_push_sent WHERE sent_at < ?')->execute([time() - 60 * 86400]);
} catch (Throwable $e) {
    error_log('[webpush] ' . $e->getMessage());
} finally {
    flock($lock, LOCK_UN);
}
echo "OK\n";

function ghajar_push_http(string $url, string $token, ?array $body = null): ?array
{
    $ch = curl_init($url);
    $headers = ['Accept: application/json', 'Authorization: Bearer ' . $token, 'X-Ghajar-Client: app'];
    if ($body !== null) {
        $headers[] = 'Content-Type: application/json';
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body));
    }
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 15, CURLOPT_CONNECTTIMEOUT => 6, CURLOPT_HTTPHEADER => $headers]);
    $raw = curl_exec($ch);
    curl_close($ch);
    $json = is_string($raw) ? json_decode($raw, true) : null;
    return is_array($json) ? $json : null;
}
