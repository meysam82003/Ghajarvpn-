#!/usr/bin/env python3
"""Strict migration of the reconstructed UI; abort on upstream drift.

Applied AFTER app/src overlay. Internal SSH/debug screens are retained, but
only Home/Store/Settings appear in bottom navigation.
"""
from pathlib import Path
import re
import sys

path = Path(sys.argv[1]) / 'app/src/main/java/net/gozar/app/MainActivity.kt'
s = path.read_text(encoding='utf-8')

def put(old, new):
    global s
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f'Unsafe UI migration: expected one anchor, found {count}: {old[:90]!r}')
    s = s.replace(old, new, 1)

# Update existing Material 3 semantic tokens; retain light and AMOLED choices.
for name, next_name in [('GnetDarkColors', 'GnetAmoledColors'), ('GnetAmoledColors', 'AppCyan')]:
    start = s.index(f'private val {name} = darkColorScheme(')
    end = s.index(f'private val {next_name}', start)
    theme = s[start:end]
    palette = dict(primary='00B978', onPrimary='050807', primaryContainer='14231F', onPrimaryContainer='E0FFF0', secondary='00A86B', onSecondary='050807', secondaryContainer='14231F', onSecondaryContainer='D9F9E8', background='050807', onBackground='E8F8F0', surface='0B1512', onSurface='E8F8F0', surfaceVariant='14231F', onSurfaceVariant='A8C3B6', surfaceBright='14231F', surfaceDim='050807', surfaceContainerLowest='050807', surfaceContainerLow='0B1512', surfaceContainer='101816', surfaceContainerHigh='14231F', surfaceContainerHighest='1A2D25', tertiary='24D98B', onTertiary='050807', tertiaryContainer='14231F', onTertiaryContainer='D9F9E8', inversePrimary='00A86B', surfaceTint='00B978', outline='426354', outlineVariant='253D32')
    if name == 'GnetAmoledColors':
        palette.update(background='000000', surface='000000', surfaceDim='000000', surfaceContainerLowest='000000')
    for key, value in palette.items():
        pattern = rf'(?m)^(\s*{re.escape(key)}\s*=\s*)Color\(0xFF[0-9A-Fa-f]{{6}}\)'
        theme, matches = re.subn(pattern, lambda m: m.group(1) + f'Color(0xFF{value})', theme)
        if matches != 1:
            raise RuntimeError(f'Unsafe palette migration: {name}.{key}: {matches} matches')
    s = s[:start] + theme + s[end:]
put('private val SplashBackground = Color(0xFF071B2E)', 'private val SplashBackground = Color(0xFF050807)')
put('window.navigationBarColor = if (dark) 0xFF071B2E.toInt() else 0xFFEEF3FA.toInt()', 'window.navigationBarColor = if (dark) 0xFF050807.toInt() else 0xFFEEF3FA.toInt()')

# Preserve every existing destination; move SSH and Debugger into Settings.
put('    var sshSubScreen by remember { mutableStateOf(false) }', '    var sshSubScreen by remember { mutableStateOf(false) }\n    var sshSettingsDetail by remember { mutableStateOf(false) }\n    var debugSettingsDetail by remember { mutableStateOf(false) }')
put('|| (onSettingsTab && (usageDetail ||', '|| (onSettingsTab && (sshSettingsDetail || debugSettingsDetail || usageDetail ||')
put('        page == PAGE_DEBUG -> "debugger"', '        onSettingsTab && sshSettingsDetail -> "ssh"\n        onSettingsTab && debugSettingsDetail -> "debugger"\n        page == PAGE_DEBUG -> "debugger"')
put('            page == PAGE_SSH && sshSubScreen -> Unit', '            sshSettingsDetail -> sshSettingsDetail = false\n            debugSettingsDetail -> debugSettingsDetail = false\n            page == PAGE_SSH && sshSubScreen -> Unit')
put('                    usageDetail -> "usage"', '                    sshSettingsDetail -> "ssh"\n                    debugSettingsDetail -> "debugger"\n                    usageDetail -> "usage"')
put('                        "usage" -> DataUsageScreen()', '                        "ssh" -> SshScreen(\n                            store = SshStore.get(LocalContext.current),\n                            onSubScreenChange = { sshSubScreen = it }\n                        )\n                        "debugger" -> ConfigDebuggerScreen(\n                            store = store, onSwitch = onSwitch,\n                            active = pagerState.settledPage == PAGE_SETTINGS && !pagerState.isScrollInProgress\n                        )\n                        "usage" -> DataUsageScreen()')
put('active = pagerState.settledPage == 2 && !pagerState.isScrollInProgress', 'active = pagerState.settledPage == PAGE_DEBUG && !pagerState.isScrollInProgress')
put('                            onOpenNetMon = { netMonDetail = true }', '                            onOpenNetMon = { netMonDetail = true },\n                            onOpenSsh = { sshSettingsDetail = true },\n                            onOpenDebugger = { debugSettingsDetail = true }')
put('    onOpenNetMon: () -> Unit,\n    modifier: Modifier = Modifier\n) {', '    onOpenNetMon: () -> Unit,\n    onOpenSsh: () -> Unit,\n    onOpenDebugger: () -> Unit,\n    modifier: Modifier = Modifier\n) {')
put('        SettingsHubCard(\n            icon = Icons.Filled.DataUsage,', '        SettingsHubCard(\n            icon = Icons.Filled.Terminal, title = t("ssh"),\n            subtitle = if (lang == Lang.FA) "مدیریت واقعی SSH" else "SSH management",\n            onClick = onOpenSsh\n        )\n        SettingsHubCard(\n            icon = Icons.Filled.BugReport, title = t("debugger_title"),\n            subtitle = if (lang == Lang.FA) "عیب‌یابی و آزمایش اتصال" else "Diagnostics and connection tests",\n            onClick = onOpenDebugger\n        )\n        SettingsHubCard(\n            icon = Icons.Filled.DataUsage,')

# Reorder five old bottom items into exactly Home, Store, Settings. Retain
# the two internal pager destinations to avoid destructive feature removal.
start = s.index('        bottomBar = {\n            NavigationBar(')
end = s.index('\n            }\n        }\n    ) { padding ->', start)
nav = s[start:end]
items = list(re.finditer(r'(?m)^                NavigationBarItem\(', nav))
if len(items) != 5:
    raise RuntimeError(f'Expected five legacy tabs, found {len(items)}')
blocks = [nav[m.start():items[i+1].start() if i+1 < len(items) else len(nav)] for i, m in enumerate(items)]
identified = {}
for block in blocks:
    matches = [label for label in ('shop', 'tunnel', 'home', 'tools', 'settings') if f'R.drawable.ic_royal_{label}' in block]
    if len(matches) != 1:
        raise RuntimeError('Cannot identify one of the existing navigation items')
    identified[matches[0]] = block
if len(identified) != 5:
    raise RuntimeError('Missing legacy navigation items')
s = s[:start] + nav[:items[0].start()] + identified['home'] + identified['shop'] + identified['settings'] + s[end:]
path.write_text(s, encoding='utf-8')
print('Applied emerald palette, three bottom tabs, and nested live SSH/Debugger with preserved legacy pages')
