<?php
declare(strict_types=1);

namespace Ghajar\Studio\Bot;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Drafts\DraftComposer;
use Ghajar\Studio\Drafts\DraftRepository;
use Ghajar\Studio\Drafts\PostNumbering;
use Ghajar\Studio\Github\GithubClient;
use Ghajar\Studio\Github\GithubException;
use Ghajar\Studio\Links\LinkRepository;
use Ghajar\Studio\Publishing\Publisher;
use Ghajar\Studio\Support\Html;
use Ghajar\Studio\Support\Logger;
use Ghajar\Studio\Support\Str;
use Ghajar\Studio\Telegram\Api;
use Ghajar\Studio\Telegram\ApiException;
use Ghajar\Studio\Telegram\Keyboard;
use Ghajar\Studio\Templates\CustomEmojiRepository;
use Ghajar\Studio\Templates\MessageParser;
use Ghajar\Studio\Templates\TemplateRenderer;
use Ghajar\Studio\Templates\TemplateRepository;

final class Router
{
    private DraftRepository $drafts;
    private TemplateRepository $templates;
    private LinkRepository $links;
    private DraftComposer $composer;
    private PostNumbering $numbering;
    private Publisher $publisher;
    private MessageParser $parser;
    private TemplateRenderer $renderer;
    private CustomEmojiRepository $emoji;

    public function __construct(private Api $api)
    {
        $this->drafts    = new DraftRepository();
        $this->templates = new TemplateRepository();
        $this->links     = new LinkRepository();
        $this->composer  = new DraftComposer();
        $this->numbering = new PostNumbering();
        $this->publisher = new Publisher($api);
        $this->parser    = new MessageParser();
        $this->renderer  = new TemplateRenderer();
        $this->emoji     = new CustomEmojiRepository();
    }

    // ---------------------------------------------------------------- entry

    /** @param array<string,mixed> $update */
    public function handle(array $update): void
    {
        $updateId = (int) ($update['update_id'] ?? 0);
        if ($updateId > 0 && !$this->rememberUpdate($updateId)) {
            return; // duplicate delivery
        }

        if (isset($update['callback_query'])) {
            $this->onCallback((array) $update['callback_query']);
            return;
        }
        if (isset($update['message'])) {
            $this->onMessage((array) $update['message']);
        }
    }

    private function rememberUpdate(int $updateId): bool
    {
        try {
            Database::connect()
                ->prepare('INSERT INTO processed_updates (update_id) VALUES (:id)')
                ->execute([':id' => $updateId]);
            Database::connect()->exec(
                'DELETE FROM processed_updates WHERE update_id NOT IN
                 (SELECT update_id FROM processed_updates ORDER BY update_id DESC LIMIT 500)'
            );
            return true;
        } catch (\PDOException) {
            return false;
        }
    }

    private function isAdmin(int $userId): bool
    {
        return $userId > 0 && $userId === Settings::int('admin_id');
    }

    // -------------------------------------------------------------- message

    /** @param array<string,mixed> $message */
    private function onMessage(array $message): void
    {
        $userId = (int) ($message['from']['id'] ?? 0);
        $chatId = (int) ($message['chat']['id'] ?? 0);
        if (!$this->isAdmin($userId)) {
            if ($chatId !== 0 && ($message['chat']['type'] ?? '') === 'private') {
                $this->safeSend($chatId, '⛔️ این ربات خصوصی است و فقط مدیر ثبت‌شده می‌تواند از آن استفاده کند.');
            }
            return;
        }

        $state = new State($userId);
        $text  = (string) ($message['text'] ?? $message['caption'] ?? '');

        if ($text !== '' && str_starts_with($text, '/')) {
            $this->onCommand($chatId, $userId, $state, strtolower(trim(explode(' ', $text)[0])));
            return;
        }

        $photoId = $this->photoFileId($message);

        if ($text === '' && $photoId !== '') {
            $this->onPhotoOnly($chatId, $userId, $state, $photoId);
            return;
        }

        if ($text === '') {
            $this->safeSend(
                $chatId,
                "⚠️ محتوای متنی در این پیام پیدا نشد.\n"
                . 'اگر پیام از یک چت محافظت‌شده فوروارد شده، تلگرام اجازه دریافت متن آن را به ربات نمی‌دهد. '
                . 'لطفاً متن را کپی و به صورت پیام معمولی ارسال کنید.'
            );
            return;
        }

        $entities = (array) ($message['entities'] ?? $message['caption_entities'] ?? []);
        $html     = Html::fromEntities($text, $entities);

        $current = $state->load();
        if ($current['state'] !== '') {
            $this->onStateInput($chatId, $userId, $state, $current, $text, $html, $message);
            return;
        }

        $this->createDraftFromMessage($chatId, $userId, $state, $message, $text, $html);
    }

    /** The largest photo in a message, if any. */
    private function photoFileId(array $message): string
    {
        $photos = (array) ($message['photo'] ?? []);
        if ($photos !== []) {
            $last = end($photos);
            return (string) ($last['file_id'] ?? '');
        }
        $document = (array) ($message['document'] ?? []);
        if (str_starts_with((string) ($document['mime_type'] ?? ''), 'image/')) {
            return (string) ($document['file_id'] ?? '');
        }
        return '';
    }

    /** A bare photo attaches itself to the draft the admin is working on. */
    private function onPhotoOnly(int $chatId, int $userId, State $state, string $fileId): void
    {
        $current = $state->load();
        $payload = $current['payload'];

        if ($current['state'] === 'edit.await_photo') {
            $this->applyChannelEditPhoto($chatId, $userId, $state, $payload, $fileId);
            return;
        }

        $draftId = (int) ($payload['draft'] ?? 0);
        if ($draftId === 0) {
            $latest  = $this->drafts->listByStatus(DraftRepository::STATUS_DRAFT, 1);
            $draftId = $latest === [] ? 0 : (int) $latest[0]['id'];
        }
        if ($draftId === 0) {
            $this->safeSend($chatId, '🖼 عکس دریافت شد، اما هنوز پیش‌نویسی وجود ندارد. اول متن پست را بفرستید، بعد عکس را.');
            return;
        }
        $this->drafts->setMedia($draftId, 'photo', $fileId);
        $this->safeSend($chatId, '🖼 عکس به پیش‌نویس #' . $draftId . ' اضافه شد. متن به‌صورت کپشن زیر عکس منتشر می‌شود.');
        $this->showDraft($chatId, $userId, null, $draftId);
    }

    private function onCommand(int $chatId, int $userId, State $state, string $command): void
    {
        $state->clear();
        switch ($command) {
            case '/start':
            case '/menu':
            case '/home':
                $this->showHome($chatId, $userId, null);
                return;
            case '/new':
                $this->safeSend($chatId, '📝 متن پست را بفرستید یا پیام موردنظر را فوروارد کنید.');
                return;
            case '/stats':
                $this->showStats($chatId, $userId, null);
                return;
            case '/cancel':
                $this->safeSend($chatId, '❌ عملیات لغو شد.');
                $this->showHome($chatId, $userId, null);
                return;
            case '/id':
                $this->safeSend($chatId, 'آیدی عددی شما: <code>' . $userId . '</code>');
                return;
            default:
                $this->safeSend($chatId, 'دستور ناشناخته. /start را بزنید.');
        }
    }

    /** @param array<string,mixed> $message */
    private function createDraftFromMessage(int $chatId, int $userId, State $state, array $message, string $text, string $html): void
    {
        $content = $this->composer->parseIncoming($html);
        $entities = (array) ($message['entities'] ?? $message['caption_entities'] ?? []);
        $emoji    = Html::customEmoji($text, $entities);
        $meta     = [
            'forwarded'    => isset($message['forward_origin']) || isset($message['forward_date']),
            'received_at'  => gmdate('c'),
            'message_id'   => (int) ($message['message_id'] ?? 0),
            'custom_emoji' => $emoji,
        ];
        $template = $this->templates->default();
        $draftId  = $this->drafts->create($userId, $text, $html, $content, $template['id'] ?? null, $meta);

        $photoId = $this->photoFileId($message);
        if ($photoId !== '') {
            $this->drafts->setMedia($draftId, 'photo', $photoId);
        }
        foreach ($emoji as $item) {
            $this->emoji->save('emoji_' . $item['id'], (string) $item['id'], (string) $item['emoji']);
        }

        $state->set('choose_template', ['draft' => $draftId]);
        $this->showTemplateChooser($chatId, $userId, null, $draftId);
    }

    // ------------------------------------------------------------- callback

    /** @param array<string,mixed> $callback */
    private function onCallback(array $callback): void
    {
        $userId    = (int) ($callback['from']['id'] ?? 0);
        $chatId    = (int) ($callback['message']['chat']['id'] ?? 0);
        $messageId = (int) ($callback['message']['message_id'] ?? 0);
        $data      = (string) ($callback['data'] ?? '');
        $queryId   = (string) ($callback['id'] ?? '');

        if (!$this->isAdmin($userId)) {
            $this->api->answerCallbackQuery($queryId, '⛔️ دسترسی ندارید.', true);
            return;
        }
        $this->api->answerCallbackQuery($queryId);

        $state = new State($userId);
        $parts = explode('|', $data);
        $head  = $parts[0] ?? '';

        try {
            match ($head) {
                'h'   => $this->showHome($chatId, $userId, $messageId),
                'np'  => $this->promptNewPost($chatId, $userId, $state, $messageId),
                'tpl' => $this->onTemplateCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'd'   => $this->onDraftCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'p'   => $this->onPublishedCallback($chatId, $userId, $messageId, $parts, $queryId),
                'l'   => $this->onLinkCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'v'   => $this->onVersionCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'n'   => $this->onNumberingCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                's'   => $this->onSettingsCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'em'  => $this->onEmojiCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'edit' => $this->onChannelEditCallback($chatId, $userId, $state, $messageId, $parts, $queryId),
                'st'  => $this->showStats($chatId, $userId, $messageId),
                'x'   => $this->cancel($chatId, $userId, $state, $messageId),
                default => $this->api->answerCallbackQuery($queryId, 'این دکمه دیگر معتبر نیست.', true),
            };
        } catch (ApiException $e) {
            Logger::error('callback failed', ['data' => $data, 'code' => $e->errorCode]);
            $this->safeSend($chatId, '⚠️ ' . $e->friendly());
        } catch (\Throwable $e) {
            Logger::error('callback exception', ['data' => $data, 'error' => $e->getMessage()]);
            $this->safeSend($chatId, '⚠️ خطای داخلی هنگام اجرای این عملیات. جزئیات در لاگ ثبت شد.');
        }
    }

    // ----------------------------------------------------------- state input

    /**
     * @param array{state:string,payload:array<string,mixed>,menu_message_id:?int} $current
     */
    private function onStateInput(int $chatId, int $userId, State $state, array $current, string $text, string $html, array $message = []): void
    {
        $name    = $current['state'];
        $payload = $current['payload'];
        $value   = trim($text);

        switch ($name) {
            case 'choose_template':
                // A new message while choosing simply starts a fresh draft.
                $state->clear();
                $this->safeSend($chatId, '📥 پیام جدید دریافت شد؛ پیش‌نویس تازه ساخته می‌شود.');
                $this->createDraftFromMessage($chatId, $userId, $state, $message, $text, $html);
                return;

            case 'draft.edit_text':
                $this->updateDraftPart($chatId, $userId, $state, (int) $payload['draft'], 'body', $html, 'متن');
                return;
            case 'draft.edit_title':
                $this->updateDraftPart($chatId, $userId, $state, (int) $payload['draft'], 'title', $html, 'عنوان');
                return;
            case 'draft.edit_emoji':
                $this->updateDraftPart($chatId, $userId, $state, (int) $payload['draft'], 'title_emoji', $value, 'ایموجی عنوان');
                return;
            case 'draft.edit_section':
                $this->updateDraftPart($chatId, $userId, $state, (int) $payload['draft'], (string) $payload['key'], $html, (string) $payload['label']);
                return;
            case 'draft.set_number':
                $this->applyManualNumber($chatId, $userId, $state, (int) $payload['draft'], $value);
                return;
            case 'draft.search':
                $state->clear();
                $this->showDraftSearchResults($chatId, $userId, null, $value);
                return;

            case 'tpl.new_name':
                $this->createTemplateFromDraft($chatId, $userId, $state, (int) $payload['draft'], $value);
                return;
            case 'tpl.rename':
                $this->templates->rename((int) $payload['template'], Str::truncate($value, 60));
                $state->clear();
                $this->safeSend($chatId, '✅ نام قالب تغییر کرد.');
                $this->showTemplate($chatId, $userId, null, (int) $payload['template']);
                return;
            case 'tpl.section_text':
                $this->applyTemplateSectionText($chatId, $userId, $state, (int) $payload['template'], (int) $payload['index'], $html);
                return;
            case 'tpl.section_label':
                $this->applyTemplateSectionLabel($chatId, $userId, $state, (int) $payload['template'], (int) $payload['index'], $value);
                return;

            case 'link.edit':
                $this->applyLinkValue($chatId, $userId, $state, (int) $payload['link'], $value);
                return;
            case 'link.anchor':
                $link = $this->links->find((int) $payload['link']);
                if ($link !== null) {
                    $this->links->setAnchor((string) $link['key'], Str::truncate($value, 60));
                }
                $state->clear();
                $this->safeSend($chatId, '✅ متن نمایشی لینک به‌روزرسانی شد.');
                $this->showLinkList($chatId, $userId, null);
                return;
            case 'link.add_label':
                $state->set('link.add_value', ['label' => Str::truncate($value, 40)]);
                $this->safeSend($chatId, '🔗 حالا آدرس لینک را بفرستید (با http:// یا https://).');
                return;
            case 'link.add_value':
                $this->addCustomLink($chatId, $userId, $state, (string) $payload['label'], $value);
                return;

            case 'emoji.capture':
                $this->captureEmoji($chatId, $userId, $state, $text, $message);
                return;
            case 'edit.await_link':
                $this->applyChannelEditLink($chatId, $userId, $state, $value);
                return;
            case 'edit.await_text':
                $this->applyChannelEditText($chatId, $userId, $state, $payload, $html);
                return;
            case 'edit.menu':
            case 'edit.await_photo':
            case 'draft.await_photo':
                // Waiting for a photo; a text message here just re-opens the menu.
                $state->clear();
                $this->safeSend($chatId, 'ℹ️ منتظر دریافت عکس بودم. عملیات لغو شد.');
                $this->showHome($chatId, $userId, null);
                return;
            case 'settings.channel':
                $this->applyChannel($chatId, $userId, $state, $value);
                return;
            case 'settings.repo':
                $this->applyRepo($chatId, $userId, $state, $value);
                return;
            case 'numbering.set_next':
                $this->applyNextNumber($chatId, $userId, $state, $value);
                return;
        }

        $state->clear();
        $this->safeSend($chatId, 'وضعیت قبلی منقضی شده بود. از منوی اصلی دوباره تلاش کنید.');
        $this->showHome($chatId, $userId, null);
    }

    // ------------------------------------------------------------- screens

    private function showHome(int $chatId, int $userId, ?int $messageId): void
    {
        $channel = Settings::get('channel', '@Ghajarvpn');
        $text = "🏠 <b>Ghajar Content Studio</b>\n\n"
            . "استودیوی ساخت و انتشار پست کانال قاجار VPN.\n"
            . "📢 کانال فعلی: <code>" . Html::escape($channel) . "</code>\n"
            . "🔢 شماره پست بعدی: <b>" . Str::toPersianDigits((string) $this->numbering->next()) . "</b>\n\n"
            . "برای ساخت پست، کافی است متن را بفرستید یا پیام را فوروارد کنید.";

        $kb = Keyboard::make()
            ->row([['📝 ساخت پست جدید', 'np']])
            ->row([['🎨 قالب‌های من', 'tpl|list|0'], ['📂 پیش‌نویس‌ها', 'd|list|0']])
            ->row([['📢 پست‌های منتشرشده', 'p|list|0'], ['🔗 مدیریت لینک‌ها', 'l|list']])
            ->row([['📦 مدیریت نسخه APK', 'v|home'], ['🔢 شماره‌گذاری', 'n|home']])
            ->row([['⚙️ تنظیمات', 's|home'], ['📊 آمار', 'st']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function promptNewPost(int $chatId, int $userId, State $state, ?int $messageId): void
    {
        $state->clear();
        $text = "📝 <b>ساخت پست جدید</b>\n\n"
            . "متن پست را بفرستید، یا یک پیام را فوروارد کنید.\n"
            . "قالب‌بندی تلگرام (بولد، ایتالیک، لینک) و ایموجی‌ها حفظ می‌شوند.";
        $this->screen($chatId, $userId, $messageId, $text, Keyboard::make()->button('🏠 بازگشت به خانه', 'h'));
    }

    private function showStats(int $chatId, int $userId, ?int $messageId): void
    {
        $pdo       = Database::connect();
        $published = (int) $pdo->query('SELECT COUNT(*) FROM published_posts WHERE deleted_at IS NULL')->fetchColumn();
        $lastNum   = $pdo->query('SELECT MAX(post_number) FROM published_posts')->fetchColumn();

        $text = "📊 <b>آمار</b>\n\n"
            . '📄 کل پست‌ها (پیش‌نویس + منتشرشده): <b>' . Str::toPersianDigits((string) $this->drafts->countAll()) . "</b>\n"
            . '📢 منتشرشده: <b>' . Str::toPersianDigits((string) $published) . "</b>\n"
            . '📂 پیش‌نویس‌ها: <b>' . Str::toPersianDigits((string) $this->drafts->countByStatus(DraftRepository::STATUS_DRAFT)) . "</b>\n"
            . '🎨 قالب‌ها: <b>' . Str::toPersianDigits((string) $this->templates->count()) . "</b>\n"
            . '🔢 آخرین شماره منتشرشده: <b>' . Str::toPersianDigits((string) ($lastNum !== false && $lastNum !== null ? $lastNum : '—')) . "</b>\n"
            . '➡️ شماره بعدی: <b>' . Str::toPersianDigits((string) $this->numbering->next()) . '</b>';

        $this->screen($chatId, $userId, $messageId, $text, Keyboard::make()->button('🏠 خانه', 'h'));
    }

    private function cancel(int $chatId, int $userId, State $state, ?int $messageId): void
    {
        $state->clear();
        $this->showHome($chatId, $userId, $messageId);
    }

    // ------------------------------------------------------------ templates

    /** @param string[] $parts */
    private function onTemplateCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'list';
        $id     = (int) ($parts[2] ?? 0);
        $index  = (int) ($parts[3] ?? 0);

        switch ($action) {
            case 'list':
                $this->showTemplateList($chatId, $userId, $messageId, (int) ($parts[2] ?? 0));
                return;
            case 'v':
                $this->showTemplate($chatId, $userId, $messageId, $id);
                return;
            case 'prev':
                $this->showTemplatePreview($chatId, $userId, $messageId, $id);
                return;
            case 'def':
                $this->templates->setDefault($id);
                $this->api->answerCallbackQuery($queryId, '⭐️ قالب پیش‌فرض شد.');
                $this->showTemplate($chatId, $userId, $messageId, $id);
                return;
            case 'cp':
                $newId = $this->templates->duplicate($id);
                $this->showTemplate($chatId, $userId, $messageId, $newId ?? $id);
                return;
            case 'rn':
                $state->set('tpl.rename', ['template' => $id]);
                $this->safeSend($chatId, '✏️ نام جدید قالب را بفرستید:');
                return;
            case 'del':
                $this->screen($chatId, $userId, $messageId, '❗️ حذف این قالب قطعی است. مطمئن هستید؟', Keyboard::make()
                    ->row([['🗑 بله، حذف کن', 'tpl|delok|' . $id], ['↩️ انصراف', 'tpl|v|' . $id]]));
                return;
            case 'delok':
                $ok = $this->templates->delete($id);
                $this->api->answerCallbackQuery($queryId, $ok ? 'حذف شد.' : 'حذف ممکن نیست.', !$ok);
                $this->showTemplateList($chatId, $userId, $messageId, 0);
                return;
            case 'sec':
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $index);
                return;
            case 'sectog':
                $this->mutateSection($id, $index, static function (array $s): array {
                    $s['enabled'] = !($s['enabled'] ?? true);
                    return $s;
                });
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $index);
                return;
            case 'secmode':
                $this->mutateSection($id, $index, static function (array $s): array {
                    $s['mode'] = ($s['mode'] ?? 'variable') === 'fixed' ? 'variable' : 'fixed';
                    return $s;
                });
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $index);
                return;
            case 'secup':
            case 'secdown':
                $this->moveSection($id, $index, $action === 'secup' ? -1 : 1);
                $newIndex = max(0, $index + ($action === 'secup' ? -1 : 1));
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $newIndex);
                return;
            case 'secdel':
                $this->deleteSection($id, $index);
                $this->showTemplate($chatId, $userId, $messageId, $id);
                return;
            case 'secedit':
                $state->set('tpl.section_text', ['template' => $id, 'index' => $index]);
                $this->safeSend(
                    $chatId,
                    "✏️ متن/الگوی این بخش را بفرستید.\n\n"
                    . "متغیرهای مجاز:\n<code>" . implode('</code>, <code>', array_map(
                        static fn (string $k) => '{' . $k . '}',
                        array_keys(TemplateRenderer::PLACEHOLDERS)
                    )) . "</code>\n\n"
                    . 'برای بخش اختیاری از <code>[[ ... ]]</code> استفاده کنید؛ اگر متغیر داخل آن خالی باشد، کل بخش حذف می‌شود.'
                );
                return;
            case 'seclabel':
                $state->set('tpl.section_label', ['template' => $id, 'index' => $index]);
                $this->safeSend($chatId, '🏷 نام جدید این بخش را بفرستید:');
                return;
            case 'secq':
                $this->mutateSection($id, $index, static function (array $section): array {
                    $order = ['', 'main', 'footer'];
                    $current = array_search(trim((string) ($section['quote'] ?? '')), $order, true);
                    $section['quote'] = $order[(((int) $current) + 1) % count($order)];
                    return $section;
                });
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $index);
                return;
            case 'secx':
                $this->mutateSection($id, $index, static function (array $section): array {
                    $section['expandable'] = !($section['expandable'] ?? false);
                    return $section;
                });
                $this->showTemplateSection($chatId, $userId, $messageId, $id, $index);
                return;
            case 'secadd':
                $this->addSection($id);
                $this->showTemplate($chatId, $userId, $messageId, $id);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showTemplateList(int $chatId, int $userId, ?int $messageId, int $page): void
    {
        $all    = $this->templates->all();
        $perPage = 8;
        $slice  = array_slice($all, $page * $perPage, $perPage);

        $kb = Keyboard::make();
        foreach ($slice as $template) {
            $label = ($template['is_default'] === 1 ? '⭐️ ' : '') . Str::truncate((string) $template['name'], 40);
            $kb->button($label, 'tpl|v|' . $template['id']);
        }
        $nav = [];
        if ($page > 0) {
            $nav[] = ['⬅️ قبلی', 'tpl|list|' . ($page - 1)];
        }
        if (count($all) > ($page + 1) * $perPage) {
            $nav[] = ['بعدی ➡️', 'tpl|list|' . ($page + 1)];
        }
        if ($nav !== []) {
            $kb->row($nav);
        }
        $kb->button('🏠 خانه', 'h');

        $text = "🎨 <b>قالب‌های من</b>\n\nتعداد: " . Str::toPersianDigits((string) count($all))
            . "\nقالب پیش‌فرض با ⭐️ مشخص شده است.";
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showTemplate(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $template = $this->templates->find($id);
        if ($template === null) {
            $this->showTemplateList($chatId, $userId, $messageId, 0);
            return;
        }
        $text = '🎨 <b>' . Html::escape((string) $template['name']) . "</b>\n"
            . ($template['description'] !== '' ? Html::escape((string) $template['description']) . "\n" : '')
            . "\n<b>بخش‌ها:</b>\n" . Html::escape($this->renderer->outline($template['structure']));

        $kb = Keyboard::make();
        foreach ((array) ($template['structure']['sections'] ?? []) as $i => $section) {
            $mark = ($section['enabled'] ?? true) ? '✅' : '⛔️';
            $kb->button($mark . ' ' . Str::truncate((string) ($section['label'] ?? '—'), 32), 'tpl|sec|' . $id . '|' . $i);
        }
        $kb->row([['➕ افزودن بخش', 'tpl|secadd|' . $id], ['👁 پیش‌نمایش', 'tpl|prev|' . $id]]);
        $kb->row([['⭐️ پیش‌فرض', 'tpl|def|' . $id], ['📄 کپی', 'tpl|cp|' . $id]]);
        $kb->row([['✏️ تغییر نام', 'tpl|rn|' . $id], ['🗑 حذف', 'tpl|del|' . $id]]);
        $kb->row([['↩️ فهرست قالب‌ها', 'tpl|list|0'], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showTemplateSection(int $chatId, int $userId, ?int $messageId, int $id, int $index): void
    {
        $template = $this->templates->find($id);
        $section  = $template['structure']['sections'][$index] ?? null;
        if ($template === null || !is_array($section)) {
            $this->showTemplate($chatId, $userId, $messageId, $id);
            return;
        }
        $quote = trim((string) ($section['quote'] ?? ''));
        $quoteLabel = match ($quote) {
            'main'   => '❝ نقل‌قول اصلی',
            'footer' => '❝ نقل‌قول فوتر',
            ''       => 'بدون نقل‌قول',
            default  => '❝ ' . $quote,
        };
        $text = '🧩 <b>بخش: ' . Html::escape((string) ($section['label'] ?? '')) . "</b>\n\n"
            . 'وضعیت: ' . (($section['enabled'] ?? true) ? '✅ فعال' : '⛔️ غیرفعال') . "\n"
            . 'نوع: ' . ((($section['mode'] ?? 'variable') === 'fixed') ? '📌 ثابت' : '🔁 متغیر') . "\n"
            . 'نقل‌قول: ' . $quoteLabel . (($section['expandable'] ?? false) ? ' (بازشو)' : '') . "\n\n"
            . "<b>الگو:</b>\n<code>" . Html::escape((string) ($section['template'] ?? '')) . '</code>'
            . "\n\nℹ️ بخش‌هایی که نقل‌قول یکسان دارند، داخل یک نقل‌قول مشترک نمایش داده می‌شوند.";

        $kb = Keyboard::make()
            ->row([['✏️ ویرایش الگو', 'tpl|secedit|' . $id . '|' . $index], ['🏷 نام بخش', 'tpl|seclabel|' . $id . '|' . $index]])
            ->row([
                [($section['enabled'] ?? true) ? '⛔️ غیرفعال کن' : '✅ فعال کن', 'tpl|sectog|' . $id . '|' . $index],
                [(($section['mode'] ?? 'variable') === 'fixed') ? '🔁 متغیر کن' : '📌 ثابت کن', 'tpl|secmode|' . $id . '|' . $index],
            ])
            ->row([
                ['❝ تغییر نقل‌قول', 'tpl|secq|' . $id . '|' . $index],
                [($section['expandable'] ?? false) ? '🔽 نقل‌قول عادی' : '🔼 نقل‌قول بازشو', 'tpl|secx|' . $id . '|' . $index],
            ])
            ->row([['🔼 بالا', 'tpl|secup|' . $id . '|' . $index], ['🔽 پایین', 'tpl|secdown|' . $id . '|' . $index], ['🗑 حذف', 'tpl|secdel|' . $id . '|' . $index]])
            ->row([['↩️ بازگشت به قالب', 'tpl|v|' . $id], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showTemplatePreview(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $template = $this->templates->find($id);
        if ($template === null) {
            $this->showTemplateList($chatId, $userId, $messageId, 0);
            return;
        }
        $vars = array_merge($this->links->placeholders(), [
            'number'      => Str::toPersianDigits((string) $this->numbering->next()),
            'title'       => '<b>عنوان نمونه</b>',
            'title_emoji' => '🤔',
            'intro'       => 'این یک متن نمونه برای پیش‌نمایش قالب است.',
            'body'        => "بدنه اصلی پست اینجا قرار می‌گیرد.\nخط دوم بدنه.",
            'features'    => "⚡ قابلیت اول\n📊 قابلیت دوم\n🔍 قابلیت سوم",
            'summary'     => 'جمع‌بندی نمونه.',
            'slogan'      => 'قاجار VPN | انتخاب آگاهانه، اتصال ساده.',
            'channel'     => Settings::get('channel', '@Ghajarvpn'),
            'bot'         => '@' . (Settings::get('bot_username') ?: 'Ghajar_vpnbot'),
            'version'     => Settings::get('current_version', '1.0.4'),
            'date'        => Str::toPersianDigits(gmdate('Y-m-d')),
        ]);
        $preview = $this->renderer->render($template['structure'], $vars);

        $this->screen(
            $chatId,
            $userId,
            $messageId,
            "👁 <b>پیش‌نمایش قالب</b>\n\n" . $preview,
            Keyboard::make()->row([['↩️ بازگشت', 'tpl|v|' . $id], ['🏠 خانه', 'h']])
        );
    }

    private function mutateSection(int $templateId, int $index, callable $mutator): void
    {
        $template = $this->templates->find($templateId);
        if ($template === null || !isset($template['structure']['sections'][$index])) {
            return;
        }
        $structure = $template['structure'];
        $structure['sections'][$index] = $mutator((array) $structure['sections'][$index]);
        $this->templates->updateStructure($templateId, $structure);
    }

    private function moveSection(int $templateId, int $index, int $delta): void
    {
        $template = $this->templates->find($templateId);
        if ($template === null) {
            return;
        }
        $sections = array_values((array) ($template['structure']['sections'] ?? []));
        $target   = $index + $delta;
        if (!isset($sections[$index], $sections[$target])) {
            return;
        }
        [$sections[$index], $sections[$target]] = [$sections[$target], $sections[$index]];
        $structure             = $template['structure'];
        $structure['sections'] = $sections;
        $this->templates->updateStructure($templateId, $structure);
    }

    private function deleteSection(int $templateId, int $index): void
    {
        $template = $this->templates->find($templateId);
        if ($template === null) {
            return;
        }
        $sections = array_values((array) ($template['structure']['sections'] ?? []));
        if (!isset($sections[$index]) || count($sections) <= 1) {
            return;
        }
        unset($sections[$index]);
        $structure             = $template['structure'];
        $structure['sections'] = array_values($sections);
        $this->templates->updateStructure($templateId, $structure);
    }

    private function addSection(int $templateId): void
    {
        $template = $this->templates->find($templateId);
        if ($template === null) {
            return;
        }
        $structure   = $template['structure'];
        $structure['sections'][] = [
            'key'      => 'custom_' . bin2hex(random_bytes(2)),
            'label'    => 'بخش جدید',
            'mode'     => 'fixed',
            'enabled'  => true,
            'template' => 'متن این بخش را ویرایش کنید.',
        ];
        $this->templates->updateStructure($templateId, $structure);
    }

    private function applyTemplateSectionText(int $chatId, int $userId, State $state, int $templateId, int $index, string $html): void
    {
        $this->mutateSection($templateId, $index, static function (array $s) use ($html): array {
            $s['template'] = $html;
            return $s;
        });
        $state->clear();
        $this->safeSend($chatId, '✅ الگوی بخش به‌روزرسانی شد.');
        $this->showTemplateSection($chatId, $userId, null, $templateId, $index);
    }

    private function applyTemplateSectionLabel(int $chatId, int $userId, State $state, int $templateId, int $index, string $label): void
    {
        $this->mutateSection($templateId, $index, static function (array $s) use ($label): array {
            $s['label'] = Str::truncate($label, 40);
            return $s;
        });
        $state->clear();
        $this->showTemplateSection($chatId, $userId, null, $templateId, $index);
    }

    private function createTemplateFromDraft(int $chatId, int $userId, State $state, int $draftId, string $name): void
    {
        $draft = $this->drafts->find($draftId);
        if ($draft === null) {
            $state->clear();
            $this->safeSend($chatId, 'پیش‌نویس پیدا نشد.');
            return;
        }
        $map = [];
        foreach ($this->links->all() as $link) {
            $value = trim((string) $link['value']);
            if ($value !== '') {
                $map[$value] = (string) preg_replace('/_(url|link)$/', '', (string) $link['key']) . '_url';
            }
        }
        $structure = $this->parser->toTemplateStructure((array) $draft['content'], $map);
        $templateId = $this->templates->create(Str::truncate($name, 60), $structure, 'ساخته‌شده از یک پیام نمونه.');
        $state->clear();
        $this->safeSend($chatId, '✅ قالب جدید ذخیره شد. می‌توانید بخش‌ها را ثابت یا متغیر کنید.');
        $this->showTemplate($chatId, $userId, null, $templateId);
    }

    private function showTemplateChooser(int $chatId, int $userId, ?int $messageId, int $draftId): void
    {
        $draft = $this->drafts->find($draftId);
        if ($draft === null) {
            $this->showHome($chatId, $userId, $messageId);
            return;
        }
        $content = (array) $draft['content'];
        $text = "📥 <b>پیام دریافت شد</b>\n\n"
            . 'عنوان تشخیص‌داده‌شده: <b>' . Html::escape(Str::truncate(Html::toPlain((string) ($content['title'] ?? '—')), 60)) . "</b>\n"
            . 'بخش‌های شناسایی‌شده: ' . Html::escape($this->detectedParts($content)) . "\n\n"
            . 'قالب موردنظر را انتخاب کنید:';

        $kb = Keyboard::make();
        foreach ($this->templates->all() as $template) {
            $kb->button(
                ($template['is_default'] === 1 ? '⭐️ ' : '') . Str::truncate((string) $template['name'], 40),
                'd|settpl|' . $draftId . '|' . $template['id']
            );
        }
        $kb->row([['💾 ذخیره به‌عنوان قالب جدید', 'd|astpl|' . $draftId]]);
        $kb->row([['❌ لغو', 'd|del|' . $draftId], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    /** @param array<string,mixed> $content */
    private function detectedParts(array $content): string
    {
        $map = [
            'title' => 'عنوان', 'intro' => 'مقدمه', 'body' => 'بدنه',
            'features' => 'فهرست قابلیت‌ها', 'summary' => 'جمع‌بندی', 'slogan' => 'شعار',
        ];
        $found = [];
        foreach ($map as $key => $label) {
            if (trim((string) ($content[$key] ?? '')) !== '') {
                $found[] = $label;
            }
        }
        if (!empty($content['footer'])) {
            $found[] = 'فوتر';
        }
        return $found === [] ? 'موردی تشخیص داده نشد (متن کامل حفظ شد)' : implode('، ', $found);
    }

    // --------------------------------------------------------------- drafts

    /** @param string[] $parts */
    private function onDraftCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'list';
        $id     = (int) ($parts[2] ?? 0);

        switch ($action) {
            case 'list':
                $this->showDraftList($chatId, $userId, $messageId, (int) ($parts[2] ?? 0));
                return;
            case 'v':
                $state->clear();
                $this->showDraft($chatId, $userId, $messageId, $id);
                return;
            case 'settpl':
                $this->drafts->setTemplate($id, (int) ($parts[3] ?? 0));
                $state->clear();
                $this->showDraft($chatId, $userId, $messageId, $id);
                return;
            case 'tpl':
                $this->showTemplateChooser($chatId, $userId, $messageId, $id);
                return;
            case 'astpl':
                $state->set('tpl.new_name', ['draft' => $id]);
                $this->safeSend($chatId, '🏷 یک نام برای قالب جدید بفرستید:');
                return;
            case 'et':
                $state->set('draft.edit_text', ['draft' => $id]);
                $this->safeSend($chatId, '✏️ متن جدید بدنه را بفرستید. (قالب‌بندی و ایموجی حفظ می‌شود)');
                return;
            case 'eti':
                $state->set('draft.edit_title', ['draft' => $id]);
                $this->safeSend($chatId, '📝 عنوان جدید را بفرستید:');
                return;
            case 'em':
                $state->set('draft.edit_emoji', ['draft' => $id]);
                $this->safeSend($chatId, '😀 ایموجی عنوان را بفرستید. برای حذف، یک خط تیره (-) بفرستید.');
                return;
            case 'sec':
                $this->showDraftSections($chatId, $userId, $messageId, $id);
                return;
            case 'secedit':
                $key   = (string) ($parts[3] ?? 'body');
                $label = $this->partLabel($key);
                $state->set('draft.edit_section', ['draft' => $id, 'key' => $key, 'label' => $label]);
                $this->safeSend($chatId, '✏️ محتوای جدید بخش «' . $label . '» را بفرستید. برای خالی کردن، یک خط تیره (-) بفرستید.');
                return;
            case 'num':
                $this->showDraftNumbering($chatId, $userId, $messageId, $id);
                return;
            case 'nummode':
                $mode = (string) ($parts[3] ?? PostNumbering::MODE_AUTO);
                if ($mode === PostNumbering::MODE_MANUAL) {
                    $state->set('draft.set_number', ['draft' => $id]);
                    $this->safeSend($chatId, '🔢 شماره دلخواه این پست را بفرستید (فقط عدد):');
                    return;
                }
                $this->drafts->setNumber($id, null, $mode);
                $this->showDraftNumbering($chatId, $userId, $messageId, $id);
                return;
            case 'links':
                $this->showLinkList($chatId, $userId, $messageId, $id);
                return;
            case 'media':
                $state->set('draft.await_photo', ['draft' => $id]);
                $this->showDraftMedia($chatId, $userId, $messageId, $id);
                return;
            case 'nomedia':
                $this->drafts->setMedia($id, '', '', 'above');
                $state->clear();
                $this->api->answerCallbackQuery($queryId, '🗑 عکس حذف شد.');
                $this->showDraft($chatId, $userId, $messageId, $id);
                return;
            case 'copy':
                $draft = $this->drafts->find($id);
                if ($draft !== null) {
                    $plain = $this->composer->toPlain($this->composer->render($draft));
                    $this->safeSend($chatId, '📋 متن قابل کپی:');
                    $this->safeSend($chatId, '<pre>' . Html::escape($plain) . '</pre>');
                }
                return;
            case 'save':
                $draft = $this->drafts->find($id);
                if ($draft !== null) {
                    $this->drafts->setRendered($id, $this->composer->render($draft));
                }
                $this->api->answerCallbackQuery($queryId, '💾 پیش‌نویس ذخیره شد.');
                $this->showDraft($chatId, $userId, $messageId, $id);
                return;
            case 'undo':
                $ok = $this->drafts->restoreLatestRevision($id);
                $this->api->answerCallbackQuery($queryId, $ok ? '↩️ به نسخه قبلی برگشت.' : 'نسخه قبلی موجود نیست.', !$ok);
                $this->showDraft($chatId, $userId, $messageId, $id);
                return;
            case 'del':
                $this->screen($chatId, $userId, $messageId, '❗️ حذف این پیش‌نویس قطعی است. مطمئن هستید؟', Keyboard::make()
                    ->row([['🗑 بله، حذف کن', 'd|delok|' . $id], ['↩️ انصراف', 'd|v|' . $id]]));
                return;
            case 'delok':
                $this->drafts->delete($id);
                $state->clear();
                $this->showDraftList($chatId, $userId, $messageId, 0);
                return;
            case 'search':
                $state->set('draft.search', []);
                $this->safeSend($chatId, '🔍 عبارت جست‌وجو را بفرستید:');
                return;
            case 'pub':
                $this->confirmPublish($chatId, $userId, $messageId, $id);
                return;
            case 'pubok':
                $this->doPublish($chatId, $userId, $messageId, $id);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function partLabel(string $key): string
    {
        return [
            'title' => 'عنوان', 'title_emoji' => 'ایموجی عنوان', 'intro' => 'مقدمه',
            'body' => 'بدنه اصلی', 'features' => 'فهرست قابلیت‌ها',
            'summary' => 'جمع‌بندی', 'slogan' => 'شعار پایانی',
        ][$key] ?? $key;
    }

    private function showDraftList(int $chatId, int $userId, ?int $messageId, int $page): void
    {
        $perPage = 8;
        $items   = $this->drafts->listByStatus(DraftRepository::STATUS_DRAFT, $perPage + 1, $page * $perPage);
        $hasMore = count($items) > $perPage;
        $items   = array_slice($items, 0, $perPage);

        $kb = Keyboard::make();
        foreach ($items as $draft) {
            $title = Html::toPlain((string) ($draft['content']['title'] ?? '')) ?: Html::toPlain((string) $draft['source_text']);
            $kb->button('📄 #' . $draft['id'] . ' ' . Str::truncate(trim($title), 34), 'd|v|' . $draft['id']);
        }
        $nav = [];
        if ($page > 0) {
            $nav[] = ['⬅️ قبلی', 'd|list|' . ($page - 1)];
        }
        if ($hasMore) {
            $nav[] = ['بعدی ➡️', 'd|list|' . ($page + 1)];
        }
        if ($nav !== []) {
            $kb->row($nav);
        }
        $kb->row([['🔍 جست‌وجو', 'd|search|0'], ['🏠 خانه', 'h']]);

        $count = $this->drafts->countByStatus(DraftRepository::STATUS_DRAFT);
        $text  = "📂 <b>پیش‌نویس‌ها</b>\n\nتعداد: " . Str::toPersianDigits((string) $count)
            . ($count === 0 ? "\n\nهنوز پیش‌نویسی ندارید. یک متن بفرستید تا ساخته شود." : '');
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showDraftSearchResults(int $chatId, int $userId, ?int $messageId, string $term): void
    {
        $items = $this->drafts->search($term, 10);
        $kb    = Keyboard::make();
        foreach ($items as $draft) {
            $title = Html::toPlain((string) ($draft['content']['title'] ?? '')) ?: Html::toPlain((string) $draft['source_text']);
            $kb->button('📄 #' . $draft['id'] . ' ' . Str::truncate(trim($title), 34), 'd|v|' . $draft['id']);
        }
        $kb->row([['📂 همه پیش‌نویس‌ها', 'd|list|0'], ['🏠 خانه', 'h']]);
        $this->screen(
            $chatId,
            $userId,
            $messageId,
            '🔍 نتایج جست‌وجو برای «' . Html::escape($term) . '»: ' . Str::toPersianDigits((string) count($items)) . ' مورد',
            $kb
        );
    }

    private function showDraft(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $draft = $this->drafts->find($id);
        if ($draft === null) {
            $this->showDraftList($chatId, $userId, $messageId, 0);
            return;
        }
        $rendered = $this->composer->render($draft);
        $this->drafts->setRendered($id, $rendered);
        $warning = $this->composer->validate($rendered, (string) $draft['media_file_id'] !== '');
        $number  = $this->numbering->preview($draft);
        $template = $draft['template_id'] !== null ? $this->templates->find((int) $draft['template_id']) : null;

        $header = '📄 <b>پیش‌نویس #' . $id . "</b>\n"
            . '🎨 قالب: ' . Html::escape((string) ($template['name'] ?? 'پیش‌فرض')) . "\n"
            . '🔢 شماره: ' . ($number !== null ? Str::toPersianDigits((string) $number) : 'بدون شماره')
            . "\n🖼 عکس: " . ((string) $draft['media_file_id'] !== '' ? 'دارد (متن به‌صورت کپشن ارسال می‌شود)' : 'ندارد')
            . ($draft['status'] === DraftRepository::STATUS_UNKNOWN ? "\n⚠️ وضعیت آخرین انتشار نامشخص است." : '')
            . ($warning !== null ? "\n⚠️ " . Html::escape($warning) : '')
            . "\n\n— — — پیش‌نمایش — — —\n\n";

        $kb = Keyboard::make()
            ->row([['✅ تأیید و انتشار', 'd|pub|' . $id]])
            ->row([['✏️ ویرایش متن', 'd|et|' . $id], ['📝 ویرایش عنوان', 'd|eti|' . $id]])
            ->row([['😀 تغییر ایموجی', 'd|em|' . $id], ['🎨 تغییر قالب', 'd|tpl|' . $id]])
            ->row([['🔢 تغییر شماره', 'd|num|' . $id], ['🔗 ویرایش لینک‌ها', 'd|links|' . $id]])
            ->row([['🧩 ویرایش بخش‌ها', 'd|sec|' . $id], ['🖼 عکس پست', 'd|media|' . $id]])
            ->row([['📋 متن قابل کپی', 'd|copy|' . $id], ['❝ نقل‌قول قالب', 'tpl|v|' . (int) ($draft['template_id'] ?? 0)]])
            ->row([['💾 ذخیره پیش‌نویس', 'd|save|' . $id], ['↩️ بازگشت نسخه قبلی', 'd|undo|' . $id]])
            ->row([['💾 ذخیره به‌عنوان قالب', 'd|astpl|' . $id]])
            ->row([['❌ حذف', 'd|del|' . $id], ['📂 پیش‌نویس‌ها', 'd|list|0'], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $header . $rendered, $kb);
    }

    private function showDraftSections(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $draft = $this->drafts->find($id);
        if ($draft === null) {
            $this->showDraftList($chatId, $userId, $messageId, 0);
            return;
        }
        $content = (array) $draft['content'];
        $lines   = [];
        $kb      = Keyboard::make();
        foreach (['title', 'title_emoji', 'intro', 'body', 'features', 'summary', 'slogan'] as $key) {
            $value   = trim(Html::toPlain((string) ($content[$key] ?? '')));
            $lines[] = '• <b>' . $this->partLabel($key) . '</b>: ' . ($value === '' ? '—' : Html::escape(Str::truncate($value, 60)));
            $kb->button('✏️ ' . $this->partLabel($key), 'd|secedit|' . $id . '|' . $key);
        }
        $kb->row([['↩️ بازگشت به پیش‌نویس', 'd|v|' . $id], ['🏠 خانه', 'h']]);

        $text = "🧩 <b>بخش‌های پیش‌نویس #" . $id . "</b>\n\n" . implode("\n", $lines)
            . "\n\nمتن اصلی همیشه محفوظ است و با «↩️ بازگشت نسخه قبلی» قابل بازیابی است.";
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function updateDraftPart(int $chatId, int $userId, State $state, int $draftId, string $key, string $value, string $label): void
    {
        $draft = $this->drafts->find($draftId);
        if ($draft === null) {
            $state->clear();
            $this->safeSend($chatId, 'پیش‌نویس پیدا نشد.');
            return;
        }
        $content       = (array) $draft['content'];
        $content[$key] = trim($value) === '-' ? '' : $value;
        $this->drafts->updateContent($draftId, $content, 'ویرایش ' . $label);
        $state->clear();
        $this->safeSend($chatId, '✅ ' . $label . ' به‌روزرسانی شد.');
        $this->showDraft($chatId, $userId, null, $draftId);
    }

    private function showDraftNumbering(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $draft = $this->drafts->find($id);
        if ($draft === null) {
            $this->showDraftList($chatId, $userId, $messageId, 0);
            return;
        }
        $mode   = (string) $draft['numbering_mode'];
        $labels = [
            PostNumbering::MODE_AUTO   => 'خودکار (شماره بعدی هنگام انتشار)',
            PostNumbering::MODE_MANUAL => 'دستی (' . Str::toPersianDigits((string) ($draft['post_number'] ?? '—')) . ')',
            PostNumbering::MODE_NONE   => 'بدون شماره',
        ];
        $text = "🔢 <b>شماره‌گذاری پیش‌نویس #" . $id . "</b>\n\n"
            . 'حالت فعلی: <b>' . Html::escape($labels[$mode] ?? $mode) . "</b>\n"
            . 'شماره بعدی سراسری: <b>' . Str::toPersianDigits((string) $this->numbering->next()) . "</b>\n\n"
            . 'شماره قطعی فقط هنگام انتشار تخصیص داده می‌شود.';

        $kb = Keyboard::make()
            ->button('🔁 خودکار', 'd|nummode|' . $id . '|' . PostNumbering::MODE_AUTO)
            ->button('✍️ دستی', 'd|nummode|' . $id . '|' . PostNumbering::MODE_MANUAL)
            ->button('🚫 بدون شماره', 'd|nummode|' . $id . '|' . PostNumbering::MODE_NONE)
            ->row([['↩️ بازگشت', 'd|v|' . $id], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function applyManualNumber(int $chatId, int $userId, State $state, int $draftId, string $value): void
    {
        $number = (int) Str::toEnglishDigits($value);
        if ($number <= 0) {
            $this->safeSend($chatId, '⚠️ لطفاً یک عدد معتبر بفرستید.');
            return;
        }
        if ($this->numbering->isUsed($number)) {
            $this->safeSend($chatId, '⚠️ شماره ' . Str::toPersianDigits((string) $number) . ' قبلاً استفاده شده است. عدد دیگری بفرستید.');
            return;
        }
        $this->drafts->setNumber($draftId, $number, PostNumbering::MODE_MANUAL);
        $state->clear();
        $this->safeSend($chatId, '✅ شماره پست روی ' . Str::toPersianDigits((string) $number) . ' تنظیم شد.');
        $this->showDraft($chatId, $userId, null, $draftId);
    }

    // ------------------------------------------------------------ publishing

    private function confirmPublish(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $draft = $this->drafts->find($id);
        if ($draft === null) {
            $this->showDraftList($chatId, $userId, $messageId, 0);
            return;
        }
        $check    = $this->publisher->checkChannel();
        $number   = $this->numbering->preview($draft);
        $rendered = $this->composer->render($draft, $number);
        $error    = $this->composer->validate($rendered);

        $text = "📢 <b>تأیید انتشار</b>\n\n"
            . 'کانال: <code>' . Html::escape(Settings::get('channel', '@Ghajarvpn')) . "</code>\n"
            . 'شماره پست: <b>' . ($number !== null ? Str::toPersianDigits((string) $number) : 'بدون شماره') . "</b>\n"
            . 'وضعیت دسترسی: ' . ($check['ok'] ? '✅ ' : '⚠️ ') . Html::escape($check['message']) . "\n"
            . ($error !== null ? '⚠️ ' . Html::escape($error) . "\n" : '')
            . "\n— — — متن نهایی — — —\n\n" . $rendered;

        $kb = Keyboard::make();
        if ($check['ok'] && $error === null) {
            $kb->button('✅ بله، منتشر کن', 'd|pubok|' . $id);
        } else {
            $text .= "\n\n" . $this->adminGuide();
            $kb->button('🔄 بررسی دوباره دسترسی', 'd|pub|' . $id);
        }
        $kb->row([['↩️ بازگشت', 'd|v|' . $id], ['🏠 خانه', 'h']]);

        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function adminGuide(): string
    {
        return "ℹ️ <b>راهنمای ادمین کردن ربات:</b>\n"
            . "۱) وارد کانال شوید → Manage Channel → Administrators\n"
            . "۲) Add Admin را بزنید و نام ربات را جست‌وجو کنید.\n"
            . "۳) مجوز <b>Post Messages</b> را فعال کنید و ذخیره کنید.\n"
            . '۴) سپس دکمه «🔄 بررسی دوباره دسترسی» را بزنید.';
    }

    private function doPublish(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $result = $this->publisher->publish($id);
        if (!$result['ok']) {
            $this->screen($chatId, $userId, $messageId, '⚠️ ' . $result['message'], Keyboard::make()
                ->row([['↩️ بازگشت به پیش‌نویس', 'd|v|' . $id], ['🏠 خانه', 'h']]));
            return;
        }
        $text = "✅ <b>پست با موفقیت منتشر شد.</b>\n\n"
            . '🔢 شماره پست: <b>' . ($result['number'] !== null ? Str::toPersianDigits((string) $result['number']) : '—') . "</b>\n"
            . '🆔 شناسه پیام: <code>' . (int) $result['message_id'] . '</code>'
            . (($result['url'] ?? '') !== '' ? "\n🔗 لینک پیام: " . Html::escape((string) $result['url']) : '')
            . (($result['emoji_fallback'] ?? false)
                ? "\n\nℹ️ تلگرام ایموجی پریمیوم این ربات را نپذیرفت، بنابراین ایموجی معمولی جایگزین شد."
                : '');

        $kb = Keyboard::make();
        if (($result['url'] ?? '') !== '') {
            $kb->url('👁 مشاهده پست', (string) $result['url']);
        }
        $kb->row([['📢 پست‌های منتشرشده', 'p|list|0'], ['🏠 خانه', 'h']]);
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    /** @param string[] $parts */
    private function onPublishedCallback(int $chatId, int $userId, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'list';
        $id     = (int) ($parts[2] ?? 0);

        switch ($action) {
            case 'list':
                $this->showPublishedList($chatId, $userId, $messageId, (int) ($parts[2] ?? 0));
                return;
            case 'v':
                $this->showPublished($chatId, $userId, $messageId, $id);
                return;
            case 'del':
                $this->screen($chatId, $userId, $messageId, '❗️ پست از کانال حذف شود؟', Keyboard::make()
                    ->row([['🗑 بله', 'p|delok|' . $id], ['↩️ انصراف', 'p|v|' . $id]]));
                return;
            case 'delok':
                $result = $this->publisher->deletePublished($id);
                $this->api->answerCallbackQuery($queryId, $result['message'], !$result['ok']);
                $this->showPublishedList($chatId, $userId, $messageId, 0);
                return;
            case 'sync':
                $this->syncPublished($chatId, $userId, $messageId, $id, $queryId);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showPublishedList(int $chatId, int $userId, ?int $messageId, int $page): void
    {
        $perPage = 8;
        $items   = $this->publisher->history($perPage + 1, $page * $perPage);
        $hasMore = count($items) > $perPage;
        $items   = array_slice($items, 0, $perPage);

        $kb = Keyboard::make();
        foreach ($items as $post) {
            $label = ($post['deleted_at'] !== null ? '🗑 ' : '📢 ')
                . 'پست ' . Str::toPersianDigits((string) ($post['post_number'] ?? '—'))
                . ' — ' . Str::truncate(trim(Html::toPlain((string) $post['text'])), 28);
            $kb->button($label, 'p|v|' . $post['id']);
        }
        $nav = [];
        if ($page > 0) {
            $nav[] = ['⬅️ قبلی', 'p|list|' . ($page - 1)];
        }
        if ($hasMore) {
            $nav[] = ['بعدی ➡️', 'p|list|' . ($page + 1)];
        }
        if ($nav !== []) {
            $kb->row($nav);
        }
        $kb->row([['✏️ ویرایش پیام با لینک', 'edit|start']]);
        $kb->button('🏠 خانه', 'h');

        $this->screen(
            $chatId,
            $userId,
            $messageId,
            "📢 <b>پست‌های منتشرشده</b>\n\nبا «✏️ ویرایش پیام با لینک» می‌توانید هر پیام ربات در کانال را با متن یا عکس تازه جایگزین کنید.",
            $kb
        );
    }

    private function showPublished(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $stmt = Database::connect()->prepare('SELECT * FROM published_posts WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $post = $stmt->fetch();
        if ($post === false) {
            $this->showPublishedList($chatId, $userId, $messageId, 0);
            return;
        }
        $text = '📢 <b>پست ' . Str::toPersianDigits((string) ($post['post_number'] ?? '—')) . "</b>\n"
            . '🗓 ' . Html::escape((string) $post['published_at']) . "\n"
            . '🆔 <code>' . (int) $post['message_id'] . '</code>'
            . ($post['deleted_at'] !== null ? "\n🗑 این پست از کانال حذف شده است." : '')
            . "\n\n" . (string) $post['text'];

        $kb = Keyboard::make();
        if ((string) $post['message_url'] !== '') {
            $kb->url('👁 مشاهده در کانال', (string) $post['message_url']);
        }
        if ($post['deleted_at'] === null) {
            $kb->row([['🔄 هم‌گام‌سازی متن با پیش‌نویس', 'p|sync|' . $id], ['🗑 حذف از کانال', 'p|del|' . $id]]);
        }
        $kb->row([['↩️ فهرست', 'p|list|0'], ['🏠 خانه', 'h']]);
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function syncPublished(int $chatId, int $userId, ?int $messageId, int $id, string $queryId): void
    {
        $stmt = Database::connect()->prepare('SELECT * FROM published_posts WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $post = $stmt->fetch();
        if ($post === false || $post['draft_id'] === null) {
            $this->api->answerCallbackQuery($queryId, 'پیش‌نویس مرتبط پیدا نشد.', true);
            return;
        }
        $draft = $this->drafts->find((int) $post['draft_id']);
        if ($draft === null) {
            $this->api->answerCallbackQuery($queryId, 'پیش‌نویس مرتبط حذف شده است.', true);
            return;
        }
        $result = $this->publisher->editPublished($id, $this->composer->render($draft, $draft['post_number']));
        $this->api->answerCallbackQuery($queryId, $result['message'], !$result['ok']);
        $this->showPublished($chatId, $userId, $messageId, $id);
    }

    // ---------------------------------------------------------------- links

    /** @param string[] $parts */
    private function onLinkCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'list';
        $id     = (int) ($parts[2] ?? 0);

        switch ($action) {
            case 'list':
                $this->showLinkList($chatId, $userId, $messageId, (int) ($parts[2] ?? 0));
                return;
            case 'e':
                $link = $this->links->find($id);
                if ($link === null) {
                    $this->showLinkList($chatId, $userId, $messageId, 0);
                    return;
                }
                $state->set('link.edit', ['link' => $id]);
                $this->safeSend($chatId, '🔗 مقدار جدید برای «' . Html::escape((string) $link['label']) . '» را بفرستید:');
                return;
            case 'add':
                $state->set('link.add_label', []);
                $this->safeSend($chatId, '🏷 عنوان لینک جدید را بفرستید (مثلاً: کانال پشتیبانی)');
                return;
            case 'anchor':
                $kb = Keyboard::make();
                foreach ($this->links->all() as $link) {
                    $kb->button('✍️ ' . Str::truncate((string) $link['label'], 28), 'l|setanchor|' . $link['id']);
                }
                $kb->row([['↩️ بازگشت', 'l|list'], ['🏠 خانه', 'h']]);
                $this->screen($chatId, $userId, $messageId, "✍️ <b>متن نمایشی لینک‌ها</b>\n\n"
                    . 'این متن همان چیزی است که در پست دیده می‌شود و آدرس پشت آن پنهان می‌ماند '
                    . '(در قالب‌ها با متغیرهایی مثل <code>{apk_link}</code>).', $kb);
                return;
            case 'setanchor':
                $link = $this->links->find($id);
                if ($link === null) {
                    $this->showLinkList($chatId, $userId, $messageId, 0);
                    return;
                }
                $state->set('link.anchor', ['link' => $id]);
                $this->safeSend($chatId, '✍️ متن نمایشی تازه برای «' . Html::escape((string) $link['label'])
                    . '» را بفرستید. مثال: <code>دانلود مستقیم APK</code>');
                return;
            case 'del':
                $ok = $this->links->delete($id);
                $this->api->answerCallbackQuery($queryId, $ok ? 'حذف شد.' : 'لینک‌های پایه قابل حذف نیستند.', !$ok);
                $this->showLinkList($chatId, $userId, $messageId, 0);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showLinkList(int $chatId, int $userId, ?int $messageId, int $draftId = 0): void
    {
        $lines = [];
        $kb    = Keyboard::make();
        foreach ($this->links->all() as $link) {
            $anchor  = trim((string) ($link['anchor'] ?? '')) !== '' ? (string) $link['anchor'] : (string) $link['label'];
            $lines[] = '• <b>' . Html::escape((string) $link['label']) . '</b> — نمایش: «'
                . Html::escape($anchor) . '»' . "\n   "
                . ((string) $link['value'] !== '' ? '<code>' . Html::escape(Str::truncate((string) $link['value'], 60)) . '</code>' : '—');
            $kb->row([
                ['✏️ ' . Str::truncate((string) $link['label'], 24), 'l|e|' . $link['id']],
                [(int) $link['is_builtin'] === 1 ? '🔒' : '🗑', (int) $link['is_builtin'] === 1 ? 'x' : 'l|del|' . $link['id']],
            ]);
        }
        $kb->button('✍️ تغییر متن نمایشی لینک‌ها', 'l|anchor');
        $kb->button('➕ افزودن لینک دلخواه', 'l|add');
        if ($draftId > 0) {
            $kb->row([['↩️ بازگشت به پیش‌نویس', 'd|v|' . $draftId], ['🏠 خانه', 'h']]);
        } else {
            $kb->button('🏠 خانه', 'h');
        }

        $text = "🔗 <b>مدیریت لینک‌ها</b>\n\n" . implode("\n", $lines)
            . "\n\nدر قالب‌ها:\n"
            . "• <code>{apk_link}</code> → لینک روی متن (آدرس دیده نمی‌شود)\n"
            . "• <code>{apk_url}</code> → آدرس خام\n"
            . '• <code>{apk_text}</code> → فقط متن نمایشی';
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function applyLinkValue(int $chatId, int $userId, State $state, int $linkId, string $value): void
    {
        $link = $this->links->find($linkId);
        if ($link === null) {
            $state->clear();
            $this->safeSend($chatId, 'لینک پیدا نشد.');
            return;
        }
        if (!LinkRepository::isValidUrl($value)) {
            $this->safeSend($chatId, '⚠️ مقدار نامعتبر است. یک آدرس با http/https یا یک آیدی مثل @Ghajarvpn بفرستید.');
            return;
        }
        $this->links->set((string) $link['key'], $value);
        $state->clear();
        $this->safeSend($chatId, '✅ لینک به‌روزرسانی شد.');
        $this->showLinkList($chatId, $userId, null);
    }

    private function addCustomLink(int $chatId, int $userId, State $state, string $label, string $value): void
    {
        if (!LinkRepository::isValidUrl($value)) {
            $this->safeSend($chatId, '⚠️ آدرس نامعتبر است. دوباره بفرستید.');
            return;
        }
        $this->links->set('custom_' . bin2hex(random_bytes(3)), $value, $label);
        $state->clear();
        $this->safeSend($chatId, '✅ لینک جدید اضافه شد.');
        $this->showLinkList($chatId, $userId, null);
    }

    // -------------------------------------------------------------- version

    /** @param string[] $parts */
    private function onVersionCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'home';

        switch ($action) {
            case 'home':
                $this->showVersionHome($chatId, $userId, $messageId);
                return;
            case 'check':
                $this->checkVersions($chatId, $userId, $state, $messageId);
                return;
            case 'rel':
                $this->showRelease($chatId, $userId, $state, $messageId, (int) ($parts[2] ?? 0));
                return;
            case 'apk':
                $this->applyApk($chatId, $userId, $state, $messageId, (int) ($parts[2] ?? 0), (int) ($parts[3] ?? 0), $queryId);
                return;
            case 'setrel':
                $this->applyReleaseLink($chatId, $userId, $state, $messageId, (int) ($parts[2] ?? 0), $queryId);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showVersionHome(int $chatId, int $userId, ?int $messageId): void
    {
        $text = "📦 <b>مدیریت نسخه APK</b>\n\n"
            . 'مخزن: <code>' . Html::escape(Settings::get('github_repo', 'meysam82003/Ghajarvpn-')) . "</code>\n"
            . 'نسخه ثبت‌شده: <b>' . Html::escape(Settings::get('current_version', '—')) . "</b>\n"
            . '📥 لینک APK فعلی: <code>' . Html::escape(Str::truncate($this->links->get('apk_url'), 70)) . "</code>\n"
            . '🔗 لینک ریلیز فعلی: <code>' . Html::escape(Str::truncate($this->links->get('release_url'), 70)) . '</code>';

        $kb = Keyboard::make()
            ->button('🔄 بررسی نسخه جدید', 'v|check')
            ->row([['🔗 مدیریت لینک‌ها', 'l|list'], ['🏠 خانه', 'h']]);
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function checkVersions(int $chatId, int $userId, State $state, ?int $messageId): void
    {
        $client = new GithubClient();
        try {
            $releases = $client->releases(10);
        } catch (GithubException $e) {
            $this->screen($chatId, $userId, $messageId, '⚠️ ' . Html::escape($e->getMessage())
                . "\n\nلینک‌های قبلی بدون تغییر باقی ماندند.", Keyboard::make()
                ->row([['🔄 تلاش دوباره', 'v|check'], ['↩️ بازگشت', 'v|home']]));
            return;
        }
        if ($releases === []) {
            $this->screen($chatId, $userId, $messageId, 'ℹ️ هیچ ریلیزی در مخزن پیدا نشد.', Keyboard::make()
                ->row([['↩️ بازگشت', 'v|home']]));
            return;
        }

        $slim = [];
        foreach ($releases as $release) {
            $client->cache($release);
            $slim[] = $release;
        }
        $payload = $state->payload();
        $payload['releases'] = $slim;
        $state->set($state->current(), $payload);

        $current = Settings::get('current_version');
        $lines   = [];
        $kb      = Keyboard::make();
        foreach ($slim as $i => $release) {
            $flag = $release['is_draft'] ? '📝 Draft' : ($release['is_prerelease'] ? '🧪 Prerelease' : '✅ Stable');
            $mark = $release['tag'] === $current ? ' (نسخه فعلی)' : '';
            $lines[] = '• <b>' . Html::escape($release['tag']) . '</b> — ' . $flag
                . ' — ' . Html::escape(substr($release['published_at'], 0, 10)) . $mark;
            $kb->button(($release['is_draft'] || $release['is_prerelease'] ? '⚠️ ' : '') . $release['tag'], 'v|rel|' . $i);
        }
        $kb->row([['↩️ بازگشت', 'v|home'], ['🏠 خانه', 'h']]);

        $newest = $slim[0]['tag'] ?? '';
        $header = $newest !== '' && $newest !== $current
            ? "🆕 <b>نسخه جدید موجود است: " . Html::escape($newest) . "</b>\n\n"
            : "ℹ️ نسخه‌های مخزن:\n\n";

        $this->screen($chatId, $userId, $messageId, $header . implode("\n", $lines)
            . "\n\nنسخه‌های Draft/Prerelease با ⚠️ مشخص شده‌اند و بدون تأیید شما جایگزین نمی‌شوند.", $kb);
    }

    private function showRelease(int $chatId, int $userId, State $state, ?int $messageId, int $index): void
    {
        $releases = (array) ($state->payload()['releases'] ?? []);
        $release  = $releases[$index] ?? null;
        if (!is_array($release)) {
            $this->checkVersions($chatId, $userId, $state, $messageId);
            return;
        }
        $lines = [
            '📦 <b>' . Html::escape((string) $release['tag']) . '</b>',
            '🗓 تاریخ انتشار: ' . Html::escape(substr((string) $release['published_at'], 0, 10)),
            '📄 وضعیت: ' . ($release['is_draft'] ? 'Draft' : ($release['is_prerelease'] ? 'Prerelease' : 'Stable')),
            '',
        ];
        $kb = Keyboard::make();
        $apkCount = 0;
        foreach ((array) $release['assets'] as $i => $asset) {
            if (!($asset['is_apk'] ?? false)) {
                continue;
            }
            $apkCount++;
            $lines[] = '• ' . Html::escape((string) $asset['name'])
                . ($asset['arch'] !== '' ? ' — معماری: ' . Html::escape((string) $asset['arch']) : '')
                . ' — ' . Str::toPersianDigits((string) round(((int) $asset['size']) / 1048576, 1)) . ' مگابایت';
            $kb->button('📥 این فایل، لینک دانلود پیش‌فرض شود: ' . Str::truncate((string) $asset['name'], 24), 'v|apk|' . $index . '|' . $i);
        }
        if ($apkCount === 0) {
            $lines[] = 'فایل APK عمومی در این ریلیز پیدا نشد.';
        }
        $kb->button('🔗 ثبت لینک ریلیز این نسخه', 'v|setrel|' . $index);
        $kb->row([['↩️ فهرست نسخه‌ها', 'v|check'], ['🏠 خانه', 'h']]);

        if ($release['is_draft'] || $release['is_prerelease']) {
            $lines[] = '';
            $lines[] = '⚠️ این نسخه پایدار نیست. در صورت تأیید شما جایگزین می‌شود.';
        }
        $this->screen($chatId, $userId, $messageId, implode("\n", $lines), $kb);
    }

    private function applyApk(int $chatId, int $userId, State $state, ?int $messageId, int $releaseIndex, int $assetIndex, string $queryId): void
    {
        $release = (array) ($state->payload()['releases'][$releaseIndex] ?? []);
        $asset   = (array) ($release['assets'][$assetIndex] ?? []);
        $url     = (string) ($asset['download_url'] ?? '');
        if ($url === '') {
            $this->api->answerCallbackQuery($queryId, 'لینک این فایل در دسترس نیست.', true);
            return;
        }
        $this->links->set('apk_url', $url);
        Settings::set('current_version', (string) ($release['tag'] ?? ''));
        $this->api->answerCallbackQuery($queryId, '✅ لینک دانلود به‌روزرسانی شد.');
        $this->showRelease($chatId, $userId, $state, $messageId, $releaseIndex);
    }

    private function applyReleaseLink(int $chatId, int $userId, State $state, ?int $messageId, int $releaseIndex, string $queryId): void
    {
        $release = (array) ($state->payload()['releases'][$releaseIndex] ?? []);
        $url     = (string) ($release['html_url'] ?? '');
        if ($url === '') {
            $this->api->answerCallbackQuery($queryId, 'لینک ریلیز در دسترس نیست.', true);
            return;
        }
        $this->links->set('release_url', $url);
        Settings::set('current_version', (string) ($release['tag'] ?? ''));
        $this->api->answerCallbackQuery($queryId, '✅ لینک ریلیز ثبت شد.');
        $this->showRelease($chatId, $userId, $state, $messageId, $releaseIndex);
    }

    // ------------------------------------------------------------ numbering

    /** @param string[] $parts */
    private function onNumberingCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'home';
        switch ($action) {
            case 'home':
                $this->showNumberingHome($chatId, $userId, $messageId);
                return;
            case 'set':
                $state->set('numbering.set_next', []);
                $this->safeSend($chatId, '🔢 شماره پست بعدی را بفرستید (فقط عدد):');
                return;
            case 'toggle':
                $this->numbering->setEnabled(!$this->numbering->enabled());
                $this->showNumberingHome($chatId, $userId, $messageId);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showNumberingHome(int $chatId, int $userId, ?int $messageId): void
    {
        $lastNum = Database::connect()->query('SELECT MAX(post_number) FROM published_posts')->fetchColumn();
        $text = "🔢 <b>شماره‌گذاری پست‌ها</b>\n\n"
            . 'وضعیت: ' . ($this->numbering->enabled() ? '✅ فعال' : '⛔️ غیرفعال') . "\n"
            . 'شماره بعدی: <b>' . Str::toPersianDigits((string) $this->numbering->next()) . "</b>\n"
            . 'آخرین شماره منتشرشده: <b>' . Str::toPersianDigits((string) ($lastNum !== false && $lastNum !== null ? $lastNum : '—')) . "</b>\n\n"
            . 'شماره فقط هنگام انتشار موفق مصرف می‌شود؛ ساخت یا حذف پیش‌نویس شماره‌ای مصرف نمی‌کند.';

        $kb = Keyboard::make()
            ->row([['✍️ تغییر شماره بعدی', 'n|set'], [$this->numbering->enabled() ? '⛔️ غیرفعال کردن' : '✅ فعال کردن', 'n|toggle']])
            ->button('🏠 خانه', 'h');
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function applyNextNumber(int $chatId, int $userId, State $state, string $value): void
    {
        $number = (int) Str::toEnglishDigits($value);
        if ($number <= 0) {
            $this->safeSend($chatId, '⚠️ یک عدد معتبر بفرستید.');
            return;
        }
        $this->numbering->setNext($number);
        $state->clear();
        $this->safeSend($chatId, '✅ شماره بعدی روی ' . Str::toPersianDigits((string) $number) . ' تنظیم شد.');
        $this->showNumberingHome($chatId, $userId, null);
    }

    // ------------------------------------------------------------- settings

    /** @param string[] $parts */
    private function onSettingsCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'home';
        switch ($action) {
            case 'home':
                $this->showSettings($chatId, $userId, $messageId);
                return;
            case 'channel':
                $state->set('settings.channel', []);
                $this->safeSend($chatId, '📢 آیدی کانال را بفرستید (مثلاً <code>@Ghajarvpn</code>):');
                return;
            case 'repo':
                $state->set('settings.repo', []);
                $this->safeSend($chatId, '📦 مخزن گیت‌هاب را به شکل <code>owner/repo</code> بفرستید:');
                return;
            case 'check':
                $result = $this->publisher->checkChannel();
                $text   = ($result['ok'] ? '✅ ' : '⚠️ ') . Html::escape($result['message'])
                    . ($result['ok'] ? '' : "\n\n" . $this->adminGuide());
                $this->screen($chatId, $userId, $messageId, $text, Keyboard::make()
                    ->row([['🔄 بررسی دوباره', 's|check'], ['↩️ تنظیمات', 's|home']]));
                return;
            case 'webhook':
                $this->showWebhookStatus($chatId, $userId, $messageId);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showSettings(int $chatId, int $userId, ?int $messageId): void
    {
        $text = "⚙️ <b>تنظیمات</b>\n\n"
            . '👤 مدیر: <code>' . Settings::int('admin_id') . "</code>\n"
            . '📢 کانال: <code>' . Html::escape(Settings::get('channel', '@Ghajarvpn')) . "</code>\n"
            . '🤖 ربات: <code>@' . Html::escape(Settings::get('bot_username', '—')) . "</code>\n"
            . '📦 مخزن: <code>' . Html::escape(Settings::get('github_repo')) . '</code>';

        $kb = Keyboard::make()
            ->row([['📢 تغییر کانال', 's|channel'], ['📦 تغییر مخزن', 's|repo']])
            ->row([['🔍 بررسی دسترسی کانال', 's|check'], ['🌐 وضعیت Webhook', 's|webhook']])
            ->row([['🔢 شماره‌گذاری', 'n|home'], ['🔗 لینک‌ها', 'l|list']])
            ->row([['😎 ایموجی پریمیوم', 'em|list'], ['✏️ ویرایش پیام کانال', 'edit|start']])
            ->button('🏠 خانه', 'h');
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showWebhookStatus(int $chatId, int $userId, ?int $messageId): void
    {
        try {
            $info = $this->api->getWebhookInfo();
        } catch (ApiException $e) {
            $this->screen($chatId, $userId, $messageId, '⚠️ ' . Html::escape($e->friendly()), Keyboard::make()
                ->button('↩️ تنظیمات', 's|home'));
            return;
        }
        $text = "🌐 <b>وضعیت Webhook</b>\n\n"
            . 'آدرس ثبت‌شده: ' . ((string) ($info['url'] ?? '') !== '' ? '✅ فعال' : '⛔️ ثبت نشده') . "\n"
            . 'به‌روزرسانی‌های در صف: <b>' . Str::toPersianDigits((string) ($info['pending_update_count'] ?? 0)) . "</b>\n"
            . ((string) ($info['last_error_message'] ?? '') !== ''
                ? '⚠️ آخرین خطا: ' . Html::escape((string) $info['last_error_message'])
                : '✅ خطایی گزارش نشده است.');
        $this->screen($chatId, $userId, $messageId, $text, Keyboard::make()
            ->row([['🔄 بررسی دوباره', 's|webhook'], ['↩️ تنظیمات', 's|home']]));
    }

    private function applyChannel(int $chatId, int $userId, State $state, string $value): void
    {
        $value = trim($value);
        if (!preg_match('/^(@[A-Za-z0-9_]{4,32}|-100\d{6,})$/', $value)) {
            $this->safeSend($chatId, '⚠️ آیدی کانال معتبر نیست. مثل <code>@Ghajarvpn</code> یا <code>-1001234567890</code> بفرستید.');
            return;
        }
        Settings::set('channel', $value);
        if (str_starts_with($value, '@')) {
            $this->links->set('channel_link', 'https://t.me/' . substr($value, 1));
        }
        $state->clear();
        $this->safeSend($chatId, '✅ کانال ثبت شد.');
        $this->showSettings($chatId, $userId, null);
    }

    private function applyRepo(int $chatId, int $userId, State $state, string $value): void
    {
        $value = trim($value);
        $value = (string) preg_replace('#^https?://github\.com/#i', '', $value);
        $value = rtrim($value, '/');
        if (!preg_match('#^[\w.\-]+/[\w.\-]+$#', $value)) {
            $this->safeSend($chatId, '⚠️ قالب صحیح: <code>owner/repo</code>');
            return;
        }
        Settings::set('github_repo', $value);
        $this->links->set('repo_url', 'https://github.com/' . $value);
        $state->clear();
        $this->safeSend($chatId, '✅ مخزن ثبت شد.');
        $this->showSettings($chatId, $userId, null);
    }

    // ---------------------------------------------------------------- media

    private function showDraftMedia(int $chatId, int $userId, ?int $messageId, int $id): void
    {
        $draft = $this->drafts->find($id);
        if ($draft === null) {
            $this->showDraftList($chatId, $userId, $messageId, 0);
            return;
        }
        $has = (string) $draft['media_file_id'] !== '';
        $text = "🖼 <b>عکس پست #" . $id . "</b>\n\n"
            . 'وضعیت فعلی: ' . ($has ? '✅ عکس ثبت شده است' : '⛔️ بدون عکس') . "\n\n"
            . "برای افزودن یا تعویض عکس، همین حالا عکس را در همین چت بفرستید.\n"
            . 'وقتی پست عکس دارد، متن به‌صورت کپشن زیر عکس منتشر می‌شود و حد مجاز آن ۱۰۲۴ کاراکتر است.';

        $kb = Keyboard::make();
        if ($has) {
            $kb->button('🗑 حذف عکس', 'd|nomedia|' . $id);
        }
        $kb->row([['↩️ بازگشت به پیش‌نویس', 'd|v|' . $id], ['🏠 خانه', 'h']]);
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    // --------------------------------------------------------- premium emoji

    /** @param string[] $parts */
    private function onEmojiCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action = $parts[1] ?? 'list';
        $id     = (int) ($parts[2] ?? 0);

        switch ($action) {
            case 'list':
                $this->showEmojiList($chatId, $userId, $messageId);
                return;
            case 'add':
                $state->set('emoji.capture', []);
                $this->safeSend(
                    $chatId,
                    "😎 یک پیام حاوی ایموجی پریمیوم بفرستید یا فوروارد کنید.\n"
                    . 'ربات شناسه همه ایموجی‌های پریمیوم آن پیام را استخراج و ذخیره می‌کند.'
                );
                return;
            case 'code':
                $row = $this->emoji->find($id);
                if ($row === null) {
                    $this->showEmojiList($chatId, $userId, $messageId);
                    return;
                }
                $snippet = CustomEmojiRepository::toHtml((string) $row['emoji_id'], (string) $row['fallback']);
                $this->safeSend(
                    $chatId,
                    "📋 این کد را در الگوی هر بخش قالب بچسبانید:\n\n"
                    . '<code>' . Html::escape($snippet) . '</code>' . "\n\n"
                    . 'پیش‌نمایش: ' . $snippet
                );
                return;
            case 'del':
                $this->emoji->delete($id);
                $this->api->answerCallbackQuery($queryId, 'حذف شد.');
                $this->showEmojiList($chatId, $userId, $messageId);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showEmojiList(int $chatId, int $userId, ?int $messageId): void
    {
        $items = $this->emoji->all();
        $lines = [];
        $kb    = Keyboard::make();
        foreach ($items as $row) {
            $lines[] = '• ' . (string) $row['fallback'] . ' — <code>' . Html::escape((string) $row['emoji_id']) . '</code>';
            $kb->row([
                ['📋 کد ' . (string) $row['fallback'], 'em|code|' . $row['id']],
                ['🗑', 'em|del|' . $row['id']],
            ]);
        }
        $kb->row([['➕ افزودن از روی یک پیام', 'em|add']]);
        $kb->row([['↩️ تنظیمات', 's|home'], ['🏠 خانه', 'h']]);

        $text = "😎 <b>ایموجی‌های پریمیوم</b>\n\n"
            . ($lines === [] ? "هنوز ایموجی‌ای ذخیره نشده است.\n" : implode("\n", $lines) . "\n")
            . "\nℹ️ ایموجی پریمیوم فقط برای ربات‌هایی که تلگرام اجازه داده نمایش داده می‌شود. "
            . 'اگر تلگرام آن را نپذیرد، ربات هنگام انتشار به‌صورت خودکار ایموجی معمولی را جایگزین می‌کند و به شما اطلاع می‌دهد.';
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function captureEmoji(int $chatId, int $userId, State $state, string $text, array $message): void
    {
        $entities = (array) ($message['entities'] ?? $message['caption_entities'] ?? []);
        $found    = Html::customEmoji($text, $entities);
        if ($found === []) {
            $this->safeSend($chatId, '⚠️ در این پیام ایموجی پریمیوم پیدا نشد. پیام دیگری بفرستید یا /cancel بزنید.');
            return;
        }
        foreach ($found as $item) {
            $this->emoji->save('emoji_' . $item['id'], (string) $item['id'], (string) $item['emoji']);
        }
        $state->clear();
        $this->safeSend($chatId, '✅ ' . Str::toPersianDigits((string) count($found)) . ' ایموجی پریمیوم ذخیره شد.');
        $this->showEmojiList($chatId, $userId, null);
    }

    // ------------------------------------------------- editing a channel post

    /** @param string[] $parts */
    private function onChannelEditCallback(int $chatId, int $userId, State $state, ?int $messageId, array $parts, string $queryId): void
    {
        $action  = $parts[1] ?? 'start';
        $payload = $state->payload();

        switch ($action) {
            case 'start':
                $state->set('edit.await_link', []);
                $this->screen($chatId, $userId, $messageId, "🔗 <b>ویرایش پیام کانال</b>\n\n"
                    . "لینک پیام را بفرستید، مثل:\n<code>https://t.me/Ghajarvpn/152</code>\n\n"
                    . 'برای گرفتن لینک، روی پیام در کانال نگه دارید و «Copy Link» را بزنید.',
                    Keyboard::make()->row([['📢 پست‌های منتشرشده', 'p|list|0'], ['🏠 خانه', 'h']]));
                return;
            case 'text':
                $state->set('edit.await_text', $payload);
                $this->safeSend($chatId, "✏️ متن جدید پیام را بفرستید.\n"
                    . 'قالب‌بندی، ایموجی، نقل‌قول و لینک‌های روی متن همان‌طور که می‌فرستید حفظ می‌شوند.');
                return;
            case 'photo':
                $state->set('edit.await_photo', $payload);
                $this->safeSend($chatId, '🖼 عکس جدید را بفرستید؛ جایگزین عکس فعلی پیام می‌شود.');
                return;
            case 'draft':
                $this->applyChannelEditFromDraft($chatId, $userId, $state, $payload, (int) ($parts[2] ?? 0), $queryId);
                return;
            case 'pickdraft':
                $this->showDraftPickerForEdit($chatId, $userId, $messageId);
                return;
            case 'back':
                $this->showChannelEditMenu($chatId, $userId, $messageId, $payload);
                return;
        }
        $this->api->answerCallbackQuery($queryId, 'عملیات ناشناخته.', true);
    }

    private function showChannelEditMenu(int $chatId, int $userId, ?int $messageId, array $payload): void
    {
        $chat      = (string) ($payload['chat'] ?? '');
        $messageNo = (int) ($payload['message_id'] ?? 0);
        $known     = $this->publisher->findPublished($chat, $messageNo);

        $text = "✏️ <b>ویرایش پیام کانال</b>\n\n"
            . 'کانال: <code>' . Html::escape($chat) . "</code>\n"
            . 'شناسه پیام: <code>' . $messageNo . "</code>\n"
            . ($known !== null
                ? 'این پیام در تاریخچه ربات موجود است' . ((int) ($known['has_media'] ?? 0) === 1 ? ' (عکس‌دار).' : '.')
                : 'این پیام در تاریخچه ربات نیست؛ ویرایش فقط اگر پیام را همین ربات فرستاده باشد ممکن است.')
            . "\n\nچه چیزی را جایگزین کنم؟";

        $kb = Keyboard::make()
            ->row([['📝 متن/کپشن جدید', 'edit|text'], ['🖼 عکس جدید', 'edit|photo']])
            ->row([['📄 جایگزینی با یک پیش‌نویس', 'edit|pickdraft']])
            ->row([['↩️ لینک دیگر', 'edit|start'], ['🏠 خانه', 'h']]);
        $this->screen($chatId, $userId, $messageId, $text, $kb);
    }

    private function showDraftPickerForEdit(int $chatId, int $userId, ?int $messageId): void
    {
        $kb = Keyboard::make();
        foreach ($this->drafts->listByStatus(DraftRepository::STATUS_DRAFT, 8) as $draft) {
            $title = Html::toPlain((string) ($draft['content']['title'] ?? '')) ?: Html::toPlain((string) $draft['source_text']);
            $kb->button('📄 #' . $draft['id'] . ' ' . Str::truncate(trim($title), 32), 'edit|draft|' . $draft['id']);
        }
        $kb->row([['↩️ بازگشت', 'edit|back'], ['🏠 خانه', 'h']]);
        $this->screen(
            $chatId,
            $userId,
            $messageId,
            'کدام پیش‌نویس جایگزین محتوای آن پیام شود؟ (متن و عکس پیش‌نویس هر دو اعمال می‌شوند)',
            $kb
        );
    }

    private function applyChannelEditLink(int $chatId, int $userId, State $state, string $value): void
    {
        $parsed = Publisher::parseMessageLink($value);
        if ($parsed === null) {
            $this->safeSend($chatId, '⚠️ لینک معتبر نیست. نمونه درست: <code>https://t.me/Ghajarvpn/152</code>');
            return;
        }
        $state->set('edit.menu', ['chat' => $parsed['chat'], 'message_id' => $parsed['message_id']]);
        $this->showChannelEditMenu($chatId, $userId, null, $parsed);
    }

    private function applyChannelEditText(int $chatId, int $userId, State $state, array $payload, string $html): void
    {
        $result = $this->publisher->editMessage(
            (string) ($payload['chat'] ?? ''),
            (int) ($payload['message_id'] ?? 0),
            $html
        );
        $this->finishChannelEdit($chatId, $userId, $state, $payload, $result);
    }

    private function applyChannelEditPhoto(int $chatId, int $userId, State $state, array $payload, string $fileId): void
    {
        $result = $this->publisher->editMessage(
            (string) ($payload['chat'] ?? ''),
            (int) ($payload['message_id'] ?? 0),
            null,
            $fileId
        );
        $this->finishChannelEdit($chatId, $userId, $state, $payload, $result);
    }

    private function applyChannelEditFromDraft(int $chatId, int $userId, State $state, array $payload, int $draftId, string $queryId): void
    {
        $draft = $this->drafts->find($draftId);
        if ($draft === null) {
            $this->api->answerCallbackQuery($queryId, 'پیش‌نویس پیدا نشد.', true);
            return;
        }
        $result = $this->publisher->editMessage(
            (string) ($payload['chat'] ?? ''),
            (int) ($payload['message_id'] ?? 0),
            $this->composer->render($draft),
            (string) $draft['media_file_id']
        );
        $this->finishChannelEdit($chatId, $userId, $state, $payload, $result);
    }

    private function finishChannelEdit(int $chatId, int $userId, State $state, array $payload, array $result): void
    {
        if (!$result['ok']) {
            $this->safeSend($chatId, '⚠️ ' . $result['message']);
            $this->showChannelEditMenu($chatId, $userId, null, $payload);
            return;
        }
        $state->clear();
        $note = ($result['emoji_fallback'] ?? false)
            ? "\nℹ️ تلگرام ایموجی پریمیوم را نپذیرفت؛ ایموجی معمولی جایگزین شد."
            : '';
        $this->safeSend($chatId, $result['message'] . $note);
        $this->showHome($chatId, $userId, null);
    }

    // -------------------------------------------------------------- helpers

    /** Edit the current menu message when possible, otherwise send a new one. */
    private function screen(int $chatId, int $userId, ?int $messageId, string $text, Keyboard $keyboard): void
    {
        $extra = $keyboard->isEmpty() ? [] : ['reply_markup' => $keyboard->toArray()];
        $text  = Str::truncate($text, 4000);

        if ($messageId !== null) {
            try {
                $this->api->editMessageText($chatId, $messageId, $text, $extra);
                (new State($userId))->setMenuMessageId($messageId);
                return;
            } catch (ApiException $e) {
                if (str_contains($e->getMessage(), 'message is not modified')) {
                    return;
                }
            }
        }
        try {
            $sent = $this->api->sendMessage($chatId, $text, $extra);
            (new State($userId))->setMenuMessageId((int) ($sent['message_id'] ?? 0));
        } catch (ApiException $e) {
            Logger::error('screen send failed', ['code' => $e->errorCode]);
        }
    }

    private function safeSend(int $chatId, string $text): bool
    {
        try {
            $this->api->sendMessage($chatId, Str::truncate($text, 4000));
            return true;
        } catch (ApiException $e) {
            Logger::error('send failed', ['code' => $e->errorCode]);
            return false;
        }
    }
}
