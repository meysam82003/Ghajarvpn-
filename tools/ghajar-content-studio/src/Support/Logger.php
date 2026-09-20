<?php
declare(strict_types=1);

namespace Ghajar\Studio\Support;

final class Logger
{
    private static ?string $dir = null;

    public static function dir(): string
    {
        if (self::$dir === null) {
            self::$dir = GCS_STORAGE . '/logs';
            if (!is_dir(self::$dir)) {
                @mkdir(self::$dir, 0700, true);
            }
        }
        return self::$dir;
    }

    public static function info(string $message, array $context = []): void
    {
        self::write('INFO', $message, $context);
    }

    public static function error(string $message, array $context = []): void
    {
        self::write('ERROR', $message, $context);
    }

    private static function write(string $level, string $message, array $context): void
    {
        $line = sprintf(
            "[%s] %s %s %s\n",
            gmdate('Y-m-d H:i:s'),
            $level,
            self::redact($message),
            $context ? self::redact(json_encode($context, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) ?: '') : ''
        );
        @file_put_contents(self::dir() . '/studio-' . gmdate('Y-m-d') . '.log', $line, FILE_APPEND | LOCK_EX);
    }

    /** Never let a bot token or webhook secret reach the log files. */
    public static function redact(string $text): string
    {
        $text = (string) preg_replace('/\b\d{6,12}:[A-Za-z0-9_\-]{30,}\b/', '[TOKEN]', $text);
        return (string) preg_replace('/(secret[_-]?token"?\s*[:=]\s*"?)[A-Za-z0-9_\-]{8,}/i', '$1[REDACTED]', $text);
    }
}
