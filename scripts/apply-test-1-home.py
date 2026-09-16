#!/usr/bin/env python3
"""Strictly wire the real ConfigStore-backed server strip into Home."""
from pathlib import Path
import sys

path = Path(sys.argv[1]) / 'app/src/main/java/net/gozar/app/MainActivity.kt'
source = path.read_text(encoding='utf-8')
anchor = '                GhajarSelectedServerCard(selectedConfig, conn, onOpenPicker)'
replacement = '''                GhajarSelectedServerCard(selectedConfig, conn, onOpenPicker)
                GhajarHomeServices(
                    configs = configs,
                    selectedId = selectedId,
                    activeId = activeCfgId,
                    connection = conn,
                    persian = lang == Lang.FA,
                    onSelectDisconnected = { store.setSelectedId(it.id) },
                    onOpenAll = onOpenPicker
                )'''
if source.count(anchor) != 1 or 'GhajarHomeServices(' in source:
    raise RuntimeError('Home source changed; refusing to duplicate or silently miswire server strip')
path.write_text(source.replace(anchor, replacement, 1), encoding='utf-8')
print('Home now displays actual ConfigStore servers and delegates connected switching to the existing picker')
