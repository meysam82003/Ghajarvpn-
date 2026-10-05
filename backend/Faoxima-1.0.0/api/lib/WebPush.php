<?php

declare(strict_types=1);

/**
 * Web Push for the Ghajar PWA, with no third-party package.
 *
 *  - VAPID (RFC 8292): an ES256 JWT signed with this server's own P-256 key,
 *    generated once and kept in ghajar_push_config.
 *  - Message encryption (RFC 8291, aes128gcm / RFC 8188): ECDH with the
 *    browser's key, HKDF, AES-128-GCM - all through ext-openssl.
 *
 * Everything the push services see is ciphertext; the notice text is readable
 * only by the subscribed browser.
 */
final class GhajarWebPush
{
    private const P256_SPKI_PREFIX = '3059301306072a8648ce3d020106082a8648ce3d030107034200';

    public static function ensureSchema(PDO $pdo): void
    {
        $pdo->exec("CREATE TABLE IF NOT EXISTS ghajar_push_config (
            k VARCHAR(64) NOT NULL PRIMARY KEY,
            v TEXT NOT NULL
        ) DEFAULT CHARSET=utf8mb4");
        $pdo->exec("CREATE TABLE IF NOT EXISTS ghajar_push_subscriptions (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            endpoint TEXT NOT NULL,
            endpoint_hash CHAR(64) NOT NULL,
            p256dh VARCHAR(255) NOT NULL,
            auth VARCHAR(64) NOT NULL,
            prefs VARCHAR(255) NOT NULL DEFAULT '',
            api_base VARCHAR(512) NOT NULL DEFAULT '',
            ua VARCHAR(255) NOT NULL DEFAULT '',
            created_at INT NOT NULL DEFAULT 0,
            last_ok_at INT NOT NULL DEFAULT 0,
            fail_count INT NOT NULL DEFAULT 0,
            UNIQUE KEY uniq_endpoint (endpoint_hash),
            KEY idx_user (user_id)
        ) DEFAULT CHARSET=utf8mb4");
        $pdo->exec("CREATE TABLE IF NOT EXISTS ghajar_push_sent (
            user_id BIGINT NOT NULL,
            notice_id BIGINT NOT NULL,
            fingerprint CHAR(24) NOT NULL DEFAULT '',
            sent_at INT NOT NULL DEFAULT 0,
            PRIMARY KEY (user_id, notice_id)
        ) DEFAULT CHARSET=utf8mb4");
    }

    // ------------------------------------------------------------ VAPID keys

    /** @return array{private: string, public: string} PEM private key and base64url raw public key */
    public static function vapidKeys(PDO $pdo): array
    {
        self::ensureSchema($pdo);
        $rows = $pdo->query("SELECT k, v FROM ghajar_push_config WHERE k IN ('vapid_private','vapid_public')")->fetchAll(PDO::FETCH_KEY_PAIR);
        if (!empty($rows['vapid_private']) && !empty($rows['vapid_public'])) {
            return ['private' => $rows['vapid_private'], 'public' => $rows['vapid_public']];
        }
        $key = openssl_pkey_new(['curve_name' => 'prime256v1', 'private_key_type' => OPENSSL_KEYTYPE_EC]);
        if ($key === false) {
            throw new RuntimeException('openssl could not create a P-256 key');
        }
        openssl_pkey_export($key, $pem);
        $public = self::b64url(self::rawPublic($key));
        $stmt = $pdo->prepare("INSERT INTO ghajar_push_config (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = v");
        $stmt->execute(['vapid_private', $pem]);
        $stmt->execute(['vapid_public', $public]);
        // Re-read: a concurrent first request must not leave two different keys.
        $rows = $pdo->query("SELECT k, v FROM ghajar_push_config WHERE k IN ('vapid_private','vapid_public')")->fetchAll(PDO::FETCH_KEY_PAIR);
        return ['private' => $rows['vapid_private'], 'public' => $rows['vapid_public']];
    }

    /** The uncompressed point 0x04||X||Y of an EC key. */
    private static function rawPublic($key): string
    {
        $d = openssl_pkey_get_details($key);
        $x = str_pad($d['ec']['x'], 32, "\0", STR_PAD_LEFT);
        $y = str_pad($d['ec']['y'], 32, "\0", STR_PAD_LEFT);
        return "\x04" . $x . $y;
    }

    private static function publicKeyFromRaw(string $raw)
    {
        $der = hex2bin(self::P256_SPKI_PREFIX) . $raw;
        $pem = "-----BEGIN PUBLIC KEY-----\n" . chunk_split(base64_encode($der), 64, "\n") . "-----END PUBLIC KEY-----\n";
        $key = openssl_pkey_get_public($pem);
        if ($key === false) {
            throw new InvalidArgumentException('invalid P-256 public key');
        }
        return $key;
    }

    // ------------------------------------------------------------ JWT

    public static function vapidHeader(string $endpoint, array $keys, string $subject): string
    {
        $parts = parse_url($endpoint);
        $aud = $parts['scheme'] . '://' . $parts['host'] . (isset($parts['port']) ? ':' . $parts['port'] : '');
        $header = self::b64url(json_encode(['typ' => 'JWT', 'alg' => 'ES256']));
        $claims = self::b64url(json_encode(['aud' => $aud, 'exp' => time() + 12 * 3600, 'sub' => $subject], JSON_UNESCAPED_SLASHES));
        $input = $header . '.' . $claims;
        $priv = openssl_pkey_get_private($keys['private']);
        if (!openssl_sign($input, $der, $priv, OPENSSL_ALGO_SHA256)) {
            throw new RuntimeException('VAPID signature failed');
        }
        $jwt = $input . '.' . self::b64url(self::derToJose($der));
        return 'vapid t=' . $jwt . ', k=' . $keys['public'];
    }

    /** ECDSA DER (SEQUENCE{INTEGER r, INTEGER s}) to the 64-byte r||s JOSE form. */
    private static function derToJose(string $der): string
    {
        $offset = 2;
        if (ord($der[1]) & 0x80) {
            $offset += ord($der[1]) & 0x7f;
        }
        $read = function () use ($der, &$offset): string {
            $offset++; // 0x02
            $len = ord($der[$offset++]);
            $v = substr($der, $offset, $len);
            $offset += $len;
            return str_pad(ltrim($v, "\0"), 32, "\0", STR_PAD_LEFT);
        };
        $r = $read();
        $s = $read();
        return $r . $s;
    }

    // ------------------------------------------------------------ encryption

    /** RFC 8291 aes128gcm body for one subscription. */
    public static function encrypt(string $payload, string $p256dhB64, string $authB64): string
    {
        $uaPublic = self::b64urlDecode($p256dhB64);
        $authSecret = self::b64urlDecode($authB64);
        if (strlen($uaPublic) !== 65 || strlen($authSecret) < 16) {
            throw new InvalidArgumentException('invalid subscription keys');
        }
        $local = openssl_pkey_new(['curve_name' => 'prime256v1', 'private_key_type' => OPENSSL_KEYTYPE_EC]);
        $asPublic = self::rawPublic($local);
        $shared = openssl_pkey_derive(self::publicKeyFromRaw($uaPublic), $local, 32);
        if ($shared === false) {
            throw new RuntimeException('ECDH failed');
        }
        $shared = str_pad($shared, 32, "\0", STR_PAD_LEFT);

        // IKM = HKDF(auth_secret, ecdh_secret, "WebPush: info\0" || ua_public || as_public, 32)
        $prkKey = hash_hmac('sha256', $shared, $authSecret, true);
        $ikm = hash_hmac('sha256', "WebPush: info\0" . $uaPublic . $asPublic . "\x01", $prkKey, true);

        $salt = random_bytes(16);
        $prk = hash_hmac('sha256', $ikm, $salt, true);
        $cek = substr(hash_hmac('sha256', "Content-Encoding: aes128gcm\0\x01", $prk, true), 0, 16);
        $nonce = substr(hash_hmac('sha256', "Content-Encoding: nonce\0\x01", $prk, true), 0, 12);

        $plain = $payload . "\x02";
        $tag = '';
        $cipher = openssl_encrypt($plain, 'aes-128-gcm', $cek, OPENSSL_RAW_DATA, $nonce, $tag, '', 16);
        if ($cipher === false) {
            throw new RuntimeException('AES-GCM failed');
        }
        $header = $salt . pack('N', 4096) . chr(65) . $asPublic;
        return $header . $cipher . $tag;
    }

    /**
     * Sends one message. Returns the HTTP status: 201/200 delivered, 404/410
     * the subscription is gone (delete it), anything else a retry later.
     */
    public static function send(array $sub, string $payload, array $keys, string $subject, int $ttl = 86400, string $urgency = 'normal'): int
    {
        $body = self::encrypt($payload, $sub['p256dh'], $sub['auth']);
        $ch = curl_init($sub['endpoint']);
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $body,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 15,
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/octet-stream',
                'Content-Encoding: aes128gcm',
                'TTL: ' . $ttl,
                'Urgency: ' . $urgency,
                'Authorization: ' . self::vapidHeader($sub['endpoint'], $keys, $subject),
            ],
        ]);
        curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        return $code;
    }

    public static function b64url(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }

    public static function b64urlDecode(string $s): string
    {
        $s = strtr(trim($s), '-_', '+/');
        $pad = strlen($s) % 4;
        if ($pad) {
            $s .= str_repeat('=', 4 - $pad);
        }
        return (string)base64_decode($s, true);
    }

    /** Only real push services: HTTPS, a public host, no credentials. */
    public static function validEndpoint(string $endpoint): bool
    {
        $p = parse_url($endpoint);
        if (!$p || ($p['scheme'] ?? '') !== 'https' || empty($p['host']) || isset($p['user']) || isset($p['pass'])) {
            return false;
        }
        $host = strtolower($p['host']);
        if ($host === 'localhost' || filter_var($host, FILTER_VALIDATE_IP)) {
            return false;
        }
        return strlen($endpoint) <= 2000;
    }
}
