<?php
declare(strict_types=1);

namespace Ghajar\Studio\Support;

/**
 * Telegram HTML helpers: escaping and converting message entities (which use
 * UTF-16 code-unit offsets) into the HTML subset Telegram accepts.
 */
final class Html
{
    public static function escape(string $text): string
    {
        return str_replace(['&', '<', '>'], ['&amp;', '&lt;', '&gt;'], $text);
    }

    /** Remove Telegram HTML tags, returning plain text. */
    public static function toPlain(string $html): string
    {
        $text = (string) preg_replace('#<br\s*/?>#i', "\n", $html);
        $text = strip_tags($text);
        return html_entity_decode($text, ENT_QUOTES | ENT_HTML5, 'UTF-8');
    }

    /**
     * Convert a Telegram message text + entities into Telegram-flavoured HTML.
     *
     * @param array<int,array<string,mixed>> $entities
     */
    public static function fromEntities(string $text, array $entities): string
    {
        $units = self::toUtf16Units($text);
        $len   = count($units);
        /** @var array<int,string[]> $open */
        $open = [];
        /** @var array<int,string[]> $close */
        $close = [];

        // Longer entities must open first and close last so tags nest properly.
        usort($entities, static function (array $a, array $b): int {
            return [$a['offset'] ?? 0, -($a['length'] ?? 0)] <=> [$b['offset'] ?? 0, -($b['length'] ?? 0)];
        });

        foreach ($entities as $entity) {
            $type   = (string) ($entity['type'] ?? '');
            $offset = (int) ($entity['offset'] ?? 0);
            $length = (int) ($entity['length'] ?? 0);
            if ($length <= 0 || $offset < 0 || $offset + $length > $len) {
                continue;
            }
            [$openTag, $closeTag] = self::tagsFor($type, $entity, $units, $offset, $length);
            if ($openTag === null) {
                continue;
            }
            $open[$offset][]              = $openTag;
            $close[$offset + $length][]   = $closeTag;
        }

        $out = '';
        for ($i = 0; $i <= $len; $i++) {
            if (isset($close[$i])) {
                foreach (array_reverse($close[$i]) as $tag) {
                    $out .= $tag;
                }
            }
            if (isset($open[$i])) {
                foreach ($open[$i] as $tag) {
                    $out .= $tag;
                }
            }
            if ($i < $len) {
                $out .= self::escape(self::unitsToString([$units[$i]]));
            }
        }
        return $out;
    }

    /**
     * @param string[] $units
     * @return array{0:?string,1:string}
     */
    private static function tagsFor(string $type, array $entity, array $units, int $offset, int $length): array
    {
        $inner = self::unitsToString(array_slice($units, $offset, $length));
        return match ($type) {
            'bold'           => ['<b>', '</b>'],
            'italic'         => ['<i>', '</i>'],
            'underline'      => ['<u>', '</u>'],
            'strikethrough'  => ['<s>', '</s>'],
            'spoiler'        => ['<tg-spoiler>', '</tg-spoiler>'],
            'code'           => ['<code>', '</code>'],
            'blockquote'     => ['<blockquote>', '</blockquote>'],
            'expandable_blockquote' => ['<blockquote expandable>', '</blockquote>'],
            'pre'            => [
                '<pre>' . (isset($entity['language']) && $entity['language'] !== ''
                    ? '<code class="language-' . self::escape((string) $entity['language']) . '">'
                    : ''),
                (isset($entity['language']) && $entity['language'] !== '' ? '</code>' : '') . '</pre>',
            ],
            'text_link'      => ['<a href="' . self::escape((string) ($entity['url'] ?? '')) . '">', '</a>'],
            'text_mention'   => ['<a href="tg://user?id=' . (int) (($entity['user']['id'] ?? 0)) . '">', '</a>'],
            'custom_emoji'   => ['<tg-emoji emoji-id="' . self::escape((string) ($entity['custom_emoji_id'] ?? '')) . '">', '</tg-emoji>'],
            default          => [null, ''],
        };
    }

    /**
     * Split a UTF-8 string into UTF-16 code units (Telegram offset space).
     * Each returned element is a UTF-8 fragment; astral characters occupy two
     * elements where the second is an empty string.
     *
     * @return string[]
     */
    public static function toUtf16Units(string $text): array
    {
        $units = [];
        $chars = preg_split('//u', $text, -1, PREG_SPLIT_NO_EMPTY) ?: [];
        foreach ($chars as $char) {
            $units[] = $char;
            if (self::codePoint($char) > 0xFFFF) {
                $units[] = '';
            }
        }
        return $units;
    }

    /** @param string[] $units */
    public static function unitsToString(array $units): string
    {
        return implode('', $units);
    }

    public static function utf16Length(string $text): int
    {
        return count(self::toUtf16Units($text));
    }

    /** Replace premium/custom emoji with their plain fallback character. */
    public static function stripCustomEmoji(string $html): string
    {
        return (string) preg_replace('#<tg-emoji[^>]*>(.*?)</tg-emoji>#us', '$1', $html);
    }

    public static function hasCustomEmoji(string $html): bool
    {
        return str_contains($html, '<tg-emoji');
    }

    /**
     * Every custom emoji in a message, as ['id' => ..., 'emoji' => ...].
     *
     * @param array<int,array<string,mixed>> $entities
     * @return list<array{id:string,emoji:string}>
     */
    public static function customEmoji(string $text, array $entities): array
    {
        $units = self::toUtf16Units($text);
        $found = [];
        foreach ($entities as $entity) {
            if (($entity['type'] ?? '') !== 'custom_emoji') {
                continue;
            }
            $id = (string) ($entity['custom_emoji_id'] ?? '');
            if ($id === '' || isset($found[$id])) {
                continue;
            }
            $found[$id] = [
                'id'    => $id,
                'emoji' => self::unitsToString(array_slice($units, (int) $entity['offset'], (int) $entity['length'])),
            ];
        }
        return array_values($found);
    }

    /**
     * Split Telegram HTML into top-level blocks, separating blockquotes from
     * ordinary text so the parser can see the message's real layout.
     *
     * @return list<array{quote:bool,expandable:bool,html:string}>
     */
    public static function splitBlocks(string $html): array
    {
        $blocks = [];
        $offset = 0;
        $pattern = '#<blockquote(\s+expandable)?>(.*?)</blockquote>#us';
        if (preg_match_all($pattern, $html, $matches, PREG_OFFSET_CAPTURE | PREG_SET_ORDER)) {
            foreach ($matches as $match) {
                $start = (int) $match[0][1];
                $before = substr($html, $offset, $start - $offset);
                if (trim($before) !== '') {
                    $blocks[] = ['quote' => false, 'expandable' => false, 'html' => trim($before)];
                }
                $blocks[] = [
                    'quote'      => true,
                    'expandable' => trim((string) $match[1][0]) !== '',
                    'html'       => trim((string) $match[2][0]),
                ];
                $offset = $start + strlen((string) $match[0][0]);
            }
        }
        $rest = substr($html, $offset);
        if (trim($rest) !== '') {
            $blocks[] = ['quote' => false, 'expandable' => false, 'html' => trim($rest)];
        }
        return $blocks;
    }

    private static function codePoint(string $char): int
    {
        $value = mb_ord($char, 'UTF-8');
        return $value === false ? 0 : $value;
    }
}
