<?php
/**
 * Compatibility gate: proves the source only uses syntax and functions that
 * exist in PHP 8.2, and avoids everything deprecated in 8.3 / 8.4.
 *
 * Usage:  php tests/compat.php
 */
declare(strict_types=1);

require __DIR__ . '/TestCase.php';

const MIN_VERSION = '8.2';
const MAX_TESTED  = '8.4';

/** Syntax introduced after 8.2 — must not appear anywhere. */
const FORBIDDEN_SYNTAX = [
    'ثابت کلاس نوع‌دار (۸.۳)'            => '/^\s*(?:public|protected|private|final)?\s*const\s+(?:int|string|float|bool|array|iterable|object|mixed|self|static|\?[A-Z])/mi',
    'دسترسی پویا به ثابت کلاس (۸.۳)'     => '/::\{\s*\$/',
    'فراخوانی بدون پرانتز روی new (۸.۴)' => '/(?<!\()\bnew\s+[A-Za-z_\\\\][A-Za-z0-9_\\\\]*\s*\((?:[^()]|\([^()]*\))*\)\s*->/',
    'property hooks (۸.۴)'               => '/\$this\s*=>\s*|\bget\s*=>\s*[^;]+;\s*\}\s*$/m',
    'asymmetric visibility (۸.۴)'        => '/\b(?:public|protected|private)\s+(?:public|protected|private)\(set\)/',
];

/** Functions/classes that only exist in 8.3+ or 8.4+. */
const FORBIDDEN_CALLS = [
    'json_validate (۸.۳)'      => '/\bjson_validate\s*\(/',
    'mb_str_pad (۸.۳)'         => '/\bmb_str_pad\s*\(/',
    'str_increment (۸.۳)'      => '/\bstr_(?:in|de)crement\s*\(/',
    'array_find/any/all (۸.۴)' => '/\barray_(?:find|find_key|any|all)\s*\(/',
    'mb_trim (۸.۴)'            => '/\bmb_(?:trim|ltrim|rtrim|ucfirst|lcfirst)\s*\(/',
    'Attribute #[Override] (۸.۳)' => '/#\[\s*\\\\?Override\s*\]/',
];

/** Deprecated in 8.4 — would print notices on a modern host. */
const DEPRECATED_IN_84 = [
    'پارامتر nullable ضمنی'  => '/function\s+\w+\s*\([^)]*?(?<![?|\w])\b(?:int|float|string|bool|array|callable|iterable|object|self|static|[A-Z]\w*)\s+\$\w+\s*=\s*null/s',
    'E_STRICT'               => '/\bE_STRICT\b/',
    'session_set_save_handler با چند آرگومان' => '/session_set_save_handler\s*\([^)]*,[^)]*,/',
];

$root  = dirname(__DIR__);
$files = [];
$it = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS));
foreach ($it as $file) {
    $path = $file->getPathname();
    if (!str_ends_with($path, '.php') || str_contains($path, '/dist/')) {
        continue;
    }
    $files[] = $path;
}
sort($files);

TestRunner::group('سازگاری PHP ' . MIN_VERSION . ' تا ' . MAX_TESTED);

TestRunner::test('همه فایل‌ها بدون خطای نحوی هستند', function () use ($files): void {
    foreach ($files as $file) {
        $output = [];
        $code   = 0;
        exec(escapeshellarg(PHP_BINARY) . ' -l ' . escapeshellarg($file) . ' 2>&1', $output, $code);
        TestRunner::assertSame(0, $code, $file . ': ' . implode(' ', $output));
    }
});

TestRunner::test('هیچ نحو مخصوص PHP ۸.۳/۸.۴ استفاده نشده', function () use ($files): void {
    foreach ($files as $file) {
        if (str_ends_with($file, 'tests/compat.php')) {
            continue; // this file names the patterns on purpose
        }
        $source = (string) file_get_contents($file);
        foreach (FORBIDDEN_SYNTAX as $label => $pattern) {
            TestRunner::assertSame(0, preg_match($pattern, $source), basename($file) . ' → ' . $label);
        }
    }
});

TestRunner::test('هیچ تابع مخصوص PHP ۸.۳/۸.۴ فراخوانی نشده', function () use ($files): void {
    foreach ($files as $file) {
        if (str_ends_with($file, 'tests/compat.php')) {
            continue; // this file names the patterns on purpose
        }
        $source = (string) file_get_contents($file);
        foreach (FORBIDDEN_CALLS as $label => $pattern) {
            TestRunner::assertSame(0, preg_match($pattern, $source), basename($file) . ' → ' . $label);
        }
    }
});

TestRunner::test('هیچ الگوی منسوخ‌شده در PHP ۸.۴ وجود ندارد', function () use ($files): void {
    foreach ($files as $file) {
        if (str_ends_with($file, 'tests/compat.php')) {
            continue; // this file names the patterns on purpose
        }
        $source = (string) file_get_contents($file);
        foreach (DEPRECATED_IN_84 as $label => $pattern) {
            TestRunner::assertSame(0, preg_match($pattern, $source), basename($file) . ' → ' . $label);
        }
    }
});

TestRunner::test('همه توابع استفاده‌شده در PHP ' . MIN_VERSION . ' وجود دارند', function () use ($files): void {
    // Functions the project relies on that were added at some point; all of
    // these exist in 8.2 and are asserted to exist on the running interpreter.
    $required = [
        'str_starts_with', 'str_ends_with', 'str_contains', 'array_is_list',
        'mb_str_split', 'mb_ord', 'json_encode', 'json_decode', 'hash_equals',
        'random_bytes', 'curl_init', 'preg_replace_callback', 'array_column',
    ];
    foreach ($required as $function) {
        TestRunner::assertTrue(function_exists($function), 'missing function: ' . $function);
    }
});

TestRunner::test('مفسر جاری در محدوده پشتیبانی‌شده است', function (): void {
    TestRunner::assertTrue(
        version_compare(PHP_VERSION, MIN_VERSION, '>='),
        'PHP ' . PHP_VERSION . ' پایین‌تر از حداقل پشتیبانی‌شده است.'
    );
});

TestRunner::test('اجرای کامل بدون هیچ Deprecated/Warning/Notice', function () use ($root): void {
    $output = [];
    $code   = 0;
    exec(
        escapeshellarg(PHP_BINARY) . ' -d error_reporting=E_ALL -d display_errors=1 '
        . escapeshellarg($root . '/tests/run.php') . ' 2>&1',
        $output,
        $code
    );
    $noise = array_values(array_filter(
        $output,
        static fn (string $line) => (bool) preg_match('/\b(Deprecated|Warning|Notice|Strict Standards|Fatal error)\b/i', $line)
    ));
    TestRunner::assertSame([], $noise, 'runtime noise: ' . implode(' | ', array_slice($noise, 0, 3)));
    TestRunner::assertSame(0, $code, 'test suite exited with ' . $code);
});

exit(TestRunner::summary());
