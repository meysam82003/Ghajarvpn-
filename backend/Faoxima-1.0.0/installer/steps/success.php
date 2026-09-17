<section class="step-section is-active">
    <div class="card">
        <div class="alert alert-success">
            <svg><use href="#i-check-circle"/></svg>
            <div><?php echo implode('<br>', array_map('rx_escape_html', $successMessages)); ?></div>
        </div>
        <a class="btn btn-primary btn-grow" style="width:100%;padding:16px;font-size:16px;margin-top:10px;" href="https://t.me/<?php echo rx_escape_html($botUsername); ?>">
            <svg><use href="#i-bot"/></svg>
            رفتن به ربات @<?php echo rx_escape_html($botUsername); ?>
            <svg><use href="#i-arrow-left"/></svg>
        </a>
        <div style="text-align:center;margin-top:22px;color:var(--text-muted);">
            <p><strong style="color:var(--text-main);">نصب با موفقیت تکمیل شد!</strong></p>
            <p style="font-size:13px;margin-top:4px;">پوشه‌ی <code>installer</code> به‌صورت خودکار در حال حذف است — برای امنیت هاست.</p>
        </div>
    </div>
</section>
<script>
window.addEventListener('load', function () {
    var body = 'rx_action=cleanup';
    if (navigator.sendBeacon) {
        navigator.sendBeacon('index.php', new Blob([body], { type: 'application/x-www-form-urlencoded' }));
    } else {
        fetch('index.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body,
            keepalive: true,
        }).catch(function () {});
    }
});
</script>
