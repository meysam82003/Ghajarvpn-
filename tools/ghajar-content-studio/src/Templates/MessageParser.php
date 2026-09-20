<?php
declare(strict_types=1);

namespace Ghajar\Studio\Templates;

use Ghajar\Studio\Support\Html;
use Ghajar\Studio\Support\Str;

/**
 * Splits an incoming (already Telegram-HTML encoded) message into the
 * manageable parts the templates understand. Nothing is invented here:
 * every produced part is a verbatim slice of the original message.
 */
final class MessageParser
{
    /**
     * @return array{
     *   number:string, title:string, title_emoji:string, intro:string,
     *   body:string, features:string, summary:string, slogan:string,
     *   footer:string[], links:string[], layout:array<string,bool>
     * }
     */
    public function parse(string $html): array
    {
        $content = [
            'number' => '', 'title' => '', 'title_emoji' => '', 'intro' => '',
            'body' => '', 'features' => '', 'summary' => '', 'slogan' => '',
            'footer' => [], 'links' => [],
            'layout' => [
                'main_quote' => false, 'main_expandable' => false,
                'footer_quote' => false, 'footer_expandable' => false,
            ],
        ];

        $html = str_replace(["\r\n", "\r"], "\n", trim($html));
        if ($html === '') {
            return $content;
        }

        // Unwrap blockquotes but remember the layout, so a forwarded post keeps
        // its quote structure when it is saved as a template.
        $blocks       = Html::splitBlocks($html);
        $footerBlock  = null;
        if (count($blocks) > 1) {
            $last = $blocks[count($blocks) - 1];
            if ($last['quote'] && $this->looksLikeFooterBlock((string) $last['html'])) {
                $footerBlock = $last;
                array_pop($blocks);
                $content['layout']['footer_quote']      = true;
                $content['layout']['footer_expandable'] = (bool) $last['expandable'];
            }
        }
        foreach ($blocks as $block) {
            if ($block['quote']) {
                $content['layout']['main_quote']      = true;
                $content['layout']['main_expandable'] = $content['layout']['main_expandable'] || (bool) $block['expandable'];
            }
        }
        $html = trim(implode("\n\n", array_map(static fn (array $b): string => (string) $b['html'], $blocks)));
        if ($footerBlock !== null) {
            $html .= "\n\n" . (string) $footerBlock['html'];
        }

        $lines = Str::lines($html);
        $content['links'] = Str::extractUrls(Html::toPlain($html));

        // 1) Peel the footer off the end (channel / bot / links block).
        $footer = [];
        while ($lines !== []) {
            $last = trim((string) end($lines));
            if ($last === '') {
                array_pop($lines);
                continue;
            }
            if ($this->isFooterLine($last)) {
                array_unshift($footer, $last);
                array_pop($lines);
                continue;
            }
            break;
        }
        $content['footer'] = $footer;

        // 2) Header line → number + emoji + title.
        $header = '';
        while ($lines !== []) {
            $first = trim((string) array_shift($lines));
            if ($first !== '') {
                $header = $first;
                break;
            }
        }
        if ($header !== '') {
            $parsed              = $this->parseHeader($header);
            $content['number']      = $parsed['number'];
            $content['title']       = $parsed['title'];
            $content['title_emoji'] = $parsed['emoji'];
        }

        // 3) Remaining body → paragraphs.
        $rest       = trim(implode("\n", $lines));
        $paragraphs = Str::paragraphs($rest);

        $features = [];
        $others   = [];
        foreach ($paragraphs as $paragraph) {
            $paraLines = array_values(array_filter(array_map('trim', Str::lines($paragraph)), static fn ($l) => $l !== ''));
            $bullets   = array_filter($paraLines, static fn (string $l) => Str::isBulletLine($l));
            if (count($paraLines) >= 2 && count($bullets) === count($paraLines)) {
                $features = array_merge($features, $paraLines);
                continue;
            }
            $others[] = $paragraph;
        }
        $content['features'] = implode("\n", $features);

        // 4) Slogan: a short closing single-line paragraph mentioning the brand.
        if ($others !== []) {
            $lastParagraph = (string) end($others);
            if ($this->isSloganLine($lastParagraph)) {
                $content['slogan'] = $lastParagraph;
                array_pop($others);
            }
        }

        // 5) Summary: a short closing paragraph (often starting with an emoji).
        if (count($others) > 1) {
            $lastParagraph = (string) end($others);
            if (mb_strlen(Html::toPlain($lastParagraph)) <= 160 && !str_contains($lastParagraph, "\n")) {
                $content['summary'] = $lastParagraph;
                array_pop($others);
            }
        }

        // 6) Intro: first paragraph, body: the rest.
        if ($others !== []) {
            $content['intro'] = (string) array_shift($others);
        }
        $content['body'] = implode("\n\n", $others);

        // A single-paragraph message keeps everything in the body so nothing is lost.
        if ($content['body'] === '' && $content['features'] === '' && $content['summary'] === '' && $content['intro'] !== '') {
            $content['body']  = $content['intro'];
            $content['intro'] = '';
        }

        return $content;
    }

    /** @return array{number:string,title:string,emoji:string} */
    public function parseHeader(string $header): array
    {
        $number = '';
        $emoji  = '';

        [$leading, $rest] = Str::splitLeadingEmoji($header);
        if ($leading !== '') {
            $emoji = $leading;
        }

        // "پست ۱۵ | عنوان"  /  "پست 15 - عنوان"  /  "#15 عنوان"
        $normalized = Str::toEnglishDigits($rest);
        if (preg_match('/^(?:پست|post|#)\s*(\d{1,6})\s*[|\-–—:]?\s*(.*)$/ui', $normalized, $m)) {
            $number = $m[1];
            $offset = mb_strlen($m[0]) - mb_strlen($m[2]);
            $rest   = trim(mb_substr($rest, $offset));
        }

        // Trailing emoji on the title becomes the title emoji when none was found.
        if (preg_match('/^(.*?)\s*((?:[\x{1F000}-\x{1FAFF}\x{2190}-\x{27BF}\x{2B00}-\x{2BFF}\x{2600}-\x{26FF}]\x{FE0F}?)+)$/u', $rest, $m)) {
            $rest = trim($m[1]);
            if ($emoji === '' || $emoji === '📱') {
                $emoji = trim($m[2]);
            }
        }

        return ['number' => $number, 'title' => trim($rest), 'emoji' => $emoji];
    }

    /** True when every line of a block looks like footer material. */
    private function looksLikeFooterBlock(string $html): bool
    {
        $lines = array_values(array_filter(array_map('trim', Str::lines($html)), static fn ($l) => $l !== ''));
        if ($lines === [] || count($lines) > 8) {
            return false;
        }
        foreach ($lines as $line) {
            if (!$this->isFooterLine($line)) {
                return false;
            }
        }
        return true;
    }

    public function isFooterLine(string $line): bool
    {
        // A line whose only content is one hyperlink is footer material too
        // (channel posts link a phrase instead of showing the raw URL).
        if (preg_match('#^\s*[^<\w]{0,4}\s*<a\s[^>]*>.*</a>\s*:?\s*$#us', $line)) {
            return true;
        }
        $plain = trim(Html::toPlain($line));
        if ($plain === '') {
            return false;
        }
        if (Str::looksLikeUrlLine($plain)) {
            return true;
        }
        return (bool) preg_match('/^(📢|🤖|📥|🔗|🌐)?\s*(کانال|ربات|دانلود|ریلیز|لینک|channel|bot|download|release)/ui', $plain)
            && (str_contains($plain, ':') || str_contains($plain, '@') || str_contains($plain, 'http'));
    }

    private function isSloganLine(string $paragraph): bool
    {
        if (str_contains($paragraph, "\n")) {
            return false;
        }
        $plain = Html::toPlain($paragraph);
        if (mb_strlen($plain) > 120) {
            return false;
        }
        return (bool) preg_match('/(قاجار\s*VPN|Ghajar\s*VPN)/ui', $plain) && (str_contains($plain, '|') || str_contains($plain, '،'));
    }

    /**
     * Build a brand new template structure out of a sample message: every
     * detected part becomes a section, variable where content was found.
     *
     * @param array<string,string|array> $content
     * @return array<string,mixed>
     */
    public function toTemplateStructure(array $content, array $linkPlaceholders = []): array
    {
        $layout     = (array) ($content['layout'] ?? []);
        $mainQuote  = ($layout['main_quote'] ?? false) ? 'main' : '';
        $mainExpand = (bool) ($layout['main_expandable'] ?? false);
        $footQuote  = ($layout['footer_quote'] ?? false) ? 'footer' : '';
        $footExpand = (bool) ($layout['footer_expandable'] ?? false);

        $sections = [];
        $sections[] = [
            'key' => 'header', 'label' => 'سرصفحه (شماره و عنوان)', 'mode' => 'variable', 'enabled' => true,
            'template' => ($content['title_emoji'] !== '' ? (string) $content['title_emoji'] . ' ' : '') . '[[پست {number} | ]]{title}',
            'quote' => $mainQuote, 'expandable' => $mainExpand,
        ];
        foreach ([
            ['intro', 'مقدمه', '{intro}'],
            ['body', 'بدنه اصلی', '{body}'],
            ['features', 'فهرست قابلیت‌ها', '{features}'],
            ['summary', 'جمع‌بندی', '{summary}'],
        ] as [$key, $label, $tpl]) {
            // Core text parts stay enabled even when the sample lacked them:
            // an empty variable renders nothing, but a disabled section would
            // silently swallow that part of every future post.
            $sections[] = [
                'key' => $key, 'label' => $label, 'mode' => 'variable',
                'enabled' => true, 'template' => $tpl,
                'quote' => $mainQuote, 'expandable' => $mainExpand,
            ];
        }
        if (trim((string) ($content['slogan'] ?? '')) !== '') {
            $sections[] = [
                'key' => 'slogan', 'label' => 'شعار پایانی', 'mode' => 'fixed',
                'enabled' => true, 'template' => (string) $content['slogan'],
                'quote' => $mainQuote, 'expandable' => $mainExpand,
            ];
        }
        $footer = (array) ($content['footer'] ?? []);
        if ($footer !== []) {
            $sections[] = [
                'key' => 'footer', 'label' => 'فوتر (اطلاعات ثابت)', 'mode' => 'fixed',
                'enabled' => true,
                'template' => $this->placeholderize(implode("\n", array_map('strval', $footer)), $linkPlaceholders),
                'quote' => $footQuote, 'expandable' => $footExpand,
            ];
        }

        return ['rules' => DefaultTemplates::DEFAULT_RULES, 'sections' => $sections];
    }

    /**
     * Replace URLs that the bot already manages with their placeholder, so a
     * template built from a sample keeps following the link manager.
     *
     * @param array<string,string> $linkPlaceholders url => placeholder name
     */
    public function placeholderize(string $html, array $linkPlaceholders): string
    {
        foreach ($linkPlaceholders as $url => $placeholder) {
            if (trim((string) $url) === '') {
                continue;
            }
            $html = str_replace(Html::escape((string) $url), '{' . $placeholder . '}', $html);
            $html = str_replace((string) $url, '{' . $placeholder . '}', $html);
        }
        return $html;
    }
}
