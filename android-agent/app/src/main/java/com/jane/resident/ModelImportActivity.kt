package com.jane.resident

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class ModelImportActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var role: String = ROLE_MIND

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        role = intent.getStringExtra(EXTRA_ROLE) ?: inferRole(intent.data)
        setContentView(buildUi())

        val supplied = intent.data
        if (supplied != null) {
            importUri(supplied)
        } else {
            pickModel()
        }
    }

    @Deprecated("Uses the platform document picker for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MODEL) return
        if (resultCode != RESULT_OK) {
            finish()
            return
        }
        val uri = data?.data ?: run {
            showFailure("No model file was selected.")
            return
        }
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        importUri(uri)
    }

    private fun pickModel() {
        status.text = if (role == ROLE_TOOLS) "Choose the tool model" else "Choose Smith's mind model"
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_MODEL)
    }

    private fun importUri(uri: Uri) {
        val name = displayName(uri)
        if (name != null && !name.endsWith(".litertlm", ignoreCase = true)) {
            showFailure("That file is not a .litertlm model package.")
            return
        }

        progress.visibility = View.VISIBLE
        status.text = "Installing ${name ?: "model"}…\nKeep Smith open until this finishes."
        Thread {
            val result = runCatching {
                val runtime = SmithModelRuntime.get(this)
                runtime.unload("model_replacement")
                val store = ModelStore(this)
                if (role == ROLE_TOOLS) store.importToolRouter(uri) else store.importPrimary(uri)
            }
            runOnUiThread {
                progress.visibility = View.GONE
                result.onSuccess { installed ->
                    status.text = buildString {
                        append(if (role == ROLE_TOOLS) "Tool model installed" else "Mind installed")
                        append("\n")
                        append(formatBytes(installed.bytes))
                    }
                    AgentScheduler(this).wakeNow("model_installed")
                    status.postDelayed({
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            },
                        )
                        finish()
                    }, 700)
                }.onFailure { error ->
                    showFailure(error.message ?: "Model import failed")
                }
            }
        }.start()
    }

    private fun inferRole(uri: Uri?): String {
        val name = uri?.let(::displayName)?.lowercase().orEmpty()
        return if ("function" in name || "tool" in name) ROLE_TOOLS else ROLE_MIND
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.parseColor("#07090D"))
        }
        root.addView(TextView(this).apply {
            text = "Smith"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F4F7FB"))
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            text = "Choose a model"
            textSize = 15f
            setTextColor(Color.parseColor("#9AA6B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(18))
        }
        root.addView(status)
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(progress)
        return root
    }

    private fun showFailure(message: String) {
        progress.visibility = View.GONE
        status.text = message
        status.setTextColor(Color.parseColor("#FFAAA4"))
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / 1_073_741_824.0
        return if (gb >= 1.0) String.format("%.2f GB", gb) else String.format("%.0f MB", bytes / 1_048_576.0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_IMPORT_MODEL = "com.jane.resident.action.IMPORT_MODEL"
        const val EXTRA_ROLE = "model_role"
        const val ROLE_MIND = "mind"
        const val ROLE_TOOLS = "tools"
        private const val REQUEST_MODEL = 904
    }
}
