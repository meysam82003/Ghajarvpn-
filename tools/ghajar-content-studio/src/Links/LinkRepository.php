<?php
declare(strict_types=1);

namespace Ghajar\Studio\Links;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Support\Html;

final class LinkRepository
{
    /** @return list<array<string,mixed>> */
    public function all(): array
    {
        return Database::connect()
            ->query('SELECT * FROM links ORDER BY position ASC, id ASC')
            ->fetchAll();
    }

    public function get(string $key, string $default = ''): string
    {
        $stmt = Database::connect()->prepare('SELECT value FROM links WHERE key = :key');
        $stmt->execute([':key' => $key]);
        $value = $stmt->fetchColumn();
        return $value === false ? $default : (string) $value;
    }

    public function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM links WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $row = $stmt->fetch();
        return $row ?: null;
    }

    public function set(string $key, string $value, ?string $label = null, ?string $anchor = null): void
    {
        $pdo  = Database::connect();
        $stmt = $pdo->prepare('SELECT id FROM links WHERE key = :key');
        $stmt->execute([':key' => $key]);
        if ($stmt->fetchColumn() !== false) {
            $pdo->prepare('UPDATE links SET value = :v, updated_at = CURRENT_TIMESTAMP WHERE key = :k')
                ->execute([':v' => $value, ':k' => $key]);
            if ($anchor !== null) {
                $this->setAnchor($key, $anchor);
            }
            return;
        }
        $max = (int) $pdo->query('SELECT COALESCE(MAX(position), 0) FROM links')->fetchColumn();
        $pdo->prepare('INSERT INTO links (key, label, value, anchor, is_builtin, position) VALUES (:k, :l, :v, :a, 0, :p)')
            ->execute([
                ':k' => $key,
                ':l' => $label ?? $key,
                ':v' => $value,
                ':a' => $anchor ?? $label ?? $key,
                ':p' => $max + 10,
            ]);
    }

    /** The clickable text shown instead of the raw URL. */
    public function setAnchor(string $key, string $anchor): void
    {
        Database::connect()
            ->prepare('UPDATE links SET anchor = :a, updated_at = CURRENT_TIMESTAMP WHERE key = :k')
            ->execute([':a' => $anchor, ':k' => $key]);
    }

    public function delete(int $id): bool
    {
        $link = $this->find($id);
        if ($link === null || (int) $link['is_builtin'] === 1) {
            return false;
        }
        Database::connect()->prepare('DELETE FROM links WHERE id = :id')->execute([':id' => $id]);
        return true;
    }

    /**
     * Values keyed for the template renderer.
     *
     * Every link yields two placeholders:
     *   {key}       → the raw URL
     *   {key_link}  → <a href="URL">anchor text</a>, so the post shows a
     *                 clickable phrase instead of a long URL.
     *
     * @return array<string,string>
     */
    public function placeholders(): array
    {
        $out   = [];
        $extra = [];
        foreach ($this->all() as $row) {
            $key   = (string) $row['key'];
            $value = (string) $row['value'];
            $text  = trim((string) ($row['anchor'] ?? '')) !== ''
                ? (string) $row['anchor']
                : (string) $row['label'];

            // "apk_url" and "channel_link" both shorten to a friendly base, so
            // templates read {apk_link} / {channel_link} instead of {apk_url_link}.
            $base = (string) preg_replace('/_(url|link)$/', '', $key);
            $anchorHtml = $value === ''
                ? ''
                : '<a href="' . Html::escape($value) . '">' . Html::escape($text) . '</a>';

            $out[$key]               = $value;
            $out[$base . '_url']     = $value;
            $out[$base . '_link']    = $anchorHtml;
            $out[$base . '_text']    = Html::escape($text);
            $out[$key . '_link']   ??= $anchorHtml;

            if ((int) $row['is_builtin'] === 0 && trim($value) !== '') {
                $extra[] = '🔗 ' . $anchorHtml;
            }
        }
        $out['extra_links'] = implode("\n", $extra);
        return $out;
    }

    public static function isValidUrl(string $url): bool
    {
        $url = trim($url);
        if ($url === '') {
            return false;
        }
        if (preg_match('/^@[A-Za-z0-9_]{4,32}$/', $url)) {
            return true;
        }
        return (bool) filter_var($url, FILTER_VALIDATE_URL) && (bool) preg_match('#^https?://#i', $url);
    }
}
