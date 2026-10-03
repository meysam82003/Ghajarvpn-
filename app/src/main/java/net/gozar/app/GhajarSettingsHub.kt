package net.gozar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.gozar.app.engine.Availability
import net.gozar.app.engine.CoreManager
import net.gozar.app.engine.EngineId

/*
 * The navigation language of Settings and Tools: square-ish tiles in an
 * adaptive grid. Forms stay forms (sections of rows); only the places you go
 * to became tiles.
 */

/** One destination. [critical] tiles cannot be hidden by Personalization. */
@Immutable
data class SettingsTileSpec(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val accent: Color? = null,
    val badge: String? = null,
    val active: Boolean? = null,
    val critical: Boolean = false,
    val onClick: () -> Unit
)

/** Applies the user's order and hidden set; critical tiles always stay. */
fun arrangeTiles(tiles: List<SettingsTileSpec>, look: GhajarLook, showHidden: Boolean = false): List<SettingsTileSpec> {
    val rank = look.settingsOrder.withIndex().associate { it.value to it.index }
    val base = tiles.withIndex().sortedBy { (i, t) -> rank[t.id]?.toFloat() ?: (1000f + i) }.map { it.value }
    return if (showHidden) base else base.filter { it.critical || it.id !in look.settingsHidden }
}

/**
 * An adaptive grid: two columns on a phone, more as the width allows. Not a
 * lazy grid because it sits inside a scrolling page with a handful of tiles;
 * a lazy grid there would need a fixed height.
 */
@Composable
fun TileGrid(
    tiles: List<SettingsTileSpec>,
    modifier: Modifier = Modifier,
    minTileWidth: Dp = 150.dp
) {
    val look = LocalGhajarLook.current
    val gap = look.gridGap.dp
    val min = when (look.tileSize) { "compact" -> minTileWidth * 0.8f; "large" -> minTileWidth * 1.3f; else -> minTileWidth }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = ((maxWidth + gap) / (min + gap)).toInt().coerceIn(2, 5)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            tiles.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { SettingsTile(it, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun SettingsTile(tile: SettingsTileSpec, modifier: Modifier = Modifier) {
    val c = ghajarColors
    val look = LocalGhajarLook.current
    val accent = tile.accent ?: c.primary
    val compact = look.settingsLayout == "compact" || look.tileSize == "compact"
    val large = look.tileSize == "large"
    val shape = RoundedCornerShape(look.cardRadius.coerceAtMost(28).dp)
    Column(
        modifier
            .heightIn(min = if (compact) 88.dp else if (large) 132.dp else 110.dp)
            .clip(shape)
            .background(c.secondaryCard)
            .border(1.dp, c.border.copy(alpha = 0.6f), shape)
            .clickable { tile.onClick() }
            .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val box = if (compact) 34.dp else if (large) 46.dp else 40.dp
            val iconMod = when (look.iconStyle) {
                "filled" -> Modifier.size(box).clip(RoundedCornerShape(12.dp)).background(accent)
                "outline" -> Modifier.size(box).clip(RoundedCornerShape(12.dp)).border(1.5.dp, accent, RoundedCornerShape(12.dp))
                "plain" -> Modifier.size(box)
                else -> Modifier.size(box).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.15f))
            }
            val tint = if (look.iconStyle == "filled") c.onPrimary else accent
            Box(iconMod, contentAlignment = Alignment.Center) {
                val size = if (large) 24.dp else 20.dp
                if (tile.icon != null) Icon(tile.icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
                else if (tile.iconRes != null) Icon(androidx.compose.ui.res.painterResource(tile.iconRes), contentDescription = null,
                    tint = tint, modifier = Modifier.size(size))
            }
            Spacer(Modifier.weight(1f))
            tile.active?.let { on ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.good else c.textMuted.copy(alpha = 0.5f)))
            }
            tile.badge?.let { b ->
                Spacer(Modifier.size(6.dp))
                Text(
                    b,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            mixedText(tile.title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (look.boldTitles) FontWeight.Bold else FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!compact && !tile.subtitle.isNullOrBlank()) {
            Text(
                mixedText(tile.subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------- cores

/**
 * Where each core's settings live. Global settings are app-wide pages; the
 * rest are per profile and edited in that profile's form. Data, not code
 * paths: a new core adds one entry here and the hub shows it.
 */
object CoreSettingsRegistry {
    data class Link(val titleKey: String, val route: String)

    fun links(id: EngineId): List<Link> = when (id) {
        EngineId.XRAY -> listOf(Link("set_core_xray_global", "conn:xray"), Link("sec_dns", "conn:dns"), Link("routing", "conn:routing"))
        EngineId.ZEPTUN_TUN -> listOf(Link("set_core_zeptun_global", "conn:zeptun"))
        EngineId.OPENVPN -> listOf(Link("set_core_openvpn_global", "conn:openvpn"))
        EngineId.DNS_TUNNEL -> listOf(Link("dnsproto_title", "dnsproto"))
        EngineId.SINGBOX -> listOf(Link("sec_dns", "dnsproto"))
        else -> emptyList()
    }

    /** Profile-level options, edited in the add/edit form of that protocol. */
    fun profileNote(id: EngineId): String = when (id) {
        EngineId.XRAY -> "Transport · TLS / REALITY · uTLS · Fragment · Mux · SNI · ALPN"
        EngineId.SINGBOX -> "TUIC · Hysteria · AnyTLS · MASQUE · SSH · OpenConnect · NaiveProxy · ShadowTLS"
        EngineId.AETHER -> "MASQUE / MASQUE² / WireGuard / gool · scan · noize · exit country · SNI fragment"
        EngineId.TOR -> "Bridges: obfs4 · meek · webtunnel · snowflake"
        EngineId.IKEV2 -> "Server · EAP user / password · remote ID · MTU"
        EngineId.PSIPHON -> "Region · Oblivion options"
        EngineId.DNS_TUNNEL -> "Domain · public key · UDP / DoT / DoH · resolver · upstream"
        else -> ""
    }
}

private data class CoreRow(val id: EngineId, val name: String, val ready: Boolean, val experimental: Boolean, val why: String?,
                           val protocols: List<String>, val license: String, val integration: String)

@Composable
fun CoreHubScreen(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    var rows by remember { mutableStateOf<List<CoreRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) {
            CoreManager.engines.map { e ->
                val a = runCatching { e.availability(ctx) }.getOrNull()
                CoreRow(e.id, e.displayName, a is Availability.Available || a is Availability.Experimental,
                    a is Availability.Experimental, (a as? Availability.Missing)?.why ?: (a as? Availability.Experimental)?.why,
                    e.capabilities.protocols, e.capabilities.license, e.capabilities.integration)
            }
        }
    }
    val c = ghajarColors
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        ScreenHeader(title = t("set_tile_cores"), context = t("set_tile_cores_sub"))
        TileGrid(rows.map { r ->
            SettingsTileSpec(
                id = "core_${r.id}",
                title = r.name,
                subtitle = r.protocols.take(3).joinToString(" · "),
                icon = coreIcon(r.id),
                accent = if (r.ready) c.primary else c.textMuted,
                badge = localizeDigits("${r.protocols.size}", lang),
                active = r.ready,
                onClick = { onOpen("core:${r.id.name}") }
            )
        })
    }
}

@Composable
fun CoreDetailScreen(idName: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val t: (String) -> String = { Strings.get(lang, it) }
    val engine = CoreManager.engines.firstOrNull { it.id.name == idName }
    val c = ghajarColors
    var availability by remember { mutableStateOf<Availability?>(null) }
    LaunchedEffect(idName) { availability = withContext(Dispatchers.IO) { engine?.let { runCatching { it.availability(ctx) }.getOrNull() } } }
    if (engine == null) {
        SkinEmpty(title = t("set_core_missing"), modifier = modifier.padding(GhajarSpacing.lg))
        return
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(GhajarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(GhajarSpacing.md)
    ) {
        ScreenHeader(title = engine.displayName, context = engine.capabilities.integration)
        val a = availability
        Slab(spacing = 0.dp) {
            SlabRow(
                title = t("set_core_status"),
                subtitle = when (a) {
                    is Availability.Available -> t("about_engine_ready")
                    is Availability.Experimental -> a.why
                    is Availability.Missing -> a.why
                    null -> "…"
                },
                icon = coreIcon(engine.id),
                accent = when (a) { is Availability.Missing -> c.error; is Availability.Experimental -> c.warning; else -> c.good }
            )
            SlabDivider()
            SlabRow(title = t("set_core_license"), subtitle = engine.capabilities.license, icon = androidx.compose.material.icons.Icons.Filled.Gavel)
        }
        Rail(t("set_core_protocols"))
        Slab(spacing = 6.dp) {
            Text(
                mixedText(engine.capabilities.protocols.joinToString(" · ")),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
        val links = CoreSettingsRegistry.links(engine.id)
        if (links.isNotEmpty()) {
            Rail(t("set_core_global"))
            TileGrid(links.map { l ->
                SettingsTileSpec(id = l.route, title = t(l.titleKey), icon = androidx.compose.material.icons.Icons.Filled.Tune,
                    onClick = { onOpen(l.route) })
            })
        }
        val note = CoreSettingsRegistry.profileNote(engine.id)
        if (note.isNotBlank()) {
            Rail(t("set_core_profile"))
            Slab(spacing = 6.dp) {
                Text(t("set_core_profile_sub"), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(mixedText(note), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
            }
        }
    }
}

private fun coreIcon(id: EngineId): ImageVector = when (id) {
    EngineId.XRAY -> androidx.compose.material.icons.Icons.Filled.Bolt
    EngineId.PSIPHON -> androidx.compose.material.icons.Icons.Filled.Public
    EngineId.OPENVPN -> androidx.compose.material.icons.Icons.Filled.Security
    EngineId.IKEV2 -> androidx.compose.material.icons.Icons.Filled.Shield
    EngineId.TOR -> androidx.compose.material.icons.Icons.Filled.Hub
    EngineId.AETHER -> androidx.compose.material.icons.Icons.Filled.Cloud
    EngineId.ZEPTUN_TUN -> androidx.compose.material.icons.Icons.Filled.Memory
    EngineId.DNS_TUNNEL -> androidx.compose.material.icons.Icons.Filled.Dns
    EngineId.SINGBOX -> androidx.compose.material.icons.Icons.Filled.Layers
    EngineId.PLUGIN -> androidx.compose.material.icons.Icons.Filled.Layers
}
