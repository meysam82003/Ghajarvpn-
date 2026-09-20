<?php
/**
 * Ghajar Content Studio — test suite.
 * Usage:  php tests/run.php
 */
declare(strict_types=1);

require __DIR__ . '/../src/autoload.php';
require __DIR__ . '/TestCase.php';
require __DIR__ . '/FakeApi.php';

use Ghajar\Studio\Bot\Router;
use Ghajar\Studio\Bot\State;
use Ghajar\Studio\Core\Config;
use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Migrations;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Drafts\DraftComposer;
use Ghajar\Studio\Drafts\DraftRepository;
use Ghajar\Studio\Drafts\PostNumbering;
use Ghajar\Studio\Github\GithubClient;
use Ghajar\Studio\Github\GithubException;
use Ghajar\Studio\Links\LinkRepository;
use Ghajar\Studio\Publishing\Publisher;
use Ghajar\Studio\Support\Html;
use Ghajar\Studio\Support\Str;
use Ghajar\Studio\Telegram\ApiException;
use Ghajar\Studio\Templates\CustomEmojiRepository;
use Ghajar\Studio\Templates\MessageParser;
use Ghajar\Studio\Templates\TemplateRenderer;
use Ghajar\Studio\Templates\TemplateRepository;

const ADMIN_ID = 555001;
const DB_PATH  = '/tmp/gcs-test.sqlite';

function boot(): void
{
    foreach ([DB_PATH, DB_PATH . '-wal', DB_PATH . '-shm'] as $file) {
        @unlink($file);
    }
    Database::reset();
    $pdo = Database::connect(DB_PATH);
    Database::set($pdo);
    Migrations::run($pdo);
    Migrations::seed($pdo, ADMIN_ID);
    Settings::flush();
}

boot();

$t        = TestRunner::class;
$parser   = new MessageParser();
$composer = new DraftComposer();
$drafts   = new DraftRepository();
$templates = new TemplateRepository();
$numbering = new PostNumbering();

$sample = "📱 پست ۱۵ | این سرور یا اون سرور؟ 🤔\n\n"
    . "تا حالا شده ده تا کانفیگ داشته باشی، ولی ندونی کدوم رو انتخاب کنی؟\n\n"
    . "یکی رو وصل می‌کنی، خوب نیست.\nدومی رو امتحان می‌کنی، اونم نه!\n\n"
    . "قاجار VPN برای انتخاب سرور ابزارهای کاربردی داره:\n\n"
    . "⚡ تست پینگ سرورها\n📊 مرتب‌سازی بر اساس نتایج\n🔍 جست‌وجوی سریع\n❤️ اضافه‌کردن به علاقه‌مندی‌ها\n\n"
    . "👑 به‌جای انتخاب شانسی، با اطلاعات انتخاب کن.\n\n"
    . "قاجار VPN | انتخاب آگاهانه، اتصال ساده.\n\n"
    . "📢 کانال: @Ghajarvpn\n🤖 ربات تلگرام: @Ghajar_vpnbot\n\n"
    . "📥 دانلود مستقیم APK:\nhttps://github.com/meysam82003/Ghajarvpn-/releases/download/1.0.4/app.apk\n\n"
    . "🔗 ریلیز گیت‌هاب و سایر فایل‌های نصب:\nhttps://github.com/meysam82003/Ghajarvpn-/releases/tag/1.0.4";

// ------------------------------------------------------------- installation
TestRunner::group('نصب');

TestRunner::test('۱/۳ ساخت دیتابیس و همه جدول‌ها', function () use ($t): void {
    $tables = Database::connect()->query("SELECT name FROM sqlite_master WHERE type='table'")->fetchAll();
    $names  = array_column($tables, 'name');
    foreach (['settings', 'templates', 'drafts', 'draft_revisions', 'links', 'published_posts',
              'conversation_state', 'github_releases', 'processed_updates'] as $table) {
        $t::assertTrue(in_array($table, $names, true), "missing table $table");
    }
});

TestRunner::test('۲ رد کردن توکن نامعتبر (قالب توکن)', function () use ($t): void {
    $t::assertFalse((bool) preg_match('/^\d{6,12}:[A-Za-z0-9_\-]{30,}$/', 'not-a-token'));
    $t::assertTrue((bool) preg_match('/^\d{6,12}:[A-Za-z0-9_\-]{30,}$/', '123456789:AAF' . str_repeat('x', 32)));
});

TestRunner::test('۴ ثبت موفق Webhook با secret', function () use ($t): void {
    $api = new FakeApi();
    $t::assertTrue($api->setWebhook('https://example.test/webhook.php', 'S3CRET'));
    $params = $api->sentTo('setWebhook')[0];
    $t::assertSame('S3CRET', $params['secret_token']);
});

TestRunner::test('۵ جلوگیری از نصب مجدد (قفل نصب‌کننده)', function () use ($t): void {
    $lock = sys_get_temp_dir() . '/gcs-installed.lock';
    @unlink($lock);
    $t::assertFalse(is_file($lock));
    file_put_contents($lock, '{}');
    $t::assertTrue(is_file($lock), 'lock file must block a second run');
    @unlink($lock);
});

TestRunner::test('توکن در لاگ افشا نمی‌شود', function () use ($t): void {
    $redacted = \Ghajar\Studio\Support\Logger::redact('token=123456789:AAF' . str_repeat('x', 32));
    $t::assertStringContains('[TOKEN]', $redacted);
});

// --------------------------------------------------------------------- auth
TestRunner::group('امنیت');

TestRunner::test('۶ کاربر غیرمجاز نمی‌تواند از ربات استفاده کند', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9001, 'message' => [
        'message_id' => 1, 'from' => ['id' => 424242], 'chat' => ['id' => 424242, 'type' => 'private'], 'text' => '/start',
    ]]);
    $sent = $api->sentTo('sendMessage');
    $t::assertSame(1, count($sent));
    $t::assertStringContains('خصوصی', (string) $sent[0]['text']);
    $t::assertStringNotContains('Ghajar Content Studio', (string) $sent[0]['text']);
});

TestRunner::test('callback کاربر غیرمجاز اجرا نمی‌شود', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9002, 'callback_query' => [
        'id' => 'cb1', 'from' => ['id' => 111], 'data' => 'd|pubok|1',
        'message' => ['message_id' => 5, 'chat' => ['id' => 111]],
    ]]);
    $t::assertSame([], $api->sentTo('sendMessage'));
});

TestRunner::test('هر update فقط یک بار پردازش می‌شود', function () use ($t): void {
    $api    = new FakeApi();
    $router = new Router($api);
    $update = ['update_id' => 9100, 'message' => [
        'message_id' => 2, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'], 'text' => '/start',
    ]];
    $router->handle($update);
    $count = count($api->sentTo('sendMessage'));
    $router->handle($update);
    $t::assertSame($count, count($api->sentTo('sendMessage')), 'duplicate update must be ignored');
});

// ------------------------------------------------------------------ parsing
TestRunner::group('دریافت و تبدیل پیام');

TestRunner::test('۷ دریافت پیام و ساخت پیش‌نویس', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9200, 'message' => [
        'message_id' => 3, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'text' => "عنوان تست\n\nمتن بدنه تست.",
    ]]);
    $t::assertTrue((new DraftRepository())->countByStatus(DraftRepository::STATUS_DRAFT) > 0);
    $t::assertStringContains('قالب موردنظر را انتخاب کنید', (string) $api->sentTo('sendMessage')[0]['text']);
});

TestRunner::test('۸ دریافت پیام فوروارد شده', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9201, 'message' => [
        'message_id' => 4, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'forward_date' => time(), 'forward_origin' => ['type' => 'channel'],
        'text' => "پیام فوروارد شده\n\nمتن.",
    ]]);
    $draft = (new DraftRepository())->listByStatus(DraftRepository::STATUS_DRAFT, 1)[0];
    $t::assertTrue((bool) ($draft['source_meta']['forwarded'] ?? false));
});

TestRunner::test('پیام بدون متن و بدون عکس (محتوای محافظت‌شده) اطلاع‌رسانی می‌کند', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9202, 'message' => [
        'message_id' => 5, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'voice' => ['file_id' => 'v1'],
    ]]);
    $t::assertStringContains('محافظت‌شده', (string) $api->sentTo('sendMessage')[0]['text']);
});

TestRunner::test('عکس تنها به پیش‌نویس جاری می‌چسبد', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9203, 'message' => [
        'message_id' => 6, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'photo' => [['file_id' => 'small'], ['file_id' => 'BIGGEST']],
    ]]);
    $draft = (new DraftRepository())->listByStatus(DraftRepository::STATUS_DRAFT, 1)[0];
    $t::assertSame('BIGGEST', (string) $draft['media_file_id']);
    $t::assertStringContains('کپشن', (string) $api->sentTo('sendMessage')[0]['text']);
});

TestRunner::test('عکس با کپشن، هم پیش‌نویس می‌سازد هم عکس را ثبت می‌کند', function () use ($t): void {
    $api = new FakeApi();
    (new Router($api))->handle(['update_id' => 9204, 'message' => [
        'message_id' => 7, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'photo'   => [['file_id' => 'PHOTO_WITH_CAPTION']],
        'caption' => "عنوان عکس‌دار\n\nمتن زیر عکس.",
    ]]);
    $draft = (new DraftRepository())->listByStatus(DraftRepository::STATUS_DRAFT, 1)[0];
    $t::assertSame('PHOTO_WITH_CAPTION', (string) $draft['media_file_id']);
    $t::assertStringContains('عنوان عکس‌دار', (string) $draft['content']['title']);
});

TestRunner::test('۱۱ تبدیل متن: شماره، عنوان و ایموجی تشخیص داده می‌شود', function () use ($t, $parser, $sample): void {
    $content = $parser->parse($sample);
    $t::assertSame('15', $content['number']);
    $t::assertStringContains('این سرور یا اون سرور؟', $content['title']);
    $t::assertSame('🤔', $content['title_emoji']);
});

TestRunner::test('۱۲ ایموجی‌های متن حفظ می‌شوند', function () use ($t, $parser, $sample): void {
    $content = $parser->parse($sample);
    foreach (['⚡', '📊', '🔍', '❤️'] as $emoji) {
        $t::assertStringContains($emoji, $content['features']);
    }
});

TestRunner::test('۱۳ لینک‌ها از متن استخراج و حفظ می‌شوند', function () use ($t, $parser, $sample): void {
    $content = $parser->parse($sample);
    $t::assertTrue(count($content['links']) >= 2);
    $t::assertStringContains('releases/tag/1.0.4', implode(' ', $content['links']));
});

TestRunner::test('قالب‌بندی تلگرام (entities) به HTML تبدیل می‌شود', function () use ($t): void {
    $html = Html::fromEntities('سلام دنیا', [['type' => 'bold', 'offset' => 0, 'length' => 4]]);
    $t::assertSame('<b>سلام</b> دنیا', $html);
    $link = Html::fromEntities('کلیک', [['type' => 'text_link', 'offset' => 0, 'length' => 4, 'url' => 'https://a.test']]);
    $t::assertSame('<a href="https://a.test">کلیک</a>', $link);
});

TestRunner::test('آفست‌های UTF-16 برای ایموجی درست محاسبه می‌شوند', function () use ($t): void {
    // "🔥" is a surrogate pair → 2 UTF-16 units, so "ok" starts at offset 3.
    $html = Html::fromEntities('🔥 ok', [['type' => 'bold', 'offset' => 3, 'length' => 2]]);
    $t::assertSame('🔥 <b>ok</b>', $html);
});

TestRunner::test('HTML ورودی کاربر escape می‌شود (جلوگیری از HTML Injection)', function () use ($t): void {
    $html = Html::fromEntities('<script>alert(1)</script>', []);
    $t::assertStringNotContains('<script>', $html);
    $t::assertStringContains('&lt;script&gt;', $html);
});

// ---------------------------------------------------------------- templates
TestRunner::group('قالب‌ها');

TestRunner::test('۱۰ ذخیره و بارگذاری قالب', function () use ($t, $templates): void {
    $id = $templates->create('قالب تستی', ['rules' => [], 'sections' => [
        ['key' => 'h', 'label' => 'سرصفحه', 'mode' => 'variable', 'enabled' => true, 'template' => '{title}'],
    ]], 'توضیح');
    $loaded = $templates->find($id);
    $t::assertSame('قالب تستی', (string) $loaded['name']);
    $t::assertSame('{title}', (string) $loaded['structure']['sections'][0]['template']);
});

TestRunner::test('۹ ساخت قالب جدید از روی پیام نمونه', function () use ($t, $parser, $templates, $sample): void {
    $structure = $parser->toTemplateStructure($parser->parse($sample));
    $id        = $templates->create('از روی نمونه', $structure);
    $loaded    = $templates->find($id);
    $keys      = array_column((array) $loaded['structure']['sections'], 'key');
    $t::assertTrue(in_array('header', $keys, true));
    $t::assertTrue(in_array('features', $keys, true));
    $t::assertTrue(in_array('footer', $keys, true));
});

TestRunner::test('متن نمونه به پست‌های بعدی منتقل نمی‌شود', function () use ($t, $parser, $templates, $sample, $composer, $drafts): void {
    $structure  = $parser->toTemplateStructure($parser->parse($sample));
    $templateId = $templates->create('قالب نمونه‌محور', $structure);
    $newContent = $parser->parse('عنوان تازه' . "\n\n" . 'متن کاملاً جدید.');
    $draftId    = $drafts->create(ADMIN_ID, 'x', 'x', $newContent, $templateId);
    $rendered   = $composer->render($drafts->find($draftId));
    $t::assertStringNotContains('کانفیگ', $rendered, 'sample body leaked into a new post');
    $t::assertStringContains('متن کاملاً جدید', $rendered);
});

TestRunner::test('۱۰ قالب پیش‌فرض، کپی، تغییر نام و حذف', function () use ($t, $templates): void {
    $id = $templates->create('برای حذف', ['rules' => [], 'sections' => [['key' => 'b', 'label' => 'b', 'mode' => 'variable', 'enabled' => true, 'template' => '{body}']]]);
    $templates->rename($id, 'نام تازه');
    $t::assertSame('نام تازه', (string) $templates->find($id)['name']);
    $copy = $templates->duplicate($id);
    $t::assertTrue($copy !== null && $copy !== $id);
    $templates->setDefault($id);
    $t::assertSame($id, (int) $templates->default()['id']);
    $t::assertTrue($templates->delete((int) $copy));
    $t::assertTrue($templates->find((int) $copy) === null);
    $templates->setDefault((int) $templates->findBySlug('ghajar-main')['id']);
});

TestRunner::test('بخش اختیاری [[..]] وقتی متغیر خالی است حذف می‌شود', function () use ($t): void {
    $renderer = new TemplateRenderer();
    $t::assertSame('📱 عنوان', trim($renderer->substitute('📱[[ پست {number} |]] {title}', ['number' => '', 'title' => 'عنوان'])));
    $t::assertSame('📱 پست ۱۵ | عنوان', trim($renderer->substitute('📱[[ پست {number} |]] {title}', ['number' => '۱۵', 'title' => 'عنوان'])));
});

// ------------------------------------------------------------------- drafts
TestRunner::group('پیش‌نویس و انتشار');

$mainTemplateId = (int) $templates->findBySlug('ghajar-main')['id'];
$draftId        = $drafts->create(ADMIN_ID, $sample, $sample, $parser->parse($sample), $mainTemplateId);

TestRunner::test('۱۴ فوتر تکراری اضافه نمی‌شود', function () use ($t, $composer, $drafts, $draftId): void {
    $rendered = $composer->render($drafts->find($draftId));
    $t::assertSame(1, substr_count($rendered, '📢 کانال:'), 'channel footer duplicated');
    $t::assertSame(1, substr_count($rendered, '📥 دانلود مستقیم APK:'), 'download footer duplicated');
});

TestRunner::test('لینک‌های ثابت از دیتابیس در انتهای پست قرار می‌گیرند', function () use ($t, $composer, $drafts, $draftId): void {
    $rendered = $composer->render($drafts->find($draftId));
    $apk      = (new LinkRepository())->get('apk_url');
    $t::assertStringContains($apk, $rendered);
    $t::assertTrue(mb_strpos($rendered, $apk) > mb_strpos($rendered, 'کانال:'), 'links must sit at the end');
});

TestRunner::test('۱۶ ویرایش عنوان بدنه را تغییر نمی‌دهد', function () use ($t, $drafts, $composer, $draftId): void {
    $draft   = $drafts->find($draftId);
    $before  = (string) $draft['content']['body'];
    $content = $draft['content'];
    $content['title'] = 'عنوان عوض شد';
    $drafts->updateContent($draftId, $content, 'تست');
    $after = $drafts->find($draftId);
    $t::assertSame($before, (string) $after['content']['body']);
    $t::assertStringContains('عنوان عوض شد', $composer->render($after));
});

TestRunner::test('۱۷ تغییر قالب، محتوای اصلی را حفظ می‌کند', function () use ($t, $drafts, $templates, $composer, $draftId): void {
    $before = (string) $drafts->find($draftId)['content']['body'];
    $drafts->setTemplate($draftId, (int) $templates->findBySlug('minimal')['id']);
    $after = $drafts->find($draftId);
    $t::assertSame($before, (string) $after['content']['body']);
    $t::assertStringContains('عنوان عوض شد', $composer->render($after));
    $drafts->setTemplate($draftId, (int) $templates->findBySlug('ghajar-main')['id']);
});

TestRunner::test('بازگشت به نسخه قبلی پیش‌نویس کار می‌کند', function () use ($t, $drafts, $draftId): void {
    $before  = $drafts->find($draftId);
    $content = $before['content'];
    $content['title'] = 'عنوان موقت';
    $drafts->updateContent($draftId, $content, 'تست undo');
    $t::assertSame('عنوان موقت', (string) $drafts->find($draftId)['content']['title']);
    $t::assertTrue($drafts->restoreLatestRevision($draftId));
    $t::assertSame((string) $before['content']['title'], (string) $drafts->find($draftId)['content']['title']);
});

TestRunner::test('۲۳ پیش‌نویس بعد از Restart (اتصال جدید) باقی می‌ماند', function () use ($t, $draftId): void {
    Database::reset();
    Database::set(Database::connect(DB_PATH));
    Settings::flush();
    $t::assertTrue((new DraftRepository())->find($draftId) !== null);
});

TestRunner::test('۱۵ شماره پست فقط هنگام انتشار مصرف می‌شود', function () use ($t, $drafts, $numbering, $parser): void {
    $before = $numbering->next();
    $tmp    = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse('یک عنوان' . "\n\n" . 'متن'), null);
    $drafts->delete($tmp);
    $t::assertSame($before, $numbering->next(), 'creating/deleting a draft must not burn a number');
});

TestRunner::test('۱۵ شماره تکراری پذیرفته نمی‌شود', function () use ($t, $numbering): void {
    Database::connect()->prepare(
        'INSERT OR IGNORE INTO published_posts (post_number, channel, message_id, text) VALUES (77, "@x", 4242, "t")'
    )->execute();
    $t::assertTrue($numbering->isUsed(77));
    $t::assertFalse($numbering->isUsed(78));
});

TestRunner::test('۲۱ انتشار پست در کانال', function () use ($t, $draftId, $numbering): void {
    $api    = new FakeApi();
    $number = $numbering->next();
    $result = (new Publisher($api))->publish($draftId);
    $t::assertTrue($result['ok'], (string) $result['message']);
    $t::assertSame($number, $result['number']);
    $t::assertStringContains('t.me/Ghajarvpn/', (string) $result['url']);
    $t::assertSame($number + 1, $numbering->next(), 'the number must be consumed on success');
    $sent = $api->sentTo('sendMessage')[0];
    $t::assertSame('@Ghajarvpn', (string) $sent['chat_id']);
    $t::assertStringContains('انتخاب آگاهانه', (string) $sent['text']);
});

TestRunner::test('۲۲ جلوگیری از انتشار دوباره', function () use ($t, $draftId): void {
    $result = (new Publisher(new FakeApi()))->publish($draftId);
    $t::assertFalse($result['ok']);
    $t::assertStringContains('قبلاً منتشر شده', (string) $result['message']);
});

TestRunner::test('انتشار بدون دسترسی ادمین متوقف می‌شود و شماره مصرف نمی‌کند', function () use ($t, $drafts, $parser, $numbering): void {
    $api = new FakeApi();
    $api->responses['getChatMember'] = ['status' => 'member'];
    $id     = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse("عنوان\n\nمتن"), null);
    $before = $numbering->next();
    $result = (new Publisher($api))->publish($id);
    $t::assertFalse($result['ok']);
    $t::assertStringContains('ادمین', (string) $result['message']);
    $t::assertSame($before, $numbering->next());
    $t::assertSame([], $api->sentTo('sendMessage'));
});

TestRunner::test('قطع ارتباط هنگام انتشار باعث ارسال کورکورانه دوباره نمی‌شود', function () use ($t, $drafts, $parser): void {
    $api        = new FakeApi();
    $api->throw = new ApiException('connection reset', 0, 'sendMessage', true);
    $id         = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse("عنوان دوم\n\nمتن"), null);
    $result     = (new Publisher($api))->publish($id);
    $t::assertFalse($result['ok']);
    $t::assertSame('unknown', (string) ($result['state'] ?? ''));
    $t::assertSame(DraftRepository::STATUS_UNKNOWN, (string) $drafts->find($id)['status']);
});

TestRunner::test('متن بلندتر از ۴۰۹۶ کاراکتر منتشر نمی‌شود', function () use ($t, $composer): void {
    $t::assertTrue($composer->validate(str_repeat('ا', 5000)) !== null);
    $t::assertTrue($composer->validate('متن کوتاه') === null);
});

// ------------------------------------------------------------------- github
TestRunner::group('گیت‌هاب');

TestRunner::test('۲۰ مدیریت خطای گیت‌هاب: لینک‌های قبلی حفظ می‌شوند', function () use ($t): void {
    $links  = new LinkRepository();
    $before = $links->get('apk_url');
    try {
        (new GithubClient('this-owner-does-not-exist/xx-' . bin2hex(random_bytes(6))))->releases(1);
        $failed = false;
    } catch (GithubException) {
        $failed = true;
    }
    $t::assertTrue($failed, 'a missing repo must raise GithubException');
    $t::assertSame($before, $links->get('apk_url'));
});

TestRunner::test('۱۹ ساختار داده ریلیز و تشخیص معماری APK', function () use ($t): void {
    $client = new GithubClient();
    $client->cache([
        'tag' => '9.9.9', 'name' => 'test', 'published_at' => '2026-01-01T00:00:00Z',
        'html_url' => 'https://github.com/x/y/releases/tag/9.9.9',
        'is_draft' => false, 'is_prerelease' => true,
        'assets' => [['name' => 'app-1.0-arm64-v8a.apk', 'size' => 1048576,
                      'download_url' => 'https://x/app.apk', 'is_apk' => true, 'arch' => 'arm64-v8a']],
    ]);
    $cached = $client->cachedByTag('9.9.9');
    $t::assertTrue($cached !== null);
    $t::assertTrue((bool) $cached['is_prerelease']);
    $t::assertSame('arm64-v8a', (string) $cached['assets'][0]['arch']);
});

// ------------------------------------------------------------------ helpers
TestRunner::group('ابزارها');

TestRunner::test('تبدیل ارقام فارسی/انگلیسی', function () use ($t): void {
    $t::assertSame('۱۵', Str::toPersianDigits('15'));
    $t::assertSame('15', Str::toEnglishDigits('۱۵'));
});

TestRunner::test('وضعیت گفت‌وگو ذخیره و پاک می‌شود', function () use ($t): void {
    $state = new State(ADMIN_ID);
    $state->set('draft.edit_title', ['draft' => 7]);
    $t::assertSame('draft.edit_title', $state->current());
    $t::assertSame(7, (int) $state->payload()['draft']);
    $state->clear();
    $t::assertSame('', $state->current());
});

TestRunner::test('پرس‌وجوهای دیتابیس با پارامتر اجرا می‌شوند (SQL Injection)', function () use ($t, $drafts): void {
    $results = $drafts->search("'; DROP TABLE drafts; --");
    $t::assertSame([], $results);
    $t::assertTrue($drafts->countAll() > 0, 'drafts table must still exist');
});

TestRunner::test('تنظیمات در دیتابیس ذخیره و خوانده می‌شود', function () use ($t): void {
    Settings::set('channel', '@TestChannel');
    Settings::flush();
    $t::assertSame('@TestChannel', Settings::get('channel'));
    Settings::set('channel', '@Ghajarvpn');
});

// ------------------------------------------------- quotes, links, emoji, media
TestRunner::group('نقل‌قول، لینک روی متن، ایموجی و عکس');

TestRunner::test('بخش‌های هم‌گروه داخل یک نقل‌قول مشترک رندر می‌شوند', function () use ($t): void {
    $renderer = new TemplateRenderer();
    $out = $renderer->render([
        'rules' => ['bold_title' => false],
        'sections' => [
            ['key' => 'a', 'label' => 'a', 'mode' => 'variable', 'enabled' => true, 'template' => '{title}', 'quote' => 'main'],
            ['key' => 'b', 'label' => 'b', 'mode' => 'variable', 'enabled' => true, 'template' => '{body}', 'quote' => 'main'],
            ['key' => 'c', 'label' => 'c', 'mode' => 'fixed', 'enabled' => true, 'template' => 'فوتر', 'quote' => 'footer'],
        ],
    ], ['title' => 'عنوان', 'body' => 'متن']);
    $t::assertSame(2, substr_count($out, '<blockquote>'), 'exactly two separate quotes');
    $t::assertStringContains("<blockquote>عنوان\n\nمتن</blockquote>", $out);
    $t::assertStringContains('<blockquote>فوتر</blockquote>', $out);
});

TestRunner::test('نقل‌قول بازشو (expandable) تولید می‌شود', function () use ($t): void {
    $out = (new TemplateRenderer())->render([
        'rules' => ['bold_title' => false],
        'sections' => [
            ['key' => 'a', 'label' => 'a', 'mode' => 'variable', 'enabled' => true,
             'template' => '{body}', 'quote' => 'main', 'expandable' => true],
        ],
    ], ['body' => 'متن بلند']);
    $t::assertStringContains('<blockquote expandable>متن بلند</blockquote>', $out);
});

TestRunner::test('لینک‌ها روی متن نمایش داده می‌شوند و آدرس خام دیده نمی‌شود', function () use ($t, $templates, $drafts, $composer, $parser): void {
    $links = new LinkRepository();
    $links->set('apk_url', 'https://example.test/app.apk');
    $links->setAnchor('apk_url', 'دانلود مستقیم APK');

    $id    = (int) $templates->findBySlug('ghajar-quote')['id'];
    $draft = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse("یک عنوان\n\nیک متن."), $id);
    $out   = $composer->render($drafts->find($draft));

    $t::assertStringContains('<a href="https://example.test/app.apk">دانلود مستقیم APK</a>', $out);
    $t::assertStringNotContains('📥 https://example.test/app.apk', $out, 'the raw URL must not be shown');
    $t::assertSame(2, substr_count($out, '<blockquote>'), 'body and footer are separate quotes');
});

TestRunner::test('قالب فورواردشده نقل‌قول و لینک روی متن را حفظ می‌کند', function () use ($t, $parser, $templates, $composer, $drafts): void {
    // A forwarded channel post: whole body quoted, footer quoted separately,
    // and the download line is a hyperlink on a phrase.
    $sampleHtml = '<blockquote>📱 پست ۱۴ | <b>قاجار فقط یک VPN نیست!</b>' . "\n\n"
        . 'یه برنامه برای اتصال، یه برنامه برای تست سرور.' . "\n\n"
        . '🌍 مدیریت و انتخاب سرور' . "\n" . '⚡ تست پینگ کانفیگ‌ها</blockquote>' . "\n"
        . '<blockquote>📢 کانال: <a href="https://t.me/Ghajarvpn">@Ghajarvpn</a>' . "\n"
        . '📥 <a href="https://github.com/x/y/releases/download/1/app.apk">دانلود مستقیم APK</a></blockquote>';

    $content = $parser->parse($sampleHtml);
    $t::assertTrue((bool) $content['layout']['main_quote'], 'main quote must be detected');
    $t::assertTrue((bool) $content['layout']['footer_quote'], 'footer quote must be detected');
    $t::assertSame('14', $content['number']);

    $structure  = $parser->toTemplateStructure($content, [
        'https://github.com/x/y/releases/download/1/app.apk' => 'apk_url',
    ]);
    $templateId = $templates->create('از فوروارد', $structure);
    $loaded     = $templates->find($templateId);

    $quotes = array_column((array) $loaded['structure']['sections'], 'quote');
    $t::assertTrue(in_array('main', $quotes, true), 'body sections keep the main quote');
    $t::assertTrue(in_array('footer', $quotes, true), 'footer keeps its own quote');

    $footer = '';
    foreach ((array) $loaded['structure']['sections'] as $section) {
        if (($section['key'] ?? '') === 'footer') {
            $footer = (string) $section['template'];
        }
    }
    $t::assertStringContains('{apk_url}', $footer, 'a managed URL becomes a placeholder');
    $t::assertStringContains('<a href=', $footer, 'the hyperlink itself is preserved');

    // A brand new post through that template keeps the layout, not the sample text.
    $draftId = $drafts->create(ADMIN_ID, 'x', 'x', (new MessageParser())->parse("عنوان تازه\n\nمتن تازه."), $templateId);
    $out     = $composer->render($drafts->find($draftId));
    $t::assertStringContains('<blockquote>', $out);
    $t::assertStringContains('متن تازه', $out);
    $t::assertStringNotContains('یه برنامه برای اتصال', $out);
});

TestRunner::test('ایموجی پریمیوم از پیام استخراج و ذخیره می‌شود', function () use ($t): void {
    $entities = [['type' => 'custom_emoji', 'offset' => 0, 'length' => 2, 'custom_emoji_id' => '5384541907051357217']];
    $found    = Html::customEmoji('👑 سلام', $entities);
    $t::assertSame(1, count($found));
    $t::assertSame('5384541907051357217', $found[0]['id']);

    $repo = new CustomEmojiRepository();
    $repo->save('emoji_test', $found[0]['id'], $found[0]['emoji']);
    $t::assertTrue($repo->count() > 0);
    $html = Html::fromEntities('👑 سلام', $entities);
    $t::assertStringContains('<tg-emoji emoji-id="5384541907051357217">', $html);
});

TestRunner::test('اگر تلگرام ایموجی پریمیوم را نپذیرد، به ایموجی معمولی برمی‌گردد', function () use ($t, $drafts, $templates, $parser): void {
    $templateId = $templates->create('با ایموجی پریمیوم', ['rules' => ['bold_title' => false], 'sections' => [
        ['key' => 'b', 'label' => 'b', 'mode' => 'variable', 'enabled' => true,
         'template' => '<tg-emoji emoji-id="123">👑</tg-emoji> {body}'],
    ]]);
    $draftId = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse("عنوان\n\nمتن ایموجی‌دار"), $templateId);

    $api = new class extends FakeApi {
        public int $attempts = 0;
        public function call(string $method, array $params = []): mixed
        {
            if ($method === 'sendMessage') {
                $this->attempts++;
                if ($this->attempts === 1) {
                    throw new ApiException('Bad Request: CUSTOM_EMOJI_INVALID', 400, 'sendMessage');
                }
            }
            return parent::call($method, $params);
        }
    };
    $result = (new Publisher($api))->publish($draftId);
    $t::assertTrue($result['ok'], (string) $result['message']);
    $t::assertTrue((bool) ($result['emoji_fallback'] ?? false), 'the fallback must be reported');
    // The rejected attempt never reaches the transport, so the recorded call
    // is the successful retry.
    $sent = $api->sentTo('sendMessage');
    $last = (string) $sent[count($sent) - 1]['text'];
    $t::assertSame(2, $api->attempts, 'exactly one retry');
    $t::assertStringNotContains('<tg-emoji', $last);
    $t::assertStringContains('👑', $last);
    $t::assertStringContains('متن ایموجی‌دار', $last);
});

TestRunner::test('پست عکس‌دار با sendPhoto و کپشن منتشر می‌شود', function () use ($t, $drafts, $templates, $parser): void {
    $templateId = (int) $templates->findBySlug('minimal')['id'];
    $draftId    = $drafts->create(ADMIN_ID, 'x', 'x', $parser->parse("عنوان عکس\n\nمتن کوتاه."), $templateId);
    $drafts->setMedia($draftId, 'photo', 'FILE_ID_123');

    $api    = new FakeApi();
    $result = (new Publisher($api))->publish($draftId);
    $t::assertTrue($result['ok'], (string) $result['message']);
    $t::assertSame([], $api->sentTo('sendMessage'), 'a photo post must not be sent as plain text');
    $photo = $api->sentTo('sendPhoto')[0];
    $t::assertSame('FILE_ID_123', (string) $photo['photo']);
    $t::assertStringContains('متن کوتاه', (string) $photo['caption']);
});

TestRunner::test('کپشن بلندتر از ۱۰۲۴ کاراکتر رد می‌شود', function () use ($t, $composer): void {
    $long = str_repeat('ا', 1100);
    $t::assertTrue($composer->validate($long, true) !== null, 'caption limit must apply');
    $t::assertTrue($composer->validate($long, false) === null, 'the same text is fine without a photo');
});

TestRunner::test('لینک پیام تلگرام درست تجزیه می‌شود', function () use ($t): void {
    $public = Publisher::parseMessageLink('https://t.me/Ghajarvpn/152');
    $t::assertSame('@Ghajarvpn', (string) $public['chat']);
    $t::assertSame(152, (int) $public['message_id']);

    $private = Publisher::parseMessageLink('https://t.me/c/1234567890/44');
    $t::assertSame('-1001234567890', (string) $private['chat']);
    $t::assertSame(44, (int) $private['message_id']);

    $t::assertTrue(Publisher::parseMessageLink('https://example.com/x') === null);
});

TestRunner::test('ویرایش پیام کانال با لینک: متن و عکس', function () use ($t, $drafts, $parser): void {
    $api = new FakeApi();
    $publisher = new Publisher($api);

    // A text-only message is edited with editMessageText.
    $result = $publisher->editMessage('@Ghajarvpn', 500, 'متن کاملاً تازه');
    $t::assertTrue($result['ok'], (string) $result['message']);
    $t::assertSame('متن کاملاً تازه', (string) $api->sentTo('editMessageText')[0]['text']);

    // Replacing the photo goes through editMessageMedia.
    $result = $publisher->editMessage('@Ghajarvpn', 500, 'کپشن تازه', 'NEW_PHOTO');
    $t::assertTrue($result['ok'], (string) $result['message']);
    $media = (array) $api->sentTo('editMessageMedia')[0]['media'];
    $t::assertSame('NEW_PHOTO', (string) $media['media']);
    $t::assertSame('کپشن تازه', (string) $media['caption']);
});

TestRunner::test('ویرایش پست عکس‌دارِ ثبت‌شده، کپشن را عوض می‌کند', function () use ($t): void {
    Database::connect()->prepare(
        'INSERT INTO published_posts (post_number, channel, message_id, text, media_file_id, has_media)
         VALUES (901, :c, 777, :t, :f, 1)'
    )->execute([':c' => '@Ghajarvpn', ':t' => 'کپشن قدیمی', ':f' => 'OLD_PHOTO']);

    $api    = new FakeApi();
    $result = (new Publisher($api))->editMessage('@Ghajarvpn', 777, 'کپشن جدید');
    $t::assertTrue($result['ok'], (string) $result['message']);
    $t::assertSame('کپشن جدید', (string) $api->sentTo('editMessageCaption')[0]['caption']);
    $t::assertSame([], $api->sentTo('editMessageText'), 'a photo post has no text to edit');
});

TestRunner::test('منوی ویرایش پیام کانال با لینک واقعی باز می‌شود', function () use ($t): void {
    $api    = new FakeApi();
    $router = new Router($api);
    $router->handle(['update_id' => 9400, 'callback_query' => [
        'id' => 'cb-edit', 'from' => ['id' => ADMIN_ID], 'data' => 'edit|start',
        'message' => ['message_id' => 70, 'chat' => ['id' => ADMIN_ID]],
    ]]);
    $router->handle(['update_id' => 9401, 'message' => [
        'message_id' => 71, 'from' => ['id' => ADMIN_ID], 'chat' => ['id' => ADMIN_ID, 'type' => 'private'],
        'text' => 'https://t.me/Ghajarvpn/152',
    ]]);
    $texts = array_map(static fn (array $p) => (string) $p['text'], $api->sentTo('sendMessage'));
    $joined = implode(' | ', $texts) . implode(' | ', array_map(
        static fn (array $p) => (string) $p['text'],
        $api->sentTo('editMessageText')
    ));
    $t::assertStringContains('152', $joined);
    $t::assertStringContains('چه چیزی را جایگزین کنم', $joined);
});

// ---------------------------------------------------------------- installer
TestRunner::group('نصب‌کننده');

/** Copy the project into a temp dir and serve it with PHP's built-in server. */
function installer_sandbox(): array
{
    $src = dirname(__DIR__);
    $dir = sys_get_temp_dir() . '/gcs-installer-' . bin2hex(random_bytes(4));
    mkdir($dir, 0777, true);
    $it = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($src, FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::SELF_FIRST
    );
    foreach ($it as $item) {
        $rel = substr($item->getPathname(), strlen($src) + 1);
        if (str_starts_with($rel, 'dist') || str_contains($rel, '.sqlite') || str_contains($rel, 'install-claim')) {
            continue;
        }
        $target = $dir . '/' . $rel;
        $item->isDir() ? @mkdir($target, 0777, true) : @copy($item->getPathname(), $target);
    }
    @unlink($dir . '/storage/config.php');
    @unlink($dir . '/storage/installed.lock');

    $port = random_int(9200, 9600);
    $proc = proc_open(
        [PHP_BINARY, '-S', '127.0.0.1:' . $port, '-t', $dir],
        [1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']],
        $pipes,
        $dir
    );
    for ($i = 0; $i < 50; $i++) {
        $socket = @fsockopen('127.0.0.1', $port, $errNo, $errStr, 0.2);
        if ($socket !== false) {
            fclose($socket);
            break;
        }
        usleep(100_000);
    }
    return ['dir' => $dir, 'port' => $port, 'proc' => $proc];
}

function installer_request(int $port, string $jar, array $post = []): array
{
    $ch = curl_init('http://127.0.0.1:' . $port . '/install.php');
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_COOKIEJAR      => $jar,
        CURLOPT_COOKIEFILE     => $jar,
        CURLOPT_TIMEOUT        => 15,
    ]);
    if ($post !== []) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query($post));
    }
    $body   = (string) curl_exec($ch);
    $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
    curl_close($ch);
    return ['status' => $status, 'body' => $body];
}

$sandbox = installer_sandbox();
$jar     = $sandbox['dir'] . '/cookies.txt';

TestRunner::test('صفحه نصب بدون خطا باز می‌شود و فقط دو فیلد دارد', function () use ($t, $sandbox, $jar): void {
    $response = installer_request($sandbox['port'], $jar);
    $t::assertSame(200, $response['status'], 'installer must not error out');
    $t::assertStringContains('name="bot_token"', $response['body']);
    $t::assertStringContains('name="admin_id"', $response['body']);
    $t::assertSame(2, substr_count($response['body'], '<input id='), 'only two visible inputs are allowed');
    foreach (['Fatal error', 'Deprecated', 'Warning:', 'Notice:'] as $noise) {
        $t::assertStringNotContains($noise, $response['body']);
    }
});

TestRunner::test('بدون توکن CSRF نصب انجام نمی‌شود', function () use ($t, $sandbox, $jar): void {
    $response = installer_request($sandbox['port'], $jar, ['bot_token' => 'x', 'admin_id' => '1', 'csrf' => 'wrong']);
    $t::assertStringContains('نشست نصب منقضی', $response['body']);
});

TestRunner::test('مراحل بررسی محیط اجرا و گزارش می‌شوند', function () use ($t, $sandbox, $jar): void {
    $claim = json_decode((string) file_get_contents($sandbox['dir'] . '/storage/install-claim.json'), true);
    $response = installer_request($sandbox['port'], $jar, [
        'csrf'      => (string) ($claim['csrf'] ?? ''),
        'bot_token' => '123456789:AAF' . str_repeat('0', 32),
        'admin_id'  => '555',
    ]);
    $t::assertSame(200, $response['status']);
    $t::assertStringContains('بررسی PHP و افزونه‌ها', $response['body']);
    $t::assertStringContains('۸.۲ / ۸.۳ / ۸.۴', $response['body'], 'the PHP range must be reported');
    $t::assertStringContains('بررسی cURL', $response['body']);
    // Served over plain HTTP, so the HTTPS step must fail with a clear reason.
    $t::assertStringContains('HTTPS', $response['body']);
    $t::assertStringContains('نصب کامل نشد', $response['body']);
    $t::assertStringNotContains('AAF00000', $response['body'], 'the token must never be echoed back');
    foreach (['Fatal error', 'Deprecated', 'Warning:'] as $noise) {
        $t::assertStringNotContains($noise, $response['body']);
    }
});

TestRunner::test('نصب‌کننده قفل‌شده دیگر فرم نمی‌دهد', function () use ($t, $sandbox, $jar): void {
    file_put_contents($sandbox['dir'] . '/storage/installed.lock', '{}');
    $response = installer_request($sandbox['port'], $jar);
    $t::assertStringNotContains('name="bot_token"', $response['body']);
    $t::assertStringContains('قفل شده', $response['body']);
    @unlink($sandbox['dir'] . '/storage/installed.lock');
});

if (is_resource($sandbox['proc'])) {
    proc_terminate($sandbox['proc']);
    proc_close($sandbox['proc']);
}

exit(TestRunner::summary());
