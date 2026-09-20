<?php
declare(strict_types=1);

namespace Ghajar\Studio\Drafts;

use Ghajar\Studio\Core\Database;

final class DraftRepository
{
    public const STATUS_DRAFT     = 'draft';
    public const STATUS_PUBLISHED = 'published';
    public const STATUS_FAILED    = 'failed';
    public const STATUS_UNKNOWN   = 'unknown';

    /**
     * @param array<string,mixed> $content
     * @param array<string,mixed> $meta
     */
    public function create(int $ownerId, string $sourceText, string $sourceHtml, array $content, ?int $templateId, array $meta = []): int
    {
        $pdo  = Database::connect();
        $stmt = $pdo->prepare(
            'INSERT INTO drafts (owner_id, title, template_id, source_text, source_html, source_meta, content, rendered)
             VALUES (:owner, :title, :template, :text, :html, :meta, :content, \'\')'
        );
        $stmt->execute([
            ':owner'    => $ownerId,
            ':title'    => (string) ($content['title'] ?? ''),
            ':template' => $templateId,
            ':text'     => $sourceText,
            ':html'     => $sourceHtml,
            ':meta'     => $this->encode($meta),
            ':content'  => $this->encode($content),
        ]);
        return (int) $pdo->lastInsertId();
    }

    public function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM drafts WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $row = $stmt->fetch();
        return $row ? $this->hydrate($row) : null;
    }

    /** @return list<array<string,mixed>> */
    public function listByStatus(string $status, int $limit = 20, int $offset = 0): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM drafts WHERE status = :status ORDER BY updated_at DESC, id DESC LIMIT :limit OFFSET :offset'
        );
        $stmt->bindValue(':status', $status);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->bindValue(':offset', $offset, \PDO::PARAM_INT);
        $stmt->execute();
        return array_map([$this, 'hydrate'], $stmt->fetchAll());
    }

    public function countByStatus(string $status): int
    {
        $stmt = Database::connect()->prepare('SELECT COUNT(*) FROM drafts WHERE status = :s');
        $stmt->execute([':s' => $status]);
        return (int) $stmt->fetchColumn();
    }

    public function countAll(): int
    {
        return (int) Database::connect()->query('SELECT COUNT(*) FROM drafts')->fetchColumn();
    }

    /** @return list<array<string,mixed>> */
    public function search(string $term, int $limit = 20): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM drafts WHERE title LIKE :t OR source_text LIKE :t ORDER BY id DESC LIMIT :limit'
        );
        $stmt->bindValue(':t', '%' . $term . '%');
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();
        return array_map([$this, 'hydrate'], $stmt->fetchAll());
    }

    /** @param array<string,mixed> $content */
    public function updateContent(int $id, array $content, string $note = ''): void
    {
        $this->snapshot($id, $note);
        $stmt = Database::connect()->prepare(
            'UPDATE drafts SET content = :content, title = :title, updated_at = CURRENT_TIMESTAMP WHERE id = :id'
        );
        $stmt->execute([
            ':content' => $this->encode($content),
            ':title'   => (string) ($content['title'] ?? ''),
            ':id'      => $id,
        ]);
    }

    public function setTemplate(int $id, int $templateId): void
    {
        $this->snapshot($id, 'تغییر قالب');
        Database::connect()
            ->prepare('UPDATE drafts SET template_id = :t, updated_at = CURRENT_TIMESTAMP WHERE id = :id')
            ->execute([':t' => $templateId, ':id' => $id]);
    }

    public function setRendered(int $id, string $rendered): void
    {
        Database::connect()
            ->prepare('UPDATE drafts SET rendered = :r, updated_at = CURRENT_TIMESTAMP WHERE id = :id')
            ->execute([':r' => $rendered, ':id' => $id]);
    }

    public function setNumber(int $id, ?int $number, string $mode): void
    {
        Database::connect()
            ->prepare('UPDATE drafts SET post_number = :n, numbering_mode = :m, updated_at = CURRENT_TIMESTAMP WHERE id = :id')
            ->execute([':n' => $number, ':m' => $mode, ':id' => $id]);
    }

    public function setStatus(int $id, string $status): void
    {
        Database::connect()
            ->prepare('UPDATE drafts SET status = :s, updated_at = CURRENT_TIMESTAMP WHERE id = :id')
            ->execute([':s' => $status, ':id' => $id]);
    }

    public function markPublished(int $id, string $channel, int $messageId, ?int $number): void
    {
        Database::connect()->prepare(
            'UPDATE drafts SET status = \'published\', channel = :c, message_id = :m, post_number = :n,
             published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = :id'
        )->execute([':c' => $channel, ':m' => $messageId, ':n' => $number, ':id' => $id]);
    }

    public function delete(int $id): void
    {
        Database::connect()->prepare('DELETE FROM drafts WHERE id = :id')->execute([':id' => $id]);
    }

    /** Store the current state so the admin can undo an edit. */
    public function snapshot(int $id, string $note = ''): void
    {
        $draft = $this->find($id);
        if ($draft === null) {
            return;
        }
        $payload = [
            'content'     => $draft['content'],
            'template_id' => $draft['template_id'],
            'post_number' => $draft['post_number'],
            'rendered'    => $draft['rendered'],
        ];
        Database::connect()
            ->prepare('INSERT INTO draft_revisions (draft_id, payload, note) VALUES (:d, :p, :n)')
            ->execute([':d' => $id, ':p' => $this->encode($payload), ':n' => $note]);
        // keep only the latest 20 revisions per draft
        Database::connect()->prepare(
            'DELETE FROM draft_revisions WHERE draft_id = :d AND id NOT IN
             (SELECT id FROM draft_revisions WHERE draft_id = :d ORDER BY id DESC LIMIT 20)'
        )->execute([':d' => $id]);
    }

    /** @return list<array<string,mixed>> */
    public function revisions(int $draftId, int $limit = 10): array
    {
        $stmt = Database::connect()->prepare(
            'SELECT * FROM draft_revisions WHERE draft_id = :d ORDER BY id DESC LIMIT :limit'
        );
        $stmt->bindValue(':d', $draftId, \PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, \PDO::PARAM_INT);
        $stmt->execute();
        return $stmt->fetchAll();
    }

    public function restoreLatestRevision(int $draftId): bool
    {
        $revision = Database::connect()->prepare(
            'SELECT * FROM draft_revisions WHERE draft_id = :d ORDER BY id DESC LIMIT 1'
        );
        $revision->execute([':d' => $draftId]);
        $row = $revision->fetch();
        if ($row === false) {
            return false;
        }
        $payload = json_decode((string) $row['payload'], true);
        if (!is_array($payload)) {
            return false;
        }
        Database::connect()->prepare(
            'UPDATE drafts SET content = :c, template_id = :t, post_number = :n, rendered = :r,
             updated_at = CURRENT_TIMESTAMP WHERE id = :id'
        )->execute([
            ':c'  => $this->encode((array) ($payload['content'] ?? [])),
            ':t'  => $payload['template_id'] ?? null,
            ':n'  => $payload['post_number'] ?? null,
            ':r'  => (string) ($payload['rendered'] ?? ''),
            ':id' => $draftId,
        ]);
        Database::connect()->prepare('DELETE FROM draft_revisions WHERE id = :id')->execute([':id' => $row['id']]);
        return true;
    }

    /** Claim an exclusive publish token so a double tap cannot post twice. */
    public function claimPublishToken(int $id): ?string
    {
        return Database::transaction(function ($pdo) use ($id): ?string {
            $stmt = $pdo->prepare('SELECT status, publish_token FROM drafts WHERE id = :id');
            $stmt->execute([':id' => $id]);
            $row = $stmt->fetch();
            if ($row === false || $row['status'] === self::STATUS_PUBLISHED) {
                return null;
            }
            if (!empty($row['publish_token'])) {
                return null; // a publish attempt is already in flight
            }
            $token = bin2hex(random_bytes(8));
            $pdo->prepare('UPDATE drafts SET publish_token = :t, updated_at = CURRENT_TIMESTAMP WHERE id = :id')
                ->execute([':t' => $token, ':id' => $id]);
            return $token;
        });
    }

    public function releasePublishToken(int $id): void
    {
        Database::connect()
            ->prepare('UPDATE drafts SET publish_token = NULL WHERE id = :id')
            ->execute([':id' => $id]);
    }

    private function encode(array $data): string
    {
        return (string) json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    /** @param array<string,mixed> $row */
    private function hydrate(array $row): array
    {
        $row['id']          = (int) $row['id'];
        $row['template_id'] = $row['template_id'] !== null ? (int) $row['template_id'] : null;
        $row['post_number'] = $row['post_number'] !== null ? (int) $row['post_number'] : null;
        $row['message_id']  = $row['message_id'] !== null ? (int) $row['message_id'] : null;
        $content            = json_decode((string) $row['content'], true);
        $row['content']     = is_array($content) ? $content : [];
        $meta               = json_decode((string) $row['source_meta'], true);
        $row['source_meta'] = is_array($meta) ? $meta : [];
        return $row;
    }
}
