<?php


declare(strict_types=1);

require_once __DIR__ . '/BaseHandler.php';
require_once __DIR__ . '/service_output.php';
require_once __DIR__ . '/../lib/PanelTrial.php';

final class TestAccountHandler extends BaseHandler
{
    public $mode = 'info';

    public function handle(): void
    {
        if ($this->mode === 'create') {
            $this->handleCreate();
            return;
        }
        $this->handleInfo();
    }


    private function handleInfo(): void
    {
        $this->requireMethod('GET');
        $admin = $this->userIsAdmin();
        $panels = GhajarPanelTrial::panels($this->user, $this->setting, $admin);
        $items = array_map(static function ($p) {
            return ['id' => (string)$p['code_panel'], 'name' => (string)$p['name_panel'],
                'limit_left' => $p['trial_limit_left']];
        }, $panels);
        FaoximaResponse::ok(['available' => count($items) > 0, 'panels' => $items,
            'limit_per_panel' => GhajarPanelTrial::limit($this->setting),
            'limit_left' => array_sum(array_column($items, 'limit_left'))]);
    }

    private function handleCreate(): void
    {
        $this->requireMethod('POST');
        $isAdmin = $this->userIsAdmin();
        $codePanel = FaoximaInput::string($this->data, 'country_id');
        $customUsername = FaoximaInput::nullableString($this->data, 'custom_username');
        $panels = GhajarPanelTrial::panels($this->user, $this->setting, $isAdmin);
        if ($codePanel === '' && count($panels) === 1) $codePanel = (string)$panels[0]['code_panel'];
        $panel = null;
        foreach ($panels as $candidate) {
            if ((string)$candidate['code_panel'] === $codePanel) { $panel = $candidate; break; }
        }
        if ($panel === null) FaoximaResponse::fail(409, 'پنل فعال نیست یا سهمیه تست همین پنل تمام شده است.');

        $methodUsername = (string)($panel['MethodUsername'] ?? '');
        $requiresCustom = ($methodUsername === 'نام کاربری دلخواه + عدد رندوم' || $methodUsername === 'متن دلخواه کاربر + رندوم');
        if ($requiresCustom) {
            $trimmedCustom = trim((string)$customUsername);
            if ($trimmedCustom === '') {
                FaoximaResponse::fail(422, faoxima_textbot_get('dyn_testaccount_username_required', 'لطفاً یک نام کاربری وارد کنید.'));
            }
            if (!preg_match('~(?!_)^[a-z][a-z\d_]{2,32}(?<!_)$~i', $trimmedCustom)) {
                FaoximaResponse::fail(422, faoxima_textbot_get('dyn_testaccount_username_invalid', 'نام کاربری معتبر نیست.'));
            }
            $customUsername = $trimmedCustom;
        }

        if (($panel['type'] ?? '') === 'Manualsale') {
            $stock = (int) FaoximaDb::fetchScalar(
                "SELECT COUNT(*) FROM manualsell WHERE codepanel = :p AND codeproduct = 'usertest' AND status = 'active'",
                [':p' => $panel['code_panel']]
            );
            if ($stock === 0) {
                FaoximaResponse::fail(409, faoxima_textbot_get('dyn_testaccount_manualsale_stock_depleted', '❌ موجودی این سرویس به پایان رسیده.'));
            }
        }

        $randomString = bin2hex(random_bytes(4));
        $text = strtolower((string)($customUsername ?? ''));

        $usernameAc = generateUsername(
            (string)$this->user['id'],
            $methodUsername,
            (string)($this->user['username'] ?? ''),
            $randomString,
            $text,
            (string)($panel['namecustom'] ?? ''),
            (string)($this->user['namecustom'] ?? '')
        );
        $usernameAc = strtolower((string)$usernameAc);

        $managePanel = new ManagePanel();
        $existsLocal = FaoximaDb::fetchScalar(
            'SELECT 1 FROM invoice WHERE username = :u LIMIT 1',
            [':u' => $usernameAc]
        );
        $remoteCheck = $managePanel->DataUser($panel['name_panel'], $usernameAc);
        if ($existsLocal || (is_array($remoteCheck) && isset($remoteCheck['username']))) {
            $usernameAc = rand(1000000, 9999999) . '_' . $usernameAc;
        }

        $claim = GhajarPanelTrial::reserve($this->user, (string)$panel['code_panel'], $this->setting, $isAdmin);
        if ($claim === null) FaoximaResponse::fail(409, 'سهمیه تست این پنل تمام شده یا پنل غیرفعال شده است.');

        $expireTs = strtotime('+' . (int)($panel['time_usertest'] ?? 0) . ' hours');
        $dataLimitBytes = (int)($panel['val_usertest'] ?? 0) * 1048576;

        $orderId = $randomString;
        $notifications = json_encode(['volume' => false, 'time' => false]);

        try {
            $saved = FaoximaDb::execute(
                "INSERT IGNORE INTO invoice
                    (id_user, id_invoice, username, time_sell, Service_location, name_product,
                     price_product, Volume, Service_time, Status, notifctions)
                 VALUES (:id_user, :id_invoice, :username, :time_sell, :location, :name_product,
                         :price, :volume, :service_time, :status, :notifs)",
                [
                    ':id_user'      => $this->user['id'],
                    ':id_invoice'   => $orderId,
                    ':username'     => $usernameAc,
                    ':time_sell'    => time(),
                    ':location'     => $panel['name_panel'],
                    ':name_product' => 'سرویس تست',
                    ':price'        => 0,
                    ':volume'       => (int)($panel['val_usertest'] ?? 0),
                    ':service_time' => (int)($panel['time_usertest'] ?? 0),
                    ':status'       => 'active',
                    ':notifs'       => $notifications,
                ]
            );
            if ($saved !== 1) throw new RuntimeException("Trial invoice was not saved");
        } catch (Throwable $e) {
            GhajarPanelTrial::release($claim);
            FaoximaLogger::exception($e, 'TestAccount invoice insert failed', ['user_id' => $this->user['id']]);
            FaoximaResponse::serverError(faoxima_textbot_get('dyn_purchase_invoice_save_failed', 'خطا در ذخیره فاکتور'));
        }

        $datac = [
            'expire'     => $expireTs,
            'data_limit' => $dataLimitBytes,
            'from_id'    => $this->user['id'],
            'username'   => $usernameAc,
            'type'       => 'usertest',
        ];

        $dataoutput = $managePanel->createUser($panel['name_panel'], 'usertest', $usernameAc, $datac);

        if (empty($dataoutput['username'])) {
            $reason = is_array($dataoutput) ? json_encode($dataoutput['msg'] ?? $dataoutput) : (string)$dataoutput;
            FaoximaLogger::error('TestAccount createUser failed', [
                'user_id' => $this->user['id'],
                'panel'   => $panel['name_panel'],
                'reason'  => $reason,
            ]);

            GhajarPanelTrial::release($claim);
            try { FaoximaDb::execute('DELETE FROM invoice WHERE id_invoice = :o AND id_user = :u', [':o' => $orderId, ':u' => $this->user['id']]); } catch (Throwable $_) {}

            $errorText = faoxima_render_text(faoxima_textbot_get('dyn_testaccount_create_failed_report_tpl', "⭕️ یک کاربر قصد دریافت اکانت تست از مینی‌اپ داشت که ساخت کانفیگ با خطا مواجه شده\n<blockquote>✍️ دلیل خطا :</blockquote>\n<blockquote>{reason}</blockquote>\n<blockquote>آیدی کابر : {user_id}</blockquote>\n<blockquote>نام کاربری کاربر : @{username}</blockquote>\n<blockquote>نام پنل : {panel_name}</blockquote>"), [
                'reason' => $reason,
                'user_id' => $this->user['id'],
                'username' => $this->user['username'],
                'panel_name' => $panel['name_panel'],
            ]);
            $channel = $this->setting['Channel_Report'] ?? '';
            if ((string)$channel !== '') {
                $errRow = select('topicid', 'idreport', 'report', 'errorreport', 'select');
                $errTopic = is_array($errRow) ? (string)($errRow['idreport'] ?? '') : '';
                try {
                    telegram('sendmessage', [
                        'chat_id'           => $channel,
                        'message_thread_id' => $errTopic,
                        'text'              => $errorText,
                        'parse_mode'        => 'HTML',
                    ]);
                } catch (Throwable $_) {  }
            }

            update('invoice', 'Status', 'Unsuccessful', 'id_invoice', $orderId);
            FaoximaResponse::serverError(faoxima_textbot_get('dyn_testaccount_create_failed_customer', '❌ خطایی در ساخت اکانت تست رخ داد. با پشتیبانی تماس بگیرید.'));
        }

        GhajarPanelTrial::finish($claim);
        $configList = is_array($dataoutput['configs'] ?? null) ? $dataoutput['configs'] : [];
        $subLink = ($panel['sublink'] ?? '') === 'onsublink' ? (string)($dataoutput['subscription_url'] ?? '') : '';

        // For a Manualsale, the copyable "sub link" the admin attached alongside
        // a file lives in sub_link (never in the file's raw content). Resolve
        // $subLink from it so a file delivery still surfaces the link.
        $manualHasSubLink = false;
        if (($panel['type'] ?? '') === 'Manualsale') {
            $manualExtRaw = strtolower(ltrim(trim((string)($dataoutput['file_ext'] ?? '')), '.'));
            $manualSubLink = trim((string)($dataoutput['manual_sub_link'] ?? $dataoutput['sub_link'] ?? ''));
            $manualContentRaw = (string)($dataoutput['subscription_url'] ?? '');
            if ($manualExtRaw === 'sub') {
                $subLink = $manualContentRaw !== '' ? $manualContentRaw : $manualSubLink;
            } else {
                $subLink = $manualSubLink;
            }
            $manualHasSubLink = ($manualSubLink !== '');
        }

        $serviceTime = (int)($panel['time_usertest'] ?? 0);
        $volumeGb = (int)($panel['val_usertest'] ?? 0);
        $totalBytes = $dataLimitBytes;

        $configsArr = array_values(array_filter(array_map(static function ($c) {
            return is_string($c) ? trim($c) : '';
        }, $configList), static function ($c) { return $c !== ''; }));

        $template = $this->resolveTestTemplate((string)($panel['type'] ?? ''));
        if (trim($template) === '') {
            $template = faoxima_textbot_get('dyn_testaccount_default_template', "✅ اکانت تست شما ساخته شد\n\n👤 نام کاربری : {username}\n🌿 نام سرویس : {name_service}\n🇺🇳 لوکیشن : {location}\n⏳ مدت زمان: {day}\n🗜 حجم بسته: {volume}");
        }
        global $textbotlang;
        $displayDay    = $serviceTime === 0 ? ($textbotlang['users']['stateus']['Unlimited'] ?? '∞') : (string)$serviceTime;
        $displayVolume = $volumeGb === 0 ? ($textbotlang['users']['stateus']['Unlimited'] ?? '∞') : (string)$volumeGb;
        $template = str_replace('{username}', "<code>" . htmlspecialchars($usernameAc, ENT_QUOTES, 'UTF-8') . "</code>", $template);
        $template = str_replace('{name_service}', htmlspecialchars(faoxima_textbot_get('dyn_testaccount_product_name', 'سرویس تست'), ENT_QUOTES, 'UTF-8'), $template);
        $template = str_replace('{location}', htmlspecialchars((string)($panel['name_panel'] ?? ''), ENT_QUOTES, 'UTF-8'), $template);
        $template = str_replace('{day}', htmlspecialchars($displayDay, ENT_QUOTES, 'UTF-8'), $template);
        $template = str_replace('{volume}', htmlspecialchars($displayVolume, ENT_QUOTES, 'UTF-8'), $template);
        if (function_exists('applyConnectionPlaceholders')) {
            $template = applyConnectionPlaceholders($template, $subLink, '');
        }
        if (trim($template) === '') {
            $template = faoxima_render_text(faoxima_textbot_get('dyn_testaccount_minimal_template', "✅ اکانت تست شما ساخته شد\n\n👤 نام کاربری : <code>{username}</code>"), ['username' => $usernameAc]);
        }

        $keyboard = json_encode([
            'inline_keyboard' => [
                [
                    ['text' => faoxima_textbot_get('dyn_purchase_view_tutorial_btn', '📚 مشاهده آموزش استفاده '), 'callback_data' => 'helpbtn'],
                ],
            ],
        ], JSON_UNESCAPED_UNICODE);

        global $setting;
        if (empty($setting) && !empty($this->setting)) {
            $setting = $this->setting;
        }


        $manualDelivered  = false;
        $manualFileName   = '';
        $manualFileB64    = '';
        $manualContentForOutput = '';
        $manualExtForOutput = '';
        $manualItemsForOutput = [];
        if (($panel['type'] ?? '') === 'Manualsale') {
            $manualRows = select('manualsell', '*', 'username', $usernameAc, 'fetchAll');
            if (!is_array($manualRows)) {
                $manualRows = [];
            }
            $manualRow = !empty($manualRows) ? $manualRows[0] : null;
            foreach ($manualRows as $mr) {
                if (!is_array($mr)) {
                    continue;
                }
                $mrContent = (string)($mr['contentrecord'] ?? '');
                if (trim($mrContent) === '') {
                    continue;
                }
                $manualItemsForOutput[] = [
                    'content'  => $mrContent,
                    'file_ext' => strtolower(ltrim(trim((string)($mr['file_ext'] ?? '')), '.')),
                    'sub_link' => trim((string)($mr['sub_link'] ?? '')),
                ];
            }
            $manualExt = '';
            $manualContent = '';
            if (is_array($manualRow)) {
                $manualExt = strtolower(ltrim(trim((string)($manualRow['file_ext'] ?? '')), '.'));
                $manualContent = (string)($manualRow['contentrecord'] ?? '');
            }
            $manualContentForOutput = $manualContent;
            $manualExtForOutput = $manualExt;
            $isManualLink = ($manualExt === 'sub');
            $isManualText = ($manualExt === 'text');
            if ($manualContent !== '' && !$isManualLink && !$isManualText) {
                $ext = ($manualExt !== '') ? $manualExt : 'bin';
                $cleanUser = preg_replace('/[^a-zA-Z0-9_\-]/', '', $usernameAc);
                if ($cleanUser === '') $cleanUser = 'config';
                $manualDelivered = true;
                $manualFileName = $cleanUser . '.' . $ext;
                $manualFileB64  = base64_encode($manualContent);
                $configsArr = [];
            } elseif ($isManualText && $manualContent !== '') {
                if (!in_array($manualContent, $configsArr, true)) {
                    $configsArr[] = $manualContent;
                }
            }
            if (!$manualHasSubLink && $manualDelivered) {
                $subLink = '';
            }
        }

        $deliveryOk = false;
        try {
            sendMessageService(
                $panel,
                $configsArr,
                $subLink,
                $usernameAc,
                $keyboard,
                $template,
                $orderId,
                $this->user['id']
            );
            $deliveryOk = true;
        } catch (Throwable $e) {
            FaoximaLogger::exception($e, 'TestAccount delivery to user threw', ['user_id' => $this->user['id']]);
        }

        if (!$deliveryOk) {
            FaoximaLogger::error('TestAccount delivery to user failed', [
                'user_id'  => $this->user['id'],
                'order_id' => $orderId,
                'panel'    => $panel['name_panel'],
            ]);
            FaoximaResponse::fail(502, faoxima_textbot_get('dyn_testaccount_delivery_failed', '❌ اکانت تست ساخته شد اما ارسال آن به تلگرام ناموفق بود. لطفاً ربات را استارت کنید و دوباره تلاش کنید.'));
        }

        $channel = $this->setting['Channel_Report'] ?? '';
        if ((string)$channel !== '') {
            $topicRow = select('topicid', 'idreport', 'report', 'paymentreport', 'select');
            $topicId = is_array($topicRow) ? (string)($topicRow['idreport'] ?? '') : '';
            $text = faoxima_render_text(faoxima_textbot_get('dyn_testaccount_report_tpl', "🧪 دریافت اکانت تست از مینی‌اپ\n\n<blockquote>▫️آیدی عددی کاربر : <code>{user_id}</code></blockquote>\n<blockquote>▫️نام کاربری کاربر :@{username}</blockquote>\n<blockquote>▫️نام کاربری کانفیگ :{config_username}</blockquote>\n<blockquote>▫️موقعیت سرویس : {panel_name}</blockquote>"), [
                'user_id' => $this->user['id'],
                'username' => $this->user['username'],
                'config_username' => $usernameAc,
                'panel_name' => $panel['name_panel'],
            ]);
            try {
                telegram('sendmessage', [
                    'chat_id'           => $channel,
                    'message_thread_id' => $topicId,
                    'text'              => $text,
                    'parse_mode'        => 'HTML',
                ]);
            } catch (Throwable $_) {  }
        }

        FaoximaLogger::debug('Test account created via miniapp', [
            'user_id'  => $this->user['id'],
            'order_id' => $orderId,
            'panel'    => $panel['name_panel'],
        ]);

        FaoximaResponse::ok([
            'success'  => true,
            'order_id' => $orderId,
            'service'  => [
                'id'                => $orderId,
                'username'          => (string)($dataoutput['username'] ?? $usernameAc),
                'status'            => 'active',
                'active'            => true,
                'expire'            => $expireTs,
                'product_name'      => faoxima_textbot_get('dyn_testaccount_product_name', 'سرویس تست'),
                'panel_name'        => (string)($panel['name_panel'] ?? ''),
                'panel_type'        => (string)($panel['type'] ?? ''),
                'service_time_days' => $serviceTime,
                'days_left'         => $serviceTime,
                'volume_gb'         => $volumeGb,
                'used_bytes'        => 0,
                'total_bytes'       => $totalBytes,
                'unlimited_volume'  => $totalBytes === 0,
                'unlimited_time'    => $serviceTime === 0,
                'subscription_url'  => $subLink,
                'configs'           => $configsArr,
                'delivered_as_file' => $manualDelivered,
                'file_name'         => $manualFileName,
                'file_content_b64'  => $manualFileB64,
                'service_output'    => faoxima_build_service_output([
                    'panel_type'   => (string)($panel['type'] ?? ''),
                    'subscription' => $subLink,
                    'configs'      => $configsArr,
                    'sub_link'     => $subLink,
                    'content'      => $manualContentForOutput,
                    'file_ext'     => $manualExtForOutput,
                    'items'        => $manualItemsForOutput,
                    'username'     => $usernameAc,
                ]),
            ],
        ]);
    }

    private function resolveTestTemplate(string $panelType): string
    {
        $rows = select('textbot', '*', null, null, 'fetchAll') ?: [];
        $bag = [
            'textafterpay' => '',
            'textmanual' => '',
            'text_wgdashboard' => '',
        ];
        foreach ($rows as $row) {
            if (isset($bag[$row['id_text']])) {
                $bag[$row['id_text']] = $row['text'];
            }
        }

        if ($panelType === 'Manualsale')  return $bag['textmanual'] ?: $bag['textafterpay'];
        if ($panelType === 'WGDashboard') return $bag['text_wgdashboard'] ?: $bag['textafterpay'];
        return $bag['textafterpay'];
    }
}
