<?php

function rx_config_is_already_installed(string $configDirectory): bool
{
    if (!is_file($configDirectory)) {
        return false;
    }
    $contents = @file_get_contents($configDirectory);
    if ($contents === false || $contents === '') {
        return false;
    }
    if (strpos($contents, '{API_KEY}') !== false || strpos($contents, '{database_name}') !== false) {
        return false;
    }
    if (!preg_match('/\$APIKEY\s*=\s*[\'"]([^\'"]*)[\'"]/', $contents, $matches)) {
        return false;
    }
    return trim($matches[1]) !== '';
}

if (!function_exists('rx_cleanup_installer')) {
    function rx_cleanup_installer(string $installerDir, string $trigger = 'installer'): bool
    {
        $rootDir = dirname($installerDir);
        $logsDir = $rootDir . DIRECTORY_SEPARATOR . 'logs';
        $flagFile = $logsDir . DIRECTORY_SEPARATOR . '.cleanup_failed';

        if ($trigger === 'config_bootstrap' && is_file($flagFile)) {
            $mtime = (int) @filemtime($flagFile);
            if ((time() - $mtime) < 300) {
                return false;
            }
        }

        if (!is_dir($installerDir)) {
            if (is_file($flagFile)) {
                @unlink($flagFile);
            }
            return true;
        }

        $indexFile = $installerDir . DIRECTORY_SEPARATOR . 'index.php';
        if (is_file($indexFile)) {
            @chmod($indexFile, 0666);
            @file_put_contents($indexFile, "<?php http_response_code(404); exit;\n");
        }

        @chmod($rootDir, 0777);
        @chmod($installerDir, 0777);

        $deleteRecursive = static function (string $dir) use (&$deleteRecursive): bool {
            if (!is_dir($dir)) {
                return true;
            }
            @chmod($dir, 0777);
            $items = @scandir($dir);
            if ($items === false) {
                return false;
            }

            $success = true;
            foreach ($items as $item) {
                if ($item === '.' || $item === '..') {
                    continue;
                }
                $path = $dir . DIRECTORY_SEPARATOR . $item;
                clearstatcache(true, $path);
                if (function_exists('opcache_invalidate')) {
                    @opcache_invalidate($path, true);
                }
                @chmod($path, 0777);

                if (is_dir($path) && !is_link($path)) {
                    if (!$deleteRecursive($path)) {
                        $success = false;
                    }
                    if (!@rmdir($path)) {
                        $success = false;
                    }
                } else {
                    if (!@unlink($path)) {
                        @chmod($path, 0666);
                        if (!@unlink($path)) {
                            $success = false;
                        }
                    }
                }
            }
            return $success;
        };

        $deleteRecursive($installerDir);
        clearstatcache(true, $installerDir);

        if (!is_dir($installerDir)) {
            if (is_file($flagFile)) {
                @unlink($flagFile);
            }
            return true;
        }

        if (@rmdir($installerDir)) {
            if (is_file($flagFile)) {
                @unlink($flagFile);
            }
            return true;
        }

        $escapedDir = escapeshellarg($installerDir);
        $shellCmd = strncasecmp(PHP_OS, 'WIN', 3) === 0
            ? "rmdir /s /q {$escapedDir} 2>&1"
            : "chmod -R 777 {$escapedDir} 2>&1; rm -rf {$escapedDir} 2>&1";

        if (function_exists('exec')) {
            @exec($shellCmd);
        } elseif (function_exists('shell_exec')) {
            @shell_exec($shellCmd);
        } elseif (function_exists('system')) {
            ob_start();
            @system($shellCmd);
            ob_end_clean();
        } elseif (function_exists('passthru')) {
            ob_start();
            @passthru($shellCmd);
            ob_end_clean();
        }

        clearstatcache(true, $installerDir);
        if (!is_dir($installerDir)) {
            if (is_file($flagFile)) {
                @unlink($flagFile);
            }
            return true;
        }

        @touch($flagFile);

        if ($trigger === 'installer_shutdown') {
            $phpBin = PHP_BINARY && is_executable(PHP_BINARY) ? PHP_BINARY : 'php';
            $cleanCode = "define('REFACTORED_LEGACY_ROOT', " . var_export($rootDir, true) . ');';
            $cleanCode .= 'require_once ' . var_export($rootDir . '/config.php', true) . ';';
            $cleanCode .= "if (function_exists('rx_cleanup_installer')) { rx_cleanup_installer(" . var_export($installerDir, true) . ", 'background'); }";
            $cmdStr = escapeshellarg($phpBin) . ' -r ' . escapeshellarg('sleep(1); ' . $cleanCode);
            if (strncasecmp(PHP_OS, 'WIN', 3) === 0) {
                @pclose(@popen('start /B ' . $cmdStr, 'r'));
            } else {
                @exec($cmdStr . ' > /dev/null 2>&1 &');
            }
        }

        return !is_dir($installerDir);
    }
}

function rx_is_https(): bool
{
    return (
        ($_SERVER['REQUEST_SCHEME'] ?? 'http') === 'https' ||
        ($_SERVER['HTTPS'] ?? 'off') === 'on' ||
        ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https'
    );
}

function rx_get_contents(string $url)
{
    $context = stream_context_create([
        'http' => ['timeout' => 30],
        'https' => ['timeout' => 30],
    ]);
    $response = @file_get_contents($url, false, $context);
    if ($response === false) {
        return ['ok' => false];
    }
    $decoded = json_decode($response, true);
    if ($decoded === null && json_last_error() !== JSON_ERROR_NONE) {
        return ['ok' => false];
    }
    return $decoded;
}

function rx_is_valid_telegram_token(string $token): bool
{
    return (bool) preg_match('/^\d{6,12}:[A-Za-z0-9_-]{35}$/', $token);
}

function rx_is_valid_telegram_id(string $id): bool
{
    return (bool) preg_match('/^\d{6,12}$/', $id);
}

function rx_sanitize_input($input, array $options = [])
{
    $defaultOptions = [
        'allow_html' => false,
        'allowed_tags' => '',
        'remove_spaces' => false,
        'max_length' => 0,
        'encoding' => 'UTF-8',
    ];
    $options = array_merge($defaultOptions, $options);
    if (is_array($input)) {
        return array_map(static function ($item) use ($options) {
            return rx_sanitize_input($item, $options);
        }, $input);
    }
    if ($input === null || $input === false) {
        return '';
    }
    $input = trim((string) $input);
    $input = preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', $input);
    if ($options['max_length'] > 0) {
        $input = mb_substr($input, 0, $options['max_length'], $options['encoding']);
    }
    if (!$options['allow_html']) {
        $input = strip_tags($input);
    } elseif (!empty($options['allowed_tags'])) {
        $input = strip_tags($input, $options['allowed_tags']);
    }
    if ($options['remove_spaces']) {
        $input = preg_replace('/\s+/', ' ', trim($input));
    }
    return $input;
}

function rx_normalize_domain_address(string $url): ?array
{
    $url = trim($url);
    if ($url === '') {
        return null;
    }
    if (!preg_match('#^https?://#i', $url)) {
        $url = 'https://' . $url;
    }
    $parsedUrl = parse_url($url);
    if (empty($parsedUrl['host'])) {
        return null;
    }
    $path = $parsedUrl['path'] ?? '';
    $path = preg_replace('#/index\.php$#i', '', $path);
    $path = preg_replace('#/installer/?$#', '', $path);
    $path = rtrim($path, '/');
    $path = ltrim($path, '/');
    $address = $parsedUrl['host'];
    if ($path !== '') {
        $address .= '/' . $path;
    }
    return ['address' => $address];
}

function rx_update_config_values(string $configContents, array $placeholderValues, int &$replacementCount = 0): string
{
    $replacementCount = 0;
    $configData = str_replace(array_keys($placeholderValues), array_values($placeholderValues), $configContents, $placeholderReplacementCount);
    if ($placeholderReplacementCount > 0) {
        $replacementCount += $placeholderReplacementCount;
    }
    $variableMap = [
        'dbname' => $placeholderValues['{database_name}'] ?? '',
        'usernamedb' => $placeholderValues['{username_db}'] ?? '',
        'passworddb' => $placeholderValues['{password_db}'] ?? '',
        'dbhost' => $placeholderValues['{db_host}'] ?? '',
        'APIKEY' => $placeholderValues['{API_KEY}'] ?? '',
        'adminnumber' => $placeholderValues['{admin_number}'] ?? '',
        'domainhosts' => $placeholderValues['{domain_name}'] ?? '',
        'usernamebot' => $placeholderValues['{username_bot}'] ?? '',
    ];
    $updatedConfig = $configData;
    foreach ($variableMap as $variable => $value) {
        $pattern = '/(\$' . preg_quote($variable, '/') . '\s*=\s*)([\'\"])(.*?)(\2)(\s*;)([^\n]*)(\n?)/u';
        $updatedConfig = preg_replace_callback(
            $pattern,
            static function ($matches) use ($value, &$replacementCount) {
                $replacementCount++;
                $quoteChar = $matches[2];
                $formattedValue = rx_format_config_value($value, $quoteChar);
                return $matches[1] . $formattedValue . $matches[5] . $matches[6] . $matches[7];
            },
            $updatedConfig,
            1
        );
    }
    return $updatedConfig;
}

function rx_format_config_value($value, string $quoteChar = "'"): string
{
    if ($value === null) {
        return 'null';
    }
    if (is_bool($value)) {
        return $value ? 'true' : 'false';
    }
    if ($quoteChar !== "'" && $quoteChar !== '"') {
        $quoteChar = "'";
    }
    $stringValue = (string) $value;
    $escapedValue = addcslashes($stringValue, "\\$quoteChar");
    return $quoteChar . $escapedValue . $quoteChar;
}

function rx_escape_html($value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_HTML5, 'UTF-8');
}

function rx_table_migrations_verify_ready(array $dbInfo, int $retries = 5, int $delaySeconds = 2): bool
{
    for ($attempt = 1; $attempt <= $retries; $attempt++) {
        $connect = @new mysqli($dbInfo['host'], $dbInfo['username'], $dbInfo['password'], $dbInfo['name']);
        if (!$connect->connect_error) {
            $result = $connect->query("SHOW TABLES LIKE 'setting'");
            $ready = ($result && $result->num_rows > 0);
            $connect->close();
            if ($ready) {
                return true;
            }
        }
        if ($attempt < $retries) {
            sleep($delaySeconds);
        }
    }
    return false;
}

function rx_run_table_migrations(string $rootDirectory, array $dbInfo): array
{
    $tableFile = $rootDirectory . 'table.php';
    if (!file_exists($tableFile)) {
        return ['ok' => false, 'message' => 'فایل table.php یافت نشد.'];
    }
    $obLevelBefore = ob_get_level();
    $prevCwd = getcwd();
    $included = false;
    $error = null;
    try {
        chdir(rtrim($rootDirectory, '/'));
        ob_start();
        include $tableFile;
        ob_end_clean();
        $included = true;
    } catch (\Throwable $e) {
        while (ob_get_level() > $obLevelBefore) {
            ob_end_clean();
        }
        $error = $e->getMessage();
    } finally {
        if ($prevCwd !== false) {
            chdir($prevCwd);
        }
    }
    if (!$included) {
        return ['ok' => false, 'message' => $error ?: 'اجرای table.php امکان‌پذیر نبود.'];
    }
    if (!rx_table_migrations_verify_ready($dbInfo)) {
        return ['ok' => false, 'message' => 'اسکریپت table.php اجرا شد اما جدول setting در دیتابیس ایجاد نشد. اتصال دیتابیس یا دسترسی‌های کاربر را بررسی کنید.'];
    }
    return ['ok' => true, 'message' => ''];
}

function rx_ensure_admin_record(array $dbInfo, string $adminNumber): bool
{
    try {
        $connect = @new mysqli($dbInfo['host'], $dbInfo['username'], $dbInfo['password'], $dbInfo['name']);
        if ($connect->connect_error) {
            return false;
        }
        $connect->set_charset('utf8mb4');
        $tableCheck = $connect->query("SHOW TABLES LIKE 'admin'");
        if ($tableCheck && $tableCheck->num_rows > 0) {
            $result = $connect->query('SELECT COUNT(*) as cnt FROM admin');
            $countRow = $result ? $result->fetch_assoc() : ['cnt' => 0];
            $count = (int) ($countRow['cnt'] ?? 0);
            if ($count === 0) {
                $stmt = $connect->prepare("INSERT INTO `admin` (`id_admin`, `username`, `password`, `rule`) VALUES (?, 'admin', '14e9eab674', 'administrator')");
                if ($stmt) {
                    $stmt->bind_param('s', $adminNumber);
                    $stmt->execute();
                    $stmt->close();
                }
            } else {
                $adminNumberEscaped = $connect->real_escape_string($adminNumber);
                $connect->query("UPDATE `admin` SET `id_admin` = '{$adminNumberEscaped}', `username` = 'admin', `password` = '14e9eab674', `rule` = 'administrator' LIMIT 1");
            }
        } else {
            $connect->query("CREATE TABLE `admin` (
              `id_admin` varchar(500) NOT NULL,
              `username` varchar(1000) NOT NULL,
              `password` varchar(1000) NOT NULL,
              `rule` varchar(500) NOT NULL,
              PRIMARY KEY (`id_admin`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            $stmt = $connect->prepare("INSERT INTO `admin` (`id_admin`, `username`, `password`, `rule`) VALUES (?, 'admin', '14e9eab674', 'administrator')");
            if ($stmt) {
                $stmt->bind_param('s', $adminNumber);
                $stmt->execute();
                $stmt->close();
            }
        }
        $connect->close();
        return true;
    } catch (\Throwable $e) {
        return false;
    }
}
