package top.niunaijun.blackboxa.util

import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackboxa.app.App
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sandbox migration wizard for the WhatsApp edition.
 *
 * Export packs the clone's whole data tree (internal data directory + sandbox
 * external directory, minus caches/libs) into a zip under the host app's
 * `files/export/` directory (i.e.
 * /sdcard/Android/data/top.niunaijun.blackbox/files/export/), so "wiped
 * sandbox" becomes a recoverable event and the export can travel between
 * devices/builds.
 *
 * Import restores such a zip. Safety model:
 *  - the clone process is stopped first,
 *  - data is unpacked to a staging directory and atomically swapped in,
 *  - only performed when the caller confirms (the Settings entry shows a
 *    confirm dialog; this object does the work).
 *
 * Everything stays on the device: no cloud, no third-party server involved.
 */
object MigrationManager {

    private const val TAG = "Migration"

    /** Target of this edition; kept in sync with AutoImportManager. */
    const val TARGET = "com.whatsapp"
    private const val USER = 0

    private const val EXCLUDE = "cache|code_cache|lib|CrashReportCache|tmp"

    fun exportDir(): File {
        val dir = File(App.getContext().getExternalFilesDir("export"), ".")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** @return the exported zip file, or null on failure (message logged). */
    fun export(): File? {
        return try {
            BlackBoxCore.get().stopPackage(TARGET, USER)
            Thread.sleep(500)

            val dataDir = BEnvironment.getDataDir(TARGET, USER)
            if (!dataDir.exists()) {
                Log.w(TAG, "export: no sandbox data yet for $TARGET")
                return null
            }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
            val out = File(exportDir(), "$TARGET-sandbox-$stamp.zip")
            ZipOutputStream(FileOutputStream(out)).use { zip ->
                addDir(dataDir, "data/", zip)
                val external = BEnvironment.getExternalDataDir(TARGET, USER)
                if (external != null && external.exists()) {
                    addDir(external, "external/", zip)
                }
            }
            Log.i(TAG, "export OK -> ${out.absolutePath} (${out.length() / 1024 / 1024} MB)")
            out
        } catch (t: Throwable) {
            Log.e(TAG, "export failed", t)
            null
        }
    }

    /**
     * Restore the newest export zip matching [TARGET]-sandbox-*.zip.
     * Replaces any current sandbox data for the target (caller confirms).
     * @return true on success.
     */
    fun importNewest(): Boolean {
        val candidates = exportDir().listFiles { f ->
            f.name.startsWith("$TARGET-sandbox-") && f.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() }
        if (candidates.isNullOrEmpty()) {
            Log.w(TAG, "import: no export zips found in ${exportDir().absolutePath}")
            return false
        }
        return import(candidates[0])
    }

    fun import(zipFile: File): Boolean {
        return try {
            BlackBoxCore.get().stopPackage(TARGET, USER)
            Thread.sleep(500)

            val dataTarget = BEnvironment.getDataDir(TARGET, USER)
            val staging = File(dataTarget.parentFile, "${TARGET.nameIf()}-import-stage")
            if (staging.exists()) staging.deleteRecursively()
            staging.mkdirs()

            // unzip into staging; entries keep their data/|external/ prefixes
            ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
                var entry: ZipEntry? = zin.nextEntry
                while (entry != null) {
                    val outFile = File(staging, entry.name)
                    if (!outFile.canonicalPath.startsWith(staging.canonicalPath)) {
                        throw SecurityException("zip entry escapes staging: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zin.copyTo(it) }
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }

            // atomic-ish swap: move current aside, staging in, backup out
            val backup = File(dataTarget.parentFile, "${TARGET.nameIf()}-pre-import-backup")
            if (backup.exists()) backup.deleteRecursively()
            if (dataTarget.exists()) {
                if (!dataTarget.renameTo(backup)) {
                    throw IllegalStateException("could not move existing data dir aside")
                }
            }
            val stagedData = File(staging, "data")
            if (!stagedData.renameTo(dataTarget)) {
                dataTarget.parentFile?.mkdirs()
                if (!stagedData.renameTo(dataTarget)) {
                    // roll back
                    if (backup.exists()) backup.renameTo(dataTarget)
                    throw IllegalStateException("could not move staged data into place")
                }
            }
            val externalTarget = BEnvironment.getExternalDataDir(TARGET, USER)
            val stagedExternal = File(staging, "external")
            if (stagedExternal.exists() && stagedExternal.list()!!.isNotEmpty() && externalTarget != null) {
                if (externalTarget.exists()) externalTarget.deleteRecursively()
                stagedExternal.renameTo(externalTarget)
            }

            // cleanup
            staging.deleteRecursively()
            if (backup.exists()) backup.deleteRecursively()
            BlackBoxCore.get().launchApk(TARGET, USER) // bring clone back, refreshed
            Log.i(TAG, "import OK from ${zipFile.name}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            false
        }
    }

    private fun String.nameIf(): String = replace(".", "_")

    private fun addDir(root: File, zipRoot: String, zip: ZipOutputStream) {
        val skipTop = Regex(EXCLUDE)
        root.walk()
                .filter { file ->
                    if (file == root) return@filter false
                    val top = file.absolutePath
                            .removePrefix(root.absolutePath)
                            .trimStart('/')
                            .split('/')
                            .firstOrNull() ?: return@filter false
                    !top.matches(skipTop)
                }
                .filter { it.isFile }
                .forEach { file ->
                    val rel = file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                    zip.putNextEntry(ZipEntry(zipRoot + rel))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
    }
}
