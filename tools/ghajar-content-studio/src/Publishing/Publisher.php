<?php
declare(strict_types=1);

namespace Ghajar\Studio\Publishing;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Drafts\DraftComposer;
use Ghajar\Studio\Drafts\DraftRepository;
use Ghajar\Studio\Drafts\PostNumbering;
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
        $error  = $this->composer->validate($text);
        if ($error !== null) {
            if ($number !== null) {
                $this->numbering->rollback($number);
            }
            $this->drafts->releasePublishToken($draftId);
            return ['ok' => false, 'message' => $error];
        }

        try {
            $message = $this->api->sendMessage($channel, $text);
        } catch (ApiException $e) {
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

        Database::transaction(function () use ($draftId, $channel, $messageId, $number, $text, $url): void {
            $this->drafts->markPublished($draftId, $channel, $messageId, $number);
            $this->drafts->setRendered($draftId, $text);
            Database::connect()->prepare(
                'INSERT OR IGNORE INTO published_posts (draft_id, post_number, channel, message_id, message_url, text)
                 VALUES (:d, :n, :c, :m, :u, :t)'
            )->execute([
                ':d' => $draftId, ':n' => $number, ':c' => $channel,
                ':m' => $messageId, ':u' => $url, ':t' => $text,
            ]);
        });
        $this->drafts->releasePublishToken($draftId);

        return [
            'ok'         => true,
            'message'    => '✅ پست با موفقیت منتشر شد.',
            'number'     => $number,
            'message_id' => $messageId,
            'url'        => $url,
        ];
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
