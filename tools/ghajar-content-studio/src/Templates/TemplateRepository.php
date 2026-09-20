<?php
declare(strict_types=1);

namespace Ghajar\Studio\Templates;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Support\Str;

final class TemplateRepository
{
    /** @return list<array<string,mixed>> */
    public function all(): array
    {
        $rows = Database::connect()
            ->query('SELECT * FROM templates ORDER BY is_default DESC, id ASC')
            ->fetchAll();
        return array_map([$this, 'hydrate'], $rows);
    }

    public function find(int $id): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM templates WHERE id = :id');
        $stmt->execute([':id' => $id]);
        $row = $stmt->fetch();
        return $row ? $this->hydrate($row) : null;
    }

    public function findBySlug(string $slug): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM templates WHERE slug = :slug');
        $stmt->execute([':slug' => $slug]);
        $row = $stmt->fetch();
        return $row ? $this->hydrate($row) : null;
    }

    public function default(): ?array
    {
        $row = Database::connect()
            ->query('SELECT * FROM templates ORDER BY is_default DESC, id ASC LIMIT 1')
            ->fetch();
        return $row ? $this->hydrate($row) : null;
    }

    /** @param array<string,mixed> $structure */
    public function create(string $name, array $structure, string $description = '', ?string $slug = null): int
    {
        $pdo  = Database::connect();
        $slug = $this->uniqueSlug($slug ?? Str::slugify($name));
        $stmt = $pdo->prepare(
            'INSERT INTO templates (name, slug, description, structure, is_default, is_system)
             VALUES (:name, :slug, :description, :structure, 0, 0)'
        );
        $stmt->execute([
            ':name'        => $name,
            ':slug'        => $slug,
            ':description' => $description,
            ':structure'   => $this->encode($structure),
        ]);
        return (int) $pdo->lastInsertId();
    }

    /** @param array<string,mixed> $structure */
    public function updateStructure(int $id, array $structure): void
    {
        $stmt = Database::connect()->prepare(
            'UPDATE templates SET structure = :structure, updated_at = CURRENT_TIMESTAMP WHERE id = :id'
        );
        $stmt->execute([':structure' => $this->encode($structure), ':id' => $id]);
    }

    public function rename(int $id, string $name): void
    {
        $stmt = Database::connect()->prepare(
            'UPDATE templates SET name = :name, updated_at = CURRENT_TIMESTAMP WHERE id = :id'
        );
        $stmt->execute([':name' => $name, ':id' => $id]);
    }

    public function duplicate(int $id): ?int
    {
        $template = $this->find($id);
        if ($template === null) {
            return null;
        }
        return $this->create(
            Str::truncate($template['name'] . ' (کپی)', 60),
            $template['structure'],
            (string) $template['description']
        );
    }

    public function delete(int $id): bool
    {
        return Database::transaction(function ($pdo) use ($id): bool {
            $stmt = $pdo->prepare('SELECT COUNT(*) FROM templates');
            $stmt->execute();
            if ((int) $stmt->fetchColumn() <= 1) {
                return false; // never leave the bot without a template
            }
            $stmt = $pdo->prepare('SELECT is_default FROM templates WHERE id = :id');
            $stmt->execute([':id' => $id]);
            $row = $stmt->fetch();
            if ($row === false) {
                return false;
            }
            $pdo->prepare('DELETE FROM templates WHERE id = :id')->execute([':id' => $id]);
            if ((int) $row['is_default'] === 1) {
                $pdo->exec('UPDATE templates SET is_default = 1 WHERE id = (SELECT MIN(id) FROM templates)');
            }
            return true;
        });
    }

    public function setDefault(int $id): void
    {
        Database::transaction(function ($pdo) use ($id): void {
            $pdo->exec('UPDATE templates SET is_default = 0');
            $pdo->prepare('UPDATE templates SET is_default = 1 WHERE id = :id')->execute([':id' => $id]);
        });
    }

    public function count(): int
    {
        return (int) Database::connect()->query('SELECT COUNT(*) FROM templates')->fetchColumn();
    }

    private function uniqueSlug(string $slug): string
    {
        $base = $slug;
        $i    = 1;
        while ($this->findBySlug($slug) !== null) {
            $slug = $base . '-' . (++$i);
        }
        return $slug;
    }

    /** @param array<string,mixed> $structure */
    private function encode(array $structure): string
    {
        return (string) json_encode($structure, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    /** @param array<string,mixed> $row */
    private function hydrate(array $row): array
    {
        $structure = json_decode((string) $row['structure'], true);
        $row['structure'] = is_array($structure) ? $structure : ['rules' => DefaultTemplates::DEFAULT_RULES, 'sections' => []];
        $row['id']         = (int) $row['id'];
        $row['is_default'] = (int) $row['is_default'];
        $row['is_system']  = (int) $row['is_system'];
        return $row;
    }
}
