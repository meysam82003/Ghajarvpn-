<?php
declare(strict_types=1);

namespace Ghajar\Studio\Templates;

use PDO;

/**
 * The built-in templates shipped with Ghajar Content Studio.
 * Every one of them is a normal row in `templates` and can be edited,
 * copied, renamed or deleted from inside the bot.
 */
final class DefaultTemplates
{
    public const DEFAULT_RULES = [
        'bold_title'         => true,
        'persian_numbers'    => true,
        'blank_line_between' => true,
        'links_at_end'       => true,
        'dedupe_footer'      => true,
        'title_first_line'   => true,
        'number_before_title'=> true,
    ];

    private static function section(
        string $key,
        string $label,
        string $mode,
        string $template,
        bool $enabled = true,
        string $quote = '',
        bool $expandable = false
    ): array {
        return [
            'key'        => $key,
            'label'      => $label,
            'mode'       => $mode,      // fixed | variable
            'enabled'    => $enabled,
            'template'   => $template,
            'quote'      => $quote,     // '' = no blockquote; same value = one shared quote
            'expandable' => $expandable,
        ];
    }

    /**
     * Footer blocks shared by most templates.
     * $quote groups them into one Telegram blockquote; $anchors shows a
     * clickable phrase instead of a bare URL.
     */
    private static function footer(string $quote = '', bool $anchors = false): array
    {
        if ($anchors) {
            return [
                self::section('channel_info', 'اطلاعات کانال و ربات', 'fixed', "📢 کانال: {channel_link}\n🤖 ربات تلگرام: {bot_link}", true, $quote),
                self::section('download', 'لینک دانلود', 'fixed', '📥 {apk_link}', true, $quote),
                self::section('release', 'لینک ریلیز', 'fixed', '🔗 {release_link}', true, $quote),
                self::section('extra_links', 'لینک‌های اضافی', 'fixed', '{extra_links}', false, $quote),
            ];
        }
        return [
            self::section('channel_info', 'اطلاعات کانال و ربات', 'fixed', "📢 کانال: {channel}\n🤖 ربات تلگرام: {bot}", true, $quote),
            self::section('download', 'لینک دانلود', 'fixed', "📥 دانلود مستقیم APK:\n{apk_url}", true, $quote),
            self::section('release', 'لینک ریلیز', 'fixed', "🔗 ریلیز گیت‌هاب و سایر فایل‌های نصب:\n{release_url}", true, $quote),
            self::section('extra_links', 'لینک‌های اضافی', 'fixed', '{extra_links}', false, $quote),
        ];
    }

    /** @return array<string,array<string,mixed>> slug => definition */
    public static function all(): array
    {
        $templates = [];

        $templates['ghajar-main'] = [
            'name'        => '👑 قالب اصلی قاجار VPN',
            'description' => 'قالب پیش‌فرض کانال قاجار VPN با شماره پست، بدنه، فهرست قابلیت‌ها و فوتر کامل.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه (شماره و عنوان)', 'variable', '📱[[ پست {number} |]] {title}[[ {title_emoji}]]'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'بدنه اصلی', 'variable', '{body}'),
                    self::section('features', 'فهرست قابلیت‌ها', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '👑 {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | انتخاب آگاهانه، اتصال ساده.'),
                ], self::footer()),
            ],
        ];

        $templates['ghajar-quote'] = [
            'name'        => '❝ قاجار — نقل‌قولی (پیشنهادی)',
            'description' => 'همان استایل پست‌های کانال: کل متن داخل نقل‌قول، فوتر در نقل‌قول جدا، و لینک‌ها روی متن (بدون نمایش آدرس خام).',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه (شماره و عنوان)', 'variable', '📱[[ پست {number} |]] {title}[[ {title_emoji}]]', true, 'main'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}', true, 'main'),
                    self::section('body', 'بدنه اصلی', 'variable', '{body}', true, 'main'),
                    self::section('features', 'فهرست قابلیت‌ها', 'variable', '{features}', true, 'main'),
                    self::section('summary', 'جمع‌بندی', 'variable', '👑 {summary}', true, 'main'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN؛ چندین ابزار، یک اپلیکیشن.', true, 'main'),
                ], self::footer('footer', true)),
            ],
        ];

        $templates['ghajar-quote-expandable'] = [
            'name'        => '❝ قاجار — نقل‌قول بازشو',
            'description' => 'مثل قالب نقل‌قولی، اما متن اصلی به‌صورت نقل‌قول بازشو (Expandable) نمایش داده می‌شود؛ مناسب پست‌های بلند.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه (شماره و عنوان)', 'variable', '📱[[ پست {number} |]] {title}[[ {title_emoji}]]', true, 'main', true),
                    self::section('intro', 'مقدمه', 'variable', '{intro}', true, 'main', true),
                    self::section('body', 'بدنه اصلی', 'variable', '{body}', true, 'main', true),
                    self::section('features', 'فهرست قابلیت‌ها', 'variable', '{features}', true, 'main', true),
                    self::section('summary', 'جمع‌بندی', 'variable', '👑 {summary}', true, 'main', true),
                ], self::footer('footer', true)),
            ],
        ];

        $templates['promo-friendly'] = [
            'name'        => '🎉 تبلیغاتی و صمیمی',
            'description' => 'لحن خودمانی برای جذب مخاطب.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '🔥[[ پست {number} |]] {title}[[ {title_emoji}]]'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'بدنه اصلی', 'variable', '{body}'),
                    self::section('features', 'فهرست قابلیت‌ها', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '✨ {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | همیشه وصل باش. 😎'),
                ], self::footer()),
            ],
        ];

        $templates['official'] = [
            'name'        => '🏛 رسمی و حرفه‌ای',
            'description' => 'لحن رسمی برای اطلاع‌رسانی‌های اداری و بیانیه‌ها.',
            'structure'   => [
                'rules'    => array_merge(self::DEFAULT_RULES, ['persian_numbers' => true]),
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '📌[[ پست {number} |]] {title}'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'بدنه اصلی', 'variable', '{body}'),
                    self::section('features', 'فهرست موارد', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '{summary}'),
                    self::section('slogan', 'امضا', 'fixed', 'تیم قاجار VPN'),
                ], self::footer()),
            ],
        ];

        $templates['new-feature'] = [
            'name'        => '✨ معرفی قابلیت جدید',
            'description' => 'برای معرفی یک قابلیت تازه در اپلیکیشن.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '✨[[ پست {number} |]] قابلیت جدید: {title}'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'توضیح قابلیت', 'variable', '{body}'),
                    self::section('features', 'مزیت‌ها', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '👑 {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | هر نسخه، یک قدم جلوتر.'),
                ], self::footer()),
            ],
        ];

        $templates['release'] = [
            'name'        => '🚀 انتشار نسخه جدید',
            'description' => 'اعلام انتشار نسخه تازه به همراه لینک دانلود و ریلیز.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '🚀[[ پست {number} |]] {title}[[ — نسخه {version}]]'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'تغییرات', 'variable', '{body}'),
                    self::section('features', 'فهرست تغییرات', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '{summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | به‌روز بمان.'),
                ], self::footer()),
            ],
        ];

        $templates['announcement'] = [
            'name'        => '📣 اطلاعیه مهم',
            'description' => 'برای اطلاعیه‌های فوری و مهم.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '📣[[ پست {number} |]] اطلاعیه: {title}'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'متن اطلاعیه', 'variable', '{body}'),
                    self::section('features', 'نکات', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '⚠️ {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN'),
                ], self::footer()),
            ],
        ];

        $templates['tutorial'] = [
            'name'        => '📚 آموزش و راهنما',
            'description' => 'قالب گام‌به‌گام برای آموزش‌ها.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '📚[[ پست {number} |]] آموزش: {title}'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'توضیح', 'variable', '{body}'),
                    self::section('features', 'مراحل', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '✅ {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | ساده و بدون پیچیدگی.'),
                ], self::footer()),
            ],
        ];

        $templates['server-intro'] = [
            'name'        => '🌐 معرفی سرور',
            'description' => 'معرفی سرورها و لوکیشن‌های تازه.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '🌐[[ پست {number} |]] {title}'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'مشخصات سرور', 'variable', '{body}'),
                    self::section('features', 'ویژگی‌ها', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '⚡ {summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | اتصال پایدار.'),
                ], self::footer()),
            ],
        ];

        $templates['bugfix'] = [
            'name'        => '🐞 رفع باگ و تغییرات نسخه',
            'description' => 'گزارش رفع اشکال‌ها و تغییرات جزئی.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => array_merge([
                    self::section('header', 'سرصفحه', 'variable', '🐞[[ پست {number} |]] {title}[[ — نسخه {version}]]'),
                    self::section('intro', 'مقدمه', 'variable', '{intro}'),
                    self::section('body', 'توضیح', 'variable', '{body}'),
                    self::section('features', 'فهرست رفع اشکال', 'variable', '{features}'),
                    self::section('summary', 'جمع‌بندی', 'variable', '{summary}'),
                    self::section('slogan', 'شعار پایانی', 'fixed', 'قاجار VPN | پایدارتر از همیشه.'),
                ], self::footer()),
            ],
        ];

        $templates['minimal'] = [
            'name'        => '🪶 کوتاه و مینیمال',
            'description' => 'فقط عنوان، متن و کانال. بدون فوتر سنگین.',
            'structure'   => [
                'rules'    => array_merge(self::DEFAULT_RULES, ['links_at_end' => true]),
                'sections' => [
                    self::section('header', 'سرصفحه', 'variable', '[[پست {number} | ]]{title}[[ {title_emoji}]]'),
                    self::section('body', 'متن', 'variable', '{body}'),
                    self::section('features', 'فهرست', 'variable', '{features}', false),
                    self::section('channel_info', 'اطلاعات کانال', 'fixed', '📢 {channel}'),
                ],
            ],
        ];

        $templates['custom'] = [
            'name'        => '🧩 قالب اختصاصی کاربر',
            'description' => 'قالب خالی برای ساخت ساختار دلخواه شما.',
            'structure'   => [
                'rules'    => self::DEFAULT_RULES,
                'sections' => [
                    self::section('header', 'سرصفحه', 'variable', '[[پست {number} | ]]{title}'),
                    self::section('body', 'متن اصلی', 'variable', '{body}'),
                    self::section('channel_info', 'اطلاعات کانال', 'fixed', "📢 کانال: {channel}\n🤖 ربات: {bot}"),
                ],
            ],
        ];

        return $templates;
    }

    public static function install(PDO $pdo): void
    {
        $stmt = $pdo->prepare(
            'INSERT OR IGNORE INTO templates (name, slug, description, structure, is_default, is_system)
             VALUES (:name, :slug, :description, :structure, :is_default, 1)'
        );
        foreach (self::all() as $slug => $definition) {
            $stmt->execute([
                ':name'        => $definition['name'],
                ':slug'        => $slug,
                ':description' => $definition['description'],
                ':structure'   => json_encode($definition['structure'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
                ':is_default'  => $slug === 'ghajar-quote' ? 1 : 0,
            ]);
        }
    }
}
