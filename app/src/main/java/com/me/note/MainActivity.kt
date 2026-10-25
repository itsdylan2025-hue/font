package com.me.note

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

val ACC = Color(0xFFE6A800)
val SUB = Color(0xFF8E8E93)
var SF: FontFamily = FontFamily.Default
var SFB: FontFamily = FontFamily.Default
var EMO: FontFamily = FontFamily.Default
val FOLDERS = listOf("Home" to "🏡", "Personal" to "🦊", "Study" to "📚", "Travel" to "✈️", "Work" to "💼")

data class Note(val id: Long, val text: String, val folder: String, val pinned: Boolean, val deleted: Boolean, val time: Long)

class Store(c: Context) {
    private val p = c.getSharedPreferences("note", 0)
    fun load(): List<Note> = runCatching {
        val a = JSONArray(p.getString("n", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Note(o.getLong("id"), o.getString("t"), o.getString("f"), o.getBoolean("p"), o.getBoolean("d"), o.getLong("m"))
        }
    }.getOrDefault(emptyList())
    fun save(l: List<Note>) = p.edit().putString("n", JSONArray(l.map {
        JSONObject().put("id", it.id).put("t", it.text).put("f", it.folder).put("p", it.pinned).put("d", it.deleted).put("m", it.time)
    }).toString()).apply()
}

fun Context.font(n: String): FontFamily? = runCatching { FontFamily(Typeface.createFromAsset(assets, n)) }.getOrNull()

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        font("sf_regular.ttf")?.let { SF = it; SFB = it }
        font("sf_bold.ttf")?.let { SFB = it }
        font("emoji.ttf")?.let { EMO = it }
        val store = Store(this)
        setContent { App(store) }
    }
}

@Composable
fun T(s: String, size: Int, c: Color, b: Boolean = false, e: Boolean = false, modifier: Modifier = Modifier) =
    Text(s, modifier, c, size.sp, fontFamily = if (e) EMO else if (b) SFB else SF,
        fontWeight = if (b && SFB == FontFamily.Default) FontWeight.Bold else null,
        maxLines = 1, overflow = TextOverflow.Ellipsis)

fun ofFolder(n: List<Note>, f: String) = when (f) {
    "All Notes" -> n.filter { !it.deleted }
    "Pinned" -> n.filter { it.pinned && !it.deleted }
    "Recently Deleted" -> n.filter { it.deleted }
    else -> n.filter { it.folder == f && !it.deleted }
}

fun stamp(t: Long) = SimpleDateFormat(if (System.currentTimeMillis() - t < 86400000L * 6) "h:mm a" else "d MMM", Locale.US).format(Date(t))

@Composable
fun App(store: Store) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val tx = if (dark) Color.White else Color.Black
    var notes by remember { mutableStateOf(store.load()) }
    var folder by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<Note?>(null) }
    var q by remember { mutableStateOf("") }
    fun save(l: List<Note>) { notes = l; store.save(l) }
    BackHandler(folder != null) { folder = null; q = "" }
    val e = edit
    if (e != null) {
        Editor(e, tx, bg) { n ->
            save(notes.filter { it.id != n.id } + if (n.text.isBlank()) emptyList() else listOf(n))
            edit = null
        }
    } else {
        val f = folder
        Column(Modifier.fillMaxSize().background(bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Spacer(Modifier.height(24.dp))
            if (f == null) {
                T("Folders", 34, tx, b = true)
                Spacer(Modifier.height(12.dp))
                listOf(listOf("All Notes" to "📁") + FOLDERS, listOf("Pinned" to "📌", "Recently Deleted" to "🗑️")).forEach { grp ->
                    Column(Modifier.padding(bottom = 16.dp).clip(RoundedCornerShape(12.dp)).background(card)) {
                        grp.forEach { (name, em) ->
                            Row(Modifier.fillMaxWidth().clickable { folder = name }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                T(em, 22, tx, e = true); Spacer(Modifier.width(12.dp)); T(name, 17, tx)
                                Spacer(Modifier.weight(1f)); T("${ofFolder(notes, name).size}  ›", 17, SUB)
                            }
                        }
                    }
                }
            } else {
                T("‹ Folders", 17, ACC, modifier = Modifier.clickable { folder = null; q = "" })
                Spacer(Modifier.height(8.dp))
                T(f, 34, tx, b = true)
                Spacer(Modifier.height(12.dp))
                BasicTextField(q, { q = it }, textStyle = TextStyle(color = tx, fontSize = 17.sp, fontFamily = SF),
                    cursorBrush = SolidColor(ACC),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22767680)).padding(10.dp),
                    decorationBox = { inner -> Box { if (q.isEmpty()) T("Search", 17, SUB); inner() } })
                val list = ofFolder(notes, f).filter { it.text.contains(q, true) }.sortedByDescending { it.time }
                val t0 = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val d = 86400000L
                val groups = listOf(
                    "Pinned" to list.filter { it.pinned },
                    "Today" to list.filter { !it.pinned && it.time >= t0 },
                    "Yesterday" to list.filter { !it.pinned && it.time in (t0 - d) until t0 },
                    "Earlier" to list.filter { !it.pinned && it.time < t0 - d }
                ).filter { it.second.isNotEmpty() }
                LazyColumn(Modifier.weight(1f)) {
                    items(groups) { (name, ns) ->
                        T(name, 20, tx, b = true, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(card)) {
                            ns.forEach { n ->
                                val lines = n.text.lines()
                                Column(Modifier.fillMaxWidth().clickable { edit = n }.padding(14.dp)) {
                                    T(lines.first().ifBlank { "New Note" }, 17, tx, b = true)
                                    T(stamp(n.time) + "  " + (lines.drop(1).firstOrNull { it.isNotBlank() } ?: "No additional text"), 14, SUB)
                                    Row {
                                        T(FOLDERS.firstOrNull { it.first == n.folder }?.second ?: "", 14, tx, e = true)
                                        T(" " + n.folder, 14, SUB)
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    T("${list.size} Notes", 13, tx, modifier = Modifier.align(Alignment.Center))
                    T("New", 17, ACC, b = true, modifier = Modifier.align(Alignment.CenterEnd).clickable {
                        val now = System.currentTimeMillis()
                        edit = Note(now, "", if (FOLDERS.any { it.first == f }) f else "Home", false, false, now)
                    })
                }
            }
        }
    }
}

@Composable
fun Editor(n: Note, tx: Color, bg: Color, onClose: (Note) -> Unit) {
    var t by remember { mutableStateOf(n.text) }
    var pin by remember { mutableStateOf(n.pinned) }
    var del by remember { mutableStateOf(n.deleted) }
    var f by remember { mutableStateOf(n.folder) }
    fun out() = n.copy(text = t, pinned = pin, deleted = del, folder = f, time = if (t != n.text) System.currentTimeMillis() else n.time)
    BackHandler { onClose(out()) }
    Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("‹ Notes", 17, ACC, modifier = Modifier.clickable { onClose(out()) })
            Spacer(Modifier.weight(1f))
            T(if (pin) "Unpin" else "Pin", 17, ACC, modifier = Modifier.clickable { pin = !pin })
            Spacer(Modifier.width(20.dp))
            T(if (del) "Restore" else "Delete", 17, ACC, modifier = Modifier.clickable { del = !del })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.clickable { f = FOLDERS[(FOLDERS.indexOfFirst { it.first == f } + 1) % FOLDERS.size].first }) {
            T(FOLDERS.firstOrNull { it.first == f }?.second ?: "", 15, tx, e = true)
            T(" $f", 15, SUB)
        }
        Spacer(Modifier.height(12.dp))
        BasicTextField(t, { t = it }, Modifier.fillMaxSize(),
            textStyle = TextStyle(color = tx, fontSize = 17.sp, fontFamily = SF), cursorBrush = SolidColor(ACC))
    }
}
