<?php

error_reporting(E_ALL & ~E_DEPRECATED & ~E_STRICT);
@ini_set('display_errors', '0');
@ini_set('log_errors', '1');
@ini_set('default_charset', 'UTF-8');

if (!defined('RX_INSTALLER_RUNNING')) {
    define('RX_INSTALLER_RUNNING', true);
}

if (!headers_sent()) {
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
    header('Pragma: no-cache');
    header('Expires: 0');
    header('X-LiteSpeed-Cache-Control: no-cache');
}

session_start();

require_once __DIR__ . '/includes/requirements.php';
require_once __DIR__ . '/includes/helpers.php';
require_once __DIR__ . '/includes/icons.php';

$rootDirectory = dirname(__DIR__) . '/';
$configDirectory = $rootDirectory . 'config.php';
$tablesDirectory = $rootDirectory . 'table.php';

$projectDepthInfo = rx_project_subdirectory_depth($rootDirectory);
$isRootExecution = $projectDepthInfo['depth'] <= 0;

$uPOST = rx_sanitize_input($_POST);
$rxAction = $uPOST['rx_action'] ?? '';

if (($_SESSION['rx_step'] ?? '') === 'success' && !rx_config_is_already_installed($configDirectory)) {
    unset($_SESSION['rx_step'], $_SESSION['rx_success_messages'], $_SESSION['rx_bot_username'], $_SESSION['rx_requirement_checks'], $_SESSION['rx_requirements_passed']);
}

if (!$isRootExecution && $rxAction === 'cleanup') {
    header('Content-Type: application/json; charset=utf-8');
    if (($_SESSION['rx_step'] ?? '') === 'success') {
        rx_cleanup_installer(__DIR__, 'installer_shutdown');
        echo json_encode(['ok' => true]);
    } else {
        echo json_encode(['ok' => false]);
    }
    exit;
}

if ($isRootExecution) {
    $rootBlockedPath = $projectDepthInfo['path'];
    $rootBlockedDomain = $_SERVER['HTTP_HOST'] ?? '';
    $pageTitle = 'اجرای اینستالر در ریشه مجاز نیست';
    $activeView = 'root_blocked';
} else {
    $requirementChecks = rx_run_requirement_checks($rootDirectory);
    $_SESSION['rx_requirement_checks'] = $requirementChecks;
    $requirementsPassed = rx_requirement_checks_passed($requirementChecks);
    $_SESSION['rx_requirements_passed'] = $requirementsPassed;

    if ($rxAction === 'proceed' && $requirementsPassed) {
        $_SESSION['rx_step'] = 'install';
    }

    $currentStepName = $_SESSION['rx_step'] ?? 'requirements';
    if ($currentStepName !== 'requirements' && !$requirementsPassed) {
        $currentStepName = 'requirements';
        $_SESSION['rx_step'] = 'requirements';
    }

    $ERROR = [];
    $SUCCESS = [];
    $success = false;
    $tgBot = [];
    $formValues = $uPOST;

    $tempPath = dirname(dirname($_SERVER['SCRIPT_NAME']));
    $tempPath = str_replace('//', '/', '/' . trim($tempPath, '/'));
    $webAddress = rtrim(($_SERVER['HTTP_HOST'] ?? '') . $tempPath, '/') . '/';
    $defaultWebhookAddress = $webAddress . 'index.php';

    if ($currentStepName === 'install' && !file_exists($configDirectory)) {
        $ERROR[] = 'فایل های پروژه ناقص هستند.';
        $ERROR[] = "فایل های پروژه را مجددا دانلود و بارگذاری کنید (<a href='https://github.com/Mmd-Amir/Faoxima/releases/'>Github</a>)";
    }

    if ($currentStepName === 'install' && $rxAction === 'install' && empty($ERROR)) {
        $rawConfigData = file_get_contents($configDirectory);
        $tgAdminId = $uPOST['admin_id'] ?? '';
        $tgBotToken = $uPOST['tg_bot_token'] ?? '';
        $dbInfo = [
            'host' => getenv('DB_HOST') ?: 'localhost',
            'name' => $uPOST['database_name'] ?? '',
            'username' => $uPOST['database_username'] ?? '',
            'password' => $uPOST['database_password'] ?? '',
        ];
        $inputUrl = $uPOST['bot_address_webhook'] ?? $defaultWebhookAddress;
        $document = rx_normalize_domain_address($inputUrl);
        if ($document === null) {
            $ERROR[] = 'آدرس ارائه شده برای ربات نامعتبر است.';
        }
        if (!rx_is_https()) {
            $ERROR[] = 'برای فعال سازی ربات تلگرام نیازمند فعال بودن SSL (https) هستید';
            $ERROR[] = '<i>اگر از فعال بودن SSL مطمئن هستید، سرور پشت proxy/CDN (مثل Cloudflare) است – headers را در cPanel چک کنید یا با https مستقیم باز کنید.</i>';
            $sslLink = 'https://' . ($_SERVER['HTTP_HOST'] ?? '') . ($_SERVER['SCRIPT_NAME'] ?? '');
            $ERROR[] = '<a href="' . rx_escape_html($sslLink) . '">' . rx_escape_html($sslLink) . '</a>';
        }
        $isValidToken = rx_is_valid_telegram_token($tgBotToken);
        if (!$isValidToken) {
            $ERROR[] = 'توکن ربات صحیح نمی باشد.';
        }
        if (!rx_is_valid_telegram_id($tgAdminId)) {
            $ERROR[] = 'آیدی عددی ادمین نامعتبر است.';
        }
        if ($isValidToken) {
            $tgBot['details'] = rx_get_contents('https://api.telegram.org/bot' . $tgBotToken . '/getMe');
            if (empty($tgBot['details']['ok'])) {
                $ERROR[] = 'توکن ربات را بررسی کنید. <i>عدم توانایی دریافت جزئیات ربات.</i>';
            } else {
                $tgBot['recognition'] = rx_get_contents('https://api.telegram.org/bot' . $tgBotToken . '/getChat?chat_id=' . $tgAdminId);
                if (empty($tgBot['recognition']['ok'])) {
                    $ERROR[] = '<b>عدم شناسایی مدیر ربات:</b>';
                    $ERROR[] = 'ابتدا ربات را فعال/استارت کنید با اکانت که میخواهید مدیر اصلی ربات باشد.';
                    $ERROR[] = "<a href='https://t.me/" . rx_escape_html($tgBot['details']['result']['username']) . "'>@" . rx_escape_html($tgBot['details']['result']['username']) . '</a>';
                }
            }
        }

        if (empty($ERROR)) {
            try {
                $dsn = 'mysql:host=' . $dbInfo['host'] . ';dbname=' . $dbInfo['name'] . ';charset=utf8mb4';
                $pdo = new PDO($dsn, $dbInfo['username'], $dbInfo['password']);
                $SUCCESS[] = 'اتصال به دیتابیس موفقیت آمیز بود!';
            } catch (\PDOException $e) {
                $dbAutoCreated = false;
                try {
                    $serverDsn = 'mysql:host=' . $dbInfo['host'] . ';charset=utf8mb4';
                    $serverPdo = new PDO($serverDsn, $dbInfo['username'], $dbInfo['password']);
                    $serverPdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
                    $quotedDbName = str_replace('`', '``', (string) $dbInfo['name']);
                    try {
                        $serverPdo->exec('CREATE DATABASE IF NOT EXISTS `' . $quotedDbName . '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
                        $dbAutoCreated = true;
                    } catch (\PDOException $createError) {
                        $dbAutoCreated = false;
                    }
                    $serverPdo = null;
                } catch (\PDOException $serverError) {
                    $dbAutoCreated = false;
                }
                if ($dbAutoCreated) {
                    try {
                        $dsn = 'mysql:host=' . $dbInfo['host'] . ';dbname=' . $dbInfo['name'] . ';charset=utf8mb4';
                        $pdo = new PDO($dsn, $dbInfo['username'], $dbInfo['password']);
                        $SUCCESS[] = 'اتصال به دیتابیس موفقیت آمیز بود!';
                    } catch (\PDOException $reconnectError) {
                        $ERROR[] = 'عدم اتصال به دیتابیس:';
                        $ERROR[] = 'اطلاعات ورودی را بررسی کنید.';
                        $ERROR[] = '<code>' . rx_escape_html($e->getMessage()) . '</code>';
                    }
                } else {
                    $ERROR[] = 'عدم اتصال به دیتابیس:';
                    $ERROR[] = 'اطلاعات ورودی را بررسی کنید.';
                    $ERROR[] = '<code>' . rx_escape_html($e->getMessage()) . '</code>';
                }
            }
        }

        if (empty($ERROR)) {
            $replacements = [
                '{database_name}' => $dbInfo['name'],
                '{username_db}' => $dbInfo['username'],
                '{password_db}' => $dbInfo['password'],
                '{db_host}' => $dbInfo['host'],
                '{API_KEY}' => $tgBotToken,
                '{admin_number}' => $tgAdminId,
                '{domain_name}' => $document['address'],
                '{username_bot}' => $tgBot['details']['result']['username'],
            ];
            $replacementCount = 0;
            $newConfigData = rx_update_config_values($rawConfigData, $replacements, $replacementCount);
            if ($replacementCount === 0 || file_put_contents($configDirectory, $newConfigData) === false) {
                $ERROR[] = 'خطا در زمان بازنویسی اطلاعات فایل اصلی ربات';
                $ERROR[] = "فایل های پروژه را مجددا دانلود و بارگذاری کنید (<a href='https://github.com/Mmd-Amir/Faoxima/releases/'>Github</a>)";
            } else {
                clearstatcache(true, $configDirectory);
                if (function_exists('opcache_invalidate')) {
                    @opcache_invalidate($configDirectory, true);
                }

                $tablesMigrated = rx_run_table_migrations($rootDirectory, $dbInfo);
                if (!$tablesMigrated['ok']) {
                    $ERROR[] = 'خطا در ایجاد/بروزرسانی جداول دیتابیس:';
                    $ERROR[] = '<code>' . rx_escape_html($tablesMigrated['message']) . '</code>';
                }
                if (empty($ERROR)) {
                    $SUCCESS[] = 'جداول دیتابیس ایجاد/بروزرسانی شد';
                    rx_ensure_admin_record($dbInfo, $tgAdminId);
                    $SUCCESS[] = 'Webhook تنظیم شد';
                    $telegramMessage = urlencode(" \xF0\x9F\xA4\x96 نصب با موفقیت انجام شد!\nشما به عنوان ادمین معرفی شدید.");
                    $replyMarkup = urlencode(json_encode([
                        'inline_keyboard' => [[['text' => 'شروع ربات', 'callback_data' => 'start']]],
                    ], JSON_UNESCAPED_UNICODE));
                    rx_get_contents("https://api.telegram.org/bot{$tgBotToken}/sendMessage?chat_id={$tgAdminId}&text={$telegramMessage}&reply_markup={$replyMarkup}");
                    $success = true;
                    $_SESSION['rx_step'] = 'success';
                    $_SESSION['rx_success_messages'] = $SUCCESS;
                    $_SESSION['rx_bot_username'] = $tgBot['details']['result']['username'] ?? '';
                }
            }
        }
    }

    $activeView = $success ? 'success' : $currentStepName;
    if ($activeView === 'success') {
        $successMessages = $_SESSION['rx_success_messages'] ?? [];
        $botUsername = $_SESSION['rx_bot_username'] ?? '';
    }
    $pageTitle = 'نصب خودکار ربات فاکسیما';
}
?>
<!DOCTYPE html>
<html dir="rtl" lang="fa" data-theme="purple">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="theme-color" content="#131316">
    <title><?php echo rx_escape_html($pageTitle); ?></title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="assets/installer.css">
</head>
<body>
<?php echo rx_icon_sprite(); ?>

<div class="app-shell">
    <header class="app-header">
        <div class="app-header-inner">
            <a class="brand" href="#">
                <span class="brand-mark"><img src="assets/img/faoxima.jpg" alt="فاکسیما"></span>
                <span>فاکسیما</span>
            </a>
        </div>
    </header>

    <main class="app-main">
        <div class="hero">
            <div class="hero-badge">
                <span class="pulse-dot"></span>
                <span>نصب کننده‌ی هوشمند</span>
            </div>
            <h1>نصب خودکار <span class="accent">ربات فاکسیما</span></h1>
            <p>تنها چند مرحله ساده تا راه‌اندازی کامل ربات شما — بدون نیاز به دانش فنی پیچیده.</p>
        </div>

        <?php if ($isRootExecution): ?>
            <?php require __DIR__ . '/steps/root_blocked.php'; ?>
        <?php else: ?>
            <div class="stepper">
                <div class="step <?php echo $activeView === 'requirements' ? 'is-active' : 'is-completed'; ?>">
                    <span class="step-num"><?php echo $activeView === 'requirements' ? '1' : '<svg style="width:12px;height:12px"><use href="#i-check"/></svg>'; ?></span>
                    <span class="step-label">بررسی پیش‌نیاز</span>
                </div>
                <div class="step-divider"></div>
                <div class="step <?php echo $activeView === 'install' ? 'is-active' : ($activeView === 'success' ? 'is-completed' : ''); ?>">
                    <span class="step-num">2</span>
                    <span class="step-label">اطلاعات نصب</span>
                </div>
                <div class="step-divider"></div>
                <div class="step <?php echo $activeView === 'success' ? 'is-active' : ''; ?>">
                    <span class="step-num">3</span>
                    <span class="step-label">پایان</span>
                </div>
            </div>

            <?php if (!empty($ERROR)): ?>
                <div class="alert alert-danger">
                    <svg><use href="#i-x-circle"/></svg>
                    <div><?php echo implode('<br>', $ERROR); ?></div>
                </div>
            <?php endif; ?>

            <?php if ($activeView === 'requirements'): ?>
                <?php require __DIR__ . '/steps/requirements.php'; ?>
            <?php elseif ($activeView === 'install'): ?>
                <?php require __DIR__ . '/steps/install_form.php'; ?>
            <?php elseif ($activeView === 'success'): ?>
                <?php require __DIR__ . '/steps/success.php'; ?>
            <?php endif; ?>
        <?php endif; ?>
    </main>

    <footer class="app-footer">
        <p>
            Faoxima Installer
            ·
            <a href="https://github.com/Mmd-Amir/Faoxima" target="_blank" rel="noopener">گیت‌هاب</a>
            ·
            <a href="https://t.me/faoxima" target="_blank" rel="noopener">تلگرام</a>
            ·
            &copy; <?php echo date('Y'); ?>
        </p>
    </footer>
</div>

<script src="assets/installer.js"></script>
</body>
</html>
