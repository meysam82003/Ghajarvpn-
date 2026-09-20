<?php
/**
 * A tiny zero-dependency test harness (shared hosting cannot run composer).
 */
declare(strict_types=1);

final class TestRunner
{
    /** @var array<int,array{name:string,ok:bool,message:string}> */
    public static array $results = [];
    private static string $group = '';

    public static function group(string $name): void
    {
        self::$group = $name;
    }

    public static function test(string $name, callable $fn): void
    {
        $label = (self::$group !== '' ? '[' . self::$group . '] ' : '') . $name;
        try {
            $fn();
            self::$results[] = ['name' => $label, 'ok' => true, 'message' => ''];
        } catch (Throwable $e) {
            self::$results[] = ['name' => $label, 'ok' => false, 'message' => $e->getMessage()];
        }
    }

    public static function assertTrue(bool $value, string $message = 'expected true'): void
    {
        if (!$value) {
            throw new RuntimeException($message);
        }
    }

    public static function assertFalse(bool $value, string $message = 'expected false'): void
    {
        self::assertTrue(!$value, $message);
    }

    public static function assertSame(mixed $expected, mixed $actual, string $message = ''): void
    {
        if ($expected !== $actual) {
            throw new RuntimeException(($message !== '' ? $message . ' — ' : '')
                . 'expected ' . var_export($expected, true) . ', got ' . var_export($actual, true));
        }
    }

    public static function assertStringContains(string $needle, string $haystack, string $message = ''): void
    {
        if (!str_contains($haystack, $needle)) {
            throw new RuntimeException(($message !== '' ? $message . ' — ' : '')
                . '"' . $needle . '" not found in: ' . mb_substr($haystack, 0, 400));
        }
    }

    public static function assertStringNotContains(string $needle, string $haystack, string $message = ''): void
    {
        if (str_contains($haystack, $needle)) {
            throw new RuntimeException(($message !== '' ? $message . ' — ' : '')
                . '"' . $needle . '" unexpectedly found in: ' . mb_substr($haystack, 0, 400));
        }
    }

    public static function summary(): int
    {
        $failed = 0;
        foreach (self::$results as $result) {
            if ($result['ok']) {
                echo "  ✅ " . $result['name'] . "\n";
            } else {
                $failed++;
                echo "  ❌ " . $result['name'] . "\n      " . $result['message'] . "\n";
            }
        }
        $total = count(self::$results);
        echo "\n" . ($failed === 0 ? '✅ ' : '❌ ') . ($total - $failed) . "/$total tests passed\n";
        return $failed === 0 ? 0 : 1;
    }
}
