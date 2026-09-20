<?php
/**
 * Telegram webhook endpoint for Ghajar Content Studio.
 * Telegram only needs a 200 response, so every error is logged, never printed.
 */
declare(strict_types=1);

// Never let a host-specific notice or deprecation leak into the JSON reply
// (error output differs between PHP 8.2, 8.3 and 8.4 configurations).
@ini_set('display_errors', '0');
@ini_set('html_errors', '0');

require __DIR__ . '/src/autoload.php';

use Ghajar\Studio\Bot\Router;
use Ghajar\Studio\Core\Config;
use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Support\Logger;
use Ghajar\Studio\Telegram\Api;

header('Content-Type: application/json; charset=utf-8');

if (!Config::exists()) {
    http_response_code(503);
    echo '{"ok":false}';
    exit;
}

$expected = (string) Config::get('webhook_secret', '');
$provided = (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');
if ($expected === '' || !hash_equals($expected, $provided)) {
    http_response_code(403);
    echo '{"ok":false}';
    exit;
}

$raw = file_get_contents('php://input') ?: '';
if ($raw === '' || strlen($raw) > 1_000_000) {
    http_response_code(400);
    echo '{"ok":false}';
    exit;
}

$update = json_decode($raw, true);
if (!is_array($update)) {
    http_response_code(400);
    echo '{"ok":false}';
    exit;
}

// Answer Telegram immediately; the work continues after the connection closes.
http_response_code(200);
echo '{"ok":true}';
if (function_exists('fastcgi_finish_request')) {
    @fastcgi_finish_request();
}

try {
    Database::connect();
    Settings::flush();
    (new Router(Api::fromConfig()))->handle($update);
} catch (Throwable $e) {
    Logger::error('webhook failure', [
        'error' => $e->getMessage(),
        'file'  => basename($e->getFile()) . ':' . $e->getLine(),
    ]);
}
