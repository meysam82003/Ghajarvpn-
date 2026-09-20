<?php
declare(strict_types=1);

namespace Ghajar\Studio\Support;

final class Str
{
    private const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
    private const AR_DIGITS = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];

    public static function toPersianDigits(string $value): string
    {
        return str_replace(range(0, 9), self::FA_DIGITS, $value);
    }

    public static function toEnglishDigits(string $value): string
    {
        $value = str_replace(self::FA_DIGITS, range(0, 9), $value);
        return str_replace(self::AR_DIGITS, range(0, 9), $value);
    }

    /** Normalise whitespace without destroying intentional blank lines. */
    public static function normalizeWhitespace(string $text): string
    {
        $text = str_replace(["\r\n", "\r"], "\n", $text);
        $text = (string) preg_replace('/[ \t\x{00A0}]+/u', ' ', $text);
        $text = (string) preg_replace('/ +\n/u', "\n", $text);
        $text = (string) preg_replace('/\n{3,}/u', "\n\n", $text);
        return trim($text);
    }

    /** Fix Persian spacing rules that commonly break when text is pasted. */
    public static function fixPersianSpacing(string $text): string
    {
        $text = (string) preg_replace('/\s+([،؛:.!؟?])/u', '$1', $text);
        $text = (string) preg_replace('/([،؛:])(?=\S)/u', '$1 ', $text);
        $text = str_replace('ك', 'ک', $text);
        return str_replace('ي', 'ی', $text);
    }

    /** @return string[] Paragraphs separated by one or more blank lines. */
    public static function paragraphs(string $text): array
    {
        $parts = preg_split('/\n\s*\n/u', self::normalizeWhitespace($text)) ?: [];
        return array_values(array_filter(array_map('trim', $parts), static fn ($p) => $p !== ''));
    }

    /** @return string[] */
    public static function lines(string $text): array
    {
        return explode("\n", str_replace(["\r\n", "\r"], "\n", $text));
    }

    /** Extract every http(s) or t.me URL from a text. @return string[] */
    public static function extractUrls(string $text): array
    {
        preg_match_all('#(https?://[^\s<>"\']+|t\.me/[^\s<>"\']+)#u', $text, $m);
        $urls = array_map(static fn (string $u) => rtrim($u, '.,;،؛)'), $m[0] ?? []);
        return array_values(array_unique($urls));
    }

    public static function startsWithEmoji(string $line): bool
    {
        return (bool) preg_match('/^\s*(?:[\x{1F000}-\x{1FAFF}\x{2190}-\x{27BF}\x{2B00}-\x{2BFF}\x{FE0F}\x{2600}-\x{26FF}]\x{FE0F}?)/u', $line);
    }

    /** Split the leading emoji off a line. @return array{0:string,1:string} [emoji, rest] */
    public static function splitLeadingEmoji(string $line): array
    {
        if (preg_match('/^\s*((?:[\x{1F000}-\x{1FAFF}\x{2190}-\x{27BF}\x{2B00}-\x{2BFF}\x{2600}-\x{26FF}]\x{FE0F}?\x{200D}?)+)\s*(.*)$/u', $line, $m)) {
            return [trim($m[1]), trim($m[2])];
        }
        return ['', trim($line)];
    }

    public static function isBulletLine(string $line): bool
    {
        $line = trim($line);
        if ($line === '') {
            return false;
        }
        return self::startsWithEmoji($line) || (bool) preg_match('/^([-*•·▪▫◆●]|\d+[.)])\s+/u', $line);
    }

    public static function stripBullet(string $line): string
    {
        return trim((string) preg_replace('/^([-*•·▪▫◆●]|\d+[.)])\s+/u', '', trim($line)));
    }

    public static function looksLikeUrlLine(string $line): bool
    {
        $line = trim($line);
        return $line !== '' && (bool) preg_match('#^\S*(https?://|t\.me/|@[A-Za-z0-9_]{4,})\S*$#u', $line);
    }

    public static function randomToken(int $bytes = 24): string
    {
        return rtrim(strtr(base64_encode(random_bytes($bytes)), '+/', '-_'), '=');
    }

    public static function slugify(string $value): string
    {
        $value = trim($value);
        $slug = (string) preg_replace('/[^\p{L}\p{N}]+/u', '-', $value);
        $slug = trim(mb_strtolower($slug), '-');
        return $slug !== '' ? $slug : 'tpl-' . bin2hex(random_bytes(3));
    }

    public static function truncate(string $value, int $limit, string $suffix = '…'): string
    {
        return mb_strlen($value) <= $limit ? $value : mb_substr($value, 0, $limit - 1) . $suffix;
    }
}
