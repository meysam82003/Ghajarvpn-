<?php
declare(strict_types=1);

namespace Ghajar\Studio\Github;

use Ghajar\Studio\Core\Database;
use Ghajar\Studio\Core\Settings;
use Ghajar\Studio\Support\Logger;

final class GithubClient
{
    public function __construct(private ?string $repo = null, private int $timeout = 20)
    {
    }

    public function repo(): string
    {
        return $this->repo ?? Settings::get('github_repo', 'meysam82003/Ghajarvpn-');
    }

    /**
     * Fetch the newest releases. Draft/prerelease entries are returned too but
     * flagged, so the admin decides explicitly.
     *
     * @return list<array<string,mixed>>
     * @throws GithubException
     */
    public function releases(int $perPage = 10): array
    {
        $url  = sprintf('https://api.github.com/repos/%s/releases?per_page=%d', $this->repo(), max(1, min(30, $perPage)));
        $data = $this->request($url);
        if (!is_array($data)) {
            throw new GithubException('پاسخ نامعتبر از گیت‌هاب دریافت شد.');
        }
        $out = [];
        foreach ($data as $release) {
            if (!is_array($release)) {
                continue;
            }
            $out[] = $this->normalize($release);
        }
        return $out;
    }

    /** @throws GithubException */
    public function latestStable(): ?array
    {
        foreach ($this->releases() as $release) {
            if (!$release['is_draft'] && !$release['is_prerelease']) {
                return $release;
            }
        }
        return null;
    }

    /** @param array<string,mixed> $release */
    private function normalize(array $release): array
    {
        $assets = [];
        foreach ((array) ($release['assets'] ?? []) as $asset) {
            if (!is_array($asset)) {
                continue;
            }
            $name = (string) ($asset['name'] ?? '');
            $assets[] = [
                'name'         => $name,
                'size'         => (int) ($asset['size'] ?? 0),
                'download_url' => (string) ($asset['browser_download_url'] ?? ''),
                'is_apk'       => str_ends_with(strtolower($name), '.apk'),
                'arch'         => $this->detectArch($name),
            ];
        }
        return [
            'tag'           => (string) ($release['tag_name'] ?? ''),
            'name'          => (string) ($release['name'] ?? ($release['tag_name'] ?? '')),
            'published_at'  => (string) ($release['published_at'] ?? ''),
            'html_url'      => (string) ($release['html_url'] ?? ''),
            'body'          => (string) ($release['body'] ?? ''),
            'is_draft'      => (bool) ($release['draft'] ?? false),
            'is_prerelease' => (bool) ($release['prerelease'] ?? false),
            'assets'        => $assets,
        ];
    }

    private function detectArch(string $name): string
    {
        $lower = strtolower($name);
        foreach (['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86', 'universal'] as $arch) {
            if (str_contains($lower, $arch)) {
                return $arch;
            }
        }
        return '';
    }

    /** @throws GithubException */
    private function request(string $url): mixed
    {
        $headers = [
            'Accept: application/vnd.github+json',
            'User-Agent: GhajarContentStudio/1.0',
            'X-GitHub-Api-Version: 2022-11-28',
        ];
        $token = Settings::get('github_token');
        if ($token !== '') {
            $headers[] = 'Authorization: Bearer ' . $token;
        }

        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->timeout,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_HTTPHEADER     => $headers,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_FOLLOWLOCATION => true,
        ]);
        $body   = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        $errNo  = curl_errno($ch);
        $errMsg = curl_error($ch);
        curl_close($ch);

        if ($body === false || $errNo !== 0) {
            Logger::error('GitHub network failure', ['curl' => $errNo]);
            throw new GithubException('ارتباط با گیت‌هاب برقرار نشد: ' . ($errMsg !== '' ? $errMsg : 'خطای شبکه'));
        }
        if ($status === 404) {
            throw new GithubException('مخزن یافت نشد. نام مخزن را در تنظیمات بررسی کنید.');
        }
        if ($status === 403) {
            throw new GithubException('محدودیت نرخ گیت‌هاب فعال شد. بعداً تلاش کنید.');
        }
        if ($status < 200 || $status >= 300) {
            throw new GithubException('گیت‌هاب پاسخ ' . $status . ' داد.');
        }
        return json_decode((string) $body, true);
    }

    /** Cache a release row so the last known data survives a GitHub outage. */
    public function cache(array $release): void
    {
        Database::connect()->prepare(
            'INSERT INTO github_releases (tag, name, published_at, is_prerelease, is_draft, html_url, assets, fetched_at)
             VALUES (:tag, :name, :published, :pre, :draft, :url, :assets, CURRENT_TIMESTAMP)
             ON CONFLICT(tag) DO UPDATE SET name = excluded.name, published_at = excluded.published_at,
               is_prerelease = excluded.is_prerelease, is_draft = excluded.is_draft,
               html_url = excluded.html_url, assets = excluded.assets, fetched_at = excluded.fetched_at'
        )->execute([
            ':tag'       => $release['tag'],
            ':name'      => $release['name'],
            ':published' => $release['published_at'],
            ':pre'       => $release['is_prerelease'] ? 1 : 0,
            ':draft'     => $release['is_draft'] ? 1 : 0,
            ':url'       => $release['html_url'],
            ':assets'    => json_encode($release['assets'], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
        ]);
    }

    public function cachedByTag(string $tag): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM github_releases WHERE tag = :t');
        $stmt->execute([':t' => $tag]);
        $row = $stmt->fetch();
        if ($row === false) {
            return null;
        }
        $assets = json_decode((string) $row['assets'], true);
        return [
            'tag'           => (string) $row['tag'],
            'name'          => (string) $row['name'],
            'published_at'  => (string) $row['published_at'],
            'html_url'      => (string) $row['html_url'],
            'body'          => '',
            'is_draft'      => (bool) $row['is_draft'],
            'is_prerelease' => (bool) $row['is_prerelease'],
            'assets'        => is_array($assets) ? $assets : [],
        ];
    }
}
