<?php
declare(strict_types=1);

namespace Ghajar\Studio\Core;

use Ghajar\Studio\Templates\DefaultTemplates;
use PDO;

final class Migrations
{
    /** Creates every table + index. Safe to run repeatedly. */
    public static function run(PDO $pdo): void
    {
        $statements = [
            'CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT \'\',
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE TABLE IF NOT EXISTS templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                slug TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL DEFAULT \'\',
                structure TEXT NOT NULL,
                is_default INTEGER NOT NULL DEFAULT 0,
                is_system INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE INDEX IF NOT EXISTS idx_templates_default ON templates (is_default)',
            'CREATE TABLE IF NOT EXISTS drafts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_id INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT \'\',
                template_id INTEGER,
                source_text TEXT NOT NULL DEFAULT \'\',
                source_html TEXT NOT NULL DEFAULT \'\',
                source_meta TEXT NOT NULL DEFAULT \'{}\',
                content TEXT NOT NULL DEFAULT \'{}\',
                rendered TEXT NOT NULL DEFAULT \'\',
                post_number INTEGER,
                numbering_mode TEXT NOT NULL DEFAULT \'auto\',
                status TEXT NOT NULL DEFAULT \'draft\',
                publish_token TEXT,
                channel TEXT NOT NULL DEFAULT \'\',
                message_id INTEGER,
                published_at TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (template_id) REFERENCES templates (id) ON DELETE SET NULL
            )',
            'CREATE INDEX IF NOT EXISTS idx_drafts_status ON drafts (status)',
            'CREATE INDEX IF NOT EXISTS idx_drafts_owner ON drafts (owner_id)',
            'CREATE UNIQUE INDEX IF NOT EXISTS idx_drafts_publish_token ON drafts (publish_token)',
            'CREATE TABLE IF NOT EXISTS draft_revisions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                draft_id INTEGER NOT NULL,
                payload TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT \'\',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (draft_id) REFERENCES drafts (id) ON DELETE CASCADE
            )',
            'CREATE INDEX IF NOT EXISTS idx_revisions_draft ON draft_revisions (draft_id, id)',
            'CREATE TABLE IF NOT EXISTS links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT NOT NULL UNIQUE,
                label TEXT NOT NULL,
                value TEXT NOT NULL DEFAULT \'\',
                is_builtin INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE TABLE IF NOT EXISTS published_posts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                draft_id INTEGER,
                post_number INTEGER,
                channel TEXT NOT NULL DEFAULT \'\',
                message_id INTEGER NOT NULL,
                message_url TEXT NOT NULL DEFAULT \'\',
                text TEXT NOT NULL DEFAULT \'\',
                published_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                FOREIGN KEY (draft_id) REFERENCES drafts (id) ON DELETE SET NULL
            )',
            'CREATE UNIQUE INDEX IF NOT EXISTS idx_published_message ON published_posts (channel, message_id)',
            'CREATE INDEX IF NOT EXISTS idx_published_number ON published_posts (post_number)',
            'CREATE TABLE IF NOT EXISTS conversation_state (
                user_id INTEGER PRIMARY KEY,
                state TEXT NOT NULL DEFAULT \'\',
                payload TEXT NOT NULL DEFAULT \'{}\',
                menu_message_id INTEGER,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE TABLE IF NOT EXISTS github_releases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tag TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL DEFAULT \'\',
                published_at TEXT NOT NULL DEFAULT \'\',
                is_prerelease INTEGER NOT NULL DEFAULT 0,
                is_draft INTEGER NOT NULL DEFAULT 0,
                html_url TEXT NOT NULL DEFAULT \'\',
                assets TEXT NOT NULL DEFAULT \'[]\',
                fetched_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE TABLE IF NOT EXISTS custom_emoji (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                emoji_id TEXT NOT NULL,
                fallback TEXT NOT NULL DEFAULT \'⭐️\',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
            'CREATE TABLE IF NOT EXISTS processed_updates (
                update_id INTEGER PRIMARY KEY,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )',
        ];

        foreach ($statements as $sql) {
            $pdo->exec($sql);
        }

        self::upgrade($pdo);
    }

    /**
     * Columns added after 1.0.0. Adding a column that already exists throws,
     * so each one is attempted independently — this keeps existing installs
     * upgradable without any manual database work.
     */
    public static function upgrade(PDO $pdo): void
    {
        $columns = [
            'links'           => [
                'anchor' => 'TEXT NOT NULL DEFAULT \'\'',
            ],
            'drafts'          => [
                'media_type'     => 'TEXT NOT NULL DEFAULT \'\'',
                'media_file_id'  => 'TEXT NOT NULL DEFAULT \'\'',
                'media_position' => 'TEXT NOT NULL DEFAULT \'above\'',
            ],
            'published_posts' => [
                'media_file_id' => 'TEXT NOT NULL DEFAULT \'\'',
                'has_media'     => 'INTEGER NOT NULL DEFAULT 0',
            ],
        ];
        foreach ($columns as $table => $definitions) {
            $existing = [];
            foreach ($pdo->query('PRAGMA table_info(' . $table . ')')->fetchAll() as $row) {
                $existing[] = (string) $row['name'];
            }
            foreach ($definitions as $name => $definition) {
                if (in_array($name, $existing, true)) {
                    continue;
                }
                $pdo->exec(sprintf('ALTER TABLE %s ADD COLUMN %s %s', $table, $name, $definition));
            }
        }
        // Links created before 1.1 have no anchor text yet.
        $pdo->exec('UPDATE links SET anchor = label WHERE anchor = \'\'');
    }

    /** Insert the initial settings, links and system templates. */
    public static function seed(PDO $pdo, int $adminId): void
    {
        $settings = [
            'admin_id'          => (string) $adminId,
            'channel'           => '@Ghajarvpn',
            'bot_username'      => '',
            'github_repo'       => 'meysam82003/Ghajarvpn-',
            'next_post_number'  => '1',
            'numbering_enabled' => '1',
            'parse_mode'        => 'HTML',
            'auto_footer'       => '1',
            'installed_at'      => gmdate('c'),
        ];
        $stmt = $pdo->prepare('INSERT OR IGNORE INTO settings (key, value) VALUES (:k, :v)');
        foreach ($settings as $k => $v) {
            $stmt->execute([':k' => $k, ':v' => $v]);
        }

        $links = [
            ['channel_link', 'کانال تلگرام', 'https://t.me/Ghajarvpn', 1, 10, '@Ghajarvpn'],
            ['bot_link', 'ربات تلگرام', 'https://t.me/Ghajar_vpnbot', 1, 20, '@Ghajar_vpnbot'],
            ['apk_url', 'دانلود مستقیم APK', 'https://github.com/meysam82003/Ghajarvpn-/releases/download/1.0.4/Ghajarvpn-1.0.4-arm64-v8a.apk', 1, 30, 'دانلود مستقیم APK'],
            ['release_url', 'ریلیز گیت‌هاب', 'https://github.com/meysam82003/Ghajarvpn-/releases/tag/1.0.4', 1, 40, 'ریلیز گیت‌هاب و سایر فایل‌های نصب'],
            ['repo_url', 'مخزن گیت‌هاب', 'https://github.com/meysam82003/Ghajarvpn-', 1, 50, 'مخزن گیت‌هاب'],
        ];
        $stmt = $pdo->prepare('INSERT OR IGNORE INTO links (key, label, value, is_builtin, position, anchor) VALUES (?,?,?,?,?,?)');
        foreach ($links as $row) {
            $stmt->execute($row);
        }

        DefaultTemplates::install($pdo);
    }
}
