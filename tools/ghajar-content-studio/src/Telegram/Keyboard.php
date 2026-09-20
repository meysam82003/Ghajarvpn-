<?php
declare(strict_types=1);

namespace Ghajar\Studio\Telegram;

final class Keyboard
{
    /** @var array<int,array<int,array<string,string>>> */
    private array $rows = [];

    public static function make(): self
    {
        return new self();
    }

    /** @param array<int,array{0:string,1:string}> $buttons */
    public function row(array $buttons): self
    {
        $row = [];
        foreach ($buttons as [$label, $data]) {
            $row[] = ['text' => $label, 'callback_data' => $data];
        }
        if ($row !== []) {
            $this->rows[] = $row;
        }
        return $this;
    }

    public function button(string $label, string $data): self
    {
        return $this->row([[$label, $data]]);
    }

    public function url(string $label, string $url): self
    {
        $this->rows[] = [['text' => $label, 'url' => $url]];
        return $this;
    }

    public function isEmpty(): bool
    {
        return $this->rows === [];
    }

    /** @return array{inline_keyboard: array<int,array<int,array<string,string>>>} */
    public function toArray(): array
    {
        return ['inline_keyboard' => $this->rows];
    }
}
