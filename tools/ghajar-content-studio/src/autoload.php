<?php
/**
 * Ghajar Content Studio - PSR-4 style autoloader (no composer needed).
 */
declare(strict_types=1);

define('GCS_ROOT', dirname(__DIR__));
define('GCS_SRC', GCS_ROOT . '/src');
define('GCS_STORAGE', GCS_ROOT . '/storage');

spl_autoload_register(static function (string $class): void {
    $prefix = 'Ghajar\\Studio\\';
    if (!str_starts_with($class, $prefix)) {
        return;
    }
    $relative = substr($class, strlen($prefix));
    $path = GCS_SRC . '/' . str_replace('\\', '/', $relative) . '.php';
    if (is_file($path)) {
        require_once $path;
    }
});

mb_internal_encoding('UTF-8');
