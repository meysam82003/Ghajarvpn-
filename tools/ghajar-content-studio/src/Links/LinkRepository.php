<?php
declare(strict_types=1);

namespace Ghajar\Studio\Links;

use Ghajar\Studio\Core\Database;

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

    public function set(string $key, string $value, ?string $label = null): void
    {
        $pdo  = Database::connect();
        $stmt = $pdo->prepare('SELECT id FROM links WHERE key = :key');
        $stmt->execute([':key' => $key]);
        if ($stmt->fetchColumn() !== false) {
            $pdo->prepare('UPDATE links SET value = :v, updated_at = CURRENT_TIMESTAMP WHERE key = :k')
                ->execute([':v' => $value, ':k' => $key]);
            return;
        }
        $max = (int) $pdo->query('SELECT COALESCE(MAX(position), 0) FROM links')->fetchColumn();
        $pdo->prepare('INSERT INTO links (key, label, value, is_builtin, position) VALUES (:k, :l, :v, 0, :p)')
            ->execute([':k' => $key, ':l' => $label ?? $key, ':v' => $value, ':p' => $max + 10]);
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

    /** Values keyed for the template renderer. @return array<string,string> */
    public function placeholders(): array
    {
        $out = [];
        foreach ($this->all() as $row) {
            $out[(string) $row['key']] = (string) $row['value'];
        }
        $extra = [];
        foreach ($this->all() as $row) {
            if ((int) $row['is_builtin'] === 0 && trim((string) $row['value']) !== '') {
                $extra[] = '🔗 ' . $row['label'] . ':' . "\n" . $row['value'];
            }
        }
        $out['extra_links'] = implode("\n\n", $extra);
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
