package app.slate.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.SlateApp
import app.slate.data.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Contact(val name: String, val number: String?)

/** Swipe down on home: apps, contacts, quick maths, and the web — all from one field. */
@Composable
fun SearchOverlay(slate: SlateApp, actions: Actions, onClose: () -> Unit) {
    val context = LocalContext.current
    val apps by slate.apps.apps.collectAsState()
    val prefs by slate.settings.prefs.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val close by rememberUpdatedState(onClose)
    var contactsAllowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }

    LaunchedEffect(Unit) { delay(120); runCatching { focus.requestFocus() }; keyboard?.show() }
    BackHandler { close() }

    val q = query.trim()
    val hits = remember(apps, q, prefs.hidden) {
        if (q.isEmpty()) emptyList() else {
            val l = q.lowercase()
            apps.filter { it.key !in prefs.hidden }
                .mapNotNull { a ->
                    val n = a.label.lowercase()
                    val words = n.split(' ', '-', '.')
                    val r = when {
                        n.startsWith(l) -> 0
                        words.any { it.startsWith(l) } -> 1
                        n.contains(l) -> 2
                        words.joinToString("") { it.take(1) }.startsWith(l) -> 3
                        else -> -1
                    }
                    if (r < 0) null else a to r
                }.sortedBy { it.second }.map { it.first }.take(12)
        }
    }
    val math = remember(q) { Calc.eval(q) }
    val contacts by produceState(emptyList<Contact>(), q, contactsAllowed) {
        value = if (contactsAllowed && q.length >= 2) withContext(Dispatchers.IO) { findContacts(context, q) } else emptyList()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, null) { close() }
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy < -80.dp.toPx()) close() }) { _, d -> dy += d }
            }
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), Color(0x33FFFFFF)).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search apps, contacts, maths, the web", color = Slate.Muted, size = 19.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Slate.Ink, fontSize = 19.sp),
                        cursorBrush = SolidColor(Slate.Ink),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            hits.firstOrNull()?.let { actions.launch(it, null); close() }
                                ?: if (q.isNotEmpty()) { actions.webSearch(q); close() } else Unit
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Txt("Clear", color = Slate.Muted, size = 15.sp, modifier = Modifier.press { query = "" }.padding(start = 12.dp))
            }

            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 18.dp)) {
                if (q.isEmpty()) {
                    val recents by slate.usage.recents.collectAsState()
                    val recentApps = recents.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(8)
                    if (recentApps.isNotEmpty()) {
                        Section("Recent")
                        AppStrip(recentApps) { actions.launch(it, null); close() }
                    }
                }
                if (math != null) {
                    Section("Calculator")
                    Txt(
                        "= $math", size = 34.sp, weight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), Color(0x22FFFFFF)).padding(18.dp),
                    )
                }
                if (hits.isNotEmpty()) {
                    Section("Apps")
                    AppStrip(hits) { actions.launch(it, null); close() }
                }
                if (q.length >= 2) {
                    if (contacts.isNotEmpty()) {
                        Section("Contacts")
                        contacts.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(RoundedCornerShape(14.dp), Color(0x22FFFFFF))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(38.dp).glass(CircleShape, Slate.Accent.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                                    Txt(c.name.take(1).uppercase(), size = 17.sp, weight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Txt(c.name, size = 17.sp, maxLines = 1)
                                    c.number?.let { Txt(it, color = Slate.Muted, size = 13.sp) }
                                }
                                c.number?.let { n ->
                                    Chip("Call") { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))); close() }
                                    Spacer(Modifier.width(8.dp))
                                    Chip("Message") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n"))); close() }
                                }
                            }
                        }
                    } else if (!contactsAllowed) {
                        Txt(
                            "Show contacts in search too", color = Slate.Accent, size = 15.sp,
                            modifier = Modifier.padding(vertical = 14.dp).press { askContacts.launch(Manifest.permission.READ_CONTACTS) },
                        )
                    }
                }
                if (q.isNotEmpty()) {
                    Section("Web")
                    Txt(
                        "Search the web for “$q”", size = 17.sp,
                        modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp), Color(0x22FFFFFF))
                            .press { actions.webSearch(q); close() }.padding(18.dp),
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Txt(title, color = Slate.Muted, size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppStrip(apps: List<App>, onOpen: (App) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        apps.forEach { a ->
            LabeledIcon(a.label, 60.dp, true, Modifier.width(88.dp).press { onOpen(a) }.padding(vertical = 6.dp)) { AppIcon(a, 60.dp) }
        }
    }
}

private fun findContacts(context: Context, q: String): List<Contact> = runCatching {
    val out = mutableListOf<Contact>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { c ->
        val seen = HashSet<String>()
        while (c.moveToNext() && out.size < 6) {
            val name = c.getString(0) ?: continue
            if (seen.add(name)) out += Contact(name, c.getString(1))
        }
    }
    out
}.getOrDefault(emptyList())

/** Tiny calculator: + − × ÷ ^ %, parentheses. Returns null unless the text is clearly maths. */
object Calc {
    fun eval(s: String): String? {
        val t = s.replace("×", "*").replace("÷", "/").replace(" ", "").replace(",", "")
        if (t.length < 3 || t.none { it in "+-*/^%" } || t.any { !(it.isDigit() || it in "+-*/^%().") }) return null
        return runCatching {
            val v = Parser(t).parse()
            if (v.isNaN() || v.isInfinite()) null
            else if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else "%.8f".format(v).trimEnd('0').trimEnd('.')
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); require(i == s.length); return v }
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) { val op = s[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                val op = s[i++]
                if (op == '%' && (i >= s.length || s[i] in "+-*/)")) { v /= 100; continue }
                val r = power()
                v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
            }
            return v
        }
        fun power(): Double { val b = unary(); return if (i < s.length && s[i] == '^') { i++; Math.pow(b, power()) } else b }
        fun unary(): Double = if (i < s.length && s[i] == '-') { i++; -unary() } else atom()
        fun atom(): Double {
            if (s[i] == '(') { i++; val v = expr(); require(s[i++] == ')'); return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}
