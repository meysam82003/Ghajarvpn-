<?php
/**
 * Ghajar Content Studio — web installer.
 *
 * Only two inputs are required: bot token and numeric admin id.
 * Everything else (database, tables, settings, webhook, secrets) is automatic.
 */
declare(strict_types=1);

require __DIR__ . '/src/autoload.php';

use Ghajar\Studio\Core\Config;
use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Migrations;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Support\Str;
use Ghajar\Studio\Telegram\Api;
use Ghajar\Studio\Telegram\ApiException;

const LOCK_FILE    = GCS_STORAGE . '/installed.lock';
const CLAIM_FILE   = GCS_STORAGE . '/install-claim.json';
const ATTEMPT_FILE = GCS_STORAGE . '/install-attempts.json';
const CLAIM_TTL    = 3600;           // the installer belongs to one browser for an hour
const MAX_ATTEMPTS = 8;              // per window
const ATTEMPT_WIN  = 900;            // 15 minutes

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('Cache-Control: no-store');

@mkdir(GCS_STORAGE, 0700, true);

// --------------------------------------------------------------------- guards

if (is_file(LOCK_FILE)) {
    render_page('locked', ['message' => 'این نصب‌کننده قبلاً با موفقیت اجرا شده و قفل شده است.']);
    exit;
}

/** The first browser that opens the installer owns it until it finishes. */
function claim_ok(): bool
{
    $cookie = (string) ($_COOKIE['gcs_install'] ?? '');
    $claim  = is_file(CLAIM_FILE) ? json_decode((string) file_get_contents(CLAIM_FILE), true) : null;

    if (is_array($claim) && (int) ($claim['expires'] ?? 0) > time()) {
        return $cookie !== '' && hash_equals((string) ($claim['hash'] ?? ''), hash('sha256', $cookie));
    }

    // No live claim: create one for this visitor.
    $token = Str::randomToken(32);
    file_put_contents(CLAIM_FILE, json_encode([
        'hash'    => hash('sha256', $token),
        'expires' => time() + CLAIM_TTL,
        'csrf'    => Str::randomToken(32),
    ]), LOCK_EX);
    @chmod(CLAIM_FILE, 0600);
    setcookie('gcs_install', $token, [
        'expires'  => time() + CLAIM_TTL,
        'path'     => rtrim(dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/')), '/') . '/',
        'secure'   => is_https(),
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    $_COOKIE['gcs_install'] = $token;
    return true;
}

function claim(): array
{
    $claim = is_file(CLAIM_FILE) ? json_decode((string) file_get_contents(CLAIM_FILE), true) : [];
    return is_array($claim) ? $claim : [];
}

function is_https(): bool
{
    if (($_SERVER['HTTPS'] ?? '') !== '' && strtolower((string) $_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    if ((int) ($_SERVER['SERVER_PORT'] ?? 0) === 443) {
        return true;
    }
    foreach (['HTTP_X_FORWARDED_PROTO', 'HTTP_X_FORWARDED_SCHEME', 'HTTP_X_SCHEME', 'REQUEST_SCHEME'] as $key) {
        if (strtolower((string) ($_SERVER[$key] ?? '')) === 'https') {
            return true;
        }
    }
    return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_SSL'] ?? '')) === 'on';
}

function base_url(): string
{
    $host = (string) ($_SERVER['HTTP_HOST'] ?? $_SERVER['SERVER_NAME'] ?? 'localhost');
    $dir  = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/install.php'))), '/');
    return 'https://' . $host . $dir;
}

function rate_limited(): bool
{
    $data = is_file(ATTEMPT_FILE) ? json_decode((string) file_get_contents(ATTEMPT_FILE), true) : null;
    $now  = time();
    if (!is_array($data) || ($now - (int) ($data['start'] ?? 0)) > ATTEMPT_WIN) {
        $data = ['start' => $now, 'count' => 0];
    }
    $data['count'] = (int) $data['count'] + 1;
    file_put_contents(ATTEMPT_FILE, json_encode($data), LOCK_EX);
    @chmod(ATTEMPT_FILE, 0600);
    return $data['count'] > MAX_ATTEMPTS;
}

if (!claim_ok()) {
    render_page('locked', [
        'message' => 'این نصب‌کننده توسط مرورگر دیگری باز شده است. اگر خودتان هستید، فایل '
            . '<code>storage/install-claim.json</code> را حذف کرده و دوباره تلاش کنید.',
    ]);
    exit;
}

// -------------------------------------------------------------- install steps

/** @return array{ok:bool,steps:list<array{title:string,ok:bool,detail:string}>,data:array<string,mixed>} */
function run_install(string $token, string $adminIdRaw): array
{
    $steps = [];
    $data  = [];
    $fail  = static function (array &$steps, string $title, string $detail): array {
        $steps[] = ['title' => $title, 'ok' => false, 'detail' => $detail];
        return ['ok' => false, 'steps' => $steps, 'data' => []];
    };

    // 1. PHP + extensions
    if (version_compare(PHP_VERSION, '8.2.0', '<')) {
        return $fail($steps, 'بررسی نسخه PHP', 'نسخه PHP شما ' . PHP_VERSION . ' است. حداقل ۸.۲ لازم است.');
    }
    $missing = array_values(array_filter(
        ['pdo', 'pdo_sqlite', 'curl', 'mbstring', 'json', 'openssl'],
        static fn (string $ext) => !extension_loaded($ext)
    ));
    if ($missing !== []) {
        return $fail($steps, 'بررسی افزونه‌های PHP', 'این افزونه‌ها روی هاست فعال نیستند: ' . implode('، ', $missing));
    }
    $steps[] = ['title' => 'بررسی PHP و افزونه‌ها', 'ok' => true, 'detail' => 'PHP ' . PHP_VERSION . ' — همه افزونه‌های لازم فعال است.'];

    // 2. HTTPS
    if (!is_https()) {
        return $fail($steps, 'بررسی HTTPS', 'تلگرام فقط Webhook روی HTTPS معتبر را می‌پذیرد. ابتدا گواهی SSL دامنه را فعال کنید.');
    }
    $steps[] = ['title' => 'بررسی HTTPS', 'ok' => true, 'detail' => base_url()];

    // 3. writable storage
    if (!is_dir(GCS_STORAGE) && !@mkdir(GCS_STORAGE, 0700, true)) {
        return $fail($steps, 'بررسی دسترسی نوشتن', 'پوشه storage ساخته نشد. دسترسی نوشتن را فعال کنید.');
    }
    if (!is_writable(GCS_STORAGE)) {
        return $fail($steps, 'بررسی دسترسی نوشتن', 'پوشه storage قابل نوشتن نیست (chmod 755 یا 775).');
    }
    $steps[] = ['title' => 'بررسی دسترسی نوشتن', 'ok' => true, 'detail' => 'پوشه storage قابل نوشتن است.'];

    // 4. token format + getMe
    if (!preg_match('/^\d{6,12}:[A-Za-z0-9_\-]{30,}$/', $token)) {
        return $fail($steps, 'اعتبارسنجی توکن', 'قالب توکن درست نیست. توکن را از @BotFather کپی کنید.');
    }
    try {
        $me = (new Api($token))->getMe();
    } catch (ApiException $e) {
        return $fail($steps, 'اعتبارسنجی توکن', $e->friendly());
    }
    $data['bot'] = $me;
    $steps[] = ['title' => 'اعتبارسنجی توکن', 'ok' => true, 'detail' => 'ربات: @' . (string) ($me['username'] ?? '')];

    // 5. admin id
    $adminId = (int) Str::toEnglishDigits(trim($adminIdRaw));
    if ($adminId <= 0 || $adminId > 999_999_999_999) {
        return $fail($steps, 'اعتبارسنجی آیدی مدیر', 'آیدی عددی معتبر نیست. آن را از @userinfobot بگیرید.');
    }
    $data['admin_id'] = $adminId;
    $steps[] = ['title' => 'اعتبارسنجی آیدی مدیر', 'ok' => true, 'detail' => (string) $adminId];

    // 6. database + migrations + seed
    $dbDir  = GCS_STORAGE . '/data';
    $dbFile = $dbDir . '/studio-' . bin2hex(random_bytes(6)) . '.sqlite';
    try {
        $pdo = Database::connect($dbFile);
        Database::set($pdo);
        Migrations::run($pdo);
        Migrations::seed($pdo, $adminId);
    } catch (Throwable $e) {
        return $fail($steps, 'ساخت دیتابیس', 'ایجاد دیتابیس SQLite ناموفق بود: ' . $e->getMessage());
    }
    protect_storage();
    $steps[] = ['title' => 'ساخت دیتابیس و جدول‌ها', 'ok' => true, 'detail' => 'SQLite آماده شد (داخل storage و محافظت‌شده).'];

    // 7. config file
    $secret = Str::randomToken(24);
    try {
        Config::write([
            'bot_token'      => $token,
            'webhook_secret' => $secret,
            'db_file'        => $dbFile,
            'installed_at'   => gmdate('c'),
            'version'        => '1.0.0',
        ]);
    } catch (Throwable $e) {
        return $fail($steps, 'ذخیره تنظیمات امن', $e->getMessage());
    }
    $steps[] = ['title' => 'ذخیره تنظیمات امن', 'ok' => true, 'detail' => 'توکن فقط داخل storage/config.php نگهداری می‌شود.'];

    // 8. webhook
    $webhookUrl = base_url() . '/webhook.php';
    try {
        (new Api($token))->setWebhook($webhookUrl, $secret);
        $info = (new Api($token))->getWebhookInfo();
    } catch (ApiException $e) {
        return $fail($steps, 'ثبت Webhook', $e->friendly());
    }
    if ((string) ($info['url'] ?? '') !== $webhookUrl) {
        return $fail($steps, 'ثبت Webhook', 'تلگرام آدرس Webhook را تأیید نکرد. آدرس سایت و گواهی SSL را بررسی کنید.');
    }
    $data['webhook'] = $webhookUrl;
    $steps[] = ['title' => 'ثبت Webhook', 'ok' => true, 'detail' => $webhookUrl];

    // 9. settings from the verified bot
    Settings::set('bot_username', (string) ($me['username'] ?? ''));
    Settings::set('bot_name', (string) ($me['first_name'] ?? ''));
    Settings::set('admin_id', (string) $adminId);
    $steps[] = ['title' => 'ثبت تنظیمات اولیه', 'ok' => true, 'detail' => 'کانال پیش‌فرض: @Ghajarvpn'];

    // 10. lock
    file_put_contents(LOCK_FILE, json_encode([
        'installed_at' => gmdate('c'),
        'bot'          => (string) ($me['username'] ?? ''),
        'admin_id'     => $adminId,
    ], JSON_UNESCAPED_UNICODE), LOCK_EX);
    @chmod(LOCK_FILE, 0600);
    @unlink(CLAIM_FILE);
    @unlink(ATTEMPT_FILE);
    $steps[] = ['title' => 'قفل کردن نصب‌کننده', 'ok' => true, 'detail' => 'دسترسی مجدد به install.php مسدود شد.'];

    return ['ok' => true, 'steps' => $steps, 'data' => $data];
}

/** Block direct web access to storage on both Apache and (best effort) others. */
function protect_storage(): void
{
    $htaccess = "Require all denied\n<IfModule !mod_authz_core.c>\n  Order allow,deny\n  Deny from all\n</IfModule>\n";
    @file_put_contents(GCS_STORAGE . '/.htaccess', $htaccess);
    @file_put_contents(GCS_STORAGE . '/index.php', "<?php http_response_code(404); exit;\n");
    @mkdir(GCS_STORAGE . '/data', 0700, true);
    @file_put_contents(GCS_STORAGE . '/data/.htaccess', $htaccess);
    @file_put_contents(GCS_STORAGE . '/data/index.php', "<?php http_response_code(404); exit;\n");
    @file_put_contents(GCS_STORAGE . '/logs/.htaccess', $htaccess);
}

// --------------------------------------------------------------------- routing

$result = null;
$error  = null;

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $csrf = (string) ($claim()['csrf'] ?? '');
    if ($csrf === '' || !hash_equals($csrf, (string) ($_POST['csrf'] ?? ''))) {
        $error = 'نشست نصب منقضی شده است. صفحه را تازه کنید و دوباره تلاش کنید.';
    } elseif (rate_limited()) {
        $error = 'تعداد تلاش‌های نصب زیاد بود. ۱۵ دقیقه دیگر دوباره تلاش کنید.';
    } else {
        $result = run_install(trim((string) ($_POST['bot_token'] ?? '')), (string) ($_POST['admin_id'] ?? ''));
    }
}

if ($result !== null && $result['ok']) {
    render_page('success', $result);
    exit;
}
render_page('form', ['result' => $result, 'error' => $error, 'csrf' => (string) ($claim()['csrf'] ?? '')]);

// ----------------------------------------------------------------- the view

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

/** @param array<string,mixed> $vars */
function render_page(string $view, array $vars): void
{
    $steps = (array) ($vars['result']['steps'] ?? ($vars['steps'] ?? []));
    ?><!DOCTYPE html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>نصب Ghajar Content Studio</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Vazirmatn:wght@400;500;700&display=swap" rel="stylesheet">
<style>
  :root{
    --bg:#050807; --surface:#0B1512; --card:#101816;
    --primary:#00A86B; --premium:#00B978; --highlight:#24D98B;
    --text:#E6F2EC; --muted:#8FA79C; --danger:#FF6B6B; --line:#1B2A25;
  }
  *{box-sizing:border-box}
  body{margin:0;background:radial-gradient(1200px 600px at 50% -10%,#0C1A15 0%,var(--bg) 60%);
       color:var(--text);font-family:Vazirmatn,system-ui,"Segoe UI",Tahoma,sans-serif;
       min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
  .wrap{width:100%;max-width:560px}
  .brand{text-align:center;margin-bottom:22px}
  .logo{width:62px;height:62px;border-radius:18px;margin:0 auto 12px;
        background:linear-gradient(145deg,var(--premium),var(--primary));
        display:flex;align-items:center;justify-content:center;font-size:30px;
        box-shadow:0 10px 30px rgba(0,168,107,.35)}
  h1{font-size:20px;margin:0 0 6px}
  .sub{color:var(--muted);font-size:13px;margin:0}
  .card{background:var(--card);border:1px solid var(--line);border-radius:18px;padding:22px;
        box-shadow:0 18px 48px rgba(0,0,0,.45)}
  label{display:block;font-size:13px;margin:0 0 8px;color:var(--text)}
  .hint{color:var(--muted);font-size:11.5px;margin:6px 2px 0}
  input{width:100%;padding:13px 14px;border-radius:12px;border:1px solid var(--line);
        background:var(--surface);color:var(--text);font-family:inherit;font-size:14px;
        direction:ltr;text-align:left}
  input:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px rgba(0,168,107,.18)}
  .field{margin-bottom:18px}
  button{width:100%;padding:14px;border:0;border-radius:12px;cursor:pointer;font-family:inherit;
         font-size:15px;font-weight:700;color:#04140D;
         background:linear-gradient(145deg,var(--highlight),var(--primary));
         box-shadow:0 10px 24px rgba(0,168,107,.3)}
  button:active{transform:translateY(1px)}
  .alert{border-radius:12px;padding:12px 14px;font-size:13px;margin-bottom:16px;line-height:1.8}
  .alert.err{background:rgba(255,107,107,.1);border:1px solid rgba(255,107,107,.35);color:#FFB3B3}
  .alert.ok{background:rgba(36,217,139,.1);border:1px solid rgba(36,217,139,.35);color:var(--highlight)}
  ul.steps{list-style:none;padding:0;margin:0 0 16px}
  ul.steps li{display:flex;gap:10px;padding:10px 12px;border:1px solid var(--line);
              border-radius:12px;margin-bottom:8px;background:var(--surface);font-size:13px}
  ul.steps li .t{font-weight:700}
  ul.steps li .d{color:var(--muted);font-size:11.5px;word-break:break-all;direction:ltr;text-align:left}
  .row{display:flex;justify-content:space-between;gap:10px;font-size:13px;padding:10px 0;
       border-bottom:1px solid var(--line)}
  .row b{color:var(--highlight)}
  a.btn{display:block;text-align:center;text-decoration:none;margin-top:18px;padding:14px;
        border-radius:12px;font-weight:700;color:#04140D;
        background:linear-gradient(145deg,var(--highlight),var(--primary))}
  code{background:var(--surface);padding:2px 6px;border-radius:6px;font-size:12px}
  footer{text-align:center;color:var(--muted);font-size:11px;margin-top:16px}
</style>
</head>
<body>
<div class="wrap">
  <div class="brand">
    <div class="logo">👑</div>
    <h1>Ghajar Content Studio</h1>
    <p class="sub">استودیوی ساخت، قالب‌بندی و انتشار پست کانال قاجار VPN</p>
  </div>
  <div class="card">
  <?php if ($view === 'locked'): ?>
    <div class="alert err">🔒 <?= $vars['message'] ?? '' ?></div>
    <p class="hint">برای نصب دوباره، فایل <code>storage/installed.lock</code> را حذف کنید.</p>
  <?php elseif ($view === 'success'): $bot = (array) ($vars['data']['bot'] ?? []); ?>
    <div class="alert ok">✅ ربات با موفقیت نصب شد.</div>
    <div class="row"><span>🤖 نام ربات</span><b><?= e((string) ($bot['first_name'] ?? '')) ?> (@<?= e((string) ($bot['username'] ?? '')) ?>)</b></div>
    <div class="row"><span>👤 مدیر</span><b><?= e((string) ($vars['data']['admin_id'] ?? '')) ?></b></div>
    <div class="row"><span>🌐 وضعیت Webhook</span><b>فعال</b></div>
    <div class="row"><span>🗄 وضعیت دیتابیس</span><b>آماده</b></div>
    <a class="btn" href="https://t.me/<?= e((string) ($bot['username'] ?? '')) ?>" target="_blank" rel="noopener">باز کردن ربات در تلگرام</a>
    <p class="hint">در ربات دستور <code>/start</code> را بفرستید تا منوی اصلی نمایش داده شود.
      برای انتشار در کانال، ربات را ادمین کانال کنید و مجوز «ارسال پیام» بدهید.</p>
  <?php else: ?>
    <?php if (!empty($vars['error'])): ?>
      <div class="alert err">⚠️ <?= e((string) $vars['error']) ?></div>
    <?php endif; ?>
    <?php if ($steps !== []): ?>
      <ul class="steps">
      <?php foreach ($steps as $step): ?>
        <li><span><?= $step['ok'] ? '✅' : '❌' ?></span>
          <span><span class="t"><?= e((string) $step['title']) ?></span><br>
          <span class="d"><?= e((string) $step['detail']) ?></span></span></li>
      <?php endforeach; ?>
      </ul>
      <div class="alert err">نصب کامل نشد. مشکل بالا را برطرف کنید و دوباره «نصب و راه‌اندازی» را بزنید.</div>
    <?php endif; ?>
    <form method="post" autocomplete="off">
      <input type="hidden" name="csrf" value="<?= e((string) ($vars['csrf'] ?? '')) ?>">
      <div class="field">
        <label for="bot_token">🤖 توکن ربات تلگرام</label>
        <input id="bot_token" name="bot_token" required placeholder="123456789:AA..." spellcheck="false">
        <p class="hint">توکن را از <b>@BotFather</b> بگیرید. توکن فقط در فایل محافظت‌شده ذخیره می‌شود.</p>
      </div>
      <div class="field">
        <label for="admin_id">👤 آیدی عددی ادمین</label>
        <input id="admin_id" name="admin_id" required placeholder="123456789" inputmode="numeric" spellcheck="false">
        <p class="hint">آیدی عددی خود را از <b>@userinfobot</b> بگیرید.</p>
      </div>
      <button type="submit">🚀 نصب و راه‌اندازی ربات</button>
    </form>
  <?php endif; ?>
  </div>
  <footer>Ghajar Content Studio v1.0.0</footer>
</div>
</body>
</html><?php
}
