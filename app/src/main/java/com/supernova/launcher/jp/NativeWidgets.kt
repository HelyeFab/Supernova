package com.supernova.launcher.jp

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.ui.ShortcutTarget
import com.supernova.launcher.ui.WidgetPickerEntry
import com.supernova.launcher.ui.WidgetPickerGroup
import com.supernova.launcher.ui.WidgetPickerIcons

// JP fork: widgets drawn by the launcher itself rather than hosted from
// another app through AppWidgetHost. They are ordinary WIDGET items whose
// widgetProvider is "native:<kind>" and whose appWidgetId stays null, so the
// workspace places, drags, resizes and backs them up like any widget, while
// the host-id machinery (rebind, orphan sweep) leaves them alone.
//
// Being real views in the launcher's own window, they are free of the
// RemoteViews limits: they can scroll, animate, know where they sit on
// screen, and refresh whenever the home screen is shown.
object NativeWidgets {

    private const val PREFIX = "native:"
    const val GROUP_KEY = "jp-native"
    // Not a widget: places a folder of the installed Japanese study apps (JapaneseApps).
    const val JAPANESE_FOLDER = "japanese-folder"

    class Spec(
        val kind: String,
        @StringRes val label: Int,
        @DrawableRes val icon: Int,
        val spanX: Int,
        val spanY: Int,
        val create: (Context, LauncherItem) -> View
    )

    private val specs = listOf(
        Spec(ShortcutBarWidget.KIND, R.string.jp_widget_shortcuts, R.drawable.ic_jp_torii, 4, 1) { c, _ ->
            ShortcutBarWidget(c)
        },
        Spec(
            com.supernova.launcher.jp.struggle.StruggleWidgetView.KIND,
            R.string.jp_widget_struggle, R.drawable.ic_jp_torii, 4, 2
        ) { c, _ -> com.supernova.launcher.jp.struggle.StruggleWidgetView(c) },
        Spec(WordOfDayView.KIND, R.string.jp_widget_word_of_day, R.drawable.ic_jp_torii, 4, 2) { c, _ ->
            WordOfDayView(c)
        },
        // KWGT replacements (jp/home).
        Spec(com.supernova.launcher.jp.home.SerifClockView.KIND, R.string.jp_widget_clock, R.drawable.ic_jp_torii, 4, 3) { c, _ ->
            com.supernova.launcher.jp.home.SerifClockView(c)
        },
        Spec(com.supernova.launcher.jp.home.TodayPanelView.KIND, R.string.jp_widget_today, R.drawable.ic_jp_torii, 4, 2) { c, _ ->
            com.supernova.launcher.jp.home.TodayPanelView(c)
        },
        Spec(com.supernova.launcher.jp.home.MonthGridView.KIND, R.string.jp_widget_month, R.drawable.ic_jp_torii, 4, 3) { c, _ ->
            com.supernova.launcher.jp.home.MonthGridView(c)
        },
        Spec(com.supernova.launcher.jp.home.WeatherForecastView.KIND, R.string.jp_widget_weather, R.drawable.ic_jp_torii, 4, 2) { c, _ ->
            com.supernova.launcher.jp.home.WeatherForecastView(c)
        }
    )

    fun provider(kind: String): String = PREFIX + kind

    fun isNative(provider: String?): Boolean = provider?.startsWith(PREFIX) == true

    fun spec(kind: String): Spec? = specs.firstOrNull { it.kind == kind }

    fun create(context: Context, item: LauncherItem): View {
        val kind = item.widgetProvider.orEmpty().removePrefix(PREFIX)
        return spec(kind)?.create?.invoke(context, item) ?: TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            text = context.getString(R.string.jp_widget_unknown)
        }
    }

    // Listed first in both widget pickers.
    fun pickerGroup(context: Context, icons: WidgetPickerIcons): WidgetPickerGroup = WidgetPickerGroup(
        packageName = context.packageName,
        label = context.getString(R.string.jp_widgets_group),
        icon = context.getDrawable(R.drawable.ic_jp_torii)?.let(icons.render),
        widgets = emptyList(),
        shortcuts = listOf(
            WidgetPickerEntry.Shortcut(
                packageName = context.packageName,
                label = context.getString(R.string.jp_japanese_folder),
                icon = context.getDrawable(R.drawable.ic_jp_torii)?.let(icons.render),
                target = ShortcutTarget.Native(JAPANESE_FOLDER)
            )
        ) + specs.map { spec ->
            WidgetPickerEntry.Shortcut(
                packageName = context.packageName,
                label = context.getString(spec.label),
                icon = context.getDrawable(spec.icon)?.let(icons.render),
                target = ShortcutTarget.Native(spec.kind)
            )
        },
        key = GROUP_KEY
    )
}
