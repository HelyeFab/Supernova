package com.supernova.launcher.jp.reader

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.PasswordTransformationMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.supernova.launcher.R
import com.supernova.launcher.SupernovaAccessibilityService
import com.supernova.launcher.jp.cards.RubySpan
import com.supernova.launcher.jp.dict.JpDict
import com.supernova.launcher.jp.dict.JpDictRowView
import com.supernova.launcher.jp.reader.ReaderUi.ACCENT
import com.supernova.launcher.jp.reader.ReaderUi.INK
import com.supernova.launcher.jp.reader.ReaderUi.LINE
import com.supernova.launcher.jp.reader.ReaderUi.MUTED
import com.supernova.launcher.jp.reader.ReaderUi.ON_ACCENT
import com.supernova.launcher.jp.reader.ReaderUi.Pill
import com.supernova.launcher.jp.reader.ReaderUi.RAISED
import com.supernova.launcher.jp.reader.ReaderUi.SHEET
import com.supernova.launcher.jp.reader.ReaderUi.dp
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
    private lateinit var scroll: ScrollView
    private lateinit var page: LinearLayout
    private lateinit var thumbSlot: FrameLayout
    private lateinit var rendered: TextView
    private lateinit var editor: EditText
    private lateinit var editPanel: LinearLayout
    private lateinit var status: TextView
    private lateinit var sentenceBar: SentenceBar
    private lateinit var sentenceLabel: TextView
    private lateinit var wholeText: TextView
    private lateinit var furiganaChip: TextView
    private lateinit var vocabulary: LinearLayout
    private lateinit var wordDetail: LinearLayout
    private lateinit var explanation: TextView
    private lateinit var explainButton: TextView
    private lateinit var question: EditText
    private lateinit var askButton: TextView
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
    /** Leaves the settings page (saving it); null while the reader page is showing. */
    private var closeSettings: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = ReaderPrefs(this)
        source = savedInstanceState?.getString("source") ?: incomingText(intent)
        captureId = savedInstanceState?.getString("capture") ?: intent.getStringExtra(EXTRA_CAPTURE)
        sentenceIndex = savedInstanceState?.getInt("sentence") ?: 0
        lastAnswer = savedInstanceState?.getString("answer").orEmpty()
        explanationSource = savedInstanceState?.getString("explanation_source").orEmpty()
        savedInstanceState?.getStringArrayList("turns")?.chunked(2)?.filter { it.size == 2 }
            ?.forEach { turns.add(ReaderClaude.Turn(it[0], it[1])) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { closeSettings?.invoke() ?: finish() }
        })
        buildUi()
        if (source.isNotBlank()) updateSource(reset = false)
        else if (captureId == null) showWelcome()
        if (captureId != null) loadCapture(readText = source.isBlank())
        savedInstanceState?.getString("draft")?.let { editor.setText(it) }
        if (savedInstanceState?.getBoolean("editing") == true) editPanel.isVisible = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A capture reuses this dialog task. Consume the new capture instead of showing
        // the previous task's root screen (Android NEW_TASK can bring that task back).
        analysis?.cancel(); claude?.cancel(); captureRead?.cancel(); Tts.stop()
        turns.clear(); explanationSource = ""; lastAnswer = ""; sentenceIndex = 0
        sentences = emptyList()
        source = incomingText(intent)
        captureId = intent.getStringExtra(EXTRA_CAPTURE)
        buildUi()
        if (source.isNotBlank()) updateSource()
        else if (captureId == null) showWelcome()
        if (captureId != null) loadCapture(readText = source.isBlank())
    }

    private fun incomingText(intent: Intent) = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        else -> null
    }.orEmpty().take(MAX_SOURCE)

    // ---- Reader page -------------------------------------------------------------------

    private fun buildUi() {
        closeSettings?.invoke()
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(26))
        }
        page.addView(header(titleView("読む", 26f).apply { typeface = Typeface.DEFAULT_BOLD }) {
            addView(ReaderUi.iconButton(context, R.drawable.jp_ic_tune, "Reader settings") { settings() }, square(44))
            addView(space(8))
            addView(ReaderUi.iconButton(context, R.drawable.jp_ic_close, "Close") { finish() }, square(44))
        })

        thumbSlot = FrameLayout(this)
        page.addView(thumbSlot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        // The sentence: the one thing on this screen allowed to be big.
        rendered = ReaderUi.text(this, 27f).apply {
            movementMethod = LinkMovementMethod.getInstance()
            setLineSpacing(textSize * RubySpan.FURIGANA_SPACING, 1.05f)
            setPadding(0, (textSize * RubySpan.FURIGANA_SPACING).toInt() + dp(8), 0, dp(6))
            highlightColor = 0x44F4B3A6
        }
        page.addView(rendered)

        editPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isVisible = false }
        editor = EditText(this).apply {
            hint = "Paste or type Japanese"
            textSize = 19f; setTextColor(INK); setHintTextColor(MUTED)
            minLines = 3; maxLines = 9; gravity = Gravity.TOP
            background = ReaderUi.rounded(RAISED, dp(16).toFloat())
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_SOURCE))
            setText(source)
        }
        editPanel.addView(editor, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        editPanel.addView(ReaderUi.row(this).apply {
            addView(ReaderUi.pill(context, "Read this", Pill.PRIMARY) {
                source = editor.text.toString().trim()
                if (source.isBlank()) { toast("Type or paste some Japanese first."); return@pill }
                hideKeyboard(); updateSource()
            }, weighted())
            addView(space(10))
            addView(ReaderUi.pill(context, "Read the selected part") {
                val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                if (end <= start) { toast("Select part of the text first."); return@pill }
                source = editor.text.substring(start, end); hideKeyboard(); updateSource()
            }, weighted())
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        page.addView(editPanel)

        // Where am I in the text: one segment per sentence.
        val position = ReaderUi.row(this)
        position.addView(stepper("‹", "Previous sentence") { moveSentence(-1) })
        sentenceBar = SentenceBar(this) { sentenceIndex = it; showSentence() }
        position.addView(sentenceBar, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginStart = dp(6); marginEnd = dp(6) })
        position.addView(stepper("›", "Next sentence") { moveSentence(1) })
        page.addView(position, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        sentenceLabel = ReaderUi.text(this, 13f, MUTED)
        wholeText = ReaderUi.link(this, "") { toggleAll() }.apply { setPadding(dp(12), dp(6), dp(4), dp(6)) }
        page.addView(ReaderUi.row(this).apply {
            setPadding(dp(4), 0, 0, 0)
            addView(sentenceLabel, LinearLayout.LayoutParams(0, -2, 1f))
            addView(wholeText)
        })

        val controls = ReaderUi.row(this)
        controls.addView(ReaderUi.pill(this, "▶  Listen", Pill.PRIMARY) { speak(currentText(), 1.0) }, weighted())
        controls.addView(space(8))
        controls.addView(ReaderUi.pill(this, "Slow") { speak(currentText(), 0.8) })
        controls.addView(space(8))
        controls.addView(ReaderUi.pill(this, "■") { Tts.stop(); status.text = "" }.apply { contentDescription = "Stop audio" })
        controls.addView(space(8))
        furiganaChip = ReaderUi.pill(this, "あ") {
            prefs.furigana = !prefs.furigana; styleFuriganaChip(); showSentence(clearExplanation = false)
        }.apply { contentDescription = "Show readings above kanji" }
        styleFuriganaChip()
        controls.addView(furiganaChip)
        page.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        status = ReaderUi.text(this, 13f, MUTED).apply { setPadding(0, dp(10), 0, 0) }
        page.addView(status)

        wordDetail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(wordDetail)

        page.addView(ReaderUi.sectionTitle(this, "Words"))
        vocabulary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(vocabulary)

        page.addView(ReaderUi.sectionTitle(this, "Grammar"))
        explainButton = ReaderUi.pill(this, getString(R.string.jp_reader_explain), Pill.QUIET) { explain() }
        page.addView(explainButton, LinearLayout.LayoutParams(-1, -2))
        explanation = ReaderUi.text(this, 16f).apply {
            text = ReaderExplanation.render(lastAnswer)
            setTextIsSelectable(true); setLineSpacing(dp(5).toFloat(), 1.1f)
            setPadding(0, dp(14), 0, dp(4))
            isVisible = lastAnswer.isNotBlank()
        }
        page.addView(explanation)
        val ask = ReaderUi.row(this).apply {
            background = ReaderUi.rounded(RAISED, dp(26).toFloat())
            setPadding(dp(16), dp(4), dp(5), dp(4))
        }
        question = EditText(this).apply {
            hint = "Ask about this sentence"; textSize = 15f
            setTextColor(INK); setHintTextColor(MUTED); background = null
            maxLines = 3; filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        }
        ask.addView(question, LinearLayout.LayoutParams(0, -2, 1f))
        askButton = ReaderUi.pill(this, "Ask", Pill.PRIMARY) { explain(question.text.toString().trim()) }
        ask.addView(askButton, LinearLayout.LayoutParams(-2, dp(42)))
        page.addView(ask, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        page.addView(ReaderUi.row(this).apply {
            addView(ReaderUi.link(context, "Edit text") {
                editPanel.isVisible = true; editor.setText(source); editor.requestFocus()
            })
            addView(ReaderUi.link(context, "Share") {
                if (currentText().isNotBlank()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentText()), "Share Japanese"))
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        scroll = ScrollView(this).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false
            background = ReaderUi.rounded(SHEET, dp(28).toFloat(), dp(1), LINE)
            clipToOutline = true
            addView(page)
        }
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
        rendered.text = getString(R.string.jp_reader_empty_title)
        rendered.textSize = 22f
        status.setText(R.string.jp_reader_welcome)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) status.append(" Reading other apps needs Android 11 or later.")
        editPanel.isVisible = true
        sentenceBar.count = 0; sentenceLabel.text = ""; wholeText.isVisible = false
        explainButton.isEnabled = false; askButton.isEnabled = false
        vocabulary.addView(ReaderUi.text(this, 14f, MUTED, "Words from the sentence appear here."))
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
                // The capture, as a quiet strip: proof of what was read, not the focus.
                val thumbnail = ImageView(this@ReaderActivity).apply {
                    setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP
                    alpha = 0.9f; contentDescription = "The captured part of the screen"
                    background = ReaderUi.rounded(RAISED, dp(16).toFloat()); clipToOutline = true
                }
                val width = minOf(resources.displayMetrics.widthPixels - dp(64), dp(606))
                val height = (width.toFloat() * bitmap.height / bitmap.width).toInt().coerceIn(dp(36), dp(120))
                thumbSlot.removeAllViews()
                thumbSlot.addView(thumbnail, FrameLayout.LayoutParams(-1, height))
                if (!readText) return@launch
                // Keep the bitmap alive until ML Kit completes, even if the activity is destroyed.
                source = ReaderOcr.read(bitmap).take(MAX_SOURCE)
                if (source.isBlank()) {
                    status.setText(R.string.jp_reader_ocr_empty)
                    editPanel.isVisible = true
                } else updateSource()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status.text = error.message ?: "Couldn't read this region. Try a tighter box." }
        }
    }

    private fun updateSource(reset: Boolean = true) {
        if (reset) { sentenceIndex = 0; clearTutor() }
        sentences = ReaderText.sentences(source)
        sentenceIndex = sentenceIndex.coerceIn(-1, (sentences.size - 1).coerceAtLeast(0))
        editor.setText(source)
        editPanel.isVisible = false
        rendered.textSize = 27f
        status.text = ""
        showSentence(clearExplanation = reset)
    }

    private fun moveSentence(delta: Int) {
        if (sentences.isEmpty()) return
        sentenceIndex = (sentenceIndex.coerceAtLeast(0) + delta).coerceIn(0, sentences.lastIndex)
        showSentence()
    }

    private fun toggleAll() {
        if (sentences.isEmpty()) return
        sentenceIndex = if (sentenceIndex == -1) 0 else -1
        showSentence()
    }

    private fun currentText(): String = if (sentenceIndex == -1) source else sentences.getOrNull(sentenceIndex).orEmpty()

    private fun showSentence(clearExplanation: Boolean = true) {
        analysis?.cancel()
        if (clearExplanation) { clearTutor(); Tts.stop() }
        val text = currentText()
        rendered.text = text
        sentenceBar.count = sentences.size; sentenceBar.current = sentenceIndex
        sentenceLabel.text = when {
            sentences.isEmpty() -> ""
            sentenceIndex == -1 -> "The whole text"
            sentences.size == 1 -> "One sentence"
            else -> "Sentence ${sentenceIndex + 1} of ${sentences.size}"
        }
        wholeText.isVisible = sentences.size > 1
        wholeText.text = if (sentenceIndex == -1) "One at a time" else "Whole text"
        explainButton.isEnabled = text.isNotBlank(); askButton.isEnabled = text.isNotBlank()
        wordDetail.removeAllViews(); vocabulary.removeAllViews()
        if (text.isBlank()) return
        vocabulary.addView(ReaderUi.text(this, 14f, MUTED, "Looking up words…"))
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
                    word.reading?.let { annotated.setSpan(RubySpan(it, rubyColor = ReaderUi.RUBY), word.start, word.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
                rendered.text = annotated
                val entries = withContext(Dispatchers.IO) {
                    words.filter { it.pos in CONTENT_POS }
                        .distinctBy { it.base }.take(30).map { it to JpDict.exact(this@ReaderActivity, it.base, 1).firstOrNull() }
                }
                vocabulary.removeAllViews()
                entries.forEachIndexed { index, (word, entry) ->
                    if (index > 0) vocabulary.addView(ReaderUi.divider(this@ReaderActivity), LinearLayout.LayoutParams(-1, 1))
                    vocabulary.addView(wordRow(word, entry))
                }
                if (entries.isEmpty()) vocabulary.addView(ReaderUi.text(this@ReaderActivity, 14f, MUTED, "No dictionary words here. Tap the sentence to try a word."))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                vocabulary.removeAllViews()
                vocabulary.addView(ReaderUi.text(this@ReaderActivity, 14f, MUTED, "Couldn't split this into words. Edit the text and try again."))
            }
        }
    }

    /** Word on the left (with its reading under it), meaning on the right. */
    private fun wordRow(word: ReaderLanguage.Word, entry: JpDict.Entry?) = ReaderUi.row(this).apply {
        gravity = Gravity.TOP
        setPadding(0, dp(12), 0, dp(12))
        background = ReaderUi.pressable(0, dp(8).toFloat())
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        left.addView(ReaderUi.text(context, 19f, INK, word.base))
        // The dictionary form's reading: できる, not できました.
        left.addView(ReaderUi.text(context, 13f, MUTED, entry?.reading ?: word.reading.orEmpty()))
        addView(left, LinearLayout.LayoutParams(dp(112), -2))
        addView(ReaderUi.text(context, 14f, if (entry == null) MUTED else INK,
            entry?.gloss ?: "Not in the offline dictionary").apply {
                maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        setOnClickListener { showWord(word) }
        contentDescription = "Look up ${word.base}"
    }

    private fun showWord(word: ReaderLanguage.Word) {
        wordDetail.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ReaderUi.rounded(RAISED, dp(20).toFloat())
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        card.addView(ReaderUi.row(this).apply {
            addView(ReaderUi.text(context, 26f, INK, word.base))
            addView(ReaderUi.text(context, 15f, MUTED, listOfNotNull(word.reading, if (word.surface != word.base) "as ${word.surface}" else null)
                .joinToString("   ")).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ReaderUi.iconButton(context, R.drawable.jp_ic_close, "Close word") { wordDetail.removeAllViews() }.apply {
                background = null; imageTintList = ColorStateList.valueOf(MUTED)
            }, square(36))
        })
        val result = ReaderUi.text(this, 15f, INK, "Looking up…").apply { setPadding(0, dp(8), 0, dp(6)); setLineSpacing(dp(3).toFloat(), 1f) }
        card.addView(result)
        card.addView(ReaderUi.row(this).apply {
            addView(ReaderUi.link(context, "Listen") { speak(word.surface, 1.0) })
            addView(ReaderUi.link(context, "Open in dictionary") { JpDictRowView.open(this@ReaderActivity, word.base) })
        })
        wordDetail.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { JpDict.exact(this@ReaderActivity, word.base) }
            result.text = if (entries.isEmpty()) "Not in the offline dictionary. Open the dictionary, or ask about it below."
            else entries.joinToString("\n\n") { "${it.head}【${it.reading}】  ${it.pos}\n${it.gloss}" }
        }
    }

    private fun speak(text: String, speed: Double) {
        if (text.isBlank()) return
        if (text.length > 1600) { toast("Pick one sentence to listen to, not the whole page."); return }
        status.setText(R.string.jp_reader_audio_loading)
        Tts.speakReader(applicationContext, text, speed,
            onError = { message -> runOnUiThread { status.text = getString(R.string.jp_reader_audio_error, message) } },
            onDone = { runOnUiThread { status.text = "" } })
    }

    private fun clearTutor() {
        claude?.cancel(); claude = null
        turns.clear(); explanationSource = ""; lastAnswer = ""
        if (::explanation.isInitialized) { explanation.text = ""; explanation.isVisible = false }
        if (::explainButton.isInitialized) explainButton.setText(R.string.jp_reader_explain)
    }

    private fun explain(followup: String = "") {
        val text = currentText()
        if (text.isBlank()) return
        if (text.length > 4000) { toast("Pick one sentence, or capture a smaller area."); return }
        if (prefs.claudeKey.isBlank()) { settings(); toast("Add your Claude API key to get explanations."); return }
        if (claude?.isActive == true) return
        if (followup.isBlank()) { turns.clear(); explanationSource = text }
        else if (explanationSource != text || turns.isEmpty()) { turns.clear(); explanationSource = text }
        // Limit carried conversation; never silently change the source being discussed.
        if (turns.size >= 16) { toast("Start a new explanation before asking more."); return }
        val requestTurns = turns.toMutableList().apply { if (followup.isNotBlank()) add(ReaderClaude.Turn("user", followup)) }
        explainButton.isEnabled = false; askButton.isEnabled = false
        explainButton.setText(R.string.jp_reader_thinking)
        claude = lifecycleScope.launch {
            try {
                val answer = ReaderClaude.explain(prefs.claudeKey, prefs.model, text, requestTurns)
                turns.clear(); turns.addAll(requestTurns); turns.add(ReaderClaude.Turn("assistant", answer))
                lastAnswer = if (followup.isBlank()) answer else "$lastAnswer\n\nYou: $followup\n\n$answer"
                explanation.text = ReaderExplanation.render(lastAnswer); explanation.isVisible = true
                question.setText("")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { toast(error.message ?: "Claude didn't answer. Try again.") }
            finally {
                explainButton.setText(R.string.jp_reader_explain)
                explainButton.isEnabled = currentText().isNotBlank(); askButton.isEnabled = currentText().isNotBlank()
            }
        }
    }

    // ---- Settings page -----------------------------------------------------------------

    /**
     * Settings replace the reader page inside the same sheet (an AlertDialog here crashed
     * the launcher process: the sheet's floating theme can't resolve textColorLink).
     * Everything saves as it changes; the fields save when you leave the page.
     */
    private fun settings() {
        if (closeSettings != null) return
        hideKeyboard()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(28))
        }
        val key = field(prefs.claudeKey, secret = true)
        val model = field(prefs.model, secret = false)
        val meter = KnockMeter(this).apply { threshold = prefs.backTapThreshold }
        // Live test: the page runs its own listener; the service's knocks are muted meanwhile.
        val probe = BackTapSensor(this, onDoubleTap = { meter.double() }, onKnock = { meter.knock(it) })

        fun leave() {
            probe.stop(); ReaderOverlay.calibrating = false
            prefs.claudeKey = key.text.toString(); prefs.model = model.text.toString()
            hideKeyboard()
            closeSettings = null
            scroll.removeAllViews(); scroll.addView(page); scroll.scrollTo(0, 0)
        }
        closeSettings = ::leave

        panel.addView(header(titleView("Settings", 26f).apply { typeface = ReaderUi.HEADING }) {
            addView(ReaderUi.iconButton(context, R.drawable.jp_ic_close, "Done") { leave() }, square(44))
        })

        panel.addView(group("Opening the reader"))
        val strength = segmented(listOf("Firm", "Normal", "Light"), prefs.backTapSensitivity) { index ->
            prefs.backTapSensitivity = index
            meter.threshold = prefs.backTapThreshold
            probe.start(prefs.backTapThreshold, screenGated = false)
            SupernovaAccessibilityService.refreshReader()
        }
        val strengthBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ReaderUi.text(context, 14f, MUTED, "How hard a knock has to be").apply { setPadding(0, dp(4), 0, dp(8)) })
            addView(strength, LinearLayout.LayoutParams(-1, dp(44)))
            addView(meter, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            isVisible = prefs.backTap
        }
        panel.addView(switchRow("Double-tap the back of the phone", "Opens the reader over whatever is on screen", prefs.backTap) { on ->
            prefs.backTap = on; strengthBlock.isVisible = on
            SupernovaAccessibilityService.refreshReader()
        })
        panel.addView(strengthBlock)
        panel.addView(ReaderUi.divider(this), LinearLayout.LayoutParams(-1, 1).apply { topMargin = dp(18) })
        panel.addView(switchRow("Floating 読 button", "A small button that stays over other apps", prefs.floating) { on ->
            prefs.floating = on; SupernovaAccessibilityService.refreshReader()
        })
        panel.addView(ReaderUi.divider(this), LinearLayout.LayoutParams(-1, 1))
        val enabled = SupernovaAccessibilityService.isEnabled(this)
        panel.addView(actionRow("Supernova accessibility",
            if (enabled) "On. Needed to read other apps." else "Off. Turn it on to read other apps.",
            if (enabled) "Open" else "Turn on") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        panel.addView(group("Claude explanations"))
        panel.addView(fieldLabel("API key")); panel.addView(key)
        panel.addView(fieldLabel("Model")); panel.addView(model)

        panel.addView(ReaderUi.text(this, 13f, MUTED,
            "Reading the screen and looking up words happen on your phone. When you listen, the Japanese goes to " +
                "AivisSpeech; when you ask for an explanation, it goes to Claude. Screenshots never leave the phone.").apply {
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(28), 0, 0)
        })

        ReaderOverlay.calibrating = true
        if (probe.available) probe.start(prefs.backTapThreshold, screenGated = false)
        scroll.removeAllViews(); scroll.addView(panel); scroll.scrollTo(0, 0)
    }

    private fun group(title: String) = ReaderUi.text(this, 19f, INK, title).apply {
        typeface = ReaderUi.HEADING; setPadding(0, dp(26), 0, dp(6))
    }

    private fun switchRow(title: String, subtitle: String, on: Boolean, changed: (Boolean) -> Unit): View {
        val toggle = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = on
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ON_ACCENT, MUTED))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ACCENT, LINE))
            setOnCheckedChangeListener { _, value -> changed(value) }
        }
        return textRow(title, subtitle).apply {
            addView(toggle)
            setOnClickListener { toggle.toggle() }
        }
    }

    private fun actionRow(title: String, subtitle: String, action: String, onClick: () -> Unit) =
        textRow(title, subtitle).apply { addView(ReaderUi.pill(context, action, Pill.SECONDARY, onClick).apply { minHeight = dp(40) }) }

    private fun textRow(title: String, subtitle: String) = ReaderUi.row(this).apply {
        setPadding(0, dp(14), 0, dp(14))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ReaderUi.text(context, 16f, INK, title))
            addView(ReaderUi.text(context, 13f, MUTED, subtitle).apply { setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
    }

    /** Three-way choice, the chosen one filled. */
    private fun segmented(options: List<String>, selected: Int, picked: (Int) -> Unit): LinearLayout {
        val bar = ReaderUi.row(this).apply {
            background = ReaderUi.rounded(RAISED, dp(22).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val cells = options.mapIndexed { index, name ->
            ReaderUi.text(this, 15f, INK, name).apply {
                gravity = Gravity.CENTER; typeface = ReaderUi.MEDIUM
                setOnClickListener { picked(index); select(bar, index) }
            }
        }
        cells.forEach { bar.addView(it, LinearLayout.LayoutParams(0, -1, 1f)) }
        select(bar, selected)
        return bar
    }

    private fun select(bar: LinearLayout, index: Int) {
        for (i in 0 until bar.childCount) (bar.getChildAt(i) as TextView).apply {
            val chosen = i == index
            setTextColor(if (chosen) ON_ACCENT else INK)
            background = if (chosen) ReaderUi.rounded(ACCENT, dp(18).toFloat()) else null
        }
    }

    private fun fieldLabel(text: String) = ReaderUi.text(this, 14f, MUTED, text).apply { setPadding(0, dp(14), 0, dp(6)) }

    private fun field(value: String, secret: Boolean) = EditText(this).apply {
        setSingleLine(); textSize = 16f; setTextColor(INK); setHintTextColor(MUTED)
        background = ReaderUi.rounded(RAISED, dp(14).toFloat())
        setPadding(dp(16), dp(12), dp(16), dp(12))
        if (secret) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        setText(value)
    }

    // ---- Small helpers -----------------------------------------------------------------

    private fun header(title: TextView, actions: LinearLayout.() -> Unit) = ReaderUi.row(this).apply {
        addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        actions()
    }

    private fun titleView(value: String, size: Float) = ReaderUi.text(this, size, INK, value)

    private fun stepper(label: String, description: String, onClick: () -> Unit) = ReaderUi.text(this, 24f, INK, label).apply {
        gravity = Gravity.CENTER; contentDescription = description
        background = ReaderUi.pressable(0, dp(20).toFloat())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
    }

    private fun styleFuriganaChip() {
        val on = prefs.furigana
        furiganaChip.setTextColor(if (on) ON_ACCENT else INK)
        furiganaChip.background = ReaderUi.pressable(if (on) 0xFFE9D9CF.toInt() else RAISED, dp(24).toFloat())
    }

    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("source", source); outState.putString("capture", captureId)
        outState.putString("draft", editor.text.toString()); outState.putBoolean("editing", editPanel.isVisible)
        outState.putInt("sentence", sentenceIndex); outState.putString("answer", lastAnswer)
        outState.putString("explanation_source", explanationSource)
        outState.putStringArrayList("turns", ArrayList(turns.flatMap { listOf(it.role, it.text) }))
        super.onSaveInstanceState(outState)
    }

    override fun onStop() { Tts.stop(); super.onStop() }
    override fun onDestroy() { closeSettings?.invoke(); super.onDestroy() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun square(size: Int) = LinearLayout.LayoutParams(dp(size), dp(size))
    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }

    companion object {
        const val EXTRA_CAPTURE = "jp.reader.capture"
        private const val MAX_SOURCE = 12000
        private val CONTENT_POS = setOf("名詞", "動詞", "形容詞", "副詞", "形容動詞", "連体詞", "接続詞")
    }
}
