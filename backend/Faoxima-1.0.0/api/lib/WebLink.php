<?php

declare(strict_types=1);

if (class_exists('FaoximaWebLink')) {
    return;
}

/**
 * Lets the mini-app be opened outside Telegram (a normal browser tab).
 *
 * Since a plain browser has no Telegram WebApp `initData` to sign requests
 * with, we can't trust "who the user is" the normal way. Instead:
 *   1. The browser asks us for a short numeric code (generate()).
 *   2. The person sends that code to the bot in Telegram.
 *   3. The bot calls linkCode() with the code + their real Telegram id,
 *      which is the only place identity is actually established.
 *   4. The browser polls status() until the code has been linked, then
 *      receives a normal session token (same one verify.php would issue).
 *
 * A code proves nothing by itself — it only becomes a session once someone
 * with a working Telegram account claims it from inside the bot.
 */
final class FaoximaWebLink
{
    private const TTL_SECONDS = 300; // 5 minutes
    private const MAX_ATTEMPTS = 5;

    public static function ensureTable(): void
    {
        global $pdo;
        static $done = false;
        if ($done) {
            return;
        }
        if (!($pdo instanceof PDO)) {
            return;
        }
        $done = true;
        try {
            $pdo->exec(
                "CREATE TABLE IF NOT EXISTS web_link_codes (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(8) NOT NULL,
                    session_token VARCHAR(64) NOT NULL,
                    telegram_id BIGINT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'pending',
                    created_at INT NOT NULL,
                    expires_at INT NOT NULL,
                    claimed_at INT NULL,
                    delivered_at INT NULL,
                    UNIQUE KEY uniq_session (session_token),
                    KEY idx_code (code),
                    KEY idx_telegram (telegram_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            foreach (['claimed_at', 'delivered_at'] as $column) {
                $check = $pdo->query("SHOW COLUMNS FROM web_link_codes LIKE " . $pdo->quote($column));
                if (!$check || $check->rowCount() === 0) {
                    $pdo->exec("ALTER TABLE web_link_codes ADD COLUMN `{$column}` INT NULL");
                }
            }
        } catch (Throwable $e) {
            if (class_exists('FaoximaLogger')) {
                FaoximaLogger::exception($e, 'Failed to ensure web_link_codes table');
            }
        }
    }

    /** Creates a fresh pending code + opaque session token for the browser tab. */
    public static function generate(): array
    {
        global $pdo;
        self::ensureTable();
        if (!($pdo instanceof PDO)) {
            throw new RuntimeException('Database unavailable');
        }

        $now = time();
        $expires = $now + self::TTL_SECONDS;
        try {
            $pdo->prepare("DELETE FROM web_link_codes WHERE expires_at < :cutoff AND status <> 'linked'")
                ->execute([':cutoff' => $now - 86400]);
        } catch (Throwable $e) {
        }

        for ($attempt = 0; $attempt < self::MAX_ATTEMPTS; $attempt++) {
            $code = (string)random_int(100000, 999999);
            $session = bin2hex(random_bytes(24));
            try {
                $collision = $pdo->prepare(
                    "SELECT COUNT(*) FROM web_link_codes
                      WHERE code = :code AND status = 'pending' AND expires_at >= :now"
                );
                $collision->execute([':code' => $code, ':now' => $now]);
                if ((int)$collision->fetchColumn() > 0) continue;
                $stmt = $pdo->prepare(
                    "INSERT INTO web_link_codes (code, session_token, telegram_id, status, created_at, expires_at)
                     VALUES (:code, :session, NULL, 'pending', :created, :expires)"
                );
                $stmt->execute([
                    ':code'    => $code,
                    ':session' => $session,
                    ':created' => $now,
                    ':expires' => $expires,
                ]);
                return [
                    'code'          => $code,
                    'session_token' => $session,
                    'expires_in'    => self::TTL_SECONDS,
                ];
            } catch (Throwable $e) {
                continue;
            }
        }

        throw new RuntimeException('Failed to generate link code');
    }

    /** Looks up the state of a browser session by its opaque token. */
    public static function status(string $sessionToken): array
    {
        global $pdo;
        self::ensureTable();
        if (!($pdo instanceof PDO)) {
            return ['link_status' => 'error'];
        }
        if ($sessionToken === '') {
            return ['link_status' => 'not_found'];
        }

        $stmt = $pdo->prepare("SELECT * FROM web_link_codes WHERE session_token = :s LIMIT 1");
        $stmt->execute([':s' => $sessionToken]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$row) {
            return ['link_status' => 'not_found'];
        }
        if ($row['status'] === 'pending' && (int)$row['expires_at'] < time()) {
            return ['link_status' => 'expired'];
        }
        if ($row['status'] === 'linked' && !empty($row['telegram_id'])) {
            $claimedAt = (int)($row['claimed_at'] ?? 0);
            $deliveredAt = (int)($row['delivered_at'] ?? 0);
            if (($claimedAt > 0 && $claimedAt < time() - 600)
                || ($deliveredAt > 0 && $deliveredAt < time() - 120)) {
                return ['link_status' => 'expired'];
            }
            return ['link_status' => 'linked', 'telegram_id' => (int)$row['telegram_id']];
        }
        return ['link_status' => 'pending'];
    }

    /**
     * Called from the bot's own message handler (never from the browser)
     * once a real Telegram user has claimed a code. Only a still-pending,
     * unexpired code can be claimed, and only once.
     */
    public static function linkCode(string $code, int $telegramId): bool
    {
        global $pdo;
        self::ensureTable();
        if (!($pdo instanceof PDO)) {
            return false;
        }
        if (!preg_match('/^\d{6}$/', $code) || $telegramId <= 0) {
            return false;
        }

        $now = time();
        $stmt = $pdo->prepare(
            "UPDATE web_link_codes
             SET telegram_id = :tid, status = 'linked', claimed_at = :claimed
             WHERE code = :code AND status = 'pending' AND expires_at >= :expires_now
             ORDER BY id DESC LIMIT 1"
        );
        $stmt->execute([
            ':tid' => $telegramId,
            ':code' => $code,
            ':claimed' => $now,
            ':expires_now' => $now,
        ]);
        return $stmt->rowCount() > 0;
    }

    /** Limits replay of a browser-link session after the permanent token was delivered. */
    public static function markDelivered(string $sessionToken): void
    {
        global $pdo;
        if (!($pdo instanceof PDO) || $sessionToken === '') return;
        try {
            $stmt = $pdo->prepare(
                "UPDATE web_link_codes SET delivered_at = COALESCE(delivered_at, :now)
                  WHERE session_token = :session AND status = 'linked'"
            );
            $stmt->execute([':now' => time(), ':session' => $sessionToken]);
        } catch (Throwable $e) {
        }
    }

    public static function resolveBotUsername(): string
    {
        global $setting;
        $botUsername = '';
        if (is_array($setting ?? null)) {
            foreach (['bot_username', 'username_bot', 'usernamebot', 'BotUsername', 'bot_user'] as $k) {
                if (isset($setting[$k]) && is_string($setting[$k]) && trim($setting[$k]) !== '') {
                    $botUsername = ltrim((string)$setting[$k], '@');
                    break;
                }
            }
        }
        if ($botUsername === '' && function_exists('telegram')) {
            $me = @telegram('getMe', []);
            if (is_array($me) && isset($me['result']['username'])) {
                $botUsername = (string)$me['result']['username'];
            }
        }
        return $botUsername;
    }
}
