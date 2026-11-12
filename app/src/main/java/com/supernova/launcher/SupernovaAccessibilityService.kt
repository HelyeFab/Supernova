package com.supernova.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

// Optional lock/recents actions. JP fork: also hosts the explicitly invoked
// Japanese reader's screenshot overlay; it never retrieves accessibility nodes.
class SupernovaAccessibilityService : AccessibilityService() {
    // JP fork: explicitly invoked Japanese screen reader; also available outside home.
    private var reader: com.supernova.launcher.jp.reader.ReaderOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        reader = com.supernova.launcher.jp.reader.ReaderOverlay(this).also { it.connect() }
    }

    override fun onDestroy() {
        reader?.destroy()
        reader = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // JP fork: keep existing lock/recents actions independent of the reader.
    companion object {
        fun refreshReader() { instance?.reader?.refresh() }
        fun readScreen(): Boolean = instance?.reader?.let { it.capture(); true } ?: false

        @Volatile
        private var instance: SupernovaAccessibilityService? = null

        // Switched on in the system's accessibility settings; the service
        // may not be connected yet even so.
        fun isEnabled(context: android.content.Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return flat.split(':').any {
                android.content.ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }

        fun lockScreen(): Boolean = perform(GLOBAL_ACTION_LOCK_SCREEN)

        fun showRecents(): Boolean = perform(GLOBAL_ACTION_RECENTS)

        fun showQuickSettings(): Boolean = perform(GLOBAL_ACTION_QUICK_SETTINGS)

        private fun perform(action: Int): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(action)
        }
    }
}
