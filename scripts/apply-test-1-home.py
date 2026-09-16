#!/usr/bin/env python3
"""Wire real Home servers and harden the three-destination navigation shell.

The legacy five-page pager remains as an implementation detail to preserve all
features, but only three root pages may be opened through the public bottom bar.
SSH and Debugger are accessible exclusively from Settings.
"""
from pathlib import Path
import sys

path = Path(sys.argv[1]) / 'app/src/main/java/net/gozar/app/MainActivity.kt'
source = path.read_text(encoding='utf-8')

def replace_once(old: str, new: str) -> None:
    global source
    n = source.count(old)
    if n != 1:
        raise RuntimeError(f'Unsafe Home/navigation migration: expected one anchor, got {n}: {old[:100]!r}')
    source = source.replace(old, new, 1)

replace_once(
    '                GhajarSelectedServerCard(selectedConfig, conn, onOpenPicker)',
    '''                GhajarSelectedServerCard(selectedConfig, conn, onOpenPicker)
                GhajarHomeServices(
                    configs = configs,
                    selectedId = selectedId,
                    activeId = activeCfgId,
                    connection = conn,
                    persian = lang == Lang.FA,
                    onSelectDisconnected = { store.setSelectedId(it.id) },
                    onOpenAll = onOpenPicker
                )'''
)

# A five-page HorizontalPager remained swipeable after the two unwanted bottom
# items were removed. Disable root swipes until pageCount is truly migrated;
# the explicitly wired Home/Store/Settings buttons remain fully functional.
replace_once(
    '            userScrollEnabled = !subScreenOpen,',
    '            userScrollEnabled = false, // Only the 3 public bottom destinations are reachable.'
)

# Allow tapping Settings again to return from its nested SSH/Debugger pages.
replace_once(
    '''                    onClick = {
                        usageDetail = false''',
    '''                    onClick = {
                        sshSettingsDetail = false
                        debugSettingsDetail = false
                        usageDetail = false'''
)

# Expose a normal, discoverable back arrow for the two nested screens. The
# existing system/predictive-back path is still present and unmodified.
replace_once(
    '''                        "preferences" -> BounceIconButton(onClick = { prefsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
''',
    '''                        "preferences" -> BounceIconButton(onClick = { prefsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "ssh" -> if (onSettingsTab) BounceIconButton(onClick = { sshSettingsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                        "debugger" -> if (onSettingsTab) BounceIconButton(onClick = { debugSettingsDetail = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
'''
)

path.write_text(source, encoding='utf-8')
print('Home uses actual servers; root swipe cannot expose hidden legacy tabs; SSH and debugger have Settings back navigation')
