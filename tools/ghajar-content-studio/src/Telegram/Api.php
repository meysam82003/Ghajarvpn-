<?php
declare(strict_types=1);

namespace Ghajar\Studio\Telegram;

use Ghajar\Studio\Core\Config;
use Ghajar\Studio\Support\Logger;

class Api
{
    public function __construct(private string $token, private int $timeout = 25)
    {
    }

    public static function fromConfig(): self
    {
        $token = (string) Config::get('bot_token', '');
        if ($token === '') {
            throw new ApiException('توکن ربات ثبت نشده است.', 401);
        }
        return new self($token);
    }

    /**
     * @param array<string,mixed> $params
     * @return array<string,mixed>|list<mixed>|bool|int|string
     */
    public function call(string $method, array $params = []): mixed
    {
        $payload = [];
        foreach ($params as $key => $value) {
            if ($value === null) {
                continue;
            }
            $payload[$key] = is_scalar($value) ? $value : json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        }

        $ch = curl_init('https://api.telegram.org/bot' . $this->token . '/' . $method);
        curl_setopt_array($ch, [
            CURLOPT_POST           => true,
            CURLOPT_POSTFIELDS     => http_build_query($payload),
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->timeout,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER     => ['Accept: application/json'],
        ]);
        $body   = curl_exec($ch);
        $errNo  = curl_errno($ch);
        $errMsg = curl_error($ch);
        curl_close($ch);

        if ($body === false || $errNo !== 0) {
            Logger::error('Telegram network failure', ['method' => $method, 'curl' => $errNo]);
            throw new ApiException($errMsg !== '' ? $errMsg : 'خطای شبکه', 0, $method, true);
        }

        $decoded = json_decode((string) $body, true);
        if (!is_array($decoded)) {
            throw new ApiException('پاسخ نامعتبر از تلگرام دریافت شد.', 0, $method);
        }
        if (($decoded['ok'] ?? false) !== true) {
            $code = (int) ($decoded['error_code'] ?? 0);
            $desc = (string) ($decoded['description'] ?? 'خطای نامشخص');
            Logger::error('Telegram API error', ['method' => $method, 'code' => $code, 'desc' => $desc]);
            throw new ApiException($desc, $code, $method);
        }
        return $decoded['result'];
    }

    /** @return array<string,mixed> */
    public function getMe(): array
    {
        /** @var array<string,mixed> $result */
        $result = $this->call('getMe');
        return $result;
    }

    public function setWebhook(string $url, string $secret): bool
    {
        return (bool) $this->call('setWebhook', [
            'url'             => $url,
            'secret_token'    => $secret,
            'max_connections' => 20,
            'allowed_updates' => ['message', 'edited_message', 'callback_query', 'channel_post'],
            'drop_pending_updates' => true,
        ]);
    }

    public function deleteWebhook(): bool
    {
        return (bool) $this->call('deleteWebhook', ['drop_pending_updates' => false]);
    }

    /** @return array<string,mixed> */
    public function getWebhookInfo(): array
    {
        /** @var array<string,mixed> $info */
        $info = $this->call('getWebhookInfo');
        return $info;
    }

    /** @return array<string,mixed> */
    public function sendMessage(int|string $chatId, string $text, array $extra = []): array
    {
        /** @var array<string,mixed> $msg */
        $msg = $this->call('sendMessage', array_merge([
            'chat_id'                  => $chatId,
            'text'                     => $text,
            'parse_mode'               => 'HTML',
            'link_preview_options'     => ['is_disabled' => true],
        ], $extra));
        return $msg;
    }

    /** @return array<string,mixed>|bool */
    public function editMessageText(int|string $chatId, int $messageId, string $text, array $extra = []): mixed
    {
        return $this->call('editMessageText', array_merge([
            'chat_id'              => $chatId,
            'message_id'           => $messageId,
            'text'                 => $text,
            'parse_mode'           => 'HTML',
            'link_preview_options' => ['is_disabled' => true],
        ], $extra));
    }

    public function answerCallbackQuery(string $id, string $text = '', bool $alert = false): void
    {
        try {
            $this->call('answerCallbackQuery', [
                'callback_query_id' => $id,
                'text'              => $text,
                'show_alert'        => $alert,
            ]);
        } catch (ApiException) {
            // A late answer is not fatal for the user flow.
        }
    }

    public function deleteMessage(int|string $chatId, int $messageId): bool
    {
        return (bool) $this->call('deleteMessage', ['chat_id' => $chatId, 'message_id' => $messageId]);
    }

    /** @return array<string,mixed> */
    public function getChat(int|string $chatId): array
    {
        /** @var array<string,mixed> $chat */
        $chat = $this->call('getChat', ['chat_id' => $chatId]);
        return $chat;
    }

    /** @return array<string,mixed> */
    public function getChatMember(int|string $chatId, int $userId): array
    {
        /** @var array<string,mixed> $member */
        $member = $this->call('getChatMember', ['chat_id' => $chatId, 'user_id' => $userId]);
        return $member;
    }
}
