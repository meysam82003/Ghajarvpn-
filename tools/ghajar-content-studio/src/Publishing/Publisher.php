<?php
declare(strict_types=1);

namespace Ghajar\Studio\Publishing;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Drafts\DraftComposer;
use Ghajar\Studio\Drafts\DraftRepository;
use Ghajar\Studio\Drafts\PostNumbering;
use Ghajar\Studio\Support\Html;
use Ghajar\Studio\Support\Logger;
use Ghajar\Studio\Telegram\Api;
use Ghajar\Studio\Telegram\ApiException;

final class Publisher
{
    public function __construct(
        private Api $api,
        private DraftRepository $drafts = new DraftRepository(),
        private DraftComposer $composer = new DraftComposer(),
        private PostNumbering $numbering = new PostNumbering(),
    ) {
    }

    /**
     * Verify the bot can actually post in the target channel.
     *
     * @return array{ok:bool,message:string,chat?:array<string,mixed>}
     */
    public function checkChannel(?string $channel = null): array
    {
        $channel ??= Settings::get('channel', '@Ghajarvpn');
        if ($channel === '') {
            return ['ok' => false, 'message' => 'کانالی تنظیم نشده است. از بخش ⚙️ تنظیمات کانال را ثبت کنید.'];
        }
        try {
            $chat = $this->api->getChat($channel);
        } catch (ApiException $e) {
            return ['ok' => false, 'message' => 'کانال یافت نشد یا ربات به آن دسترسی ندارد. ' . $e->friendly()];
        }
        try {
            $me     = $this->api->getMe();
            $member = $this->api->getChatMember($channel, (int) ($me['id'] ?? 0));
        } catch (ApiException $e) {
            return ['ok' => false, 'message' => 'بررسی دسترسی ربات ممکن نشد. ' . $e->friendly()];
        }

        $status = (string) ($member['status'] ?? '');
        if (!in_array($status, ['administrator', 'creator'], true)) {
            return ['ok' => false, 'message' => 'ربات هنوز ادمین کانال نیست.', 'chat' => $chat];
        }
        if ($status === 'administrator' && ($member['can_post_messages'] ?? true) === false) {
            return ['ok' => false, 'message' => 'ربات ادمین است اما مجوز «ارسال پیام» ندارد.', 'chat' => $chat];
        }
        return ['ok' => true, 'message' => 'ربات ادمین کانال است و می‌تواند پیام ارسال کند.', 'chat' => $chat];
    }

    /**
     * Publish a draft. Numbers are reserved before the send and rolled back
     * when Telegram clearly rejected the message.
     *
     * @return array{ok:bool,message:string,number?:int|null,url?:string,message_id?:int,state?:string}
     */
    public function publish(int $draftId): array
    {
        $draft = $this->drafts->find($draftId);
        if ($draft === null) {
            return ['ok' => false, 'message' => 'پیش‌نویس پیدا نشد.'];
        }
        if ($draft['status'] === DraftRepository::STATUS_PUBLISHED) {
            return ['ok' => false, 'message' => 'این پیش‌نویس قبلاً منتشر شده است.'];
        }

        $token = $this->drafts->claimPublishToken($draftId);
        if ($token === null) {
            return ['ok' => false, 'message' => 'یک عملیات انتشار برای این پیش‌نویس در جریان است یا قبلاً منتشر شده.'];
        }

        $channel = Settings::get('channel', '@Ghajarvpn');
        $check   = $this->checkChannel($channel);
        if (!$check['ok']) {
            $this->drafts->releasePublishToken($draftId);
            return ['ok' => false, 'message' => $check['message']];
        }

        $number = Database::transaction(fn () => $this->numbering->reserve($draft));
        $text   = $this->composer->render($draft, $number);
        $photo  = (string) ($draft['media_file_id'] ?? '');
        $error  = $this->composer->validate($text, $photo !== '');
        if ($error !== null) {
            if ($number !== null) {
                $this->numbering->rollback($number);
            }
            $this->drafts->releasePublishToken($draftId);
            return ['ok' => false, 'message' => $error];
        }

        $emojiFallback = false;
        try {
            $message = $this->send($channel, $text, $photo);
        } catch (ApiException $e) {
            // Custom (premium) emoji are only allowed for some bots; rather
            // than failing the post, retry once with the plain emoji.
            if (!$e->networkFailure && Html::hasCustomEmoji($text) && $this->isCustomEmojiError($e)) {
                try {
                    $text          = Html::stripCustomEmoji($text);
                    $message       = $this->send($channel, $text, $photo);
                    $emojiFallback = true;
                } catch (ApiException $retry) {
                    $e = $retry;
                }
            }
        }
        if (!isset($message)) {
            /** @var ApiException $e */
            $this->drafts->releasePublishToken($draftId);
            if ($e->networkFailure) {
                // The message may or may not have gone through — never retry blindly.
                $this->drafts->setStatus($draftId, DraftRepository::STATUS_UNKNOWN);
                Logger::error('Publish result unknown', ['draft' => $draftId]);
                return [
                    'ok'      => false,
                    'state'   => 'unknown',
                    'number'  => $number,
                    'message' => "⚠️ نتیجه انتشار نامشخص است (قطع ارتباط با تلگرام).\n"
                        . "شماره پست رزرو شد و برای جلوگیری از ارسال تکراری، ارسال مجدد خودکار انجام نشد.\n"
                        . 'ابتدا کانال را بررسی کنید؛ اگر پست ارسال نشده بود، دوباره «تأیید و انتشار» را بزنید.',
                ];
            }
            if ($number !== null) {
                $this->numbering->rollback($number);
            }
            $this->drafts->setStatus($draftId, DraftRepository::STATUS_FAILED);
            return ['ok' => false, 'message' => 'انتشار ناموفق بود. ' . $e->friendly()];
        }

        $messageId = (int) ($message['message_id'] ?? 0);
        if ($messageId === 0) {
            $this->drafts->releasePublishToken($draftId);
            return ['ok' => false, 'message' => 'تلگرام شناسه پیام را برنگرداند؛ انتشار تأیید نشد.'];
        }

        $url = $this->messageUrl($channel, $message, $messageId);

        Database::transaction(function () use ($draftId, $channel, $messageId, $number, $text, $url, $photo): void {
            $this->drafts->markPublished($draftId, $channel, $messageId, $number);
            $this->drafts->setRendered($draftId, $text);
            Database::connect()->prepare(
                'INSERT OR IGNORE INTO published_posts
                    (draft_id, post_number, channel, message_id, message_url, text, media_file_id, has_media)
                 VALUES (:d, :n, :c, :m, :u, :t, :f, :h)'
            )->execute([
                ':d' => $draftId, ':n' => $number, ':c' => $channel,
                ':m' => $messageId, ':u' => $url, ':t' => $text,
                ':f' => $photo, ':h' => $photo !== '' ? 1 : 0,
            ]);
        });
        $this->drafts->releasePublishToken($draftId);

        return [
            'ok'             => true,
            'message'        => '✅ پست با موفقیت منتشر شد.',
            'number'         => $number,
            'message_id'     => $messageId,
            'url'            => $url,
            'emoji_fallback' => $emojiFallback,
        ];
    }

    /** Send as a photo with caption when the draft carries an image. */
    private function send(string $channel, string $text, string $photo): array
    {
        return $photo !== ''
            ? $this->api->sendPhoto($channel, $photo, $text)
            : $this->api->sendMessage($channel, $text);
    }

    private function isCustomEmojiError(ApiException $e): bool
    {
        $message = strtolower($e->getMessage());
        foreach (['custom_emoji', 'custom emoji', 'emoji_invalid', 'premium'] as $needle) {
            if (str_contains($message, $needle)) {
                return true;
            }
        }
        return false;
    }

    /** @param array<string,mixed> $message */
    private function messageUrl(string $channel, array $message, int $messageId): string
    {
        $username = (string) ($message['chat']['username'] ?? '');
        if ($username === '' && str_starts_with($channel, '@')) {
            $username = substr($channel, 1);
        }
        return $username !== '' ? sprintf('https://t.me/%s/%d', $username, $messageId) : '';
    }

    /** Edit an already published post (Telegram allows this for bot messages). */
    public function editPublished(int $publishedId, string $newText): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM published_posts WHERE id = :id');
        $stmt->execute([':id' => $publishedId]);
        $row = $stmt->fetch();
        if ($row === false) {
            return ['ok' => false, 'message' => 'پست پیدا نشد.'];
        }
        try {
            $this->api->editMessageText((string) $row['channel'], (int) $row['message_id'], $newText);
        } catch (ApiException $e) {
            return ['ok' => false, 'message' => 'ویرایش ممکن نشد. ' . $e->friendly()];
        }
        Database::connect()->prepare('UPDATE published_posts SET text = :t WHERE id = :id')
            ->execute([':t' => $newText, ':id' => $publishedId]);
        return ['ok' => true, 'message' => '✅ پست منتشرشده ویرایش شد.'];
    }

    public function deletePublished(int $publishedId): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM published_posts WHERE id = :id');
        $stmt->execute([':id' => $publishedId]);
        $row = $stmt->fetch();
        if ($row === false) {
            return ['ok' => false, 'message' => 'پست پیدا نشد.'];
        }
        try {
            $this->api->deleteMessage((string) $row['channel'], (int) $row['message_id']);
        } catch (ApiException $e) {
            return ['ok' => false, 'message' => 'حذف ممکن نشد. ' . $e->friendly()];
        }
        Database::connect()->prepare('UPDATE published_posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id')
            ->execute([':id' => $publishedId]);
        return ['ok' => true, 'message' => '🗑 پست از کانال حذف شد.'];
    }

    /**
     * Parse a t.me message link into a chat reference + message id.
     *
     * Supports https://t.me/<channel>/<id> and private links
     * https://t.me/c/<internalId>/<id>.
     *
     * @return array{chat:string,message_id:int}|null
     */
    public static function parseMessageLink(string $link): ?array
    {
        $link = trim($link);
        if (preg_match('#t\.me/c/(\d+)/(\d+)#i', $link, $m)) {
            return ['chat' => '-100' . $m[1], 'message_id' => (int) $m[2]];
        }
        if (preg_match('#t\.me/([A-Za-z][A-Za-z0-9_]{3,31})/(\d+)#i', $link, $m)) {
            return ['chat' => '@' . $m[1], 'message_id' => (int) $m[2]];
        }
        return null;
    }

    /**
     * Replace the content of an existing channel message: text, photo, or both.
     * Telegram cannot turn a text-only message into a photo message, so that
     * case is reported clearly instead of failing silently.
     *
     * @return array{ok:bool,message:string,emoji_fallback?:bool}
     */
    public function editMessage(string $chat, int $messageId, ?string $text, string $photo = ''): array
    {
        if ($text === null && $photo === '') {
            return ['ok' => false, 'message' => 'چیزی برای جایگزینی داده نشده است.'];
        }

        $known    = $this->findPublished($chat, $messageId);
        $hadMedia = $known !== null
            ? (int) ($known['has_media'] ?? 0) === 1
            : $photo !== '';
        $fallback = false;

        $attempt = function (string $body) use ($chat, $messageId, $photo, $hadMedia): void {
            if ($photo !== '') {
                $this->api->editMessageMedia($chat, $messageId, $photo, $body);
                return;
            }
            if ($hadMedia) {
                $this->api->editMessageCaption($chat, $messageId, $body);
                return;
            }
            $this->api->editMessageText($chat, $messageId, $body);
        };

        $body = $text ?? (string) ($known['text'] ?? '');
        if ($photo !== '' && $text === null) {
            $body = (string) ($known['text'] ?? '');
        }
        $limitError = $this->composer->validate($body, $photo !== '' || $hadMedia);
        if ($limitError !== null) {
            return ['ok' => false, 'message' => $limitError];
        }

        try {
            $attempt($body);
        } catch (ApiException $e) {
            if (Html::hasCustomEmoji($body) && $this->isCustomEmojiError($e)) {
                try {
                    $body = Html::stripCustomEmoji($body);
                    $attempt($body);
                    $fallback = true;
                } catch (ApiException $retry) {
                    return ['ok' => false, 'message' => 'ویرایش ممکن نشد. ' . $retry->friendly()];
                }
            } else {
                $hint = str_contains(strtolower($e->getMessage()), 'there is no text in the message')
                    ? ' (این پیام عکس‌دار است؛ فقط کپشن آن قابل ویرایش است.)'
                    : '';
                return ['ok' => false, 'message' => 'ویرایش ممکن نشد. ' . $e->friendly() . $hint];
            }
        }

        if ($known !== null) {
            Database::connect()->prepare(
                'UPDATE published_posts SET text = :t, media_file_id = :f,
                 has_media = :h WHERE id = :id'
            )->execute([
                ':t'  => $body,
                ':f'  => $photo !== '' ? $photo : (string) ($known['media_file_id'] ?? ''),
                ':h'  => ($photo !== '' || $hadMedia) ? 1 : 0,
                ':id' => (int) $known['id'],
            ]);
        }

        return [
            'ok'             => true,
            'message'        => '✅ پیام کانال با موفقیت ویرایش شد.',
            'emoji_fallback' => $fallback,
        ];
    }

    /** @return array<string,mixed>|null */
    public function findPublished(string $chat, int $messageId): ?array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM published_posts WHERE message_id = :m AND (channel = :c OR :c = \'\') ORDER BY id DESC LIMIT 1'
        );
        $stmt->execute([':m' => $messageId, ':c' => $chat]);
        $row = $stmt->fetch();
        return $row ?: null;
    }

    /** @return list<array<string,mixed>> */
    public function history(int $limit = 10, int $offset = 0): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM published_posts ORDER BY id DESC LIMIT :limit OFFSET :offset'
        );
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->bindValue(':offset', $offset, \PDO::PARAM_INT);
        $stmt->execute();
        return $stmt->fetchAll();
    }
}
