package com.supernova.launcher.jp.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.supernova.launcher.jp.dict.JpDict

/** Run only on an emulator: opens reader dialogs; never resets launcher layout or preferences. */
@RunWith(AndroidJUnit4::class)
class ReaderIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun observedKanaSpellingBeatsAnUnrelatedHomophone() {
        val entry = JpDict.exact(context, "こと", 1).single()
        assertEquals("こと", entry.head)
        assertTrue(entry.gloss.contains("thing"))
    }

    @Test fun subsequentCaptureIntentReplacesPreviousReaderText() {
        val first = "日本語を読む。"
        val second = "昨日、食べさせられた。"
        ActivityScenario.launch<ReaderActivity>(textIntent(first)).use { scenario ->
            scenario.onActivity { assertTrue(visibleText(it.findViewById(android.R.id.content)).contains(first)) }
            context.startActivity(textIntent(second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // startActivity is asynchronous across ActivityTaskManager; main-thread idle
            // alone can precede the new-intent transaction from system_server.
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            var delivered = false
            while (!delivered && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { delivered = visibleText(it.findViewById(android.R.id.content)).contains(second) }
                if (!delivered) android.os.SystemClock.sleep(40)
            }
            scenario.onActivity {
                val text = visibleText(it.findViewById(android.R.id.content))
                assertTrue("New sentence missing from $text", text.contains(second))
                assertFalse(text.contains(first))
            }
            scenario.recreate()
            scenario.onActivity { assertTrue(visibleText(it.findViewById(android.R.id.content)).contains(second)) }
        }
    }

    @Test fun bundledJapaneseOcrReadsARegionWithoutDownloadingAModel() = runBlocking {
        val bitmap = Bitmap.createBitmap(1200, 160, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText("日本語を勉強しています。", 30f, 100f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 70f })
            }
            assertEquals("日本語を勉強しています。", ReaderOcr.read(bitmap).replace(" ", "").replace("\n", ""))
        } finally { bitmap.recycle() }
    }

    private fun textIntent(text: String) = Intent(context, ReaderActivity::class.java)
        .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)

    private fun visibleText(view: View): List<String> {
        if (view.visibility != View.VISIBLE) return emptyList()
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { visibleText(view.getChildAt(it)) }
            else -> emptyList()
        }
    }
}
