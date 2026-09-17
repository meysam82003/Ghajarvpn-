<?php

function nmDecodeState($raw)
{
    if (is_array($raw)) return $raw;
    $decoded = json_decode((string)$raw, true);
    return is_array($decoded) ? $decoded : [];
}

function nmResolvePanelFromUserState(array $user = null, $fallbackPanelName = null)
{
    $candidates = [];
    if ($user) {
        foreach (['Processing_value', 'Processing_value_one', 'Processing_value_tow', 'Processing_value_four'] as $stateField) {
            if (!array_key_exists($stateField, $user)) {
                continue;
            }
            $state = nmDecodeState($user[$stateField] ?? '');
            foreach (['namepanel', 'name_panel', 'panel', 'panel_name', 'code_panel', 'codepanel', 'stock_codepanel', 'source_codepanel'] as $key) {
                if (!empty($state[$key])) {
                    $candidates[] = trim((string)$state[$key]);
                }
            }

            if (!is_array($user[$stateField])) {
                $raw = trim((string)$user[$stateField]);
                if ($raw !== '' && $raw !== '0' && $raw !== 'none' && $raw[0] !== '{' && $raw[0] !== '[') {
                    $candidates[] = $raw;
                }
            }
        }
    }
    if ($fallbackPanelName !== null && trim((string)$fallbackPanelName) !== '') {
        $candidates[] = trim((string)$fallbackPanelName);
    }
    foreach (array_unique(array_filter($candidates)) as $candidate) {
        try {
            $panel = select('marzban_panel', '*', 'name_panel', $candidate, 'select');
            if ($panel) return $panel;
            $panel = select('marzban_panel', '*', 'code_panel', $candidate, 'select');
            if ($panel) return $panel;
            if (ctype_digit((string)$candidate)) {
                $panel = select('marzban_panel', '*', 'id', $candidate, 'select');
                if ($panel) return $panel;
            }
        } catch (Throwable $e) {}
    }
    return false;
}

function nmResolvePanelNameForUser(array $user = null, $fallback = null)
{
    if ($user) {
        foreach (['Processing_value', 'Processing_value_one', 'Processing_value_tow', 'Processing_value_four'] as $stateField) {
            if (!array_key_exists($stateField, $user)) continue;
            $raw = $user[$stateField];
            if (is_string($raw)) {
                $trim = trim($raw);
                if ($trim !== '' && $trim[0] !== '{' && $trim[0] !== '[' && $trim !== '0' && $trim !== 'none') {
                    return $trim;
                }
                $state = nmDecodeState($trim);
                foreach (['namepanel', 'name_panel', 'panel', 'panel_name'] as $key) {
                    if (!empty($state[$key])) return trim((string)$state[$key]);
                }
            } elseif (is_array($raw)) {
                foreach (['namepanel', 'name_panel', 'panel', 'panel_name'] as $key) {
                    if (!empty($raw[$key])) return trim((string)$raw[$key]);
                }
            }
        }
    }
    if ($fallback !== null) {
        $fallback = trim((string)$fallback);
        if ($fallback !== '') return $fallback;
    }
    $resolved = nmResolvePanelFromUserState($user, $fallback);
    if (is_array($resolved) && !empty($resolved['name_panel'])) return (string)$resolved['name_panel'];
    return '';
}


function nmNormalizeText($value)
{
    $value = trim((string)$value);
    $value = preg_replace('/[\x{200c}\x{200d}\x{200e}\x{200f}\x{202a}-\x{202e}]/u', '', $value);
    $value = str_replace(["ي", "ك", "ة", "ۀ"], ["ی", "ک", "ه", "ه"], $value);
    $value = preg_replace('/\s+/u', ' ', $value);
    return trim($value);
}

function nmProductButtonName($text)
{
    $text = nmNormalizeText($text);
    $text = preg_replace('/\s*-\s*[0-9۰-۹,،.]+\s*(?:تومان|ریال)?\s*$/u', '', $text);
    return nmNormalizeText($text);
}

function nmTableHasColumn($table, $column)
{
    global $pdo;
    try {
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND COLUMN_NAME = :c");
        $stmt->execute([':t' => $table, ':c' => $column]);
        return ((int)$stmt->fetchColumn()) > 0;
    } catch (Throwable $e) { return false; }
}

function nmCategoryLookupValues($category)
{
    $values = [];
    $category = nmNormalizeText($category);
    if ($category === '' || $category === 'بدون دسته‌بندی') {
        return [];
    }

    $values[] = $category;
    $lookupColumns = [];
    foreach (['id', 'remark', 'name', 'title'] as $column) {
        if (function_exists('nmTableHasColumn') && nmTableHasColumn('category', $column)) {
            $lookupColumns[] = $column;
        }
    }

    foreach ($lookupColumns as $column) {
        if ($column === 'id' && !ctype_digit((string)$category)) {
            continue;
        }
        try {
            $row = select('category', '*', $column, $category, 'select');
            if (is_array($row) && $row) {
                foreach (['id', 'remark', 'name', 'title'] as $k) {
                    if (array_key_exists($k, $row) && trim((string)$row[$k]) !== '') {
                        $values[] = nmNormalizeText($row[$k]);
                    }
                }
            }
        } catch (Throwable $e) {
            error_log('nmCategoryLookupValues lookup failed on category.' . $column . ': ' . $e->getMessage());
        }
    }

    if (in_array('remark', $lookupColumns, true) && in_array('id', $lookupColumns, true)) {
        try {
            $row = select('category', '*', 'remark', $category, 'select');
            if (is_array($row) && isset($row['id'])) {
                $values[] = nmNormalizeText($row['id']);
            }
        } catch (Throwable $e) {}
    }

    return array_values(array_unique(array_filter($values, static function($v) { return trim((string)$v) !== ''; })));
}
function nmProductsForPanelCategory(array $panel, $agent = 'all', $category = null)
{
    global $pdo;
    $loc = trim((string)($panel['name_panel'] ?? ''));
    if ($loc === '' || !isset($pdo)) return [];

    $agent = trim((string)($agent ?? ''));
    $filterAgent = !in_array($agent, ['', 'all', '*', 'any'], true);
    $params = [
        ':loc_where' => $loc,
        ':loc_order' => $loc,
    ];
    $sql = "SELECT * FROM product WHERE (FIND_IN_SET(:loc_where, Location) > 0 OR Location = '/all')";
    if ($filterAgent) {
        $sql .= " AND (agent = :agent OR agent = 'all' OR agent = '' OR agent IS NULL)";
        $params[':agent'] = $agent;
    }

    $catValues = nmCategoryLookupValues($category);
    if ($catValues) {
        [$catSql, $catParams] = nmBuildFindInSetClause('category', $catValues, 'cat');
        if ($catSql !== '') {
            $sql .= " AND ({$catSql}";
            $params += $catParams;
            if (nmTableHasColumn('product', 'category_id')) {
                $inId = [];
                foreach ($catValues as $j => $value) {
                    $key = ':cat_id' . $j;
                    $inId[] = $key;
                    $params[$key] = $value;
                }
                $sql .= ' OR category_id IN (' . implode(',', $inId) . ')';
            }
            $sql .= ')';
        }
    }
    $sql .= " ORDER BY CASE WHEN FIND_IN_SET(:loc_order, Location) > 0 THEN 0 ELSE 1 END, CAST(price_product AS UNSIGNED) ASC, id DESC";
    try {
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $rows = $stmt->fetchAll(PDO::FETCH_ASSOC) ?: [];
        if (!$rows && $catValues) {
            $fallbackParams = [
                ':fallback_loc_where' => $loc,
                ':fallback_loc_order' => $loc,
            ];
            $fallbackSql = "SELECT * FROM product WHERE (FIND_IN_SET(:fallback_loc_where, Location) > 0 OR Location = '/all')";
            if ($filterAgent) {
                $fallbackSql .= " AND (agent = :agent OR agent = 'all' OR agent = '' OR agent IS NULL)";
                $fallbackParams[':agent'] = $agent;
            }
            $fallbackSql .= " ORDER BY CASE WHEN FIND_IN_SET(:fallback_loc_order, Location) > 0 THEN 0 ELSE 1 END, CAST(price_product AS UNSIGNED) ASC, id DESC";
            $stmt = $pdo->prepare($fallbackSql);
            $stmt->execute($fallbackParams);
            $rows = $stmt->fetchAll(PDO::FETCH_ASSOC) ?: [];
        }
        return $rows;
    } catch (Throwable $e) {
        error_log('nmProductsForPanelCategory failed: ' . $e->getMessage());
        return [];
    }
}

function nmProductByCodeForPanel($codeProduct, $panelName, $agent = null)
{
    global $pdo;

    $codeProduct = trim((string) $codeProduct);
    $panelName = trim((string) $panelName);
    $agent = $agent === null ? null : trim((string) $agent);

    if (!($pdo instanceof PDO) || $codeProduct === '' || $panelName === '') {
        return false;
    }

    try {
        $sql = "SELECT * FROM product
                WHERE code_product = :code_product
                  AND (FIND_IN_SET(:location_where, Location) > 0 OR Location = '/all')";
        $params = [
            ':code_product' => $codeProduct,
            ':location_where' => $panelName,
            ':location_order' => $panelName,
        ];

        if ($agent !== null && $agent !== '') {
            $sql .= " AND (agent = :agent_where OR agent = 'all' OR agent = '' OR agent IS NULL)";
            $params[':agent_where'] = $agent;
            $params[':agent_order'] = $agent;
            $sql .= " ORDER BY
                        CASE WHEN FIND_IN_SET(:location_order, Location) > 0 THEN 0 ELSE 1 END,
                        CASE WHEN agent = :agent_order THEN 0 WHEN agent = 'all' THEN 1 ELSE 2 END,
                        id ASC
                      LIMIT 1";
        } else {
            $sql .= " ORDER BY CASE WHEN FIND_IN_SET(:location_order, Location) > 0 THEN 0 ELSE 1 END, id ASC LIMIT 1";
        }

        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        return $row ?: false;
    } catch (Throwable $e) {
        error_log('nmProductByCodeForPanel failed: ' . $e->getMessage());
        return false;
    }
}

function nmProductByNameForPanel($productName, $panelName, $agent = null, $category = null)
{
    global $pdo;
    try {
        $productName = nmProductButtonName($productName);
        $panelName = trim((string)$panelName);
        $params = [
            ':name_product' => $productName,
            ':location_where' => $panelName,
            ':location_order' => $panelName,
        ];
        $sql = "SELECT * FROM product WHERE name_product = :name_product AND (FIND_IN_SET(:location_where, Location) > 0 OR Location = '/all')";
        $catValues = nmCategoryLookupValues($category);
        if ($catValues) {
            [$catSql, $catParams] = nmBuildFindInSetClause('category', $catValues, 'cat');
            if ($catSql !== '') {
                $sql .= " AND ({$catSql}";
                $params += $catParams;
                if (nmTableHasColumn('product', 'category_id')) {
                    $inId = [];
                    foreach ($catValues as $j => $value) {
                        $key = ':cat_id' . $j;
                        $inId[] = $key;
                        $params[$key] = $value;
                    }
                    $sql .= ' OR category_id IN (' . implode(',', $inId) . ')';
                }
                $sql .= ')';
            }
        }
        $agent = trim((string)($agent ?? ''));
        if (!in_array($agent, ['', 'all', '*', 'any'], true)) {
            $sql .= " AND (agent = :agent OR agent = 'all' OR agent = '' OR agent IS NULL)";
            $params[':agent'] = $agent;
        }
        $sql .= " ORDER BY CASE WHEN FIND_IN_SET(:location_order, Location) > 0 THEN 0 ELSE 1 END LIMIT 1";
        $stmt = $pdo->prepare($sql); $stmt->execute($params); $row = $stmt->fetch(PDO::FETCH_ASSOC); if ($row) return $row;

        $rows = nmProductsForPanelCategory(['name_panel' => $panelName], $agent ?: 'all', $category);
        foreach ($rows as $r) if (nmNormalizeText($r['name_product'] ?? '') === $productName) return $r;
        foreach ($rows as $r) { $name = nmNormalizeText($r['name_product'] ?? ''); if ($name !== '' && (mb_strpos($productName, $name) !== false || mb_strpos($name, $productName) !== false)) return $r; }
        if (count($rows) === 1) return $rows[0];
        return false;
    } catch (Throwable $e) {
        error_log('nmProductByNameForPanel failed: ' . $e->getMessage());
        return false;
    }
}

function nm_renderInfoCardForInvoice($panel_info, $username_service, $invoice_id, $user_id)
{
    if (!function_exists('createServiceInfoCard')) {
        return null;
    }
    if (!function_exists('getInfoCardStatus') || !getInfoCardStatus()) {
        return null;
    }
    if (is_array($panel_info) && ($panel_info['type'] ?? '') === 'Manualsale') {
        return null;
    }
    if (is_array($panel_info) && function_exists('nmPanelNationalEnabled') && nmPanelNationalEnabled($panel_info)) {
        return null;
    }
    global $ManagePanel, $setting;
    try {
        if (!isset($ManagePanel) || !is_object($ManagePanel) || !method_exists($ManagePanel, 'DataUser')) {
            return null;
        }
        $name_panel = is_array($panel_info) ? ($panel_info['name_panel'] ?? '') : '';
        if ($name_panel === '') {
            return null;
        }
        $data = @$ManagePanel->DataUser($name_panel, $username_service);
        if (!is_array($data) || (isset($data['status']) && $data['status'] === 'Unsuccessful')) {
            return null;
        }
        $used  = (float)($data['used_traffic'] ?? 0);
        $total = (float)($data['data_limit']   ?? 0);
        $expire = $data['expire'] ?? 0;
        $unlimitedTime = empty($expire);
        $daysLeft = 0;
        if (!$unlimitedTime) {
            $diff = (int)$expire - time();
            $daysLeft = max(0, (int) floor($diff / 86400));
        }
        $statusVal = (string)($data['status'] ?? 'active');
        $isActive = in_array($statusVal, ['active', 'on_hold'], true);


        $botUsername = '';
        if (is_array($setting ?? null)) {
            foreach (['bot_username', 'username_bot', 'usernamebot', 'BotUsername', 'bot_user'] as $k) {
                if (isset($setting[$k]) && is_string($setting[$k]) && trim($setting[$k]) !== '') {
                    $botUsername = ltrim((string)$setting[$k], '@');
                    break;
                }
            }
        }
        if ($botUsername === '' && function_exists('telegram')) {
            $me = @telegram('getMe', []);
            if (is_array($me) && isset($me['result']['username'])) {
                $botUsername = (string)$me['result']['username'];
            }
        }

        $color = function_exists('getInfoCardColor') ? getInfoCardColor() : 'yellow';
        $params = [
            'config_name'    => (string)$username_service,
            'bot_username'   => $botUsername,
            'user_id'        => (string)$user_id,
            'active'         => $isActive,
            'used_bytes'     => $used,
            'total_bytes'    => $total,
            'days_left'      => $daysLeft,
            'unlimited_time' => $unlimitedTime,
        ];
        $outPath = (defined('REFACTORED_LEGACY_ROOT') ? REFACTORED_LEGACY_ROOT : __DIR__)
            . DIRECTORY_SEPARATOR . 'infocard_' . $user_id . '_' . bin2hex(random_bytes(3)) . '.png';
        $written = createServiceInfoCard($params, $color, $outPath);
        if ($written === false) {
            return null;
        }
        return $written;
    } catch (\Throwable $e) {
        error_log('nm_renderInfoCardForInvoice failed: ' . $e->getMessage());
        return null;
    }
}

function nm_sendServiceQrFallback($user_id, string $qrPayload, string $backgroundImage = 'images.jpg'): bool
{
    if (function_exists('isQrDisabled') && isQrDisabled()) {
        return false;
    }
    if (!function_exists('createqrcode') || !function_exists('telegram')) {
        return false;
    }
    $qrPayload = trim($qrPayload);
    if ($qrPayload === '') {
        return false;
    }
    try {
        $qrCode = createqrcode($qrPayload);
        if ($qrCode === null) {
            return false;
        }
        $urlimage = (defined('REFACTORED_LEGACY_ROOT') ? REFACTORED_LEGACY_ROOT : __DIR__)
            . DIRECTORY_SEPARATOR . $user_id . bin2hex(random_bytes(3)) . '.png';
        file_put_contents($urlimage, $qrCode->getString());
        if (function_exists('addBackgroundImage')) {
            @addBackgroundImage($urlimage, $qrCode, $backgroundImage);
        }
        telegram('sendphoto', [
            'chat_id'    => $user_id,
            'photo'      => new CURLFile($urlimage),
            'caption'    => '📥 کیو‌آر کد',
            'parse_mode' => 'HTML',
        ]);
        @unlink($urlimage);
        return true;
    } catch (Throwable $e) {
        error_log('nm_sendServiceQrFallback failed: ' . $e->getMessage());
        return false;
    }
}