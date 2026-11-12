#!/usr/bin/env python3
"""JP fork: the Honor's themed drawer tabs (2026-11-12). Debug builds only.

    P=dev.fabiani.supernova.debug
    adb shell am broadcast -n $P/com.supernova.launcher.jp.JpDebugReceiver -a jp.DUMP_APPS
    adb shell run-as $P cat files/jp_apps.tsv > apps.tsv
    python3 -I tools/jp/drawer_groups.py apps.tsv > jp_groups.txt     # report on stderr
    adb push jp_groups.txt /data/local/tmp/ && adb shell "run-as $P sh -c 'cat /data/local/tmp/jp_groups.txt > files/jp_groups.txt'"
    adb shell am broadcast -n $P/com.supernova.launcher.jp.JpDebugReceiver -a jp.SET_GROUPS

Tabs keep their apps in All ("keep"); personal profile only (serial 0).
"""
import sys, collections
apps=[l.rstrip('\n').split('\t') for l in open(sys.argv[1], encoding='utf-8') if l.strip()]
personal=[a for a in apps if a[0].startswith('0/')]
G=collections.OrderedDict([
 ("Japanese 🍙", "com.craxic.akebifree com.ichi2.anki jp.takoboto com.mizuvt.jlptplus.free com.mh.kanjiblossom com.mindtwisted.kanjistudy com.kotomora.android com.makotostorytime org.chromium.webapk.ad44d92bb0897b34f_v2 com.myoland.miraa org.chromium.webapk.a5e0072321ddef81b_v2 com.mugi jp.co.eversense.pibo com.renshuu.renshuu_org org.chromium.webapk.a9890649035813701_v2 com.brilliantcorp.japanesetube dev.fabiani.translayer org.chromium.webapk.a60d4f8be8571b9bf_v2 com.waocorp.waochi.kotobaland org.chromium.webapk.a3943a3bad283c8d0_v2 com.WagotabiLimited.Wagotabi com.languagedrops.drops.international com.elysoft.nazoru com.wirelingo.app com.preply dev.fabiani.strugglewidget com.hihonor.magictranslate com.google.android.apps.translate"),
 ("Social", "com.whatsapp org.telegram.messenger com.google.android.apps.messaging com.facebook.orca com.facebook.katana com.instagram.android com.twitter.android com.zhiliaoapp.musically com.pinterest com.google.android.apps.tachyon com.google.android.dialer com.google.android.contacts com.google.android.gm com.hihonor.email com.microsoft.office.outlook com.yahoo.mobile.client.android.mail"),
 ("AI", "com.openai.chatgpt com.anthropic.claude com.google.android.apps.bard com.hihonor.magicvoice com.hihonor.magichome sh.paseo"),
 ("Notes & docs", "md.obsidian com.google.android.keep com.hihonor.notepad com.google.android.apps.labs.language.tailwind org.chromium.webapk.ae3bc453a9eaf1953_v2 org.chromium.webapk.aa47e61eed569a4fd_v2 com.cosmovex.mdvedit com.microsoft.office.excel com.hihonor.hnofficelauncher com.google.android.apps.docs com.ttxapps.drivesync com.nextcloud.client com.google.android.calendar com.hihonor.calendar com.onyx.galaxy.global.note com.hihonor.soundrecorder org.chromium.webapk.a85dc0f39ef123711_v2"),
 ("Reading", "com.amazon.kindle org.koreader.launcher com.overdrive.mobile.android.libby com.foobnix.pro.pdf.reader com.flyersoft.moonreaderp com.google.android.apps.books com.goodreads com.readany.app.preview com.mobisystems.msdict.embedded.wireless.oxford.dictionaryofenglish"),
 ("Media", "com.crunchyroll.crunchyroid com.disney.disneyplus com.google.android.videos com.netflix.mediaclient org.schabi.newpipe com.amazon.avod.thirdpartyclient org.videolan.vlc com.hihonor.hnvideoplayer com.hihonor.hnmusicplayer in.krosbits.musicolet com.google.android.youtube com.google.android.apps.youtube.kids com.google.android.apps.youtube.music com.myvue.app com.google.android.apps.photos com.google.android.apps.photosgo com.hihonor.photos com.hihonor.camera app.alextran.immich"),
 ("Games", "com.x.aniimos dev.eden.eden_emulator.nightly com.DotDotFire.DDF2024 com.hihonor.gamecenter com.HoYoverse.hkrpgoversea com.google.android.play.games com.tgc.sky.android com.valvesoftware.android.steam.community com.vitastudio.mahjong com.MojikenStudio.WhenPastWasAround"),
 ("Travel", "com.airbnb.android com.booking com.citymapper.app.release com.mttnow.droid.easyjet com.mttnow.android.etihad com.grabtaxi.passenger es.heymondo com.holafly.holafly com.google.android.apps.maps com.raildeliverygroup.railcard com.ryanair.cheapflights com.thetrainline ctrip.english com.ubercab org.chromium.webapk.acaa4e9c0bfb6bec2_v2 uk.co.novaware.telepass.android com.toyota.oneapp.eu"),
 ("Money", "it.caitalia.apphub com.newdaycards.jlcc co.uk.getmondo com.rbs.mobile.android.natwest com.paypal.android.p2pmobile com.google.android.apps.walletnfcrel net.maya.mobile it.pagopa.io.app it.company.sielte com.alibaba.aliexpresshd com.shopify.arrive com.google.android.apps.subscriptions.red it.wind.myWind"),
 ("Health", "com.fitbit.FitbitMobile com.heytap.health.international co.healthium.nutrium epic.mychart.android com.nhs.online.nhsonline"),
 ("Home & devices", "com.google.android.apps.chromecast.app com.tplink.iot chipolo.net.v3 com.google.android.wearable.app com.google.android.apps.adm cn.com.aftershokz.app com.sony.songpal.mdr com.redmiwatch5liteappguide.peniinc com.hihonor.android.remotecontroller com.hihonor.mirror org.kde.kdeconnect_tp com.pittvandewitt.wavelet"),
 ("Customise", "com.hihonor.android.thememanager com.jndapp.cartoon.crayon.iconpack app.lawnchair.lawnicons io.github.PapirusDevelopmentTeam.papirus_icons org.kustom.widget org.kustom.widget.pro org.kustom.booster com.otp.octopilauncher dev.fabiani.washi dev.fdesign.widgetwall.app com.akshaykadam.pixelboard dev.fabiani.supernova.debug"),
 ("Tools", "com.android.settings com.hihonor.systemmanager com.google.android.apps.nbu.files com.google.android.documentsui com.hihonor.filemanager pl.solidexplorer2 com.termux com.tailscale.ipn moe.shizuku.privileged.api com.oasisfeng.island com.azure.authenticator com.google.android.apps.authenticator2 com.zoho.accounts.oneauth com.surfshark.vpnclient.android org.fdroid.fdroid cm.aptoide.pt com.hihonor.appmarket com.android.vending com.github.android com.android.chrome org.mozilla.firefox com.google.android.googlequicksearchbox com.hihonor.search com.google.android.calculator com.hihonor.calculator com.hihonor.deskclock com.hihonor.compass com.hihonor.android.totemweather com.zte.weather com.hihonor.android.clone com.hihonor.ouc com.hihonor.tips com.hihonor.phoneservice com.google.android.apps.accessibility.voiceaccess com.android.stk"),
])
assigned=set(); out=[]
for i,(name,pk) in enumerate(G.items()):
    pkgs=pk.split()
    keys=[a[0] for a in personal if a[2] in pkgs]
    # leaks / debug-settings activities of the launcher itself stay out of Customise
    keys=[k for k in keys if 'leakcanary' not in k]
    assigned.update(a[2] for a in personal if a[0] in keys)
    missing=[p for p in pkgs if not any(a[2]==p for a in personal)]
    print(f"{name}: {len(keys)} apps" + (f"  (not installed: {missing})" if missing else ""), file=sys.stderr)
    out.append(f"{name}|{','.join(keys)}|{i+1}|TAB||keep")
left=sorted({(a[1],a[2]) for a in personal if a[2] not in assigned})
print("UNASSIGNED:", left, file=sys.stderr)
print("\n".join(out))
