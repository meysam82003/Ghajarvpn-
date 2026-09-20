<?php
declare(strict_types=1);

namespace Ghajar\Studio\Core;

use PDO;

final class Database
{
    private static ?PDO $pdo = null;

    public static function connect(?string $file = null): PDO
    {
        if (self::$pdo instanceof PDO && $file === null) {
            return self::$pdo;
        }
        $file ??= (string) Config::get('db_file', GCS_STORAGE . '/data/studio.sqlite');
        $dir = dirname($file);
        if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) {
            throw new \RuntimeException('امکان ایجاد پوشه دیتابیس وجود ندارد: ' . $dir);
        }
        $pdo = new PDO('sqlite:' . $file, null, null, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
        $pdo->exec('PRAGMA journal_mode = WAL');
        $pdo->exec('PRAGMA foreign_keys = ON');
        $pdo->exec('PRAGMA busy_timeout = 8000');
        if (is_file($file)) {
            @chmod($file, 0600);
        }
        return self::$pdo = $pdo;
    }

    public static function set(PDO $pdo): void
    {
        self::$pdo = $pdo;
    }

    public static function reset(): void
    {
        self::$pdo = null;
    }

    /** Run a callback inside a transaction (nesting-safe). */
    public static function transaction(callable $fn): mixed
    {
        $pdo = self::connect();
        if ($pdo->inTransaction()) {
            return $fn($pdo);
        }
        $pdo->beginTransaction();
        try {
            $result = $fn($pdo);
            $pdo->commit();
            return $result;
        } catch (\Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            throw $e;
        }
    }
}
