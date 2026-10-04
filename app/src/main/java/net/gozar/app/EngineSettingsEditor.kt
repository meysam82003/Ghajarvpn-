package net.gozar.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import net.gozar.app.engine.CapabilityRegistry

@Composable
fun EngineSettingsEditor(protocol: String, values: MutableMap<String, String>) {
    val fields = CapabilityRegistry.settingsFor(protocol)
    if (fields.isEmpty()) return
    var expanded by remember(protocol) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text("تنظیمات پیشرفتهٔ این اتصال") }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { setting ->
            if (setting.type == EngineSettings.Type.BOOL) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(setting.label, Modifier.weight(1f))
                Switch(values[setting.key] in setOf("1", "true"), onCheckedChange = { values[setting.key] = it.toString() })
            } else OutlinedTextField(values[setting.key].orEmpty(), { values[setting.key] = it },
                label = { Text(setting.label) }, supportingText = { if (setting.hint.isNotBlank()) Text(setting.hint) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (setting.type in setOf(EngineSettings.Type.SECRET, EngineSettings.Type.PROXY)) PasswordVisualTransformation() else VisualTransformation.None)
        }
    }
}
