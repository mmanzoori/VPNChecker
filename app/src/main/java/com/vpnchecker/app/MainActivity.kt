package com.vpnchecker.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

 data class VpnApp(val label: String, val packageName: String)
 data class Probe(val service: String, val ok: Boolean, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CheckerScreen(this) } }
    }
}

@Composable
private fun CheckerScreen(activity: MainActivity) {
    val context = activity
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(emptyList<VpnApp>()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf("VPNهای نصب‌شده را اسکن و موارد دلخواه را انتخاب کنید.") }
    var probes by remember { mutableStateOf(emptyList<Probe>()) }
    var busy by remember { mutableStateOf(false) }
    var activeVpn by remember { mutableStateOf(false) }

    fun scan() {
        apps = discoverVpnApps(context)
        selected = apps.map { it.packageName }.toSet()
        status = "${apps.size} برنامه VPN دارای سرویس استاندارد اندروید پیدا شد."
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("VPN Checker", style = MaterialTheme.typography.headlineMedium)
        Text("نسخه آزمایشی 0.1.1 • Samsung / Android 15–16")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { scan() }) { Text("اسکن برنامه‌های VPN") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("مجوز اتوماسیون") }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("VPNهای قابل انتخاب (${apps.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(checked = app.packageName in selected, onCheckedChange = {
                        selected = if (it) selected + app.packageName else selected - app.packageName
                    })
                }
                HorizontalDivider()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && selected.isNotEmpty(), onClick = {
                val first = apps.firstOrNull { it.packageName in selected }
                if (first != null) {
                    VpnAssistState.allowedPackages = selected
                    VpnAssistState.enabled = true
                    VpnAssistState.clickedForPackage = null
                    val launch = context.packageManager.getLaunchIntentForPackage(first.packageName)
                    if (launch != null) context.startActivity(launch)
                    status = "${first.label} باز شد. اگر اتصال خودکار انجام نشد، دستی Connect را بزنید؛ سپس به VPN Checker برگردید."
                }
            }) { Text("شروع با VPN اول") }
            OutlinedButton(enabled = !busy, onClick = {
                activeVpn = hasActiveVpn(context)
                scope.launch {
                    busy = true
                    probes = runProbes()
                    busy = false
                    status = if (activeVpn) "تونل VPN شناسایی شد؛ نتیجه تست شبکه ثبت شد." else "VPN فعال شناسایی نشد؛ نتیجه‌ها بدون تأیید تونل هستند."
                }
            }) { Text(if (busy) "در حال تست…" else "تست شبکه") }
        }
        Text(if (activeVpn) "وضعیت تونل: VPN فعال" else "وضعیت تونل: تأیید نشده")
        probes.forEach { p ->
            Text("${p.service}: ${if (p.ok) "موفق" else "ناموفق"} — ${p.detail}")
        }
        Text("فعلاً اتصال هر VPN را از داخل برنامه خودش برقرار کنید؛ سپس به این برنامه برگردید و تست تلگرام و اینستاگرام را اجرا کنید. اتصال خودکار همه کلاینت‌ها در نسخه بعدی و با آداپتورهای اختصاصی اضافه می‌شود.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun discoverVpnApps(context: Context): List<VpnApp> {
    val pm = context.packageManager
    val intent = Intent(android.net.VpnService.SERVICE_INTERFACE)
    val found = mutableMapOf<String, VpnApp>()
    @Suppress("DEPRECATION")
    val services = pm.queryIntentServices(intent, android.content.pm.PackageManager.GET_META_DATA)
    for (resolved in services) {
        val serviceInfo = resolved.serviceInfo ?: continue
        if (serviceInfo.permission != "android.permission.BIND_VPN_SERVICE") continue
        val packageName = serviceInfo.packageName
        if (packageName == context.packageName) continue
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            resolved.loadLabel(pm).toString().ifBlank { packageName }
        }
        found[packageName] = VpnApp(label, packageName)
    }
    return found.values.sortedBy { it.label.lowercase() }
}

private fun hasActiveVpn(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

private suspend fun runProbes(): List<Probe> = withContext(Dispatchers.IO) {
    listOf(
        probe("Telegram API", "https://api.telegram.org"),
        probe("Instagram", "https://www.instagram.com/")
    )
}

private fun probe(name: String, address: String): Probe {
    return try {
        val c = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        val code = c.responseCode
        c.disconnect()
        Probe(name, code in 200..399, "HTTP $code")
    } catch (e: Exception) {
        Probe(name, false, e.javaClass.simpleName + ": " + (e.message ?: "network error"))
    }
}
