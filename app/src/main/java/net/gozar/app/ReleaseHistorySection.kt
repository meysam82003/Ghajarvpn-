package net.gozar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Release archive inside About; each selected release enters the same verified installer. */
@Composable
fun ReleaseHistorySection(installedVersion: String) {
    val scope=rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<UpdateChecker.Result.Available>()) }
    var next by remember { mutableStateOf<Int?>(1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun load() {
        val page=next ?: return
        if(busy)return
        busy=true;error=false
        scope.launch {
            try { val result=UpdateChecker.history(page);rows=(rows+result.releases).distinctBy { it.version };next=result.nextPage }
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){error=true}
            finally {busy=false}
        }
    }
    Card(modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(20.dp),
        colors=CardDefaults.cardColors(containerColor=ghajarColors.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("نسخه‌های قاجار",style=MaterialTheme.typography.titleLarge)
            Text("نسخهٔ نصب‌شده: $installedVersion",style=MaterialTheme.typography.bodyMedium)
            Text("نسخه‌های منتشرشده را ببین و از همین‌جا دریافت کن. نصب نسخهٔ قدیمی‌تر ممکن است توسط اندروید پذیرفته نشود.",style=MaterialTheme.typography.bodySmall)
            rows.forEach { release ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(release.version,style=MaterialTheme.typography.titleMedium)
                        Text(if(release.version==installedVersion)"نصب‌شده" else if(UpdateChecker.isNewer(release.version,installedVersion))"نسخهٔ جدید" else "نسخهٔ پیشین",style=MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick={GhajarUpdateFlow.offer(release)},enabled=release.version!=installedVersion) {Text("مشاهده و نصب")}
                }
            }
            if(error)Text("فهرست نسخه‌ها دریافت نشد؛ دوباره تلاش کن.",color=MaterialTheme.colorScheme.error)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(next!=null)OutlinedButton(onClick={load()},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text(if(rows.isEmpty())"نمایش نسخه‌های منتشرشده" else "نسخه‌های بیشتر")
            }
        }
    }
}
