<?php

if (!defined('REFACTORED_LEGACY_ROOT')) {
    define('REFACTORED_LEGACY_ROOT', __DIR__);
}
@chdir(__DIR__);

include('config.php');
require_once 'request.php';
date_default_timezone_set('Asia/Tehran');

function pasarguardFormatUptime($seconds)
{
    $seconds = (int) $seconds;
    if ($seconds < 60) {
        return "{$seconds} ثانیه";
    }
    $days = intdiv($seconds, 86400);
    $hours = intdiv($seconds % 86400, 3600);
    $minutes = intdiv($seconds % 3600, 60);
    if ($days > 0) {
        $parts = ["{$days} روز"];
        if ($hours > 0) {
            $parts[] = "{$hours} ساعت";
        }
        return implode(' و ', $parts);
    }
    if ($hours > 0) {
        $parts = ["{$hours} ساعت"];
        if ($minutes > 0) {
            $parts[] = "{$minutes} دقیقه";
        }
        return implode(' و ', $parts);
    }
    return "{$minutes} دقیقه";
}

function findPasarGuardPanelByName($location)
{
    $panel = select("marzban_panel", "*", "name_panel", $location, "select");
    if (!is_array($panel) || empty($panel)) {
        return null;
    }
    return $panel;
}

function pasarguardApiKey(array $panel)
{
    return $panel['api_key'] ?? null;
}

function pasarguardRequest($panel, $method, $path, $data = null)
{
    $apiKey = pasarguardApiKey($panel);
    $url = rtrim($panel['url_panel'], '/') . $path;
    $headers = ['accept: application/json'];
    if ($data !== null) {
        $headers[] = 'Content-Type: application/json';
    }
    $req = new CurlRequest($url);
    $req->setHeaders($headers);
    $req->api_key($apiKey);
    switch (strtoupper($method)) {
        case 'POST':
            $response = $req->post($data !== null ? json_encode($data) : []);
            break;
        case 'PUT':
            $response = $req->put($data !== null ? json_encode($data) : null);
            break;
        case 'DELETE':
            $response = $req->delete($data !== null ? json_encode($data) : null);
            break;
        default:
            $response = $req->get();
            break;
    }
    return $response;
}

function pasarguardGetUser($username_account, $location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'GET', '/api/user/' . $username_account);
}

function pasarguardGetSubscriptionLinks($username_account, $location)
{
    $userResponse = pasarguardGetUser($username_account, $location);
    if (!empty($userResponse['error'])) {
        return $userResponse;
    }
    if (empty($userResponse['body'])) {
        return ["error" => "Empty response from panel when resolving subscription url."];
    }
    $userData = json_decode($userResponse['body'], true);
    if (!is_array($userData) || empty($userData['subscription_url'])) {
        return ["error" => "Unable to resolve subscription url."];
    }
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    $subUrl = rtrim($panel['url_panel'], '/') . '/' . ltrim($userData['subscription_url'], '/');
    $req = new CurlRequest(rtrim($subUrl, '/') . '/links');
    $req->setHeaders(['accept: text/plain']);
    $response = $req->get();
    if (!empty($response['error']) || empty($response['status']) || $response['status'] >= 400) {
        return ["error" => "Unable to fetch subscription links."];
    }
    $links = array_values(array_filter(array_map('trim', explode("\n", (string) $response['body'])), function ($line) {
        return $line !== '';
    }));
    return ["status" => true, "links" => $links];
}

function pasarguardGetUsersByStatus($location, $status)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    $response = pasarguardRequest($panel, 'GET', '/api/users?status=' . rawurlencode($status) . '&limit=200');
    if (!empty($response['error'])) {
        return $response;
    }
    if (empty($response['status']) || $response['status'] >= 400) {
        return ["error" => "Unable to fetch users by status."];
    }
    $data = json_decode((string) $response['body'], true);
    $users = is_array($data['users'] ?? null) ? $data['users'] : [];
    return ["status" => true, "users" => $users];
}

function pasarguardGetNodes($location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'GET', '/api/nodes');
}

function pasarguardGetSystemStats($location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'GET', '/api/system');
}

function pasarguardGetInbounds($location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'GET', '/api/inbounds');
}

function pasarguardGetUserHwids($username_account, $location)
{
    $userResponse = pasarguardGetUser($username_account, $location);
    if (!empty($userResponse['error'])) {
        return $userResponse;
    }
    if (empty($userResponse['body'])) {
        return ["error" => "Empty response from panel when resolving user id."];
    }
    $userData = json_decode($userResponse['body'], true);
    if (!is_array($userData) || !isset($userData['id'])) {
        return ["error" => "Unable to resolve numeric user id for HWID lookup."];
    }
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'GET', '/api/user/' . $userData['id'] . '/hwids');
}

function pasarguardAddUser($location, $data_limit, $username_ac, $timestamp, $note = '', $data_limit_reset = 'no_reset', $name_product = false, $hwid_limit = null)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    $inbounds = null;
    $product = null;
    if (!empty($panel['inbounds']) && $panel['inbounds'] != "null") {
        if ($name_product != false && $name_product != "usertest") {
            $product = select("product", "*", "name_product", $name_product, "select");
            if ($product == false || $product['inbounds'] == false) {
                $inbounds = json_decode($panel['inbounds'], true);
            } else {
                $inbounds = json_decode($product['inbounds'], true);
                $panel['proxies'] = $product['proxies'];
            }
        } else {
            $inbounds = json_decode($panel['inbounds'], true);
        }
    }
    if ($hwid_limit === null && $name_product != false && $name_product != "usertest") {
        if ($product === null) {
            $product = select("product", "*", "name_product", $name_product, "select");
        }
        if (is_array($product) && isset($product['hwid_limit'])) {
            $hwid_limit = (int) $product['hwid_limit'];
        }
    }
    $data = array(
        "proxy_settings" => json_decode($panel['proxies']),
        "data_limit" => $data_limit,
        "username" => $username_ac,
        "note" => $note,
        "data_limit_reset_strategy" => $data_limit_reset
    );
    if (isset($inbounds)) {
        $data['group_ids'] = $inbounds;
    }
    if (!empty($hwid_limit)) {
        $data['hwid_limit'] = (int) $hwid_limit;
    }
    if ($name_product == "usertest") {
        if ($panel['on_hold_test'] == "0") {
            $data["expire"] = $timestamp == 0 ? 0 : $timestamp;
        } else {
            if ($timestamp == 0) {
                $data["expire"] = 0;
            } else {
                $data["expire"] = 0;
                $data["status"] = "on_hold";
                $data["on_hold_expire_duration"] = $timestamp - time();
            }
        }
    } else {
        if ($panel['conecton'] == "offconecton") {
            $data["expire"] = $timestamp == 0 ? 0 : $timestamp;
        } else {
            if ($timestamp == 0) {
                $data["expire"] = 0;
            } else {
                $data["expire"] = 0;
                $data["status"] = "on_hold";
                $data["on_hold_expire_duration"] = $timestamp - time();
            }
        }
    }
    return pasarguardRequest($panel, 'POST', '/api/user', $data);
}

function pasarguardModifyUser($location, $username, array $data)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'PUT', '/api/user/' . $username, $data);
}

function pasarguardResetUserDataUsage($username_account, $location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'POST', '/api/user/' . $username_account . '/reset', []);
}

function pasarguardRevokeSub($username_account, $location)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'POST', '/api/user/' . $username_account . '/revoke_sub', []);
}

function pasarguardTestConnection($baseUrl, $apiKey)
{
    $apiKey = trim((string) $apiKey);
    $normalizedUrl = rtrim((string) $baseUrl, '/');
    if ($apiKey === '') {
        return [
            'status' => false,
            'msg' => 'PasarGuard API key is missing'
        ];
    }
    $panel = [
        'url_panel' => $normalizedUrl,
        'api_key' => $apiKey,
    ];
    $response = pasarguardRequest($panel, 'GET', '/api/admin');
    if (!empty($response['error'])) {
        return [
            'status' => false,
            'msg' => $response['error']
        ];
    }
    $httpStatus = $response['status'] ?? null;
    if ($httpStatus === null || $httpStatus >= 400) {
        $decoded = json_decode((string) ($response['body'] ?? ''), true);
        $message = is_array($decoded) && isset($decoded['detail']) ? $decoded['detail'] : "HTTP {$httpStatus}";
        return [
            'status' => false,
            'msg' => is_array($message) ? json_encode($message, JSON_UNESCAPED_UNICODE) : $message
        ];
    }
    return [
        'status' => true,
        'msg' => 'PasarGuard connection succeeded',
        'data' => json_decode((string) ($response['body'] ?? ''), true)
    ];
}

function pasarguardRemoveUser($location, $username)
{
    $panel = findPasarGuardPanelByName($location);
    if ($panel === null) {
        return ["error" => "Panel configuration not found for the requested location."];
    }
    return pasarguardRequest($panel, 'DELETE', '/api/user/' . $username);
}
