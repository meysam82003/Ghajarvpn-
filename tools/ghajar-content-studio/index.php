<?php
/** Public landing page: never exposes anything about the bot. */
declare(strict_types=1);

require __DIR__ . '/src/autoload.php';

use Ghajar\Studio\Core\Config;

http_response_code(200);
header('Content-Type: text/html; charset=utf-8');

$installed = Config::exists() && is_file(GCS_STORAGE . '/installed.lock');
?><!DOCTYPE html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow"><title>Ghajar Content Studio</title>
<style>body{background:#050807;color:#E6F2EC;font-family:Tahoma,sans-serif;display:flex;
min-height:100vh;align-items:center;justify-content:center;margin:0}
.c{background:#101816;border:1px solid #1B2A25;border-radius:18px;padding:28px;text-align:center;max-width:420px}
a{color:#24D98B}</style></head>
<body><div class="c"><h1>👑 Ghajar Content Studio</h1>
<?php if ($installed): ?>
<p>ربات نصب شده و فعال است.</p>
<?php else: ?>
<p>هنوز نصب نشده است.</p><p><a href="install.php">شروع نصب</a></p>
<?php endif; ?>
</div></body></html>
