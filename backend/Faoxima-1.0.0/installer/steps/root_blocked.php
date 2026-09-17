<section class="step-section is-active">
    <div class="card">
        <h2 class="card-title">
            <svg><use href="#i-x-circle"/></svg>
            اجرا در ریشه اصلی دامنه مجاز نیست
        </h2>
        <div class="alert alert-danger">
            <svg><use href="#i-warn"/></svg>
            <div>
                پوشه پروژه نباید مستقیماً روی ریشه اصلی هاست (<code><?php echo rx_escape_html($rootBlockedDomain); ?>/</code>) قرار بگیرد،
                حتی اگر اینستالر خودش داخل زیرپوشه <code>installer</code> باشد.
                برای ادامه، تمام فایل‌های پروژه (شامل <code>config.php</code> و پوشه <code>installer</code>) را درون یک ساب‌دایرکتوری
                (مثلاً <code>bot</code> یا هر نام دلخواه دیگر) آپلود کنید و اینستالر را از مسیر آن ساب‌دایرکتوری اجرا کنید.
            </div>
        </div>
        <div class="check-grid">
            <div class="check-row is-fail">
                <span class="check-icon"><svg><use href="#i-folder"/></svg></span>
                <span class="check-text">
                    <span class="check-label">مسیر فعلی پوشه پروژه</span>
                    <span class="check-detail"><?php echo rx_escape_html($rootBlockedPath); ?></span>
                </span>
                <span class="check-badge">FAIL</span>
            </div>
        </div>
    </div>
</section>
