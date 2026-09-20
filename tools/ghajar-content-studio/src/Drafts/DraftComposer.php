<?php
declare(strict_types=1);

namespace Ghajar\Studio\Drafts;

use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Links\LinkRepository;
use Ghajar\Studio\Support\Html;
use Ghajar\Studio\Support\Str;
use Ghajar\Studio\Templates\MessageParser;
use Ghajar\Studio\Templates\TemplateRenderer;
use Ghajar\Studio\Templates\TemplateRepository;

final class DraftComposer
{
    public function __construct(
        private TemplateRepository $templates = new TemplateRepository(),
        private LinkRepository $links = new LinkRepository(),
        private TemplateRenderer $renderer = new TemplateRenderer(),
        private MessageParser $parser = new MessageParser(),
        private PostNumbering $numbering = new PostNumbering(),
    ) {
    }

    /**
     * Render a draft into final Telegram HTML.
     *
     * @param array<string,mixed> $draft
     */
    public function render(array $draft, ?int $forcedNumber = null): string
    {
        $template = $draft['template_id'] !== null ? $this->templates->find((int) $draft['template_id']) : null;
        $template ??= $this->templates->default();
        if ($template === null) {
            return (string) ($draft['source_html'] ?: $draft['source_text']);
        }

        $structure = $template['structure'];
        $rules     = (array) ($structure['rules'] ?? []);
        $content   = (array) $draft['content'];

        if ($rules['dedupe_footer'] ?? true) {
            foreach (['intro', 'body', 'summary'] as $key) {
                $content[$key] = $this->stripFooter((string) ($content[$key] ?? ''));
            }
        }

        $number = $forcedNumber ?? $this->numbering->preview($draft);

        $vars = array_merge($this->links->placeholders(), [
            'number'      => $number !== null ? (string) $number : '',
            'title'       => trim((string) ($content['title'] ?? '')),
            'title_emoji' => trim((string) ($content['title_emoji'] ?? '')),
            'intro'       => trim((string) ($content['intro'] ?? '')),
            'body'        => trim((string) ($content['body'] ?? '')),
            'features'    => trim((string) ($content['features'] ?? '')),
            'summary'     => trim((string) ($content['summary'] ?? '')),
            'slogan'      => trim((string) ($content['slogan'] ?? '')),
            'channel'     => Settings::get('channel', '@Ghajarvpn'),
            'bot'         => Settings::get('bot_username') !== '' ? '@' . Settings::get('bot_username') : '@Ghajar_vpnbot',
            'version'     => Settings::get('current_version', ''),
            'date'        => Str::toPersianDigits(gmdate('Y-m-d')),
        ]);

        return $this->renderer->render($structure, $vars);
    }

    /** Parse an incoming message into draft content. */
    public function parseIncoming(string $html): array
    {
        return $this->parser->parse($html);
    }

    /** Plain-text version of the rendered post, for "copyable text". */
    public function toPlain(string $rendered): string
    {
        return Html::toPlain($rendered);
    }

    /** Remove already-present footer lines so they are never duplicated. */
    public function stripFooter(string $htmlBlock): string
    {
        if (trim($htmlBlock) === '') {
            return '';
        }
        $lines = Str::lines($htmlBlock);
        while ($lines !== []) {
            $last = trim((string) end($lines));
            if ($last === '') {
                array_pop($lines);
                continue;
            }
            if ($this->parser->isFooterLine($last)) {
                array_pop($lines);
                continue;
            }
            break;
        }
        return trim(implode("\n", $lines));
    }

    /**
     * Telegram hard-limits a text message to 4096 characters, and a photo
     * caption to 1024.
     */
    public function validate(string $rendered, bool $asCaption = false): ?string
    {
        $plain = Html::toPlain($rendered);
        if (trim($plain) === '') {
            return 'متن نهایی خالی است.';
        }
        $limit = $asCaption ? 1024 : 4096;
        if (mb_strlen($plain) > $limit) {
            return sprintf(
                'متن نهایی %s کاراکتر است و از حد مجاز تلگرام (%s) بیشتر است%s.',
                Str::toPersianDigits((string) mb_strlen($plain)),
                Str::toPersianDigits((string) $limit),
                $asCaption ? ' — چون پست عکس دارد، متن به‌عنوان کپشن ارسال می‌شود' : ''
            );
        }
        return null;
    }
}
