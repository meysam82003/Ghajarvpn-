<?php
declare(strict_types=1);

namespace Ghajar\Studio\Core;

/**
 * Secure configuration file handling. The config file lives inside storage/
 * which is blocked from direct web access and returns a plain PHP array.
 */
final class Config
{
    private static ?array $cache = null;

    public static function path(): string
    {
        return GCS_STORAGE . '/config.php';
    }

    public static function exists(): bool
    {
        return is_file(self::path());
    }

    /** @return array<string,mixed> */
    public static function all(): array
    {
        if (self::$cache !== null) {
            return self::$cache;
        }
        if (!self::exists()) {
            return self::$cache = [];
        }
        /** @var mixed $data */
        $data = require self::path();
        return self::$cache = is_array($data) ? $data : [];
    }

    public static function get(string $key, mixed $default = null): mixed
    {
        return self::all()[$key] ?? $default;
    }

    /** @param array<string,mixed> $data */
    public static function write(array $data): void
    {
        $dir = dirname(self::path());
        if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) {
            throw new \RuntimeException('امکان ایجاد پوشه storage وجود ندارد.');
        }
        $export = var_export($data, true);
        $php = "<?php\n// Ghajar Content Studio configuration. DO NOT share this file.\nreturn {$export};\n";
        $tmp = self::path() . '.tmp';
        if (@file_put_contents($tmp, $php, LOCK_EX) === false) {
            throw new \RuntimeException('نوشتن فایل تنظیمات ممکن نیست. دسترسی نوشتن پوشه storage را بررسی کنید.');
        }
        @chmod($tmp, 0600);
        if (!@rename($tmp, self::path())) {
            @unlink($tmp);
            throw new \RuntimeException('جابه‌جایی فایل تنظیمات ناموفق بود.');
        }
        if (function_exists('opcache_invalidate')) {
            @opcache_invalidate(self::path(), true);
        }
        self::$cache = $data;
    }

    public static function merge(array $data): void
    {
        self::write(array_merge(self::all(), $data));
    }

    public static function forgetCache(): void
    {
        self::$cache = null;
    }
}
