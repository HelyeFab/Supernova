package com.supernova.launcher.jp

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.supernova.launcher.data.LaunchableApp

// JP fork: the Japanese study apps for the "Japanese apps" folder, in the
// order of his Japanese 🍙 folder (Octopi backup, 2026-10-25). Only installed
// ones are used. Chrome WebAPKs have hashed package names, so those are found
// by the site they own instead.
object JapaneseApps {

    const val FOLDER_LABEL = "Japanese 🍙"

    private sealed interface Source
    private data class Pkg(val pkg: String) : Source
    private data class Site(val url: String) : Source

    private val SOURCES: List<Source> = listOf(
        Pkg("com.craxic.akebifree"),
        Pkg("com.ichi2.anki"),
        Pkg("jp.takoboto"),
        Pkg("com.mizuvt.jlptplus.free"),
        Pkg("com.mh.kanjiblossom"),
        Pkg("com.mindtwisted.kanjistudy"),
        Pkg("com.kotomora.android"),
        Pkg("com.makotostorytime"),
        Site("https://manabi-rho.vercel.app/"),
        Pkg("com.myoland.miraa"),
        Site("https://moshimoshi.app/"),
        Pkg("com.mugi"),
        Pkg("jp.co.eversense.pibo"),
        Pkg("com.renshuu.renshuu_org"),
        Site("https://sakiyomu.vercel.app/"),
        Pkg("com.brilliantcorp.japanesetube"),
        Pkg("dev.fabiani.translayer"),
        Site("https://anime-manga.jp/"),
        Pkg("com.waocorp.waochi.kotobaland"),
        Site("https://chiba.appsparkle.org/"),
        Pkg("com.WagotabiLimited.Wagotabi"),
        Pkg("com.languagedrops.drops.international")
    )

    fun installed(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        return SOURCES.mapNotNull { source ->
            val pkg = when (source) {
                is Pkg -> source.pkg
                is Site -> pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)), 0)
                    .map { it.activityInfo.packageName }
                    .firstOrNull { it.startsWith("org.chromium.webapk") }
            } ?: return@mapNotNull null
            val launch = pm.getLaunchIntentForPackage(pkg)?.component ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            LaunchableApp(pkg, launch.className, label)
        }.distinctBy { it.packageName }
    }
}
