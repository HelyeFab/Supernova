package com.supernova.launcher.jp.cards

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import com.supernova.launcher.R
import com.supernova.launcher.jp.dict.JpDictRowView
import com.supernova.launcher.jp.struggle.Tts
import java.time.LocalDate

// JP fork: 今日の名言 - one well-known ことわざ / 四字熟語 a day, with its reading
// and meaning. Tap to hear it; ↻ shows another; ↗ looks it up in Akebi.
@SuppressLint("ViewConstructor")
class ProverbView(context: Context) : CardBase(context, "今日の名言 · saying of the day") {

    private class Saying(val jp: String, val kana: String, val en: String)

    private val jp = text(22f).apply { gravity = Gravity.CENTER }
    private val kana = text(13f, ACCENT).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }
    private val en = text(13f, SUB).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }
    private var current: Saying? = null

    init {
        body.gravity = Gravity.CENTER
        listOf(jp, kana, en).forEach { body.addView(it) }
        button(R.drawable.jp_ic_refresh) {
            val prefs = prefs()
            prefs.edit().putInt(skipKey(), prefs.getInt(skipKey(), 0) + 1).apply()
            load()
        }
        button(R.drawable.jp_ic_expand) { current?.let { JpDictRowView.open(context, it.jp) } }
        body.setOnClickListener { current?.let { Tts.speak(context.applicationContext, it.kana) } }
    }

    override fun load() {
        val day = LocalDate.now().toEpochDay()
        val s = SAYINGS[Math.floorMod(day * 17 + prefs().getInt(skipKey(), 0) * 7, SAYINGS.size.toLong()).toInt()]
        current = s
        jp.text = s.jp
        kana.text = s.kana
        en.text = s.en
    }

    private fun prefs() = context.getSharedPreferences("jp_proverb", Context.MODE_PRIVATE)
    private fun skipKey() = "skip_${LocalDate.now().toEpochDay()}"

    companion object {
        const val KIND = "jp-proverb"

        private val SAYINGS = listOf(
            Saying("七転び八起き", "ななころびやおき", "Fall seven times, get up eight."),
            Saying("石の上にも三年", "いしのうえにもさんねん", "Three years on a cold stone - perseverance wins."),
            Saying("継続は力なり", "けいぞくはちからなり", "Persistence is power."),
            Saying("塵も積もれば山となる", "ちりもつもればやまとなる", "Even dust, piled up, becomes a mountain."),
            Saying("千里の道も一歩から", "せんりのみちもいっぽから", "A journey of a thousand ri begins with one step."),
            Saying("急がば回れ", "いそがばまわれ", "If you're in a hurry, take the long way round."),
            Saying("一期一会", "いちごいちえ", "One time, one meeting - treasure every encounter."),
            Saying("猿も木から落ちる", "さるもきからおちる", "Even monkeys fall from trees."),
            Saying("十人十色", "じゅうにんといろ", "Ten people, ten colours - everyone is different."),
            Saying("案ずるより産むが易し", "あんずるよりうむがやすし", "Doing it is easier than worrying about it."),
            Saying("好きこそものの上手なれ", "すきこそもののじょうずなれ", "You get good at what you love."),
            Saying("習うより慣れよ", "ならうよりなれよ", "Learn by doing, not just by studying."),
            Saying("失敗は成功のもと", "しっぱいはせいこうのもと", "Failure is the seed of success."),
            Saying("笑う門には福来る", "わらうかどにはふくきたる", "Good fortune comes to a home full of laughter."),
            Saying("雨降って地固まる", "あめふってじかたまる", "After the rain, the ground hardens."),
            Saying("為せば成る", "なせばなる", "If you do it, it can be done."),
            Saying("初心忘るべからず", "しょしんわするべからず", "Never forget your beginner's spirit."),
            Saying("能ある鷹は爪を隠す", "のうあるたかはつめをかくす", "The talented hawk hides its claws."),
            Saying("出る杭は打たれる", "でるくいはうたれる", "The nail that sticks out gets hammered down."),
            Saying("井の中の蛙大海を知らず", "いのなかのかわずたいかいをしらず", "The frog in the well knows nothing of the sea."),
            Saying("百聞は一見に如かず", "ひゃくぶんはいっけんにしかず", "Seeing once beats hearing a hundred times."),
            Saying("三人寄れば文殊の知恵", "さんにんよればもんじゅのちえ", "Three heads together are as wise as Monju."),
            Saying("早起きは三文の徳", "はやおきはさんもんのとく", "Early rising earns you three mon."),
            Saying("一石二鳥", "いっせきにちょう", "Two birds with one stone."),
            Saying("温故知新", "おんこちしん", "Learn the new by studying the old."),
            Saying("日進月歩", "にっしんげっぽ", "Steady progress, day by day."),
            Saying("一生懸命", "いっしょうけんめい", "With all your might."),
            Saying("有言実行", "ゆうげんじっこう", "Say it, then do it."),
            Saying("以心伝心", "いしんでんしん", "Understanding without words."),
            Saying("起死回生", "きしかいせい", "Coming back from the brink."),
            Saying("二兎を追う者は一兎をも得ず", "にとをおうものはいっとをもえず", "Chase two hares and you catch neither."),
            Saying("鉄は熱いうちに打て", "てつはあついうちにうて", "Strike while the iron is hot."),
            Saying("備えあれば憂いなし", "そなえあればうれいなし", "Be prepared and you'll have no worries."),
            Saying("時は金なり", "ときはかねなり", "Time is money."),
            Saying("知らぬが仏", "しらぬがほとけ", "Not knowing is bliss."),
            Saying("猫に小判", "ねこにこばん", "Gold coins to a cat - wasted on the wrong person."),
            Saying("弘法にも筆の誤り", "こうぼうにもふでのあやまり", "Even Kōbō makes slips of the brush."),
            Saying("郷に入っては郷に従え", "ごうにいってはごうにしたがえ", "When in Rome, do as the Romans do."),
            Saying("明日は明日の風が吹く", "あしたはあしたのかぜがふく", "Tomorrow's wind will blow tomorrow."),
            Saying("一寸の虫にも五分の魂", "いっすんのむしにもごぶのたましい", "Even a tiny insect has a soul."),
            Saying("縁の下の力持ち", "えんのしたのちからもち", "The unsung strength holding things up."),
            Saying("口は災いの元", "くちはわざわいのもと", "The mouth is the root of trouble."),
            Saying("腹八分目に医者いらず", "はらはちぶんめにいしゃいらず", "Eat till 80% full and you won't need a doctor."),
            Saying("光陰矢の如し", "こういんやのごとし", "Time flies like an arrow."),
            Saying("雲外蒼天", "うんがいそうてん", "Beyond the clouds, blue sky."),
            Saying("臨機応変", "りんきおうへん", "Adapt to whatever comes."),
            Saying("花より団子", "はなよりだんご", "Dumplings over blossoms - substance over show."),
            Saying("自業自得", "じごうじとく", "You reap what you sow."),
            Saying("本末転倒", "ほんまつてんとう", "Putting the cart before the horse."),
            Saying("一日一歩", "いちにちいっぽ", "One step a day.")
        )
    }
}
