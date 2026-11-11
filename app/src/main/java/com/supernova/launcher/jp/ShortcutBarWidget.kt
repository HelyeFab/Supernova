package com.supernova.launcher.jp

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.supernova.launcher.R

// JP fork: one row of the Japanese-study launch targets - Chiba, the
// ZeroClaw chat, and the dictionary / flashcard apps. Targets that aren't
// installed are left out, so the row only shows what this phone has.
class ShortcutBarWidget(context: Context) : LinearLayout(context) {

    // How to open one target. resolve() returns null when it isn't here.
    private class Target(
        val label: String,
        val resolve: (Context) -> Launch?
    )

    private class Launch(val icon: Drawable?, val intent: Intent, val fallback: Intent? = null)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        val pad = dp(8)
        setPadding(pad, pad / 2, pad, pad / 2)
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(CARD)
            setStroke(dp(1), EDGE)
        }
        TARGETS.forEach { target ->
            val launch = target.resolve(context) ?: return@forEach
            addView(cell(target.label, launch), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun cell(label: String, launch: Launch) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }
        setBackgroundResource(ripple.resourceId)
        addView(ImageView(context).apply {
            setImageDrawable(launch.icon)
        }, LayoutParams(dp(30), dp(30)))
        addView(TextView(context).apply {
            text = label
            setTextColor(FG)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxLines = 1
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
        setOnClickListener { start(launch) }
        // Long press falls through to the launcher's widget menu / drag.
        setOnLongClickListener { (this@ShortcutBarWidget).performLongClick() }
    }

    private fun start(launch: Launch) {
        val tried = listOfNotNull(launch.intent, launch.fallback)
        for (intent in tried) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val KIND = "jp-shortcuts"

        // His KWGT palette: fg #DCDCD8 on a translucent graphite card.
        private const val FG = 0xFFDCDCD8.toInt()
        private const val CARD = 0x73303030
        private const val EDGE = 0x33FFFFFF

        private const val CHIBA_URL = "https://chiba.appsparkle.org/"
        private const val ZEROCLAW_TG = "tg://resolve?domain=emma_myzeroclaw_bot"

        private fun app(label: String, pkg: String) = Target(label) { c ->
            val pm = c.packageManager
            pm.getLaunchIntentForPackage(pkg)?.let { Launch(runCatching { pm.getApplicationIcon(pkg) }.getOrNull(), it) }
        }

        private val TARGETS = listOf(
            // Chiba is a Chrome WebAPK whose package name is a hash; find it by
            // the URL it owns, and fall back to the browser.
            Target("Chiba") { c ->
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(CHIBA_URL))
                val pm = c.packageManager
                val webapk = pm.queryIntentActivities(view, 0)
                    .map { it.activityInfo.packageName }
                    .firstOrNull { it.startsWith("org.chromium.webapk") }
                val launch = webapk?.let(pm::getLaunchIntentForPackage)
                Launch(
                    webapk?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() }
                        ?: c.getDrawable(R.drawable.ic_jp_torii),
                    launch ?: view,
                    fallback = view
                )
            },
            // Washi's OpenChatActivity opens the chat through Shizuku, skipping
            // Honor's App Twin chooser; without Washi, the plain tg:// link.
            Target("ZeroClaw") { c ->
                val pm = c.packageManager
                val icon = runCatching { pm.getApplicationIcon("org.telegram.messenger") }.getOrNull()
                    ?: return@Target null
                val link = Intent(Intent.ACTION_VIEW, Uri.parse(ZEROCLAW_TG))
                val washi = Intent().setComponent(ComponentName("dev.fabiani.washi", "dev.fabiani.washi.OpenChatActivity"))
                val hasWashi = runCatching { pm.getActivityInfo(washi.component!!, 0) }.isSuccess
                Launch(icon, if (hasWashi) washi else link, fallback = link)
            },
            app("Anki", "com.ichi2.anki"),
            app("Akebi", "com.craxic.akebifree"),
            app("Takoboto", "jp.takoboto")
        )
    }
}
