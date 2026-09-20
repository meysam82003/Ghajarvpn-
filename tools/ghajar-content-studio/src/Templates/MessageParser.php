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
     *   footer:string[], links:string[]
     * }
     */
    public function parse(string $html): array
    {
        $content = [
            'number' => '', 'title' => '', 'title_emoji' => '', 'intro' => '',
            'body' => '', 'features' => '', 'summary' => '', 'slogan' => '',
            'footer' => [], 'links' => [],
        ];

        $html = str_replace(["\r\n", "\r"], "\n", trim($html));
        if ($html === '') {
            return $content;
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

    public function isFooterLine(string $line): bool
    {
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
    public function toTemplateStructure(array $content): array
    {
        $sections = [];
        $sections[] = [
            'key' => 'header', 'label' => 'سرصفحه (شماره و عنوان)', 'mode' => 'variable', 'enabled' => true,
            'template' => ($content['title_emoji'] !== '' ? (string) $content['title_emoji'] . ' ' : '') . '[[پست {number} | ]]{title}',
        ];
        foreach ([
            ['intro', 'مقدمه', '{intro}'],
            ['body', 'بدنه اصلی', '{body}'],
            ['features', 'فهرست قابلیت‌ها', '{features}'],
            ['summary', 'جمع‌بندی', '{summary}'],
        ] as [$key, $label, $tpl]) {
            $sections[] = [
                'key' => $key, 'label' => $label, 'mode' => 'variable',
                'enabled' => trim((string) ($content[$key] ?? '')) !== '', 'template' => $tpl,
            ];
        }
        if (trim((string) ($content['slogan'] ?? '')) !== '') {
            $sections[] = [
                'key' => 'slogan', 'label' => 'شعار پایانی', 'mode' => 'fixed',
                'enabled' => true, 'template' => (string) $content['slogan'],
            ];
        }
        $footer = (array) ($content['footer'] ?? []);
        if ($footer !== []) {
            $sections[] = [
                'key' => 'footer', 'label' => 'فوتر (اطلاعات ثابت)', 'mode' => 'fixed',
                'enabled' => true, 'template' => implode("\n", array_map('strval', $footer)),
            ];
        }

        return ['rules' => DefaultTemplates::DEFAULT_RULES, 'sections' => $sections];
    }
}
