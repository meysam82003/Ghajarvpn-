<?php
declare(strict_types=1);

namespace Ghajar\Studio\Templates;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Support\Html;

/**
 * Premium (custom) emoji the admin captured from a forwarded message, ready to
 * be pasted into any template section.
 *
 * Telegram only renders <tg-emoji> for bots allowed to use custom emoji; the
 * publisher falls back to the plain emoji automatically when it is rejected.
 */
final class CustomEmojiRepository
{
    /** @return list<array<string,mixed>> */
    public function all(): array
    {
        return Database::connect()
            ->query('SELECT * FROM custom_emoji ORDER BY id DESC')
            ->fetchAll();
    }

    public function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM custom_emoji WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $row = $stmt->fetch();
        return $row ?: null;
    }

    public function save(string $name, string $emojiId, string $fallback): int
    {
        $pdo = Database::connect();
        $pdo->prepare(
            'INSERT INTO custom_emoji (name, emoji_id, fallback) VALUES (:n, :i, :f)
             ON CONFLICT(name) DO UPDATE SET emoji_id = excluded.emoji_id, fallback = excluded.fallback'
        )->execute([':n' => $name, ':i' => $emojiId, ':f' => $fallback]);
        return (int) $pdo->lastInsertId();
    }

    public function delete(int $id): void
    {
        Database::connect()->prepare('DELETE FROM custom_emoji WHERE id = :id')->execute([':id' => $id]);
    }

    public function count(): int
    {
        return (int) Database::connect()->query('SELECT COUNT(*) FROM custom_emoji')->fetchColumn();
    }

    /** The exact HTML to paste into a template section. */
    public static function toHtml(string $emojiId, string $fallback): string
    {
        return '<tg-emoji emoji-id="' . Html::escape($emojiId) . '">' . $fallback . '</tg-emoji>';
    }
}
