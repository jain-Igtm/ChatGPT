package com.jane.resident

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var repository: AgentRepository
    private lateinit var scheduler: AgentScheduler

    private lateinit var modeText: TextView
    private lateinit var identityText: TextView
    private lateinit var activityText: TextView
    private lateinit var countsText: TextView
    private lateinit var lastWakeText: TextView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var input: EditText
    private lateinit var attachmentText: TextView

    private var pendingAttachment: Uri? = null
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

        window.statusBarColor = color("#090B10")
        window.navigationBarColor = color("#090B10")
        setContentView(buildUi())
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
            attachmentText.text = pendingAttachment?.lastPathSegment ?: "Attachment selected"
            attachmentText.visibility = View.VISIBLE
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(color("#090B10"))
        }

        root.addView(TextView(this).apply {
            text = "RESIDENT"
            textSize = 12f
            letterSpacing = 0.22f
            setTextColor(color("#9CC8FF"))
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Continuity Core"
            textSize = 30f
            setTextColor(color("#F1F4FA"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(14))
        })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded("#121620", 22f)
        }
        modeText = statusLine("State")
        identityText = statusLine("Identity")
        activityText = statusLine("Activity")
        countsText = statusLine("Memory")
        lastWakeText = statusLine("Last wake")
        statusCard.addView(modeText)
        statusCard.addView(identityText)
        statusCard.addView(activityText)
        statusCard.addView(countsText)
        statusCard.addView(lastWakeText)
        root.addView(statusCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) })

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttonRow.addView(actionButton("Wake now") {
            scheduler.wakeNow("private_dashboard")
            refresh()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            rightMargin = dp(8)
        })
        buttonRow.addView(actionButton("Battery settings") {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(buttonRow)

        root.addView(TextView(this).apply {
            text = "PRIVATE CHAT"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(color("#9AA6BC"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(8))
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        messagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        scroll.addView(messagesContainer)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        attachmentText = TextView(this).apply {
            visibility = View.GONE
            textSize = 12f
            setTextColor(color("#9CC8FF"))
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        root.addView(attachmentText)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(6), 0, 0)
        }
        composer.addView(actionButton("+") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_ATTACHMENT)
        }, LinearLayout.LayoutParams(dp(48), dp(52)).apply {
            rightMargin = dp(8)
        })
        input = EditText(this).apply {
            hint = "Message the resident…"
            setHintTextColor(color("#667087"))
            setTextColor(color("#F1F4FA"))
            textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded("#121620", 18f)
            minHeight = dp(52)
            maxLines = 5
        }
        composer.addView(input, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { rightMargin = dp(8) })
        composer.addView(actionButton("Send") { sendMessage() },
            LinearLayout.LayoutParams(dp(78), dp(52)))
        root.addView(composer)

        return root
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isBlank() && pendingAttachment == null) return
        repository.addUserMessage(
            body = text.ifBlank { "[Attachment]" },
            attachmentUri = pendingAttachment?.toString(),
        )
        input.setText("")
        pendingAttachment = null
        attachmentText.visibility = View.GONE
        scheduler.wakeNow("user_message")
        refresh()
    }

    private fun refresh() {
        val snapshot = repository.snapshot()
        modeText.text = "State  ${snapshot.mode.name.lowercase()}"
        identityText.text = "Identity  ${snapshot.identityId.take(8)}"
        activityText.text = "Activity  ${snapshot.currentActivityType ?: "none"}"
        countsText.text =
            "Memory  ${snapshot.memoryCount}   Pending  ${snapshot.pendingActivities}   Journal  ${snapshot.journalCount}"
        lastWakeText.text = "Last wake  ${formatTime(snapshot.lastWakeAt)}"

        messagesContainer.removeAllViews()
        repository.listMessages().forEach { message ->
            messagesContainer.addView(messageBubble(message))
        }
    }

    private fun messageBubble(message: ChatMessage): View {
        val isUser = message.role == "user"
        return TextView(this).apply {
            text = buildString {
                append(message.body)
                message.attachmentUri?.let {
                    append("\n\nAttachment: ")
                    append(Uri.parse(it).lastPathSegment ?: it)
                }
            }
            textSize = 15f
            setTextColor(color("#F1F4FA"))
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (isUser) "#23344B" else "#121620", 18f)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.gravity = if (isUser) Gravity.END else Gravity.START
            params.setMargins(
                if (isUser) dp(40) else 0,
                dp(4),
                if (isUser) 0 else dp(40),
                dp(4),
            )
            layoutParams = params
        }
    }

    private fun statusLine(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(color("#D8E0EE"))
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(color("#F1F4FA"))
            background = rounded("#1A2030", 18f)
            setOnClickListener { onClick() }
        }

    private fun rounded(hex: String, radiusDp: Float) = GradientDrawable().apply {
        setColor(color(hex))
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun formatTime(value: Long?): String {
        if (value == null) return "never"
        return DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(Date(value))
    }

    private fun color(hex: String): Int = Color.parseColor(hex)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_ATTACHMENT = 42
    }
}
