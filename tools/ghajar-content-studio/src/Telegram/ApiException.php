<?php
declare(strict_types=1);

namespace Ghajar\Studio\Telegram;

final class ApiException extends \RuntimeException
{
    public function __construct(
        string $message,
        public readonly int $errorCode = 0,
        public readonly ?string $method = null,
        public readonly bool $networkFailure = false,
    ) {
        parent::__construct($message, $errorCode);
    }

    /** Persian, user-facing explanation of the failure. */
    public function friendly(): string
    {
        if ($this->networkFailure) {
            return 'ارتباط با سرور تلگرام برقرار نشد: ' . $this->getMessage();
        }
        return match ($this->errorCode) {
            400 => 'درخواست نامعتبر بود: ' . $this->getMessage(),
            401 => 'توکن ربات نامعتبر است.',
            403 => 'ربات اجازه این عملیات را ندارد: ' . $this->getMessage(),
            409 => 'تداخل در تنظیمات دریافت پیام (Webhook/Polling).',
            429 => 'محدودیت نرخ تلگرام. کمی بعد دوباره تلاش کنید.',
            default => 'خطای تلگرام: ' . $this->getMessage(),
        };
    }
}
