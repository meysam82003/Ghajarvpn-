<?php
declare(strict_types=1);

namespace Ghajar\Studio\Drafts;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;

/**
 * Post numbers are only *reserved* at publish time, so creating and deleting
 * drafts never burns a number.
 */
final class PostNumbering
{
    public const MODE_AUTO   = 'auto';
    public const MODE_MANUAL = 'manual';
    public const MODE_NONE   = 'none';

    public function next(): int
    {
        return max(1, Settings::int('next_post_number', 1));
    }

    public function setNext(int $value): void
    {
        Settings::set('next_post_number', (string) max(1, $value));
    }

    public function enabled(): bool
    {
        return Settings::bool('numbering_enabled', true);
    }

    public function setEnabled(bool $enabled): void
    {
        Settings::set('numbering_enabled', $enabled ? '1' : '0');
    }

    public function isUsed(int $number): bool
    {
        $stmt = Database::connect()->prepare(
            'SELECT COUNT(*) FROM published_posts WHERE post_number = :n AND deleted_at IS NULL'
        );
        $stmt->execute([':n' => $number]);
        return (int) $stmt->fetchColumn() > 0;
    }

    /** The number a draft *would* get, without consuming anything. */
    public function preview(array $draft): ?int
    {
        $mode = (string) ($draft['numbering_mode'] ?? self::MODE_AUTO);
        if ($mode === self::MODE_NONE || !$this->enabled()) {
            return null;
        }
        if ($mode === self::MODE_MANUAL && $draft['post_number'] !== null) {
            return (int) $draft['post_number'];
        }
        return $this->next();
    }

    /**
     * Reserve the definitive number for a publish. Must run inside the
     * publishing transaction.
     */
    public function reserve(array $draft): ?int
    {
        $mode = (string) ($draft['numbering_mode'] ?? self::MODE_AUTO);
        if ($mode === self::MODE_NONE || !$this->enabled()) {
            return null;
        }
        if ($mode === self::MODE_MANUAL && $draft['post_number'] !== null) {
            return (int) $draft['post_number'];
        }
        $number = $this->next();
        while ($this->isUsed($number)) {
            $number++;
        }
        $this->setNext($number + 1);
        return $number;
    }

    /** Give a reserved number back when the publish did not happen. */
    public function rollback(int $number): void
    {
        if ($this->next() === $number + 1 && !$this->isUsed($number)) {
            $this->setNext($number);
        }
    }
}
