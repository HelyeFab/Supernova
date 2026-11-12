package com.supernova.launcher.jp.list

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.supernova.launcher.data.LaunchableApp
import com.supernova.launcher.notification.NotificationPreview

// JP fork, list mode: what the list page needs from the launcher activity.
interface ListHost {
    fun listApps(): List<LaunchableApp>
    fun listFavourites(): List<String>
    fun appKey(app: LaunchableApp): String
    fun appIcon(app: LaunchableApp): Drawable?
    fun launchFromList(app: LaunchableApp, anchor: View)
    fun showListAppMenu(anchor: View, app: LaunchableApp)
    fun showListShortcuts(anchor: View, app: LaunchableApp)
    fun notificationsFor(app: LaunchableApp): List<NotificationPreview>
}

// Tells the list page that apps, favourites, icons or notifications changed.
object ListBus {
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    fun changed() = main.post { listeners.toList().forEach { it() } }

    private val tops = LinkedHashSet<() -> Unit>()
    fun addTop(l: () -> Unit) { tops += l }
    fun removeTop(l: () -> Unit) { tops -= l }
    fun scrollTop() = main.post { tops.toList().forEach { it() } }
}
