package com.bahar.calltracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject

val Gold = Color(0xFFD4AF37)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

suspend fun call(o: JSONObject): JSONObject? =
    withContext(Dispatchers.IO) { try { Api.post(o) } catch (e: Exception) { null } }

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    var screen by remember { mutableStateOf("splash") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }

    fun startSync() {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")))
            } catch (e: Exception) {}
        }
        ctx.startForegroundService(Intent(ctx, SyncService::class.java))
        screen = "status"
    }

    LaunchedEffect(Unit) {
        delay(2600)
        if (prefs.getString("name", null) != null) startSync() else screen = "home"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            "splash" -> Splash()
            "home" -> Home(onLoggedIn = { n, p ->
                prefs.edit().putString("name", n).putString("pass", p).apply(); startSync()
            })
            "status" -> StatusScreen(prefs.getString("name", "") ?: "")
        }
    }
}

@Composable
fun Splash() {
    val full = "BAHAR DOĞAN DİŞ KLİNİĞİ"
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        for (i in 1..full.length) { shown = full.take(i); delay(2000L / full.length) }
    }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(shown, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PasswordDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(pw, { pw = it }, label = { Text("Şifre") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton({ onOk(pw) }) { Text("Tamam") } },
        dismissButton = { TextButton(onDismiss) { Text("İptal") } })
}

@Composable
fun Home(onLoggedIn: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var staff by remember { mutableStateOf(listOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            val r = call(JSONObject().put("action", "getStaff"))
            if (r != null && r.has("staff")) {
                val a = r.getJSONArray("staff")
                staff = List(a.length()) { a.getString(it) }
            } else msg = "Sunucuya bağlanılamadı"
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null, tint = Gold) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Satıcı ekle") }, { menu = false; dialog = "addname" })
                    DropdownMenuItem({ Text("Satıcı sil") }, { menu = false; dialog = "dellist" })
                }
            }
        }
        Spacer(Modifier.height(80.dp))
        Button({ list = !list }, Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)) {
            Text("SATICI", fontWeight = FontWeight.Bold)
        }
        if (list) staff.forEach { n ->
            TextButton({ pending = n; dialog = "login" }, Modifier.fillMaxWidth()) { Text(n, color = Gold) }
        }
        if (msg.isNotEmpty()) Text(msg, color = Color.Red)
    }

    when (dialog) {
        "login" -> PasswordDialog(pending, { dialog = "" }) { pw ->
            scope.launch {
                val r = call(JSONObject().put("action", "login").put("password", pw))
                dialog = ""
                if (r?.optBoolean("ok") == true) onLoggedIn(pending, pw)
                else msg = if (r == null) "Sunucuya bağlanılamadı" else "Yanlış şifre"
            }
        }
        "addname" -> AlertDialog({ dialog = "" }, title = { Text("Satıcı ekle") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("Ad Soyad") }) },
            confirmButton = { TextButton({ if (newName.isNotBlank()) dialog = "addpw" }) { Text("Kaydet") } },
            dismissButton = { TextButton({ dialog = "" }) { Text("İptal") } })
        "addpw" -> PasswordDialog("Yönetici şifresi", { dialog = "" }) { pw ->
            scope.launch {
                val r = call(JSONObject().put("action", "addStaff").put("password", pw).put("name", newName.trim()))
                msg = if (r?.optBoolean("ok") == true) "Eklendi" else "Yanlış şifre / hata"
                newName = ""; dialog = ""; refresh()
            }
        }
        "dellist" -> AlertDialog({ dialog = "" }, title = { Text("Satıcı sil") },
            text = { Column { staff.forEach { n ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(n)
                    IconButton({ pending = n; dialog = "delpw" }) { Icon(Icons.Default.Close, null, tint = Color.Red) }
                } } } },
            confirmButton = { TextButton({ dialog = "" }) { Text("Kapat") } })
        "delpw" -> PasswordDialog("Yönetici şifresi", { dialog = "" }) { pw ->
            scope.launch {
                val r = call(JSONObject().put("action", "removeStaff").put("password", pw).put("name", pending))
                msg = if (r?.optBoolean("ok") == true) "Silindi" else "Yanlış şifre / hata"
                dialog = ""; refresh()
            }
        }
    }
}

@Composable
fun StatusScreen(name: String) {
    val s by Status.text.collectAsState()
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(name, color = Gold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Text(s, color = Color.White, fontSize = 18.sp)
    }
}
