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
- [ ] 2. **Struggle** widget ported from `~/Dev/anki-struggle-widget` (AnkiDroid provider,
      swipeable carousel, real frost from the view's own position, grading, AivisSpeech).
- [ ] 3. **Dictionary in search**: offline JMdict (from `~/Downloads/JMdict_english.zip`) as a
      search provider; tap → Akebi/Takoboto.
- [ ] 4. **Word of the day** card (home + feed), from Anki decks, furigana + audio.
- [ ] 5. Replace KWGT: clock (SerifClock look), Today panel, month grid with 曜日/祝日/令和,
      weather, moon phase. Then retire KWGT + the Washi Octopi auto-fix.
