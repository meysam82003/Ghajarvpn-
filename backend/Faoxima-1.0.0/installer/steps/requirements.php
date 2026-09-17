<?php

$allPassed = rx_requirement_checks_passed($requirementChecks);
$failedCount = count(array_filter($requirementChecks, static fn($c) => empty($c['ok'])));
?>
<section class="step-section is-active" id="step-requirements">
    <div class="card">
        <h2 class="card-title">
            <svg><use href="#i-shield-check"/></svg>
            بررسی پیش‌نیازهای سیستم
        </h2>
        <p class="card-sub">اینستالر پیش از ادامه، سلامت محیط اجرا و دسترسی‌های لازم را بررسی می‌کند.</p>

        <div class="summary-bar <?php echo $allPassed ? 'is-ok' : 'is-fail'; ?>">
            <div class="summary-text">
                <svg><use href="<?php echo $allPassed ? '#i-check-circle' : '#i-x-circle'; ?>"/></svg>
                <?php echo $allPassed
                    ? 'همه موارد با موفقیت بررسی شد'
                    : "{$failedCount} مورد نیازمند رفع اشکال است"; ?>
            </div>
            <form method="post">
                <input type="hidden" name="rx_action" value="rescan">
                <button type="submit" class="btn btn-secondary">
                    <svg><use href="#i-refresh"/></svg>
                    اسکن مجدد
                </button>
            </form>
        </div>

        <div class="check-grid">
            <?php foreach ($requirementChecks as $check): ?>
                <div class="check-row <?php echo !empty($check['ok']) ? 'is-ok' : 'is-fail'; ?>">
                    <span class="check-icon">
                        <svg><use href="<?php echo !empty($check['ok']) ? '#i-check' : '#i-x-circle'; ?>"/></svg>
                    </span>
                    <span class="check-text">
                        <span class="check-label"><?php echo rx_escape_html($check['label']); ?></span>
                        <span class="check-detail"><?php echo rx_escape_html($check['detail']); ?></span>
                    </span>
                    <span class="check-badge"><?php echo !empty($check['ok']) ? 'OK' : 'FAIL'; ?></span>
                </div>
            <?php endforeach; ?>
        </div>

        <?php if (!$allPassed): ?>
            <div class="alert alert-warning" style="margin-top:20px;">
                <svg><use href="#i-warn"/></svg>
                <div>
                    تا زمانی که تمام موارد بالا سبز نشوند، امکان ادامه نصب وجود ندارد.
                    پس از رفع مشکلات (مثلاً فعال کردن اکستنشن در cPanel &rarr; PHP Selector، یا اصلاح دسترسی نوشتن پوشه‌ها)، روی «اسکن مجدد» بزنید.
                </div>
            </div>
        <?php endif; ?>

        <form method="post">
            <input type="hidden" name="rx_action" value="proceed">
            <div class="btn-row">
                <button type="submit" class="btn btn-primary btn-grow" <?php echo $allPassed ? '' : 'disabled'; ?>>
                    ادامه نصب
                    <svg><use href="#i-arrow-left"/></svg>
                </button>
            </div>
        </form>
    </div>
</section>
