package net.gozar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Faoxima 1.0.0 support: list, departments, create, thread, reply and close. */
@Composable
fun GhajarTickets(api: GhajarStoreApi) {
    val scope = rememberCoroutineScope()
    var tickets by remember { mutableStateOf(emptyList<JSONObject>()) }
    val rowsSaver = Saver<List<JSONObject>, String>(save = { org.json.JSONArray(it).toString() }, restore = { raw -> val a = org.json.JSONArray(raw); (0 until a.length()).map { a.getJSONObject(it) } })
    var departments by rememberSaveable(stateSaver = rowsSaver) { mutableStateOf(emptyList<JSONObject>()) }
    var selectedDepartment by rememberSaveable { mutableIntStateOf(0) }
    val jsonSaver = Saver<JSONObject?, String>(save = { it?.toString().orEmpty() }, restore = { it.takeIf(String::isNotBlank)?.let(::JSONObject) })
    var thread by rememberSaveable(stateSaver = jsonSaver) { mutableStateOf<JSONObject?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    fun rows(o: JSONObject, key: String): List<JSONObject> {
        val a = o.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
    suspend fun reload() {
        val t = thread
        if (t != null) thread = api.support("ticket_thread", params = mapOf("t" to t.getString("tracking")))
        else {
            val result = api.support("tickets", params = mapOf("page" to page.toString()))
            tickets = rows(result, "items")
            totalPages = result.optInt("total_pages", 1).coerceAtLeast(1)
        }
    }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "دریافت پشتیبانی ناموفق بود" }
            finally { busy = false }
        }
    }
    LaunchedEffect(page) {
        busy = true
        try { reload() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { busy = false }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("پشتیبانی و تیکت", style = MaterialTheme.typography.titleLarge)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { run { reload() } }) { Text("بروزرسانی") }
            if (thread != null || creating) TextButton(enabled = !busy, onClick = {
                thread = null; creating = false; message = ""; run { reload() }
            }) { Text("بازگشت") }
            else Button(enabled = !busy, onClick = { run {
                departments = rows(api.support("ticket_departments"), "items")
                selectedDepartment = departments.firstOrNull()?.optInt("id") ?: 0
                subject = ""; message = ""; creating = true
            } }) { Text("تیکت جدید") }
        }
        if (creating) {
            departments.forEach { department ->
                FilterChip(selected = selectedDepartment == department.optInt("id"), enabled = !busy,
                    onClick = { selectedDepartment = department.optInt("id") }, label = { Text(department.optString("name")) })
            }
            OutlinedTextField(subject, { subject = it.take(150) }, label = { Text("موضوع") }, modifier = Modifier.fillMaxWidth())
        } else if (thread == null) {
            if (tickets.isEmpty() && !busy) Text("هنوز تیکتی ثبت نکرده‌ای.")
            tickets.forEach { ticket ->
                OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { run {
                    thread = api.support("ticket_thread", params = mapOf("t" to ticket.getString("tracking")))
                    message = ""
                } }) {
                    Column { Text(ticket.optString("subject").ifBlank { ticket.optString("tracking") })
                        Text("${ticket.optString("department")} • ${if (ticket.optBoolean("open")) "باز" else "بسته"}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy && page > 1, onClick = { page-- }) { Text("قبلی") }
                Text("$page / $totalPages")
                TextButton(enabled = !busy && page < totalPages, onClick = { page++ }) { Text("بعدی") }
            }
        }
        thread?.let { t ->
            Text(t.optString("subject"), style = MaterialTheme.typography.titleMedium)
            rows(t, "messages").forEach { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (m.optString("sender") == "admin") "پشتیبانی" else "شما", style = MaterialTheme.typography.labelLarge)
                        m.optJSONObject("reply_to")?.let { Text("↪ ${it.optString("body")}", style = MaterialTheme.typography.bodySmall) }
                        Text(m.optString("body"))
                        if (m.optInt("media_count") > 0) Text("پیوست: در پنل کامل پشتیبانی مشاهده کن.")
                        Text(m.optString("time"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (t.optBoolean("open")) OutlinedButton(enabled = !busy, onClick = { run {
                api.support("ticket_close", JSONObject().put("t", t.getString("tracking")))
                reload()
            } }) { Text("بستن تیکت") }
        }
        if (creating || thread?.optBoolean("open") == true) {
            OutlinedTextField(message, { message = it.take(4000) }, label = { Text("متن پیام") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && message.isNotBlank() && (!creating || selectedDepartment > 0), onClick = { run {
                if (creating) {
                    val created = api.support("ticket_create", JSONObject().put("department_id", selectedDepartment)
                        .put("subject", subject).put("text", message))
                    thread = api.support("ticket_thread", params = mapOf("t" to created.getString("tracking")))
                    creating = false
                } else {
                    api.support("ticket_reply", JSONObject().put("t", thread!!.getString("tracking")).put("text", message))
                    reload()
                }
                message = ""
            } }) { Text("ارسال") }
        }
    }
}
