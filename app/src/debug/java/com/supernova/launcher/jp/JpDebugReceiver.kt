package com.supernova.launcher.jp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import com.supernova.launcher.appGraph
import com.supernova.launcher.data.DrawerGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// JP fork, DEBUG BUILDS ONLY: lets adb (the shell holds DUMP, which the
// manifest requires of senders) export the launchable apps and import drawer
// groups written off-device - for laying out the drawer from linuxai.
//   adb shell am broadcast -n <pkg>/com.supernova.launcher.jp.JpDebugReceiver -a jp.DUMP_APPS
//     -> files/jp_apps.tsv  (key \t label \t package)
//   adb shell am broadcast -n ... -a jp.SET_GROUPS   (reads files/jp_groups.txt,
//     one DrawerGroup.encode() line each; replaces groups of the same name)
class JpDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "jp.DUMP_APPS" -> dumpApps(context)
                    "jp.SET_GROUPS" -> setGroups(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun dumpApps(context: Context) {
        val apps = context.getSystemService(LauncherApps::class.java)
        val users = context.getSystemService(UserManager::class.java)
        val lines = apps.profiles.flatMap { user ->
            val serial = users.getSerialNumberForUser(user)
            apps.getActivityList(null, user).map { a ->
                "$serial/${a.componentName.packageName}/${a.componentName.className}\t${a.label}\t${a.componentName.packageName}"
            }
        }
        File(context.filesDir, "jp_apps.tsv").writeText(lines.joinToString("\n"))
    }

    private suspend fun setGroups(context: Context) {
        val file = File(context.filesDir, "jp_groups.txt")
        val groups = file.readLines().mapNotNull { DrawerGroup.decode(it.trim()) }
        groups.forEach { context.appGraph.preferences.saveDrawerGroup(it) }
        File(context.filesDir, "jp_groups.done").writeText("${groups.size} groups saved\n")
    }
}
