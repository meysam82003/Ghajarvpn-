<?php

declare(strict_types=1);

/** Shared by Telegram, mini-app and Android. A reservation counts before remote provisioning. */
final class GhajarPanelTrial
{
    private static function db(): PDO
    {
        global $pdo;
        if (!($pdo instanceof PDO)) throw new RuntimeException('Database unavailable');
        return $pdo;
    }

    public static function ensureSchema(): void
    {
        static $done = false;
        if ($done) return;
        $db = self::db();
        $db->exec("CREATE TABLE IF NOT EXISTS ghajar_panel_trial_quota (
            user_id BIGINT NOT NULL, panel_code VARCHAR(191) NOT NULL,
            used_count INT NOT NULL DEFAULT 0,
            PRIMARY KEY (user_id, panel_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $db->exec("CREATE TABLE IF NOT EXISTS ghajar_panel_trial_claim (
            claim_id CHAR(32) PRIMARY KEY, user_id BIGINT NOT NULL,
            panel_code VARCHAR(191) NOT NULL, status VARCHAR(16) NOT NULL,
            created_at BIGINT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        $done = true;
    }

    public static function limit(array $setting): int
    {
        // user.limit_usertest is the OLD global remaining counter, not a per-panel cap.
        return max(0, (int)($setting['limit_usertest_all'] ?? 1));
    }

    public static function eligible(array $panel, array $user): bool
    {
        $hide = json_decode((string)($panel['hide_user'] ?? ''), true);
        return ($panel['status'] ?? '') === 'active'
            && ($panel['TestAccount'] ?? '') === 'ONTestAccount'
            && in_array((string)($panel['agent'] ?? ''), ['all', (string)($user['agent'] ?? 'f')], true)
            && (!is_array($hide) || !in_array((string)$user['id'], array_map('strval', $hide), true));
    }

    private static function seed(array $user, array $panel): void
    {
        $db = self::db();
        // Existing successful trials still count after upgrading. Failed invoices do not.
        $q = $db->prepare("INSERT IGNORE INTO ghajar_panel_trial_quota (user_id,panel_code,used_count)
            SELECT :uid, :code, COUNT(*) FROM invoice
             WHERE id_user = :history_uid AND Service_location = :location
               AND name_product = 'سرویس تست' AND Status NOT IN ('Unsuccessful','failed')");
        $q->execute([':uid' => $user['id'], ':code' => $panel['code_panel'],
            ':history_uid' => $user['id'], ':location' => $panel['name_panel']]);
    }

    public static function remaining(array $user, array $panel, array $setting, bool $admin = false): int
    {
        if (!self::eligible($panel, $user)) return 0;
        if ($admin) return PHP_INT_MAX;
        self::ensureSchema();
        self::seed($user, $panel);
        $q = self::db()->prepare('SELECT used_count FROM ghajar_panel_trial_quota WHERE user_id=? AND panel_code=?');
        $q->execute([$user['id'], $panel['code_panel']]);
        return max(0, self::limit($setting) - (int)$q->fetchColumn());
    }

    public static function panels(array $user, array $setting, bool $admin = false): array
    {
        $q = self::db()->query("SELECT * FROM marzban_panel WHERE status='active' AND TestAccount='ONTestAccount' ORDER BY code_panel");
        $panels = [];
        foreach ($q->fetchAll(PDO::FETCH_ASSOC) as $row) {
            if (!self::eligible($row, $user)) continue;
            $left = self::remaining($user, $row, $setting, $admin);
            if ($left <= 0) continue;
            $row['trial_limit_left'] = $admin ? null : $left;
            $panels[] = $row;
        }
        return $panels;
    }

    /** Returns an opaque reservation; null means unavailable or quota exhausted. */
    public static function reserve(array $user, string $panelCode, array $setting, bool $admin = false): ?string
    {
        self::ensureSchema();
        $db = self::db();
        $db->beginTransaction();
        try {
            $q = $db->prepare('SELECT * FROM marzban_panel WHERE code_panel=? LIMIT 1 FOR UPDATE');
            $q->execute([$panelCode]);
            $panel = $q->fetch(PDO::FETCH_ASSOC);
            if (!is_array($panel) || !self::eligible($panel, $user)) { $db->rollBack(); return null; }
            self::seed($user, $panel);
            if (!$admin) {
                $q = $db->prepare('UPDATE ghajar_panel_trial_quota SET used_count=used_count+1 WHERE user_id=? AND panel_code=? AND used_count < ?');
                $q->execute([$user['id'], $panelCode, self::limit($setting)]);
                if ($q->rowCount() !== 1) { $db->rollBack(); return null; }
            }
            $id = bin2hex(random_bytes(16));
            $q = $db->prepare('INSERT INTO ghajar_panel_trial_claim (claim_id,user_id,panel_code,status,created_at) VALUES (?,?,?,?,?)');
            $q->execute([$id, $user['id'], $panelCode, $admin ? 'admin' : 'pending', time()]);
            $db->commit();
            return $id;
        } catch (Throwable $e) {
            if ($db->inTransaction()) $db->rollBack();
            throw $e;
        }
    }

    public static function finish(string $id): void
    {
        $q = self::db()->prepare("UPDATE ghajar_panel_trial_claim SET status='issued' WHERE claim_id=? AND status='pending'");
        $q->execute([$id]);
    }

    /** Only release a confirmed failure. A process crash keeps its reservation for reconciliation. */
    public static function release(string $id): void
    {
        $db = self::db(); $db->beginTransaction();
        try {
            $q = $db->prepare('SELECT * FROM ghajar_panel_trial_claim WHERE claim_id=? FOR UPDATE');
            $q->execute([$id]); $claim = $q->fetch(PDO::FETCH_ASSOC);
            if (is_array($claim) && $claim['status'] === 'pending') {
                $q = $db->prepare('UPDATE ghajar_panel_trial_quota SET used_count=GREATEST(0,used_count-1) WHERE user_id=? AND panel_code=?');
                $q->execute([$claim['user_id'], $claim['panel_code']]);
                $q = $db->prepare("UPDATE ghajar_panel_trial_claim SET status='failed' WHERE claim_id=?");
                $q->execute([$id]);
            }
            $db->commit();
        } catch (Throwable $e) { if ($db->inTransaction()) $db->rollBack(); throw $e; }
    }
}
