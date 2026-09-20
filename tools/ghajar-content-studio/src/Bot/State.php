<?php
declare(strict_types=1);

namespace Ghajar\Studio\Bot;

use Ghajar\Studio\Core\Database;

/** Per-admin conversation state, persisted so a restart never loses context. */
final class State
{
    public function __construct(private int $userId)
    {
    }

    /** @return array{state:string,payload:array<string,mixed>,menu_message_id:?int} */
    public function load(): array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM conversation_state WHERE user_id = :u');
        $stmt->execute([':u' => $this->userId]);
        $row = $stmt->fetch();
        if ($row === false) {
            return ['state' => '', 'payload' => [], 'menu_message_id' => null];
        }
        $payload = json_decode((string) $row['payload'], true);
        return [
            'state'           => (string) $row['state'],
            'payload'         => is_array($payload) ? $payload : [],
            'menu_message_id' => $row['menu_message_id'] !== null ? (int) $row['menu_message_id'] : null,
        ];
    }

    public function current(): string
    {
        return $this->load()['state'];
    }

    /** @return array<string,mixed> */
    public function payload(): array
    {
        return $this->load()['payload'];
    }

    /** @param array<string,mixed> $payload */
    public function set(string $state, array $payload = []): void
    {
        $this->persist($state, $payload, $this->load()['menu_message_id']);
    }

    public function clear(): void
    {
        $this->persist('', [], $this->load()['menu_message_id']);
    }

    public function menuMessageId(): ?int
    {
        return $this->load()['menu_message_id'];
    }

    public function setMenuMessageId(?int $messageId): void
    {
        $current = $this->load();
        $this->persist($current['state'], $current['payload'], $messageId);
    }

    /** @param array<string,mixed> $payload */
    private function persist(string $state, array $payload, ?int $menuMessageId): void
    {
        Database::connect()->prepare(
            'INSERT INTO conversation_state (user_id, state, payload, menu_message_id, updated_at)
             VALUES (:u, :s, :p, :m, CURRENT_TIMESTAMP)
             ON CONFLICT(user_id) DO UPDATE SET state = excluded.state, payload = excluded.payload,
               menu_message_id = excluded.menu_message_id, updated_at = excluded.updated_at'
        )->execute([
            ':u' => $this->userId,
            ':s' => $state,
            ':p' => json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            ':m' => $menuMessageId,
        ]);
    }
}
