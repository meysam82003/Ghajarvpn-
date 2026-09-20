<?php
declare(strict_types=1);

use Ghajar\Studio\Telegram\Api;
use Ghajar\Studio\Telegram\ApiException;

/** Offline stand-in for the Telegram API used by the unit tests. */
class FakeApi extends Api
{
    /** @var list<array{method:string,params:array<string,mixed>}> */
    public array $calls = [];
    /** @var array<string,mixed> */
    public array $responses = [];
    public ?ApiException $throw = null;
    public int $nextMessageId = 1000;

    public function __construct()
    {
        parent::__construct('123456789:TEST-TOKEN-TEST-TOKEN-TEST-TOKEN');
    }

    public function call(string $method, array $params = []): mixed
    {
        $this->calls[] = ['method' => $method, 'params' => $params];
        if ($this->throw !== null && in_array($method, ['sendMessage', 'sendPhoto'], true)) {
            throw $this->throw;
        }
        if (array_key_exists($method, $this->responses)) {
            return $this->responses[$method];
        }
        return match ($method) {
            'getMe'             => ['id' => 777, 'is_bot' => true, 'username' => 'Ghajar_vpnbot', 'first_name' => 'Ghajar'],
            'getChat'           => ['id' => -1001, 'type' => 'channel', 'username' => 'Ghajarvpn'],
            'getChatMember'     => ['status' => 'administrator', 'can_post_messages' => true],
            'sendMessage',
            'sendPhoto'         => ['message_id' => $this->nextMessageId++, 'chat' => ['username' => 'Ghajarvpn']],
            'editMessageText',
            'editMessageCaption',
            'editMessageMedia'  => ['message_id' => 1, 'chat' => ['username' => 'Ghajarvpn']],
            'deleteMessage'     => true,
            'setWebhook'        => true,
            'getWebhookInfo'    => ['url' => 'https://example.test/webhook.php', 'pending_update_count' => 0],
            'answerCallbackQuery' => true,
            default             => true,
        };
    }

    /** @return list<array<string,mixed>> */
    public function sentTo(string $method): array
    {
        return array_values(array_map(
            static fn (array $c) => $c['params'],
            array_filter($this->calls, static fn (array $c) => $c['method'] === $method)
        ));
    }

    public function reset(): void
    {
        $this->calls = [];
        $this->throw = null;
    }
}
