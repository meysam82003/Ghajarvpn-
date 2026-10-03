#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
# Tests must compile in the pinned upstream module; the root Go module is Xray.
git clone -q https://github.com/appshubcc/Bettbox "$work/bettbox"
git -C "$work/bettbox" checkout --detach 3189346611caeba73aa87feaf708e4fd65115d16
git -C "$work/bettbox" apply "$root/native/plugin-mihomo/patches/profile-sandbox.patch"
core="$work/bettbox/core/Clash.Meta"
mkdir -p "$core/cmd/ghajar-plugin"
cp "$root"/native/plugin-mihomo/*.go "$core/cmd/ghajar-plugin/"
(cd "$core" && go test ./cmd/ghajar-plugin)
python3 -m unittest discover -s "$root/scripts/validation" -p 'test_plugin_manifest.py' -v
