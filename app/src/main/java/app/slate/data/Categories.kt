package app.slate.data

import android.content.Context
import android.content.pm.ApplicationInfo

/** App Library categories — keyword, package and Play-category rules. */
object Categories {
    private class Rule(
        val name: String,
        val packages: Set<String>,
        val prefixes: List<String> = emptyList(),
        val words: List<String> = emptyList(),
        val categories: Set<Int> = emptySet(),
    )

    // Order matters twice: first matching rule wins, and groups appear in this order.
    private val rules = listOf(
        Rule(
            "Messaging",
            setOf(
                "com.whatsapp", "com.whatsapp.w4b", "com.google.android.apps.messaging", "com.android.mms",
                "com.samsung.android.messaging", "org.telegram.messenger", "org.thoughtcrime.securesms",
                "com.google.android.gm", "com.microsoft.office.outlook", "com.facebook.orca", "com.Slack",
                "com.microsoft.teams", "com.google.android.apps.dynamite", "jp.naver.line.android",
            ),
            words = listOf("whatsapp", "messages", "messaging", "telegram", "signal", "gmail", "mail", "outlook",
                "slack", "teams", "messenger", "sms", "chat"),
        ),
        Rule(
            "Phone",
            setOf(
                "com.android.contacts", "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
                "com.google.android.contacts", "com.samsung.android.app.contacts", "com.truecaller",
                "com.google.android.apps.tachyon",
            ),
            words = listOf("phone", "dialer", "contacts", "truecaller", "call"),
        ),
        Rule(
            "AI",
            setOf(
                "com.openai.chatgpt", "com.anthropic.claude", "ai.x.grok", "com.google.android.apps.bard",
                "ai.perplexity.app.android", "com.microsoft.copilot", "com.deepseek.chat",
            ),
            words = listOf("chatgpt", "claude", "grok", "gemini", "copilot", "perplexity", "deepseek"),
        ),
        Rule(
            "Social",
            setOf(
                "com.twitter.android", "com.reddit.frontpage", "com.pinterest", "com.discord", "com.linkedin.android",
                "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.snapchat.android",
                "com.zhiliaoapp.musically", "com.quora.android", "com.tumblr", "org.joinmastodon.android",
                "xyz.blueskyweb.app", "in.mohalla.sharechat",
            ),
            words = listOf("twitter", "reddit", "pinterest", "discord", "instagram", "facebook", "snapchat", "linkedin",
                "threads", "tiktok", "quora", "bluesky", "mastodon"),
            categories = setOf(ApplicationInfo.CATEGORY_SOCIAL),
        ),
        Rule(
            "Watch & Listen",
            setOf(
                "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.netflix.mediaclient",
                "com.spotify.music", "com.bsbportal.music", "tv.accedo.airtel.wynk", "org.videolan.vlc",
                "com.android.VideoPlayer", "com.android.bbkmusic", "com.utorrent.client", "in.startv.hotstar",
                "com.amazon.avod.thirdpartyclient", "com.jio.media.ondemand", "com.graymatrix.did",
                "com.google.android.apps.youtube.creator", "com.oceanwing.soundcore", "com.jio.jiosaavn",
            ),
            words = listOf("youtube", "netflix", "spotify", "music", "video", "player", "prime video", "hotstar",
                "podcast", "radio", "wynk", "xstream", "saavn", "gaana", "movies", "tv"),
            categories = setOf(ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO),
        ),
        Rule(
            "Finance",
            setOf(
                "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "com.msf.angelmobile",
                "in.org.npci.upiapp", "com.iexceed.ib.digitalbankingprod", "com.google.android.apps.walletnfcrel",
                "icici.lombard.ghi", "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.csam.icici.bank.imobile",
                "com.snapwork.hdfc", "com.axis.mobile", "com.zerodha.kite3", "com.nextbillion.groww", "in.upstox.pro",
                "com.dreamplug.androidapp", "com.mobikwik_new", "com.kotak811mobilebankingapp",
            ),
            words = listOf("bank", "pay", "upi", "wallet", "money", "finance", "trade", "trading", "invest", "stock",
                "mutual", "credit", "loan", "bhim", "kite", "groww", "demat", "angel", "yono", "cred", "insurance",
                "expense", "budget", "lombard"),
        ),
        Rule(
            "Shopping & Food",
            setOf(
                "com.application.zomato", "com.application.zomato.district", "in.swiggy.android",
                "in.swiggy.android.instamart", "com.zeptoconsumerapp", "com.grofers.customerapp",
                "com.flipkart.android", "com.myntra.android", "com.ril.ajio", "in.amazon.mShop.android.shopping",
                "com.bigbasket.mobileapp", "com.meesho.supply", "com.nykaa.app", "com.jio.jiomart",
            ),
            words = listOf("zomato", "swiggy", "zepto", "blinkit", "instamart", "flipkart", "myntra", "ajio", "amazon",
                "shopping", "shop", "store", "mart", "food", "grocery", "district", "meesho", "nykaa"),
        ),
        Rule(
            "Travel",
            setOf(
                "com.google.android.apps.maps", "com.rapido.passenger", "com.app.abhibus", "com.apsrtc.online",
                "com.comuto", "com.whereismytrain.android", "org.cris.aikyam", "com.ttdapp", "com.royalenfield.reprime",
                "com.ubercab", "com.olacabs.customer", "cris.org.in.prs.ima", "com.makemytrip", "com.goibibo",
                "com.redbus.android", "com.ixigo.train.ixitrain",
            ),
            words = listOf("maps", "uber", "ola", "rapido", "bus", "train", "rail", "irctc", "flight", "travel", "trip",
                "metro", "cab", "ride", "royal enfield", "blablacar"),
            categories = setOf(ApplicationInfo.CATEGORY_MAPS),
        ),
        Rule(
            "Work",
            setOf(
                "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets", "com.google.android.apps.docs.editors.slides",
                "com.microsoft.office.word", "com.microsoft.office.excel", "com.microsoft.office.powerpoint",
                "com.microsoft.office.officehubrow", "com.adobe.scan.android", "naukriApp.appModules.login",
                "com.duolingo", "com.notion.id", "com.todoist", "com.google.android.keep", "com.camerasideas.instashot",
            ),
            words = listOf("docs", "sheets", "slides", "word", "excel", "powerpoint", "office", "drive", "scan",
                "naukri", "notion", "todoist", "duolingo", "learn", "study", "course", "editor", "studio", "jobs"),
            categories = setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY),
        ),
        Rule(
            "Tools",
            setOf(
                "com.android.camera", "com.android.deskclock", "com.android.filemanager", "com.vivo.calculator",
                "com.vivo.compass", "com.vivo.notes", "com.vivo.soundrecorder", "com.vivo.weather", "com.vivo.gallery",
                "com.google.android.apps.photos", "com.google.android.apps.nbu.files", "com.google.android.calendar",
                "com.android.chrome", "com.vivo.browser", "com.google.android.googlequicksearchbox",
                "com.google.android.calculator", "com.google.android.deskclock", "com.google.android.GoogleCamera",
            ),
            words = listOf("camera", "clock", "calculator", "calendar", "files", "file manager", "notes", "recorder",
                "weather", "compass", "gallery", "photos", "album", "chrome", "browser", "google", "flashlight",
                "translate", "scanner"),
            categories = setOf(ApplicationInfo.CATEGORY_IMAGE),
        ),
        Rule(
            "Utilities",
            setOf(
                "com.android.settings", "com.android.vending", "com.vivo.appstore", "com.bbk.theme", "com.bbk.cloud",
                "com.vivo.easyshare", "com.vivo.imanager", "com.vivo.feedback", "com.vivo.vhome", "com.vivo.exhealth",
                "com.iqoo.website", "com.android.stk", "com.google.android.apps.safetyhub",
                "com.google.android.apps.subscriptions.red", "com.azure.authenticator", "com.digilocker.android",
                "in.gov.uidai.pehchaan", "com.myairtelapp", "com.apspdcl.consumerapp", "com.xiaomi.smarthome",
                "com.amazon.dee.app", "com.android.bbk.lockscreen3", "com.vivo.simplelauncher",
            ),
            words = listOf("settings", "play store", "app store", "theme", "cloud", "share", "manager", "authenticator",
                "digilocker", "aadhaar", "airtel", "jio", "vi ", "electricity", "power", "bill", "home", "alexa",
                "security", "safety", "backup", "sim", "health"),
        ),
        Rule("Games", setOf("com.vivo.game"), words = listOf("game", "games", "chess", "puzzle"),
            categories = setOf(ApplicationInfo.CATEGORY_GAME)),
    )
    const val OTHER = "Other"

    /** One compiled pattern per rule (word starts only), built once. */
    private val patterns: Map<String, Regex> by lazy {
        rules.associate { r -> r.name to Regex("(^|[^a-z])(" + r.words.joinToString("|") { Regex.escape(it) } + ")") }
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun of(context: Context, app: App): String = cache.getOrPut(app.key + "|" + app.label) {
        val label = app.label.lowercase()
        (rules.firstOrNull { app.packageName in it.packages }
            ?: rules.firstOrNull { r -> r.words.isNotEmpty() && patterns.getValue(r.name).containsMatchIn(label) }
            ?: run {
                val category = runCatching { context.packageManager.getApplicationInfo(app.packageName, 0).category }.getOrDefault(-1)
                rules.firstOrNull { category in it.categories }
            })?.name ?: OTHER
    }

    /** Category name → apps, in display order (empty categories dropped). */
    fun sort(context: Context, apps: List<App>): List<Pair<String, List<App>>> {
        val by = apps.groupBy { of(context, it) }
        return (rules.map { it.name } + OTHER).mapNotNull { n -> by[n]?.takeIf { it.isNotEmpty() }?.let { n to it } }
    }
}
