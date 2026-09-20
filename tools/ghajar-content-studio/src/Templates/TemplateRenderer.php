<?php
declare(strict_types=1);

namespace Ghajar\Studio\Templates;

use Ghajar\Studio\Support\Str;

/**
 * Renders a template structure into the final Telegram-HTML post.
 *
 * Template syntax:
 *   {placeholder}   → replaced by its value (empty string when missing)
 *   [[ ... ]]       → optional group, dropped entirely when a placeholder
 *                     inside it resolves to an empty value.
 */
final class TemplateRenderer
{
    public const PLACEHOLDERS = [
        'number'      => 'شماره پست',
        'title'       => 'عنوان',
        'title_emoji' => 'ایموجی عنوان',
        'intro'       => 'مقدمه',
        'body'        => 'بدنه اصلی',
        'features'    => 'فهرست قابلیت‌ها',
        'summary'     => 'جمع‌بندی',
        'slogan'      => 'شعار پایانی',
        'channel'     => 'آیدی کانال',
        'bot'         => 'آیدی ربات',
        'apk_url'     => 'لینک دانلود APK',
        'release_url' => 'لینک ریلیز',
        'repo_url'    => 'لینک مخزن',
        'version'     => 'شماره نسخه',
        'date'        => 'تاریخ',
        'extra_links' => 'لینک‌های اضافی',
    ];

    /** Placeholder suffixes every link key also provides. */
    public const LINK_SUFFIXES = [
        '_link' => 'لینک کلیک‌شدنی روی متن',
        '_text' => 'فقط متن لینک',
    ];

    /**
     * @param array<string,mixed>  $structure
     * @param array<string,string> $vars
     */
    public function render(array $structure, array $vars): string
    {
        $rules    = (array) ($structure['rules'] ?? []);
        $sections = (array) ($structure['sections'] ?? []);

        if (($rules['persian_numbers'] ?? true) && isset($vars['number']) && $vars['number'] !== '') {
            $vars['number'] = Str::toPersianDigits($vars['number']);
        }
        if (($rules['bold_title'] ?? true) && !empty($vars['title'])) {
            $vars['title'] = '<b>' . $vars['title'] . '</b>';
        }

        $glue = ($rules['blank_line_between'] ?? true) ? "\n\n" : "\n";

        // Sections carrying the same non-empty "quote" group are rendered
        // inside one shared Telegram blockquote, exactly like the channel
        // posts where the body is one quote and the footer another.
        $groups = [];
        foreach ($sections as $section) {
            if (!is_array($section) || ($section['enabled'] ?? true) === false) {
                continue;
            }
            $text = trim($this->substitute((string) ($section['template'] ?? ''), $vars));
            if ($text === '') {
                continue;
            }
            $quote = trim((string) ($section['quote'] ?? ''));
            $last  = $groups === [] ? null : array_key_last($groups);
            if ($last !== null && $groups[$last]['quote'] === $quote && $quote !== '') {
                $groups[$last]['parts'][] = $text;
                continue;
            }
            $groups[] = [
                'quote'      => $quote,
                'expandable' => (bool) ($section['expandable'] ?? false),
                'parts'      => [$text],
            ];
        }

        $blocks = [];
        foreach ($groups as $group) {
            $body = implode($glue, $group['parts']);
            if ($group['quote'] === '') {
                $blocks[] = $body;
                continue;
            }
            $blocks[] = ($group['expandable'] ? '<blockquote expandable>' : '<blockquote>')
                . $body . '</blockquote>';
        }

        $output = implode($glue, $blocks);
        $output = (string) preg_replace('/[ \t]+\n/u', "\n", $output);
        $output = (string) preg_replace('/\n{3,}/u', "\n\n", $output);

        return trim($output);
    }

    /** @param array<string,string> $vars */
    public function substitute(string $template, array $vars): string
    {
        // Optional groups first: drop the whole group when any placeholder is empty.
        $template = (string) preg_replace_callback('/\[\[(.*?)\]\]/us', function (array $m) use ($vars): string {
            $inner = $m[1];
            preg_match_all('/\{([a-z_]+)\}/u', $inner, $found);
            foreach ($found[1] ?? [] as $name) {
                if (trim((string) ($vars[$name] ?? '')) === '') {
                    return '';
                }
            }
            return $inner;
        }, $template);

        return (string) preg_replace_callback('/\{([a-z_]+)\}/u', static function (array $m) use ($vars): string {
            return (string) ($vars[$m[1]] ?? '');
        }, $template);
    }

    /**
     * A short human readable preview of a template structure (for menus).
     * @param array<string,mixed> $structure
     */
    public function outline(array $structure): string
    {
        $lines = [];
        foreach ((array) ($structure['sections'] ?? []) as $index => $section) {
            if (!is_array($section)) {
                continue;
            }
            $mark = ($section['enabled'] ?? true) ? '✅' : '⛔️';
            $mode = ($section['mode'] ?? 'variable') === 'fixed' ? 'ثابت' : 'متغیر';
            $quote = trim((string) ($section['quote'] ?? '')) !== ''
                ? ' — ❝ نقل‌قول: ' . (string) $section['quote'] . (($section['expandable'] ?? false) ? ' (بازشو)' : '')
                : '';
            $lines[] = sprintf('%s %d. %s — %s%s', $mark, $index + 1, (string) ($section['label'] ?? '—'), $mode, $quote);
        }
        return implode("\n", $lines);
    }
}
