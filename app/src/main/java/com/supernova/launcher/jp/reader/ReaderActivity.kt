package com.supernova.launcher.jp.reader

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.isVisible
import com.supernova.launcher.SupernovaAccessibilityService
import com.supernova.launcher.R
import com.supernova.launcher.jp.cards.RubySpan
import com.supernova.launcher.jp.dict.JpDict
import com.supernova.launcher.jp.dict.JpDictRowView
import com.supernova.launcher.jp.struggle.Tts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One Japanese reading sheet, reached through capture, Android text selection, or sharing. */
class ReaderActivity : AppCompatActivity() {
    private lateinit var prefs: ReaderPrefs
    private lateinit var body: LinearLayout
    private lateinit var rendered: TextView
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var vocabulary: LinearLayout
    private lateinit var wordDetail: LinearLayout
    private lateinit var explanation: TextView
    private lateinit var explainButton: Button
    private lateinit var sentenceLabel: TextView
    private lateinit var question: EditText
    private lateinit var askButton: Button
    private lateinit var sourceActions: LinearLayout
    private var source = ""
    private var sentences = emptyList<String>()
    private var sentenceIndex = 0
    private var analysis: Job? = null
    private var claude: Job? = null
    private var captureRead: Job? = null
    private val turns = mutableListOf<ReaderClaude.Turn>()
    private var explanationSource = ""
    private var lastAnswer = ""
    private var captureId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ReaderPrefs(this)
        source = savedInstanceState?.getString("source") ?: when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }.orEmpty().take(MAX_SOURCE)
        captureId = savedInstanceState?.getString("capture") ?: intent.getStringExtra(EXTRA_CAPTURE)
        sentenceIndex = savedInstanceState?.getInt("sentence") ?: 0
        lastAnswer = savedInstanceState?.getString("answer").orEmpty()
        explanationSource = savedInstanceState?.getString("explanation_source").orEmpty()
        savedInstanceState?.getStringArrayList("turns")?.chunked(2)?.filter { it.size == 2 }
            ?.forEach { turns.add(ReaderClaude.Turn(it[0], it[1])) }
        buildUi()
        if (source.isNotBlank()) updateSource(reset = false)
        else if (captureId == null) showWelcome()
        if (captureId != null) loadCapture(readText = source.isBlank())
        savedInstanceState?.getString("draft")?.let { editor.setText(it) }
        if (savedInstanceState?.getBoolean("editing") == true) {
            editor.visibility = View.VISIBLE; sourceActions.visibility = View.VISIBLE
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A capture reuses this dialog task. Consume the new capture instead of showing
        // the previous task's root screen (Android NEW_TASK can bring that task back).
        analysis?.cancel(); claude?.cancel(); captureRead?.cancel(); Tts.stop()
        turns.clear(); explanationSource = ""; lastAnswer = ""; sentenceIndex = 0
        sentences = emptyList()
        source = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }.orEmpty().take(MAX_SOURCE)
        captureId = intent.getStringExtra(EXTRA_CAPTURE)
        buildUi()
        if (source.isNotBlank()) updateSource()
        else if (captureId == null) showWelcome()
        if (captureId != null) loadCapture(readText = source.isBlank())
    }

    private fun buildUi() {
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(24))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(PAPER) }
        }
        val header = row()
        header.addView(label("読む", 30f).apply { typeface = Typeface.SERIF },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Settings") { settings() })
        header.addView(button("Close") { finish() })
        body.addView(header)
        body.addView(label("JAPANESE READER", 11f, MUTED).apply { letterSpacing = 0.16f })
        status = label("", 13f, MUTED)
        body.addView(status, spaced())

        editor = EditText(this).apply {
            hint = "Paste Japanese here, or double-tap the back of the phone over another app"
            textSize = 20f; setTextColor(INK); setHintTextColor(MUTED)
            minLines = 2; maxLines = 8; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_SOURCE))
            setText(source)
        }
        body.addView(editor)
        sourceActions = row().apply {
            addView(button("Read this text") {
                source = editor.text.toString().trim()
                if (source.isBlank()) { toast("Enter some Japanese first."); return@button }
                editor.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(editor.windowToken, 0)
                updateSource()
            })
            addView(button("Use selection") {
                val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                if (end <= start) { toast("Select a sentence in the text first."); return@button }
                source = editor.text.substring(start, end); updateSource()
            })
        }
        body.addView(sourceActions)

        val navigation = row()
        navigation.addView(button("‹") { moveSentence(-1) }.apply { contentDescription = "Previous sentence" })
        sentenceLabel = label("", 13f, MUTED).apply { gravity = Gravity.CENTER }
        navigation.addView(sentenceLabel, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(button("›") { moveSentence(1) }.apply { contentDescription = "Next sentence" })
        navigation.addView(button("All") { sentenceIndex = -1; showSentence() })
        body.addView(navigation)
        rendered = label("", 25f).apply {
            movementMethod = LinkMovementMethod.getInstance()
            setLineSpacing(dp(17).toFloat(), 1.15f)
            setPadding(0, dp(18), 0, dp(10))
            highlightColor = 0x33B65D3D
        }
        body.addView(rendered)
        val controls = row()
        controls.addView(button("▶ Listen") { speak(currentText(), 1.0) }, weighted())
        controls.addView(button("▶ Slow") { speak(currentText(), 0.8) }, weighted())
        controls.addView(button("■") { Tts.stop() }.apply { contentDescription = "Stop reading" })
        controls.addView(button("あ") { prefs.furigana = !prefs.furigana; showSentence(clearExplanation = false) }
            .apply { contentDescription = "Toggle furigana" })
        body.addView(controls)
        body.addView(row().apply {
            addView(button("Edit source") {
                editor.visibility = View.VISIBLE; sourceActions.visibility = View.VISIBLE
                editor.setText(source); editor.requestFocus()
            }, weighted())
            addView(button("Share") {
                if (currentText().isNotBlank()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentText()), "Share Japanese"))
            }, weighted())
        })
        wordDetail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(wordDetail)
        body.addView(label("言葉 · Vocabulary", 16f).apply { setPadding(0, dp(18), 0, dp(6)) })
        vocabulary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(vocabulary)

        body.addView(label("文法 · Understand the sentence", 16f).apply { setPadding(0, dp(22), 0, dp(8)) })
        explainButton = button("Explain with Claude") { explain() }
        body.addView(explainButton)
        body.addView(label("Sends the Japanese shown above to Claude when you ask.", 12f, MUTED))
        explanation = label("", 16f).apply {
            text = ReaderExplanation.render(lastAnswer)
            setTextIsSelectable(true); setLineSpacing(dp(4).toFloat(), 1.1f)
        }
        body.addView(explanation, spaced())
        question = EditText(this).apply {
            hint = "Ask about this sentence…"; textSize = 15f
            setTextColor(INK); setHintTextColor(MUTED)
            maxLines = 3; filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        }
        body.addView(question)
        askButton = button("Ask Claude") { explain(question.text.toString().trim()) }
        body.addView(askButton)

        val scroll = ScrollView(this).apply { isFillViewport = false; addView(body) }
        setContentView(scroll)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        window.setLayout(minOf(metrics.widthPixels - dp(20), dp(650)), (metrics.heightPixels * 0.88f).toInt())
    }

    private fun showWelcome() {
        status.setText(R.string.jp_reader_welcome)
        sentenceLabel.setText(R.string.jp_reader_no_text)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) status.append(" Screen capture requires Android 11 or later.")
        explainButton.isEnabled = false; askButton.isEnabled = false
    }

    private fun loadCapture(readText: Boolean) {
        val id = captureId ?: return
        if (!id.matches(Regex("[a-f0-9-]{36}"))) { status.setText(R.string.jp_reader_invalid_capture); return }
        if (readText) {
            status.setText(R.string.jp_reader_ocr_loading)
            explainButton.isEnabled = false; askButton.isEnabled = false
        }
        captureRead = lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(cacheDir, "reader/$id.png").path) }
                    ?: error("This capture has expired. Double-tap the back to capture it again.")
                val thumbnail = ImageView(this@ReaderActivity).apply {
                    setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Original Japanese screen region"
                }
                val previewWidth = minOf(resources.displayMetrics.widthPixels - dp(64), dp(606))
                val previewHeight = (previewWidth.toFloat() * bitmap.height / bitmap.width).toInt()
                    .coerceIn(dp(28), dp(130))
                body.addView(thumbnail, 3, LinearLayout.LayoutParams(-1, previewHeight))
                if (!readText) return@launch
                // Keep the bitmap alive until ML Kit completes, even if the activity is destroyed.
                source = ReaderOcr.read(bitmap).take(MAX_SOURCE)
                if (source.isBlank()) {
                    status.setText(R.string.jp_reader_ocr_empty)
                } else updateSource()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status.text = error.message ?: "Could not read this region. Try again." }
        }
    }

    private fun updateSource(reset: Boolean = true) {
        if (reset) {
            sentenceIndex = 0; clearTutor()
        }
        sentences = ReaderText.sentences(source)
        sentenceIndex = sentenceIndex.coerceIn(-1, (sentences.size - 1).coerceAtLeast(0))
        editor.setText(source)
        editor.visibility = View.GONE; sourceActions.visibility = View.GONE
        status.setText(R.string.jp_reader_source_hint)
        showSentence(clearExplanation = reset)
    }

    private fun moveSentence(delta: Int) {
        if (sentences.isEmpty()) return
        sentenceIndex = (sentenceIndex + delta).coerceIn(0, sentences.lastIndex)
        showSentence()
    }

    private fun currentText(): String = if (sentenceIndex == -1) source else sentences.getOrNull(sentenceIndex).orEmpty()

    private fun showSentence(clearExplanation: Boolean = true) {
        analysis?.cancel()
        if (clearExplanation) { clearTutor(); Tts.stop() }
        val text = currentText()
        rendered.text = text
        sentenceLabel.text = if (sentenceIndex == -1) "All text" else "${sentenceIndex + 1} / ${sentences.size}"
        explainButton.isEnabled = text.isNotBlank(); askButton.isEnabled = text.isNotBlank()
        wordDetail.removeAllViews(); vocabulary.removeAllViews()
        if (text.isBlank()) return
        vocabulary.addView(label("Looking up words on your phone…", 13f, MUTED))
        analysis = lifecycleScope.launch {
            try {
                val (words, readings) = withContext(Dispatchers.Default) { ReaderLanguage.words(text) to ReaderLanguage.readings(text) }
                val annotated = SpannableString(text)
                words.forEach { word ->
                    annotated.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) { showWord(word) }
                        override fun updateDrawState(ds: android.text.TextPaint) { ds.color = INK; ds.isUnderlineText = false }
                    }, word.start, word.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (prefs.furigana) readings.forEach { word ->
                    word.reading?.let { annotated.setSpan(RubySpan(it), word.start, word.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
                rendered.text = annotated
                val entries = withContext(Dispatchers.IO) {
                    words.filter { it.pos in setOf("名詞", "動詞", "形容詞", "副詞", "形容動詞", "連体詞", "接続詞") }
                        .distinctBy { it.base }.take(30).map { it to JpDict.exact(this@ReaderActivity, it.base, 1).firstOrNull() }
                }
                vocabulary.removeAllViews()
                entries.forEach { (word, entry) ->
                    vocabulary.addView(label(if (entry == null) "${word.surface}  ·  ${word.base}"
                        else "${word.surface}  ·  ${word.reading ?: entry.reading}\n${entry.gloss}", 15f).apply {
                        setPadding(0, dp(9), 0, dp(9)); maxLines = 4
                        setOnClickListener { showWord(word) }
                        contentDescription = "Look up ${word.surface}"
                    })
                }
                if (entries.isEmpty()) vocabulary.addView(label("Tap a word above, or edit the captured text.", 13f, MUTED))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { vocabulary.removeAllViews(); vocabulary.addView(label("Word analysis unavailable. Edit the source or try again.", 13f, MUTED)) }
        }
    }

    private fun showWord(word: ReaderLanguage.Word) {
        wordDetail.removeAllViews()
        wordDetail.addView(label("${word.surface} → ${word.base}", 21f))
        wordDetail.addView(label(listOfNotNull(word.reading, word.pos).joinToString(" · "), 14f, MUTED))
        val result = label("Looking up…", 16f)
        wordDetail.addView(result)
        wordDetail.addView(row().apply {
            addView(button("▶ Word") { speak(word.surface, 1.0) })
            addView(button("Dictionary ↗") { JpDictRowView.open(this@ReaderActivity, word.base) })
        })
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { JpDict.exact(this@ReaderActivity, word.base) }
            result.text = if (entries.isEmpty()) "No offline dictionary entry. Try Dictionary ↗ or ask Claude about this word."
            else entries.joinToString("\n\n") { "${it.head}【${it.reading}】\n${it.pos}\n${it.gloss}" }
        }
    }

    private fun speak(text: String, speed: Double) {
        if (text.isBlank()) return
        if (text.length > 1600) { toast("Choose a sentence to listen to, rather than the whole page."); return }
        status.setText(R.string.jp_reader_audio_loading)
        Tts.speakReader(applicationContext, text, speed,
            onError = { message -> runOnUiThread { status.text = getString(R.string.jp_reader_audio_error, message) } },
            onDone = { runOnUiThread { status.setText(R.string.jp_reader_audio_done) } })
    }

    private fun clearTutor() {
        claude?.cancel(); claude = null
        turns.clear(); explanationSource = ""; lastAnswer = ""
        if (::explanation.isInitialized) explanation.text = ""
        if (::explainButton.isInitialized) explainButton.setText(R.string.jp_reader_explain)
    }

    private fun explain(followup: String = "") {
        val text = currentText()
        if (text.isBlank()) return
        if (text.length > 4000) { toast("Choose a sentence or a smaller region for Claude."); return }
        if (prefs.claudeKey.isBlank()) { settings(); toast("Add your Claude API key to enable explanations."); return }
        if (claude?.isActive == true) return
        if (followup.isBlank()) { turns.clear(); explanationSource = text }
        else if (explanationSource != text || turns.isEmpty()) {
            turns.clear(); explanationSource = text
        }
        // Limit carried conversation; never silently change the source being discussed.
        if (turns.size >= 16) { toast("Start a fresh explanation before asking more questions."); return }
        val requestTurns = turns.toMutableList().apply { if (followup.isNotBlank()) add(ReaderClaude.Turn("user", followup)) }
        explainButton.isEnabled = false; askButton.isEnabled = false
        explainButton.setText(R.string.jp_reader_thinking)
        claude = lifecycleScope.launch {
            try {
                val answer = ReaderClaude.explain(prefs.claudeKey, prefs.model, text, requestTurns)
                turns.clear(); turns.addAll(requestTurns); turns.add(ReaderClaude.Turn("assistant", answer))
                lastAnswer = if (followup.isBlank()) answer else "$lastAnswer\n\nYou: $followup\n\n$answer"
                explanation.text = ReaderExplanation.render(lastAnswer)
                question.setText("")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { toast(error.message ?: "Claude is unavailable. Please try again.") }
            finally {
                explainButton.setText(R.string.jp_reader_explain)
                explainButton.isEnabled = currentText().isNotBlank(); askButton.isEnabled = currentText().isNotBlank()
            }
        }
    }

    private fun settings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val backTap = android.widget.CheckBox(this).apply {
            setText(R.string.jp_reader_back_tap)
            isChecked = prefs.backTap
        }
        content.addView(backTap)
        val sensitivity = android.widget.RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("Firm knock", "Normal", "Light knock").forEachIndexed { index, name ->
                addView(android.widget.RadioButton(this@ReaderActivity).apply { text = name; id = 100 + index })
            }
            check(100 + prefs.backTapSensitivity)
        }
        content.addView(sensitivity)
        // Live test: the panel runs its own listener; the service's knocks are muted meanwhile.
        val test = label(getString(R.string.jp_reader_back_tap_test), 13f, MUTED).apply { setPadding(0, dp(4), 0, dp(8)) }
        content.addView(test)
        val knocks = ArrayDeque<String>()
        fun thresholdFor(id: Int) = when (id) {
            100 -> BackTapDetector.SENSITIVITY_LOW; 102 -> BackTapDetector.SENSITIVITY_HIGH
            else -> BackTapDetector.SENSITIVITY_MEDIUM
        }
        val probe = BackTapSensor(this,
            onDoubleTap = { knocks.addLast("✓ double tap"); test.text = knocks.joinToString("   ") },
            onKnock = { peak ->
                knocks.addLast("%.1f".format(peak)); while (knocks.size > 8) knocks.removeFirst()
                test.text = knocks.joinToString("   ")
            })
        ReaderOverlay.calibrating = true
        if (probe.available) probe.start(thresholdFor(sensitivity.checkedRadioButtonId), screenGated = false)
        else test.setText(R.string.jp_reader_back_tap_unavailable)
        sensitivity.setOnCheckedChangeListener { _, id -> probe.start(thresholdFor(id), screenGated = false) }
        val enabled = android.widget.CheckBox(this).apply {
            setText(R.string.jp_reader_floating)
            isChecked = prefs.floating
        }
        content.addView(enabled)
        content.addView(label("Capture and word lookups stay on your phone. Listening sends the selected Japanese to AivisSpeech. Explanations send it to Claude only when requested.", 13f, MUTED))
        content.addView(button("Enable Supernova accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(label("Claude API key", 13f, MUTED))
        val key = EditText(this).apply {
            setSingleLine(); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            setText(prefs.claudeKey); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        content.addView(key)
        content.addView(label("Claude model", 13f, MUTED))
        val model = EditText(this).apply { setSingleLine(); setText(prefs.model) }
        content.addView(model)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("読む · Reader settings").setView(content)
            .setOnDismissListener { probe.stop(); ReaderOverlay.calibrating = false }
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                prefs.backTap = backTap.isChecked
                prefs.backTapSensitivity = sensitivity.checkedRadioButtonId - 100
                prefs.floating = enabled.isChecked; prefs.claudeKey = key.text.toString(); prefs.model = model.text.toString()
                SupernovaAccessibilityService.refreshReader()
                if ((prefs.floating || prefs.backTap) && !SupernovaAccessibilityService.isEnabled(this)) {
                    toast("Enable Supernova JP in Accessibility, then double-tap the back over your Japanese content.")
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("source", source); outState.putString("capture", captureId)
        outState.putString("draft", editor.text.toString()); outState.putBoolean("editing", editor.isVisible)
        outState.putInt("sentence", sentenceIndex); outState.putString("answer", lastAnswer)
        outState.putString("explanation_source", explanationSource)
        outState.putStringArrayList("turns", ArrayList(turns.flatMap { listOf(it.role, it.text) }))
        super.onSaveInstanceState(outState)
    }

    override fun onStop() { Tts.stop(); super.onStop() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) }
    private fun label(value: String, size: Float, color: Int = INK) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 13f; setTextColor(INK)
        minWidth = dp(48); minimumWidth = dp(48); minHeight = dp(48)
        setPadding(dp(8), 0, dp(8), 0)
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE3E5D8.toInt())
        setOnClickListener { action() }
    }

    companion object {
        const val EXTRA_CAPTURE = "jp.reader.capture"
        private const val MAX_SOURCE = 12000
        private val PAPER = Color.rgb(247, 243, 232)
        private val INK = Color.rgb(38, 59, 51)
        private val MUTED = Color.rgb(99, 106, 91)
    }
}
