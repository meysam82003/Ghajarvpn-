<section class="step-section is-active" id="step-install">
    <div class="card">
        <h2 class="card-title">
            <svg><use href="#i-database"/></svg>
            اطلاعات نصب
        </h2>

        <form id="installer-form" method="post" enctype="multipart/form-data">
            <input type="hidden" name="rx_action" value="install">

            <div class="field-step is-active" data-field-step="1">
                <div class="form-field">
                    <label class="form-label" for="admin_id"><svg><use href="#i-user"/></svg> آیدی عددی ادمین</label>
                    <input class="form-input" type="text" id="admin_id" name="admin_id" placeholder="ADMIN TELEGRAM #ID" value="<?php echo rx_escape_html($formValues['admin_id'] ?? ''); ?>" required>
                    <p class="form-hint">می‌توانید آیدی عددی خود را از ربات <code>@userinfobot</code> بگیرید.</p>
                </div>
            </div>

            <div class="field-step" data-field-step="2">
                <div class="form-field">
                    <label class="form-label" for="tg_bot_token"><svg><use href="#i-key"/></svg> توکن ربات تلگرام</label>
                    <input class="form-input" type="text" id="tg_bot_token" name="tg_bot_token" placeholder="123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11" value="<?php echo rx_escape_html($formValues['tg_bot_token'] ?? ''); ?>" required>
                    <p class="form-hint">توکن را از <code>@BotFather</code> دریافت کنید.</p>
                </div>
            </div>

            <div class="field-step" data-field-step="3">
                <div class="form-field">
                    <label class="form-label" for="database_username"><svg><use href="#i-user"/></svg> نام کاربری دیتابیس</label>
                    <input class="form-input" type="text" id="database_username" name="database_username" placeholder="DATABASE USERNAME" value="<?php echo rx_escape_html($formValues['database_username'] ?? ''); ?>" required>
                </div>
            </div>

            <div class="field-step" data-field-step="4">
                <div class="form-field">
                    <label class="form-label" for="database_password"><svg><use href="#i-lock"/></svg> رمز عبور دیتابیس</label>
                    <input class="form-input" type="text" id="database_password" name="database_password" placeholder="DATABASE PASSWORD" value="<?php echo rx_escape_html($formValues['database_password'] ?? ''); ?>" required>
                </div>
            </div>

            <div class="field-step" data-field-step="5">
                <div class="form-field">
                    <label class="form-label" for="database_name"><svg><use href="#i-database"/></svg> نام دیتابیس</label>
                    <input class="form-input" type="text" id="database_name" name="database_name" placeholder="DATABASE NAME" value="<?php echo rx_escape_html($formValues['database_name'] ?? ''); ?>" required>
                </div>
            </div>

            <div class="field-step" data-field-step="6">
                <div class="form-field">
                    <details>
                        <summary><svg><use href="#i-globe"/></svg> آدرس سورس ربات (پیشرفته)</summary>
                        <label for="bot_address_webhook">آدرس صفحه‌ی سورس ربات (نه installer)</label>
                        <input class="form-input" type="text" id="bot_address_webhook" name="bot_address_webhook" placeholder="https://yourdomain.com/path/index.php" value="<?php echo rx_escape_html($formValues['bot_address_webhook'] ?? $defaultWebhookAddress); ?>" required>
                    </details>
                </div>
            </div>

            <div class="field-step" data-field-step="7">
                <div class="alert alert-warning">
                    <svg><use href="#i-warn"/></svg>
                    <div>
                        <strong>هشدار:</strong> پس از نصب موفقیت‌آمیز، پوشه‌ی <code>installer</code> به‌صورت <strong>خودکار حذف</strong> خواهد شد. این کار برای حفظ امنیت هاست انجام می‌شود.
                    </div>
                </div>
            </div>

            <div class="field-progress">
                <span>فیلد <span id="install-progress-text">1</span> از 7</span>
                <div class="field-progress-bar">
                    <div class="field-progress-fill" id="field-progress-fill" style="width: 14%"></div>
                </div>
            </div>

            <div class="btn-row">
                <button type="button" class="btn btn-secondary" id="install-prev-btn" hidden>
                    <svg><use href="#i-arrow-right"/></svg>
                    فیلد قبل
                </button>
                <button type="button" class="btn btn-primary btn-grow" id="install-next-btn">
                    فیلد بعد
                    <svg><use href="#i-arrow-left"/></svg>
                </button>
                <button type="submit" class="btn btn-primary btn-grow" id="install-submit" hidden>
                    <svg><use href="#i-rocket"/></svg>
                    شروع نصب ربات
                </button>
            </div>
        </form>
    </div>
</section>
