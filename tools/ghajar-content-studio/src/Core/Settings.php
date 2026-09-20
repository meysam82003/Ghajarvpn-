<?php
declare(strict_types=1);

namespace Ghajar\Studio\Core;

final class Settings
{
    /** @var array<string,string>|null */
    private static ?array $cache = null;

    public static function get(string $key, string $default = ''): string
    {
        return self::all()[$key] ?? $default;
    }

    public static function int(string $key, int $default = 0): int
    {
        $value = self::get($key, (string) $default);
        return is_numeric($value) ? (int) $value : $default;
    }

    public static function bool(string $key, bool $default = false): bool
    {
        $value = self::get($key, $default ? '1' : '0');
        return $value === '1' || $value === 'true';
    }

    public static function set(string $key, string $value): void
    {
        $pdo = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO settings (key, value, updated_at) VALUES (:k, :v, CURRENT_TIMESTAMP)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at'
        );
        $stmt->execute([':k' => $key, ':v' => $value]);
        self::$cache[$key] = $value;
    }

    /** @return array<string,string> */
    public static function all(): array
    {
        if (self::$cache !== null) {
            return self::$cache;
        }
        $rows = Database::connect()->query('SELECT key, value FROM settings')->fetchAll();
        $out = [];
        foreach ($rows as $row) {
            $out[(string) $row['key']] = (string) $row['value'];
        }
        return self::$cache = $out;
    }

    public static function flush(): void
    {
        self::$cache = null;
    }
}
