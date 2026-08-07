package com.jane.resident

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var repository: AgentRepository
    private lateinit var scheduler: AgentScheduler

    private lateinit var root: LinearLayout
    private lateinit var contentHost: LinearLayout
    private lateinit var statusPill: TextView
    private lateinit var dashboardTab: TextView
    private lateinit var chatTab: TextView

    private lateinit var nowValue: TextView
    private lateinit var detailValue: TextView
    private lateinit var memoryValue: TextView
    private lateinit var queueValue: TextView
    private lateinit var journalValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var ramValue: TextView
    private lateinit var storageValue: TextView
    private lateinit var thermalValue: TextView
    private lateinit var lastWakeValue: TextView
    private lateinit var lastSleepValue: TextView
    private lateinit var wakeReasonValue: TextView

    private lateinit var messagesContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var attachmentText: TextView

    private var pendingAttachment: Uri? = null
    private var activeScreen = Screen.DASHBOARD

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            refreshHandler.postDelayed(this, 1_500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AgentRepository(this)
        scheduler = AgentScheduler(this)
        repository.initialize()
        scheduler.ensureAutonomousWake()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        root = buildShell()
        setContentView(root)
        installInsets(root)
        showScreen(Screen.DASHBOARD)
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    @Deprecated("Uses the platform file picker for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ATTACHMENT && resultCode == RESULT_OK) {
            pendingAttachment = data?.data
            pendingAttachment?.let { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        data?.flags?.and(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        ) ?: Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (::attachmentText.isInitialized) {
                attachmentText.text = pendingAttachment?.lastPathSegment ?: "Attachment selected"
                attachmentText.visibility = View.VISIBLE
            }
        }
    }

    private fun installInsets(view: View) {
        val horizontal = dp(18)
        val topBase = dp(10)
        val bottomBase = dp(10)
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(bars.bottom, ime.bottom)
            target.setPadding(
                horizontal + bars.left,
                topBase + bars.top,
                horizontal + bars.right,
                bottomBase + bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun buildShell(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(BG))

            addView(buildHeader())
            addView(buildTabs(), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46),
            ).apply {
                topMargin = dp(18)
                bottomMargin = dp(14)
            })

            contentHost = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(contentHost, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleStack.addView(TextView(this).apply {
            text = "Smith"
            textSize = 32f
            setTextColor(color(TEXT))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
        })
        titleStack.addView(TextView(this).apply {
            text = "resident / local"
            textSize = 12f
            setTextColor(color(MUTED))
            letterSpacing = 0.08f
            setPadding(1, dp(2), 0, 0)
        })
        row.addView(titleStack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        statusPill = TextView(this).apply {
            text = "SLEEPING"
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(color(ACCENT))
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = rounded(CARD_2, 18f, STROKE)
        }
        row.addView(statusPill)
        return row
    }

    private fun buildTabs(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(CARD, 18f, STROKE)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        dashboardTab = tab("Overview") { showScreen(Screen.DASHBOARD) }
        chatTab = tab("Chat") { showScreen(Screen.CHAT) }
        strip.addView(dashboardTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        strip.addView(chatTab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        return strip
    }

    private fun showScreen(screen: Screen) {
        activeScreen = screen
        contentHost.removeAllViews()
        dashboardTab.background = if (screen == Screen.DASHBOARD) rounded(CARD_2, 14f) else null
        chatTab.background = if (screen == Screen.CHAT) rounded(CARD_2, 14f) else null
        dashboardTab.setTextColor(color(if (screen == Screen.DASHBOARD) TEXT else MUTED))
        chatTab.setTextColor(color(if (screen == Screen.CHAT) TEXT else MUTED))

        when (screen) {
            Screen.DASHBOARD -> contentHost.addView(buildDashboard(), match())
            Screen.CHAT -> contentHost.addView(buildChat(), match())
        }
        refresh()
    }

    private fun buildDashboard(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        val nowCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(17))
            background = rounded(CARD, 24f, STROKE)
        }
        nowCard.addView(sectionLabel("NOW"))
        nowValue = TextView(this).apply {
            text = "Idle"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(TEXT))
            setPadding(0, dp(7), 0, dp(4))
        }
        detailValue = TextView(this).apply {
            text = "No active work"
            textSize = 13f
            setTextColor(color(MUTED))
        }
        nowCard.addView(nowValue)
        nowCard.addView(detailValue)
        column.addView(nowCard, matchWrap().apply { bottomMargin = dp(12) })

        val metricRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        memoryValue = metricCard(metricRow, "MEMORY", "0")
        queueValue = metricCard(metricRow, "QUEUE", "0")
        journalValue = metricCard(metricRow, "JOURNAL", "0", last = true)
        column.addView(metricRow, matchWrap().apply { bottomMargin = dp(12) })

        val deviceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = rounded(CARD, 24f, STROKE)
        }
        deviceCard.addView(sectionLabel("DEVICE"))
        batteryValue = dataRow(deviceCard, "Battery")
        ramValue = dataRow(deviceCard, "Memory available")
        storageValue = dataRow(deviceCard, "Storage free")
        thermalValue = dataRow(deviceCard, "Thermal")
        column.addView(deviceCard, matchWrap().apply { bottomMargin = dp(12) })

        val rhythmCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(15))
            background = rounded(CARD, 24f, STROKE)
        }
        rhythmCard.addView(sectionLabel("RHYTHM"))
        lastWakeValue = dataRow(rhythmCard, "Last wake")
        lastSleepValue = dataRow(rhythmCard, "Last sleep")
        wakeReasonValue = dataRow(rhythmCard, "Wake reason")
        column.addView(rhythmCard, matchWrap().apply { bottomMargin = dp(12) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(primaryButton("Wake now") {
            scheduler.wakeNow("private_dashboard")
            refresh()
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) })
        actions.addView(quietButton("Power settings") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        column.addView(actions)

        scroll.addView(column)
        return scroll
    }

    private fun buildChat(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        scroll.addView(messagesContainer)
        column.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        attachmentText = TextView(this).apply {
            visibility = if (pendingAttachment == null) View.GONE else View.VISIBLE
            text = pendingAttachment?.lastPathSegment ?: ""
            textSize = 12f
            maxLines = 1
            setTextColor(color(ACCENT))
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(CARD, 14f, STROKE)
        }
        column.addView(attachmentText, matchWrap().apply { bottomMargin = dp(7) })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        composer.addView(iconButton("+") { pickAttachment() }, LinearLayout.LayoutParams(dp(52), dp(52)).apply {
            rightMargin = dp(8)
        })
        input = EditText(this).apply {
            hint = "Message Smith"
            setHintTextColor(color("#626B7B"))
            setTextColor(color(TEXT))
            textSize = 15f
            setPadding(dp(15), dp(10), dp(15), dp(10))
            background = rounded(CARD, 19f, STROKE)
            minHeight = dp(52)
            maxLines = 5
            setSingleLine(false)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(8)
        })
        composer.addView(primaryButton("Send") { sendMessage() }, LinearLayout.LayoutParams(dp(76), dp(52)))
        column.addView(composer)
        return column
    }

    private fun pickAttachment() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_ATTACHMENT)
    }

    private fun sendMessage() {
        if (!::input.isInitialized) return
        val text = input.text.toString().trim()
        if (text.isBlank() && pendingAttachment == null) return
        repository.addUserMessage(
            body = text.ifBlank { "[Attachment]" },
            attachmentUri = pendingAttachment?.toString(),
        )
        input.setText("")
        pendingAttachment = null
        if (::attachmentText.isInitialized) attachmentText.visibility = View.GONE
        scheduler.wakeNow("user_message")
        refresh()
    }

    private fun refresh() {
        val snapshot = repository.snapshot()
        statusPill.text = snapshot.mode.name
        statusPill.setTextColor(color(if (snapshot.mode == AgentMode.AWAKE) GREEN else ACCENT))

        if (activeScreen == Screen.DASHBOARD && ::nowValue.isInitialized) {
            nowValue.text = humanActivity(snapshot.currentActivityType)
            detailValue.text = snapshot.currentCheckpoint?.takeIf { it.isNotBlank() }
                ?: if (snapshot.currentActivityType == null) "No active work" else "Working"
            memoryValue.text = snapshot.memoryCount.toString()
            queueValue.text = snapshot.pendingActivities.toString()
            journalValue.text = snapshot.journalCount.toString()
            lastWakeValue.text = formatTime(snapshot.lastWakeAt)
            lastSleepValue.text = formatTime(snapshot.lastSleepAt)
            wakeReasonValue.text = snapshot.wakeReason?.replace('_', ' ') ?: "—"
            refreshDeviceData()
        }

        if (activeScreen == Screen.CHAT && ::messagesContainer.isInitialized) {
            messagesContainer.removeAllViews()
            repository.listMessages().forEach { message ->
                messagesContainer.addView(messageBubble(message))
            }
        }
    }

    private fun refreshDeviceData() {
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        batteryValue.text = if (batteryPct in 0..100) "$batteryPct%" else "—"

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        ramValue.text = "${formatBytes(memoryInfo.availMem)} / ${formatBytes(memoryInfo.totalMem)}"

        val stat = StatFs(filesDir.absolutePath)
        storageValue.text = formatBytes(stat.availableBytes)

        val power = getSystemService(POWER_SERVICE) as PowerManager
        thermalValue.text = if (android.os.Build.VERSION.SDK_INT >= 29) {
            thermalLabel(power.currentThermalStatus)
        } else {
            "unknown"
        }
    }

    private fun messageBubble(message: ChatMessage): View {
        val isUser = message.role == "user"
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (isUser) USER_BUBBLE else CARD, 19f, if (isUser) null else STROKE)
        }
        bubble.addView(TextView(this).apply {
            text = message.body
            textSize = 15f
            setTextColor(color(TEXT))
        })
        message.attachmentUri?.let {
            bubble.addView(TextView(this).apply {
                text = Uri.parse(it).lastPathSegment ?: "Attachment"
                textSize = 11f
                setTextColor(color(ACCENT))
                setPadding(0, dp(7), 0, 0)
            })
        }
        bubble.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            setMargins(
                if (isUser) dp(46) else 0,
                dp(4),
                if (isUser) 0 else dp(46),
                dp(4),
            )
        }
        return bubble
    }

    private fun metricCard(parent: LinearLayout, label: String, value: String, last: Boolean = false): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(CARD, 20f, STROKE)
        }
        card.addView(sectionLabel(label))
        val number = TextView(this).apply {
            text = value
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(TEXT))
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(number)
        parent.addView(card, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (!last) rightMargin = dp(8)
        })
        return number
    }

    private fun dataRow(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(3))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(color(MUTED))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(this).apply {
            text = "—"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(TEXT))
            gravity = Gravity.END
        }
        row.addView(value)
        parent.addView(row)
        return value
    }

    private fun sectionLabel(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 10f
        letterSpacing = 0.15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(MUTED))
    }

    private fun tab(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(color(MUTED))
        setOnClickListener { onClick() }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = button(label, ACCENT_DARK, TEXT, onClick)
    private fun quietButton(label: String, onClick: () -> Unit): Button = button(label, CARD, TEXT, onClick, STROKE)
    private fun iconButton(label: String, onClick: () -> Unit): Button = button(label, CARD, TEXT, onClick, STROKE)

    private fun button(
        label: String,
        fill: String,
        textColor: String,
        onClick: () -> Unit,
        stroke: String? = null,
    ): Button = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(textColor))
        background = rounded(fill, 18f, stroke)
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { onClick() }
    }

    private fun humanActivity(type: String?): String {
        if (type == null) return "Idle"
        return type.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "cool"
        PowerManager.THERMAL_STATUS_LIGHT -> "warm"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "hot"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "unknown"
    }

    private fun formatTime(value: Long?): String {
        if (value == null) return "never"
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / 1_073_741_824.0
        return if (gb >= 1.0) String.format("%.1f GB", gb) else String.format("%.0f MB", bytes / 1_048_576.0)
    }

    private fun rounded(fill: String, radiusDp: Float, stroke: String? = null) = GradientDrawable().apply {
        setColor(color(fill))
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), color(stroke))
    }

    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun color(hex: String): Int = Color.parseColor(hex)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class Screen { DASHBOARD, CHAT }

    companion object {
        private const val REQUEST_ATTACHMENT = 42
        private const val BG = "#07090D"
        private const val CARD = "#10141B"
        private const val CARD_2 = "#171D27"
        private const val STROKE = "#232B38"
        private const val TEXT = "#F4F7FB"
        private const val MUTED = "#8590A3"
        private const val ACCENT = "#9EC8FF"
        private const val ACCENT_DARK = "#244A78"
        private const val GREEN = "#8FE0B0"
        private const val USER_BUBBLE = "#203752"
    }
}
