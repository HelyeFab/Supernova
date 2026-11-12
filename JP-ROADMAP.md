# Supernova JP — fork roadmap

Fork of [tanujnotes/Supernova](https://github.com/tanujnotes/Supernova) (AGPL-3.0) for the Honor Magic V6.
Goal: a Japanese-study-friendly launcher whose widgets are its own, replacing KWGT entirely.

- Package: `dev.fabiani.supernova` (debug: `.debug`), label "Supernova JP". Kotlin namespace stays
  `com.supernova.launcher`, so upstream merges stay clean.
- Branch `jp` carries the fork; `main` tracks upstream. Merge upstream with
  `git fetch upstream && git merge upstream/main` on `jp`.
- **All fork code lives in `app/src/main/java/com/supernova/launcher/jp/`** and `res/values/jp_strings.xml`.
  Edits to upstream files are small hooks marked `JP fork`.

## Built-in widgets (the core idea)
A built-in widget is an ordinary `WIDGET` item with `widgetProvider = "native:<kind>"` and
`appWidgetId = null`. The workspace places/drags/resizes/backs it up like any widget; the
AppWidgetHost rebind and orphan sweep skip it. Registry: `jp/NativeWidgets.kt`.
They are real views in the launcher window — no RemoteViews limits (scrolling, animation,
exact on-screen position for frosted glass, refresh on every home show, no stale-widget bug).
Added from the widget picker's first group, **Built-in (日本語)** (tap to place).

Hooks in upstream files: `WidgetPickerGroups` (ShortcutTarget.Native), `WidgetPickerAdapter`,
`ShortcutPlacer.addNativeWidget`, `WidgetController.buildWidgetView`, `RestoredWidgets`,
`WidgetPickerSheet` + `HomeEditController` (picker group), `addWidget(appWidgetId: Int?)`.

## Phases
- [x] 0. Own app id + name; builds; 446 unit tests pass.
- [x] 1. Built-in widget framework + **Japanese shortcuts** bar (Chiba WebAPK, ZeroClaw via
      Washi's OpenChatActivity → tg:// fallback, Anki, Akebi, Takoboto).
- [x] 2. **Struggle** widget ported from `~/Dev/anki-struggle-widget` into `jp/struggle/`
      (CardRepo/Queue/Tts/ReviewActivity copied; new `StruggleWidgetView` + `Frost`). Reviews Shiba
      Dash's top-level **"Struggling Cards"** filtered deck (renamed 2026-11-10; old "Struggling"
      still accepted). Tap = reveal (+ AivisSpeech), grade pills show AnkiDroid's intervals,
      swipe ← or ⤼ = skip (bury), ⤢ = full reviewer, reloads whenever home is shown. Frost is
      cut from the wallpaper at the view's real screen position — no calibration.
      Phone setup: `pm grant dev.fabiani.supernova.debug com.ichi2.anki.permission.READ_WRITE_DATABASE`,
      `appops set dev.fabiani.supernova.debug MANAGE_EXTERNAL_STORAGE allow`; `tts.key` in local.properties.
      ⚠️ MagicOS "Smart recovery" offers to UNINSTALL after several `adb install -r` in a row while the
      app is foreground — press Cancel; it is the reinstall kills, not a crash (process stable after).
- [x] 3. **Dictionary in search** (`jp/dict/`): offline JMdict, 218,989 entries. Kana, kanji or
      fully-convertible romaji (`taberu`, `kyou`, `konnichiha`; `Romaji.kt`, tested) → up to 4 rows
      under a "Dictionary" header after the apps (★ = common). Tap → Akebi (PROCESS_TEXT), else
      Takoboto, else jisho.org. App queries like "chrome"/"maps" never convert, so they're unaffected.
      **The database is git-ignored and must be built before assembling**:
      `python3 -I tools/jp/build_jmdict.py ~/Downloads/JMdict_english.zip app/src/main/assets/jp/jmdict.db`
      (source: yomidevs/jmdict-yomitan). 43 MB on device (copied once per app update), +20 MB APK.
      Not yet: deinflection (食べた → 食べる), English → Japanese lookup.
- [x] 4. **Word of the day** (`jp/WordOfDayView.kt`, 4x2): three sources, switched by swiping
      inside the card or tapping its tabs — **Anki** (Core 2k/6k, JLPT Tango, kotoba;
      `-is:new -is:suspended prop:ivl<21`, i.e. learning but not mature), **辞書** (a common JMdict
      noun/verb/adjective with kanji), **Chiba** (latest Telegram ping from
      `chiba.appsparkle.org/api/ping-current`, token `chiba.widget.token` in local.properties,
      refetched at most every 5 min when home is shown). Anki/辞書 picks are stable per day.
      Tap = AivisSpeech (pings at 0.8 speed); ↗ = Akebi for the word / Chiba for the ping.
- [x] 5. KWGT replacements (`jp/home/`), same palette (fg #DCDCD8, sub #BEBEBA, card #73303030,
      accent #E05A47) and Abril Fatface (`res/font/jp_abril_fatface.ttf`, OFL):
      **Serif clock** 4x3 (11月11日(水) date, HH.mm, battery ring, drawn moon phase, ZeroClaw paw,
      weather pill; taps → Calendar / Honor Clock / battery / Honor Weather / ZeroClaw),
      **Today panel** 4x2 (day, 令和 year, 曜日, weather, 次の祝日 countdown, time),
      **Month calendar** 4x3 (月-first grid, 土 blue, 日+祝日 red, today ringed, holidays named),
      **Weather forecast** 4x2 (now + 4 days, 雨 · Rain labels, drawn icons — `WeatherGlyph`, since
      emoji ignore U+FE0E here). `JpCalendar` computes 祝日 incl. 振替休日/国民の休日 (tested vs the
      2026 Cabinet Office list); `Moon` = mean synodic month. Weather = Open-Meteo for a FIXED place
      (`Weather.PLACE` Medicina 44.48,11.64), cached 30 min. All redraw on TIME_TICK/battery and on
      every home show — they cannot go stale.
- [ ] 6. Switch the Honor's default home to Supernova JP, move the layout over (backup export/import
      from F-Droid Supernova, or rebuild), then retire KWGT + the Washi Octopi auto-fix.
- Ideas: deinflection in dictionary search; EN→JP lookup; weather place setting; frost on all cards.
## Since phase 5 (2026-11-11, evening)
- Supernova JP Debug is the Honor's DEFAULT HOME. Home page = clock page (edit mode → house button).
  ⚠️ MagicOS Smart recovery resets the HOME role after repeated `adb install -r` of the home app:
  after every install run `adb shell cmd role get-role-holders android.app.role.HOME` and restore with
  `cmd role add-role-holder android.app.role.HOME dev.fabiani.supernova.debug 0`. Always press Cancel on
  the "Smart recovery … uninstall" dialog. A signed release build installed once would end this.
- Clock: digits drawn by ink (`InkTimeView`, 90% width), date/time/pills packed; date Japanese only.
- **Japanese apps folder** picker entry (`jp/JapaneseApps.kt`, 22 apps, WebAPKs by URL); bar removed.
- **Study stack** (`jp/StackWidgetView.kt`): word of the day + Struggle, vertical swipe rotates, dots;
  `WidgetController.trackWidgetScroll` lets it own its gestures. Word card has ↻ (per-day skips),
  real deck names, ruby-free text, Japanese-first for kotoba notes.
- **Feed cards** (`jp/cards/`): News (NHK EASY from nhk.selfmind.dev with furigana `RubySpan` + ▶ NHK
  audio, fallback NHK ONE RSS), 今日の名言 (50 sayings), 季節 (和風月名 + 二十四節気), 今日の復習
  (AnkiDroid due per top-level deck). Feed cards can feature any built-in widget (Add card → Widget →
  Built-in); name + height set automatically.
- nhk-easy fetcher on leonard is now on an hourly cron (Emmanuel added it).

## Next
- Verify News card furigana rendering on the phone (not yet seen).
- Signed release build (stop the home-role resets).
- Optional: jump to the clock page on screen-on; dictionary deinflection; EN→JP lookup.

## 2026-11-12
- **Drawer theme tabs** (13, before Work): `tools/jp/drawer_groups.py` + debug-only `JpDebugReceiver`
  (`jp.DUMP_APPS` / `jp.SET_GROUPS`, senders need DUMP = adb). Unplaced: Linksi, Sluka, cuscon, hakuX.
- **List home mode** (Niagara-style; Settings > Home screen > "List home"): `jp/list/` — `ListHomeView`
  (serif clock header, favourites, A–Z/#/あ–わ/漢 sections via `ListSections`, scrubber with glide + wave
  leaning towards the thumb, inline notifications tap/swipe-left dismiss, tap/long-press/swipe-right
  shortcuts), `ListHost` implemented by LauncherActivity, `ListBus`. Inserted as page 0 widget
  `native:jp-list`; dock + swipe-up drawer off; search bar at the very bottom; Home scrolls list to top;
  personal copies preferred over work/App Twin duplicates. Turning it off restores the old home page.
- Search bar: 🔍 opens Google search; new **Claude** chip (mic removed in his settings).
- Open: list-mode notifications not yet seen live; list-mode OFF path not tested on the phone;
  signed release build (MagicOS still resets the HOME role after installs — check after every install).
- Later 2026-11-12: list mode left-edge swipe (first 40dp) always opens the cards feed; feed cards
  recolour to the feed card's text colour (`FeedThemed`, `CardBase.applyFeedColors`); easy news shows 3
  whole headlines. NHK Easy furigana + ▶ 聞く verified on the phone.

## Japanese screen reader (2026-11-12)
- `jp/reader/`: an integrated first reading loop, using the launcher's existing JMdict and
  AivisSpeech. Floating **読** accessibility overlay → frozen screen → select a region (or whole
  screen) → bundled Japanese ML Kit OCR → editable source → sentence navigation, optional furigana,
  tappable words, offline vocabulary/dictionary forms, normal/slow audio, Claude grammar and follow-ups.
- Entry points: **Home screen settings → 読む · Japanese reader**, its drawer entry, Android
  **PROCESS_TEXT** / text sharing, and a **Built-in (日本語)** home/feed widget.
  Reader Settings controls the floating button; drag it vertically, hold it for settings.
  Accessibility must be enabled for Supernova JP; screen capture requires Android 11+.
  Lock/recents gestures retain their existing service and behavior. No accessibility-node scraping.
- OCR and dictionary lookup run locally. Listen sends just the selected Japanese to AivisSpeech;
  Explain/Ask sends the current sentence (or All) to Claude. Captured images stay in private cache;
  Claude receives text, not the screenshot. Source corrections are explicit; no silent AI rewriting.
- Private build defaults: `tts.key` and optional `claude.key` in gitignored `local.properties`.
  Claude key/model can be changed in Reader Settings (default `claude-haiku-4-5`). Reader preferences
  are outside launcher file-domain backups. Missing keys and HTTP failures produce visible errors.
- Kuromoji/IPADIC handles segmentation, dictionary forms, and conjugated readings. Causative/passive
  suffixes remain attached to the verb: 食べさせられた → 食べる. Existing dictionary search now has an
  exact-only lookup for reader words; it never substitutes a prefix match for an unknown word.
- Audio cancellation also invalidates pending downloads, so Stop/changing sentence/closing cannot
  start a stale clip. Shared widget audio uses the same corrected player.
- Validation: 463 unit tests; targeted Android tests for OCR, successive reader intents and recreation.
  The full lint gate has existing JP findings outside the reader; do not regenerate its baseline.
- Later: Anki sentence/audio export, persistent reading history, improved vertical-page ordering,
  and optional screenshot-assisted OCR correction. Protected app screens remain uncapturable;
  text sharing/selection is the alternative where the source app supports it.
- Honor live check: captured NHK's 美しい景色を見ることができました。 exactly, with furigana,
  vocabulary, completed audio playback and a returned Claude translation/grammar breakdown.
  Narrow screenshot previews now keep their aspect ratio;
  region actions divide the available width equally. Exact dictionary lookup prefers the observed
  spelling before frequency ties (こと must select the kana noun, not the homophone 琴).
  Three targeted Android tests cover OCR, repeated intents/recreation and that dictionary regression.
- MagicOS can remove Supernova's accessibility entry asynchronously after an update: restore it
  again after the package update settles, preserving other enabled services, and check Bound services.
