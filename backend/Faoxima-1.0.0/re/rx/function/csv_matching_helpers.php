<?php

if (!function_exists('nmBuildFindInSetClause')) {
function nmBuildFindInSetClause(string $column, array $candidates, string $paramPrefix): array
{
    $candidates = array_values(array_unique(array_filter(array_map('strval', $candidates), function ($v) {
        return trim($v) !== '';
    })));
    if (!$candidates) {
        return ['', []];
    }
    $parts = [];
    $params = [];
    foreach ($candidates as $i => $value) {
        $key = ":{$paramPrefix}{$i}";
        $parts[] = "FIND_IN_SET({$key}, {$column}) > 0";
        $params[$key] = $value;
    }
    return ['(' . implode(' OR ', $parts) . ')', $params];
}
}

if (!function_exists('nmProductCategoryList')) {
function nmProductCategoryList(array $product): array
{
    $raw = (string) ($product['category'] ?? '');
    return array_values(array_filter(array_map('trim', explode(',', $raw)), function ($v) {
        return $v !== '';
    }));
}
}
