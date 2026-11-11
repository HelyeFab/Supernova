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
- [ ] 4. **Word of the day** card (home + feed), from Anki decks, furigana + audio.
- [ ] 5. Replace KWGT: clock (SerifClock look), Today panel, month grid with 曜日/祝日/令和,
      weather, moon phase. Then retire KWGT + the Washi Octopi auto-fix.
