<?php

declare(strict_types=1);

if (!defined('FAOXIMA_SKIP_BOTAPI_ROUTER')) {
    define('FAOXIMA_SKIP_BOTAPI_ROUTER', true);
}

ob_start();

@ini_set('display_errors',         '0');
@ini_set('display_startup_errors', '0');
@ini_set('log_errors',             '1');
error_reporting(E_ALL);

$GLOBALS['__weblink_response_sent'] = false;

function __weblink_emit(int $http, array $payload): void
{
    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    if (!headers_sent()) {
        http_response_code($http);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store, max-age=0');
    }
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    $GLOBALS['__weblink_response_sent'] = true;
}

register_shutdown_function(static function () {
    if (!empty($GLOBALS['__weblink_response_sent'])) {
        return;
    }
    $err = error_get_last();
    $fatal = [E_ERROR, E_PARSE, E_COMPILE_ERROR, E_CORE_ERROR, E_USER_ERROR];
    if (is_array($err) && in_array($err['type'], $fatal, true)) {
        __weblink_emit(500, [
            'status' => false,
            'msg'    => 'Internal server error',
            'detail' => 'See server error log',
        ]);
        return;
    }
    __weblink_emit(500, [
        'status' => false,
        'msg'    => 'weblink.php finished without emitting a response',
    ]);
});

try {
    require_once __DIR__ . '/lib/Bootstrap.php';
    require_once __DIR__ . '/lib/WebLink.php';
    require_once __DIR__ . '/handlers/BaseHandler.php';
    require_once __DIR__ . '/handlers/VerifyHandler.php';

    while (ob_get_level() > 0) {
        @ob_end_clean();
    }
    ob_start();

    $action = (string)($_GET['action'] ?? $_POST['action'] ?? '');

    if ($action === 'generate') {
        $data = FaoximaWebLink::generate();
        $data['bot_username'] = FaoximaWebLink::resolveBotUsername();
        __weblink_emit(200, ['status' => true] + $data);
        exit;
    }

    if ($action === 'status') {
        $sessionToken = trim((string)($_GET['session_token'] ?? $_POST['session_token'] ?? ''));
        if ($sessionToken === '') {
            __weblink_emit(400, ['status' => false, 'msg' => 'session_token is required']);
            exit;
        }

        $result = FaoximaWebLink::status($sessionToken);

        if ($result['link_status'] === 'linked') {
            $userId = (int)$result['telegram_id'];
            $userRecord = select('user', '*', 'id', $userId, 'select', ['cache' => false]);
            if (empty($userRecord) || !is_array($userRecord)) {
                // The Telegram id was claimed but the user row isn't ready yet
                // (e.g. bot hasn't finished auto-registering) — tell the
                // browser to keep waiting rather than failing outright.
                __weblink_emit(200, ['status' => true, 'link_status' => 'pending']);
                exit;
            }
            $verify = VerifyHandler::resolveForUser($userId, $userRecord);
            if (!empty($verify['status']) && !empty($verify['token'])) {
                FaoximaWebLink::markDelivered($sessionToken);
            }
            __weblink_emit(200, array_merge($verify, ['link_status' => 'linked']));
            exit;
        }

        __weblink_emit(200, ['status' => true, 'link_status' => $result['link_status']]);
        exit;
    }

    // Mints a one-time ticket for the caller's own account, so the panel can
    // be opened in a real browser and land on that same account. Requires a
    // valid bearer - this issues nothing to an anonymous caller.
    if ($action === 'web_ticket') {
        require_once __DIR__ . '/lib/Auth.php';
        $bearer = FaoximaAuth::extractBearerToken();
        $user = $bearer !== null ? FaoximaAuth::userFromToken($bearer) : null;
        if (!is_array($user) || (int)($user['id'] ?? 0) <= 0) {
            __weblink_emit(401, ['status' => false, 'msg' => 'Unauthorized']);
            exit;
        }
        $ticket = FaoximaWebLink::issueTicket((int)$user['id']);
        if ($ticket === null) {
            __weblink_emit(500, ['status' => false, 'msg' => 'Unable to issue ticket']);
            exit;
        }
        __weblink_emit(200, ['status' => true, 'ticket' => $ticket, 'expires_in' => 90]);
        exit;
    }

    // Spends a ticket and hands back that user's session token. Single use:
    // a replay of the same ticket gets a 400, not a second session.
    if ($action === 'redeem') {
        $ticket = trim((string)($_POST['ticket'] ?? $_GET['ticket'] ?? ''));
        if ($ticket === '') {
            __weblink_emit(400, ['status' => false, 'msg' => 'ticket is required']);
            exit;
        }
        $userId = FaoximaWebLink::redeemTicket($ticket);
        if ($userId === null) {
            __weblink_emit(400, ['status' => false, 'msg' => 'Ticket is invalid, expired or already used']);
            exit;
        }
        $userRecord = select('user', '*', 'id', $userId, 'select', ['cache' => false]);
        if (empty($userRecord) || !is_array($userRecord)) {
            __weblink_emit(404, ['status' => false, 'msg' => 'Account not found']);
            exit;
        }
        // Same gate evaluation and same token the Telegram path returns, so a
        // browser session is never more privileged than the mini-app one.
        __weblink_emit(200, VerifyHandler::resolveForUser($userId, $userRecord));
        exit;
    }

    __weblink_emit(400, ['status' => false, 'msg' => 'Unknown action']);
} catch (Throwable $e) {
    error_log('[GhajarWebLink] ' . $e->getMessage());
    __weblink_emit(500, [
        'status' => false,
        'msg'    => 'Unable to create or verify link session',
        'detail' => 'See server error log',
    ]);
}
