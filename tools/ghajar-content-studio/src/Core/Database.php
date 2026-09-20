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
        // Only attributes pdo_sqlite supports on every PHP 8.2–8.4 build.
        $pdo = new PDO('sqlite:' . $file, null, null, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
        // WAL needs a writable directory; a host that refuses it still works.
        try {
            $pdo->exec('PRAGMA journal_mode = WAL');
        } catch (\PDOException) {
            $pdo->exec('PRAGMA journal_mode = DELETE');
        }
        $pdo->exec('PRAGMA foreign_keys = ON');
        $pdo->exec('PRAGMA busy_timeout = 8000');
        if (is_file($file)) {
            @chmod($file, 0600);
        }
        return self::$pdo = $pdo;
    }

    /** SQLite engine version, e.g. "3.45.1". */
    public static function sqliteVersion(?PDO $pdo = null): string
    {
        $pdo ??= self::connect();
        return (string) $pdo->query('SELECT sqlite_version()')->fetchColumn();
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
