package top.niunaijun.blackboxa.util

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.app.App
import top.niunaijun.blackboxa.app.AppManager

/**
 * Imports WhatsApp (Messenger / Business) into the sandbox automatically:
 *  - A: on first launch, if the app is missing in the sandbox
 *    but installed on the host device, the host's own APK copy is cloned.
 *  - B: on every engine start afterwards, if the host copy is NEWER than
 *    the sandbox copy, the sandbox copy is replaced to avoid version drift
 *    (WhatsApp in-app update prompts cannot work inside the sandbox).
 *
 * No APK is bundled, downloaded or redistributed: this only clones the
 * already-installed application from the user's own device via BlackBoxCore.
 */
object AutoImportManager {

    private const val TAG = "AutoImport"

    /** Messenger first, Business (optional, imported only if present on host). */
    val TARGET_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b", "com.gbwhatsapp")

    fun importAllIfNeeded(userId: Int = 0) {
        for (pkg in TARGET_PACKAGES) {
            try {
                importIfNeeded(pkg, userId)
            } catch (t: Throwable) {
                Log.w(TAG, "autoImport($pkg) failed", t)
            }
        }
    }

    /**
     * @return true when a fresh install/replace was performed.
     */
    fun importIfNeeded(packageName: String, userId: Int = 0): Boolean {
        if (!AppManager.mBlackBoxLoader.autoImportWhatsApp()) {
            return false
        }

        val context = App.getContext()
        val hostInfo: android.content.pm.PackageInfo? = try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (hostInfo == null) {
            Log.d(TAG, "$packageName not installed on host; skipping auto-import")
            return false
        }

        val core = BlackBoxCore.get()
        val hostVersionCode = versionCodeOf(hostInfo)

        val sandboxVersionCode: Long? = try {
            core.getInstalledPackages(0, userId)
                    .firstOrNull { it.packageName == packageName }
                    ?.let { versionCodeOf(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "sandbox package scan failed for $packageName", t)
            null
        }

        return when {
            sandboxVersionCode == null -> install(core, packageName, userId, "missing")
            hostVersionCode > sandboxVersionCode ->
                install(core, packageName, userId,
                        "update $sandboxVersionCode -> $hostVersionCode")
            else -> {
                Log.d(TAG, "$packageName up to date (sandbox=$sandboxVersionCode host=$hostVersionCode)")
                false
            }
        }
    }

    private fun install(
            core: BlackBoxCore,
            packageName: String,
            userId: Int,
            reason: String
    ): Boolean {
        val result = try {
            core.installPackageAsUser(packageName, userId)
        } catch (t: Throwable) {
            Log.w(TAG, "auto-import $packageName ($reason) threw", t)
            return false
        }
        if (result.success) {
            Log.i(TAG, "auto-import $packageName ($reason) OK, version " + versionLabel())
        } else {
            Log.w(TAG, "auto-import $packageName ($reason) failed: ${result.msg}")
        }
        return result.success
    }

    private fun versionCodeOf(info: android.content.pm.PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else info.versionCode.toLong()

    private fun versionLabel(): String = try {
        App.getContext().packageManager
                .getPackageInfo(App.getContext().packageName, 0).versionName ?: "?"
    } catch (t: Throwable) {
        "?"
    }
}
